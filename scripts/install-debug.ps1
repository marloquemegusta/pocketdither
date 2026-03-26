param(
    [string]$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)

$ErrorActionPreference = "Stop"

$javaHome = "C:\Program Files\Android\Android Studio\jbr"
if (Test-Path $javaHome) {
    $env:JAVA_HOME = $javaHome
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

if (-not (Test-Path $AdbPath)) {
    throw "adb not found at '$AdbPath'."
}

Write-Host "Building debug APK..."
.\gradlew.bat assembleDebug

$apkPath = ".\app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apkPath)) {
    throw "Debug APK not found at '$apkPath'."
}

Write-Host "Installing APK..."
& $AdbPath install -r $apkPath

Write-Host "Launching app..."
& $AdbPath shell am start -n com.arquimea.dithercamera/.MainActivity
