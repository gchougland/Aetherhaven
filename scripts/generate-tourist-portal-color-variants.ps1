# Generates 16 color-variant tourist portal particle spawers, systems, and block item JSONs.
# Run from repo root: pwsh scripts/generate-tourist-portal-color-variants.ps1

$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot

$PresetHex = @(
    "#E85D5D", "#E8985D", "#E8C85D", "#9AD45D", "#5DC985", "#5DC9B8", "#5DA8E8", "#6D5DE8",
    "#B85DE8", "#E85DA8", "#C9A882", "#8A8A98", "#4A6B42", "#5C4A72", "#3D4F6A", "#D4AF37"
)

$BaseHueRef = 0.72  # approximate hue of default purple portal palette (#6D5DE8)

$ParticlesRoot = Join-Path $RepoRoot "subplugin-assets\Commerce\Server\Particles\Aetherhaven\TouristPortal"
$SpawnersDir = Join-Path $ParticlesRoot "Spawners"
$ItemsDir = Join-Path $RepoRoot "subplugin-assets\Commerce\Server\Item\Items\Aetherhaven"

function Write-Utf8NoBom([string]$Path, [string]$Content) {
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
}

function Convert-HexToRgb([string]$hex) {
    $h = $hex.Trim().TrimStart('#')
    if ($h.Length -ge 6) { $h = $h.Substring(0, 6) }
    return @(
        [int]"0x$($h.Substring(0,2))",
        [int]"0x$($h.Substring(2,2))",
        [int]"0x$($h.Substring(4,2))"
    )
}

function Clamp-Int([int]$v, [int]$min, [int]$max) {
    if ($v -lt $min) { return $min }
    if ($v -gt $max) { return $max }
    return $v
}

function Convert-RgbToHex([int]$r, [int]$g, [int]$b) {
    $r = Clamp-Int $r 0 255
    $g = Clamp-Int $g 0 255
    $b = Clamp-Int $b 0 255
    return "#{0:X2}{1:X2}{2:X2}" -f $r, $g, $b
}

function Convert-RgbToHsl([int]$r, [int]$g, [int]$b) {
    $rf = $r / 255.0; $gf = $g / 255.0; $bf = $b / 255.0
    $max = [Math]::Max($rf, [Math]::Max($gf, $bf))
    $min = [Math]::Min($rf, [Math]::Min($gf, $bf))
    $l = ($max + $min) / 2.0
    if ($max -eq $min) { return @(0.0, 0.0, $l) }
    $d = $max - $min
    $s = if ($l -gt 0.5) { $d / (2.0 - $max - $min) } else { $d / ($max + $min) }
    if ($max -eq $rf) {
        $h = (($gf - $bf) / $d + $(if ($gf -lt $bf) { 6 } else { 0 })) / 6.0
    } elseif ($max -eq $gf) {
        $h = (($bf - $rf) / $d + 2.0) / 6.0
    } else {
        $h = (($rf - $gf) / $d + 4.0) / 6.0
    }
    return @($h, $s, $l)
}

function Convert-HslToRgb([double]$h, [double]$s, [double]$l) {
    if ($s -eq 0) {
        $v = [int][Math]::Round($l * 255)
        return @($v, $v, $v)
    }
    function HueToRgb([double]$p, [double]$q, [double]$t) {
        if ($t -lt 0) { $t += 1 }
        if ($t -gt 1) { $t -= 1 }
        if ($t -lt 1.0/6.0) { return $p + ($q - $p) * 6.0 * $t }
        if ($t -lt 1.0/2.0) { return $q }
        if ($t -lt 2.0/3.0) { return $p + ($q - $p) * (2.0/3.0 - $t) * 6.0 }
        return $p
    }
    $q = if ($l -lt 0.5) { $l * (1 + $s) } else { $l + $s - $l * $s }
    $p = 2 * $l - $q
    return @(
        [int][Math]::Round((HueToRgb $p $q ($h + 1.0/3.0)) * 255),
        [int][Math]::Round((HueToRgb $p $q $h) * 255),
        [int][Math]::Round((HueToRgb $p $q ($h - 1.0/3.0)) * 255)
    )
}

function Get-PresetHue([string]$hex) {
    $rgb = Convert-HexToRgb $hex
    return (Convert-RgbToHsl $rgb[0] $rgb[1] $rgb[2])[0]
}

function Shift-ColorHex([string]$sourceHex, [double]$hueDelta) {
    $rgb = Convert-HexToRgb $sourceHex
    $hsl = Convert-RgbToHsl $rgb[0] $rgb[1] $rgb[2]
    $newH = $hsl[0] + $hueDelta
    while ($newH -lt 0) { $newH += 1.0 }
    while ($newH -ge 1.0) { $newH -= 1.0 }
    $newRgb = Convert-HslToRgb $newH $hsl[1] $hsl[2]
    return Convert-RgbToHex $newRgb[0] $newRgb[1] $newRgb[2]
}

function Shift-SpawnerJson([string]$json, [double]$hueDelta) {
    return [regex]::Replace($json, '"Color"\s*:\s*"#([0-9A-Fa-f]{6})"', {
        param($m)
        $shifted = Shift-ColorHex "#$($m.Groups[1].Value)" $hueDelta
        return "`"Color`": `"$shifted`""
    })
}

function Rename-SpawnerIdInJson([string]$json, [string]$suffix) {
    return [regex]::Replace($json, '(Aetherhaven_Tourist_Portal_[A-Za-z0-9_]+)(?=\.particlespawner|"|\s|$)', {
        param($m)
        $id = $m.Groups[1].Value
        if ($id -match '_C\d{2}$') { return $id }
        return "${id}${suffix}"
    })
}

$BaseSpawners = Get-ChildItem -Path $SpawnersDir -Filter "*.particlespawner" -File |
    Where-Object { $_.Name -notmatch '_C\d{2}\.' }

$IdleSystemPath = Join-Path $ParticlesRoot "Aetherhaven_Tourist_Portal_Idle.particlesystem"
$BurstSystemPath = Join-Path $ParticlesRoot "Aetherhaven_Tourist_Portal_Burst.particlesystem"
$IdleSystemJson = Get-Content -Raw -Path $IdleSystemPath
$BurstSystemJson = Get-Content -Raw -Path $BurstSystemPath

for ($i = 0; $i -lt $PresetHex.Count; $i++) {
    $idx = $i + 1
    $suffix = "_C{0:D2}" -f $idx
    $preset = $PresetHex[$i]
    $targetHue = Get-PresetHue $preset
    $hueDelta = $targetHue - $BaseHueRef
    while ($hueDelta -gt 0.5) { $hueDelta -= 1.0 }
    while ($hueDelta -lt -0.5) { $hueDelta += 1.0 }

    $variantSpawnerDir = Join-Path $SpawnersDir ("ColorVariants\C{0:D2}" -f $idx)
    New-Item -ItemType Directory -Force -Path $variantSpawnerDir | Out-Null

    foreach ($spawnerFile in $BaseSpawners) {
        $baseName = [System.IO.Path]::GetFileNameWithoutExtension($spawnerFile.Name)
        $variantName = "${baseName}${suffix}.particlespawner"
        $raw = Get-Content -Raw -Path $spawnerFile.FullName
        $shifted = Shift-SpawnerJson $raw $hueDelta
        $outPath = Join-Path $variantSpawnerDir $variantName
        Write-Utf8NoBom $outPath $shifted
    }

    $idleOut = Join-Path $ParticlesRoot "Aetherhaven_Tourist_Portal_Idle${suffix}.particlesystem"
    $idleVariant = $IdleSystemJson -replace '(Aetherhaven_Tourist_Portal_[A-Za-z0-9_]+)(?=")', "`${1}${suffix}"
    Write-Utf8NoBom $idleOut $idleVariant

    $burstOut = Join-Path $ParticlesRoot "Aetherhaven_Tourist_Portal_Burst${suffix}.particlesystem"
    $burstVariant = $BurstSystemJson -replace '(Aetherhaven_Tourist_Portal_[A-Za-z0-9_]+)(?=")', "`${1}${suffix}"
    Write-Utf8NoBom $burstOut $burstVariant

    $itemOut = Join-Path $ItemsDir "Aetherhaven_Tourist_Portal${suffix}.json"
    $idleSystemId = "Aetherhaven_Tourist_Portal_Idle${suffix}"
    $itemJson = @"
{
  "Parent": "Aetherhaven_Tourist_Portal",
  "BlockType": {
    "Particles": [
      {
        "SystemId": "$idleSystemId",
        "TargetNodeName": "PortalVFX",
        "PositionOffset": {
          "Y": 2.5
        },
        "Scale": 0.8
      }
    ]
  }
}
"@
    Write-Utf8NoBom $itemOut $itemJson
    Write-Host "Generated color variant C$("{0:D2}" -f $idx) ($preset)"
}

Write-Host "Done. Generated $($PresetHex.Count) color variants."
