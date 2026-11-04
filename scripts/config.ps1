# Copy the launcher's settings backup between the PC and the Chromecast.
#   .\scripts\config.ps1 pull     export on the TV first (Settings > Home & system > Export)
#   .\scripts\config.ps1 push     then Settings > Home & system > Import on the TV
param([Parameter(Mandatory)] [ValidateSet('pull', 'push')] [string] $Direction, [string] $File)

. "$PSScriptRoot\common.ps1"
Connect-Tv

$remote = "/sdcard/Android/data/$Package/files/launcher-config.json"
if (-not $File) { $File = Join-Path (Split-Path $PSScriptRoot -Parent) 'launcher-config.json' }

if ($Direction -eq 'pull') {
    Invoke-Adb pull $remote $File
    Write-Host "Saved to $File"
} else {
    if (-not (Test-Path $File)) { throw "$File not found" }
    Invoke-Adb push $File $remote
    Write-Host 'Pushed. On the TV: Settings > Home & system > Import settings.'
}
