param([ValidateRange(0,120)][int]$ViewSeconds=20,[string]$JavaHome=$env:JAVA_HOME,[string]$PostgresBin='C:\Program Files\PostgreSQL\18\bin',[string]$OutputDirectory)
$ErrorActionPreference='Stop'
$options=@{Mode='Mock';Timeline=$true;AlertRules=$true;ViewSeconds=$ViewSeconds;JavaHome=$JavaHome;PostgresBin=$PostgresBin}
if($OutputDirectory){$options.OutputDirectory=$OutputDirectory}
& (Join-Path $PSScriptRoot 'demo-usb-integrated.ps1') @options
