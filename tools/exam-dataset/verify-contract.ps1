[CmdletBinding()]
param(
    [string]$Ref = 'origin/main',
    [string]$Draft = '',
    [string]$GitDirectory = '',
    [string]$MainWorktree = ''
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'native-process.ps1')
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (!$Draft) { $Draft = Join-Path $PSScriptRoot 'target/output/ets2026_test01/draft.v2.json' }
if (!$GitDirectory) {
    $gitResult = Invoke-Ex01Native -Command 'git.exe' -Arguments @('-C', $repo, 'rev-parse', '--git-common-dir')
    if ($gitResult.ExitCode -ne 0) { throw 'Cannot resolve git common directory; pass -GitDirectory explicitly.' }
    $common = $gitResult.Output.Trim()
    if ([IO.Path]::IsPathRooted($common)) { $GitDirectory = $common } else { $GitDirectory = Join-Path $repo $common }
}
$target = Join-Path $PSScriptRoot 'target/contract'
$src = Join-Path $target 'src'
$classes = Join-Path $target 'classes'
New-Item -ItemType Directory -Force -Path $src,$classes | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding($false)
$gitResult = Invoke-Ex01Native -Command 'git.exe' -Arguments @("--git-dir=$GitDirectory", 'rev-parse', $Ref)
if ($gitResult.ExitCode -ne 0) { throw 'Cannot resolve contract ref.' }
$sha = $gitResult.Output.Trim()
if ($MainWorktree) {
    $actual = Invoke-Ex01Native -Command 'git.exe' -Arguments @('-C',$MainWorktree,'rev-parse','HEAD')
    if ($actual.ExitCode -ne 0 -or $actual.Output.Trim() -ne $sha) { throw 'MainWorktree HEAD differs from contract ref.' }
    $serverClasses=Join-Path $MainWorktree 'server/target/classes'
    $protocolClasses=Join-Path $MainWorktree 'protocol/target/classes'
    if (!(Test-Path (Join-Path $serverClasses 'vn/edu/toeic/server/exam/rich/RichExamValidator.class'))) {throw 'Build main server first.'}
    $serverJar=Join-Path $MainWorktree 'server/target/server-0.1.0-SNAPSHOT.jar'
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip=[IO.Compression.ZipFile]::OpenRead($serverJar)
    try {
        $entries=@($zip.Entries | Where-Object {$_.FullName -match '^BOOT-INF/lib/spring-web-[0-9].*\.jar$'})
        if($entries.Count -ne 1){throw 'Expected one spring-web runtime dependency.'}
        $springWeb=Join-Path $target $entries[0].Name
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entries[0],$springWeb,$true)
    } finally {$zip.Dispose()}
    $cp=$serverClasses+[IO.Path]::PathSeparator+$protocolClasses+[IO.Path]::PathSeparator+$springWeb+[IO.Path]::PathSeparator+([IO.File]::ReadAllText((Join-Path $PSScriptRoot 'target/runtime-classpath.txt')).Trim())
    $compile=Invoke-Ex01Native -Command 'javac.exe' -Arguments @('-encoding','UTF-8','-cp',$cp,'-d',$classes,(Join-Path $PSScriptRoot 'contract/ContractProbe.java'))
    if($compile.ExitCode -ne 0){[Console]::Error.WriteLine($compile.Error);exit $compile.ExitCode}
    Write-Output "CONTRACT_REF=$sha MODE=COMPILED_MAIN_CLASSES_NO_ADAPTERS"
    # Main classes precede classes/ so prior source-adapter probes cannot shadow production classes.
    $probe=Invoke-Ex01Native -Command 'java.exe' -Arguments @('-cp',($cp+[IO.Path]::PathSeparator+$classes),'ContractProbe',$Draft)
    [Console]::Out.WriteLine($probe.Output)
    if($probe.Error){[Console]::Error.WriteLine($probe.Error)}
    exit $probe.ExitCode
}
$names = @('RichExamImportRequest','ExamMetadata','SourceImport','GroupImport','MediaImport','QuestionImport','ContentBlock','DocumentContent','OptionContent','ScoringKey','Verification','StorageStatus','Section','PermissionStatus','MediaType','BlockType')
foreach ($name in $names) {
    $gitResult = Invoke-Ex01Native -Command 'git.exe' -Arguments @("--git-dir=$GitDirectory", 'show', "${sha}:protocol/src/main/java/vn/edu/toeic/protocol/exam/rich/$name.java")
    if ($gitResult.ExitCode -ne 0) { throw "Cannot read DTO $name" }
    [IO.File]::WriteAllText((Join-Path $src "$name.java"), $gitResult.Output, $utf8)
}
$gitResult = Invoke-Ex01Native -Command 'git.exe' -Arguments @("--git-dir=$GitDirectory", 'show', "${sha}:server/src/main/java/vn/edu/toeic/server/exam/rich/RichExamValidator.java")
if ($gitResult.ExitCode -ne 0) { throw 'Cannot read real validator.' }
[IO.File]::WriteAllText((Join-Path $src 'RichExamValidator.java'), $gitResult.Output, $utf8)
# Minimal compile adapters only: no Spring server or database is simulated.
[IO.File]::WriteAllText((Join-Path $src 'Component.java'), 'package org.springframework.stereotype; public @interface Component {}', $utf8)
[IO.File]::WriteAllText((Join-Path $src 'ExamApiException.java'), 'package vn.edu.toeic.server.exam; public class ExamApiException extends IllegalArgumentException { public ExamApiException(String s) {super(s);} public static ExamApiException invalidInput(String s) {return new ExamApiException(s);} }', $utf8)
$cp = (Get-Content (Join-Path $PSScriptRoot 'target/runtime-classpath.txt') -Raw).Trim()
$sources = @(Get-ChildItem $src -Filter '*.java' | ForEach-Object { $_.FullName })
$compile = Invoke-Ex01Native -Command 'javac.exe' -Arguments (@('-encoding', 'UTF-8', '-cp', $cp, '-d', $classes) + $sources + @((Join-Path $PSScriptRoot 'contract/ContractProbe.java')))
if ($compile.Error) { [Console]::Error.WriteLine($compile.Error) }
if ($compile.ExitCode -ne 0) { exit $compile.ExitCode }
Write-Output "CONTRACT_REF=$sha"
$probe = Invoke-Ex01Native -Command 'java.exe' -Arguments @('-cp', ($classes + [IO.Path]::PathSeparator + $cp), 'ContractProbe', $Draft)
[Console]::Out.WriteLine($probe.Output)
if ($probe.Error) { [Console]::Error.WriteLine($probe.Error) }
exit $probe.ExitCode
