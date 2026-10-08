param([string]$DeviceId = '')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot

if (-not $env:JAVA_HOME) {
    $jdkRoots = @(
        (Join-Path $projectRoot '.tools\jdk-lite-extracted'),
        'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Microsoft',
        'C:\Program Files\Java'
    )
    foreach ($jdkRoot in $jdkRoots) {
        $jdk = Get-ChildItem -LiteralPath $jdkRoot -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe') } |
            Select-Object -First 1
        if ($jdk) { $env:JAVA_HOME = $jdk.FullName; break }
    }
}
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    throw 'Android launch blocked: install JDK 17 and set JAVA_HOME. No APK has been built yet.'
}

$sdkCandidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk'), (Join-Path $projectRoot '.tools\sdk'))
$sdkRoot = $sdkCandidates | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'platform-tools\adb.exe')) } | Select-Object -First 1
if (-not $sdkRoot) { throw 'Android launch blocked: install SDK Platform 35, Build Tools 35.0.0, and Platform Tools with Android Studio SDK Manager.' }
$env:ANDROID_HOME = $sdkRoot
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-home'
$adbExecutable = Join-Path $sdkRoot 'platform-tools\adb.exe'
$connected = @(& $adbExecutable devices | Select-String '^([^\s]+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value })
if ($DeviceId -and $DeviceId -notin $connected) { throw "Device '$DeviceId' is not connected and authorized for debugging." }
if (-not $DeviceId) {
    if ($connected.Count -eq 0) { throw 'Start an Android emulator, or connect a phone and authorize USB debugging, then run this script again.' }
    if ($connected.Count -gt 1) { throw 'Multiple devices are connected. Run this script with -DeviceId followed by the desired adb device ID.' }
    $DeviceId = $connected[0]
}

& (Join-Path $projectRoot 'gradlew.bat') --no-daemon assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Build failed. The application was not installed.' }
$apkPath = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
& $adbExecutable -s $DeviceId install -r $apkPath
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
& $adbExecutable -s $DeviceId shell am start -n 'com.daylight.app/.MainActivity'
if ($LASTEXITCODE -ne 0) { throw 'Android could not launch the application.' }
