# Emergency undo, from the PC: put the Chromecast back on the stock Google TV launcher.
# Works even if the launcher app is broken or uninstalled.
#   .\scripts\revert.ps1                 restore stock launcher, keep this app installed
#   .\scripts\revert.ps1 -Uninstall      also remove this app
param([string] $Ip, [string] $Target, [switch] $Uninstall)

. "$PSScriptRoot\common.ps1"
Connect-Tv -Ip $Ip -Target $Target

foreach ($pkg in 'com.google.android.apps.tv.launcherx', 'com.google.android.tungsten.setupwraith') {
    & $script:Adb -s $script:Target shell pm enable $pkg | Out-Null
    Write-Host "Enabled $pkg"
}

# Drop our accessibility service (Home redirect) but keep any others the user has.
$services = (& $script:Adb -s $script:Target shell settings get secure enabled_accessibility_services).Trim()
if ($services -and $services -ne 'null') {
    $kept = ($services -split ':' | Where-Object { $_ -and $_ -notlike "$Package/*" }) -join ':'
    if ($kept) { Invoke-Adb shell settings put secure enabled_accessibility_services $kept }
    else { Invoke-Adb shell settings delete secure enabled_accessibility_services | Out-Null }
}

# Restore Google TV's ambient-mode screensaver.
Invoke-Adb shell settings put secure screensaver_components 'com.google.android.apps.tv.dreamx/.service.Backdrop'

if ($Uninstall) {
    Invoke-Adb uninstall $Package
    Write-Host "Uninstalled $Package"
} else {
    # Make sure the launcher doesn't redirect Home again next time it starts.
    Invoke-Adb shell am force-stop $Package
}

Invoke-Adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME -p com.google.android.apps.tv.launcherx | Out-Null
Write-Host 'Back on the Google TV launcher.'
