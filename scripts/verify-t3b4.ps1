param([string]$JavaHome=$env:JAVA_HOME,[switch]$Gui,[switch]$Focused)
$ErrorActionPreference='Stop';$previous=@{}
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    foreach($key in @('JAVA_HOME','PATH','JAVA_TOOL_OPTIONS','TOEIC_PG_INTEGRATION','TOEIC_PROCTOR_GUI','DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','TOEIC_SEED_PASSWORD')){$previous[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
    foreach($line in Get-Content -LiteralPath '.env' -Encoding UTF8){if($line -match '^\s*(DB_HOST|DB_PORT|DB_NAME|DB_USER|DB_PASSWORD|TOEIC_SEED_PASSWORD)\s*=(.*)$'){[Environment]::SetEnvironmentVariable($Matches[1],$Matches[2].Trim().Trim('"').Trim("'"),'Process')}}
    $defaults=@{DB_HOST='127.0.0.1';DB_PORT='5432';DB_NAME='toeic';DB_USER='toeic';TOEIC_SEED_PASSWORD='ChangeMe123!'}
    foreach($key in $defaults.Keys){if(-not [Environment]::GetEnvironmentVariable($key,'Process')){[Environment]::SetEnvironmentVariable($key,$defaults[$key],'Process')}}
    if($JavaHome){$env:JAVA_HOME=$JavaHome;$env:PATH=(Join-Path $JavaHome 'bin')+[IO.Path]::PathSeparator+$env:PATH}
    # PostgreSQL image may not accept the legacy JVM timezone alias Asia/Saigon.
    $env:JAVA_TOOL_OPTIONS=($env:JAVA_TOOL_OPTIONS+' -Duser.timezone=UTC').Trim()
    $env:TOEIC_PG_INTEGRATION='true';$env:TOEIC_PROCTOR_GUI=if($Gui){'true'}else{'false'}
    $output=Join-Path (Get-Location) ('target/t3-b4/'+[Guid]::NewGuid().ToString('N'));New-Item -ItemType Directory -Force $output | Out-Null
    # Clean module outputs to avoid executing stale classes left by another branch.
    # The root target/ keeps this task's local transcripts.
    $arguments=@('-B','-o',"-Dmaven.repo.local=$env:USERPROFILE/.m2/repository",'-pl','protocol,windows-observer,client,monitoring-spike,server','clean','package')
    if($Focused){$arguments+=@('-Dtest=ProctorSessionControllerTest,ProctorSessionApiClientTest,ListeningRuntimePostgresTest','-Dsurefire.failIfNoSpecifiedTests=false')}
    $ErrorActionPreference='Continue'
    & mvn @arguments *> (Join-Path $output 'package.txt');$result=$LASTEXITCODE
    $ErrorActionPreference='Stop'
    Get-Content -LiteralPath (Join-Path $output 'package.txt') -Tail 22
    Write-Output "Local output: $output"
    if($result -ne 0){throw 'B4 verification failed; inspect local package.txt.'}
} finally {foreach($key in $previous.Keys){[Environment]::SetEnvironmentVariable($key,$previous[$key],'Process')};Pop-Location}
