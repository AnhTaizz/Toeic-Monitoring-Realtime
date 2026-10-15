param([Parameter(Mandatory=$true)][string]$PackageDirectory,[string]$OutputDirectory)
$ErrorActionPreference='Stop'
if($PackageDirectory -and -not[IO.Path]::IsPathRooted($PackageDirectory)){$PackageDirectory=Join-Path (Get-Location).Path $PackageDirectory}

if(-not $OutputDirectory){$OutputDirectory=Join-Path (Split-Path -Parent $PSScriptRoot) ('jpackage-out/package-smoke-'+[Guid]::NewGuid().ToString('N'))}
if($OutputDirectory -and -not[IO.Path]::IsPathRooted($OutputDirectory)){$OutputDirectory=Join-Path (Get-Location).Path $OutputDirectory}
if(Test-Path -LiteralPath $OutputDirectory){throw 'Không overwrite smoke cũ.'}
[void](New-Item -ItemType Directory -Path $OutputDirectory)
$manifest=Get-Content -LiteralPath (Join-Path $PackageDirectory 'package-manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
foreach($file in $manifest.files){if((Get-FileHash -LiteralPath (Join-Path $PackageDirectory $file.path) -Algorithm SHA256).Hash.ToLowerInvariant() -ne $file.sha256){throw 'Package checksum mismatch.'}}
$unicode=Join-Path $OutputDirectory 'Đóng gói có dấu và khoảng trắng'
[void](New-Item -ItemType Directory -Path $unicode)
Copy-Item -LiteralPath (Join-Path $PackageDirectory 'ToeicMonitor') -Destination $unicode -Recurse
$test=Join-Path $unicode 'ToeicMonitor/AudioSmoke.exe'
if(-not(Test-Path -LiteralPath $test)){throw 'Cần package -WithAudioSmoke; không coi process launch là audio PASS.'}
$process=[Diagnostics.Process]::new();$process.StartInfo.FileName=$test;$process.StartInfo.UseShellExecute=$false
$process.StartInfo.RedirectStandardOutput=$true;$process.StartInfo.RedirectStandardError=$true
$audioOutput=Join-Path $OutputDirectory 'Audio bằng chứng có dấu'
$process.StartInfo.EnvironmentVariables['TOEIC_AUDIO_SMOKE_OUTPUT']=$audioOutput
try {
 [void]$process.Start();$stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
 if(-not$process.WaitForExit(90000)){$process.Kill();throw 'Owned TEST launcher timeout.'}
 $process.WaitForExit();[IO.File]::WriteAllText((Join-Path $OutputDirectory 'stdout.txt'),$stdout.Result);[IO.File]::WriteAllText((Join-Path $OutputDirectory 'stderr.txt'),$stderr.Result)
 if($process.ExitCode -ne 0){throw "Package audio smoke FAIL exit=$($process.ExitCode)"}
 $result=Join-Path $audioOutput 'result.txt';if(-not(Test-Path -LiteralPath $result)){throw 'Thiếu assertion result, không chấp nhận launch-only.'}
 Get-Content -LiteralPath $result -Encoding UTF8
 Write-Output 'REAL packaged runtime/JavaFX Media WAV; API/WS MOCK theo AudioPackageSmoke. Human hearing/LAN/install/uninstall NOT RUN.'
}finally{$process.Dispose()}
