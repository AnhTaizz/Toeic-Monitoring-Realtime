param([Parameter(Mandatory=$true)][string]$ServerUrl,[Parameter(Mandatory=$true)][string]$ClientJar,[string]$DeviceLabel='UNRECORDED')
$ErrorActionPreference='Stop'
$uri=$null
if(-not[Uri]::TryCreate($ServerUrl,[UriKind]::Absolute,[ref]$uri) -or $uri.Scheme -notin @('http','https') -or -not $uri.Host -or $uri.UserInfo -or $uri.Query -or $uri.Fragment -or $uri.AbsolutePath -ne '/' -or $uri.Port -le 0){throw 'ServerUrl phải origin HTTP/HTTPS không credentials/query/path.'}
if(-not(Test-Path -LiteralPath $ClientJar -PathType Leaf)){throw 'Không có ClientJar.'}
$request=[Net.HttpWebRequest]::Create($ServerUrl.TrimEnd('/')+'/api/v1/auth/me');$request.Method='GET';$request.Timeout=5000;$request.AllowAutoRedirect=$false
$status=$null
try{$response=$request.GetResponse();$status=[int]$response.StatusCode;$response.Close()}catch [Net.WebException]{if($_.Exception.Response){$status=[int]$_.Exception.Response.StatusCode;$_.Exception.Response.Close()}else{throw 'Không kết nối được listener server trong5s.'}}
if($status -ne 401){throw "Expected unauthenticated auth/me401; actual HTTP$status. Không kết luận backend healthy."}
[ordered]@{deviceLabel=$DeviceLabel;utc=[DateTime]::UtcNow.ToString('o');clientJarSha256=(Get-FileHash -LiteralPath $ClientJar -Algorithm SHA256).Hash.ToLowerInvariant();httpStatus=$status;result='LISTENER_AUTH_GATE_REACHABLE';databaseHealth='NOT VERIFIED';twoMachineLan='NOT PROVEN';firewall='UNCHANGED';credentials='NOT SENT'} | ConvertTo-Json
