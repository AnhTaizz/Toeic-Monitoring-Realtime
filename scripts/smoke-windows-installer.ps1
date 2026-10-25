# Owns one temporary per-user MSI installation; always restores ACL and uninstalls it.
param(
    [Parameter(Mandatory=$true)][string]$Installer,
    [string]$JavaHome=$env:JAVA_HOME,
    [switch]$FullSuite,
    [switch]$AllowDirtyArtifact
)
$ErrorActionPreference='Stop';$previous=@{};$installed=$false;$acl=$null;$testResult=1
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    if (-not $JavaHome) { throw 'Pass -JavaHome <JDK 21> for the test server, not the installed client.' }
    $identity=[Security.Principal.WindowsIdentity]::GetCurrent()
    if (([Security.Principal.WindowsPrincipal]::new($identity)).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Run this smoke as an ordinary, non-elevated user so the write-denied test is meaningful.'
    }
    $Installer=(Resolve-Path -LiteralPath $Installer).Path
    if ([IO.Path]::GetExtension($Installer) -ne '.msi') { throw 'Expected an MSI from package-windows.ps1.' }
    $metadata=Get-Content -LiteralPath (Join-Path (Split-Path $Installer) 'build-info.json') -Raw | ConvertFrom-Json
    if ((Get-FileHash -LiteralPath $Installer).Hash.ToLowerInvariant() -ne $metadata.installerSha256) { throw 'Installer checksum mismatch.' }
    if ($metadata.dirty -and -not $AllowDirtyArtifact) { throw 'Development artifact; pass -AllowDirtyArtifact explicitly or rebuild from committed source.' }
    # Refuse replacing an existing user installation. This test never upgrades/uninstalls somebody else's app.
    foreach($registry in @('HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall','HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall')) {
        if(Get-ChildItem -LiteralPath $registry -ErrorAction SilentlyContinue | Get-ItemProperty | Where-Object {$_.DisplayName -eq 'ToeicMonitor'}) { throw 'ToeicMonitor already installed. Keep it; use another test user/machine.' }
    }
    $windowsInstaller=New-Object -ComObject WindowsInstaller.Installer
    $database=$windowsInstaller.OpenDatabase($Installer,0)
    $query=$database.OpenView('SELECT `Value` FROM `Property` WHERE `Property`=''ProductCode''')
    $query.Execute();$product=$query.Fetch().StringData(1);$query.Close()
    if($product -notmatch '^\{[0-9A-Fa-f-]{36}\}$'){throw 'Invalid MSI ProductCode.'}
    $run=Join-Path (Get-Location) ('target/t3-b5/install-'+[Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force $run | Out-Null
    $installRoot=[IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA ('TOEIC B5 TEST\'+[Guid]::NewGuid().ToString('N')+'\Cài đặt tiếng Việt có khoảng trắng\ToeicMonitor')))
    $allowedRoot=[IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA 'TOEIC B5 TEST'))+[IO.Path]::DirectorySeparatorChar
    if(-not $installRoot.StartsWith($allowedRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'Unsafe test install path.'}
    $p=Start-Process -FilePath "$env:SystemRoot/System32/msiexec.exe" -WindowStyle Hidden -Wait -PassThru -ArgumentList @('/i',('"'+$Installer+'"'),'/qn','/norestart',('INSTALLDIR="'+$installRoot+'"'),'/l*v',('"'+(Join-Path $run 'install.log')+'"'))
    if($p.ExitCode -notin @(0,3010)){throw "MSI install failed ($($p.ExitCode)); inspect $run/install.log"}
    $installed=$true
    if(-not(Test-Path -LiteralPath (Join-Path $installRoot 'ToeicMonitor.exe'))){throw 'MSI did not install into requested Unicode INSTALLDIR.'}
    $installedMetadata=Get-Content -LiteralPath (Join-Path $installRoot 'app/build-info.json') -Raw | ConvertFrom-Json
    if($installedMetadata.sourceCommit -ne $metadata.sourceCommit -or $installedMetadata.clientJarSha256 -ne (Get-FileHash -LiteralPath (Join-Path $installRoot 'app/client-0.1.0-SNAPSHOT-all.jar')).Hash.ToLowerInvariant()){throw 'Installed source/JAR mismatch.'}
    $before=@{};Get-ChildItem -LiteralPath $installRoot -Recurse -File | ForEach-Object {$before[$_.FullName]=(Get-FileHash -LiteralPath $_.FullName).Hash}
    $acl=Get-Acl -LiteralPath $installRoot
    $denied=Get-Acl -LiteralPath $installRoot
    $rule=[Security.AccessControl.FileSystemAccessRule]::new($identity.User,'Write','ContainerInherit,ObjectInherit','None','Deny')
    [void]$denied.AddAccessRule($rule);Set-Acl -LiteralPath $installRoot -AclObject $denied
    # First launch without any TEST agent, system Java or JAVA_HOME, and close through Windows.
    $plain=[Diagnostics.ProcessStartInfo]::new()
    $plain.FileName=Join-Path $installRoot 'ToeicMonitor.exe';$plain.WorkingDirectory=$installRoot
    $plain.UseShellExecute=$false
    foreach($key in @('JAVA_HOME','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')){$plain.EnvironmentVariables.Remove($key)}
    $plain.EnvironmentVariables['PATH']="$env:SystemRoot\System32;$env:SystemRoot"
    $plainProcess=[Diagnostics.Process]::Start($plain);$runtimeProcess=$null
    try {
        $limit=[DateTime]::UtcNow.AddSeconds(25)
        do {
            $plainProcess.Refresh();if($plainProcess.HasExited){throw 'Ordinary launcher exited before login window.'}
            # Windows jpackage 21 forks another ToeicMonitor.exe for the JVM. Own only this direct child.
            if(-not $runtimeProcess){
                $child=Get-CimInstance Win32_Process -Filter "ParentProcessId=$($plainProcess.Id)" | Where-Object {$_.ExecutablePath -eq $plain.FileName} | Select-Object -First 1
                if($child){$runtimeProcess=Get-Process -Id $child.ProcessId;[void]$runtimeProcess.Handle}
            }
            if($runtimeProcess){$runtimeProcess.Refresh()}
            if([DateTime]::UtcNow -gt $limit){throw 'Login window did not open in owned JVM child.'}
            Start-Sleep -Milliseconds 100
        } while(-not $runtimeProcess -or $runtimeProcess.MainWindowTitle -ne 'TOEIC Realtime Monitoring Platform')
        $jvm=@($runtimeProcess.Modules | Where-Object {$_.ModuleName -eq 'jvm.dll'})
        if($jvm.Count -ne 1 -or $jvm[0].FileName -ne (Join-Path $installRoot 'runtime/bin/server/jvm.dll')){throw 'Ordinary launcher did not load bundled JVM.'}
        if(-not $runtimeProcess.CloseMainWindow() -or -not $runtimeProcess.WaitForExit(15000) -or -not $plainProcess.WaitForExit(5000) -or $runtimeProcess.ExitCode -ne 0 -or $plainProcess.ExitCode -ne 0){throw 'Ordinary app did not close cleanly.'}
        [IO.File]::WriteAllText((Join-Path $run 'ordinary-launch.txt'),"agent=NONE`r`nJAVA_HOME=REMOVED`r`nPATH=Windows only`r`njvm=$($jvm[0].FileName)`r`nclose=PASS`r`n",[Text.UTF8Encoding]::new($false))
    } finally {
        # Capture children before killing the wrapper, so failures cannot orphan an owned JVM.
        if(-not $plainProcess.HasExited){Get-CimInstance Win32_Process -Filter "ParentProcessId=$($plainProcess.Id)" | Where-Object {$_.ExecutablePath -eq $plain.FileName} | ForEach-Object {Stop-Process -Id $_.ProcessId -ErrorAction SilentlyContinue};$plainProcess.Kill();$plainProcess.WaitForExit()}
        if($runtimeProcess){$runtimeProcess.Dispose()};$plainProcess.Dispose()
    }
    foreach($key in @('JAVA_HOME','PATH','JAVA_TOOL_OPTIONS','TOEIC_PG_INTEGRATION','TOEIC_PROCTOR_GUI','TOEIC_INSTALLED_CLIENT','DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','TOEIC_SEED_PASSWORD', 'TOEIC_SEED_DEMO_ACCOUNTS')){$previous[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
    if(Test-Path -LiteralPath '.env'){foreach($line in Get-Content -LiteralPath '.env' -Encoding UTF8){if($line -match '^\s*(DB_HOST|DB_PORT|DB_NAME|DB_USER|DB_PASSWORD|TOEIC_SEED_PASSWORD|TOEIC_SEED_DEMO_ACCOUNTS)\s*=(.*)$'){[Environment]::SetEnvironmentVariable($Matches[1],$Matches[2].Trim().Trim('"').Trim("'"),'Process')}}}
    $defaults=@{DB_HOST='127.0.0.1';DB_PORT='5432';DB_NAME='toeic';DB_USER='toeic';TOEIC_SEED_PASSWORD='ChangeMe123!'}
    foreach($key in $defaults.Keys){if(-not[Environment]::GetEnvironmentVariable($key,'Process')){[Environment]::SetEnvironmentVariable($key,$defaults[$key],'Process')}}
    $env:JAVA_HOME=$JavaHome;$env:PATH=(Join-Path $JavaHome 'bin')+';'+$env:PATH
    $env:JAVA_TOOL_OPTIONS='-Duser.timezone=UTC';$env:TOEIC_PG_INTEGRATION='true';$env:TOEIC_PROCTOR_GUI='true';$env:TOEIC_INSTALLED_CLIENT=$installRoot
    $arguments=@('-B','-o',"-Dmaven.repo.local=$env:USERPROFILE/.m2/repository",'-pl','protocol,windows-observer,client,monitoring-spike,server','clean','package')
    if(-not $FullSuite){$arguments+=@('-Dtest=ListeningRuntimePostgresTest#installedProductionClientUsesBundledRuntimeAndRealAudioLifecycle','-Dsurefire.failIfNoSpecifiedTests=false')}
    $ErrorActionPreference='Continue'
    & mvn @arguments *> (Join-Path $run 'tests.log');$testResult=$LASTEXITCODE
    $ErrorActionPreference='Stop'
    Get-Content -LiteralPath (Join-Path $run 'tests.log') -Tail 22
    if($testResult -ne 0){throw "Installed client test failed; inspect $run/tests.log and server/target/t3-b5-installed/."}
    $after=@(Get-ChildItem -LiteralPath $installRoot -Recurse -File)
    if($after.Count -ne $before.Count){throw 'App wrote into the installation directory.'}
    foreach($file in $after){if($before[$file.FullName] -ne (Get-FileHash -LiteralPath $file.FullName).Hash){throw 'Installation files changed during runtime.'}}
    [IO.File]::WriteAllText((Join-Path $run 'result.txt'),"source=$($metadata.sourceCommit)`r`ninstallerSha256=$($metadata.installerSha256)`r`nproductCode=$product`r`nnormalUser=true`r`nreadOnlyInstall=true`r`nordinaryLaunch=PASS`r`ninstalledTests=PASS`r`n")
} finally {
    foreach($key in $previous.Keys){[Environment]::SetEnvironmentVariable($key,$previous[$key],'Process')}
    if($acl -and (Test-Path -LiteralPath $installRoot)){Set-Acl -LiteralPath $installRoot -AclObject $acl}
    if($installed){
        $p=Start-Process -FilePath "$env:SystemRoot/System32/msiexec.exe" -WindowStyle Hidden -Wait -PassThru -ArgumentList @('/x',$product,'/qn','/norestart','/l*v',('"'+(Join-Path $run 'uninstall.log')+'"'))
        if($p.ExitCode -notin @(0,3010)){throw "Uninstall failed ($($p.ExitCode)); preserve $run/uninstall.log and remove this TEST product manually."}
        if(Test-Path -LiteralPath (Join-Path $installRoot 'ToeicMonitor.exe')){throw 'Launcher remained after uninstall.'}
        Add-Content -LiteralPath (Join-Path $run 'result.txt') -Value 'uninstall=PASS'
    }
    Pop-Location
}
Write-Host "PASS local MSI lifecycle (not second-machine acceptance). Output: $run"
