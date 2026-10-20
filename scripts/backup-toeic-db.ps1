[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$PostgresBin,
    [Parameter(Mandatory=$true)][string]$Database,
    [Parameter(Mandatory=$true)][string]$OutputFile,
    [string]$HostName = '127.0.0.1',
    [int]$Port = 5432,
    [Parameter(Mandatory=$true)][string]$UserName
)
. (Join-Path $PSScriptRoot 'toeic-db-common.ps1')
Assert-ToeicDatabaseName $Database
Assert-ToeicExternalOutput $OutputFile
$OutputFile = [IO.Path]::GetFullPath($OutputFile)
if (!$OutputFile.EndsWith('.sql', [StringComparison]::OrdinalIgnoreCase)) { throw 'Output must be a new .sql file.' }
$metadataFile = $OutputFile + '.metadata.json'
if ((Test-Path -LiteralPath $OutputFile) -or (Test-Path -LiteralPath $metadataFile)) { throw 'Refuse to overwrite an existing backup/metadata.' }
$dump = Resolve-ToeicPgTool $PostgresBin 'pg_dump'
$psql = Resolve-ToeicPgTool $PostgresBin 'psql'
$connection = Get-ToeicConnectionArgs $HostName $Port $UserName $Database
$version = Invoke-ToeicPg $psql ($connection + @('-X','-At','-v','ON_ERROR_STOP=1','-c','SHOW server_version_num'))
New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($OutputFile)) | Out-Null
$temporary = $OutputFile + '.partial-' + [Guid]::NewGuid().ToString('N')
try {
    [void](Invoke-ToeicPg $dump ($connection + @('--format=plain','--encoding=UTF8','--no-owner','--no-privileges','--file', $temporary)))
    if ((Get-Item -LiteralPath $temporary).Length -eq 0) { throw 'pg_dump produced an empty file.' }
    [IO.File]::Move($temporary, $OutputFile)
    $metadata = [ordered]@{ formatVersion=1; producer='EX01_PG_DUMP_PLAIN'; createdAt=[DateTime]::UtcNow.ToString('o'); sourceDatabase=$Database; serverVersionNum=[int]$version; encoding='UTF8'; clean=$false; createDatabase=$false; sizeBytes=(Get-Item -LiteralPath $OutputFile).Length; sha256=(Get-ToeicHash $OutputFile) }
    [IO.File]::WriteAllText($metadataFile, ($metadata | ConvertTo-Json -Depth 5), (New-Object Text.UTF8Encoding($false)))
    Write-Output ('SQL_BACKUP_PASS sha256=' + $metadata.sha256)
} finally {
    if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary }
}
