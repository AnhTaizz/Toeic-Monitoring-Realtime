param([ValidateSet('Mock','Real')][string]$Mode='Mock',[switch]$ReadUsbStorage,
    [ValidateRange(0,120)][int]$ViewSeconds=20,[string]$JavaHome=$env:JAVA_HOME,
    [string]$PostgresBin='C:\Program Files\PostgreSQL\18\bin',[string]$OutputDirectory)
$ErrorActionPreference='Stop'
# Reuse the owned TEST cluster/seeder/API/teardown runner, not another provisioning system.
$options=@{Mode=$Mode;ReadUsbStorage=$ReadUsbStorage;Timeline=$true;ViewSeconds=$ViewSeconds;JavaHome=$JavaHome;PostgresBin=$PostgresBin}
if($OutputDirectory){$options.OutputDirectory=$OutputDirectory}
& (Join-Path $PSScriptRoot 'demo-usb-integrated.ps1') @options
