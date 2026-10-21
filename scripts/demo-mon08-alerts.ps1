param([ValidateRange(0,120)][int]$ViewSeconds=20,[string]$JavaHome=$env:JAVA_HOME,[string]$PostgresBin='C:\Program Files\PostgreSQL\18\bin',[string]$OutputDirectory,[switch]$Integrated,[ValidateSet('Mock','Real')][string]$Mode='Mock',[switch]$ReadUsbStorage)
$ErrorActionPreference='Stop'
$options=@{Mode=$Mode;Timeline=$true;AlertRules=$true;ViewSeconds=$ViewSeconds;JavaHome=$JavaHome;PostgresBin=$PostgresBin;Integrated=$Integrated;ReadUsbStorage=$ReadUsbStorage}
if($OutputDirectory){$options.OutputDirectory=$OutputDirectory}
& (Join-Path $PSScriptRoot 'demo-usb-integrated.ps1') @options
