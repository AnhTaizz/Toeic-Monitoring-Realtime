# Local ProcessHandle smoke; no DB/network credential or real monitoring assignment.
param([string]$JavaHome = $env:JAVA_HOME, [string]$DemoExecutable = '')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $javaCommand = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
    $jar = 'client/target/client-0.1.0-SNAPSHOT-all.jar'
    if (-not (Test-Path $jar)) { throw 'Run mvn package first.' }
    $smokeClasses = 'client/target/c2-smoke-classes/vn/edu/toeic/client/monitoring'
    New-Item -ItemType Directory -Force $smokeClasses | Out-Null
    Copy-Item 'client/target/test-classes/vn/edu/toeic/client/monitoring/ProcessCollectorSmoke*.class' $smokeClasses -Force
    if (-not $DemoExecutable -and ${env:ProgramFiles(x86)}) {
        $edge = Join-Path ${env:ProgramFiles(x86)} 'Microsoft/Edge/Application/msedge.exe'
        if (Test-Path $edge) { $DemoExecutable = $edge }
    }
    $stdout = Join-Path $projectRoot 'client/target/c2-smoke.stdout.txt'
    $stderr = Join-Path $projectRoot 'client/target/c2-smoke.stderr.txt'
    $javaArguments = @('-cp', '"client/target/client-0.1.0-SNAPSHOT-all.jar;client/target/c2-smoke-classes"',
        'vn.edu.toeic.client.monitoring.ProcessCollectorSmoke')
    if ($DemoExecutable) { $javaArguments += ('"{0}"' -f $DemoExecutable) }
    $process = Start-Process -FilePath $javaCommand -ArgumentList $javaArguments -Wait -PassThru -NoNewWindow `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    Get-Content $stdout
    Get-Content $stderr
    if ($process.ExitCode -ne 0) { throw 'C2 ProcessHandle smoke failed; no private metadata diagnostics printed.' }
} finally { Pop-Location }
