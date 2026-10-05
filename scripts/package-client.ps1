# Build the Windows app-image (bundled runtime) from the shaded client JAR. Run `mvn package` first.
# Only touches client/target/jpackage-* and jpackage-out/ToeicMonitor. No DB, no .env, no installer.
param(
    [string]$JavaHome = $env:JAVA_HOME,
    # TEST only: also add the AudioSmoke launcher (harness from test classes) to verify JavaFX Media inside the image.
    [switch]$WithAudioSmoke
)
$ErrorActionPreference = 'Stop'
$appName = 'ToeicMonitor'
$appVersion = '0.1.0'
$mainJar = 'client-0.1.0-SNAPSHOT-all.jar'
$mainClass = 'vn.edu.toeic.client.Launcher'

Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    if ([System.Environment]::OSVersion.Platform -ne 'Win32NT') { throw 'This script builds a Windows app-image; run it on Windows.' }
    $binDir = if ($JavaHome) { Join-Path $JavaHome 'bin' } else { Split-Path -Parent (Get-Command jpackage -ErrorAction Stop).Source }
    $jpackage = Join-Path $binDir 'jpackage.exe'
    if (-not (Test-Path $jpackage)) { throw 'jpackage.exe not found. Install JDK 21 or pass -JavaHome <JDK 21 directory>.' }
    $jarPath = Join-Path 'client/target' $mainJar
    if (-not (Test-Path $jarPath)) { throw "Missing $jarPath. Run mvn package first." }

    # Stage only the shaded JAR: --input copies the whole directory, and client/target also holds test classes and reports.
    $stage = 'client/target/jpackage-input'
    if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
    New-Item -ItemType Directory -Force $stage | Out-Null
    Copy-Item $jarPath $stage

    $arguments = @('--type', 'app-image', '--dest', 'jpackage-out', '--name', $appName, '--input', $stage,
        '--main-jar', $mainJar, '--main-class', $mainClass, '--app-version', $appVersion, '--vendor', 'TOEIC Monitor Team')

    if ($WithAudioSmoke) {
        $harnessClasses = 'client/target/test-classes/vn/edu/toeic/client/audio'
        if (-not (Test-Path (Join-Path $harnessClasses 'AudioSmokeHarness.class'))) { throw 'Audio harness classes missing. Run mvn package first.' }
        $smokeRoot = 'client/target/jpackage-audio-smoke'
        if (Test-Path $smokeRoot) { Remove-Item -Recurse -Force $smokeRoot }
        $smokePackage = Join-Path $smokeRoot 'classes/vn/edu/toeic/client/audio'
        New-Item -ItemType Directory -Force $smokePackage | Out-Null
        Get-ChildItem $harnessClasses -Filter '*.class' | Where-Object { $_.Name -notlike '*Test*' } | Copy-Item -Destination $smokePackage
        & (Join-Path $binDir 'jar.exe') --create --file (Join-Path $stage 'audio-smoke-TEST.jar') -C (Join-Path $smokeRoot 'classes') .
        if ($LASTEXITCODE -ne 0) { throw 'Could not build the TEST audio harness JAR.' }
        $launcher = Join-Path $smokeRoot 'AudioSmoke.properties'
        Set-Content -Encoding ascii $launcher @('main-jar=audio-smoke-TEST.jar', 'main-class=vn.edu.toeic.client.audio.AudioSmokeHarness')
        $arguments += @('--add-launcher', "AudioSmoke=$launcher")
    }

    # Replace only this script's previous output, and never while that app is still running.
    $outputRoot = Join-Path (Get-Location).Path 'jpackage-out'
    $output = Join-Path $outputRoot $appName
    if (Test-Path $output) {
        $running = Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.Path -and $_.Path.StartsWith($output + '\', [System.StringComparison]::OrdinalIgnoreCase) }
        if ($running) { throw "Close the running app-image first (PID $($running.Id -join ', ')); nothing was deleted." }
        Remove-Item -Recurse -Force $output
    }

    & $jpackage @arguments
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed with exit code $LASTEXITCODE." }

    $exe = Join-Path $output "$appName.exe"
    if (-not (Test-Path $exe)) { throw "jpackage finished but $appName.exe is missing." }
    if (-not (Test-Path (Join-Path $output 'runtime/bin/server/jvm.dll'))) { throw 'App-image has no bundled Java runtime.' }
    if (-not (Test-Path (Join-Path $output "app/$mainJar"))) { throw 'App-image does not contain the client JAR.' }

    $files = Get-ChildItem $output -Recurse -File
    $megabytes = [math]::Round(($files | Measure-Object Length -Sum).Sum / 1MB, 1)
    $runtimeVersion = (Get-Content (Join-Path $output 'runtime/release') | Where-Object { $_ -like 'JAVA_VERSION=*' }) -replace 'JAVA_VERSION=', ''
    Write-Output "App-image: jpackage-out\$appName ($megabytes MB, $($files.Count) files)"
    Write-Output "Launcher: jpackage-out\$appName\$appName.exe"
    Write-Output "Bundled runtime: Java $runtimeVersion (target machine needs no JDK/JRE)"
    Write-Output "Client JAR SHA256: $((Get-FileHash (Join-Path $output "app/$mainJar") -Algorithm SHA256).Hash)"
    if ($WithAudioSmoke) { Write-Output 'TEST launcher AudioSmoke.exe included; rebuild without -WithAudioSmoke for the image to hand over.' }
} finally { Pop-Location }
