# Invoked only by the standalone Java probe with a fixed mode. No device writes or file access.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
try {
    $principal = [Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
    $privilege = if ($principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { 'ELEVATED' } else { 'STANDARD' }
    [Console]::WriteLine(('META|{0}|{1}' -f $privilege, $PSVersionTable.PSVersion.ToString()))
    if ($probeMode -eq 'MSFT_DISK') {
        $rows = @(Get-CimInstance -Namespace root/Microsoft/Windows/Storage -Query 'SELECT Number,BusType FROM MSFT_Disk' -OperationTimeoutSec 5)
    } elseif ($probeMode -eq 'WIN32_DISKDRIVE') {
        $rows = @(Get-CimInstance -Query 'SELECT Index,InterfaceType FROM Win32_DiskDrive' -OperationTimeoutSec 5)
    } else { throw [ArgumentException]::new() }
    if ($rows.Count -gt 128) { [Console]::WriteLine('ERROR|ROW_LIMIT'); exit 3 }
    foreach ($row in $rows) {
        $classification = 'UNKNOWN'
        if ($probeMode -eq 'MSFT_DISK') {
            if ($null -eq $row.Number) { [Console]::WriteLine('ERROR|MISSING_METADATA'); exit 3 }
            $number = [uint32]$row.Number
            if ($null -ne $row.BusType) {
                if ([int]$row.BusType -eq 7) { $classification = 'USB' }
                # Unknown/SCSI are deliberately ambiguous; unrecognized future enums stay UNKNOWN.
                elseif ([int]$row.BusType -in @(2,3,4,5,6,8,9,10,11,12,13,14,15,16,17)) { $classification = 'NON_USB' }
            }
        } else {
            if ($null -eq $row.Index) { [Console]::WriteLine('ERROR|MISSING_METADATA'); exit 3 }
            $number = [uint32]$row.Index
            if ($row.InterfaceType -eq 'USB') { $classification = 'USB' }
            elseif ($row.InterfaceType -in @('IDE','HDC','1394')) { $classification = 'NON_USB' }
        }
        # Numeric disk index is private to this child/parent pipe, never printed in the public JSONL.
        [Console]::WriteLine(('DISK|{0}|{1}' -f $number, $classification))
    }
    [Console]::WriteLine(('END|{0}' -f $rows.Count))
    exit 0
} catch {
    $code = 'QUERY_FAILED'
    if ($_.Exception -is [Microsoft.Management.Infrastructure.CimException]) {
        switch ($_.Exception.StatusCode.ToString()) {
            'AccessDenied' { $code = 'ACCESS_DENIED' }
            'InvalidClass' { $code = 'UNSUPPORTED_PROVIDER' }
            'InvalidNamespace' { $code = 'UNSUPPORTED_PROVIDER' }
        }
    } elseif ($_.Exception -is [System.Management.Automation.CommandNotFoundException]) {
        $code = 'UNSUPPORTED_PROVIDER'
    }
    # Never output exception text, object dump, identity, serial, path or stack trace.
    [Console]::WriteLine(('ERROR|{0}' -f $code))
    exit 3
}
