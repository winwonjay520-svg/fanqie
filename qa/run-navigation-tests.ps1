param([string[]]$ProfileNames=@('landscape','portrait','split','narrow','scaled','short','phone'),[string]$Label='before',[ValidateSet('navigation','appearance','photoUpload','todo')][string]$Kind='navigation')
$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
$adb='E:\androidsdk\platform-tools\adb.exe'
function Device([string[]]$Arguments){$value=& $adb -s emulator-5554 @Arguments;if($LASTEXITCODE -ne 0){throw ('adb failed: '+($Arguments -join ' '))};return $value}
$size=Device @('shell','wm','size');$density=Device @('shell','wm','density');$rotation=(Device @('shell','settings','get','system','user_rotation')).Trim();$accelerometer=(Device @('shell','settings','get','system','accelerometer_rotation')).Trim()
$profiles=@(
 @{Name='landscape';Size='2400x3392';Density='320';Rotation='1'},@{Name='portrait';Size='2400x3392';Density='320';Rotation='0'},
 @{Name='split';Size='1696x2400';Density='320';Rotation='0'},@{Name='narrow';Size='1120x2400';Density='320';Rotation='0'},
 @{Name='scaled';Size='2400x3392';Density='400';Rotation='1'},@{Name='short';Size='1120x2000';Density='320';Rotation='1'},
 @{Name='phone';Size='1440x2560';Density='640';Rotation='0'},@{Name='phone-tall';Size='1260x2800';Density='480';Rotation='0'}
)
if($ProfileNames | Where-Object {$_ -notin $profiles.Name}){throw 'Unknown navigation test profile'}
if($Label -notmatch '^[a-zA-Z0-9.-]+$'){throw 'Invalid report label'}
try{
 Device @('shell','settings','put','system','accelerometer_rotation','0') | Out-Null
 foreach($profile in $profiles){
  if($profile.Name -notin $ProfileNames){continue}
  Device @('shell','am','force-stop','com.lumifocus.app.debug') | Out-Null
  Device @('shell','wm','size',$profile.Size) | Out-Null
  Start-Sleep -Milliseconds 800
  Device @('shell','wm','density',$profile.Density) | Out-Null
  Start-Sleep -Milliseconds 800
  Device @('shell','wm','user-rotation','lock',$profile.Rotation) | Out-Null
  Start-Sleep -Milliseconds 800
  $result=Device @('shell','am','instrument','-w','-e',$Kind,$profile.Name,'com.lumifocus.app.debug.test/com.lumifocus.app.SmokeInstrumentation')
  $result | Set-Content -Encoding UTF8 ('qa/'+$Kind+'-'+$profile.Name+'-'+$Label+'.txt')
  $result | Where-Object {$_ -match '^(NAV|TODO)_VIEWPORT|^FAIL|^(NAV|APPEARANCE|PHOTO_UPLOAD|TODO)_(DONE|FAILED)'}
  if(($result -join "`n") -match '(?m)^(FAIL|NAV_FAILED|APPEARANCE_FAILED|PHOTO_UPLOAD_FAILED|TODO_FAILED)|Error=' -or !($result -match ('^(NAV|APPEARANCE|PHOTO_UPLOAD|TODO)_DONE '+$profile.Name))){throw ('Appearance/navigation checks failed: '+$profile.Name)}
 }
}finally{
 Device @('shell','am','force-stop','com.lumifocus.app.debug') | Out-Null
 $savedSize=($size | Select-String 'Override size: (\d+x\d+)');$savedDensity=($density | Select-String 'Override density: (\d+)')
 Device @('shell','wm','size',$(if($savedSize){$savedSize.Matches[0].Groups[1].Value}else{'reset'})) | Out-Null
 Device @('shell','wm','density',$(if($savedDensity){$savedDensity.Matches[0].Groups[1].Value}else{'reset'})) | Out-Null
 if($accelerometer -eq '1'){Device @('shell','wm','user-rotation','free') | Out-Null}else{Device @('shell','wm','user-rotation','lock',$rotation) | Out-Null}
}
