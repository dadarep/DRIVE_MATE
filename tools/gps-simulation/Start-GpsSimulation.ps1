param([string]$Origin="37.48465,127.03415",[string]$Destination="37.49795,127.02762",[switch]$FollowDebugApp)
$ErrorActionPreference='Stop'
$state=Join-Path $env:TEMP 'drivemate-console-gps'
$pidFile="$state.pid"
if(Test-Path -LiteralPath $pidFile){
    $existingPid=[int](Get-Content -LiteralPath $pidFile)
    if(Get-Process -Id $existingPid -ErrorAction SilentlyContinue){Write-Host 'GPS simulation is already running.';exit}
    Remove-Item -LiteralPath $pidFile
}
if(Test-Path -LiteralPath "$state.stop"){Remove-Item -LiteralPath "$state.stop"}
$python=(Get-Command python.exe).Source
$script=Join-Path $PSScriptRoot 'console_gps_sim.py'
$simulationArgs=@('"'+$script+'"')
if(-not $FollowDebugApp){$simulationArgs+=@('--origin',$Origin,'--destination',$Destination)}
Start-Process -FilePath $python -ArgumentList $simulationArgs -WindowStyle Hidden -WorkingDirectory $PSScriptRoot
Write-Host 'GPS simulation started. Use D and the accelerator in the Pleos console. Use P to park.'
Write-Host "Log: $state.log"

if(-not $FollowDebugApp){Write-Host 'Fixed demo route: Yangjae -> Gangnam by default. Choose Gangnam in the app. App private data is not accessed.'}
