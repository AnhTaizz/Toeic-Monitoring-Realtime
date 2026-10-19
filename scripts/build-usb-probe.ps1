[CmdletBinding()]
param([string]$MavenRepository)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$sourcePaths = @('monitoring-spike/src','scripts/build-usb-probe.ps1','scripts/probe-usb-storage.ps1')
$dirty = @(& git -C $repo status --porcelain --untracked-files=all -- $sourcePaths)
if ($LASTEXITCODE -ne 0 -or $dirty.Count -ne 0) { throw 'Commit probe sources before making a source-pinned build.' }
$sourceSha = (& git -C $repo rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve source SHA' }
$mavenArgs = @('--batch-mode','--no-transfer-progress','-pl','monitoring-spike','clean','verify')
if ($MavenRepository) { $mavenArgs = @('-Dmaven.repo.local=' + $MavenRepository) + $mavenArgs }
Push-Location -LiteralPath $repo
try {
    & mvn @mavenArgs
    if ($LASTEXITCODE -ne 0) { throw 'Isolated monitoring-spike build/tests failed; no build receipt issued.' }
} finally { Pop-Location }
function Get-ReceiptFiles([string[]]$RelativeRoots) {
    $entries = @()
    foreach ($relative in $RelativeRoots) {
        $path = Join-Path $repo $relative
        foreach ($file in @(Get-ChildItem -LiteralPath $path -File -Recurse | Sort-Object FullName)) {
            $name = $file.FullName.Substring($repo.Length + 1).Replace('\','/')
            $entries += [ordered]@{ path=$name; sha256=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
        }
    }
    return $entries
}
$receipt = [ordered]@{
    schema='usb-probe-build-local-v1'; sourceSha=$sourceSha
    sourceFiles=@(Get-ReceiptFiles $sourcePaths)
    runtimeFiles=@(Get-ReceiptFiles @('monitoring-spike/target/classes'))
}
$receiptPath = Join-Path $repo 'monitoring-spike/target/usb-probe-build.json'
[IO.File]::WriteAllText($receiptPath, ($receipt | ConvertTo-Json -Depth 6), [Text.UTF8Encoding]::new($false))
Write-Host ('PROBE_BUILD source={0}; sourceFiles={1}; runtimeFiles={2}; receipt=monitoring-spike/target/usb-probe-build.json' -f $sourceSha,$receipt.sourceFiles.Count,$receipt.runtimeFiles.Count)
