param([switch]$ResetDebugData,[string]$SdkRoot='E:\androidsdk',[string]$JdkPath='E:\androidstudio\jbr')
$ErrorActionPreference='Stop'
if(!$ResetDebugData){throw 'This test needs an empty dedicated Debug installation. Rerun with -ResetDebugData to clear only com.lumifocus.app.debug. The release package is not cleared.'}
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
& node qa\charts-data.test.js
if($LASTEXITCODE -ne 0){throw 'Chart model checks failed'}
& node qa\features-data.test.js
if($LASTEXITCODE -ne 0){throw 'Feature model checks failed'}
$env:JAVA_HOME=$JdkPath
$adb=Join-Path $SdkRoot 'platform-tools\adb.exe'
& .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
if($LASTEXITCODE -ne 0){throw 'Build failed'}
& $adb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
if($LASTEXITCODE -ne 0){throw 'Debug install failed'}
& $adb -s emulator-5554 install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
if($LASTEXITCODE -ne 0){throw 'Test install failed'}
& $adb -s emulator-5554 shell pm clear com.lumifocus.app.debug
& $adb -s emulator-5554 shell pm grant com.lumifocus.app.debug android.permission.POST_NOTIFICATIONS
$main=& $adb -s emulator-5554 shell am instrument -w com.lumifocus.app.debug.test/com.lumifocus.app.SmokeInstrumentation
$main | Tee-Object -FilePath qa\device-test-2.5.0.txt
if(($main -join "`n") -match 'FAIL|Error=' -or ($main -match '^PASS ').Count -ne 95){throw 'Main device checks did not all pass'}
& $adb -s emulator-5554 shell am force-stop com.lumifocus.app.debug
$features=& $adb -s emulator-5554 shell am instrument -w -e features true com.lumifocus.app.debug.test/com.lumifocus.app.SmokeInstrumentation
$features | Tee-Object -FilePath qa\feature-test-2.5.0.txt
if(($features -join "`n") -match 'FAIL|Error=' -or ($features -match '^PASS ').Count -ne 80){throw 'Feature checks did not all pass'}
& $adb -s emulator-5554 shell am force-stop com.lumifocus.app.debug
$restore=& $adb -s emulator-5554 shell am instrument -w -e restore true com.lumifocus.app.debug.test/com.lumifocus.app.SmokeInstrumentation
$restore | Tee-Object -FilePath qa\appearance-restart-2.5.0.txt
if(($restore -join "`n") -match 'FAIL|Error=' -or ($restore -match '^PASS ').Count -ne 5){throw 'Restart checks did not all pass'}
