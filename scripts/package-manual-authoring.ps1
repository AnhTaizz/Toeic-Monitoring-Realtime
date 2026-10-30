param(
    [Parameter(Mandatory=$true)][string]$BaseBundle,
    [Parameter(Mandatory=$true)][string]$OutputDirectory
)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$base=(Resolve-Path -LiteralPath $BaseBundle).Path
$destination=[IO.Path]::GetFullPath($OutputDirectory)
if(Test-Path -LiteralPath $destination){throw 'Output already exists; choose a new directory.'}
$client=Join-Path $repo 'client/target/client-0.1.0-SNAPSHOT-all.jar'
$server=Join-Path $repo 'server/target/server-0.1.0-SNAPSHOT.jar'
foreach($path in @($client,$server)){if(-not(Test-Path -LiteralPath $path)){throw 'Build client and server with Maven package first.'}}
foreach($name in @('ToeicMonitor.exe','app','runtime','server-runtime','start-release.ps1','Start-Server.bat','server.env.example')){
    if(-not(Test-Path -LiteralPath (Join-Path $base $name))){throw ('Base portable bundle missing '+$name)}
}
New-Item -ItemType Directory -Path $destination|Out-Null
foreach($name in @('ToeicMonitor.exe','app','runtime','server-runtime','start-release.ps1','Start-Server.bat','server.env.example')){
    Copy-Item -LiteralPath (Join-Path $base $name) -Destination $destination -Recurse
}
Copy-Item -LiteralPath $client -Destination (Join-Path $destination 'app/client-0.1.0-SNAPSHOT-all.jar') -Force
Copy-Item -LiteralPath $server -Destination (Join-Path $destination 'server.jar')
Copy-Item -LiteralPath (Join-Path $repo 'docs/manual-authoring-fix.md') -Destination (Join-Path $destination 'HUONG-DAN.md')
Copy-Item -LiteralPath (Join-Path $repo 'server/Dockerfile') -Destination (Join-Path $destination 'Dockerfile.server')
$dockerfile=Join-Path $destination 'Dockerfile.server'
[IO.File]::WriteAllText($dockerfile,([IO.File]::ReadAllText($dockerfile).Replace('COPY target/server-0.1.0-SNAPSHOT.jar','COPY server.jar')),[Text.UTF8Encoding]::new($false))
Copy-Item -LiteralPath (Join-Path $repo 'docker-compose.server.yml') -Destination $destination
$metadata=[ordered]@{
    sourceCommit=(& git -C $repo rev-parse HEAD).Trim()
    branch=(& git -C $repo branch --show-current).Trim()
    uncommittedChanges=[bool](& git -C $repo status --porcelain --untracked-files=normal)
    feature='manual-server-save-pending-images'
    launcher='Reused from BaseBundle; client and server JARs rebuilt from sourceCommit'
    clientJarSha256=(Get-FileHash -LiteralPath $client).Hash
    serverJarSha256=(Get-FileHash -LiteralPath $server).Hash
    containsCredentials=$false
}
$metadata|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $destination 'build-info.json') -Encoding UTF8
Write-Output ('Portable bundle: '+$destination)
