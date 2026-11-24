param([switch]$Gate,[string]$Report='qa/performance-2.0.txt')
$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
$adb='E:\androidsdk\platform-tools\adb.exe'
& $adb -s emulator-5554 shell am force-stop com.lumifocus.app.debug
$output=& $adb -s emulator-5554 shell am instrument -w -e perf true com.lumifocus.app.debug.test/com.lumifocus.app.SmokeInstrumentation
$output | Tee-Object -FilePath $Report
if(($output -join "`n") -match 'FAIL|Error='){throw 'Performance harness failed'}
$historyLine=$output | Where-Object {$_ -match '^PERF_HISTORY '}
$idleLine=$output | Where-Object {$_ -match '^PERF_IDLE '}
if(!$historyLine -or !$idleLine){throw 'Missing performance results'}
$history=($historyLine -replace '^PERF_HISTORY ','') | ConvertFrom-Json
$idle=($idleLine -replace '^PERF_IDLE ','') | ConvertFrom-Json
if($Gate -and ($history.hiddenStatsRenders -ne 0 -or !$idle.idle -or $idle.idlePollsIn3200ms -gt 1)){throw 'Unnecessary hidden statistics rendering or idle polling detected'}
