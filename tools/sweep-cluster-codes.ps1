# Steps through cluster maneuver codes so a human can record what each
# one draws on the dashboard.
#
# Which Mappls maneuver maps to which cluster code is recovered from the
# official app. What each cluster code *renders* lives in dashboard firmware and
# is written down nowhere — so it gets established by looking at it.
#
# Usage:  .\sweep-cluster-codes.ps1                 (codes 1..52)
#         .\sweep-cluster-codes.ps1 -From 38 -To 53 (a narrower range)
#         .\sweep-cluster-codes.ps1 -Hold 6         (longer per code)

param(
  [int]$From = 1,
  [int]$To   = 53,
  [int]$Hold = 4,
  [int]$Dist = 200
)

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$cmp = "com.gnyaneshwar.rideconnectx/com.eshwar.rideconnectx.debug.NavTestReceiver"

Write-Host ""
Write-Host "Cluster maneuver sweep: codes $From..$To, ${Hold}s each" -ForegroundColor Cyan
Write-Host "Watch the dashboard. Note what each code shows." -ForegroundColor Cyan
Write-Host ""

$results = @()
for ($code = $From; $code -le $To; $code++) {
  Write-Host ("  code {0,3}  ->  " -f $code) -NoNewline -ForegroundColor Yellow

  # Re-send through the hold window: the cluster expects a repeating stream,
  # a single write can be missed.
  $deadline = (Get-Date).AddSeconds($Hold)
  while ((Get-Date) -lt $deadline) {
    & $adb shell "am broadcast -n $cmp -a com.eshwar.rideconnectx.TEST_CODE --ei code $code --ei dist $Dist" | Out-Null
    Start-Sleep -Milliseconds 700
  }

  $seen = Read-Host "what did the dashboard show?"
  $results += [PSCustomObject]@{ Code = $code; Shows = $seen }
}

$out = Join-Path $PSScriptRoot "cluster-codes.csv"
$results | Export-Csv -NoTypeInformation $out
Write-Host ""
Write-Host "Saved to $out" -ForegroundColor Green
