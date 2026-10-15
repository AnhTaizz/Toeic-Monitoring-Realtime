# Build a fresh app-image. The optional TEST launcher exercises B1 inside the bundled runtime.
param([string]$JavaHome = $env:JAVA_HOME, [switch]$WithAudioSmoke, [string]$ManifestTool)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $jdkBin = Join-Path $JavaHome 'bin'
    $run = 't3b1-' + [Guid]::NewGuid().ToString('N')
    # jpackage's native argv can lose characters outside the Windows ANSI code page.
    # Build with relative ASCII arguments, then copy with PowerShell's Unicode filesystem APIs.
    $staging = "client/target/$run/input"
    $destination = "jpackage-out/$run/build"
    New-Item -ItemType Directory -Force $staging | Out-Null
    $jarName = 'client-0.1.0-SNAPSHOT-all.jar'
    Copy-Item -LiteralPath "client/target/$jarName" -Destination $staging
    $arguments = @('--type','app-image','--dest',$destination,'--name','ToeicMonitor','--input',$staging,
        '--main-jar',$jarName,'--main-class','vn.edu.toeic.client.Launcher','--app-version','0.1.0',
        '--add-modules','java.net.http,java.desktop,java.logging,jdk.unsupported,jdk.crypto.ec')
    if ($WithAudioSmoke) {
        $classes = Join-Path (Split-Path -Parent $staging) 'test-classes'
        $testPackage = Join-Path $classes 'vn/edu/toeic/client/audio'
        New-Item -ItemType Directory -Force $testPackage | Out-Null
        Get-ChildItem 'client/target/test-classes/vn/edu/toeic/client/audio' -Filter 'AudioPackageSmoke*.class' |
            Copy-Item -Destination $testPackage
        & (Join-Path $jdkBin 'jar.exe') --create --file (Join-Path $staging 'audio-smoke-TEST.jar') -C $classes .
        if ($LASTEXITCODE -ne 0) { throw 'TEST harness packaging failed.' }
        $config = Join-Path (Get-Location) (Join-Path (Split-Path -Parent $staging) 'AudioSmoke.properties')
        [IO.File]::WriteAllLines($config, @('main-jar=audio-smoke-TEST.jar', 'main-class=vn.edu.toeic.client.audio.AudioPackageSmoke'), [Text.Encoding]::ASCII)
        $arguments += @('--add-launcher',"AudioSmoke=$config",'--win-console')
    }
    & (Join-Path $jdkBin 'jpackage.exe') @arguments
    if ($LASTEXITCODE -ne 0) { throw 'jpackage app-image failed.' }
    # JDK 21's Windows JLI uses narrow filesystem calls before Java starts. Opt the generated launchers
    # into UTF-8 via their Windows application manifest; do not change any installed JDK/DLL or OS locale.
    if (-not $ManifestTool) {
        $sdkBin = Join-Path ${env:ProgramFiles(x86)} 'Windows Kits/10/bin'
        $ManifestTool = Get-ChildItem -LiteralPath $sdkBin -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'x64/mt.exe' } |
            Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    }
    if (-not $ManifestTool) { throw 'Windows SDK mt.exe is required for Unicode launchers; pass -ManifestTool <mt.exe>.' }
    $launchers = @('ToeicMonitor')
    if ($WithAudioSmoke) { $launchers += 'AudioSmoke' }
    foreach ($launcherName in $launchers) {
        $launcherExe = Join-Path $destination "ToeicMonitor/$launcherName.exe"
        $manifestFile = Join-Path (Split-Path -Parent $staging) "$launcherName.manifest"
        & $ManifestTool -nologo "-inputresource:$launcherExe;#1" "-out:$manifestFile" | Out-Host
        if ($LASTEXITCODE -ne 0) { throw 'Could not read generated launcher manifest.' }
        [xml]$manifestXml = [IO.File]::ReadAllText((Join-Path (Get-Location) $manifestFile))
        $namespaces = [Xml.XmlNamespaceManager]::new($manifestXml.NameTable)
        $namespaces.AddNamespace('a', 'urn:schemas-microsoft-com:asm.v3')
        $windowsSettings = $manifestXml.SelectSingleNode('//a:application/a:windowsSettings', $namespaces)
        if (-not $windowsSettings) {
            $application = $manifestXml.CreateElement('application', 'urn:schemas-microsoft-com:asm.v3')
            $windowsSettings = $manifestXml.CreateElement('windowsSettings', 'urn:schemas-microsoft-com:asm.v3')
            [void]$application.AppendChild($windowsSettings)
            [void]$manifestXml.DocumentElement.AppendChild($application)
        }
        $activeCodePage = $manifestXml.CreateElement('activeCodePage', 'http://schemas.microsoft.com/SMI/2019/WindowsSettings')
        $activeCodePage.InnerText = 'UTF-8'
        [void]$windowsSettings.AppendChild($activeCodePage)
        $manifestXml.Save((Join-Path (Get-Location) $manifestFile))
        $launcherFile = Get-Item -LiteralPath $launcherExe
        $wasReadOnly = $launcherFile.IsReadOnly
        $launcherFile.IsReadOnly = $false
        try {
            & $ManifestTool -nologo -manifest $manifestFile "-outputresource:$launcherExe;#1" | Out-Host
            if ($LASTEXITCODE -ne 0) { throw 'Could not embed UTF-8 launcher manifest.' }
        } finally { $launcherFile.IsReadOnly = $wasReadOnly }
    }
    $unicodeDestination = Join-Path (Get-Location) "jpackage-out/$run/Đóng gói audio có dấu và khoảng trắng"
    New-Item -ItemType Directory -Force $unicodeDestination | Out-Null
    Copy-Item -LiteralPath (Join-Path $destination 'ToeicMonitor') -Destination $unicodeDestination -Recurse
    $image = Join-Path $unicodeDestination 'ToeicMonitor'
    if (-not (Test-Path -LiteralPath (Join-Path $image 'runtime/bin/server/jvm.dll'))) { throw 'Bundled runtime missing.' }
    # Capture a returned absolute path, rather than relying on console output for later smoke.
    Write-Output $image
} finally { Pop-Location }
