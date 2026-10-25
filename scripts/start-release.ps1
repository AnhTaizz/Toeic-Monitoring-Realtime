param(
 [ValidateSet('Client','Server')][string]$Role='Client',
 [string]$ServerUrl=$env:TOEIC_SERVER_URL,
 [string]$JavaHome=$env:JAVA_HOME,
 [string]$BindAddress='0.0.0.0',
 [ValidateRange(1,65535)][int]$Port=8080,
 [string]$EnvFile,
 [switch]$NoSignalMonitoring,
 [switch]$ValidateOnly
)
# Release default: process monitoring plus USB storage presence/connect/disconnect and focus/window/display
# signals (required for the handover). Candidates see the observation notice before entering a room.
$signals=(-not $NoSignalMonitoring.IsPresent).ToString().ToLowerInvariant()
$ErrorActionPreference='Stop'
# Show one readable line instead of a PowerShell stack for configuration and prerequisite errors.
trap { Write-Host ('ERROR: '+$_.Exception.Message) -ForegroundColor Red; exit 1 }
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
 $opts=@('-Duser.timezone=UTC',('-Dtoeic.server.url='+$uri.GetLeftPart([UriPartial]::Authority)),'-Dtoeic.realtime.delta-experimental=false',
  ('-Dtoeic.monitoring.signal-experiment-enabled='+$signals),('-Dtoeic.monitoring.usb-enabled='+$signals),'-jar',$jar)
}else{
 # No TOEIC_SEED_PASSWORD: a release never creates the development demo accounts.
 $required=@('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','TOEIC_AUDIO_ROOT','TOEIC_IMAGE_ROOT','TOEIC_ROOM_PIN_KEY')
 $optional=@('TOEIC_ADMIN_BOOTSTRAP_USERNAME','TOEIC_ADMIN_BOOTSTRAP_PASSWORD')
 # Operator settings live in server.env next to this launcher (copied from server.env.example).
 # Values already set in the process environment win; nothing from the file is printed.
 if(-not $EnvFile){$EnvFile=Join-Path $root 'server.env'}
 if(Test-Path -LiteralPath $EnvFile -PathType Leaf){
  foreach($line in [IO.File]::ReadAllLines($EnvFile,[Text.Encoding]::UTF8)){
   if($line -match '^\s*([A-Z_]+)\s*=(.*)$' -and ($required+$optional) -contains $Matches[1]){
    $value=$Matches[2].Trim().Trim('"').Trim("'")
    if($value -and -not[Environment]::GetEnvironmentVariable($Matches[1],'Process')){[Environment]::SetEnvironmentVariable($Matches[1],$value,'Process')}
   }
  }
 }
 foreach($name in $required){
  if(-not[Environment]::GetEnvironmentVariable($name,'Process')){throw ('Missing '+$name+'. Copy server.env.example to server.env next to Start-Server.bat and fill it in (see READ-ME.txt). No credentials are included in this bundle.')}
 }
 if($env:DB_PORT -notmatch '^\d{1,5}$'){throw 'DB_PORT must be a port number such as 5432.'}
 foreach($name in @('TOEIC_AUDIO_ROOT','TOEIC_IMAGE_ROOT')){
  $dir=[Environment]::GetEnvironmentVariable($name,'Process')
  if(-not[IO.Path]::IsPathRooted($dir)){throw ($name+' must be an absolute folder path (the bundle is not the repository).')}
  if(-not(Test-Path -LiteralPath $dir -PathType Container)){throw ($name+' folder does not exist. Point it at the media root of the imported exam.')}
 }
 $key=$null
 try{$text=$env:TOEIC_ROOM_PIN_KEY.Replace('-','+').Replace('_','/');$text+='='*((4-$text.Length%4)%4);$key=[Convert]::FromBase64String($text)}catch{$key=$null}
 if(-not $key -or $key.Length -lt 32){throw 'TOEIC_ROOM_PIN_KEY must be base64url of at least 32 random bytes. Generate it once (READ-ME.txt) and keep it unchanged across restarts.'}
 if(-not $env:TOEIC_ADMIN_BOOTSTRAP_USERNAME -or -not $env:TOEIC_ADMIN_BOOTSTRAP_PASSWORD){Write-Output 'Note: TOEIC_ADMIN_BOOTSTRAP_USERNAME/PASSWORD not set. Admin login works only if this database already has an Admin account; a new database has no accounts.'}
 $jar=Join-Path $root 'server.jar'
 # Command-line value wins over any TOEIC_SEED_DEMO_ACCOUNTS left in the environment.
 $opts=@('-Duser.timezone=UTC','-jar',$jar,('--server.address='+$BindAddress),('--server.port='+$Port),'--toeic.state.delta-enabled=false',
  ('--toeic.monitoring.signal-experiment-enabled='+$signals),'--toeic.seed.demo-accounts=false')
}
if(-not(Test-Path -LiteralPath $jar)){throw ('Missing '+$Role+' JAR')}
if($ValidateOnly){Write-Output ($Role+' launcher validation PASS; no network connection or exam was started.');exit 0}
if($Role -eq 'Server'){
 function Reachable([string]$address,[int]$port){
  $client=New-Object Net.Sockets.TcpClient
  try{$connect=$client.BeginConnect($address,$port,$null,$null);if(-not $connect.AsyncWaitHandle.WaitOne(2000)){return $false};$client.EndConnect($connect);return $true}catch{return $false}finally{$client.Dispose()}
 }
 if(-not(Reachable $env:DB_HOST ([int]$env:DB_PORT))){throw ('Cannot reach PostgreSQL at '+$env:DB_HOST+':'+$env:DB_PORT+'. Start PostgreSQL first and check DB_HOST/DB_PORT.')}
 if(Reachable '127.0.0.1' $Port){throw ('Port '+$Port+' is already in use. Stop the old Server (Ctrl+C in its window) or pass -Port. No process was stopped.')}
 Write-Output ('Starting TOEIC Server on '+$BindAddress+':'+$Port+'. Clients connect to http://THIS_PC_LAN_IP:'+$Port+'. Keep this window open; Ctrl+C stops the Server.')
}
Push-Location $root
try{
 $application=New-Object Diagnostics.Process
 $application.StartInfo.FileName=$java
 $application.StartInfo.Arguments=($opts|ForEach-Object {'"'+$_+'"'}) -join ' '
 $application.StartInfo.WorkingDirectory=$root;$application.StartInfo.UseShellExecute=$false
 [void]$application.Start()
 if($Role -eq 'Server'){
  # 401 from the auth probe means Flyway finished, the web server is listening and authentication is active.
  $probeHost=if($BindAddress -eq '0.0.0.0'){'127.0.0.1'}else{$BindAddress}
  $until=[DateTime]::UtcNow.AddSeconds(180);$ready=$false
  while(-not $application.HasExited -and -not $ready -and [DateTime]::UtcNow -lt $until){
   try{[void](Invoke-WebRequest ('http://'+$probeHost+':'+$Port+'/api/v1/auth/me') -UseBasicParsing -TimeoutSec 2)}catch{if($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 401){$ready=$true}}
   if(-not $ready){Start-Sleep -Milliseconds 500}
  }
  if($ready){
   $lan=@([Net.Dns]::GetHostAddresses([Net.Dns]::GetHostName())|Where-Object {$_.AddressFamily -eq 'InterNetwork' -and -not[Net.IPAddress]::IsLoopback($_)}|ForEach-Object {'http://'+$_+':'+$Port})
   Write-Output ('Server READY on port '+$Port+'. Clients: '+$(if($lan){$lan -join ' or '}else{'http://THIS_PC_LAN_IP:'+$Port})+' (same PC: http://127.0.0.1:'+$Port+').')
  }elseif(-not $application.HasExited){Write-Output ('Server has not answered on port '+$Port+' within 180 s; check the messages above.')}
 }
 $application.WaitForExit();exit $application.ExitCode
}finally{Pop-Location}
