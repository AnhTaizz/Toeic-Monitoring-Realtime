[CmdletBinding()]
param(
    [string]$Ref = 'origin/feat/ex06-javafx-rich-renderer-20261020',
    [string]$Draft = '',
    [string]$GitDirectory = ''
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
