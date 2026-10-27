# Runs tools\android-versions-run.ps1 on every test AVD, one after another (Android 10 to 17).
# Each run shuts its emulator down before the next starts. One failing AVD does not stop the rest.
$avds = 'Pixel_3_XL', 'Pixel_4', 'Pixel_5', 'Pixel_6', 'Pixel_7', 'Pixel_8', 'Pixel_10_Pro_2', 'Pixel_10_Pro'
foreach ($a in $avds) {
  & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'android-versions-run.ps1') -Avd $a
  Write-Host "=== $a finished (exit $LASTEXITCODE)"
}
Write-Host '=== ALL AVDS DONE'
