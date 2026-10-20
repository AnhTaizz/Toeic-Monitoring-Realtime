[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$PostgresBin,
    [Parameter(Mandatory=$true)][string]$Database,
    [Parameter(Mandatory=$true)][string]$ConfirmRestoreDatabase,
    [Parameter(Mandatory=$true)][string]$InputFile,
    [string]$HostName = '127.0.0.1',
    [int]$Port = 5432,
    [Parameter(Mandatory=$true)][string]$UserName
)
. (Join-Path $PSScriptRoot 'toeic-db-common.ps1')
Assert-ToeicDatabaseName $Database
if ($Database -notmatch '^toeic_[a-z0-9_]*test_[a-z0-9_]+$' -or $ConfirmRestoreDatabase -cne $Database) { throw 'Restore requires a new toeic_*test_* database and an exact confirmation name.' }
$InputFile = (Resolve-Path -LiteralPath $InputFile).Path
$metadata = [IO.File]::ReadAllText($InputFile + '.metadata.json') | ConvertFrom-Json
if ($metadata.producer -ne 'EX01_PG_DUMP_PLAIN' -or $metadata.encoding -ne 'UTF8' -or $metadata.clean -or $metadata.createDatabase -or $metadata.sha256 -ne (Get-ToeicHash $InputFile) -or $metadata.sizeBytes -ne (Get-Item -LiteralPath $InputFile).Length) { throw 'Backup metadata/checksum is invalid.' }
$sql = (New-Object Text.UTF8Encoding($false,$true)).GetString([IO.File]::ReadAllBytes($InputFile))
if ($sql -notmatch 'PostgreSQL database dump' -or $sql -match '(?im)^\s*(?:\\(?:connect|c|!)\b|(?:CREATE|ALTER|DROP)\s+DATABASE\b|ALTER\s+SYSTEM\b)') { throw 'Dump may switch databases or run outside the new target; refused.' }
$psql = Resolve-ToeicPgTool $PostgresBin 'psql'
$maintenance = Get-ToeicConnectionArgs $HostName $Port $UserName 'postgres'
$version = [int](Invoke-ToeicPg $psql ($maintenance + @('-X','-At','-v','ON_ERROR_STOP=1','-c','SHOW server_version_num')))
if ([Math]::Floor($version/10000) -lt [Math]::Floor([int]$metadata.serverVersionNum/10000)) { throw 'Target PostgreSQL major version is older than the backup source.' }
$exists = Invoke-ToeicPg $psql ($maintenance + @('-X','-At','-v','ON_ERROR_STOP=1','-c',("SELECT count(*) FROM pg_database WHERE datname = '"+$Database+"'")))
if ($exists -ne '0') { throw 'Target database already exists; no deletion or overwrite is allowed.' }
[void](Invoke-ToeicPg $psql ($maintenance + @('-X','-v','ON_ERROR_STOP=1','-c',('CREATE DATABASE "'+$Database+'" TEMPLATE template0 ENCODING ''UTF8'''))))
$connection = Get-ToeicConnectionArgs $HostName $Port $UserName $Database
$objects = Invoke-ToeicPg $psql ($connection + @('-X','-At','-v','ON_ERROR_STOP=1','-c',"SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname NOT LIKE 'pg_toast%';"))
if ($objects -ne '0') { throw 'New target is unexpectedly nonempty; restore refused and database left intact.' }
[void](Invoke-ToeicPg $psql ($connection + @('-X','--single-transaction','-v','ON_ERROR_STOP=1','--file',$InputFile)))
$receipt = [ordered]@{ formatVersion=1; sourceSha256=$metadata.sha256; targetDatabase=$Database; targetCreatedByThisRun=$true; emptyBeforeRestore=$true; restoreExitCode=0; verifiedAt=[DateTime]::UtcNow.ToString('o'); acceptance='SQL_RESTORE_ONLY_NOT_ETS_ACCEPTANCE' }
$receiptPath = $InputFile + '.' + $Database + '.restore.json'
if (Test-Path -LiteralPath $receiptPath) { throw 'Restore receipt exists; refuse overwrite.' }
[IO.File]::WriteAllText($receiptPath, ($receipt | ConvertTo-Json), (New-Object Text.UTF8Encoding($false)))
Write-Output 'SQL_RESTORE_PASS new TEST database only; verify application/catalog separately.'
