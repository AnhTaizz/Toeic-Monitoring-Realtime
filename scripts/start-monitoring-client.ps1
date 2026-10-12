param(
    [Parameter(Mandatory=$true)][string]$ServerUrl,
    [ValidateSet('Full','DeltaExperimental')][string]$Mode='Full',
    [string]$JavaHome=$env:JAVA_HOME
)
$ErrorActionPreference='Stop'
$uri=[Uri]$ServerUrl
if(-not $uri.IsAbsoluteUri -or $uri.Scheme -notin @('http','https') -or $uri.UserInfo -or $uri.Query -or $uri.Fragment -or $uri.AbsolutePath -notin @('','/')){throw 'ServerUrl phải là HTTP/HTTPS origin, không credential/query/fragment.'}
$java=if($JavaHome){Join-Path $JavaHome 'bin/java.exe'}else{'java'}
$jar=Join-Path (Split-Path -Parent $PSScriptRoot) 'client/target/client-0.1.0-SNAPSHOT-all.jar'
if(-not(Test-Path -LiteralPath $jar)){throw 'Chạy mvn package trước.'}
$opts=@("-Dtoeic.server.url=$ServerUrl","-Dtoeic.realtime.delta-experimental=$($Mode -eq 'DeltaExperimental')",'-jar',$jar)
Write-Output "Requested mode=$Mode; delta needs current-server capability; FULL remains fallback/default."
& $java @opts
exit $LASTEXITCODE
