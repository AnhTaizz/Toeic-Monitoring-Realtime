# T1-B4 smoke: run the packaged app-image from its build location and from copies in a directory with a
# space and a directory with Vietnamese diacritics; play TEST audio through JavaFX Media inside the image.
# Needs an image built by `scripts/package-client.ps1 -WithAudioSmoke`. No server, DB or login is involved.
# Only creates/removes the test directories under -WorkRoot and stops processes started from them.
param(
    [string]$AppImage = 'jpackage-out/ToeicMonitor',
    [string]$WorkRoot = (Join-Path $env:TEMP 'toeic-b4'),
    [string]$JavaHome = $env:JAVA_HOME,
    [int]$TimeoutSeconds = 40
)
$ErrorActionPreference = 'Stop'
# Kept as code points so this file stays ASCII for Windows PowerShell 5.1.
$unicodeName = "Th$([char]0x1EED) nghi$([char]0x1EC7)m TOEIC"
$audioName = "$([char]0x00C2)m thanh m$([char]0x1EAB)u"
$results = New-Object System.Collections.Generic.List[object]
function Add-Result($where, $check, $result, $detail) {
    if ($result -is [bool]) { $result = if ($result) { 'PASS' } else { 'FAIL' } }
    $results.Add([pscustomobject]@{ Where = $where; Check = $check; Result = $result; Detail = $detail })
}
function Get-ImageProcess($root) {
    Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.Path -and $_.Path.StartsWith($root + '\', [System.StringComparison]::OrdinalIgnoreCase) }
}
# Paths go to the harness through environment variables: JVM command-line arguments pass through the ANSI
# code page on Windows and lose characters outside it (measured in the first T1-B4 run).
function Start-Harness($command, $arguments, $variables) {
    try {
        foreach ($key in $variables.Keys) { [Environment]::SetEnvironmentVariable($key, $variables[$key], 'Process') }
        $process = if ($arguments) { Start-Process -FilePath $command -PassThru -ArgumentList $arguments } else { Start-Process -FilePath $command -PassThru }
    } finally {
        foreach ($key in $variables.Keys) { [Environment]::SetEnvironmentVariable($key, $null, 'Process') }
    }
    $exited = $process.WaitForExit($TimeoutSeconds * 1000)
    if (-not $exited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
    $exited
}
function Test-Audio($where, $command, $prefix, $audioFile, $expected) {
    $resultFile = "$audioFile.result.txt"
    Remove-Item -LiteralPath $resultFile -ErrorAction SilentlyContinue
    $exited = Start-Harness $command ($prefix + @('--auto')) @{ TOEIC_AUDIO_SMOKE_FILE = $audioFile; TOEIC_AUDIO_SMOKE_RESULT = $resultFile }
    $r = @{}
    if (Test-Path -LiteralPath $resultFile) {
        foreach ($line in Get-Content -LiteralPath $resultFile -Encoding utf8) { $i = $line.IndexOf('='); if ($i -gt 0) { $r[$line.Substring(0, $i)] = $line.Substring($i + 1) } }
    }
    $detail = "outcome=$($r['outcome']) statuses=$($r['statuses']) playedMs=$($r['playedMillis']) durationMs=$($r['durationMillis']) spacePath=$($r['pathHasSpace']) nonAsciiPath=$($r['pathHasNonAscii']) exited=$exited error=$($r['error'])"
    Add-Result $where "audio $([System.IO.Path]::GetFileName($audioFile)) expect $expected" ($exited -and $r['outcome'] -eq $expected) $detail
}

Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $source = (Resolve-Path $AppImage).Path
    if (-not (Test-Path (Join-Path $source 'AudioSmoke.exe'))) { throw 'AudioSmoke.exe missing. Build with scripts/package-client.ps1 -WithAudioSmoke.' }
    $fixtures = Get-ChildItem 'client/src/test/resources/audio' -File | Where-Object { $_.Extension -in '.mp3', '.m4a' }
    if (-not $fixtures) { throw 'TEST audio fixtures missing.' }
    New-Item -ItemType Directory -Force $WorkRoot | Out-Null
    $workRootPath = (Resolve-Path $WorkRoot).Path
    $codePage = (Get-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Control\Nls\CodePage').ACP

    # Audio always sits in a directory with spaces and Vietnamese diacritics, wherever the app itself is.
    $locations = @([pscustomobject]@{ Label = 'build-output'; Image = $source; Audio = (Join-Path $workRootPath "build-output $audioName") })
    foreach ($entry in @(@('space-path', 'TOEIC Test'), @('unicode-path', $unicodeName))) {
        $directory = Join-Path $workRootPath $entry[1]
        if (Get-ImageProcess $directory) { throw "A previous test app is still running from the $($entry[0]) copy; close it first." }
        if (Test-Path -LiteralPath $directory) { Remove-Item -LiteralPath $directory -Recurse -Force }
        New-Item -ItemType Directory -Force $directory | Out-Null
        Copy-Item -LiteralPath $source -Destination (Join-Path $directory 'ToeicMonitor') -Recurse
        $locations += [pscustomobject]@{ Label = $entry[0]; Image = (Join-Path $directory 'ToeicMonitor'); Audio = (Join-Path $directory $audioName) }
    }

    $playable = $null
    foreach ($location in $locations) {
        $where = $location.Label
        # 1. Main launcher: window appears, closes on request, no process left behind.
        $null = Start-Process -FilePath (Join-Path $location.Image 'ToeicMonitor.exe') -PassThru
        $window = $null
        $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
        while (-not $window -and (Get-Date) -lt $deadline) {
            Start-Sleep -Milliseconds 500
            $alive = @(Get-ImageProcess $location.Image)
            $window = $alive | Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -eq 'TOEIC Monitor' } | Select-Object -First 1
            if (-not $alive) { break }
        }
        Add-Result $where 'ToeicMonitor.exe opens main window' ([bool]$window) $(if ($window) { 'title=TOEIC Monitor' } else { 'launcher exited without a window' })
        if (-not $window) {
            Get-ImageProcess $location.Image | ForEach-Object { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }
            Add-Result $where 'window close / audio checks' 'BLOCKED' 'app does not start from this directory'
            continue
        }
        $null = $window.CloseMainWindow()
        $deadline = (Get-Date).AddSeconds(15)
        while ((Get-ImageProcess $location.Image) -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 500 }
        $left = @(Get-ImageProcess $location.Image)
        Add-Result $where 'window close leaves no process' ($left.Count -eq 0) "remaining=$($left.Count)"
        $left | ForEach-Object { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }

        # 2. JavaFX Media inside the image. The packaged harness itself writes the WAV tone.
        if (Test-Path -LiteralPath $location.Audio) { Remove-Item -LiteralPath $location.Audio -Recurse -Force }
        New-Item -ItemType Directory -Force $location.Audio | Out-Null
        $smoke = Join-Path $location.Image 'AudioSmoke.exe'
        $wav = Join-Path $location.Audio 'TEST tone 440hz.wav'
        $null = Start-Harness $smoke $null @{ TOEIC_AUDIO_SMOKE_WRITE_TONE = $wav }
        Add-Result $where 'harness writes WAV tone' (Test-Path -LiteralPath $wav) ''
        Test-Audio $where $smoke @() $wav 'PLAYED'
        foreach ($fixture in $fixtures) {
            $copy = Join-Path $location.Audio ($fixture.Name -replace '-', ' ')
            Copy-Item -LiteralPath $fixture.FullName -Destination $copy
            Test-Audio $where $smoke @() $copy 'PLAYED'
        }
        # 3. A file that is not audio, or a missing file, must end in ERROR: no crash, no hang.
        $broken = Join-Path $location.Audio 'TEST not audio.mp3'
        Set-Content -LiteralPath $broken -Encoding ascii -Value 'TEST: this is text, not MP3 data.'
        Test-Audio $where $smoke @() $broken 'ERROR'
        Test-Audio $where $smoke @() (Join-Path $location.Audio 'TEST missing.mp3') 'ERROR'
        $left = @(Get-ImageProcess $location.Image)
        Add-Result $where 'audio runs leave no process' ($left.Count -eq 0) "remaining=$($left.Count)"
        $left | ForEach-Object { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }
        $playable = $location.Audio
    }

    # 4. Same harness on the developer JDK (java -cp), reported separately from the packaged result.
    $java = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
    if ($playable -and (Test-Path 'client/target/test-classes/vn/edu/toeic/client/audio/AudioSmokeHarness.class')) {
        $prefix = @('-cp', 'client/target/test-classes;client/target/client-0.1.0-SNAPSHOT-all.jar', 'vn.edu.toeic.client.audio.AudioSmokeHarness')
        foreach ($file in Get-ChildItem -LiteralPath $playable -File | Where-Object { $_.Name -like 'TEST tone*' -and $_.Extension -ne '.txt' }) {
            Test-Audio 'dev-jdk (java -cp)' $java $prefix $file.FullName 'PLAYED'
        }
    }

    $results | Format-Table -AutoSize -Wrap | Out-String -Width 220 | Write-Output
    $failed = @($results | Where-Object { $_.Result -eq 'FAIL' }).Count
    $blocked = @($results | Where-Object { $_.Result -eq 'BLOCKED' }).Count
    Write-Output "T1-B4 smoke (system ANSI code page $codePage): $($results.Count - $failed - $blocked) PASS, $failed FAIL, $blocked BLOCKED."
    Write-Output 'PLAYED = JavaFX Media reported PLAYING and playback time advanced; it does not prove the speaker was audible.'
    if ($failed -gt 0) { exit 1 }
} finally { Pop-Location }
