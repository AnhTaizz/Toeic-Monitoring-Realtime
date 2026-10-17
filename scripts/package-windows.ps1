# Production MSI: only the shaded client JAR, generated launcher, metadata and bundled runtime.
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [Parameter(Mandatory=$true)][string]$WixHome,
    [Parameter(Mandatory=$true)][string]$ManifestTool,
    [ValidatePattern('^\d{1,3}\.\d{1,3}\.\d{1,5}$')][string]$AppVersion = '0.3.5',
    [switch]$Offline,
    [switch]$AllowDirty,
    [switch]$PreflightOnly
)
$ErrorActionPreference = 'Stop'
$previous = @{}
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    foreach ($key in @('JAVA_HOME','PATH','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')) {
        $previous[$key] = [Environment]::GetEnvironmentVariable($key,'Process')
    }
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT -or -not [Environment]::Is64BitProcess) { throw 'Use a Windows x64 packaging process.' }
    if (-not $JavaHome) { throw 'Pass -JavaHome <JDK 21 directory>.' }
    $JavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
    $release = Get-Content -LiteralPath (Join-Path $JavaHome 'release') -Raw
    if ($release -notmatch 'JAVA_VERSION="21[.\"]') { throw 'Use the project JDK 21; do not silently switch JDK.' }
    if ($release -notmatch 'OS_ARCH="(amd64|x86_64)"') { throw 'Use a Windows x64 JDK 21.' }
    $WixHome = (Resolve-Path -LiteralPath $WixHome).Path
    $ManifestTool = (Resolve-Path -LiteralPath $ManifestTool).Path
    if (-not (Test-Path -LiteralPath $ManifestTool -PathType Leaf)) { throw '-ManifestTool must name Windows SDK mt.exe, not a directory.' }
    foreach ($tool in @('java.exe','jar.exe','jpackage.exe','jlink.exe','javac.exe')) {
        if (-not (Test-Path -LiteralPath (Join-Path $JavaHome "bin/$tool") -PathType Leaf)) { throw "Missing JDK tool: $tool" }
    }
    foreach ($tool in @('candle.exe','light.exe')) {
        if (-not (Test-Path -LiteralPath (Join-Path $WixHome $tool) -PathType Leaf)) { throw "Missing WiX 3 tool: $tool" }
        if ((Get-Item -LiteralPath (Join-Path $WixHome $tool)).VersionInfo.FileMajorPart -ne 3) { throw 'JDK 21 packaging requires WiX 3 candle/light.' }
    }
    if ($PreflightOnly) { Write-Output 'PASS prerequisites only; no build, installer, installation or IT01 claim.'; return }
    $sha = (& git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot identify source commit.' }
    $dirty = [bool](& git status --porcelain --untracked-files=normal)
    if ($dirty -and -not $AllowDirty) { throw 'Commit source first, or explicitly use -AllowDirty for a development artifact (not a release).' }
    $versionParts = $AppVersion.Split('.')
    if ([int]$versionParts[0] -gt 255 -or [int]$versionParts[1] -gt 255 -or [int]$versionParts[2] -gt 65535) { throw 'MSI version limits: 255.255.65535.' }
    $run = Join-Path (Get-Location) ('target/t3-b5/package-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force $run | Out-Null
    $env:JAVA_HOME = $JavaHome
    $env:PATH = "$WixHome;$(Join-Path $JavaHome 'bin');" + $env:PATH
    # Packaging must not inherit agents, secret JVM arguments or experimental DELTA settings.
    foreach ($key in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')) { [Environment]::SetEnvironmentVariable($key,$null,'Process') }
    # List modules explicitly: -am also selects the root POM and would clean target/ tooling/logs.
    $arguments = @('-B',"-Dmaven.repo.local=$env:USERPROFILE/.m2/repository",'-pl','protocol,client','clean','package','-DskipTests')
    if ($Offline) { $arguments += '-o' }
    & mvn @arguments *> (Join-Path $run 'build.log')
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed: $run/build.log" }
    $image = & "$PSScriptRoot/package-t3b1.ps1" -JavaHome $JavaHome -ManifestTool $ManifestTool -AppVersion $AppVersion -PlainImage
    $jar = Join-Path $image 'app/client-0.1.0-SNAPSHOT-all.jar'
    $entries = & (Join-Path $JavaHome 'bin/jar.exe') tf $jar
    if ($LASTEXITCODE -ne 0 -or ($entries -match '(^|/)\.env$|^vn/edu/toeic/.*(Test.class|Smoke.*|InstalledClientAgent.*)$')) { throw 'Unexpected test/config content in production JAR.' }
    foreach ($required in @('javafx/scene/media/Media.class','javafx/scene/control/Control.class','jfxmedia.dll','glass.dll')) {
        if ($entries -notcontains $required) { throw "Missing Windows JavaFX resource: $required" }
    }
    $metadata = [ordered]@{
        runtimeServerConfig='GUI override; JVM property > process TOEIC_SERVER_URL > bounded user server-url.txt > default; no credentials';
        application='ToeicMonitor'; version=$AppVersion; sourceCommit=$sha; dirty=$dirty
        builtUtc=[DateTime]::UtcNow.ToString('o'); monitoringDefault='FULL'
        java=(Select-String -InputObject $release -Pattern 'JAVA_VERSION="([^"]+)"').Matches.Groups[1].Value
        clientJarSha256=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
        wixVersion=(Get-Item -LiteralPath (Join-Path $WixHome 'light.exe')).VersionInfo.FileVersion
        manifestToolVersion=(Get-Item -LiteralPath $ManifestTool).VersionInfo.FileVersion
    }
    [IO.File]::WriteAllText((Join-Path $image 'app/build-info.json'),($metadata | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
    # Optional launcher reads a user-writable config, never credentials or install-directory state.
    Copy-Item -LiteralPath "$PSScriptRoot/start-packaged-client.ps1" -Destination (Join-Path $image 'Start-ToeicMonitor.ps1')
    # Reuse the Unicode-corrected image, never regenerate its launcher during MSI packaging.
    & (Join-Path $JavaHome 'bin/jpackage.exe') --type msi --app-image $image --dest $run `
        --name ToeicMonitor --app-version $AppVersion --vendor 'TOEIC Monitor Team' `
        --description 'TOEIC desktop client (server and PostgreSQL installed separately)' `
        --win-per-user-install --win-dir-chooser --win-menu --win-menu-group 'TOEIC Monitor' `
        --win-upgrade-uuid '617462c2-b3e4-4b50-9a52-63f1f4b3a535' *> (Join-Path $run 'jpackage.log')
    if ($LASTEXITCODE -ne 0) { throw "MSI packaging failed: $run/jpackage.log" }
    if ((& git rev-parse HEAD).Trim() -ne $sha -or (-not $dirty -and [bool](& git status --porcelain --untracked-files=normal))) {
        throw 'Source changed during packaging. Do not distribute this artifact; rebuild from a stable commit.'
    }
    $installers = @(Get-ChildItem -LiteralPath $run -Filter '*.msi')
    if ($installers.Count -ne 1) { throw 'jpackage must produce exactly one MSI.' }
    $installer = $installers[0]
    $metadata['installer'] = $installer.Name
    $metadata['installerSha256'] = (Get-FileHash -LiteralPath $installer.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText((Join-Path $run 'build-info.json'),($metadata | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText(($installer.FullName+'.sha256'),($metadata.installerSha256+'  '+$installer.Name+"`r`n"),[Text.Encoding]::ASCII)
    Write-Host "MSI: $($installer.FullName)"
    Write-Host "Source: $sha (dirty=$dirty); SHA256: $($metadata.installerSha256)"
    Write-Output $installer.FullName
} finally {
    foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key,$previous[$key],'Process') }
    Pop-Location
}
