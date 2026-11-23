#Requires -Version 7.0
<#
.SYNOPSIS
    Generates the add-on's own part textures (32x32, Data Center's pixel-art style).

.DESCRIPTION
    Most textures are colour variants of Data Center's own item textures (read from the Data Center
    jar that Gradle downloaded), a few are drawn from scratch. Output:
      src/main/resources/assets/data_center_addon/textures/item/part/<name>.png
    In tools/parts.csv a part uses one of them with look=@<name>. Windows only (System.Drawing).
#>
param(
    [string]$DataCenterJar
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = Split-Path $PSScriptRoot -Parent
$outDir = Join-Path $root 'src/main/resources/assets/data_center_addon/textures/item/part'

if (-not $DataCenterJar) {
    $props = Get-Content (Join-Path $root 'gradle.properties') -Raw | ConvertFrom-StringData
    $cache = Join-Path $HOME ".gradle/caches/modules-2/files-2.1/curse.maven/data-center-$($props.data_center_project_id)/$($props.data_center_file_id)"
    $DataCenterJar = Get-ChildItem $cache -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notmatch 'sources' } | Select-Object -First 1 -ExpandProperty FullName
    if (-not $DataCenterJar) { throw "Data Center jar not found in $cache. Run '.\gradlew.bat compileJava' once, or pass -DataCenterJar." }
}

$zip = [System.IO.Compression.ZipFile]::OpenRead($DataCenterJar)

function Get-Source([string]$texture) {
    $entry = $zip.GetEntry("assets/datacenter/textures/item/$texture.png")
    if (-not $entry) { throw "Data Center texture '$texture' not found" }
    $stream = $entry.Open()
    try {
        $memory = [System.IO.MemoryStream]::new()
        $stream.CopyTo($memory)
        $memory.Position = 0
        $loaded = [System.Drawing.Bitmap]::new($memory)
        $copy = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $g = [System.Drawing.Graphics]::FromImage($copy)
        $g.DrawImageUnscaled($loaded, 0, 0)
        $g.Dispose(); $loaded.Dispose()
        return $copy
    } finally { $stream.Dispose() }
}

function ConvertTo-Color([string]$hex) {
    [System.Drawing.Color]::FromArgb(255, [Convert]::ToInt32($hex.Substring(0, 2), 16),
            [Convert]::ToInt32($hex.Substring(2, 2), 16), [Convert]::ToInt32($hex.Substring(4, 2), 16))
}

# Replaces exact colours. Only pixels with y < $maxY are changed (keeps e.g. gold PCIe contacts).
function Set-Colors($bitmap, [hashtable]$map, [int]$maxY = 32) {
    $lookup = @{}
    foreach ($key in $map.Keys) { $lookup[$key.ToUpperInvariant()] = ConvertTo-Color $map[$key] }
    for ($y = 0; $y -lt $maxY; $y++) {
        for ($x = 0; $x -lt 32; $x++) {
            $c = $bitmap.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $key = '{0:X2}{1:X2}{2:X2}' -f $c.R, $c.G, $c.B
            if ($lookup.ContainsKey($key)) { $bitmap.SetPixel($x, $y, $lookup[$key]) }
        }
    }
}

function Add-Rect($bitmap, [int]$x1, [int]$y1, [int]$x2, [int]$y2, [string]$hex) {
    $color = ConvertTo-Color $hex
    for ($y = $y1; $y -le $y2; $y++) { for ($x = $x1; $x -le $x2; $x++) { $bitmap.SetPixel($x, $y, $color) } }
}

function New-Blank {
    [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}

$textures = [ordered]@{}

# ------------------------------------------------------------------ CPUs
$t = Get-Source 'cpu/flint_ridge_core_i9-13900k'      # navy substrate, blue label
Set-Colors $t @{ '2B5C4A' = '1E2A44'; '3A765E' = '2E3F66'; '193C30' = '121A2E'; 'FFC85A' = '53A2ED' }
$textures['intel_core_ultra'] = $t

$t = Get-Source 'cpu/amp_piston_ryzen_9_7950x'        # dark lid, orange label: X3D
Set-Colors $t @{ 'BCC2C8' = '8C929A'; 'E2E6EA' = 'B4BAC2'; '7F868E' = '5C626C'; '989EA6' = '70767E'
                 'FFC85A' = 'ED6D41'; '3F864A' = '3A3A40'; '1C4827' = '1A1A1E'; '2F6B3A' = '2C2C32' }
$textures['amd_ryzen_x3d'] = $t

$t = Get-Source 'cpu/amp_threadwhip_epyc_9654'        # blue substrate, blue label: Xeon Scalable
Set-Colors $t @{ '2F6B3A' = '22487A'; '1C4827' = '142C50'; '3F864A' = '2E5C9A'; 'FFC85A' = '53A2ED' }
$textures['xeon_scalable'] = $t

# ------------------------------------------------------------------ GPUs
$t = Get-Source 'gpu/invidia_vtx_rtx_4090'            # dark shroud, blue hubs, white backplate
Set-Colors $t @{ '4A4F58' = '2A2C34'; '5C626C' = '3A3D48'; '7F868E' = '5A5E6E'; '3F8A2A' = '2E6FD8'; '7FD753' = '7FB8FF'; 'BCC2C8' = 'E8E8EC' }
$textures['intel_arc'] = $t

$t = Get-Source 'gpu/invidia_tensor_h100'             # black blower, green light: NVIDIA workstation
Set-Colors $t @{ '7A581C' = '16171C'; 'B88A2E' = '26282E'; 'D9A83A' = '3A3D45' } 25
$textures['workstation_green'] = $t

$t = Get-Source 'gpu/invidia_tensor_h100'             # black blower, red light: AMD workstation
Set-Colors $t @{ '7A581C' = '16171C'; 'B88A2E' = '26282E'; 'D9A83A' = '3A3D45'; '7FD753' = 'FF4646' } 25
$textures['workstation_red'] = $t

$t = Get-Source 'gpu/invidia_tensor_h100'             # silver passive data center card
Set-Colors $t @{ '7A581C' = '7F868E'; 'B88A2E' = 'BCC2C8'; 'D9A83A' = 'DCE0E4' } 25
$textures['passive_silver'] = $t

$t = New-Blank                                        # SXM / OAM module: board with a big heatsink
Add-Rect $t 2 4 29 27 '143D28'
Add-Rect $t 3 5 28 26 '1F5A3A'
Add-Rect $t 5 6 26 21 '7F868E'
Add-Rect $t 6 7 25 20 'BCC2C8'
for ($x = 7; $x -le 24; $x += 2) { Add-Rect $t $x 8 $x 19 '989EA6' }
Add-Rect $t 6 7 25 7 'DCE0E4'
Add-Rect $t 6 23 13 25 '0E0F12'
Add-Rect $t 18 23 25 25 '0E0F12'
for ($x = 7; $x -le 12; $x += 2) { Add-Rect $t $x 24 $x 24 'D9A83A' }
for ($x = 19; $x -le 24; $x += 2) { Add-Rect $t $x 24 $x 24 'D9A83A' }
Add-Rect $t 27 5 27 5 '7FD753'
$textures['sxm_module'] = $t

# ------------------------------------------------------------------ RAM
$t = Get-Source 'ram/kingsley_ddr4_16'                # green PCB, black chips: ECC RDIMM
Set-Colors $t @{ '24242A' = '2B6B45'; 'E8E8EC' = '1A1A1E'; '3C78DC' = 'D9A83A' }
$textures['rdimm_ecc'] = $t

$t = Get-Source 'ram/gforce_ddr5_32'                  # white heatspreader with RGB bar
Set-Colors $t @{ '2C2C32' = 'E2E6EA'; '14141A' = 'BCC2C8'; '0A0A10' = '7F868E'; 'C82828' = '9AA0A8' }
$textures['ram_white_rgb'] = $t

# ------------------------------------------------------------------ Storage
$t = Get-Source 'storage/samcore_pro_nvme_2tb'        # M.2 with black heatsink and white label
Set-Colors $t @{ '3C3C44' = '1A1A1E'; '505058' = '2C2C32'; '28282E' = '0E0E12'; 'C8322E' = 'E8E8EC' }
$textures['nvme_heatsink'] = $t

$t = Get-Source 'storage/seacrest_barracuda_8tb'      # silver enterprise HDD with blue label
Set-Colors $t @{ '2C2E34' = '7F868E'; '464850' = 'A8AEB6'; '3C965A' = '2E6FD8' }
$textures['hdd_enterprise'] = $t

$t = New-Blank                                        # 2.5" SATA SSD
Add-Rect $t 4 7 27 25 '141418'
Add-Rect $t 5 8 26 24 '2C2C32'
Add-Rect $t 7 11 24 18 'E8E8EC'
Add-Rect $t 7 11 24 11 'C8322E'
Add-Rect $t 8 14 17 14 '96989E'
Add-Rect $t 8 16 14 16 '96989E'
Add-Rect $t 5 23 26 24 '18181C'
Add-Rect $t 7 25 13 26 'D6B046'
Add-Rect $t 16 25 19 26 'D6B046'
$textures['ssd_sata'] = $t

# ------------------------------------------------------------------ PSUs
$t = Get-Source 'psu/corsaire_titanium_1500'          # white accents
Set-Colors $t @{ 'D63028' = 'E8E8EC' }
$textures['psu_white'] = $t

$t = Get-Source 'psu/corsaire_titanium_1500'          # blue accents
Set-Colors $t @{ 'D63028' = '2E6FD8' }
$textures['psu_blue'] = $t

# ------------------------------------------------------------------ Fans
$t = Get-Source 'fan/flowtek_120mm'                   # beige frame, brown blades
Set-Colors $t @{ '1C1C20' = 'C8B497'; '0E0E12' = '9C8A70'; '62646C' = '7A4A32'; '444448' = '5A3424'; '5A5C62' = '6E4230'; '8C9096' = '9A6446' }
$textures['fan_beige_brown'] = $t

$zip.Dispose()

# ------------------------------------------------------------------ write
if (Test-Path $outDir) { Get-ChildItem $outDir -Filter '*.png' | Remove-Item }
else { New-Item -ItemType Directory -Force $outDir | Out-Null }
foreach ($name in $textures.Keys) {
    $textures[$name].Save((Join-Path $outDir "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $textures[$name].Dispose()
}
Write-Host "Wrote $($textures.Count) textures to $outDir"
