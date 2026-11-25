param([string]$Version='2.9.4')
$ErrorActionPreference='Stop'
$projectDir=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectDir
if($Version -notmatch '^\d+\.\d+\.\d+$'){throw 'Version must contain three numbers'}
$files=@('README.md','.gitignore','build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','app/build.gradle') | ForEach-Object {Get-Item -LiteralPath $_}
$files+=Get-ChildItem -LiteralPath app/src,gradle,docs,scripts -File -Recurse
$files+=Get-ChildItem -LiteralPath qa -File | Where-Object {$_.Extension -in '.js','.java','.ps1' -or $_.Name -eq 'wallpaper-fixture.jpg'}
$archivePath=Join-Path $projectDir "dist/Fanqie-$Version-source.zip"
$stream=[IO.File]::Open($archivePath,[IO.FileMode]::Create)
$archive=[IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Create)
try{
    foreach($file in $files){
        $entry=$file.FullName.Substring($projectDir.Length+1).Replace('\','/')
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file.FullName,$entry,[IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
}finally{$archive.Dispose();$stream.Dispose()}
Get-Item -LiteralPath $archivePath | Select-Object Name,Length
