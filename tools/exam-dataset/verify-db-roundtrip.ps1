[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$PostgresBin,
    [Parameter(Mandatory=$true)][string]$JavaExe,
    [Parameter(Mandatory=$true)][string]$ServerJar,
    [Parameter(Mandatory=$true)][string]$ExternalBackupRoot
)
# Only self-authored MOCK EX02, on a newly initialized isolated PostgreSQL cluster.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Set-StrictMode -Version Latest
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $repo 'scripts/toeic-db-common.ps1')
$runId = [Guid]::NewGuid().ToString('N').Substring(0,12)
$work = Join-Path $PSScriptRoot ('target/db-roundtrip-' + $runId)
$external = Join-Path $ExternalBackupRoot ('mock-db-' + $runId)
Assert-ToeicExternalOutput (Join-Path $external 'mock.sql')
if (Test-Path -LiteralPath $external) { throw 'External destination exists.' }
New-Item -ItemType Directory -Path $work,$external | Out-Null
$snapshot = Join-Path $work 'snapshot'
$fixture = Join-Path $snapshot 'docs/plan-v2/fixtures/ex02'
New-Item -ItemType Directory -Path (Split-Path -Parent $fixture) -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $repo 'docs/plan-v2/fixtures/ex02') -Destination $fixture -Recurse
Copy-Item -LiteralPath (Join-Path $repo 'docs/QUYET_DINH.md') -Destination (Join-Path $snapshot 'docs/QUYET_DINH.md')
& (Join-Path $fixture 'verify-design.ps1') -Generate | Out-Null
$jarCopy = Join-Path $work 'server.jar'
Copy-Item -LiteralPath $ServerJar -Destination $jarCopy
$sourceDb = 'toeic_source_test_' + $runId
$targetDb = 'toeic_restore_test_' + $runId
$dbUser = 'ex01_backup_test'
$password = 'Mock!' + [Guid]::NewGuid().ToString('N')
$seedPassword = 'Mock!' + [Guid]::NewGuid().ToString('N')
$envBefore = [Environment]::GetEnvironmentVariable('PGPASSWORD','Process')
$env:PGPASSWORD = $password
$cluster = Join-Path $work 'pgdata'
$pwFile = Join-Path $work 'initdb-password.tmp'
[IO.File]::WriteAllText($pwFile,$password,(New-Object Text.UTF8Encoding($false)))
$pgctl = Resolve-ToeicPgTool $PostgresBin 'pg_ctl'
$psql = Resolve-ToeicPgTool $PostgresBin 'psql'
$servers = New-Object 'System.Collections.Generic.List[object]'
$clusterStarted = $false
function Free-Port {
    $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback,0)
    $listener.Start();$portNumber = $listener.LocalEndpoint.Port;$listener.Stop();return $portNumber
}
$dbPort = Free-Port
function Invoke-OwnedPgCtl([string[]]$Arguments) {
    # pg_ctl start leaves descendant handles open on Windows. Do not capture its pipes.
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $pgctl
    $info.Arguments = (@($Arguments | ForEach-Object {'"'+$_+'"'}) -join ' ')
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $info
    try { [void]$process.Start();$process.WaitForExit();if($process.ExitCode -ne 0){throw 'Owned pg_ctl failed.'} } finally {$process.Dispose()}
}
function Query([string]$DatabaseName,[string]$Sql) {
    return Invoke-ToeicPg $psql ((Get-ToeicConnectionArgs '127.0.0.1' $dbPort $dbUser $DatabaseName) + @('-X','-At','-v','ON_ERROR_STOP=1','-c',$Sql))
}
function Start-MockServer([string]$DatabaseName,[string]$MediaRoot) {
    $serverPort = Free-Port
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $JavaExe
    $info.Arguments = '-jar "' + $jarCopy + '"'
    $info.WorkingDirectory = $work
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $settings = @{DB_HOST='127.0.0.1';DB_PORT=[string]$dbPort;DB_NAME=$DatabaseName;DB_USER=$dbUser;DB_PASSWORD=$password;TOEIC_SEED_PASSWORD=$seedPassword;SERVER_ADDRESS='127.0.0.1';SERVER_PORT=[string]$serverPort;TOEIC_AUDIO_ROOT=(Join-Path $MediaRoot 'audio');TOEIC_IMAGE_ROOT=(Join-Path $MediaRoot 'images')}
    foreach ($name in $settings.Keys) { $info.EnvironmentVariables[$name] = $settings[$name] }
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $info
    [void]$process.Start()
    $running = [pscustomobject]@{Process=$process;Output=$process.StandardOutput.ReadToEndAsync();Error=$process.StandardError.ReadToEndAsync();Database=$DatabaseName;Base=('http://127.0.0.1:'+$serverPort);Token=$null}
    $servers.Add($running)
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($process.HasExited) { throw 'Isolated MOCK server exited; inspect private local process log.' }
        try {
            $body = @{username='proctor1';password=$seedPassword;requestId=[Guid]::NewGuid().ToString()} | ConvertTo-Json
            $login = Invoke-RestMethod -Method POST -Uri ($running.Base+'/api/v1/auth/login') -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 3
            $running.Token = $login.token
            return $running
        } catch { Start-Sleep -Milliseconds 500 }
    }
    throw 'Isolated MOCK server startup timed out.'
}
function Catalog([object]$Server) {
    $catalog = Invoke-RestMethod -Method GET -Uri ($Server.Base+'/api/v2/exams') -Headers @{Authorization=('Bearer '+$Server.Token)}
    $item = @($catalog.items | Where-Object {$_.examId -eq 'mock-ex02-200'})
    if ($item.Count -ne 1 -or !$item[0].selectable -or @($item[0].blockedReasons).Count -ne 0) { throw 'MOCK catalog must contain one selectable exam with no blocked reasons.' }
    return [ordered]@{examId=$item[0].examId;selectable=$item[0].selectable;blockedReasons=@($item[0].blockedReasons)}
}
function Fingerprints([string]$DatabaseName) {
    $result = [ordered]@{}
    foreach ($table in @('exams','exam_questions','exam_options','exam_question_groups','exam_sources','exam_media_assets','exam_content_blocks')) {
        $sql = 'SELECT count(*)::text || '':'' || md5(COALESCE(string_agg(row_to_json(t)::text,E''\n'' ORDER BY row_to_json(t)::text),'''')) FROM (SELECT * FROM ' + $table + ' WHERE exam_id=''mock-ex02-200'') t'
        $result[$table] = Query $DatabaseName $sql
    }
    $result['scoringKeys'] = Query $DatabaseName "SELECT count(*)::text || ':' || md5(string_agg(question_id || ':' || correct_option,',' ORDER BY order_index)) FROM exam_questions WHERE exam_id='mock-ex02-200'"
    return $result
}
function Expect-Refused([scriptblock]$Action,[string]$ExpectedMessage) {
    try { & $Action | Out-Null } catch { if ($_.Exception.Message -like ('*'+$ExpectedMessage+'*')) {return};throw }
    throw 'Expected restore/backup refusal did not occur.'
}
try {
    [void](Invoke-ToeicPg (Resolve-ToeicPgTool $PostgresBin 'initdb') @('-D',$cluster,'--username',$dbUser,'--pwfile',$pwFile,'--auth=scram-sha-256','--encoding=UTF8','--no-locale'))
    Remove-Item -LiteralPath $pwFile
    Invoke-OwnedPgCtl @('-D',$cluster,'-l',(Join-Path $work 'postgres-private.log'),'-o',('-h 127.0.0.1 -p '+$dbPort),'-w','start')
    $clusterStarted = $true
    # Create only a new source TEST database on this owned cluster; import all content through HTTP V2.
    [void](Query 'postgres' ('CREATE DATABASE "'+$sourceDb+'" TEMPLATE template0 ENCODING ''UTF8'''))
    $source = Start-MockServer $sourceDb (Join-Path $fixture 'assets')
    $payload = [IO.File]::ReadAllBytes((Join-Path $fixture 'import.mock.json'))
    $import = Invoke-WebRequest -UseBasicParsing -Method POST -Uri ($source.Base+'/api/v2/exams/import') -Headers @{Authorization=('Bearer '+$source.Token)} -ContentType 'application/json; charset=utf-8' -Body $payload
    if ($import.StatusCode -ne 201) { throw 'MOCK must pass the real V2 importer with HTTP 201.' }
    $sourceCatalog = Catalog $source
    $before = Fingerprints $sourceDb
    if ($before.exam_questions -notlike '200:*' -or $before.exam_options -notlike '775:*' -or $before.exam_question_groups -notlike '76:*' -or $before.exam_media_assets -notlike '57:*') { throw 'MOCK persisted counts differ from fixture.' }
    $mediaBefore = @{}
    $restoredMedia = Join-Path $work 'restored-media'
    foreach ($media in (([IO.File]::ReadAllText((Join-Path $fixture 'import.mock.json')) | ConvertFrom-Json).media)) {
        $kind = if ($media.mediaType -eq 'AUDIO') {'audio'} else {'images'}
        $relative = $kind+'/mock-ex02-200/'+$media.localFile
        $original = Join-Path (Join-Path $fixture 'assets') $relative
        $backupMediaFile = Join-Path (Join-Path $external 'media') $relative
        $restoredMediaFile = Join-Path $restoredMedia $relative
        New-Item -ItemType Directory -Path (Split-Path -Parent $backupMediaFile),(Split-Path -Parent $restoredMediaFile) -Force | Out-Null
        Copy-Item -LiteralPath $original -Destination $backupMediaFile
        Copy-Item -LiteralPath $backupMediaFile -Destination $restoredMediaFile
        foreach ($file in @($original,$backupMediaFile,$restoredMediaFile)) {if ((Get-ToeicHash $file) -ne $media.sha256 -or (Get-Item $file).Length -ne $media.sizeBytes) {throw 'MOCK media backup/restore checksum/size mismatch.'}}
        $mediaBefore[$relative] = $media.sha256
    }
    $sqlFile = Join-Path $external 'mock.sql'
    & (Join-Path $repo 'scripts/backup-toeic-db.ps1') -PostgresBin $PostgresBin -Database $sourceDb -OutputFile $sqlFile -HostName '127.0.0.1' -Port $dbPort -UserName $dbUser
    $restoreParams = @{PostgresBin=$PostgresBin;Database=$targetDb;ConfirmRestoreDatabase=$targetDb;InputFile=$sqlFile;HostName='127.0.0.1';Port=$dbPort;UserName=$dbUser}
    & (Join-Path $repo 'scripts/restore-toeic-db.ps1') @restoreParams
    $after = Fingerprints $targetDb
    foreach ($name in $before.Keys) { if ($before[$name] -ne $after[$name]) {throw ('Restored table/scoring fingerprint mismatch: '+$name)} }
    $restored = Start-MockServer $targetDb $restoredMedia
    $restoredCatalog = Catalog $restored
    Expect-Refused { & (Join-Path $repo 'scripts/restore-toeic-db.ps1') @restoreParams } 'already exists'
    $wrongConfirm = $restoreParams.Clone();$wrongConfirm.ConfirmRestoreDatabase = 'wrong'
    Expect-Refused { & (Join-Path $repo 'scripts/restore-toeic-db.ps1') @wrongConfirm } 'exact confirmation'
    $notTest = $restoreParams.Clone();$notTest.Database='production';$notTest.ConfirmRestoreDatabase='production'
    Expect-Refused { & (Join-Path $repo 'scripts/restore-toeic-db.ps1') @notTest } 'new toeic_'
    $bad = Join-Path $external 'corrupt-negative.sql'
    [IO.File]::WriteAllText($bad,'MOCK checksum mismatch',(New-Object Text.UTF8Encoding($false)))
    Copy-Item -LiteralPath ($sqlFile+'.metadata.json') -Destination ($bad+'.metadata.json')
    $corrupt = $restoreParams.Clone();$corrupt.InputFile=$bad;$corrupt.Database='toeic_corrupt_test_'+$runId;$corrupt.ConfirmRestoreDatabase=$corrupt.Database
    Expect-Refused { & (Join-Path $repo 'scripts/restore-toeic-db.ps1') @corrupt } 'metadata/checksum'
    if ((Query 'postgres' ("SELECT count(*) FROM pg_database WHERE datname='"+$corrupt.Database+"'")) -ne '0') {throw 'Corrupt backup created a database.'}
    Expect-Refused { & (Join-Path $repo 'scripts/backup-toeic-db.ps1') -PostgresBin $PostgresBin -Database $sourceDb -OutputFile $sqlFile -Port $dbPort -UserName $dbUser } 'overwrite'
    $afterRefusals = Fingerprints $targetDb
    foreach ($name in $after.Keys) { if ($after[$name] -ne $afterRefusals[$name]) {throw 'Negative restore modified the target.'} }
    $report = [ordered]@{formatVersion=1;scope='MOCK_ONLY_ETS_ACCEPTANCE_BLOCKED';runId=$runId;createdAt=[DateTime]::UtcNow.ToString('o');serverJarSha256=(Get-ToeicHash $jarCopy);postgresVersionNum=(Query 'postgres' 'SHOW server_version_num');sourceDatabase=$sourceDb;restoredDatabase=$targetDb;isolatedCluster=$true;realV2ImportHttpStatus=201;sourceCatalog=$sourceCatalog;restoredCatalog=$restoredCatalog;sourceFingerprints=$before;restoredFingerprints=$after;mediaCount=$mediaBefore.Count;mediaBackupOutsideGit=$true;restoredServerUsesCopiedMedia=$true;mediaSha256=$mediaBefore;negativeChecks=@('existing target refused unchanged','wrong confirmation refused','non-test name refused','corrupt SQL refused before database creation','backup overwrite refused');sqlSha256=(Get-ToeicHash $sqlFile);sqlBackupBytes=(Get-Item $sqlFile).Length;sqlBackup=$sqlFile;result='MOCK_BACKUP_RESTORE_PASS'}
    [IO.File]::WriteAllText((Join-Path $external 'mock-roundtrip-report.json'),($report | ConvertTo-Json -Depth 12),(New-Object Text.UTF8Encoding($false)))
    Copy-Item -LiteralPath (Join-Path $external 'mock-roundtrip-report.json') -Destination (Join-Path $work 'report.json')
    Write-Output ('MOCK_BACKUP_RESTORE_PASS report='+$external)
} finally {
    foreach ($server in $servers) {
        if (!$server.Process.HasExited) {$server.Process.Kill();$server.Process.WaitForExit()}
        [IO.File]::WriteAllText((Join-Path $work ($server.Database+'-private.log')),($server.Output.Result+$server.Error.Result),(New-Object Text.UTF8Encoding($false)))
        $server.Process.Dispose()
    }
    if ($clusterStarted) { Invoke-OwnedPgCtl @('-D',$cluster,'-m','fast','-w','stop') }
    if (Test-Path -LiteralPath $pwFile) {Remove-Item -LiteralPath $pwFile}
    [Environment]::SetEnvironmentVariable('PGPASSWORD',$envBefore,'Process')
}
