[CmdletBinding()]
param(
    [ValidateSet('msft-disk','win32-diskdrive')][string]$Method = 'msft-disk',
    [ValidateRange(0,600)][int]$DurationSeconds = 0,
    [ValidateRange(250,60000)][int]$IntervalMillis = 1000,
    [ValidateRange(100,10000)][int]$QueryTimeoutMillis = 5000,
    [ValidateRange(4,2000)][int]$MaxLines = 1000,
    [ValidatePattern('^[A-Za-z0-9_.-]{1,32}$')][string]$MachineAlias = 'WIN-PROBE',
    [string]$OutputFile
)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$classes = Join-Path $repo 'monitoring-spike\target\classes'
$observerClasses = Join-Path $repo 'windows-observer\target\classes'
if (-not (Test-Path -LiteralPath (Join-Path $classes 'vn\edu\toeic\monitoring\usb\UsbStorageProbe.class'))) {
    throw 'Build the isolated monitoring-spike module first; see docs/USB_STORAGE_PROBE.md.'
}
if (-not (Test-Path -LiteralPath (Join-Path $observerClasses 'vn\edu\toeic\monitoring\usb\CimSnapshotSource.class'))) {
    throw 'Build the shared Windows observer with scripts/build-usb-probe.ps1 first.'
}
$receiptPath = Join-Path $repo 'monitoring-spike/target/usb-probe-build.json'
if (-not (Test-Path -LiteralPath $receiptPath)) { throw 'Run scripts/build-usb-probe.ps1 to build/test and issue a source receipt first.' }
$receipt = Get-Content -LiteralPath $receiptPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($receipt.schema -ne 'usb-probe-build-local-v1' -or $receipt.sourceSha -notmatch '^[a-f0-9]{40}$') { throw 'Invalid build receipt' }
$sourceSha = $receipt.sourceSha
$sourcePaths = @('pom.xml','windows-observer/pom.xml','windows-observer/src','monitoring-spike/pom.xml',
    'monitoring-spike/src','scripts/build-usb-probe.ps1','scripts/probe-usb-storage.ps1')
$dirty = @(& git -C $repo status --porcelain --untracked-files=all -- $sourcePaths)
if ($LASTEXITCODE -ne 0 -or $dirty.Count -ne 0) { throw 'Probe source changed. Commit and rebuild before evidence capture.' }
$expectedPaths = @($receipt.sourceFiles + $receipt.runtimeFiles | ForEach-Object { $_.path } | Sort-Object)
$actualPaths = @()
foreach ($relative in @($sourcePaths + @('monitoring-spike/target/classes','windows-observer/target/classes'))) {
    foreach ($file in @(Get-ChildItem -LiteralPath (Join-Path $repo $relative) -File -Recurse)) {
        $actualPaths += $file.FullName.Substring($repo.Length + 1).Replace('\','/')
    }
}
if (@(Compare-Object $expectedPaths @($actualPaths | Sort-Object)).Count -ne 0) { throw 'Build file inventory changed; rebuild.' }
foreach ($entry in @($receipt.sourceFiles + $receipt.runtimeFiles)) {
    # Receipt paths are treated as data and restricted to this repo, even if the receipt is tampered with.
    $path = [IO.Path]::GetFullPath((Join-Path $repo $entry.path))
    if (-not $path.StartsWith($repo + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid build receipt path' }
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) { throw 'Build/source hash mismatch; rebuild.' }
}
$classpath = $classes + [IO.Path]::PathSeparator + $observerClasses
$probeArgs = @('-cp',$classpath,'vn.edu.toeic.monitoring.usb.UsbStorageProbe','--method',$Method,
    '--duration-seconds',"$DurationSeconds",'--interval-millis',"$IntervalMillis",'--query-timeout-millis',"$QueryTimeoutMillis",
    '--max-lines',"$MaxLines",'--machine-alias',$MachineAlias,'--source-sha',$sourceSha)
if ($OutputFile) { $probeArgs += @('--output-file',$OutputFile) }
& java @probeArgs
exit $LASTEXITCODE
