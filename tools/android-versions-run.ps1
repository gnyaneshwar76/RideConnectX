<#
 Runs the Android-version check on ONE emulator (AVD), then shuts it down.
 Usage:  powershell -ExecutionPolicy Bypass -File tools\android-versions-run.ps1 -Avd <name from -List> [-ScreenOffMinutes 10] [-GapThresholdSec 30]
 Output: .claude\team\reports\screens-android\<Avd>\  (screens, logcat.txt, gfxinfo-*.txt, steps.txt,
         results.txt = one line per check: PASS / FAIL / NOT TESTABLE | name | reason)
 Debug build only. Real phones are never touched: every adb call uses -s <emulator serial>.
 Run the AVDs one after another, never two at once.
#>
param([string]$Avd, [switch]$List, [int]$ScreenOffMinutes = 10, [int]$GapThresholdSec = 30)
$ErrorActionPreference = 'Stop'
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emu = Join-Path $sdk 'emulator\emulator.exe'
if ($List) { & $emu -list-avds; return }
if (-not $Avd) { throw 'Give -Avd <name> (see -List)' }
$root = Split-Path $PSScriptRoot -Parent
$pkg = 'com.gnyaneshwar.rideconnectx'
$rcv = "$pkg/com.eshwar.rideconnectx.debug.NavTestReceiver"
$lst = "$pkg/com.eshwar.rideconnectx.data.nav.MapsNotificationListener"
$out = Join-Path $root ".claude\team\reports\screens-android\$Avd"
New-Item -ItemType Directory -Force $out | Out-Null
$steps = Join-Path $out 'steps.txt'; '' | Set-Content $steps
function Log($m) { $l = "$(Get-Date -Format HH:mm:ss) $m"; Write-Host $l; Add-Content $steps $l }
$resf = Join-Path $out 'results.txt'; "Results for $Avd" | Set-Content $resf
function Res([string]$name, [string]$status, [string]$why = '') { $l = "$status | $name | $why"; Add-Content $resf $l; Log "RESULT $l" }

# One emulator at a time: refuse if any emulator or qemu is already running.
if (((& $adb devices) -match 'emulator-\d+') -or (Get-Process -Name 'qemu-system*' -ErrorAction SilentlyContinue)) {
  throw 'Another emulator is running. Shut it down first.'
}
if (-not ((& $emu -list-avds) -contains $Avd)) { throw "No AVD named $Avd" }

$script:serial = $null
function A { & $adb -s $script:serial @args }
function Sh([string]$cmd) { A shell $cmd }
function Shot($name) { Sh 'screencap -p /sdcard/rcx.png' | Out-Null; A pull /sdcard/rcx.png (Join-Path $out "$name.png") | Out-Null }
function Fake([string]$extra) {
  Sh "am broadcast -n $rcv -a com.eshwar.rideconnectx.TEST_FAKE $extra" | Out-Null
}

# ---- UI driving: uiautomator dump + tap the centre of a node found by text (no hard-coded coordinates) ----
function Get-Nodes {
  try {
    Sh 'rm -f /sdcard/ui.xml' | Out-Null; Sh 'uiautomator dump /sdcard/ui.xml' | Out-Null
    $x = [xml]((Sh 'cat /sdcard/ui.xml') -join "`n")
    return @($x.SelectNodes('//node'))
  } catch { return @() }
}
function Find-Node([string]$re, $nodes = $null) {   # whole-text regex on text or content-desc
  if ($null -eq $nodes) { $nodes = Get-Nodes }
  # prefer a visible match; Compose often reports a button's text as [0,0][0,0] (Tap-Node then uses its parent)
  $m = @($nodes | Where-Object { ($_.text -match "^($re)$") -or ($_.'content-desc' -match "^($re)$") })
  ($m | Where-Object { $_.bounds -ne '[0,0][0,0]' } | Select-Object -First 1), $m[0] | Where-Object { $_ } | Select-Object -First 1
}
function Tap-Node($n) {
  while ($n.bounds -eq '[0,0][0,0]' -and $n.ParentNode -and $n.ParentNode.bounds) { $n = $n.ParentNode }
  $b = [regex]::Match($n.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
  $cx = [int](([int]$b.Groups[1].Value + [int]$b.Groups[3].Value) / 2); $cy = [int](([int]$b.Groups[2].Value + [int]$b.Groups[4].Value) / 2)
  Sh "input tap $cx $cy" | Out-Null
}
function Tap-Text([string]$re) { $n = Find-Node $re; if ($n) { Tap-Node $n; return $true }; return $false }
function Wait-Text([string]$re, [int]$sec) {
  $until = (Get-Date).AddSeconds($sec)
  while ((Get-Date) -lt $until) { if (Find-Node $re) { return $true }; Start-Sleep 2 }
  return $false
}
function Screen-Texts { (Get-Nodes | ForEach-Object { $_.text } | Where-Object { $_ } | Select-Object -First 12) -join ' / ' }
function Alive { (Sh "pidof $pkg") -join '' -match '\d' }
function Crashed { [bool]((Sh 'logcat -d -v time *:E') | Select-String 'FATAL EXCEPTION|Process: com.gnyaneshwar.rideconnectx') }
$script:fakeBroken = $null   # set when the reflection-built fake fails: fake-based checks become NOT TESTABLE
function Check-Fake {
  $l = (Sh 'logcat -d -v time') | Select-String -Pattern '\[FAKE\] failed|listener not bound' | Select-Object -Last 1
  if ($l) { $script:fakeBroken = $l.Line.Trim() }
}
function Main-Screens([string]$tag) {   # current screen, then each bottom-bar tab that can be found
  Shot "50-$tag-current"
  foreach ($t in 'Home', 'Rides', 'Service', 'Settings') { if (Tap-Text $t) { Start-Sleep 2; Shot "50-$tag-$t" } }
}

function Start-Emu([switch]$Cold) {
  $a = @('-avd', $Avd, '-no-snapshot-save', '-no-audio')
  if ($Cold) { $a += '-no-snapshot-load' }
  Start-Process $emu -ArgumentList $a -WindowStyle Minimized | Out-Null
  $until = (Get-Date).AddMinutes(4)
  while ((Get-Date) -lt $until) {   # find the new emulator-55xx and wait for boot
    $s = ((& $adb devices) | Select-String 'emulator-\d+\s+device' | Select-Object -First 1)
    if ($s) {
      $script:serial = ($s.Matches[0].Value -split '\s+')[0]
      if ((Sh 'getprop sys.boot_completed').Trim() -eq '1') { return $true }
    }
    Start-Sleep 5
  }
  return $false
}

try {
  Log "starting $Avd"
  if (-not (Start-Emu)) {
    Log 'quick boot failed, cold boot'
    if ($script:serial) { A emu kill | Out-Null }; Start-Sleep 8
    if (-not (Start-Emu -Cold)) { throw 'emulator did not boot' }
  }
  Log "serial $script:serial, Android $((Sh 'getprop ro.build.version.release').Trim()) API $((Sh 'getprop ro.build.version.sdk').Trim())"
  $sdkInt = [int](Sh 'getprop ro.build.version.sdk').Trim()
  # same rule as BleModule.isEmulator(): debug build on such a device uses BleRepositorySimulatorImpl
  $hw = (Sh 'getprop ro.hardware').Trim(); $prod = (Sh 'getprop ro.product.name').Trim(); $fp = (Sh 'getprop ro.build.fingerprint').Trim()
  $isSim = (@('ranchu', 'goldfish') -contains $hw) -or $prod.StartsWith('sdk') -or $fp.Contains('generic')
  Add-Content $resf $(if ($isSim) { 'Build path: simulated scooter (BleRepositorySimulatorImpl)' } else { 'Build path: real BLE (BleRepositoryImpl); simulator-only NOT TESTABLE does not apply' })
  $abi = (Sh 'getprop ro.product.cpu.abi').Trim(); $page = ((Sh 'getconf PAGE_SIZE') -join '').Trim()
  $play = ((Sh 'pm list packages com.android.vending') -join '') -match 'com.android.vending'
  Add-Content $resf "Device: AVD=$Avd serial=$script:serial Android $((Sh 'getprop ro.build.version.release').Trim()) API $sdkInt abi=$abi page_size=$page"
  Add-Content $resf $(if ($play) { 'Image: has Play Store' } else { 'Image: NO Play Store (google_apis only); Play-only items are not testable here' })
  Add-Content $resf $(if ($page -eq '16384') { 'Page size 16 KB: native libs must be 16 KB aligned' } else { 'Page size 4 KB' })
  Add-Content $resf $(if ($sdkInt -ge 34) { 'API 34+: foreground service type rules enforced (type must be declared and its permission held)' } else { 'API below 34: foreground service type not enforced at start' })

  # install with the explicit serial (a bare installDebug would hit the real phone)
  $env:ANDROID_SERIAL = $script:serial
  Push-Location $root; try { & .\gradlew.bat installDebug -q } finally { Pop-Location }
  if ($LASTEXITCODE -ne 0) { throw 'installDebug failed' }

  # permissions: first DENY (the app must explain and recover), screenshot, then ALLOW
  $perms = @('android.permission.ACCESS_FINE_LOCATION', 'android.permission.ACCESS_COARSE_LOCATION')
  if ($sdkInt -ge 31) { $perms += 'android.permission.BLUETOOTH_SCAN', 'android.permission.BLUETOOTH_CONNECT' }
  if ($sdkInt -ge 33) { $perms += 'android.permission.POST_NOTIFICATIONS' }
  foreach ($p in $perms) { Sh "pm revoke $pkg $p" | Out-Null }
  Sh "cmd notification disallow_listener $lst" | Out-Null
  Sh "cmd appops set $pkg RUN_IN_BACKGROUND deny" | Out-Null
  Sh "am start -n $pkg/com.eshwar.rideconnectx.MainActivity" | Out-Null; Start-Sleep 8
  Shot '01-first-open-all-denied'
  Log 'denied state captured; now allowing'
  foreach ($p in $perms) { Sh "pm grant $pkg $p" | Out-Null }
  Sh "cmd appops set $pkg RUN_IN_BACKGROUND allow" | Out-Null
  Sh "cmd notification allow_listener $lst" | Out-Null
  Sh "dumpsys deviceidle whitelist +$pkg" | Out-Null            # battery optimisation off
  Sh "am force-stop $pkg" | Out-Null
  Sh "am start -n $pkg/com.eshwar.rideconnectx.MainActivity" | Out-Null; Start-Sleep 8
  Shot '02-after-allow'

  # ---- (5) PAIRING with the simulated scooter (debug build) ----
  # Walk first-run setup as guest, then Connect / Disconnect / Connect / force-stop + reopen.
  # Texts are from strings.xml and BlePairingScreen.kt ("Paired Successfully!", "Connect", "Disconnect").
  try {
    for ($i = 0; $i -lt 25; $i++) {
      $nodes = Get-Nodes
      if (Find-Node 'Connect|Disconnect|Paired Successfully!' $nodes) { break }
      # Create Profile: Rider's Name and Nickname are both required (Location is optional, 3rd field)
      $edits = @($nodes | Where-Object { $_.class -eq 'android.widget.EditText' } | Select-Object -First 2)
      $typed = $false
      foreach ($edit in $edits) { if (-not $edit.text) { Tap-Node $edit; Sh 'input text Tester' | Out-Null; Sh 'input keyevent 111' | Out-Null; Start-Sleep 1; $typed = $true } }
      # close the keyboard so the model picker and Continue are reachable; BACK only while the keyboard is shown (else it leaves the screen)
      if ($typed -and ((Sh 'dumpsys input_method') -join '') -match 'mInputShown=true') { Sh 'input keyevent 4' | Out-Null; Start-Sleep 1 }
      if (Tap-Text 'Choose your model') { Start-Sleep 2; Tap-Text 'Access 125' | Out-Null; Start-Sleep 2; Tap-Text 'Pearl Mat Aqua Silver|Solid Ice Green|Pearl Shiny Beige|Pearl Grace White' | Out-Null; Log 'setup: model Access 125 + a colour'; Start-Sleep 2; continue }
      # Create Profile keeps Continue disabled until the terms box is ticked
      $agree = $nodes | Where-Object { $_.checkable -eq 'true' -and $_.checked -eq 'false' } | Select-Object -First 1
      # tap the box at the row's left edge: the middle of the row is the Terms link, which opens the Terms page
      if ($agree) { $ab = [regex]::Match($agree.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]'); Sh "input tap $([int]$ab.Groups[1].Value + 35) $([int](([int]$ab.Groups[2].Value + [int]$ab.Groups[4].Value) / 2))" | Out-Null; Log 'setup tap: terms checkbox'; Start-Sleep 1 }
      $tapped = $false
      foreach ($re in 'Continue as Guest', 'Get Started', 'Let.s Go', 'Skip', 'Next', 'Continue', 'Allow', 'While using the app', 'Open Pair Your Vehicle', 'Pair', 'Scan|Retry') {
        if (Tap-Text $re) { Log "setup tap: $re"; $tapped = $true; break }
      }
      # model picker and Continue sit below the fold on Create Profile
      if (-not $tapped -or $edits.Count) { Sh 'input swipe 540 1600 540 500 300' | Out-Null }
      Start-Sleep 3
    }
    Shot '03-pairing-screen'
    if (-not (Tap-Text 'Connect')) { Tap-Text 'Scan|Retry' | Out-Null; Start-Sleep 5; Tap-Text 'Connect' | Out-Null }
    $paired = Wait-Text 'Paired Successfully!' 40
    Shot '04-paired'
    if ($paired) { Res 'pair simulated scooter: Paired Successfully appears' 'PASS' }
    else { Res 'pair simulated scooter: Paired Successfully appears' 'FAIL' "text not seen in uiautomator dump within 40 s; screen: $(Screen-Texts)" }
    if ($paired) {
      $dOk = (Tap-Text 'Disconnect') -and (Wait-Text 'Connect|.*Disconnected.*' 20)
      Res 'disconnect from pairing screen' $(if ($dOk) { 'PASS' } else { 'FAIL' }) $(if (-not $dOk) { "Disconnect or Disconnected state not seen; screen: $(Screen-Texts)" } else { '' })
      Tap-Text 'Connect' | Out-Null
      $again = Wait-Text 'Paired Successfully!' 40
      Res 'reconnect after disconnect' $(if ($again) { 'PASS' } else { 'FAIL' }) $(if (-not $again) { "screen: $(Screen-Texts)" } else { '' })
      Sh "am force-stop $pkg" | Out-Null; Start-Sleep 2
      Sh "am start -n $pkg/com.eshwar.rideconnectx.MainActivity" | Out-Null
      # shortcut: reconnect is judged by on-screen text; the exact dashboard wording ('Connected', strings.xml) is a guess
      $re = Wait-Text 'Connected|Paired Successfully!|Disconnect' 60
      Shot '05-after-force-stop-reopen'
      # debug emulator uses BleRepositorySimulatorImpl: no saved address, reconnectLastDevice() is a no-op (see done/20261026-ble-forcestop-reconnect.md)
      if ($isSim) { Res 'force-stop, reopen: reconnects by itself' 'NOT TESTABLE' 'debug emulator uses BleRepositorySimulatorImpl: no saved address, reconnectLastDevice() is a no-op; real path needs phone + scooter (N28)' }
      else { Res 'force-stop, reopen: reconnects by itself' $(if ($re) { 'PASS' } else { 'FAIL' }) "reconnect text not seen within 60 s; screen: $(Screen-Texts)" }
    }
  } catch { Res 'pairing flow' 'FAIL' "script error: $($_.Exception.Message)" }
  Sh 'logcat -c' | Out-Null

  # fake notifications, one per step, screenshot after each (presets live in NavTestReceiver.FAKES)
  $n = 0
  foreach ($f in 'old', 'liveA', 'liveB', 'liveLong', 'head', 'reroute', 'arriving', 'arrived', 'starting',
                 'callring', 'callans', 'callmiss', 'msg') {
    $n++; Fake "--es s $f"; Start-Sleep 3
    Shot ('10-fake-{0:d2}-{1}' -f $n, $f)
    Log "fake $f"
  }
  Fake '--es s callring --ez remove true'
  Check-Fake
  if ($script:fakeBroken) { Res 'fake notifications (13 presets)' 'NOT TESTABLE' "fake could not be built or posted: $script:fakeBroken" }
  else { Res 'fake notifications (13 presets)' 'PASS' 'all posted without [FAKE] failed; what the app did is in screenshots 10-fake-* and logcat.txt (not auto-judged)' }

  # GPS ride: a live-update frame first (starts the GPS countdown), then moving fixes (emu geo fix <lon> <lat>)
  Fake '--es s liveB'; Start-Sleep 2
  $lat = 17.3850; $lon = 78.4867                                  # test coordinates only
  for ($i = 0; $i -lt 12; $i++) {
    A emu geo fix ($lon + $i * 0.0003) ($lat + $i * 0.0002) | Out-Null; Start-Sleep 2
  }
  Shot '20-after-gps-ride'

  # smoothness: reset counters, drive swipes on whatever screen is on top, dump per round
  $m = [regex]::Match((Sh 'wm size'), '(\d+)x(\d+)')
  $x = [int]([int]$m.Groups[1].Value / 2); $y1 = [int]([int]$m.Groups[2].Value * 0.75); $y2 = [int]([int]$m.Groups[2].Value * 0.25)
  foreach ($screen in 'home', 'rides', 'settings') {   # round 2 and 3 follow Back
    Sh "dumpsys gfxinfo $pkg reset" | Out-Null
    for ($k = 0; $k -lt 6; $k++) { Sh "input swipe $x $y1 $x $y2 250" | Out-Null; Sh "input swipe $x $y2 $x $y1 250" | Out-Null }
    Shot "30-scroll-$screen"
    Sh "dumpsys gfxinfo $pkg" | Set-Content (Join-Path $out "gfxinfo-$screen.txt")
    Sh 'input keyevent KEYCODE_BACK' | Out-Null
  }
  Sh "am start -n $pkg/com.eshwar.rideconnectx.MainActivity" | Out-Null; Start-Sleep 3   # back to the app after the Back presses

  # ---- (1) DARK MODE ----
  try {
    Sh 'cmd uimode night yes' | Out-Null; Start-Sleep 4; Main-Screens 'dark'
    $ok = (Alive) -and -not (Crashed)
    Sh 'cmd uimode night no' | Out-Null; Start-Sleep 4; Main-Screens 'light'
    Res 'dark mode (night yes, then no)' $(if ($ok -and (Alive)) { 'PASS' } else { 'FAIL' }) 'PASS = app alive, no crash; screenshots 50-dark-*, 50-light-*; how it LOOKS is judged by eye, not proven'
  } catch { Res 'dark mode' 'FAIL' "script error: $($_.Exception.Message)" }
  finally { Sh 'cmd uimode night no' | Out-Null }

  # ---- (2) LARGE TEXT: 1.3 and 2.0 ----
  try {
    $ok = $true
    foreach ($fs in '1.3', '2.0') { Sh "settings put system font_scale $fs" | Out-Null; Start-Sleep 4; Main-Screens "font$fs"; $ok = $ok -and (Alive) -and -not (Crashed) }
    Res 'large text 1.3 and 2.0' $(if ($ok) { 'PASS' } else { 'FAIL' }) 'PASS = app alive, no crash; screenshots 50-font*; cut-off or overlapping text is judged by eye, not proven'
  } catch { Res 'large text' 'FAIL' "script error: $($_.Exception.Message)" }
  finally { Sh 'settings put system font_scale 1.0' | Out-Null }

  # ---- (3) ROTATION ----
  try {
    Sh 'settings put system accelerometer_rotation 0' | Out-Null
    $ok = $true
    foreach ($r in 1, 0) {
      Sh "settings put system user_rotation $r" | Out-Null; Start-Sleep 4; Shot "51-rotation-$r"
      $ok = $ok -and (Alive) -and -not (Crashed)
    }
    Res 'rotation landscape then portrait' $(if ($ok) { 'PASS' } else { 'FAIL' }) 'PASS = process alive and no FATAL EXCEPTION in logcat; screenshots 51-rotation-*; layout quality judged by eye'
  } catch { Res 'rotation' 'FAIL' "script error: $($_.Exception.Message)" }
  finally { Sh 'settings put system user_rotation 0' | Out-Null; Sh 'settings put system accelerometer_rotation 1' | Out-Null }

  # ---- (4) SCREEN OFF: fake live update (decreasing distance) + GPS fix every 60 s, then check service and packet stream ----
  if ($ScreenOffMinutes -gt 0) {
    try {
      Sh 'logcat -c' | Out-Null
      Sh 'input keyevent KEYCODE_SLEEP' | Out-Null; Log "screen off $ScreenOffMinutes min"
      $dist = 500
      for ($k = 0; $k -lt $ScreenOffMinutes; $k++) {
        Fake "--es s liveB --es title `"$dist m - Turn left`""; $dist = [Math]::Max(20, $dist - 40)
        A emu geo fix ($lon + (20 + $k) * 0.0003) ($lat + (20 + $k) * 0.0002) | Out-Null
        Start-Sleep 60
      }
      Sh 'input keyevent KEYCODE_WAKEUP' | Out-Null; Start-Sleep 1; Sh 'input swipe 540 1800 540 600 300' | Out-Null; Start-Sleep 2
      Shot '40-after-screen-off'
      $svc = (Sh "dumpsys activity services $pkg") -join "`n"; Set-Content (Join-Path $out 'services-after-screen-off.txt') $svc
      $fgt = [regex]::Match($svc, 'foregroundServiceType=(0x[0-9a-fA-F]+|\d+)').Groups[1].Value
      $isFg = $svc -match 'isForeground=true'
      Res "screen off $ScreenOffMinutes min: foreground service still running" $(if ($isFg) { 'PASS' } elseif ($isSim) { 'NOT TESTABLE' } else { 'FAIL' }) "$(if ($isSim) { 'simulator never starts BleForegroundService; ' })isForeground=$isFg, foregroundServiceType=$fgt (manifest: connectedDevice|location, expect 0x18 = 0x10 + 0x8)"
      # packet stream: TX / SIM FRAME lines; gap = seconds between neighbours (logcat -v time starts 'MM-DD HH:MM:SS.mmm')
      $tx = @((Sh 'logcat -d -v time') | Select-String -Pattern 'SIM FRAME|TX model=' | ForEach-Object { [datetime]::ParseExact($_.Line.Substring(0, 18), 'MM-dd HH:mm:ss.fff', [Globalization.CultureInfo]::InvariantCulture) })
      $maxGap = 0; for ($i = 1; $i -lt $tx.Count; $i++) { $g = ($tx[$i] - $tx[$i - 1]).TotalSeconds; if ($g -gt $maxGap) { $maxGap = $g } }
      $span = 0; if ($tx.Count -gt 1) { $span = ($tx[-1] - $tx[0]).TotalSeconds }
      $okTx = ($tx.Count -gt 1) -and ($maxGap -le $GapThresholdSec) -and ($span -ge ($ScreenOffMinutes * 60 * 0.8))
      Res 'screen off: packets kept being sent (TX / SIM FRAME lines)' $(if ($okTx) { 'PASS' } elseif ($isSim) { 'NOT TESTABLE' } else { 'FAIL' }) "$(if ($isSim) { 'simulator is Idle after force-stop and sends no heartbeat; ' })lines=$($tx.Count), longest gap=$([int]$maxGap) s (limit $GapThresholdSec s, my pick), first-to-last span=$([int]$span) s"
      Check-Fake
      if ($script:fakeBroken) { Res 'screen off: live-update fakes reached the app' 'NOT TESTABLE' "fake failed: $script:fakeBroken" }
    } catch { Res 'screen off' 'FAIL' "script error: $($_.Exception.Message)" }
  } else { Res 'screen off' 'NOT TESTABLE' 'ScreenOffMinutes was 0' }

  # ---- (6) SAMSUNG dialer branch: fake callType 2 + fullScreenIntent from com.samsung.android.dialer ----
  try {
    Sh 'logcat -c' | Out-Null
    Fake '--es s callsamsung'; Start-Sleep 4
    $lc = @((Sh 'logcat -d -v time') | Select-String -Pattern 'IGNORED|untrusted|\[FAKE\]|CALL ' | ForEach-Object { $_.Line.Trim() })
    $lc | Set-Content (Join-Path $out 'samsung-call-log.txt')
    Check-Fake
    $isSys = ((Sh 'pm path com.samsung.android.dialer') -join '') -match 'package:'
    if ($script:fakeBroken) { Res 'Samsung dialer call branch' 'NOT TESTABLE' "fake failed: $script:fakeBroken" }
    elseif (($lc -join "`n") -match 'IGNORED \(not a trusted dialer\)') {
      Res 'Samsung dialer call branch' 'NOT TESTABLE' "app trusts com.samsung.android.dialer only as a SYSTEM app (MapsNotificationListener.isTrustedCallSource); installed here: $isSys, so the call was IGNORED. Trust check not weakened. Needs a real Samsung phone."
    }
    elseif ($isSys) { Res 'Samsung dialer call branch' 'PASS' 'Samsung system dialer present and the call was not ignored; see samsung-call-log.txt' }
    else { Res 'Samsung dialer call branch' 'FAIL' 'no IGNORED line and no Samsung system dialer; see samsung-call-log.txt' }
    Fake '--es s callsamsung --ez remove true'
  } catch { Res 'Samsung dialer call branch' 'FAIL' "script error: $($_.Exception.Message)" }

  # ---- (7) fake-based items must never vanish silently ----
  Check-Fake
  if ($script:fakeBroken) { Res 'ALL fake-notification items (turns, calls, messages, live updates)' 'NOT TESTABLE' "reflection-built fake failed on API ${sdkInt}: $script:fakeBroken" }

  # logcat: app + crashes
  (Sh 'logcat -d -v time') | Select-String -Pattern 'RCX|rideconnectx|AndroidRuntime|ANR in|StrictMode|FATAL|SecurityException|ForegroundServiceStartNotAllowed' |
    Set-Content (Join-Path $out 'logcat.txt')
  Log "logcat lines: $((Get-Content (Join-Path $out 'logcat.txt')).Count)"
}
catch { Res 'script' 'FAIL' "stopped by error: $($_.Exception.Message)" }
finally {
  if ($script:serial) { & $adb -s $script:serial emu kill 2>$null | Out-Null }
  Start-Sleep 8
  Get-Process -Name 'qemu-system*' -ErrorAction SilentlyContinue | ForEach-Object { Log "killing leftover $($_.Name)"; $_ | Stop-Process -Force }
  Log "emulator $Avd shut down"
}
