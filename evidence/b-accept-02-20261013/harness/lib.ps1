# ASCII-only helpers for the step-4 extra GUI acceptance run (agent-driven UI Automation).
$script:AcceptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path (Split-Path -Parent $script:AcceptDir) "gui.ps1")
$script:Repo = "E:\Project\Toeic-Monitoring-Realtime-stack"
$script:StateFile = Join-Path $script:AcceptDir "state.json"
$script:Shots = Join-Path $script:AcceptDir "shots"
$script:ActionLog = Join-Path $script:AcceptDir "ui-actions.log"
New-Item -ItemType Directory -Force $script:Shots | Out-Null
if (-not ([System.Management.Automation.PSTypeName]'ToeicType3').Type) {
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class ToeicType3 {
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
    public static void Text(IntPtr hwnd, string text) { foreach (char c in text) PostMessage(hwnd, 0x0102, (IntPtr)c, (IntPtr)1); }
}
"@
}

function Log-Step([string]$text) {
    $line = "{0} {1}" -f (Get-Date).ToUniversalTime().ToString("o"), $text
    Add-Content -Path $script:ActionLog -Value $line -Encoding UTF8
    $line
}

function Sql([string]$query) {
    Import-DbEnv
    docker exec toeic-db psql -U $env:DB_USER -d $env:DB_NAME -v ON_ERROR_STOP=1 -At -c $query
}

function Seed-Password {
    Import-DbEnv
    if ($env:TOEIC_SEED_PASSWORD) { $env:TOEIC_SEED_PASSWORD } else { "ChangeMe123!" }  # MOCK dev seed account password
}

# The quick-fill button puts the user name in and focuses the password field. The password is posted
# as WM_CHAR (repeat count 1) to this window only; the window is never brought to the foreground.
function Login-As([int]$procId, [string]$user) {
    Invoke-El (Wait-In $procId "Button" "*($user)" 30)
    Start-Sleep -Milliseconds 500
    $hwnd = (Get-Process -Id $procId).MainWindowHandle
    [ToeicType3]::Text($hwnd, (Seed-Password))
    Start-Sleep -Milliseconds 500
    $login = [char]0x0110 + [char]0x0103 + "ng nh" + [char]0x1EAD + "p"   # exact text of the login button
    Invoke-El (Find-In $procId "Button" $login)
    Log-Step "login submitted for $user in pid $procId (window not brought to foreground)"
}

function Start-Client([int]$port, [string[]]$extra, [string]$tag) {
    $arguments = @("-Dtoeic.server.url=http://127.0.0.1:$port") + $extra + @("-jar", "client\target\client-0.1.0-SNAPSHOT-all.jar")
    $p = Start-Process java -ArgumentList $arguments -WorkingDirectory $script:Repo -PassThru `
        -RedirectStandardOutput (Join-Path $script:AcceptDir "client-$tag.stdout.log") -RedirectStandardError (Join-Path $script:AcceptDir "client-$tag.stderr.log")
    $deadline = (Get-Date).AddSeconds(40)
    while ((Get-Date) -lt $deadline) {
        if (@(Get-AppWindows $p.Id).Count -gt 0) { Log-Step "client JVM started tag=$tag pid=$($p.Id) port=$port args=$($extra -join ' ')" | Out-Null; return $p.Id }
        Start-Sleep -Milliseconds 300
    }
    throw "client window not found"
}

function Stop-Client([int]$procId, [string]$tag) {
    $p = Get-Process -Id $procId -ErrorAction SilentlyContinue
    if (-not $p) { return "client $tag already gone" }
    [void]$p.CloseMainWindow()
    $exited = $p.WaitForExit(15000)
    Log-Step "client JVM tag=$tag pid=$procId closed by window close; exited=$exited"
}

function Texts([int]$procId, [string]$pattern) {
    Read-Texts $procId | Where-Object { $_ -match $pattern } | Select-Object -Unique
}

function Buttons([int]$procId, [string]$pattern = ".") {
    $rows = foreach ($window in @(Get-AppWindows $procId)) { Show-Els $window "Button" }
    @($rows | Where-Object { $_ -match $pattern })
}

# All named elements of one control type across the process' windows, as "Type [on|off] name" rows.
function Els([int]$procId, [string]$type, [string]$pattern = ".") {
    $rows = foreach ($window in @(Get-AppWindows $procId)) { Show-Els $window $type }
    @($rows | Where-Object { $_ -match $pattern })
}

function Options([int]$procId) {
    $o = @(Get-Els (Get-MainWindow $procId) "Button" | Where-Object { $_.Current.Name -match "^[ABCD]\." })
    "options enabled: " + (@($o | Where-Object { $_.Current.IsEnabled }).Count) + " / " + $o.Count
}

function Shot([int]$procId, [string]$name) {
    $path = Join-Path $script:Shots $name
    Save-Shot ([IntPtr](Get-MainWindow $procId).Current.NativeWindowHandle) $path
    Log-Step "screenshot $name" | Out-Null
    "shot: $name"
}

function Shot-El($element, [string]$name) {
    $path = Join-Path $script:Shots $name
    Save-Shot ([IntPtr]$element.Current.NativeWindowHandle) $path
    Log-Step "screenshot $name" | Out-Null
    "shot: $name"
}

function Wait-Text([int]$procId, [string]$pattern, [int]$seconds = 30) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        $hit = Read-Texts $procId | Where-Object { $_ -match $pattern } | Select-Object -First 1
        if ($hit) { return $hit }
        Start-Sleep -Milliseconds 400
    }
    throw "text not seen in ${seconds}s: $pattern"
}

function Find-Dialog([int]$procId, [string]$like) {
    foreach ($window in @(Get-AppWindows $procId)) {
        if ($window.Current.Name -like $like) { return $window }
        foreach ($child in @(Get-Els $window "Window")) { if ($child.Current.Name -like $like) { return $child } }
    }
    return $null
}

function Wait-Dialog([int]$procId, [string]$like, [int]$seconds = 30) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        $d = Find-Dialog $procId $like
        if ($d) { return $d }
        Start-Sleep -Milliseconds 300
    }
    throw "dialog not found: $like"
}

# Posted mouse click on a control of the process' main window (returns at once, safe with modal dialogs).
function Click-In([int]$procId, [string]$type, [string]$like) {
    $el = Wait-In $procId $type $like 20
    Click-El (Get-MainWindow $procId) $el
    Log-Step "posted click on $type '$($el.Current.Name -replace '\s+', ' ')' in pid $procId" | Out-Null
}

# Server-side autosave log of one attempt: revision, epoch, UTC time and the saved map.
function Autosave-Log([string]$schema, [string]$attempt) {
    Sql "select answer_revision || ' epoch=' || writer_epoch || ' at=' || to_char(decision_at at time zone 'UTC','HH24:MI:SS.MS') || ' ' || answers_json from $schema.exam_autosave_requests where attempt_id = '$attempt' order by answer_revision"
}

function Attempt-Row([string]$schema, [string]$attempt) {
    Sql "select attempt_id || ' state=' || state || ' epoch=' || writer_epoch || ' rev=' || saved_revision || ' answers=' || answers_json || ' submitted=' || coalesce(to_char(submitted_at at time zone 'UTC','HH24:MI:SS'),'-') || ' correct=' || coalesce(correct_count::text,'-') || '/' || coalesce(total_questions::text,'-') from $schema.monitoring_attempts where attempt_id = '$attempt'"
}

# Out-of-band HTTP as the same candidate with a second login session (no takeover): used only to inject
# the "same revision, different content" situation. Returns the bearer token; never prints it.
function Api-Login([int]$port, [string]$user) {
    $body = @{ requestId = [Guid]::NewGuid().ToString(); username = $user; password = (Seed-Password) } | ConvertTo-Json
    (Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$port/api/v1/auth/login" -ContentType "application/json" -Body $body).token
}

function Api-Call([string]$method, [int]$port, [string]$path, [string]$token, $body) {
    $headers = @{ Authorization = "Bearer $token" }
    try {
        if ($null -ne $body) {
            $json = $body | ConvertTo-Json -Depth 6 -Compress
            $r = Invoke-WebRequest -UseBasicParsing -Method $method -Uri "http://127.0.0.1:$port$path" -Headers $headers -ContentType "application/json" -Body ([Text.Encoding]::UTF8.GetBytes($json))
        } else {
            $r = Invoke-WebRequest -UseBasicParsing -Method $method -Uri "http://127.0.0.1:$port$path" -Headers $headers
        }
        [pscustomobject]@{ status = [int]$r.StatusCode; body = $r.Content }
    } catch {
        $resp = $_.Exception.Response
        if (-not $resp) { throw }
        $reader = New-Object IO.StreamReader($resp.GetResponseStream()); $text = $reader.ReadToEnd()
        [pscustomobject]@{ status = [int]$resp.StatusCode; body = $text }
    }
}
