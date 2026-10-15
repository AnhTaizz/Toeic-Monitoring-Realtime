param([string]$ServerUrl,[string]$ConfigFile)
$ErrorActionPreference='Stop'
if($ConfigFile -and -not[IO.Path]::IsPathRooted($ConfigFile)){$ConfigFile=Join-Path (Get-Location).Path $ConfigFile}
$source='GUI/default'
if(-not $ConfigFile){$ConfigFile=Join-Path $env:LOCALAPPDATA 'TOEIC Monitor/server-url.txt'}
if($ServerUrl){$source='argument'}elseif($env:TOEIC_SERVER_URL){$ServerUrl=$env:TOEIC_SERVER_URL;$source='environment'}elseif(Test-Path -LiteralPath $ConfigFile){$ServerUrl=[IO.File]::ReadAllText($ConfigFile).Trim();$source='user file'}
if($ServerUrl){
 $uri=$null
 if(-not[Uri]::TryCreate($ServerUrl,[UriKind]::Absolute,[ref]$uri) -or $uri.Scheme -notin @('http','https') -or -not $uri.Host -or $uri.UserInfo -or $uri.Query -or $uri.Fragment -or $uri.AbsolutePath -ne '/' -or $uri.Port -le 0){throw 'Server URL không hợp lệ.'}
}
$image=Join-Path $PSScriptRoot 'ToeicMonitor/ToeicMonitor.exe'
if(-not(Test-Path -LiteralPath $image)){throw 'Không tìm thấy app-image cạnh launcher script.'}
$start=[Diagnostics.ProcessStartInfo]::new();$start.FileName=$image;$start.UseShellExecute=$false
if($ServerUrl){$start.EnvironmentVariables['TOEIC_SERVER_URL']=$ServerUrl}
Write-Output "Server configuration source=$source; credential chỉ nhập trong giao diện."
$process=[Diagnostics.Process]::Start($start);$process.WaitForExit();$code=$process.ExitCode;$process.Dispose();exit $code
