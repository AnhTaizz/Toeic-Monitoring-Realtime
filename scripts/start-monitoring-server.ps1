param([string]$JavaHome=$env:JAVA_HOME,[switch]$DeltaExperimental,[string]$BindAddress='0.0.0.0',[int]$Port=8080,[switch]$SignalExperiment,[switch]$ValidateOnly)
$ErrorActionPreference='Stop';$previous=@{}
if($Port -lt 1 -or $Port -gt 65535){throw 'Port không hợp lệ.'}
if($ValidateOnly){Write-Output "PASS validation: bind=$BindAddress port=$Port; typed signals=$($SignalExperiment.IsPresent); delta=$($DeltaExperimental.IsPresent); no .env read or server launched.";return}
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    foreach($line in Get-Content -LiteralPath '.env' -Encoding UTF8) {
        if($line -match '^\s*(DB_HOST|DB_PORT|DB_NAME|DB_USER|DB_PASSWORD|TOEIC_SEED_PASSWORD|TOEIC_AUDIO_ROOT|TOEIC_IMAGE_ROOT)\s*=(.*)$') {
            $key=$Matches[1];$previous[$key]=[Environment]::GetEnvironmentVariable($key,'Process');[Environment]::SetEnvironmentVariable($key,$Matches[2].Trim().Trim('"').Trim("'"),'Process')
        }
    }
    if($Port -lt 1 -or $Port -gt 65535){throw 'Port không hợp lệ.'}
    $java=if($JavaHome){Join-Path $JavaHome 'bin/java.exe'}else{'java'}
    $opts=@('-Duser.timezone=UTC','-jar','server/target/server-0.1.0-SNAPSHOT.jar',"--server.address=$BindAddress","--server.port=$Port","--toeic.state.delta-enabled=$DeltaExperimental",
        "--toeic.monitoring.signal-experiment-enabled=$($SignalExperiment.IsPresent.ToString().ToLowerInvariant())")
    Write-Output "Server bind=$BindAddress port=$Port; delta experimental=$DeltaExperimental; gate remains unconfirmed."
    Write-Output "Typed signals=$($SignalExperiment.IsPresent); bật thử nghiệm chỉ với dữ liệu TEST được cấp quyền."
    & $java @opts
    exit $LASTEXITCODE
} finally {foreach($key in $previous.Keys){[Environment]::SetEnvironmentVariable($key,$previous[$key],'Process')};Pop-Location}
