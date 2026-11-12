$state=Join-Path $env:TEMP 'drivemate-console-gps'
Set-Content -LiteralPath "$state.stop" -Value 'stop'
Write-Host 'GPS simulation stop requested. Vehicle speed and gear remain under console control.'
