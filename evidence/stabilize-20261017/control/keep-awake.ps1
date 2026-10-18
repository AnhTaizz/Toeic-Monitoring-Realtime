# Holds ES_CONTINUOUS|ES_SYSTEM_REQUIRED for this process only; no power setting is changed.
Add-Type -Namespace W -Name P -MemberDefinition '[DllImport("kernel32.dll")] public static extern uint SetThreadExecutionState(uint f);'
[W.P]::SetThreadExecutionState([uint32]2147483649) | Out-Null
$stop = Join-Path $PSScriptRoot 'keep-awake.stop'
while (-not (Test-Path $stop)) { Start-Sleep -Seconds 30 }
[W.P]::SetThreadExecutionState([uint32]2147483648) | Out-Null
