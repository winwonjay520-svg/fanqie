param(
    [string]$SdkRoot = 'E:\androidsdk',
    [string]$JdkPath = 'E:\androidstudio\jbr'
)
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectDir
$env:JAVA_HOME = $JdkPath
& .\gradlew.bat :app:assembleRelease :app:lintRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Android build failed' }
$signingDir = Join-Path $projectDir '.signing'
$outputDir = Join-Path $projectDir 'dist'
New-Item -ItemType Directory -Force $signingDir,$outputDir | Out-Null
$keyPath = Join-Path $signingDir 'lumi-release.jks'
$passPath = Join-Path $signingDir 'password.txt'
if (!(Test-Path -LiteralPath $passPath)) {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    [IO.File]::WriteAllText($passPath,[Convert]::ToBase64String($bytes))
}
$env:LUMI_SIGNING_PASSWORD = [IO.File]::ReadAllText($passPath)
try {
    if (!(Test-Path -LiteralPath $keyPath)) {
        & "$JdkPath\bin\keytool.exe" -genkeypair -keystore $keyPath -alias lumi -keyalg RSA -keysize 3072 -validity 10000 -storepass:env LUMI_SIGNING_PASSWORD -keypass:env LUMI_SIGNING_PASSWORD -dname 'CN=LumiFocus Developer, O=LumiFocus, C=CN'
        if ($LASTEXITCODE -ne 0) { throw 'Signing key generation failed' }
    }
    $toolsPath = Join-Path $SdkRoot 'build-tools\36.0.0'
    $aligned = Join-Path $outputDir 'lumi-aligned.apk'
    $apk = Join-Path $outputDir 'Fanqie-2.9.4.apk'
    & "$toolsPath\zipalign.exe" -f -p 4 app\build\outputs\apk\release\app-release-unsigned.apk $aligned
    if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed' }
    & "$toolsPath\apksigner.bat" sign --ks $keyPath --ks-key-alias lumi --ks-pass env:LUMI_SIGNING_PASSWORD --key-pass env:LUMI_SIGNING_PASSWORD --out $apk $aligned
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }
    & "$toolsPath\apksigner.bat" verify --verbose $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK verification failed' }
    Remove-Item -LiteralPath $aligned
    Get-FileHash -LiteralPath $apk -Algorithm SHA256 | Format-List
} finally {
    Remove-Item Env:LUMI_SIGNING_PASSWORD -ErrorAction SilentlyContinue
}
