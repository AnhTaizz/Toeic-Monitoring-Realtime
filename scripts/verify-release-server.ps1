param([Parameter(Mandatory=$true)][string]$Bundle,[Parameter(Mandatory=$true)][string]$PostgresBin,
 [string]$OutputDirectory,[string]$UpgradeFromServerJar,[string]$UpgradeJavaHome,
 [string]$ImportJson,[string]$ImportAudioRoot,[string]$ImportImageRoot)
# With -ImportJson the given rich-import payload (for example a STUDY4 adapter output) and its media
# roots replace the MOCK fixture; the Proctor imports it and the catalog must list it as selectable.
# Release smoke for the portable Server: Unicode copy of the bundle, server.env loading, fail-closed
# configuration checks, real start on an owned PostgreSQL cluster, Admin bootstrap and V21, and no demo
# accounts in a release even when TOEIC_SEED_DEMO_ACCOUNTS=true is left in the environment.
# With -UpgradeFromServerJar the owned database is first created and seeded by that older Server,
# then upgraded by this bundle. Secrets are random per run, written only under the private output
# folder and removed at the end. Not an exam flow, not LAN.
$ErrorActionPreference='Stop';$env:PATHEXT='.COM;.EXE;.BAT;.CMD'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if(-not $OutputDirectory){$OutputDirectory=Join-Path $repo ('target\release\server-smoke-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'))}
if(Test-Path -LiteralPath $OutputDirectory){throw 'Smoke output already exists'}
New-Item -ItemType Directory -Force $OutputDirectory|Out-Null
$out=$OutputDirectory
# "Kiem thu ban phat hanh" with Vietnamese diacritics, built from code points so this file stays ASCII.
$unicodeName='Ki'+[char]0x1EC3+'m th'+[char]0x1EED+' b'+[char]0x1EA3+'n ph'+[char]0x00E1+'t h'+[char]0x00E0+'nh'
$unicode=Join-Path $out $unicodeName
New-Item -ItemType Directory -Force $unicode|Out-Null
$copy=Join-Path $unicode 'TOEIC Portable';Copy-Item -LiteralPath $Bundle -Destination $copy -Recurse
$media=Join-Path $unicode ('Media '+[char]0x0111+[char]0x1EC1+' thi')
if($ImportJson){
 Copy-Item -LiteralPath $ImportAudioRoot -Destination (Join-Path $media 'audio') -Recurse
 Copy-Item -LiteralPath $ImportImageRoot -Destination (Join-Path $media 'images') -Recurse
}else{
 foreach($type in @('audio','images')){Copy-Item -LiteralPath (Join-Path $repo ('docs\plan-v2\fixtures\ex02\assets\'+$type)) -Destination (Join-Path $media $type) -Recurse}
}
function Port { $l=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0);$l.Start();$n=$l.LocalEndpoint.Port;$l.Stop();return $n }
$dbPort=Port;$httpPort=Port;$closedPort=Port
$dbUser='release_smoke';$dbName='release_smoke'
$dbPassword=[Guid]::NewGuid().ToString('N');$seed=[Guid]::NewGuid().ToString('N');$adminPassword=[Guid]::NewGuid().ToString('N')
$bytes=New-Object byte[] 32;$rng=[Security.Cryptography.RandomNumberGenerator]::Create();try{$rng.GetBytes($bytes)}finally{$rng.Dispose()}
$pinKey=[Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_')
# No TOEIC_SEED_PASSWORD in server.env: a release does not create demo accounts. $seed is only for the older Server.
$settings=[ordered]@{DB_HOST='127.0.0.1';DB_PORT=[string]$dbPort;DB_NAME=$dbName;DB_USER=$dbUser;DB_PASSWORD=$dbPassword
 TOEIC_AUDIO_ROOT=(Join-Path $media 'audio');TOEIC_IMAGE_ROOT=(Join-Path $media 'images');TOEIC_ROOM_PIN_KEY=$pinKey
 TOEIC_ADMIN_BOOTSTRAP_USERNAME='release-admin';TOEIC_ADMIN_BOOTSTRAP_PASSWORD=$adminPassword}
$names=@($settings.Keys)
$utf8=New-Object Text.UTF8Encoding($false)
function WriteEnv([hashtable]$override){
 $lines=foreach($k in $names){$v=if($override.ContainsKey($k)){$override[$k]}else{$settings[$k]};$k+'='+$v}
 [IO.File]::WriteAllLines((Join-Path $copy 'server.env'),[string[]]$lines,$utf8)
}
$cases=New-Object Collections.Generic.List[object]
function Launch([string]$file,[string]$arguments,[hashtable]$processEnv){
 $p=New-Object Diagnostics.Process;$p.StartInfo.FileName=$file;$p.StartInfo.Arguments=$arguments
 $p.StartInfo.WorkingDirectory=$copy;$p.StartInfo.UseShellExecute=$false;$p.StartInfo.RedirectStandardOutput=$true;$p.StartInfo.RedirectStandardError=$true
 $p.StartInfo.EnvironmentVariables['PATH']=$env:SystemRoot+'\System32;'+$env:SystemRoot
 foreach($n in $names+@('JAVA_HOME','JAVA_TOOL_OPTIONS','PGPASSWORD','TOEIC_SEED_PASSWORD','TOEIC_SEED_DEMO_ACCOUNTS')){$p.StartInfo.EnvironmentVariables.Remove($n)}
 if($processEnv){foreach($k in $processEnv.Keys){$p.StartInfo.EnvironmentVariables[$k]=$processEnv[$k]}}
 [void]$p.Start();return $p
}
function Validate([string]$name,[hashtable]$override,[hashtable]$processEnv,[bool]$expectOk,[string]$expectText){
 WriteEnv $override
 $p=Launch (Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe') ('-NoProfile -ExecutionPolicy Bypass -File "'+(Join-Path $copy 'start-release.ps1')+'" -Role Server -ValidateOnly') $processEnv
 $text=$p.StandardOutput.ReadToEnd()+$p.StandardError.ReadToEnd();$p.WaitForExit()
 [IO.File]::WriteAllText((Join-Path $out ($name+'.txt')),$text)
 $ok=(($p.ExitCode -eq 0) -eq $expectOk) -and (-not $expectText -or $text.Contains($expectText)) -and -not ($text.Contains($dbPassword) -or $text.Contains($pinKey) -or $text.Contains($adminPassword))
 $cases.Add(@{case=$name;status=if($ok){'PASS'}else{'FAIL'};exitCode=$p.ExitCode})
}
function Native([string]$exe,[string]$arguments,[string]$name,[switch]$NoCapture){
 # pg_ctl start leaves postgres holding inherited pipes; capturing them would wait forever.
 $p=New-Object Diagnostics.Process;$p.StartInfo.FileName=$exe;$p.StartInfo.Arguments=$arguments;$p.StartInfo.UseShellExecute=$false
 $p.StartInfo.RedirectStandardOutput=-not $NoCapture;$p.StartInfo.RedirectStandardError=-not $NoCapture;$p.StartInfo.EnvironmentVariables['PGPASSWORD']=$dbPassword
 [void]$p.Start();if(-not $NoCapture){$a=$p.StandardOutput.ReadToEndAsync();$b=$p.StandardError.ReadToEndAsync()};$p.WaitForExit()
 if(-not $NoCapture){[IO.File]::WriteAllText((Join-Path $out ($name+'.private.log')),$a.Result+$b.Result)};return $p.ExitCode
}
function Sql([string]$query,[string]$name){
 if((Native (Join-Path $PostgresBin 'psql.exe') ('-h 127.0.0.1 -p '+$dbPort+' -U '+$dbUser+' -d '+$dbName+' -At -c "'+$query+'"') $name) -ne 0){throw ('psql '+$name)}
 return [IO.File]::ReadAllText((Join-Path $out ($name+'.private.log'))).Trim()
}
function StopTree([Diagnostics.Process]$entry){
 if(-not $entry){return}
 foreach($child in @(Get-CimInstance Win32_Process -Filter ("ParentProcessId="+$entry.Id+" AND Name='java.exe'"))){$j=Get-Process -Id $child.ProcessId -ErrorAction SilentlyContinue;if($j -and -not $j.HasExited){$j.Kill();$j.WaitForExit()}}
 if(-not $entry.HasExited){if(-not $entry.WaitForExit(10000)){$entry.Kill();$entry.WaitForExit()}}
}
function AwaitHttp([string]$base,[Diagnostics.Process]$process,[int]$seconds){
 $until=[DateTime]::UtcNow.AddSeconds($seconds)
 while(-not $process.HasExited -and [DateTime]::UtcNow -lt $until){
  try{[void](Invoke-WebRequest ($base+'/api/v1/auth/me') -UseBasicParsing -TimeoutSec 2)}catch{if($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 401){return $true}}
  Start-Sleep -Milliseconds 250
 }
 return $false
}
function Login([string]$base,[string]$user,[string]$password){
 $body=@{username=$user;password=$password;requestId=[Guid]::NewGuid().ToString()}|ConvertTo-Json -Compress
 return Invoke-RestMethod ($base+'/api/v1/auth/login') -Method Post -ContentType 'application/json; charset=utf-8' -Body $body -TimeoutSec 10
}
function LoginStatus([string]$base,[string]$user,[string]$password){
 try{[void](Login $base $user $password);return 200}catch{if($_.Exception.Response){return [int]$_.Exception.Response.StatusCode};throw}
}
$data=Join-Path $out 'pgdata';$pw=Join-Path $out 'password.private';$started=$false;$server=$null;$old=$null;$failure=$null;$stop=$null
try{
 # Fail-closed configuration checks (no network connection is made in -ValidateOnly).
 Validate 'validate-unicode-server-env' @{} $null $true 'validation PASS'
 Validate 'reject-missing-pin-key' @{TOEIC_ROOM_PIN_KEY=''} $null $false 'Missing TOEIC_ROOM_PIN_KEY'
 Validate 'reject-short-pin-key' @{TOEIC_ROOM_PIN_KEY='c2hvcnQ'} $null $false 'at least 32 random bytes'
 Validate 'reject-relative-media-root' @{TOEIC_AUDIO_ROOT='audio-resources'} $null $false 'absolute folder path'
 Validate 'reject-missing-media-root' @{TOEIC_IMAGE_ROOT=(Join-Path $media 'missing')} $null $false 'does not exist'
 Validate 'process-env-overrides-file' @{} @{TOEIC_ROOM_PIN_KEY='c2hvcnQ'} $false 'at least 32 random bytes'
 WriteEnv @{}
 [IO.File]::WriteAllText($pw,$dbPassword,[Text.Encoding]::ASCII)
 if((Native (Join-Path $PostgresBin 'initdb.exe') ('-D "'+$data+'" -U '+$dbUser+' --auth=scram-sha-256 --pwfile="'+$pw+'" --no-locale -E UTF8') 'initdb') -ne 0){throw 'initdb'}
 Remove-Item -LiteralPath $pw
 if((Native (Join-Path $PostgresBin 'pg_ctl.exe') ('-D "'+$data+'" -l "'+$out+'\postgres.private.log" -o "-h 127.0.0.1 -p '+$dbPort+'" -w start') 'pg-start' -NoCapture) -ne 0){throw 'pg start'}
 $started=$true
 if((Native (Join-Path $PostgresBin 'createdb.exe') ('-h 127.0.0.1 -p '+$dbPort+' -U '+$dbUser+' -T template0 -E UTF8 '+$dbName) 'createdb') -ne 0){throw 'createdb'}
 $base='http://127.0.0.1:'+$httpPort
 $upgradeUsers=$null
 if($UpgradeFromServerJar){
  # Previous release creates and seeds the schema first; this is the "existing database" being upgraded.
  $oldJava=Join-Path $UpgradeJavaHome 'bin\java.exe'
  $oldEnv=@{};foreach($k in $names){$oldEnv[$k]=$settings[$k]};$oldEnv.Remove("TOEIC_ADMIN_BOOTSTRAP_USERNAME");$oldEnv.Remove("TOEIC_ADMIN_BOOTSTRAP_PASSWORD");$oldEnv.Remove("TOEIC_ROOM_PIN_KEY");$oldEnv['TOEIC_SEED_PASSWORD']=$seed
  $old=Launch $oldJava ('-Duser.timezone=UTC -jar "'+$UpgradeFromServerJar+'" --server.address=127.0.0.1 --server.port='+$httpPort) $oldEnv
  $oldOut=$old.StandardOutput.ReadToEndAsync();$oldErr=$old.StandardError.ReadToEndAsync()
  if(-not(AwaitHttp $base $old 120)){throw 'old server startup'}
  $proctor=Login $base 'proctor1' $seed;if($proctor.user.role -ne 'PROCTOR'){throw 'old proctor login'}
  $upgradeFrom=Sql "SELECT max(version::int) FROM flyway_schema_history WHERE success" 'old-version'
  $upgradeMaxId=Sql "SELECT max(id) FROM user_accounts" 'old-max-id'
  $upgradeUsers=Sql "SELECT count(*) FROM user_accounts" 'old-users'
  # Fingerprint of existing identities and credential hashes; only the digest reaches the log.
  $upgradeDigest=Sql "SELECT md5(string_agg(id||':'||username||':'||role||':'||password_hash,'|' ORDER BY id)) FROM user_accounts" 'old-digest'
  $old.Kill();$old.WaitForExit();[IO.File]::WriteAllText((Join-Path $out 'old-server.private.log'),$oldOut.Result+$oldErr.Result)
  $cases.Add(@{case='previous-server-created-schema';status='PASS';schemaVersion=$upgradeFrom;users=[int]$upgradeUsers})
 }
 # A stray development opt-in in the operator's environment must not create demo accounts in a release.
 $server=Launch (Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe') ('-NoProfile -ExecutionPolicy Bypass -File "'+(Join-Path $copy 'start-release.ps1')+'" -Role Server -BindAddress 127.0.0.1 -Port '+$httpPort) @{TOEIC_SEED_DEMO_ACCOUNTS='true';TOEIC_SEED_PASSWORD=$seed}
 $stdout=$server.StandardOutput.ReadToEndAsync();$stderr=$server.StandardError.ReadToEndAsync()
 if(-not(AwaitHttp $base $server 120)){throw 'Server HTTP startup'}
 $cases.Add(@{case='bundled-server-from-unicode-path-http-auth-guard';status='PASS'})
 $versions=Sql "SELECT string_agg(version,',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success AND version IN ('19','20','21')" 'migrations'
 $failed=Sql "SELECT count(*) FROM flyway_schema_history WHERE NOT success" 'migration-failures'
 $cases.Add(@{case='flyway-v19-v20-v21-applied';status=if($versions -eq '19,20,21' -and $failed -eq '0'){'PASS'}else{'FAIL'};applied=$versions})
 $admin=Login $base 'release-admin' $adminPassword
 $cases.Add(@{case='admin-bootstrap-login';status=if($admin.user.role -eq 'ADMIN'){'PASS'}else{'FAIL'}})
 $headers=@{Authorization=('Bearer '+$admin.token)}
 $page=Invoke-RestMethod ($base+'/api/v2/admin/accounts') -Headers $headers -TimeoutSec 10
 $cases.Add(@{case='admin-account-list';status=if($page){'PASS'}else{'FAIL'}})
 $demoRows=Sql "SELECT count(*) FROM user_accounts WHERE username IN ('candidate1','candidate2','proctor1')" 'demo-accounts'
 $demoLogin=LoginStatus $base 'proctor1' $seed
 if($UpgradeFromServerJar){
  # Accounts the older Server created stay usable (the digest check below compares every field).
  $cases.Add(@{case='upgrade-keeps-existing-demo-accounts';status=if($demoRows -eq '3' -and $demoLogin -eq 200){'PASS'}else{'FAIL'};demoAccounts=[int]$demoRows;proctor1Login=$demoLogin})
 }else{
  $users=Sql "SELECT string_agg(username||':'||role,',' ORDER BY id) FROM user_accounts" 'fresh-users'
  $cases.Add(@{case='fresh-release-database-has-no-demo-accounts';status=if($demoRows -eq '0' -and $demoLogin -eq 401 -and $users -eq 'release-admin:ADMIN'){'PASS'}else{'FAIL'};demoAccounts=[int]$demoRows;proctor1Login=$demoLogin})
 }
 # Release handover path: the bootstrapped Admin creates the Proctor; its one-time password is never written out.
 $createdBody=@{username='release-proctor';displayName='Release Proctor';role='PROCTOR'}|ConvertTo-Json -Compress
 $created=Invoke-RestMethod ($base+'/api/v2/admin/accounts') -Method Post -ContentType 'application/json; charset=utf-8' -Body $createdBody -Headers $headers -TimeoutSec 10
 $proctor=Login $base 'release-proctor' $created.initialPassword
 $cases.Add(@{case='admin-created-proctor-login';status=if($proctor.user.role -eq 'PROCTOR'){'PASS'}else{'FAIL'}})
 if($ImportJson){
  $examId=([IO.File]::ReadAllText($ImportJson,[Text.Encoding]::UTF8)|ConvertFrom-Json).exam.examId
  $import=Invoke-WebRequest ($base+'/api/v2/exams/import') -Method Post -ContentType 'application/json; charset=utf-8' -InFile $ImportJson -Headers @{Authorization=('Bearer '+$proctor.token)} -UseBasicParsing -TimeoutSec 120
  $cases.Add(@{case='proctor-imports-exam';status=if($import.StatusCode -eq 201){'PASS'}else{'FAIL'};examId=$examId})
  $catalog=Invoke-RestMethod ($base+'/api/v2/exams') -Headers @{Authorization=('Bearer '+$proctor.token)} -TimeoutSec 30
  $item=@($catalog.items|Where-Object {$_.examId -eq $examId})|Select-Object -First 1
  $cases.Add(@{case='imported-exam-selectable-with-media-from-unicode-root';status=if($item -and $item.selectable -and $item.totalQuestions -eq 200){'PASS'}else{'FAIL'};mediaVerification=$item.mediaVerification;totalQuestions=$item.totalQuestions})
 }
 if($UpgradeFromServerJar){
  $after=Sql ("SELECT md5(string_agg(id||':'||username||':'||role||':'||password_hash,'|' ORDER BY id)) FROM user_accounts WHERE id<="+$upgradeMaxId) 'upgraded-digest'
  $cases.Add(@{case='upgrade-preserves-existing-accounts';status=if($after -eq $upgradeDigest){'PASS'}else{'FAIL'};existingAccounts=[int]$upgradeUsers})
 }
 # A second Server on the same port must be refused before Java starts.
 $second=Launch (Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe') ('-NoProfile -ExecutionPolicy Bypass -File "'+(Join-Path $copy 'start-release.ps1')+'" -Role Server -BindAddress 127.0.0.1 -Port '+$httpPort) $null
 $text=$second.StandardOutput.ReadToEnd()+$second.StandardError.ReadToEnd();$secondExited=$second.WaitForExit(30000);if(-not $secondExited){StopTree $second}
 $cases.Add(@{case='reject-port-in-use';status=if($secondExited -and $second.ExitCode -ne 0 -and $text.Contains('already in use')){'PASS'}else{'FAIL'}})
 # Unreachable PostgreSQL is reported plainly (process env wins over server.env).
 $third=Launch (Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe') ('-NoProfile -ExecutionPolicy Bypass -File "'+(Join-Path $copy 'start-release.ps1')+'" -Role Server -BindAddress 127.0.0.1 -Port '+(Port)) @{DB_PORT=[string]$closedPort}
 $text=$third.StandardOutput.ReadToEnd()+$third.StandardError.ReadToEnd();$thirdExited=$third.WaitForExit(30000);if(-not $thirdExited){StopTree $third}
 $cases.Add(@{case='reject-unreachable-postgres';status=if($thirdExited -and $third.ExitCode -ne 0 -and $text.Contains('Cannot reach PostgreSQL')){'PASS'}else{'FAIL'}})
}catch{$failure=$_.Exception.GetType().FullName;[IO.File]::WriteAllText((Join-Path $out 'failure.private.txt'),[string]$_)}finally{
 if($old -and -not $old.HasExited){$old.Kill();$old.WaitForExit()}
 if($server){StopTree $server;[IO.File]::WriteAllText((Join-Path $out 'server.private.log'),$stdout.Result+$stderr.Result)}
 if($started){$stop=Native (Join-Path $PostgresBin 'pg_ctl.exe') ('-D "'+$data+'" -w stop -m fast') 'pg-stop'}
 foreach($secret in @($pw,(Join-Path $copy 'server.env'))){if(Test-Path -LiteralPath $secret){Remove-Item -LiteralPath $secret}}
}
if($server){$log=[IO.File]::ReadAllText((Join-Path $out 'server.private.log'));$cases.Add(@{case='launcher-prints-server-ready';status=if($log.Contains('Server READY on port '+$httpPort)){'PASS'}else{'FAIL'}})}
$failedCases=@($cases|Where-Object {$_.status -ne 'PASS'}).Count
$result=[ordered]@{bundle=$Bundle;unicodeCopy=$true;upgradeFrom=if($UpgradeFromServerJar){'previous Server JAR'}else{$null};cases=$cases;failed=$failedCases;errorType=$failure;clusterStopExit=$stop
 scope='Portable Server launcher + bundled runtime + owned PostgreSQL; config checks, startup, migrations, Admin bootstrap, no demo accounts. Not an exam flow or LAN.'}
[IO.File]::WriteAllText((Join-Path $out 'result.json'),(($result|ConvertTo-Json -Depth 6).Replace("`r`n","`n")+"`n"),$utf8)
Write-Output ('Release server smoke: '+$out);Write-Output ('PASS='+(@($cases|Where-Object {$_.status -eq 'PASS'}).Count)+' FAIL='+$failedCases+' error='+$failure)
exit [int]($failedCases -ne 0 -or $failure -or $stop -ne 0)
