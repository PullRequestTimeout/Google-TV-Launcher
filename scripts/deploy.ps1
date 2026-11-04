# Build the launcher, install it on the Chromecast over Wi-Fi and grant its privileged permissions.
#   .\scripts\deploy.ps1 -Ip 10.0.0.93          first time
#   .\scripts\deploy.ps1                         afterwards (remembers the device)
#   .\scripts\deploy.ps1 -SkipBuild              reinstall the last build
param([string] $Ip, [string] $Target, [switch] $SkipBuild, [switch] $DebugBuild)

. "$PSScriptRoot\common.ps1"
$root = Split-Path $PSScriptRoot -Parent

if (-not $SkipBuild) {
    $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
    $task = if ($DebugBuild) { 'assembleDebug' } else { 'assembleRelease' }
    Push-Location $root
    try {
        & .\gradlew.bat $task --console=plain -q
        if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
    } finally { Pop-Location }
}
$variant = if ($DebugBuild) { 'debug' } else { 'release' }
$apk = Join-Path $root "app\build\outputs\apk\$variant\app-$variant.apk"

Connect-Tv -Ip $Ip -Target $Target
Invoke-Adb install -r $apk

# WRITE_SECURE_SETTINGS lets the app re-enable Wireless debugging after reboots and switch its
# own accessibility service / screensaver on without going through Settings.
Invoke-Adb shell pm grant $Package android.permission.WRITE_SECURE_SETTINGS
# Needed to read the connected Wi-Fi network name.
Invoke-Adb shell pm grant $Package android.permission.ACCESS_FINE_LOCATION
# Don't let Android revoke adb keys (ours and the launcher's) after 7 days unused.
Invoke-Adb shell settings put global adb_allowed_connection_time 0
# Sleep timer fires on the minute rather than whenever the system gets round to it.
Invoke-Adb shell appops set $Package SCHEDULE_EXACT_ALARM allow

# am start writes "already running" to stderr, which isn't a failure.
$ErrorActionPreference = 'Continue'
& $script:Adb -s $script:Target shell am start -n "$Package/.MainActivity" 2>&1 | Out-Null
Write-Host 'Deployed.'
