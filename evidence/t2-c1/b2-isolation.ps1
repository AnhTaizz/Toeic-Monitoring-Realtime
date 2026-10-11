param([string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$javaCommand=if($JavaHome){Join-Path $JavaHome 'bin/java.exe'}else{'java'}
$previous=@{}
$owned='toeic_t2c1_b2_'+[Guid]::NewGuid().ToString('N')
if($owned -notmatch '^toeic_t2c1_b2_[a-f0-9]{32}$'){throw 'Invalid owned database'}
$created=$false
try {
    foreach($line in Get-Content -Encoding UTF8 '.env') {
        if($line -match '^\s*(DB_HOST|DB_PORT|DB_NAME|DB_USER|DB_PASSWORD|TOEIC_SEED_PASSWORD)\s*=(.*)$') {
            $key=$Matches[1];$previous[$key]=[Environment]::GetEnvironmentVariable($key,'Process')
            [Environment]::SetEnvironmentVariable($key,$Matches[2].Trim().Trim('"').Trim("'"),'Process')
        }
    }
    $originalDatabase=$env:DB_NAME
    & docker exec toeic-db psql -U $env:DB_USER -d $originalDatabase -v ON_ERROR_STOP=1 -c "CREATE DATABASE $owned;"
    if($LASTEXITCODE -ne 0){throw 'Owned TEST database creation failed'}
    $created=$true;$env:DB_NAME=$owned
    # Warm only the owned database so the old harness's readiness probe cannot race dev account seeding.
    $listener=New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback,0)
    $listener.Start();$warmPort=$listener.LocalEndpoint.Port;$listener.Stop()
    $warm=Start-Process -FilePath $javaCommand -ArgumentList @('-Duser.timezone=UTC','-jar','server/target/server-0.1.0-SNAPSHOT.jar',"--server.port=$warmPort",'--server.address=127.0.0.1') -WindowStyle Hidden -PassThru -RedirectStandardOutput 'server/target/t2c1-b2-warm.stdout.txt' -RedirectStandardError 'server/target/t2c1-b2-warm.stderr.txt'
    try {
        $ready=$false;$deadline=[DateTime]::UtcNow.AddSeconds(30)
        while([DateTime]::UtcNow -lt $deadline) {
            $table=& docker exec toeic-db psql -U $env:DB_USER -d $owned -At -c "SELECT count(*) FROM pg_class WHERE relname='user_accounts' AND relnamespace='public'::regnamespace;"
            if($table -eq '1') {
                $users=& docker exec toeic-db psql -U $env:DB_USER -d $owned -At -c 'SELECT count(*) FROM user_accounts;'
                if([int]$users -ge 2){$ready=$true;break}
            }
            Start-Sleep -Milliseconds 250
        }
        if(-not $ready){throw 'Owned database account seed readiness failed'}
    } finally {if(-not $warm.HasExited){Stop-Process -Id $warm.Id;[void]$warm.WaitForExit(10000)}}
    & $javaCommand '-Duser.timezone=UTC' '-cp' 'client/target/client-0.1.0-SNAPSHOT-all.jar;client/target/smoke-classes' 'vn.edu.toeic.client.realtime.RealtimePostgresSmoke'
    if($LASTEXITCODE -ne 0){throw 'B2 isolated regression failed'}
} finally {
    if($created) {
        & docker exec toeic-db psql -U $env:DB_USER -d $originalDatabase -v ON_ERROR_STOP=1 -c "DROP DATABASE $owned;"
        if($LASTEXITCODE -ne 0){Write-Error 'Owned TEST database cleanup failed'}
    }
    foreach($key in $previous.Keys){[Environment]::SetEnvironmentVariable($key,$previous[$key],'Process')}
}
