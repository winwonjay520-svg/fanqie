param([switch]$IncludePhoneRegression,[string]$Serial='emulator-5554',[string]$SdkRoot='E:\androidsdk',[string]$JdkPath='E:\androidstudio\jbr',[string[]]$ProfileNames=@('landscape','portrait','split','narrow','scaled','short','phone'))
$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
if($Serial -notmatch '^emulator-\d+$'){throw 'Display overrides are restricted to an emulator; this script does not reconfigure a physical tablet.'}
if(!$ProfileNames.Count -or ($ProfileNames | Where-Object {$_ -notin @('landscape','portrait','split','narrow','scaled','short','phone')})){throw 'Unknown tablet profile'}
$adb=Join-Path $SdkRoot 'platform-tools\adb.exe'
function Device([string[]]$Arguments){$result=& $adb -s $Serial @Arguments;if($LASTEXITCODE -ne 0){throw ('adb failed: '+($Arguments -join ' '))};return $result}
$size=Device @('shell','wm','size');$density=Device @('shell','wm','density')
$rotation=(Device @('shell','settings','get','system','user_rotation')).Trim()
$accelerometer=(Device @('shell','settings','get','system','accelerometer_rotation')).Trim()
$hardwareIme=(Device @('shell','settings','get','secure','show_ime_with_hard_keyboard')).Trim()
$originalIme=(Device @('shell','settings','--user','0','get','secure','default_input_method')).Trim()
$originalEnabledIme=(Device @('shell','settings','--user','0','get','secure','enabled_input_methods')).Trim()
$testIme='com.lumifocus.app.debug.test/com.lumifocus.app.QaImeService'
function RestoreSetting([string]$Scope,[string]$Name,[string]$Value){if($Value -eq 'null'){Device @('shell','settings','delete',$Scope,$Name) | Out-Null}else{Device @('shell','settings','put',$Scope,$Name,$Value) | Out-Null}}
try{
 Device @('shell','settings','put','system','accelerometer_rotation','0') | Out-Null
 Device @('shell','wm','user-rotation','lock','0') | Out-Null
 Device @('shell','settings','put','secure','show_ime_with_hard_keyboard','1') | Out-Null
 $env:JAVA_HOME=$JdkPath
 if($IncludePhoneRegression){& .\qa\run-device-tests.ps1 -ResetDebugData -SdkRoot $SdkRoot -JdkPath $JdkPath;if($LASTEXITCODE -ne 0){throw 'Phone regression failed'}}
 else{
  & .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
  if($LASTEXITCODE -ne 0){throw 'Debug build failed'}
  Device @('install','-r','app\build\outputs\apk\debug\app-debug.apk') | Out-Null
  Device @('install','-r','app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk') | Out-Null
 }
 Device @('shell','settings','--user','0','put','secure','enabled_input_methods',($originalEnabledIme+':'+$testIme)) | Out-Null
 Device @('shell','settings','--user','0','put','secure','default_input_method',$testIme) | Out-Null
 $profiles=@(
  @{Name='landscape';Size='2400x3392';Density='320';Rotation='1'},
  @{Name='portrait';Size='2400x3392';Density='320';Rotation='0'},
  @{Name='split';Size='1696x2400';Density='320';Rotation='0'},
  @{Name='narrow';Size='1120x2400';Density='320';Rotation='0'},
  @{Name='scaled';Size='2400x3392';Density='400';Rotation='1'},
  @{Name='short';Size='1120x2000';Density='320';Rotation='1'},
  @{Name='phone';Size='1440x2560';Density='640';Rotation='0'}
 )
 foreach($profile in $profiles){
  if($ProfileNames -notcontains $profile.Name){continue}
  Device @('shell','am','force-stop','com.lumifocus.app.debug') | Out-Null
  Device @('shell','wm','size',$profile.Size) | Out-Null
  Start-Sleep -Milliseconds 800
  Device @('shell','wm','density',$profile.Density) | Out-Null
  Start-Sleep -Milliseconds 800
  Device @('shell','wm','user-rotation','lock',$profile.Rotation) | Out-Null
  Start-Sleep -Milliseconds 800
  $result=Device @('shell','am','instrument','-w','-e','tablet',$profile.Name,'com.lumifocus.app.debug.test/com.lumifocus.app.SmokeInstrumentation')
  $result | Tee-Object -FilePath (Join-Path 'qa' ('tablet-'+$profile.Name+'-2.5.0.txt'))
  if(($result -join "`n") -match 'FAIL|Error=' -or !($result -match ('^TABLET_DONE '+$profile.Name+'$'))){throw ('Tablet profile failed: '+$profile.Name)}
 }
}finally{
 Device @('shell','am','force-stop','com.lumifocus.app.debug') | Out-Null
 $savedSize=($size | Select-String 'Override size: (\d+x\d+)');$savedDensity=($density | Select-String 'Override density: (\d+)')
 if($savedSize){Device @('shell','wm','size',$savedSize.Matches[0].Groups[1].Value) | Out-Null}else{Device @('shell','wm','size','reset') | Out-Null}
 if($savedDensity){Device @('shell','wm','density',$savedDensity.Matches[0].Groups[1].Value) | Out-Null}else{Device @('shell','wm','density','reset') | Out-Null}
 if($accelerometer -eq '1'){Device @('shell','wm','user-rotation','free') | Out-Null}else{Device @('shell','wm','user-rotation','lock',$rotation) | Out-Null}
 RestoreSetting 'secure' 'show_ime_with_hard_keyboard' $hardwareIme
 if($originalIme -ne 'null'){Device @('shell','settings','--user','0','put','secure','default_input_method',$originalIme) | Out-Null}
 Device @('shell','settings','--user','0','put','secure','enabled_input_methods',$originalEnabledIme) | Out-Null
}
