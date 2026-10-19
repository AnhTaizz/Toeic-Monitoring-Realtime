param([string]$JavacPath = 'javac')
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
$contractPath=Join-Path $repoRoot 'docs/EX02_RICH_EXAM_CONTRACT_V1.md'
$contract=Get-Content -LiteralPath $contractPath -Raw -Encoding UTF8
$records=@{}
foreach ($match in [regex]::Matches($contract,'(?s)record\s+(\w+)\s*\((.*?)\)\s*\{\}')) {
    $fields=@($match.Groups[2].Value.Split(',') | ForEach-Object {
        $pair=$_.Trim() -split '\s+'
        [pscustomobject]@{type=$pair[0];name=$pair[1]}
    })
    if ($records.ContainsKey($match.Groups[1].Value)) {throw 'Duplicate record declaration'}
    $records[$match.Groups[1].Value]=$fields
}
function CheckRecord($type,$value,$path) {
    if ($null -eq $value -or -not $records.ContainsKey($type)) {return}
    $expected=@($records[$type].name | Sort-Object)
    $actual=@($value.PSObject.Properties.Name | Sort-Object)
    if (($expected -join ',') -cne ($actual -join ',')) {throw "DTO field mismatch: $path ($type)"}
    foreach ($field in $records[$type]) {
        $fieldValue=$value.($field.name)
        if ($field.type -match '^List<(.+)>$') {
            $elementType=$Matches[1]
            if ($fieldValue -isnot [array]) {throw "Expected list: $path.$($field.name)"}
            foreach ($element in $fieldValue) {CheckRecord $elementType $element "$path.$($field.name)[]"}
        } else {CheckRecord $field.type $fieldValue "$path.$($field.name)"}
    }
}
$roots=[ordered]@{'import.mock.json'='RichExamImportRequest';'candidate.mock.json'='RichCandidateExam';
    'preparation.mock.json'='AttemptPreparation';'catalog.mock.json'='ExamCatalogPage';
    'import-response.mock.json'='RichExamImportResponse';'create-session-request.mock.json'='CreateRichSessionRequest';
    'create-session-response.mock.json'='CreateRichSessionResponse'}
foreach ($name in $roots.Keys) {
    $json=Get-Content -LiteralPath (Join-Path $PSScriptRoot $name) -Raw -Encoding UTF8 | ConvertFrom-Json
    CheckRecord $roots[$name] $json $name
}
$jsonFiles=@(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.json' | Where-Object Name -ne 'contract-verification.mock.json')
foreach ($file in $jsonFiles) { $null=Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8 | ConvertFrom-Json }
# Verify local links in task deliverables/handoff files. HTTP/anchors are not fetched.
$docs=@('README.md','docs/EX02_RICH_EXAM_CONTRACT_V1.md','docs/plan-v2/ADR-V2-03_SESSION_START_DEADLINE.md',
    'docs/plan-v2/EX02_DECISIONS_ROADMAP.md','docs/plan-v2/fixtures/ex02/README.md',
    'evidence/ex02-contract-20261019/REPORT.md')
$links=0
foreach ($relative in $docs) {
    $path=Join-Path $repoRoot $relative
    $body=Get-Content -LiteralPath $path -Raw -Encoding UTF8
    foreach ($link in [regex]::Matches($body,'\[[^\]]*\]\(([^)]+)\)')) {
        $destination=$link.Groups[1].Value
        if ($destination -match '^(https?://|#|mailto:)') {continue}
        $destination=($destination -split '#')[0]
        if (-not (Test-Path -LiteralPath (Join-Path (Split-Path $path) $destination))) {throw "Broken local link: $relative -> $destination"}
        $links++
    }
}
$compileDir=Join-Path $repoRoot 'target/ex02-contract-signatures'
New-Item -ItemType Directory -Force -Path $compileDir|Out-Null
$snippets=@([regex]::Matches($contract,'(?s)```java\s+(.*?)```')|ForEach-Object {$_.Groups[1].Value})
$source="import java.util.List;`nimport java.time.LocalDate;`nimport java.time.Instant;`n"+($snippets -join "`n")
$sourcePath=Join-Path $compileDir 'ContractSignatures.java'
[IO.File]::WriteAllText($sourcePath,$source,(New-Object Text.UTF8Encoding($false)))
$javacCommand=Get-Command $JavacPath -ErrorAction Stop
$javacBin=Split-Path $javacCommand.Source
$env:JAVA_HOME=Split-Path $javacBin
$env:PATHEXT='.COM;.EXE;.BAT;.CMD'
$env:PATH=$javacBin+';'+[Environment]::GetFolderPath('System')+';'+$env:PATH
& $JavacPath --release 21 -encoding UTF-8 -d $compileDir $sourcePath
if ($LASTEXITCODE -ne 0) {throw "Java record signatures did not compile; exit=$LASTEXITCODE"}
$result=[ordered]@{scope='PROPOSED_CONTRACT_ONLY';dtoRecords=$records.Count;exampleRoots=$roots.Count;
    jsonFiles=$jsonFiles.Count;localLinks=$links;fieldConsistency='PASS';java21Signatures='PASS';
    contractSha256=(Get-FileHash -LiteralPath $contractPath -Algorithm SHA256).Hash.ToLowerInvariant();
    runtime='NOT RUN';note='Compilation and design consistency do not implement or validate server behavior.'}
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'contract-verification.mock.json'),(($result|ConvertTo-Json)+"`n"),(New-Object Text.UTF8Encoding($false)))
Write-Output ($result|ConvertTo-Json)
