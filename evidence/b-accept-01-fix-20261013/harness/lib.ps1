# ASCII-only helpers for the B-ACCEPT-01 GUI acceptance run (agent-driven UI Automation).
$script:AcceptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path (Split-Path -Parent $script:AcceptDir) "gui.ps1")
$script:Repo = "E:\Project\Toeic-Monitoring-Realtime-b-accept01"
$script:StateFile = Join-Path $script:AcceptDir "state.json"
$script:Shots = Join-Path $script:AcceptDir "shots"
$script:ActionLog = Join-Path $script:AcceptDir "ui-actions.log"
New-Item -ItemType Directory -Force $script:Shots | Out-Null
if (-not ([System.Management.Automation.PSTypeName]'ToeicType').Type) {
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class ToeicType {
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
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

# Quick-fill button sets the user name and focuses the password field; the password is posted as
# WM_CHAR to this window only, then the login button is invoked.
function Login-As([int]$procId, [string]$user) {
    # The quick-fill button puts the user name in and makes the password field the scene's focus owner.
    # Characters are posted as WM_CHAR with repeat count 1 to this window only; the window is not
    # brought to the foreground, so real mouse or keyboard input on the machine cannot land in it.
    Invoke-El (Wait-In $procId "Button" "*($user)" 30)
    Start-Sleep -Milliseconds 500
    $hwnd = (Get-Process -Id $procId).MainWindowHandle
    [ToeicType]::Text($hwnd, (Seed-Password))
    Start-Sleep -Milliseconds 500
    Invoke-El (Find-In $procId "Button" "*Ngay")
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
    Read-Texts $procId | Where-Object { $_ -match $pattern } | Where-Object { $_ -notmatch "\.\.\.$" } | Select-Object -Unique
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

# Owned modal dialogs appear either as a second top-level window or as a Window child of the main window.
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

function Select-Row($row) { $row.GetCurrentPattern([System.Windows.Automation.SelectionItemPattern]::Pattern).Select() }

# Server-side autosave log of one attempt: revision, epoch, UTC time and the saved map.
function Autosave-Log([string]$schema, [string]$attempt) {
    Sql "select answer_revision || ' epoch=' || writer_epoch || ' at=' || to_char(decision_at at time zone 'UTC','HH24:MI:SS.MS') || ' ' || answers_json from $schema.exam_autosave_requests where attempt_id = '$attempt' order by answer_revision"
}
