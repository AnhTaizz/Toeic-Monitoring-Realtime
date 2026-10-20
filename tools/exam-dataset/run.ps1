[CmdletBinding()]
param(
    [ValidateSet('01','02','03','04','all')][string]$Test = '01',
    [string]$OutputPath = '',
    [string]$AudioRoot = '',
    [string]$ImageRoot = '',
    [string]$Permissions = '',
    [switch]$Download,
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'native-process.ps1')
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (!$OutputPath) { $OutputPath = Join-Path $PSScriptRoot 'target/output' }
$buildArgs = @('-f', (Join-Path $PSScriptRoot 'pom.xml'), 'test', 'dependency:build-classpath', '-DincludeScope=runtime', '-Dmdep.outputFile=target/runtime-classpath.txt')
$javaApplication = (Get-Command 'java.exe' -CommandType Application -ErrorAction Stop).Source
$buildArgs += ('-Djvm=' + $javaApplication)
if ($Offline) { $buildArgs = @('-o') + $buildArgs }
$build = Invoke-Ex01Native -Command 'mvn.cmd' -Arguments $buildArgs
[Console]::Out.WriteLine($build.Output)
if ($build.Error) { [Console]::Error.WriteLine($build.Error) }
if ($build.ExitCode -ne 0) { exit $build.ExitCode }
$cp = (Join-Path $PSScriptRoot 'target/classes') + [IO.Path]::PathSeparator + (Get-Content (Join-Path $PSScriptRoot 'target/runtime-classpath.txt') -Raw).Trim()
$runArgs = @('-cp', $cp, 'vn.edu.toeic.dataset.ExamDataset', '--root', $repo, '--output', $OutputPath, '--test', $Test)
if ($AudioRoot) { $runArgs += @('--audio-root', $AudioRoot) }
if ($ImageRoot) { $runArgs += @('--image-root', $ImageRoot) }
if ($Permissions) { $runArgs += @('--permissions', $Permissions) }
if ($Download) { $runArgs += @('--download', 'true') }
$run = Invoke-Ex01Native -Command 'java.exe' -Arguments $runArgs
[Console]::Out.WriteLine($run.Output)
if ($run.Error) { [Console]::Error.WriteLine($run.Error) }
exit $run.ExitCode
