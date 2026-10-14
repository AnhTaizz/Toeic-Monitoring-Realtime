# Re-run only the packaged GUI/media check after `mvn package` has succeeded.
param([string]$JavaHome = $env:JAVA_HOME, [string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$previousOutput = $env:TOEIC_AUDIO_SMOKE_OUTPUT
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    if (-not $OutputDirectory) {
        $OutputDirectory = Join-Path (Get-Location) ('client/target/t3b1-package-smoke/' + [Guid]::NewGuid().ToString('N'))
    }
    New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
    $image = & "$PSScriptRoot/package-t3b1.ps1" -JavaHome $JavaHome -WithAudioSmoke
    # Use environment, not argv, to preserve every Unicode character in the child JVM.
    $env:TOEIC_AUDIO_SMOKE_OUTPUT = Join-Path $OutputDirectory 'Đường dẫn audio thử nghiệm có dấu'
    # Own the native process handle directly: Start-Process -PassThru can expose a null ExitCode on PS 5.1.
    $process = [Diagnostics.Process]::new()
    $process.StartInfo.FileName = Join-Path $image 'AudioSmoke.exe'
    $process.StartInfo.WorkingDirectory = (Get-Location).Path
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.CreateNoWindow = $true
    $process.StartInfo.WindowStyle = 'Hidden'
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    try {
        if (-not $process.Start()) { throw 'Could not start owned TEST launcher.' }
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(60000)) { $process.Kill(); throw 'Owned TEST audio smoke timed out.' }
        $process.WaitForExit()
        [IO.File]::WriteAllText((Join-Path $OutputDirectory 'package-stdout.txt'), $stdoutTask.Result, [Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText((Join-Path $OutputDirectory 'package-stderr.txt'), $stderrTask.Result, [Text.UTF8Encoding]::new($false))
        if ($process.ExitCode -ne 0) { throw "TEST package smoke failed (exit $($process.ExitCode)). Log: $OutputDirectory" }
    } finally { $process.Dispose() }
    Get-Content -LiteralPath (Join-Path $env:TOEIC_AUDIO_SMOKE_OUTPUT 'result.txt') -Encoding UTF8
    [IO.File]::WriteAllText((Join-Path $OutputDirectory 'image-path.txt'), $image, [Text.UTF8Encoding]::new($false))
    Write-Output "Package smoke evidence: $OutputDirectory"
} finally {
    $env:TOEIC_AUDIO_SMOKE_OUTPUT = $previousOutput
    Pop-Location
}
