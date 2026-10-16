param([Parameter(Mandatory=$true)][string]$ImageDirectory,[string]$OutputDirectory,[switch]$AsciiDiagnostic)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$ImageDirectory=(Resolve-Path -LiteralPath $ImageDirectory).Path
foreach($file in @('ToeicMonitor.exe','AudioSmoke.exe','runtime/bin/server/jvm.dll','app/client-0.1.0-SNAPSHOT-all.jar')){
 if(-not(Test-Path -LiteralPath (Join-Path $ImageDirectory $file) -PathType Leaf)){throw "Missing QA image resource: $file"}
}
if(-not $OutputDirectory){$OutputDirectory=Join-Path $root ('jpackage-out/package-smoke-'+[Guid]::NewGuid().ToString('N'))}
if(-not[IO.Path]::IsPathRooted($OutputDirectory)){$OutputDirectory=Join-Path (Get-Location).Path $OutputDirectory}
if(Test-Path -LiteralPath $OutputDirectory){throw 'Không overwrite smoke cũ.'}
[void](New-Item -ItemType Directory -Path $OutputDirectory)
if($AsciiDiagnostic){$image=$ImageDirectory}else{
 $unicode=Join-Path $OutputDirectory 'Đóng gói có dấu và khoảng trắng';[void](New-Item -ItemType Directory -Path $unicode)
 Copy-Item -LiteralPath $ImageDirectory -Destination (Join-Path $unicode 'ToeicMonitor') -Recurse;$image=Join-Path $unicode 'ToeicMonitor'
}
$process=[Diagnostics.Process]::new();$process.StartInfo.FileName=Join-Path $image 'AudioSmoke.exe';$process.StartInfo.UseShellExecute=$false
$process.StartInfo.RedirectStandardOutput=$true;$process.StartInfo.RedirectStandardError=$true
$audioOutput=Join-Path $OutputDirectory 'Audio bằng chứng có dấu'
$process.StartInfo.EnvironmentVariables['TOEIC_AUDIO_SMOKE_OUTPUT']=$audioOutput
$process.StartInfo.EnvironmentVariables.Remove('JAVA_HOME');$process.StartInfo.EnvironmentVariables.Remove('JAVA_TOOL_OPTIONS');$process.StartInfo.EnvironmentVariables.Remove('JDK_JAVA_OPTIONS');$process.StartInfo.EnvironmentVariables.Remove('_JAVA_OPTIONS')
$process.StartInfo.EnvironmentVariables['PATH']="$env:SystemRoot/System32;$env:SystemRoot"
try {
 [void]$process.Start();$stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
 if(-not$process.WaitForExit(90000)){$process.Kill();throw 'Owned TEST launcher timeout.'}
 $process.WaitForExit();[IO.File]::WriteAllText((Join-Path $OutputDirectory 'stdout.txt'),$stdout.Result);[IO.File]::WriteAllText((Join-Path $OutputDirectory 'stderr.txt'),$stderr.Result)
 if($process.ExitCode -ne 0){throw "Package audio smoke FAIL exit=$($process.ExitCode)"}
 $result=Join-Path $audioOutput 'result.txt';if(-not(Test-Path -LiteralPath $result)){throw 'Thiếu assertions, không chấp nhận launch-only.'}
 Get-Content -LiteralPath $result -Encoding UTF8
 [ordered]@{asciiDiagnostic=[bool]$AsciiDiagnostic;clientJarSha256=(Get-FileHash (Join-Path $image 'app/client-0.1.0-SNAPSHOT-all.jar') -Algorithm SHA256).Hash.ToLowerInvariant();runtime='bundled, JAVA_HOME removed';hearing='SIMULATED';apiWs='MOCK';installer='NOT RUN';unicodeLauncher=if($AsciiDiagnostic){'NOT RUN'}else{'REAL'}} | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $OutputDirectory 'smoke-metadata.json')
 Write-Output 'REAL bundled runtime/JavaFX Media WAV; API/WS MOCK. Human hearing/LAN/install/uninstall NOT RUN.'
}finally{$process.Dispose()}
