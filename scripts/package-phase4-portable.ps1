param([Parameter(Mandatory=$true)][string]$JavaHome,[string]$OutputRoot,[switch]$WithoutRuntime,[string]$ManifestTool,
 [ValidatePattern('^[A-Za-z0-9_.-]+$')][string]$ReleaseName='toeic-monitor-portable',[string]$ReadMe)
$ErrorActionPreference='Stop'
$env:PATHEXT='.COM;.EXE;.BAT;.CMD'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if(-not $OutputRoot){$OutputRoot=Join-Path $repo 'target\release\distribution'}
$run=$ReleaseName+'-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
$root=Join-Path $OutputRoot $run
if(Test-Path -LiteralPath $root){throw 'Output already exists'}
foreach($name in @('java.exe','jlink.exe')){if(-not(Test-Path -LiteralPath (Join-Path $JavaHome ('bin\'+$name)))){throw ('Missing '+$name)}}
New-Item -ItemType Directory -Force $root|Out-Null
Copy-Item -LiteralPath (Join-Path $repo 'client\target\client-0.1.0-SNAPSHOT-all.jar') -Destination (Join-Path $root 'client.jar')
Copy-Item -LiteralPath (Join-Path $repo 'server\target\server-0.1.0-SNAPSHOT.jar') -Destination (Join-Path $root 'server.jar')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'start-release.ps1') -Destination $root
# Template only: every secret value is empty. The operator's filled server.env never enters the bundle.
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'release-server.env.example') -Destination (Join-Path $root 'server.env.example')
if(-not $ReadMe){$ReadMe=Join-Path $repo 'docs\release\PORTABLE-READ-ME.txt'}
Copy-Item -LiteralPath $ReadMe -Destination (Join-Path $root 'READ-ME.txt')
$ascii=[Text.Encoding]::ASCII
foreach($role in @('Client','Server')){
 # Keep the window open on failure, but still return the launcher's exit code (pause resets ERRORLEVEL).
 $bat='@echo off'+"`r`n"+'setlocal'+"`r`n"+'"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-release.ps1" -Role '+$role+' %*'+"`r`n"+'set "TOEIC_EXIT=%ERRORLEVEL%"'+"`r`n"+'if not "%TOEIC_EXIT%"=="0" pause'+"`r`n"+'exit /b %TOEIC_EXIT%'+"`r`n"
 [IO.File]::WriteAllText((Join-Path $root ('Start-'+$role+'.bat')),$bat,$ascii)
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach($module in @('client','server')){
 $archive=[IO.Compression.ZipFile]::OpenRead((Join-Path $root ($module+'.jar')))
 try{
  foreach($entry in $archive.Entries){
   if($entry.FullName -match '(^|/)(\.env|[^/]*\.private(?:\.[^/]*)?|credentials\.properties)$|(^|/)(surefire-reports|test-classes)/|vn/edu/toeic/.*/(AudioPackageSmoke|Phase4RoomBenchmarkTest|RoomFlowPostgresGuiTest)(\$|\.class)'){throw 'Unexpected secret/test/runtime material in production JAR'}
  }
  $prefix=if($module -eq 'server'){'BOOT-INF/classes/'}else{''}
  $packagePrefix=$prefix+'vn/edu/toeic/'+$module+'/'
  $classes=@($archive.Entries|Where-Object {$_.FullName.StartsWith($packagePrefix) -and $_.FullName.EndsWith('.class')})
  $classRoot=Join-Path $repo ($module+'\target\classes')
  $current=@(Get-ChildItem (Join-Path $classRoot ('vn\edu\toeic\'+$module)) -File -Recurse -Filter '*.class')
  if($classes.Count -ne $current.Count){throw ('Stale '+$module+' JAR class inventory')}
  foreach($entry in $classes){
   $file=Join-Path $classRoot ($entry.FullName.Substring($prefix.Length).Replace('/','\'))
   if(-not(Test-Path -LiteralPath $file)){throw ('Stale '+$module+' JAR class')}
   $stream=$entry.Open();try{$packagedHash=(Get-FileHash -Algorithm SHA256 -InputStream $stream).Hash}finally{$stream.Dispose()}
   if($packagedHash -ne (Get-FileHash -Algorithm SHA256 -LiteralPath $file).Hash){throw ('Stale '+$module+' JAR bytecode')}
  }
 }finally{$archive.Dispose()}
}
if(-not $WithoutRuntime){
 $linker=New-Object Diagnostics.Process
 $linker.StartInfo.FileName=Join-Path $JavaHome 'bin\jlink.exe'
 $linker.StartInfo.Arguments='--add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.management,jdk.zipfs --strip-debug --no-header-files --no-man-pages --output "'+(Join-Path $root 'runtime')+'"'
 $linker.StartInfo.UseShellExecute=$false;$linker.StartInfo.RedirectStandardOutput=$true;$linker.StartInfo.RedirectStandardError=$true
 [void]$linker.Start();$linkOut=$linker.StandardOutput.ReadToEndAsync();$linkErr=$linker.StandardError.ReadToEndAsync();$linker.WaitForExit()
 [IO.File]::WriteAllText((Join-Path $OutputRoot ($run+'.jlink.private.log')),$linkOut.Result+$linkErr.Result)
 if($linker.ExitCode -ne 0){throw ('Bundled runtime failed, exit='+$linker.ExitCode+'; private logs and partial output retained')}

}
# Reuse the UTF-8 application-manifest policy from package-t3b1.ps1 for JDK21 JLI Unicode paths.
if(-not $WithoutRuntime){
 if(-not $ManifestTool){
  $sdkBin=Join-Path ${env:ProgramFiles(x86)} 'Windows Kits\10\bin'
  $ManifestTool=Get-ChildItem -LiteralPath $sdkBin -Directory -ErrorAction SilentlyContinue|Sort-Object Name -Descending|ForEach-Object {Join-Path $_.FullName 'x64\mt.exe'}|Where-Object {Test-Path -LiteralPath $_}|Select-Object -First 1
 }
 if(-not $ManifestTool -or -not(Test-Path -LiteralPath $ManifestTool)){throw 'Windows SDK mt.exe required for Unicode runtime; supply -ManifestTool.'}
 function ManifestCommand([string]$arguments,[string]$label){
  $p=New-Object Diagnostics.Process;$p.StartInfo.FileName=$ManifestTool;$p.StartInfo.Arguments=$arguments
  $p.StartInfo.UseShellExecute=$false;$p.StartInfo.RedirectStandardOutput=$true;$p.StartInfo.RedirectStandardError=$true
  [void]$p.Start();$a=$p.StandardOutput.ReadToEndAsync();$b=$p.StandardError.ReadToEndAsync();$p.WaitForExit()
  [IO.File]::WriteAllText((Join-Path $OutputRoot ($run+'.'+$label+'.private.log')),$a.Result+$b.Result)
  if($p.ExitCode -ne 0){throw ('Manifest operation '+$label+' failed '+$p.ExitCode)}
 }
 $exe=Join-Path $root 'runtime\bin\java.exe'
 $xmlFile=Join-Path $OutputRoot ($run+'.java.manifest.private.xml')
 ManifestCommand ('-nologo -inputresource:"'+$exe+'";#1 -out:"'+$xmlFile+'"') 'manifest-read'
 [xml]$document=[IO.File]::ReadAllText($xmlFile)
 $namespaces=[Xml.XmlNamespaceManager]::new($document.NameTable);$namespaces.AddNamespace('a','urn:schemas-microsoft-com:asm.v3')
 $settings=$document.SelectSingleNode('//a:application/a:windowsSettings',$namespaces)
 if(-not $settings){$application=$document.CreateElement('application','urn:schemas-microsoft-com:asm.v3');$settings=$document.CreateElement('windowsSettings','urn:schemas-microsoft-com:asm.v3');[void]$application.AppendChild($settings);[void]$document.DocumentElement.AppendChild($application)}
 $namespaces.AddNamespace('u','http://schemas.microsoft.com/SMI/2019/WindowsSettings')
 $codepage=$settings.SelectSingleNode('u:activeCodePage',$namespaces)
 if(-not $codepage){$codepage=$document.CreateElement('activeCodePage','http://schemas.microsoft.com/SMI/2019/WindowsSettings');[void]$settings.AppendChild($codepage)}
 $codepage.InnerText='UTF-8';$document.Save($xmlFile)
 $file=Get-Item -LiteralPath $exe;$readOnly=$file.IsReadOnly;$file.IsReadOnly=$false
 try{ManifestCommand ('-nologo -manifest "'+$xmlFile+'" -outputresource:"'+$exe+'";#1') 'manifest-write'}finally{$file.IsReadOnly=$readOnly}
}
$env:PATHEXT='.COM;.EXE;.BAT;.CMD'
$source=(& git.exe -C $repo rev-parse HEAD).Trim()
$patchPaths=@(& git.exe -C $repo diff --name-only HEAD -- client/src/main server/src/main protocol/src/main)
if($LASTEXITCODE -ne 0){throw 'Cannot fingerprint production changes'}
$productionPatches=@($patchPaths|ForEach-Object {@{path=$_;sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $repo $_)).Hash.ToLowerInvariant()}})
$files=@(Get-ChildItem $root -File -Recurse|ForEach-Object{@{path=$_.FullName.Substring($root.Length+1).Replace('\','/');bytes=$_.Length;sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLowerInvariant()}})
$metadata=[ordered]@{sourceCommit=$source;productionWorkingTreePatches=$productionPatches;kind='portable runtime/JAR bundle; unsigned; physical LAN acceptance pending';generatedUtc=[DateTime]::UtcNow.ToString('o');containsCredentials=$false;bundledRuntime=(-not $WithoutRuntime);files=$files}
$metadata|ConvertTo-Json -Depth 6|Set-Content (Join-Path $root 'artifact-manifest.json') -Encoding UTF8
Compress-Archive -LiteralPath $root -DestinationPath ($root+'.zip') -CompressionLevel Optimal
@{directory=$root;zip=($root+'.zip');zipSha256=(Get-FileHash -Algorithm SHA256 -LiteralPath ($root+'.zip')).Hash.ToLowerInvariant();sourceCommit=$source}|ConvertTo-Json|Set-Content (Join-Path $OutputRoot 'latest.json') -Encoding UTF8
Write-Output ('Portable bundle: '+$root)
