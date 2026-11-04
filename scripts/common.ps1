# Shared helpers for the deploy/pair/revert scripts. Dot-source it: . "$PSScriptRoot\common.ps1"

$ErrorActionPreference = 'Stop'
$script:Adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$script:Package = 'dev.jcob.launcher'
$script:TargetFile = Join-Path $PSScriptRoot '.target'

# Uses $args rather than a param block so adb flags like -a/-n aren't bound as PowerShell parameters.
function Invoke-Adb {
    & $script:Adb -s $script:Target @args
    if ($LASTEXITCODE -ne 0) { throw "adb $($args -join ' ') failed ($LASTEXITCODE)" }
}

function Test-Connected([string] $target) {
    (& $script:Adb devices) -match "^$([regex]::Escape($target))\s+device$"
}

# Wireless debugging uses a random port that changes on reboot, so remember the last one
# that worked and fall back to scanning the device for it.
function Connect-Tv {
    param([string] $Ip, [string] $Target)
    if (-not $Target -and (Test-Path $script:TargetFile)) { $Target = (Get-Content $script:TargetFile).Trim() }
    if (-not $Ip -and $Target) { $Ip = $Target.Split(':')[0] }
    if (-not $Ip) { throw 'Pass -Ip <chromecast ip> the first time.' }

    if ($Target -and ((Test-Connected $Target) -or ((& $script:Adb connect $Target) -match '^connected'))) {
        $script:Target = $Target
    } else {
        Write-Host "Scanning $Ip for the Wireless debugging port..."
        $script:Target = $null
        foreach ($start in (30000..49999 | Where-Object { $_ % 2000 -eq 0 })) {
            $probes = $start..($start + 1999) | ForEach-Object {
                $c = New-Object System.Net.Sockets.TcpClient
                [pscustomobject]@{ Port = $_; Task = $c.ConnectAsync($Ip, $_); Client = $c }
            }
            Start-Sleep -Milliseconds 700
            $open = $probes | Where-Object { $_.Task.Status -eq 'RanToCompletion' } | ForEach-Object Port
            $probes | ForEach-Object { $_.Client.Dispose() }
            foreach ($p in $open) {
                if ((& $script:Adb connect "${Ip}:$p") -match '^connected') { $script:Target = "${Ip}:$p"; break }
            }
            if ($script:Target) { break }
        }
        if (-not $script:Target) {
            throw "Couldn't connect. On the TV: Developer options > Wireless debugging must be ON. If this PC isn't paired, run: adb pair ${Ip}:<pair port> <code>"
        }
    }
    Set-Content $script:TargetFile $script:Target
    Write-Host "Connected to $script:Target"
}
