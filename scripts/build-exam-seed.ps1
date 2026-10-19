[CmdletBinding()]
param(
    [string]$InputPath = "data/seedingTest/ets2026_test01.html",
    [string]$ManifestPath = "data/seedingTest/manifest.json",
    [string]$OutputPath = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function ConvertFrom-HtmlFragment {
    param([AllowEmptyString()][string]$Html)

    $value = [regex]::Replace($Html, '(?i)<br\s*/?>|</(?:p|div|li|h[1-6])>', "`n")
    $value = [regex]::Replace($value, '<[^>]+>', '')
    $value = [System.Net.WebUtility]::HtmlDecode($value)
    $lines = @($value -split "`r?`n" | ForEach-Object {
        ([regex]::Replace($_, '[\t\x20\u00a0]+', ' ')).Trim()
    } | Where-Object { $_ -ne '' })
    return ($lines -join "`n").Trim()
}

function Get-HtmlAttribute {
    param(
        [string]$Tag,
        [string]$Name
    )

    $match = [regex]::Match($Tag, ('(?i)\b{0}\s*=\s*["'']([^"'']*)["'']' -f [regex]::Escape($Name)))
    if (-not $match.Success) {
        return $null
    }
    return [System.Net.WebUtility]::HtmlDecode($match.Groups[1].Value)
}

function Get-Part {
    param([int]$QuestionNumber)

    if ($QuestionNumber -le 6) { return 1 }
    if ($QuestionNumber -le 31) { return 2 }
    if ($QuestionNumber -le 70) { return 3 }
    if ($QuestionNumber -le 100) { return 4 }
    if ($QuestionNumber -le 130) { return 5 }
    if ($QuestionNumber -le 146) { return 6 }
    return 7
}

function Get-Section {
    param([int]$Part)
    if ($Part -le 4) { return 'LISTENING' }
    return 'READING'
}

function Get-MediaMatches {
    param([string]$Html)

    $matches = @()
    foreach ($match in [regex]::Matches($Html, '(?is)<audio\b[^>]*>.*?</audio>')) {
        $matches += [pscustomobject]@{ Index = $match.Index; Type = 'AUDIO'; Tag = $match.Value }
    }
    foreach ($match in [regex]::Matches($Html, '(?is)<img\b[^>]*>')) {
        $matches += [pscustomobject]@{ Index = $match.Index; Type = 'IMAGE'; Tag = $match.Value }
    }
    return @($matches | Sort-Object Index)
}

function Get-OptionText {
    param([string]$Value)

    $text = $Value.Trim()
    $text = [regex]::Replace($text, '\s*\([^)]*\)\s*:\s*.*$', '')
    $text = [regex]::Replace($text, '\s*:\s*.*$', '')
    return $text.Trim()
}

function Get-QuestionOptions {
    param(
        [string]$ArticleHtml,
        [int]$Part,
        [int]$QuestionNumber
    )

    $explanationMatch = [regex]::Match($ArticleHtml, '(?is)<div\b[^>]*class="[^"]*zen-history-explanation[^"]*"[^>]*>(.*)$')
    if (-not $explanationMatch.Success) {
        throw "Cannot find the explanation block used to extract options."
    }
    $text = ConvertFrom-HtmlFragment $explanationMatch.Groups[1].Value
    $result = [ordered]@{}

    if ($Part -ne 5) {
        foreach ($match in [regex]::Matches($text, '(?m)^\s*\(([A-D])\)\s*([^\r\n]+)')) {
            $label = $match.Groups[1].Value
            if (-not $result.Contains($label)) {
                $result[$label] = (Get-OptionText $match.Groups[2].Value)
            }
        }
    }
    else {
        foreach ($match in [regex]::Matches($text, '(?m)^\s*([A-D])\.\s*([^\r\n]+)')) {
            $label = $match.Groups[1].Value
            if (-not $result.Contains($label)) {
                $result[$label] = (Get-OptionText $match.Groups[2].Value)
            }
        }

        if ($result.Count -lt 4) {
            $dashValues = @([regex]::Matches($text, '(?m)^\s*[\u2013-]\s*([^\r\n]+)') | ForEach-Object {
                Get-OptionText $_.Groups[1].Value
            } | Where-Object { $_ -ne '' } | Select-Object -First 4)
            if ($dashValues.Count -eq 4) {
                $result = [ordered]@{}
                for ($index = 0; $index -lt 4; $index++) {
                    $result[([char](65 + $index)).ToString()] = $dashValues[$index]
                }
            }
        }
    }

    $expectedLabels = if ($Part -eq 2) { @('A', 'B', 'C') } else { @('A', 'B', 'C', 'D') }
    foreach ($label in $expectedLabels) {
        if (-not $result.Contains($label) -or [string]::IsNullOrWhiteSpace([string]$result[$label])) {
            throw "Missing option $label in question $QuestionNumber (Part $Part); parsed labels: $($result.Keys -join ',')."
        }
    }

    return @($expectedLabels | ForEach-Object {
        [ordered]@{ optionId = $_; text = [string]$result[$_]; orderIndex = [int][char]$_ - 64 }
    })
}

$resolvedInput = (Resolve-Path -LiteralPath $InputPath).Path
$resolvedManifest = (Resolve-Path -LiteralPath $ManifestPath).Path
$sourceStem = [System.IO.Path]::GetFileNameWithoutExtension($resolvedInput)
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = "data/seedingTest/generated/$sourceStem.seed.json"
}
$examId = $sourceStem.Replace('_', '-')
$testNumberMatch = [regex]::Match($sourceStem, 'test(\d+)$', 'IgnoreCase')
$examTitle = if ($testNumberMatch.Success) {
    "ETS 2026 Test $($testNumberMatch.Groups[1].Value)"
} else {
    $sourceStem
}
$html = Get-Content -LiteralPath $resolvedInput -Raw -Encoding UTF8
$manifest = Get-Content -LiteralPath $resolvedManifest -Raw -Encoding UTF8 | ConvertFrom-Json
$relativeInput = $InputPath.Replace('\', '/')
$manifestFile = @($manifest.files | Where-Object { $_.path.Replace('\', '/') -eq $relativeInput })
if ($manifestFile.Count -ne 1) {
    throw "Manifest must contain exactly one entry for $relativeInput."
}

$sourceSha256 = (Get-FileHash -LiteralPath $resolvedInput -Algorithm SHA256).Hash.ToLowerInvariant()
if ($sourceSha256 -ne $manifestFile[0].sha256) {
    throw "The HTML SHA-256 does not match the manifest."
}

$groups = @()
$groupRanges = @()
$mediaAssets = @()
$contentBlocks = @()
$mediaByUrl = @{}
$mediaCounters = @{ AUDIO = 0; IMAGE = 0 }

function Add-MediaBlock {
    param(
        [object]$Media,
        [string]$OwnerType,
        [string]$OwnerId,
        [int]$OrderIndex
    )

    $url = Get-HtmlAttribute $Media.Tag 'src'
    if ([string]::IsNullOrWhiteSpace($url)) {
        $sourceMatch = [regex]::Match($Media.Tag, '(?is)<source\b[^>]*>')
        if ($sourceMatch.Success) {
            $url = Get-HtmlAttribute $sourceMatch.Value 'src'
        }
    }
    if ([string]::IsNullOrWhiteSpace($url)) {
        throw "Media $($Media.Type) has no src attribute."
    }

    $key = "$($Media.Type)|$url"
    if (-not $mediaByUrl.ContainsKey($key)) {
        $mediaCounters[$Media.Type]++
        $mediaId = ('{0}-{1:d3}' -f $Media.Type.ToLowerInvariant(), $mediaCounters[$Media.Type])
        $asset = [ordered]@{
            mediaId = $mediaId
            mediaType = $Media.Type
            sourceUrl = $url
            storageStatus = 'REMOTE_REFERENCE'
            altText = if ($Media.Type -eq 'IMAGE') { Get-HtmlAttribute $Media.Tag 'alt' } else { $null }
            widthPx = if ($Media.Type -eq 'IMAGE' -and (Get-HtmlAttribute $Media.Tag 'width')) { [int](Get-HtmlAttribute $Media.Tag 'width') } else { $null }
            heightPx = if ($Media.Type -eq 'IMAGE' -and (Get-HtmlAttribute $Media.Tag 'height')) { [int](Get-HtmlAttribute $Media.Tag 'height') } else { $null }
        }
        $script:mediaAssets += $asset
        $mediaByUrl[$key] = $mediaId
    }

    $blockId = ('block-{0}-{1}-{2:d2}' -f $OwnerType.ToLowerInvariant(), $OwnerId, $OrderIndex)
    $script:contentBlocks += [ordered]@{
        blockId = $blockId
        ownerType = $OwnerType
        ownerId = $OwnerId
        blockType = $Media.Type
        orderIndex = $OrderIndex
        textContent = $null
        mediaId = $mediaByUrl[$key]
    }
}

$groupPattern = '(?is)<section\b[^>]*data-zen-history-(listening|reading)-group[^>]*data-question-id="([^"]+)"[^>]*>.*?(Listening|Reading)[^0-9<]*(\d+)[^0-9<]+(\d+)'
$groupMatches = [regex]::Matches($html, $groupPattern)
foreach ($groupMatch in $groupMatches) {
    $sourceGroupId = $groupMatch.Groups[2].Value
    $firstQuestion = [int]$groupMatch.Groups[4].Value
    $lastQuestion = [int]$groupMatch.Groups[5].Value
    $part = Get-Part $firstQuestion
    if ((Get-Part $lastQuestion) -ne $part) {
        throw "Group $sourceGroupId crosses a Part boundary."
    }
    $groupId = "group-$sourceGroupId"
    $firstArticleIndex = $html.IndexOf('<article', $groupMatch.Index, [System.StringComparison]::OrdinalIgnoreCase)
    if ($firstArticleIndex -lt 0) {
        throw "Cannot find the first question in group $sourceGroupId."
    }
    $prefix = $html.Substring($groupMatch.Index, $firstArticleIndex - $groupMatch.Index)
    $groups += [ordered]@{
        groupId = $groupId
        sourceGroupId = $sourceGroupId
        section = Get-Section $part
        part = $part
        title = "Part $part - Questions $firstQuestion-$lastQuestion"
        firstQuestion = $firstQuestion
        lastQuestion = $lastQuestion
        orderIndex = $firstQuestion
    }
    $groupRanges += [pscustomobject]@{ GroupId = $groupId; First = $firstQuestion; Last = $lastQuestion }

    $blockOrder = 1
    foreach ($media in Get-MediaMatches $prefix) {
        Add-MediaBlock $media 'GROUP' $groupId $blockOrder
        $blockOrder++
    }
}

$questions = @()
$options = @()
$articleMatches = [regex]::Matches($html, '(?is)<article\b.*?</article>')
foreach ($articleMatch in $articleMatches) {
    $article = $articleMatch.Value
    $numberMatch = [regex]::Match($article, 'data-question-number="(\d+)"')
    if (-not $numberMatch.Success) {
        throw "Article has no data-question-number."
    }
    $number = [int]$numberMatch.Groups[1].Value
    $questionId = ('q{0:d3}' -f $number)
    $part = Get-Part $number
    $promptMatch = [regex]::Match($article, '(?is)<div\b[^>]*class="[^"]*zen-history-sub-question[^"]*"[^>]*>(.*?)</div>')
    if (-not $promptMatch.Success) {
        throw "Question $number has no prompt."
    }
    $prompt = ConvertFrom-HtmlFragment $promptMatch.Groups[1].Value
    $correctMatch = [regex]::Match($article, '(?is)<strong>\s*\u0110\u00e1p \u00e1n \u0111\u00fang:\s*</strong>\s*([A-D])\.')
    if (-not $correctMatch.Success) {
        throw "Question $number has no correct answer."
    }
    $group = @($groupRanges | Where-Object { $number -ge $_.First -and $number -le $_.Last })
    if ($group.Count -gt 1) {
        throw "Question $number belongs to more than one group."
    }
    $groupId = if ($group.Count -eq 1) { $group[0].GroupId } else { $null }

    $questionOptions = @(Get-QuestionOptions $article $part $number)
    foreach ($option in $questionOptions) {
        $options += [ordered]@{
            questionId = $questionId
            optionId = $option.optionId
            text = $option.text
            orderIndex = $option.orderIndex
        }
    }

    $questions += [ordered]@{
        questionId = $questionId
        sourceQuestionNumber = $number
        section = Get-Section $part
        part = $part
        groupId = $groupId
        prompt = $prompt
        correctOption = $correctMatch.Groups[1].Value
        answerKeyStatus = 'UNVERIFIED_SOURCE_REVIEW_PAGE'
        orderIndex = $number
    }

    $blockOrder = 1
    $promptPosition = $promptMatch.Index
    $items = @([pscustomobject]@{ Index = $promptPosition; Type = 'TEXT'; Value = $prompt })
    foreach ($media in Get-MediaMatches $article) {
        $items += [pscustomobject]@{ Index = $media.Index; Type = 'MEDIA'; Value = $media }
    }
    foreach ($item in @($items | Sort-Object Index)) {
        if ($item.Type -eq 'TEXT') {
            $contentBlocks += [ordered]@{
                blockId = ('block-question-{0}-{1:d2}' -f $questionId, $blockOrder)
                ownerType = 'QUESTION'
                ownerId = $questionId
                blockType = 'TEXT'
                orderIndex = $blockOrder
                textContent = $item.Value
                mediaId = $null
            }
        }
        else {
            Add-MediaBlock $item.Value 'QUESTION' $questionId $blockOrder
        }
        $blockOrder++
    }
}

$expectedPartCounts = @(6, 25, 39, 30, 30, 16, 54)
$actualPartCounts = @(1..7 | ForEach-Object {
    $targetPart = $_
    @($questions | Where-Object { $_.part -eq $targetPart }).Count
})
if (($actualPartCounts -join ',') -ne ($expectedPartCounts -join ',')) {
    throw "Unexpected Part distribution: $($actualPartCounts -join ',')."
}
$cardinalityValid = $questions.Count -eq 200 -and
        $groups.Count -eq 42 -and
        $mediaCounters.AUDIO -eq [int]$manifestFile[0].audioElements -and
        $mediaCounters.IMAGE -gt 0 -and
        $mediaCounters.IMAGE -le [int]$manifestFile[0].imageElements
if (-not $cardinalityValid) {
    throw "Sai cardinality: questions=$($questions.Count), groups=$($groups.Count), audio=$($mediaCounters.AUDIO), images=$($mediaCounters.IMAGE)."
}
if (@($questions.questionId | Sort-Object -Unique).Count -ne 200) {
    throw "Question IDs are not unique."
}
foreach ($question in $questions) {
    if (-not (@($options | Where-Object { $_.questionId -eq $question.questionId }).optionId -contains $question.correctOption)) {
        throw "Correct option for $($question.questionId) does not exist."
    }
}

$seed = [ordered]@{
    schemaVersion = 1
    seedType = 'EXAM_DATASET_CANDIDATE'
    importable = $false
    importBlockers = @(
        'Answer keys in manifest are NOT VERIFIED.',
        'Group/passage mapping in manifest is NOT VERIFIED.',
        'Media are remote references and have not been downloaded or playback-tested.',
        'Options were extracted from the review explanation and are not verified candidate-facing content.'
    )
    exam = [ordered]@{
        examId = $examId
        title = $examTitle
        description = 'Seed candidate extracted from the published sample HTML.'
        totalQuestions = 200
    }
    source = [ordered]@{
        path = $relativeInput
        sha256 = $sourceSha256
        sourceName = $manifest.source
        copyrightNotice = $manifest.sourceCopyright
        permissionStatus = $manifest.distributionPermission.status
        permissionDate = $manifest.distributionPermission.date
        licenseDocument = $manifest.distributionPermission.licenseDocument
        answerKeys = $manifest.answerKeys
        optionTextOrigin = 'REVIEW_EXPLANATION'
        candidateOptionContent = 'NOT_VERIFIED'
        groupPassageMapping = $manifest.groupPassageMapping
        mediaDownloadPlayback = $manifest.mediaDownloadPlayback
    }
    summary = [ordered]@{
        questionCount = $questions.Count
        partCounts = $actualPartCounts
        groupCount = $groups.Count
        optionCount = $options.Count
        audioAssetCount = $mediaCounters.AUDIO
        imageAssetCount = $mediaCounters.IMAGE
        contentImageCount = $mediaCounters.IMAGE
        sourceHtmlImageCount = $manifestFile[0].imageElements
        excludedPageChromeImageCount = [int]$manifestFile[0].imageElements - $mediaCounters.IMAGE
    }
    questionGroups = $groups
    questions = $questions
    options = $options
    mediaAssets = $mediaAssets
    contentBlocks = $contentBlocks
}

$outputDirectory = Split-Path -Parent $OutputPath
if (-not [string]::IsNullOrWhiteSpace($outputDirectory)) {
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
}
$json = $seed | ConvertTo-Json -Depth 12
[System.IO.File]::WriteAllText((Join-Path (Get-Location) $OutputPath), $json + "`n", [System.Text.UTF8Encoding]::new($false))

Write-Host "Generated $OutputPath"
Write-Host "Questions=$($questions.Count) Groups=$($groups.Count) Options=$($options.Count) Audio=$($mediaCounters.AUDIO) Images=$($mediaCounters.IMAGE)"
Write-Host "Importable=$($seed.importable) (see importBlockers)"
