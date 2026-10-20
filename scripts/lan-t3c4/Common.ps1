Set-StrictMode -Version Latest
$script:LanSourceSha='463cbadc50465c6ca2f2d4c0a0701dcb9c38e034'
$script:LanRuntimeSourceSha='834f683e50b22e214ab63bbf7d046a7d418b8242'
$script:LanClientHash='b406cdc82a71f3cc9c8d3ad62a8cfbf66d46e698682382b5adbf6addf317fde2'
$script:LanServerHash='8a83ab5929951def4bb66af827573ae033b08a590b7a124dd78cb4a5d7b50189'
function Get-LanOrigin([string]$Value) {
    $uri=$null; $address=$null
    if(-not [Uri]::TryCreate($Value,[UriKind]::Absolute,[ref]$uri) -or
       $uri.Scheme -notin @('http','https') -or $uri.UserInfo -or $uri.Query -or
       $uri.Fragment -or $uri.AbsolutePath -ne '/' -or
       -not [Net.IPAddress]::TryParse($uri.Host,[ref]$address) -or
       $address.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork) { throw 'LAN_ORIGIN_LITERAL_IPV4_REQUIRED' }
    $bytes=$address.GetAddressBytes()
    if($bytes[0] -eq 0 -or $bytes[0] -eq 127 -or $bytes[0] -ge 224 -or
       ($bytes[0] -eq 169 -and $bytes[1] -eq 254)) { throw 'LAN_ORIGIN_NOT_UNICAST_LAN' }
    return $uri
}
function Get-LanHash([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Get-LanJava([string]$JavaHome) {
    $java=Join-Path $JavaHome 'bin/java.exe'
    if(-not (Test-Path -LiteralPath $java)) { throw 'JDK21_REQUIRED' }
    $info=[Diagnostics.ProcessStartInfo]::new($java,'-version')
    $info.UseShellExecute=$false; $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
    $process=[Diagnostics.Process]::Start($info)
    try {
        $out=$process.StandardOutput.ReadToEndAsync(); $err=$process.StandardError.ReadToEndAsync()
        if(-not $process.WaitForExit(5000)) { $process.Kill(); throw 'JAVA_VERSION_TIMEOUT' }
        if($process.ExitCode -ne 0 -or ($out.Result+$err.Result) -notmatch 'version "21\.') { throw 'JDK21_REQUIRED' }
    } finally { $process.Dispose() }
    return $java
}
function Get-LanTextHash([string]$Text) {
    $sha=[Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text)))).Replace('-','').ToLowerInvariant() }
    finally { $sha.Dispose() }
}
function Write-LanJson([string]$Path,$Value) {
    $bytes=[Text.UTF8Encoding]::new($false).GetBytes(($Value | ConvertTo-Json -Depth 40)+[Environment]::NewLine)
    $stream=[IO.File]::Open($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    try { $stream.Write($bytes,0,$bytes.Length) } finally { $stream.Dispose() }
}
function Get-LanProperty($Value,[string]$Name) {
    if($null -ne $Value -and $null -ne $Value.PSObject.Properties[$Name]) { return ,($Value.$Name) }
    return $null
}
function Test-LanFile([string]$Root,[string]$Relative,[string]$ExpectedHash) {
    $prefix=[IO.Path]::GetFullPath($Root).TrimEnd('\','/')+[IO.Path]::DirectorySeparatorChar
    if([IO.Path]::IsPathRooted($Relative)) { throw 'BUNDLE_PATH_INVALID' }
    $file=[IO.Path]::GetFullPath((Join-Path $Root $Relative))
    if(-not $file.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase) -or
       -not (Test-Path -LiteralPath $file -PathType Leaf) -or
       $ExpectedHash -notmatch '^[0-9a-f]{64}$' -or (Get-LanHash $file) -cne $ExpectedHash) { throw 'BUNDLE_HASH_MISMATCH' }
}
function Get-LanBundle([string]$Root) {
    $path=Join-Path $Root 'artifact-manifest.json'
    $m=Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
    if($m.schema -ne 't3c4-physical-bundle-v1' -or $m.sourceSha -cne $script:LanSourceSha -or
       $m.runtimeSourceSha -cne $script:LanRuntimeSourceSha -or
       $m.clientJarSha256 -cne $script:LanClientHash -or $m.serverJarSha256 -cne $script:LanServerHash) { throw 'TASK1_ARTIFACT_REQUIRED' }
    foreach($entry in $m.files) { Test-LanFile $Root $entry.path $entry.sha256 }
    Test-LanFile $Root 'client/target/client-0.1.0-SNAPSHOT-all.jar' $script:LanClientHash
    Test-LanFile $Root 'server/target/server-0.1.0-SNAPSHOT.jar' $script:LanServerHash
    return $m
}
function Write-LanPinnedCommon([string]$Path) {
    $pins=@{LanSourceSha=$script:LanSourceSha;LanRuntimeSourceSha=$script:LanRuntimeSourceSha;
        LanClientHash=$script:LanClientHash;LanServerHash=$script:LanServerHash}
    $lines=[IO.File]::ReadAllLines($Path)
    foreach($name in $pins.Keys) {
        $prefix='$script:'+$name+'='
        $matches=0
        for($i=0;$i -lt $lines.Length;$i++) {
            if($lines[$i].StartsWith($prefix,[StringComparison]::Ordinal)) {
                $lines[$i]=$prefix+"'"+$pins[$name]+"'"; $matches++
            }
        }
        if($matches -ne 1) { throw 'EXPORTED_PIN_LAYOUT_INVALID' }
    }
    [IO.File]::WriteAllLines($Path,$lines,[Text.UTF8Encoding]::new($false))
}
function Invoke-LanHttp([Uri]$Origin,[string]$Path,[string]$Method='GET',$Body=$null,[string]$Token=$null) {
    if(-not $Path.StartsWith('/api/v1/')) { throw 'HTTP_PATH_INVALID' }
    $request=[Net.HttpWebRequest]::Create($Origin.GetLeftPart([UriPartial]::Authority)+$Path)
    $request.Method=$Method; $request.Timeout=5000; $request.ReadWriteTimeout=5000
    $request.AllowAutoRedirect=$false; $request.Proxy=$null
    if($Token) { $request.Headers['Authorization']='Bearer '+$Token }
    if($null -ne $Body) {
        $bytes=[Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 30 -Compress))
        $request.ContentType='application/json; charset=utf-8'; $request.ContentLength=$bytes.Length
        try { $stream=$request.GetRequestStream(); try { $stream.Write($bytes,0,$bytes.Length) } finally { $stream.Dispose() } }
        catch { return [pscustomobject]@{status=0;data=$null} }
    }
    $response=$null
    try {
        try { $response=$request.GetResponse() }
        catch [Net.WebException] {
            if($_.Exception.Response) { $response=$_.Exception.Response } else { return [pscustomobject]@{status=0;data=$null} }
        }
        $status=[int]$response.StatusCode; $data=$null
        # Error bodies and redirects can contain private details; never reflect or follow them.
        if($status -ge 200 -and $status -lt 300) {
            $reader=[IO.StreamReader]::new($response.GetResponseStream(),[Text.Encoding]::UTF8)
            try { $text=$reader.ReadToEnd() } finally { $reader.Dispose() }
            if($text.Length -gt 8388608) { throw 'HTTP_RESPONSE_LIMIT' }
            try { $data=$text | ConvertFrom-Json } catch { throw 'HTTP_JSON_INVALID' }
        }
        return [pscustomobject]@{status=$status;data=$data}
    } finally { if($null -ne $response) { $response.Dispose() } }
}
function Get-LanLogin([Uri]$Origin,[Management.Automation.PSCredential]$Credential) {
    $body=@{requestId=[Guid]::NewGuid().ToString();username=$Credential.UserName;password=$Credential.GetNetworkCredential().Password}
    try { $reply=Invoke-LanHttp $Origin '/api/v1/auth/login' 'POST' $body }
    finally { $body.Clear() }
    if($reply.status -ne 200 -or -not (Get-LanProperty $reply.data 'token')) { throw 'TEST_LOGIN_FAILED_NO_BODY_LOGGED' }
    return $reply.data
}
function Get-LanCanonicalHash($Value) {
    # Only JSON-compatible values; ordinal key order avoids answer-map insertion order affecting the digest.
    function Canonical($v) {
        if($null -eq $v) { return 'null' }
        if($v -is [string] -or $v -is [ValueType]) { return ConvertTo-Json -InputObject $v -Compress }
        if($v -is [Collections.IDictionary]) {
            $keys=[string[]]@($v.Keys); [Array]::Sort($keys,[StringComparer]::Ordinal)
            return '{'+(($keys | ForEach-Object { (ConvertTo-Json -InputObject $_ -Compress)+':'+(Canonical $v[$_]) }) -join ',')+'}'
        }
        if($v -is [Collections.IEnumerable] -and $v -isnot [pscustomobject]) {
            return '['+(($v | ForEach-Object { Canonical $_ }) -join ',')+']'
        }
        $map=@{}; foreach($p in $v.PSObject.Properties) { $map[$p.Name]=$p.Value }; return Canonical $map
    }
    return Get-LanTextHash (Canonical $Value)
}
