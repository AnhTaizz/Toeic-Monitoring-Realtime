[CmdletBinding()]
param(
 [string]$Pack='',
 [Parameter(Mandatory=$true)][string]$PostgresBin,
 [Parameter(Mandatory=$true)][string]$Database,
 [Parameter(Mandatory=$true)][string]$ConfirmDatabase,
 [Parameter(Mandatory=$true)][long]$ProctorUserId,
 [Parameter(Mandatory=$true)][string]$Destination,
 [string]$HostName='127.0.0.1',
 [Parameter(Mandatory=$true)][int]$Port,
 [Parameter(Mandatory=$true)][string]$UserName
)
# Standalone runtime: pack + PostgreSQL client; no repository, HTML, website or Java required.
$runtimeRoot=Split-Path -Parent $MyInvocation.MyCommand.Path
if([string]::IsNullOrWhiteSpace($Pack)){$Pack=$runtimeRoot}
. (Join-Path $runtimeRoot 'exam-seed-common.ps1')
Assert-SeedDatabase $Database $HostName $Port
if($ConfirmDatabase -cne $Database -or $ProctorUserId -lt 1){throw 'Exact TEST database confirmation and target PROCTOR id required.'}
$Pack=[IO.Path]::GetFullPath($Pack);$Destination=[IO.Path]::GetFullPath($Destination)
$m=Seed-VerifyPack $Pack
Assert-SeedNewExternal $Destination
$psql=Resolve-SeedPg $PostgresBin 'psql';$connection=Seed-Connection $Database $HostName $Port $UserName
$schema=Seed-Query $psql $connection (Seed-SchemaSql)
# Compare the schema structure, not object key order or whitespace.
$actual=$schema|ConvertFrom-Json
if(($actual|ConvertTo-Json -Depth 8 -Compress) -cne ($m.schemaColumns|ConvertTo-Json -Depth 8 -Compress)){throw 'Flyway schema differs from seed pack.'}
$flyway=Seed-Query $psql $connection "SELECT count(*) FROM public.flyway_schema_history WHERE success AND version IN ('14','15')"
if($flyway -ne '2'){throw 'Flyway V14/V15 must be applied before restoring exam data.'}
$exists=Seed-Query $psql $connection ("SELECT count(*) FROM public.exams WHERE exam_id='"+$m.examId+"'")
if($exists -ne '0'){throw 'Seed examId already exists; no overwrite.'}
$proctor=Seed-Query $psql $connection ("SELECT count(*) FROM public.user_accounts WHERE id=$ProctorUserId AND role='PROCTOR' AND enabled")
if($proctor -ne '1'){throw 'Existing enabled target PROCTOR required.'}
New-Item -ItemType Directory -Path $Destination | Out-Null
foreach($entry in $m.files|Where-Object {$_.path -match '^(audio-resources|image-resources)/'}){
 $target=Join-Path $Destination $entry.path
 New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
 Copy-Item -LiteralPath (Join-Path $Pack $entry.path) -Destination $target
 if((Seed-Hash $target) -cne $entry.sha256){throw 'Copied media hash mismatch; database unchanged.'}
}
$wrapper=Join-Path $Destination ('restore-'+[Guid]::NewGuid().ToString('N')+'.sql')
try{
 $sql="SET LOCAL toeic.seed.proctor_id = '$ProctorUserId';`n"+[IO.File]::ReadAllText((Join-Path $Pack 'seed.sql'))
 [IO.File]::WriteAllText($wrapper,$sql,(New-Object Text.UTF8Encoding($false)))
 $result=Invoke-SeedNative $psql ($connection+@('-X','--single-transaction','-v','ON_ERROR_STOP=1','--file',$wrapper))
 if($result.ExitCode -ne 0){throw 'Seed transaction failed/rolled back; no overwrite. SQL and diagnostics suppressed.'}
 $counts=[ordered]@{}
 foreach($table in $script:SeedTables){$counts[$table]=[int](Seed-Query $psql $connection ("SELECT count(*) FROM public.$table WHERE exam_id='"+$m.examId+"'"));if($counts[$table] -ne $m.counts.$table){throw 'Post-restore count mismatch; preserve TEST database for inspection.'}}
 $receipt=[ordered]@{formatVersion=1;scope='EXAM_DATA_ONLY';examId=$m.examId;seedSqlSha256=(Seed-Hash (Join-Path $Pack 'seed.sql'));counts=$counts;mediaCount=$m.mediaCount;htmlRequired=$false;repositoryRequired=$false;catalog='VERIFY_WITH_BACKEND_NEXT';transactionCommitted=$true}
 Seed-WriteJson (Join-Path $Destination 'restore-receipt.json') $receipt
 Write-Output ('PORTABLE_SEED_RESTORE_PASS exam='+$m.examId+' questions='+$counts.exam_questions+' media='+$m.mediaCount)
}finally{if(Test-Path -LiteralPath $wrapper){Remove-Item -LiteralPath $wrapper}}
