#Requires -Version 7.0
<#
.SYNOPSIS
    Generates the add-on's data-pack parts from tools/parts.csv.

.DESCRIPTION
    Writes (all three folders are wiped and regenerated):
      src/main/resources/data/data_center_addon/datacenter/part/<id>.json   new real parts
      src/main/resources/assets/data_center_addon/models/item/part/<id>.json their icons
      src/main/resources/data/datacenter/datacenter/part/<id>.json           one "hidden" override per
          original Data Center shop part, active only when the config hides that category

    Stats, prices, dates and textures of the original parts are read from the Data Center jar that
    Gradle downloaded (located from gradle.properties), or from -DataCenterJar.
#>
param(
    [string]$DataCenterJar
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$csvPath = Join-Path $PSScriptRoot 'parts.csv'
$partsDir = Join-Path $root 'src/main/resources/data/data_center_addon/datacenter/part'
$modelsDir = Join-Path $root 'src/main/resources/assets/data_center_addon/models/item/part'
$hideDir = Join-Path $root 'src/main/resources/data/datacenter/datacenter/part'

$categories = @('CPU_AMD', 'CPU_INTEL', 'CPU_SERVER', 'GPU_NVIDIA', 'GPU_AMD', 'GPU_DATACENTER', 'GPU_MINING',
    'RAM', 'PSU', 'MAINBOARD', 'MAINBOARD_MINING', 'STORAGE', 'FAN')
$required = @{
    processor = @('kind', 'compute', 'power')
    mininggpu = @('compute', 'power')
    ram       = @('generation', 'sizeMb')
    storage   = @('capacityGb', 'solidState')
    psu       = @('maxPowerThroughput', 'wasteHeatPerFe', 'efficiency')
    mainboard = @('psuSlots', 'cpuSlots', 'gpuSlots', 'ramSlots', 'storageSlots', 'fanSlots', 'socket', 'ramGenerations')
    fan       = @('airflowCfm')
}
$textureFolder = @{ mininggpu = 'gpu'; ram = 'ram'; storage = 'storage'; psu = 'psu'; mainboard = 'mainboard'; fan = 'fan' }

# ------------------------------------------------------------------ Data Center jar
if (-not $DataCenterJar) {
    $props = Get-Content (Join-Path $root 'gradle.properties') -Raw | ConvertFrom-StringData
    $cache = Join-Path $HOME ".gradle/caches/modules-2/files-2.1/curse.maven/data-center-$($props.data_center_project_id)/$($props.data_center_file_id)"
    $DataCenterJar = Get-ChildItem $cache -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notmatch 'sources' } | Select-Object -First 1 -ExpandProperty FullName
    if (-not $DataCenterJar) {
        throw "Data Center jar not found in $cache. Run '.\gradlew.bat compileJava' once, or pass -DataCenterJar."
    }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($DataCenterJar)
try {
    $textures = @{}
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName -match '^assets/datacenter/textures/item/(.+)\.png$') { $textures[$Matches[1]] = $true }
    }
    $original = @{}                          # id -> stats row
    $shop = [ordered]@{}                     # id -> first shop row (category, price in cents, date)
    foreach ($file in 'parts_early.json', 'parts.json') {
        $reader = [System.IO.StreamReader]::new($zip.GetEntry("datacenter_data/catalog/$file").Open())
        try { $catalog = $reader.ReadToEnd() | ConvertFrom-Json -AsHashtable } finally { $reader.Dispose() }
        foreach ($segment in $catalog.segments.Values) { foreach ($row in $segment) { $original[$row.id] = $row } }
        foreach ($run in $catalog.shop.Values) {
            foreach ($row in $run) {
                if (-not $shop.Contains($row[1])) { $shop[$row[1]] = @{ category = $row[0]; price = [long]$row[2]; date = $row[3] } }
            }
        }
    }
} finally {
    $zip.Dispose()
}

# ------------------------------------------------------------------ helpers
function ConvertTo-StatValue([string]$key, [string]$text) {
    if ($key -eq 'ramGenerations') { return , @($text -split '\|') }
    if ($text -eq 'true') { return $true }
    if ($text -eq 'false') { return $false }
    if ($text -match '^-?\d+$') { return [long]$text }
    if ($text -match '^-?\d*\.\d+$') { return [double]::Parse($text, [cultureinfo]::InvariantCulture) }
    return $text
}

function Write-Json([string]$path, $value) {
    $json = $value | ConvertTo-Json -Depth 6
    [System.IO.File]::WriteAllText($path, $json + "`n", [System.Text.UTF8Encoding]::new($false))
}

function Reset-Dir([string]$dir) {
    if (Test-Path $dir) { Get-ChildItem $dir -Filter '*.json' | Remove-Item }
    else { New-Item -ItemType Directory -Force $dir | Out-Null }
}

# ------------------------------------------------------------------ parts.csv
$rows = Get-Content $csvPath | Where-Object { $_ -notmatch '^\s*(#|$)' } | ConvertFrom-Csv -Delimiter ';'
$errors = [System.Collections.Generic.List[string]]::new()
$parts = [ordered]@{}

foreach ($row in $rows) {
    $id = $row.id.Trim()
    $where = "parts.csv '$id'"
    if ($id -notmatch '^[a-z0-9_]+$') { $errors.Add("${where}: invalid id"); continue }
    if ($parts.Contains($id)) { $errors.Add("${where}: duplicate id"); continue }

    $from = "$($row.from)".Trim()
    $source = $null; $sourceShop = $null
    if ($from) {
        $source = $original[$from]
        $sourceShop = $shop[$from]
        if (-not $source) { $errors.Add("${where}: 'from' part '$from' does not exist in Data Center"); continue }
    }

    $type = "$($row.type)".Trim()
    if (-not $type -and $source) { $type = $source.type }
    if (-not $required.ContainsKey($type)) { $errors.Add("${where}: unknown type '$type'"); continue }

    $stats = [ordered]@{}
    if ($source) {
        foreach ($key in $source.Keys) { if ($key -notin 'id', 'type') { $stats[$key] = $source[$key] } }
    }
    if ("$($row.stats)".Trim()) {
        foreach ($pair in ($row.stats -split ',')) {
            $key, $value = $pair -split '=', 2
            $stats[$key.Trim()] = ConvertTo-StatValue $key.Trim() $value.Trim()
        }
    }
    if ($type -eq 'psu' -and $stats.Contains('efficiency') -and ($row.stats -match 'efficiency=' -or -not $stats.Contains('wasteHeatPerFe'))) {
        $stats['wasteHeatPerFe'] = [math]::Round((1.0 - [double]$stats['efficiency']) / 15.0, 5)
    }
    foreach ($key in $required[$type]) { if (-not $stats.Contains($key)) { $errors.Add("${where}: stat '$key' is missing") } }
    if ($type -eq 'processor' -and $stats['kind'] -eq 'CPU' -and -not $stats.Contains('socket')) { $errors.Add("${where}: CPU needs a socket") }

    $category = "$($row.category)".Trim()
    if (-not $category -and $sourceShop) { $category = $sourceShop.category }
    if ($category -notin $categories) { $errors.Add("${where}: invalid category '$category'") }

    $date = "$($row.date)".Trim()
    if (-not $date -and $sourceShop) { $date = $sourceShop.date }
    $parsedDate = [datetime]::MinValue
    if (-not [datetime]::TryParseExact($date, 'yyyy-MM-dd', [cultureinfo]::InvariantCulture, 'None', [ref]$parsedDate)) {
        $errors.Add("${where}: invalid date '$date'")
    }

    $priceText = "$($row.price)".Trim()
    if ($priceText) { $price = [long][math]::Round([decimal]::Parse($priceText, [cultureinfo]::InvariantCulture) * 100) }
    elseif ($sourceShop) { $price = $sourceShop.price }
    else { $errors.Add("${where}: price is missing"); $price = 0 }

    $name = "$($row.name)".Trim()
    if (-not $name -or $name.Length -gt 64) { $errors.Add("${where}: name must be 1-64 characters") }

    $look = "$($row.look)".Trim()
    if (-not $look) { $look = $from }
    $folder = if ($type -eq 'processor') { if ($stats['kind'] -eq 'GPU') { 'gpu' } else { 'cpu' } } else { $textureFolder[$type] }
    $texture = "$folder/$look"
    if (-not $textures.ContainsKey($texture)) { $errors.Add("${where}: texture 'datacenter:item/$texture' does not exist") }

    $parts[$id] = [ordered]@{
        type  = $type
        name  = $name
        stats = $stats
        shop  = [ordered]@{ category = $category; price = $price; date = $date }
        _texture = "datacenter:item/$texture"
    }
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Host $_ -ForegroundColor Red }
    throw "$($errors.Count) error(s) in parts.csv, nothing was written."
}

# ------------------------------------------------------------------ write
Reset-Dir $partsDir
Reset-Dir $modelsDir
Reset-Dir $hideDir

foreach ($id in $parts.Keys) {
    $part = $parts[$id]
    $texture = $part._texture
    $part.Remove('_texture')
    Write-Json (Join-Path $partsDir "$id.json") $part
    Write-Json (Join-Path $modelsDir "$id.json") ([ordered]@{
        parent   = 'minecraft:item/generated'
        textures = [ordered]@{ layer0 = $texture }
    })
}

foreach ($id in $shop.Keys) {
    Write-Json (Join-Path $hideDir "$id.json") ([ordered]@{
        'neoforge:conditions' = @([ordered]@{ type = 'data_center_addon:hide_original'; category = $shop[$id].category })
        shop                  = [ordered]@{ hidden = $true }
    })
}

$byCategory = $parts.Values | Group-Object { $_.shop.category } | Sort-Object Name | ForEach-Object { "$($_.Name)=$($_.Count)" }
Write-Host "Wrote $($parts.Count) parts ($($byCategory -join ', ')) and $($shop.Count) hide overrides."
