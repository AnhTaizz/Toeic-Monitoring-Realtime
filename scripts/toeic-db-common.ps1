$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot '../tools/exam-dataset/native-process.ps1')
function Assert-ToeicDatabaseName([string]$Name) {
    if ($Name -notmatch '^[a-z][a-z0-9_]{0,62}$') { throw 'Database name must be a simple lowercase PostgreSQL identifier.' }
}
function Resolve-ToeicPgTool([string]$Bin, [string]$Name) {
    $path = Join-Path $Bin ($Name + '.exe')
    if (!(Test-Path -LiteralPath $path -PathType Leaf)) { throw ('Missing PostgreSQL tool: ' + $Name) }
    return $path
}
function Invoke-ToeicPg([string]$Tool, [string[]]$Arguments) {
    $result = Invoke-Ex01Native -Command $Tool -Arguments $Arguments
    # Never echo SQL/COPY contents or connection diagnostics into public logs.
    if ($result.ExitCode -ne 0) { throw ('PostgreSQL client failed (exit ' + $result.ExitCode + '); content/credentials suppressed.') }
    return $result.Output.Trim()
}
function Get-ToeicConnectionArgs([string]$HostName, [int]$PortNumber, [string]$UserName, [string]$DatabaseName) {
    return @('--host', $HostName, '--port', [string]$PortNumber, '--username', $UserName, '--dbname', $DatabaseName, '--no-password')
}
function Get-ToeicHash([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Assert-ToeicExternalOutput([string]$Path) {
    $parent = [IO.DirectoryInfo]([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($Path)))
    while ($parent) {
        if (Test-Path -LiteralPath (Join-Path $parent.FullName '.git')) { throw 'SQL backups must be outside Git worktrees.' }
        $parent = $parent.Parent
    }
}
