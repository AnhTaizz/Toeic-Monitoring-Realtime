[CmdletBinding()]
param(
 [Parameter(Mandatory=$true)][ValidateSet('recover')][string]$Action,
 [string]$OutputPath='',
 [string]$Normalized='',
 [string[]]$Supplement=@()
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'native-process.ps1')
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$classpathFile=Join-Path $PSScriptRoot 'target/runtime-classpath.txt'
if(!(Test-Path $classpathFile) -or !(Test-Path (Join-Path $PSScriptRoot 'target/classes/vn/edu/toeic/dataset/RecoverExam.class'))){throw 'Build exam-dataset and runtime classpath first.'}
$cp=(Join-Path $PSScriptRoot 'target/classes')+[IO.Path]::PathSeparator+[IO.File]::ReadAllText($classpathFile).Trim()
$arguments=@('-cp',$cp,'vn.edu.toeic.dataset.RecoverExam','--root',$repo)
if($OutputPath){$arguments+=@('--output',$OutputPath)}
if($Normalized){$arguments+=@('--normalized',$Normalized)}
foreach($source in $Supplement){$arguments+=@('--supplement',$source)}
$result=Invoke-Ex01Native -Command 'java.exe' -Arguments $arguments
[Console]::Out.WriteLine($result.Output)
if($result.Error){[Console]::Error.WriteLine($result.Error)}
exit $result.ExitCode
