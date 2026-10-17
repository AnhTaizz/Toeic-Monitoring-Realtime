param([Parameter(Mandatory)][string]$OutName)
# Re-runs only the MSI install + FullSuite stage of final-pipeline.ps1 for an MSI that stage already built,
# with the same environment. Used when the install stage was refused (existing TEST product).
$ErrorActionPreference='Stop'
$base='E:\Project\Toeic-Monitoring-Realtime\target\stabilize-20261017'
$root='E:\Project\Toeic-Monitoring-Realtime\target\integration-final-20261017\worktree';$out=Join-Path $base $OutName
Set-Location $root
$env:JAVA_HOME='E:\Java\Jav\jdk-21';$env:PATHEXT='.COM;.EXE;.BAT;.CMD'
$env:PATH="$env:JAVA_HOME\bin;E:\Git\cmd;E:\Java\Jav\apache-maven-3.9.11\bin;"+$env:PATH
$env:TEMP=Join-Path $out 'tmp';$env:TMP=$env:TEMP
$env:JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$env:TEMP -Duser.timezone=UTC"
$env:TOEIC_INSTALLED_CLIENT=$null;$env:TOEIC_PG_INTEGRATION='true';$env:TOEIC_PROCTOR_GUI='true'
$source=(& git rev-parse HEAD).Trim(); if((& git status --porcelain).Count -ne 0){throw 'dirty source'}
$msi=(Get-Content "$out\msi-path.txt" -Raw).Trim()
$meta=Get-Content (Join-Path (Split-Path $msi) 'build-info.json') -Raw | ConvertFrom-Json
if($meta.sourceCommit -ne $source){throw "MSI source $($meta.sourceCommit) != HEAD $source"}
$pg='E:\PostgreSQL\18\bin';$port=(Select-String -Path "$out\pgdata\postgresql.conf" -Pattern '^port=(\d+)').Matches[-1].Groups[1].Value
$p=Start-Process "$pg\pg_ctl.exe" -ArgumentList @('-D',"$out\pgdata",'-l',"$out\postgres-installed.log",'-w','start') -NoNewWindow -PassThru; $p.WaitForExit(60000) | Out-Null
try {
  $env:DB_HOST='127.0.0.1';$env:DB_PORT=$port;$env:DB_NAME='postgres';$env:DB_USER='integration_test';$env:DB_PASSWORD=[Guid]::NewGuid().ToString('N');$env:TOEIC_SEED_PASSWORD=[Guid]::NewGuid().ToString('N')
  $image=Split-Path ((Get-ChildItem 'jpackage-out' -Filter 'build-info.json' -Recurse | Where-Object {$_.FullName -match '\\app\\build-info.json$'}|Sort-Object LastWriteTime|Select-Object -Last 1).FullName)
  $env:TOEIC_PACKAGED_CLIENT=Split-Path $image
  $start=[DateTime]::UtcNow.ToString('o')
  try {
    & ./scripts/smoke-windows-installer.ps1 -Installer $msi -JavaHome $env:JAVA_HOME -FullSuite *> "$out\installed.out.log"
    $status='PASS'
  } catch { $status='FAIL_OR_BLOCKED'; $err=$_.Exception.Message }
  $dest=Join-Path $out 'installed-reports';New-Item -ItemType Directory -Force $dest|Out-Null
  foreach($module in @('protocol','client','server','monitoring-spike')){ $r=Join-Path $root "$module\target\surefire-reports"; if(Test-Path $r){Copy-Item $r (Join-Path $dest $module) -Recurse -Force} }
  @{source=$source;status=$status;error=$err;startUtc=$start;endUtc=[DateTime]::UtcNow.ToString('o');note='install stage re-run; first attempt refused because a stale TEST install from run final-4fbc538 existed (removed by product code before this run)'}|ConvertTo-Json|Set-Content -Encoding UTF8 "$out\installed-run.json"
  "installed status=$status $err"
} finally {
  & "$pg\pg_ctl.exe" -D "$out\pgdata" -w -m fast stop | Out-Null
  foreach($key in @('DB_PASSWORD','TOEIC_SEED_PASSWORD')){[Environment]::SetEnvironmentVariable($key,$null,'Process')}
  $left=(Get-ChildItem 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall','HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall' | Get-ItemProperty | Where-Object { $_.DisplayName -eq 'ToeicMonitor' }).Count
  "ToeicMonitor products left installed=$left"
}
