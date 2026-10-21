# Builds and signs the RideConnectX release APK.
#
#   powershell -ExecutionPolicy Bypass -File tools\build-release.ps1
#
# You type the key password once. It lives only in this process's environment
# while apksigner runs and is cleared at the end, also when something fails.
# Nothing secret is written to disk.
#
# Signing: APK Signature Scheme v3 key rotation. The APK carries a lineage
# "debug key -> release key", so a phone that has an older debug-signed build
# updates in place (no uninstall, data kept). A fresh install just sees the
# release key. The lineage file holds only public certificates and is created
# once next to the keystore; back it up with the keystore.
#
# Output: app\build\outputs\apk\release\RideConnectX-v<version>.apk (+ .sha256)

param(
    [string]$KeyStore = (Join-Path $PSScriptRoot '..\..\RideConnectX-keys\rideconnectx-release.jks'),
    [string]$KeyAlias = 'rideconnectx',
    [string]$DebugKeyStore = (Join-Path $env:USERPROFILE '.android\debug.keystore')
)

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')

foreach ($f in @($KeyStore, $DebugKeyStore)) {
    if (-not (Test-Path $f)) { throw "Not found: $f" }
}
$KeyStore = (Resolve-Path $KeyStore).Path
$lineage = Join-Path (Split-Path $KeyStore) 'rideconnectx-lineage.bin'

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$bt = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory |
    Sort-Object { [version]($_.Name -replace '-.*$', '') } | Select-Object -Last 1
$apksigner = Join-Path $bt.FullName 'apksigner.bat'
if (-not (Test-Path $apksigner)) { throw "apksigner not found in $($bt.FullName)" }

function Invoke-Checked([string]$exe, [string[]]$argv) {
    & $exe @argv
    if ($LASTEXITCODE -ne 0) { throw "$(Split-Path -Leaf $exe) failed (exit $LASTEXITCODE)" }
}

if ([Console]::IsInputRedirected) {
    # Piped input (scripted tests with a throwaway key); a person at the keyboard gets the hidden prompt.
    $secure = ConvertTo-SecureString ([Console]::In.ReadLine()) -AsPlainText -Force
} else {
    $secure = Read-Host -AsSecureString "Password for $KeyAlias in $(Split-Path -Leaf $KeyStore)"
}
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    Push-Location $root
    try { Invoke-Checked (Join-Path $root 'gradlew.bat') @('assembleRelease') } finally { Pop-Location }

    $outDir = Join-Path $root 'app\build\outputs\apk\release'
    $unsigned = Get-ChildItem $outDir -Filter 'RideConnectX-v*-unsigned.apk' |
        Sort-Object LastWriteTime | Select-Object -Last 1
    if (-not $unsigned) { throw "No unsigned APK in $outDir" }
    $signed = Join-Path $outDir ($unsigned.Name -replace '-unsigned\.apk$', '.apk')

    # Set only after Gradle ran, so the Gradle daemon never sees it.
    # Env var of this process only; apksigner reads it with --ks-pass env:RCX_KS_PASS.
    $env:RCX_KS_PASS = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)

    $old = @('--ks', $DebugKeyStore, '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'pass:android')
    $new = @('--ks', $KeyStore, '--ks-key-alias', $KeyAlias, '--ks-pass', 'env:RCX_KS_PASS')

    if (-not (Test-Path $lineage)) {
        Write-Host "Creating rotation lineage (debug key -> release key): $lineage"
        Invoke-Checked $apksigner (@('rotate', '--out', $lineage, '--old-signer') + $old + @('--new-signer') + $new)
    }

    # v1/v2 carry the debug key, v3 carries the release key plus the lineage.
    # --rotation-min-sdk-version 28 puts the rotation in the v3.0 block so every
    # Android version the app supports (9+) uses the release key.
    Invoke-Checked $apksigner (@('sign') + $old + @('--next-signer') + $new +
        @('--lineage', $lineage, '--rotation-min-sdk-version', '28', '--out', $signed, $unsigned.FullName))
    Invoke-Checked $apksigner @('verify', '--verbose', '--print-certs', $signed)

    $hash = (Get-FileHash $signed -Algorithm SHA256).Hash.ToLower()
    $name = Split-Path -Leaf $signed
    [IO.File]::WriteAllText("$signed.sha256", "$hash  $name`n")
    Write-Host "`nDone: $signed`nSHA-256: $hash"
}
finally {
    Remove-Item Env:RCX_KS_PASS -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    $secure.Dispose()
}
