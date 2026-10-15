# Mở rộng pipeline QD-26; chỉ tạo artifact, không cài/gỡ hay đổi hệ thống.
param(
 [string]$OutputDir,
 [ValidateSet('AppImage','Installer')][string]$Mode='AppImage',
 [string]$ServerUrl,
 [string]$JavaHome=$env:JAVA_HOME,
 [string]$ManifestTool,
 [string]$SourceSha,
 [switch]$WithAudioSmoke,
 [switch]$DryRun
)
$ErrorActionPreference='Stop'
function Test-Origin([string]$Value) {
 $uri=$null
 return [Uri]::TryCreate($Value,[UriKind]::Absolute,[ref]$uri) -and $uri.Scheme -in @('http','https') -and $uri.Host -and -not $uri.UserInfo -and -not $uri.Query -and -not $uri.Fragment -and $uri.AbsolutePath -eq '/' -and $uri.Port -gt 0
}
if($ServerUrl -and -not (Test-Origin $ServerUrl)){throw 'ServerUrl phải là HTTP/HTTPS origin, không credential/path/query/fragment.'}
if([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT -or -not [Environment]::Is64BitProcess){throw 'Cần Windows x64 build process.'}
if(-not $JavaHome -or -not(Test-Path -LiteralPath ([IO.Path]::Combine($JavaHome,'bin/jpackage.exe')))){throw 'Thiếu JDK21 jpackage; truyền -JavaHome.'}
$release=[IO.File]::ReadAllText((Join-Path $JavaHome 'release'))
if($release -notmatch 'JAVA_VERSION="21\.'){throw 'Cần JDK21.'}
if($release -notmatch 'OS_ARCH="(amd64|x86_64)"'){throw 'JDK phải x64.'}
$root=Split-Path -Parent $PSScriptRoot
$jar=Join-Path $root 'client/target/client-0.1.0-SNAPSHOT-all.jar'
if(-not(Test-Path -LiteralPath $jar)){throw 'Thiếu fat JAR; chạy mvn package trên source đã test.'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead($jar)
try {foreach($required in @('javafx/scene/media/Media.class','javafx/scene/control/Control.class','jfxmedia.dll','glass.dll')) {
 if(-not ($zip.Entries | Where-Object {$_.FullName -eq $required})){throw "Thiếu JavaFX Windows resource: $required"}
}}finally{$zip.Dispose()}
if($Mode -eq 'Installer' -and $WithAudioSmoke){throw 'TEST launcher chỉ dành AppImage QA, không đưa vào installer phát hành.'}
if($Mode -eq 'Installer' -and (-not(Get-Command candle.exe -ErrorAction SilentlyContinue) -or -not(Get-Command light.exe -ErrorAction SilentlyContinue))){throw 'INSTALLER BLOCKED: JDK21 cần WiX3 candle/light trong PATH; không tự cài.'}
if(-not $ManifestTool){
 $sdk=Join-Path ${env:ProgramFiles(x86)} 'Windows Kits/10/bin'
 $ManifestTool=Get-ChildItem -LiteralPath $sdk -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | ForEach-Object {Join-Path $_.FullName 'x64/mt.exe'} | Where-Object {Test-Path -LiteralPath $_} | Select-Object -First 1
}
if(-not $ManifestTool -or -not(Test-Path -LiteralPath $ManifestTool)){throw 'Thiếu Windows SDK mt.exe cho QD26 UTF8 manifest; truyền -ManifestTool.'}
if($DryRun){Write-Output 'DRY_RUN prerequisites PASS; chưa tạo app-image/installer, chưa IT01.';exit 0}
if(-not $OutputDir){$OutputDir=Join-Path $root ('jpackage-out/windows-'+[Guid]::NewGuid().ToString('N'))}
if($OutputDir -and -not[IO.Path]::IsPathRooted($OutputDir)){$OutputDir=Join-Path (Get-Location).Path $OutputDir}
$OutputDir=[IO.Path]::GetFullPath($OutputDir)
if(Test-Path -LiteralPath $OutputDir){throw 'OutputDir đã tồn tại; không overwrite artifact cũ.'}
[void](New-Item -ItemType Directory -Path $OutputDir)
Push-Location $root
try {
 if($SourceSha){if($SourceSha -notmatch '^[0-9a-f]{40}$'){throw 'SourceSha phải đủ40hex.'};$source=$SourceSha;$sourceDirty=$null}else{$source=(git rev-parse HEAD).Trim();if($LASTEXITCODE -ne 0){throw 'Không đọc được Git; truyền -SourceSha từ checkout đã xác minh.'};$sourceDirty=[bool](git status --porcelain)}
 $image=& "$PSScriptRoot/package-t3b1.ps1" -JavaHome $JavaHome -ManifestTool $ManifestTool -WithAudioSmoke:$WithAudioSmoke
 $image=@($image | Where-Object {$_ -is [string] -and (Test-Path -LiteralPath $_ -PathType Container)}) | Select-Object -Last 1
 if(-not $image){throw 'Pipeline app-image không trả thư mục hợp lệ.'}
 Copy-Item -LiteralPath $image -Destination (Join-Path $OutputDir 'ToeicMonitor') -Recurse
 $image=Join-Path $OutputDir 'ToeicMonitor'
 foreach($file in @('ToeicMonitor.exe','runtime/bin/server/jvm.dll','app/client-0.1.0-SNAPSHOT-all.jar')){if(-not(Test-Path -LiteralPath (Join-Path $image $file))){throw "Package thiếu $file"}}
 Copy-Item -LiteralPath "$PSScriptRoot/start-packaged-client.ps1" -Destination (Join-Path $OutputDir 'Start-ToeicMonitor.ps1')
 if($ServerUrl){[IO.File]::WriteAllText((Join-Path $OutputDir 'server-url.example.txt'),$ServerUrl,[Text.UTF8Encoding]::new($false))}
 $installer=$null
 if($Mode -eq 'Installer'){
  # jpackage nhận args ASCII tương đối; PowerShell copy Unicode sau khi build.
  $stage='jpackage-out/installer-'+[Guid]::NewGuid().ToString('N');[void](New-Item -ItemType Directory -Path $stage)
  Copy-Item -LiteralPath $image -Destination (Join-Path $stage 'ToeicMonitor') -Recurse
  $args=@('--type','exe','--app-image',"$stage/ToeicMonitor",'--dest',"$stage/out",'--name','ToeicMonitor','--app-version','0.1.0','--win-per-user-install')
  & ([IO.Path]::Combine($JavaHome,'bin/jpackage.exe')) @args
  if($LASTEXITCODE -ne 0){throw 'INSTALLER FAIL; app-image giữ lại, không coi installer PASS.'}
  $built=Get-ChildItem -LiteralPath "$stage/out" -Filter '*.exe' | Select-Object -First 1
  if(-not $built){throw 'Không có installer sau jpackage.'}
  $installer=Join-Path $OutputDir $built.Name;Copy-Item -LiteralPath $built.FullName -Destination $installer
 }
 $files=@(Get-ChildItem -LiteralPath $OutputDir -Recurse -File | ForEach-Object {[ordered]@{path=$_.FullName.Substring($OutputDir.TrimEnd('\').Length+1);sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}})
 [ordered]@{schema='toeic-windows-package-v1';sourceSha=$source;sourceDirty=$sourceDirty;utc=[DateTime]::UtcNow.ToString('o');os=[Environment]::OSVersion.VersionString;javaRelease=$release;mode=$Mode;image=$image;installer=$installer;files=$files;runtimeServerConfig='Start script parameter > process env TOEIC_SERVER_URL > user server-url.txt > app GUI/default; no token/DB secret in artifact';humanHearing='NOT RUN';secondMachine='NOT RUN';installUninstall='NOT RUN';reproducibility='timestamp/config recorded; not byte-identical claim'} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $OutputDir 'package-manifest.json') -Encoding UTF8
 Write-Output "ARTIFACT $OutputDir; cài/gỡ/máy thứ hai NOT RUN. ServerUrl dùng lúc chạy, không nhúng vào JAR."
}finally{Pop-Location}
