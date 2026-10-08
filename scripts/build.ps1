$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$localJdk = Get-ChildItem -Path (Join-Path $projectRoot '.tools\jdk-lite-extracted') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
if (-not $env:JAVA_HOME) { throw 'Install JDK 17 and set JAVA_HOME, or run scripts/download_toolchain.py.' }
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-home'
if (Test-Path -LiteralPath (Join-Path $projectRoot '.tools\sdk')) { $env:ANDROID_HOME = Join-Path $projectRoot '.tools\sdk' }
& (Join-Path $projectRoot 'gradlew.bat') --no-daemon assembleDebug testDebugUnitTest lintDebug @args
exit $LASTEXITCODE
