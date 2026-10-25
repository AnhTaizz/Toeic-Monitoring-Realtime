param([Parameter(Mandatory=$true)][string]$Bundle,[string]$OutputDirectory,[switch]$UnicodeCopy,[switch]$AudioSmoke)
$ErrorActionPreference='Stop'
if(-not $OutputDirectory){$OutputDirectory=Join-Path (Split-Path -Parent $Bundle) ('smoke-'+[Guid]::NewGuid().ToString('N'))}
if(Test-Path -LiteralPath $OutputDirectory){throw 'Smoke output already exists'}
New-Item -ItemType Directory -Force $OutputDirectory|Out-Null
if($UnicodeCopy){
 $unicode=Join-Path $OutputDirectory 'Kiểm thử có dấu và khoảng trắng'
 New-Item -ItemType Directory -Force $unicode|Out-Null
 $copy=Join-Path $unicode 'ToeicPortable'
 Copy-Item -LiteralPath $Bundle -Destination $copy -Recurse
 $Bundle=$copy
}
$launcher=Join-Path $Bundle 'start-release.ps1'
$results=New-Object Collections.Generic.List[object]
function Child([string]$arguments,[string]$name){
 $p=New-Object Diagnostics.Process
 $p.StartInfo.FileName=(Get-Process -Id $PID).Path;$p.StartInfo.Arguments=$arguments
 $p.StartInfo.UseShellExecute=$false;$p.StartInfo.RedirectStandardOutput=$true;$p.StartInfo.RedirectStandardError=$true
 $p.StartInfo.EnvironmentVariables['PATH']=$env:SystemRoot+'\System32;'+$env:SystemRoot
 $p.StartInfo.EnvironmentVariables.Remove('JAVA_HOME');$p.StartInfo.EnvironmentVariables.Remove('JAVA_TOOL_OPTIONS')
 [void]$p.Start();$a=$p.StandardOutput.ReadToEndAsync();$b=$p.StandardError.ReadToEndAsync();$p.WaitForExit()
 [IO.File]::WriteAllText((Join-Path $OutputDirectory ($name+'.txt')),$a.Result+$b.Result)
 return $p.ExitCode
}
foreach($case in @(@{name='origin';url='http://127.0.0.1:8080';ok=$true},@{name='path-denied';url='http://127.0.0.1:8080/api';ok=$false},@{name='credential-denied';url='http://MOCK:MOCK@127.0.0.1:8080';ok=$false},@{name='query-denied';url='http://127.0.0.1:8080/?token=MOCK';ok=$false},@{name='scheme-denied';url='file:///C:/MOCK';ok=$false})){
 $exit=Child ('-NoProfile -ExecutionPolicy Bypass -File "'+$launcher+'" -Role Client -ServerUrl "'+$case.url+'" -ValidateOnly') $case.name
 $pass=($exit -eq 0) -eq $case.ok
 $results.Add(@{case=$case.name;status=if($pass){'PASS'}else{'FAIL'};exitCode=$exit})
}
# Prove Server fails closed without required configuration, never connect to an existing DB.
$names=@('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','TOEIC_SEED_PASSWORD','TOEIC_AUDIO_ROOT','TOEIC_IMAGE_ROOT')
$saved=@{};foreach($n in $names){$saved[$n]=[Environment]::GetEnvironmentVariable($n,'Process');[Environment]::SetEnvironmentVariable($n,$null,'Process')}
try{$exit=Child ('-NoProfile -ExecutionPolicy Bypass -File "'+$launcher+'" -Role Server -ValidateOnly') 'server-missing-config';$results.Add(@{case='server-missing-config';status=if($exit -ne 0){'PASS'}else{'FAIL'};exitCode=$exit})}finally{foreach($n in $saved.Keys){[Environment]::SetEnvironmentVariable($n,$saved[$n],'Process')}}
# Launch the production JAR with its bundled runtime. This is a login-window smoke, not exam acceptance.
$java=Join-Path $Bundle 'runtime\bin\java.exe'
if(-not(Test-Path $java)){throw 'Bundled runtime is required for this smoke'}
$entry=New-Object Diagnostics.Process
$entry.StartInfo.FileName=Join-Path $env:SystemRoot 'System32\cmd.exe'
$entry.StartInfo.Arguments='/d /s /c ""'+(Join-Path $Bundle 'Start-Client.bat')+'" -ServerUrl http://127.0.0.1:8080"'
$entry.StartInfo.WorkingDirectory=$Bundle;$entry.StartInfo.UseShellExecute=$false;$entry.StartInfo.RedirectStandardOutput=$true;$entry.StartInfo.RedirectStandardError=$true
$entry.StartInfo.EnvironmentVariables['PATH']=$env:SystemRoot+'\System32;'+$env:SystemRoot
$entry.StartInfo.EnvironmentVariables.Remove('JAVA_HOME');$entry.StartInfo.EnvironmentVariables.Remove('JAVA_TOOL_OPTIONS')
[void]$entry.Start();$go=$entry.StandardOutput.ReadToEndAsync();$ge=$entry.StandardError.ReadToEndAsync();$until=[DateTime]::UtcNow.AddSeconds(60);$visible=$false;$gui=$null;$closedCleanly=$false
try{
 while(-not $entry.HasExited -and [DateTime]::UtcNow -lt $until){
  $parents=@($entry.Id)+@((Get-CimInstance Win32_Process -Filter ("ParentProcessId="+$entry.Id+" AND Name='powershell.exe'")).ProcessId)
  foreach($ownedParentId in $parents){if(-not $ownedParentId){continue}
  foreach($child in @(Get-CimInstance Win32_Process -Filter ("ParentProcessId="+$ownedParentId+" AND Name='java.exe'"))){
   $candidate=Get-Process -Id $child.ProcessId -ErrorAction SilentlyContinue
   if($candidate -and -not $candidate.HasExited){$candidate.Refresh();if($candidate.MainWindowHandle -ne 0 -and $candidate.MainWindowTitle -eq 'TOEIC Room'){$gui=$candidate;$visible=$true;break}}
  }
  }
  if($visible){break};Start-Sleep -Milliseconds 200
 }
 $sample=$null
 if($visible){$gui.Refresh();$cpuStart=$gui.TotalProcessorTime.TotalMilliseconds;$watch=[Diagnostics.Stopwatch]::StartNew();Start-Sleep -Seconds 5;$gui.Refresh();$watch.Stop();$cpuMs=$gui.TotalProcessorTime.TotalMilliseconds-$cpuStart
  $sample=@{scope='production desktop login window, five-second post-open sample; not exam load';elapsedMs=$watch.Elapsed.TotalMilliseconds;cpuMs=$cpuMs;cpuOneCorePercent=100*$cpuMs/$watch.Elapsed.TotalMilliseconds;workingSetBytes=$gui.WorkingSet64}}
 $results.Add(@{case='bundled-production-login-window';status=if($visible){'PASS'}else{'FAIL'};windowObserved=$visible;expectedTitle='TOEIC Room';through='Start-Client.bat -> start-release.ps1';resourceSample=$sample})
}finally{
 if($gui -and -not $gui.HasExited){[void]$gui.CloseMainWindow();$closedCleanly=$gui.WaitForExit(10000);if(-not $closedCleanly){$gui.Kill();$gui.WaitForExit()}}
 if(-not $entry.WaitForExit(5000)){$entry.Kill();$entry.WaitForExit()}
 [IO.File]::WriteAllText((Join-Path $OutputDirectory 'client.private.log'),$go.Result+$ge.Result)
}
$results.Add(@{case='launcher-graceful-exit';status=if($visible -and $closedCleanly -and $entry.ExitCode -eq 0){'PASS'}else{'FAIL'};exitCode=$entry.ExitCode})
if($AudioSmoke){
 $classes=Join-Path ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))) 'client\target\test-classes'
 if(-not(Test-Path (Join-Path $classes 'vn\edu\toeic\client\audio\AudioPackageSmoke.class'))){throw 'Compile existing AudioPackageSmoke QA harness first'}
 $audioOut=Join-Path $OutputDirectory 'audio-qa'
 $audio=New-Object Diagnostics.Process
 $audio.StartInfo.FileName=$java;$audio.StartInfo.Arguments='-cp "'+(Join-Path $Bundle 'client.jar')+';'+$classes+'" vn.edu.toeic.client.audio.AudioPackageSmoke'
 $audio.StartInfo.WorkingDirectory=$Bundle;$audio.StartInfo.UseShellExecute=$false;$audio.StartInfo.RedirectStandardOutput=$true;$audio.StartInfo.RedirectStandardError=$true
 $audio.StartInfo.EnvironmentVariables['TOEIC_AUDIO_SMOKE_OUTPUT']=$audioOut
 $audio.StartInfo.EnvironmentVariables['PATH']=$env:SystemRoot+'\System32;'+$env:SystemRoot
 $audio.StartInfo.EnvironmentVariables.Remove('JAVA_HOME');$audio.StartInfo.EnvironmentVariables.Remove('JAVA_TOOL_OPTIONS')
 [void]$audio.Start();$ao=$audio.StandardOutput.ReadToEndAsync();$ae=$audio.StandardError.ReadToEndAsync()
 $finished=$audio.WaitForExit(150000)
 if(-not $finished){$audio.Kill();$audio.WaitForExit()}
 [IO.File]::WriteAllText((Join-Path $OutputDirectory 'audio.private.log'),$ao.Result+$ae.Result)
 $results.Add(@{case='existing-audio-qa-under-bundled-runtime';status=if($finished -and $audio.ExitCode -eq 0){'PASS'}else{'FAIL'};exitCode=$audio.ExitCode;hearing='SIMULATED confirmation, not human hearing';api='MOCK; real JavaFX and local files'})
}
$failed=@($results|Where-Object {$_.status -eq 'FAIL'}).Count
@{bundle=$Bundle;cases=$results;failed=$failed;physicalSecondMachine='NOT_RUN';humanHearing='NOT_RUN';scope='Same-host Windows production login window and launcher validation, not packaged exam flow'}|ConvertTo-Json -Depth 6|Set-Content (Join-Path $OutputDirectory 'result.json') -Encoding UTF8
Write-Output ('Portable smoke output: '+$OutputDirectory)
Write-Output ('PASS='+(@($results|Where-Object {$_.status -eq 'PASS'}).Count)+' FAIL='+$failed)
exit [int]($failed -ne 0)
