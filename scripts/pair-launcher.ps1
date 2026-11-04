# One-time: let the launcher pair with the Chromecast's own Wireless debugging so it can run
# shell commands (CEC, Wi-Fi, enabling/disabling the stock launcher) by itself.
#
# On the TV: Developer options > Wireless debugging > Pair device with pairing code.
# Then run:  .\scripts\pair-launcher.ps1 -Port <pairing port> -Code <6-digit code>
param([Parameter(Mandatory)] [int] $Port, [Parameter(Mandatory)] [string] $Code, [string] $Target)

. "$PSScriptRoot\common.ps1"
Connect-Tv -Target $Target

Invoke-Adb logcat -c
Invoke-Adb shell am broadcast -a "$Package.PAIR" -n "$Package/.shell.PairReceiver" --ei port $Port --es code $Code | Out-Null

Write-Host 'Waiting for the launcher to pair...'
$deadline = (Get-Date).AddSeconds(30)
while ((Get-Date) -lt $deadline) {
    $log = & $script:Adb -s $script:Target logcat -d -s PairReceiver:I ShellClient:*
    if ($log -match 'post-pair shell check: shell-ok') { Write-Host 'Paired. The launcher now has its own shell.'; exit 0 }
    if ($log -match 'pairing on port \d+: (?!OK)(.+)') { $log | Select-Object -Last 10; throw 'Pairing failed.' }
    Start-Sleep -Seconds 1
}
& $script:Adb -s $script:Target logcat -d -s PairReceiver:* ShellClient:* | Select-Object -Last 20
throw 'Timed out waiting for pairing.'
