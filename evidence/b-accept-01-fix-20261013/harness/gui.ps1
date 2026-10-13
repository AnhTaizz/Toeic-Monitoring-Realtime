# ASCII-only helper for agent-driven UI Automation of the TOEIC JavaFX client (Windows PowerShell 5.1).
Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type -AssemblyName System.Drawing
if (-not ([System.Management.Automation.PSTypeName]'ToeicWin').Type) {
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class ToeicWin {
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr hwnd, IntPtr hdc, uint flags);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hwnd, out RECT rect);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
}
"@
}
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$script:Repo = "E:\Project\Toeic-Monitoring-Realtime-pr17"
$script:Scratch = Split-Path -Parent $MyInvocation.MyCommand.Path
$script:StateFile = Join-Path $script:Scratch "gui-state.json"
$AE = [System.Windows.Automation.AutomationElement]
$CT = [System.Windows.Automation.ControlType]
$TS = [System.Windows.Automation.TreeScope]

function Get-State { if (Test-Path $script:StateFile) { Get-Content -Raw $script:StateFile | ConvertFrom-Json } else { $null } }
function Save-State($state) { $state | ConvertTo-Json | Out-File -Encoding ascii $script:StateFile }

function Import-DbEnv {
    Get-Content "E:\Project\Toeic-Monitoring-Realtime\.env" | Where-Object { $_ -match '^\s*[A-Za-z_][A-Za-z0-9_]*=' } |
        ForEach-Object { $k, $v = $_ -split '=', 2; Set-Item -Path "env:$($k.Trim())" -Value $v.Trim() }
}

function Start-ToeicServer([string]$schema, [int]$port) {
    Import-DbEnv
    $dbHost = if ($env:DB_HOST) { $env:DB_HOST } else { "127.0.0.1" }
    $url = "jdbc:postgresql://${dbHost}:$($env:DB_PORT)/$($env:DB_NAME)?currentSchema=$schema"
    $log = Join-Path $script:Repo "client\target\t2b-gui-server.log"
    $arguments = @("-Duser.timezone=UTC", "-jar", "server\target\server-0.1.0-SNAPSHOT.jar", "--server.port=$port",
        "--server.address=127.0.0.1", "--spring.datasource.url=$url", "--spring.flyway.default-schema=$schema",
        "--spring.flyway.schemas=$schema", "--logging.level.root=WARN", "--spring.main.banner-mode=off")
    $p = Start-Process java -ArgumentList $arguments -WorkingDirectory $script:Repo -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput $log -RedirectStandardError "$log.err"
    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        try { Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$port/api/v1/auth/me" -TimeoutSec 2 | Out-Null }
        catch { if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 401) { return $p.Id } }
        if ($p.HasExited) { throw "server exited early; see $log" }
        Start-Sleep -Milliseconds 500
    }
    throw "server not ready"
}

function Start-ToeicClient([int]$port, [string[]]$extra) {
    $arguments = @("-Dtoeic.server.url=http://127.0.0.1:$port") + $extra + @("-jar", "client\target\client-0.1.0-SNAPSHOT-all.jar")
    $p = Start-Process java -ArgumentList $arguments -WorkingDirectory $script:Repo -PassThru
    $deadline = (Get-Date).AddSeconds(40)
    while ((Get-Date) -lt $deadline) {
        if (@(Get-AppWindows $p.Id).Count -gt 0) { return $p.Id }
        Start-Sleep -Milliseconds 300
    }
    throw "client window not found"
}

function Get-AppWindows([int]$procId) {
    $cond = New-Object System.Windows.Automation.PropertyCondition($AE::ProcessIdProperty, $procId)
    $AE::RootElement.FindAll($TS::Children, $cond)
}

function Get-MainWindow([int]$procId) { @(Get-AppWindows $procId)[0] }

# All descendants of a window with the given control type name ("Button", "Text", "Edit", "ComboBox", ...).
function Get-Els($window, [string]$type) {
    $cond = New-Object System.Windows.Automation.PropertyCondition($AE::ControlTypeProperty, $CT::$type)
    $window.FindAll($TS::Descendants, $cond)
}

function Find-El($window, [string]$type, [string]$like) {
    foreach ($el in (Get-Els $window $type)) { if ($el.Current.Name -like $like) { return $el } }
    return $null
}

function Wait-El($window, [string]$type, [string]$like, [int]$seconds = 15) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        $el = Find-El $window $type $like
        if ($el) { return $el }
        Start-Sleep -Milliseconds 250
    }
    throw "element not found: $type like '$like'"
}

function Invoke-El($el) { $el.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern).Invoke() }

# Invoke without waiting for the handler to return (handlers that open a modal dialog block Invoke()).
function Invoke-ElAsync($el) {
    $ps = [PowerShell]::Create()
    [void]$ps.AddScript({ param($e) $e.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern).Invoke() }).AddArgument($el)
    [void]$ps.BeginInvoke()
}

function Set-Value($el, [string]$value) { $el.GetCurrentPattern([System.Windows.Automation.ValuePattern]::Pattern).SetValue($value) }

function Show-Els($window, [string]$type) {
    foreach ($el in (Get-Els $window $type)) {
        $n = $el.Current.Name
        if ($n) { "{0} [{1}] {2}" -f $type, $(if ($el.Current.IsEnabled) { "on " } else { "off" }), ($n -replace "\s+", " ") }
    }
}

function Save-Shot([IntPtr]$hwnd, [string]$path) {
    $r = New-Object ToeicWin+RECT
    [void][ToeicWin]::GetWindowRect($hwnd, [ref]$r)
    $bmp = New-Object System.Drawing.Bitmap(($r.Right - $r.Left), ($r.Bottom - $r.Top))
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $hdc = $g.GetHdc()
    [void][ToeicWin]::PrintWindow($hwnd, $hdc, 2)
    $g.ReleaseHdc($hdc); $g.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
}

function Save-WindowShot($window, [string]$name) {
    $dir = Join-Path $script:Scratch "shots"
    New-Item -ItemType Directory -Force $dir | Out-Null
    $path = Join-Path $dir $name
    Save-Shot ([IntPtr]$window.Current.NativeWindowHandle) $path
    $path
}

# The window element goes stale when JavaFX swaps the Scene, so re-read the window on every poll.
function Wait-In([int]$procId, [string]$type, [string]$like, [int]$seconds = 20) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        foreach ($window in @(Get-AppWindows $procId)) {
            $el = Find-El $window $type $like
            if ($el) { return $el }
        }
        Start-Sleep -Milliseconds 250
    }
    throw "element not found in process ${procId}: $type like '$like'"
}

function Find-In([int]$procId, [string]$type, [string]$like) {
    foreach ($window in @(Get-AppWindows $procId)) {
        $el = Find-El $window $type $like
        if ($el) { return $el }
    }
    return $null
}

# Distinct visible texts of every window of the process, for reading labels.
function Read-Texts([int]$procId) {
    foreach ($window in @(Get-AppWindows $procId)) { Show-Els $window "Text" | Select-Object -Unique }
}

if (-not ([System.Management.Automation.PSTypeName]'ToeicInput').Type) {
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class ToeicInput {
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr hWnd, uint msg, IntPtr wParam, IntPtr lParam);
    [DllImport("user32.dll")] public static extern bool ScreenToClient(IntPtr hWnd, ref POINT point);
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
    public static void Click(IntPtr hwnd, int screenX, int screenY) {
        POINT p = new POINT(); p.X = screenX; p.Y = screenY; ScreenToClient(hwnd, ref p);
        IntPtr l = (IntPtr)((p.Y << 16) | (p.X & 0xFFFF));
        PostMessage(hwnd, 0x0200, IntPtr.Zero, l);      // WM_MOUSEMOVE
        PostMessage(hwnd, 0x0201, (IntPtr)1, l);        // WM_LBUTTONDOWN
        PostMessage(hwnd, 0x0202, IntPtr.Zero, l);      // WM_LBUTTONUP
    }
    public static void Key(IntPtr hwnd, int vk) {
        PostMessage(hwnd, 0x0100, (IntPtr)vk, IntPtr.Zero);            // WM_KEYDOWN
        PostMessage(hwnd, 0x0101, (IntPtr)vk, (IntPtr)(-1073741823));  // WM_KEYUP
    }
}
"@
}

# Mouse click posted to the window: unlike InvokePattern it returns at once, so handlers that open a
# modal dialog do not block later UI Automation calls. Sends nothing to other windows.
function Click-El($window, $el) {
    $r = $el.Current.BoundingRectangle
    [ToeicInput]::Click([IntPtr]$window.Current.NativeWindowHandle, [int]($r.X + $r.Width / 2), [int]($r.Y + $r.Height / 2))
}

function Find-Window([int]$procId, [string]$like) {
    foreach ($window in @(Get-AppWindows $procId)) { if ($window.Current.Name -like $like) { return $window } }
    return $null
}

function Wait-Window([int]$procId, [string]$like, [int]$seconds = 20) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        $w = Find-Window $procId $like
        if ($w) { return $w }
        Start-Sleep -Milliseconds 250
    }
    throw "window not found: '$like'"
}
