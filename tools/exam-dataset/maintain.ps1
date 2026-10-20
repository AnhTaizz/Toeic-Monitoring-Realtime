[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('recover','archive-create','archive-verify','archive-restore')][string]$Action,
    [string]$OutputPath = '',
    [string]$Normalized = '',
    [string[]]$Supplement = @(),
    [string]$Datasets = '',
    [string]$AudioRoot = '',
    [string]$ImageRoot = '',
    [string]$Archive = '',
    [string]$Destination = '',
    [string]$SourceCommit = 'UNRECORDED'
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'native-process.ps1')
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$classpathFile = Join-Path $PSScriptRoot 'target/runtime-classpath.txt'
if (!(Test-Path -LiteralPath $classpathFile) -or !(Test-Path (Join-Path $PSScriptRoot 'target/classes/vn/edu/toeic/dataset/RecoverExam.class'))) {
    throw 'Build tools/exam-dataset first: mvn test dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=target/runtime-classpath.txt'
}
$cp = (Join-Path $PSScriptRoot 'target/classes') + [IO.Path]::PathSeparator + [IO.File]::ReadAllText($classpathFile).Trim()
if ($Action -eq 'recover') {
    $arguments = @('-cp',$cp,'vn.edu.toeic.dataset.RecoverExam','--root',$repo)
    if ($OutputPath) {$arguments += @('--output',$OutputPath)}
    if ($Normalized) {$arguments += @('--normalized',$Normalized)}
    foreach ($source in $Supplement) {$arguments += @('--supplement',$source)}
} else {
    $arguments = @('-cp',$cp,'vn.edu.toeic.dataset.DatasetArchive',$Action.Replace('archive-',''))
    if ($Action -eq 'archive-create') {
        if (!$Datasets) {$Datasets=Join-Path $PSScriptRoot 'target/output'}
        if (!$AudioRoot) {$AudioRoot=Join-Path $repo 'audio-resources'}
        if (!$ImageRoot) {$ImageRoot=Join-Path $repo 'image-resources'}
        if (!$Destination) {throw 'New external Destination required.'}
        $arguments += @('--root',$repo,'--datasets',$Datasets,'--audio-root',$AudioRoot,'--image-root',$ImageRoot,'--destination',$Destination,'--source-commit',$SourceCommit)
    } else {
        if (!$Archive) {throw 'Archive required.'}
        $arguments += @('--archive',$Archive)
        if ($Action -eq 'archive-restore') {
            if (!$Destination) {throw 'New external Destination required.'}
            $arguments += @('--destination',$Destination)
        }
    }
}
$result = Invoke-Ex01Native -Command 'java.exe' -Arguments $arguments
[Console]::Out.WriteLine($result.Output)
if ($result.Error) {[Console]::Error.WriteLine($result.Error)}
exit $result.ExitCode
