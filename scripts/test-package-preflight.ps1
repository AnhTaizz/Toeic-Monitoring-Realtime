param([string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
 foreach($name in @('package-windows.ps1','start-packaged-client.ps1','smoke-package-windows.ps1')) {
  $tokens=$null;$errors=$null;[void][System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot $name),[ref]$tokens,[ref]$errors)
  if($errors.Count){throw "PARSE FAIL $name"};Write-Output "PASS parser $name"
 }
 function Rejected([string]$Label,[scriptblock]$Action) {try{& $Action;throw 'UNEXPECTED_SUCCESS'}catch{if($_.Exception.Message -eq 'UNEXPECTED_SUCCESS'){throw};Write-Output "PASS rejection $Label"}}
 Rejected 'REAL invalid ServerUrl credential/query' {& ./scripts/package-windows.ps1 -JavaHome $JavaHome -ServerUrl 'http://user:password@example.invalid/?token=MOCK' -DryRun}
 Rejected 'REAL nonexistent jpackage path' {& ./scripts/package-windows.ps1 -JavaHome ([IO.Path]::Combine($env:TEMP,'nonexistent-jdk-'+[Guid]::NewGuid().ToString('N'))) -DryRun}
 $owned=Join-Path $root ('jpackage-out/package-preflight-TEST-'+[Guid]::NewGuid().ToString('N'));[void](New-Item -ItemType Directory -Path (Join-Path $owned 'fake-jdk/bin'))
 [IO.File]::WriteAllBytes((Join-Path $owned 'fake-jdk/bin/jpackage.exe'),[byte[]]@())
 [IO.File]::WriteAllText((Join-Path $owned 'fake-jdk/release'),"JAVA_VERSION=`"21.0.8`"`nOS_ARCH=`"aarch64`"")
 Rejected 'MOCK wrong JDK architecture, fake exe never executed' {& ./scripts/package-windows.ps1 -JavaHome (Join-Path $owned 'fake-jdk') -DryRun}
 $missing=Join-Path $owned 'missing-runtime';[void](New-Item -ItemType Directory -Path $missing)
 [ordered]@{schema='toeic-windows-package-v1';files=@([ordered]@{path='ToeicMonitor/runtime/bin/server/jvm.dll';sha256=('0'*64)})} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $missing 'package-manifest.json') -Encoding UTF8
 Rejected 'MOCK package missing bundled runtime' {& ./scripts/smoke-package-windows.ps1 -PackageDirectory $missing -OutputDirectory (Join-Path $owned 'missing-runtime-output')}
 if(-not(Get-Command candle.exe -ErrorAction SilentlyContinue) -or -not(Get-Command light.exe -ErrorAction SilentlyContinue)) {
  Rejected 'REAL WiX unavailable' {& ./scripts/package-windows.ps1 -JavaHome $JavaHome -Mode Installer -DryRun}
 }else{Write-Output 'NOT RUN missing-WiX negative: toolchain exists'}
 try {& ./scripts/package-windows.ps1 -JavaHome $JavaHome -DryRun;Write-Output 'Prerequisite gate PASS only, no package/IT01 claim'}catch{Write-Output ('BLOCKED package prerequisites: '+$_.Exception.Message)}
 Write-Output 'Parser and negative checks complete; fixture data MOCK, no installer executed.'
}finally{Pop-Location}
