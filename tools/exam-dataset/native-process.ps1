# Explicit ProcessStartInfo also works in WSL-launched Windows PowerShell.
function Invoke-Ex01Native {
    param([string]$Command, [string[]]$Arguments)
    $application = Get-Command $Command -CommandType Application -ErrorAction Stop
    $quoted = @($Arguments | ForEach-Object {
        $value = [regex]::Replace([string]$_, '(\\*)"', '$1$1\"')
        $value = [regex]::Replace($value, '(\\+)$', '$1$1')
        '"' + $value + '"'
    }) -join ' '
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    if ([string]::IsNullOrEmpty($info.EnvironmentVariables['PATHEXT'])) { $info.EnvironmentVariables['PATHEXT'] = '.COM;.EXE;.BAT;.CMD' }
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = New-Object System.Text.UTF8Encoding($false)
    $info.StandardErrorEncoding = New-Object System.Text.UTF8Encoding($false)
    if ($application.Source.EndsWith('.cmd', [StringComparison]::OrdinalIgnoreCase)) {
        $info.FileName = Join-Path $env:SystemRoot 'System32/cmd.exe'
        $info.Arguments = '/d /s /c ""' + $application.Source + '" ' + $quoted + '"'
    } else {
        $info.FileName = $application.Source
        $info.Arguments = $quoted
    }
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $info
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        return [pscustomobject]@{ ExitCode = $process.ExitCode; Output = $stdout.Result; Error = $stderr.Result }
    } finally { $process.Dispose() }
}
