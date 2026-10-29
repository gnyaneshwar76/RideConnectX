# For each test AVD: boot, record the real Google Maps notification, remove Google accounts, shut down.
# One emulator at a time. Results: .claude\team\reports\maps-capture\<label>\ and maps-all-log.txt
param([string[]]$Only, [switch]$RemoveAccounts)   # accounts stay unless -RemoveAccounts is given (owner's rule)
if ($Only) { $Only = @($Only -split ',') }   # -File passes "A12,A13" as one string
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emu = Join-Path $sdk 'emulator\emulator.exe'
$root = Split-Path $PSScriptRoot -Parent
$bash = 'C:\Program Files\Git\bin\bash.exe'
$log = Join-Path $root '.claude\team\reports\maps-capture\maps-all-log.txt'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
function L($m) { $l = "$(Get-Date -Format HH:mm:ss) $m"; Write-Host $l; Add-Content $log $l }
$list = @(
  @('A11', 'Pixel_4'), @('A12', 'Pixel_4_3'), @('A13', 'Pixel_6'), @('A14', 'Pixel_7'),
  @('A15', 'Pixel_8'), @('A16', 'Pixel_10_Pro_2'), @('A17', 'Pixel_10_Pro'))
foreach ($e in $list) {
  $label = $e[0]; $avd = $e[1]
  if ($Only -and ($Only -notcontains $label)) { continue }
  L "=== $label ${avd}: starting"
  if (Get-Process -Name 'qemu-system*' -ErrorAction SilentlyContinue) { L 'another emulator is running, stopping'; & $adb emu kill | Out-Null; Start-Sleep 8 }
  Start-Process -FilePath $emu -ArgumentList '-avd', $avd, '-no-boot-anim' -WindowStyle Normal
  $ok = $false
  for ($i = 0; $i -lt 120; $i++) {
    Start-Sleep 4
    $b = (& $adb shell getprop sys.boot_completed 2>$null) -join ''
    if ($b.Trim() -eq '1') { $ok = $true; break }
  }
  if (-not $ok) { L "$label boot FAILED"; & $adb emu kill | Out-Null; continue }
  Start-Sleep 25
  L "$label booted, api $((& $adb shell getprop ro.build.version.sdk) -join '')"
  try {
    & $bash (Join-Path $root 'tools\maps-capture.sh') $label 2>&1 | Out-Null
    L "$label capture done"
    if ($RemoveAccounts) { $r = & $bash (Join-Path $root 'tools\remove-accounts.sh') 2>&1 | Select-Object -Last 1; L "$label $r" } else { L "$label accounts left in place" }
  } catch { L "$label ERROR $($_.Exception.Message)" }
  & $adb shell svc power stayon false | Out-Null
  & $adb emu kill | Out-Null
  for ($i = 0; $i -lt 30; $i++) { Start-Sleep 2; if (-not (Get-Process -Name 'qemu-system*' -ErrorAction SilentlyContinue)) { break } }
  L "$label shut down"
}
L '=== ALL DONE'
