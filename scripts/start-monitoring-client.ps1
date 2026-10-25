param(
    [Parameter(Mandatory=$true)][string]$ServerUrl,
    [ValidateSet('Full','DeltaExperimental')][string]$Mode='Full',
    [string]$JavaHome=$env:JAVA_HOME,
    [switch]$SignalExperiment,
    [switch]$ReadUsbStorage,
    [switch]$ValidateOnly
)
$ErrorActionPreference='Stop'
$uri=[Uri]$ServerUrl
if(-not $uri.IsAbsoluteUri -or $uri.Scheme -notin @('http','https') -or $uri.UserInfo -or $uri.Query -or $uri.Fragment -or $uri.AbsolutePath -notin @('','/')){throw 'ServerUrl phải là HTTP/HTTPS origin, không credential/query/fragment.'}
$java=if($JavaHome){Join-Path $JavaHome 'bin/java.exe'}else{'java'}
$jar=Join-Path (Split-Path -Parent $PSScriptRoot) 'client/target/client-0.1.0-SNAPSHOT-all.jar'
if(-not(Test-Path -LiteralPath $jar)){throw 'Chạy mvn package trước.'}
$signals=$SignalExperiment.IsPresent -or $ReadUsbStorage.IsPresent
if($ReadUsbStorage){
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive=[IO.Compression.ZipFile]::OpenRead($jar)
    try{
        foreach($entry in @('vn/edu/toeic/client/monitoring/usb/CimUsbScanSource.class','vn/edu/toeic/monitoring/usb/CimSnapshotSource.class','vn/edu/toeic/monitoring/usb/cim-snapshot.ps1')){
            if($null -eq $archive.GetEntry($entry)){throw 'JAR client chưa có USB observer. Build/package nhánh tích hợp USB trước.'}
        }
    }finally{$archive.Dispose()}
}
$opts=@("-Dtoeic.server.url=$ServerUrl","-Dtoeic.realtime.delta-experimental=$($Mode -eq 'DeltaExperimental')",
    "-Dtoeic.monitoring.signal-experiment-enabled=$($signals.ToString().ToLowerInvariant())",
    "-Dtoeic.monitoring.usb-enabled=$($ReadUsbStorage.IsPresent.ToString().ToLowerInvariant())",'-jar',$jar)
Write-Output "Requested mode=$Mode; delta needs current-server capability; FULL remains fallback/default."
Write-Output "Typed signals=$signals; USB storage=$($ReadUsbStorage.IsPresent); chỉ role thí sinh trong màn thi được cấp quyền mới quan sát USB."
if($ValidateOnly){Write-Output 'PASS validation; no client launched.';return}
& $java @opts
exit $LASTEXITCODE
