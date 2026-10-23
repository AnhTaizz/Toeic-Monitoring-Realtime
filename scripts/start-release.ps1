param(
 [ValidateSet('Client','Server')][string]$Role='Client',
 [string]$ServerUrl=$env:TOEIC_SERVER_URL,
 [string]$JavaHome=$env:JAVA_HOME,
 [string]$BindAddress='0.0.0.0',
 [ValidateRange(1,65535)][int]$Port=8080,
 [switch]$ValidateOnly
)
$ErrorActionPreference='Stop'
$env:PATHEXT='.COM;.EXE;.BAT;.CMD'
$root=$PSScriptRoot
$bundled=Join-Path $root 'runtime\bin\java.exe'
$java=if(Test-Path -LiteralPath $bundled){$bundled}elseif($JavaHome){Join-Path $JavaHome 'bin\java.exe'}else{(Get-Command java.exe -ErrorAction Stop).Source}
if(-not(Test-Path -LiteralPath $java)){throw 'Java 21 required. Set JAVA_HOME or supply -JavaHome.'}
$probe=New-Object Diagnostics.Process
$probe.StartInfo.FileName=$java;$probe.StartInfo.Arguments='-version';$probe.StartInfo.UseShellExecute=$false
$probe.StartInfo.RedirectStandardOutput=$true;$probe.StartInfo.RedirectStandardError=$true;$probe.StartInfo.CreateNoWindow=$true
[void]$probe.Start();$stdout=$probe.StandardOutput.ReadToEndAsync();$stderr=$probe.StandardError.ReadToEndAsync();$probe.WaitForExit()
$version=$stdout.Result+$stderr.Result
if($probe.ExitCode -ne 0){throw 'Java runtime could not start. Re-extract the complete runtime and verify the bundle path.'}
if($version -notmatch 'version "21[."]'){throw 'This release requires Java 21.'}
if($Role -eq 'Client'){
 if(-not $ServerUrl){if($ValidateOnly){throw 'ServerUrl required'};$ServerUrl=Read-Host 'Server URL (http://SERVER_LAN_IP:8080)'}
 $uri=$null
 if(-not[Uri]::TryCreate($ServerUrl,[UriKind]::Absolute,[ref]$uri) -or $uri.Scheme -notin @('http','https') -or $uri.UserInfo -or $uri.Query -or $uri.Fragment -or $uri.AbsolutePath -notin @('','/')){throw 'Server URL must be an HTTP/HTTPS origin without credential, path, query or fragment.'}
 $jar=Join-Path $root 'client.jar'
 $opts=@('-Duser.timezone=UTC',('-Dtoeic.server.url='+$uri.GetLeftPart([UriPartial]::Authority)),'-Dtoeic.realtime.delta-experimental=false','-jar',$jar)
}else{
 foreach($name in @('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','TOEIC_SEED_PASSWORD','TOEIC_AUDIO_ROOT','TOEIC_IMAGE_ROOT')){
  if(-not[Environment]::GetEnvironmentVariable($name,'Process')){throw ('Set server environment variable '+$name+' before launch. No credentials are included in this bundle.')}
 }
 foreach($dir in @($env:TOEIC_AUDIO_ROOT,$env:TOEIC_IMAGE_ROOT)){if(-not(Test-Path -LiteralPath $dir -PathType Container)){throw 'Configured exam asset directory is missing.'}}
 $jar=Join-Path $root 'server.jar'
 $opts=@('-Duser.timezone=UTC','-jar',$jar,('--server.address='+$BindAddress),('--server.port='+$Port),'--toeic.state.delta-enabled=false')
}
if(-not(Test-Path -LiteralPath $jar)){throw ('Missing '+$Role+' JAR')}
if($ValidateOnly){Write-Output ($Role+' launcher validation PASS; no network connection or exam was started.');exit 0}
Push-Location $root
try{
 $application=New-Object Diagnostics.Process
 $application.StartInfo.FileName=$java
 $application.StartInfo.Arguments=($opts|ForEach-Object {'"'+$_+'"'}) -join ' '
 $application.StartInfo.WorkingDirectory=$root;$application.StartInfo.UseShellExecute=$false
 [void]$application.Start();$application.WaitForExit();exit $application.ExitCode
}finally{Pop-Location}
