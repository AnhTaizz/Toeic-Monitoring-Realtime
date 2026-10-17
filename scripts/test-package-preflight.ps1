param([string]$JavaHome=$env:JAVA_HOME,[string]$WixHome,[string]$ManifestTool)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
foreach($name in @('package-windows.ps1','package-t3b1.ps1','start-packaged-client.ps1','smoke-package-windows.ps1')){
 $tokens=$null;$errors=$null;[void][System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot $name),[ref]$tokens,[ref]$errors)
 if($errors.Count){throw "PARSE FAIL $name"};Write-Output "PASS parser $name"
}
function Rejected([string]$Label,[scriptblock]$Action){try{& $Action;throw 'UNEXPECTED_SUCCESS'}catch{if($_.Exception.Message -eq 'UNEXPECTED_SUCCESS'){throw};Write-Output "PASS rejection $Label"}}
$owned=Join-Path $root ('jpackage-out/preflight-TEST-'+[Guid]::NewGuid().ToString('N'));[void](New-Item -ItemType Directory -Path $owned)
Rejected 'REAL invalid runtime ServerUrl' {& "$PSScriptRoot/start-packaged-client.ps1" -ServerUrl 'http://user:password@example.invalid/?token=MOCK' -ValidateOnly}
Rejected 'REAL missing JDK' {& "$PSScriptRoot/package-windows.ps1" -JavaHome (Join-Path $owned 'missing-jdk') -WixHome $owned -ManifestTool (Join-Path $owned 'missing-mt.exe') -PreflightOnly}
$fake=Join-Path $owned 'wrong-arch';[void](New-Item -ItemType Directory -Path $fake)
[IO.File]::WriteAllText((Join-Path $fake 'release'),"JAVA_VERSION=`"21.0.8`"`nOS_ARCH=`"aarch64`"")
Rejected 'MOCK wrong JDK architecture, no exe executed' {& "$PSScriptRoot/package-windows.ps1" -JavaHome $fake -WixHome $owned -ManifestTool (Join-Path $owned 'missing-mt.exe') -PreflightOnly}
Rejected 'MOCK image missing runtime, no exe executed' {& "$PSScriptRoot/smoke-package-windows.ps1" -ImageDirectory $owned}
$missing=Join-Path $owned 'missing-config.txt'
& "$PSScriptRoot/start-packaged-client.ps1" -ServerUrl 'http://127.0.0.1:8080' -ConfigFile $missing -ValidateOnly
$bad=Join-Path $owned 'oversized-server-url.txt';[IO.File]::WriteAllText($bad,('x'*4097))
$previous=$env:TOEIC_SERVER_URL
try{$env:TOEIC_SERVER_URL=$null;Rejected 'MOCK oversized config' {& "$PSScriptRoot/start-packaged-client.ps1" -ConfigFile $bad -ValidateOnly}}finally{$env:TOEIC_SERVER_URL=$previous}
if(-not $WixHome){$WixHome=Join-Path $owned 'missing-wix'}
if(-not $ManifestTool){$ManifestTool=Join-Path $owned 'missing-mt.exe'}
try{& "$PSScriptRoot/package-windows.ps1" -JavaHome $JavaHome -WixHome $WixHome -ManifestTool $ManifestTool -PreflightOnly}catch{Write-Output ('BLOCKED packaging prerequisite: '+$_.Exception.Message)}
Write-Output 'Preflight assertions complete; no installer executed, no system configuration changed.'
