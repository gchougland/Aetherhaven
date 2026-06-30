# Moves core UI lang keys into core bundles, rep-reward item strings into ReputationUnlocks pack.
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$coreLangRoot = Join-Path $root "src\main\resources\Server\Languages"
$jewelryLangRoot = Join-Path $root "subplugin-assets\Jewelry\Server\Languages"
$repLangRoot = Join-Path $root "subplugin-assets\ReputationUnlocks\Server\Languages"
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

function Write-LangFile {
    param([string]$Path, [string[]]$Lines)
    [System.IO.File]::WriteAllLines($Path, $Lines, $utf8NoBom)
}

$repItemPrefixes = @(
    "items.Aetherhaven_Firewood.",
    "items.Aetherhaven_Root_Remover.",
    "items.Aetherhaven_Growth_Serum.",
    "items.Aetherhaven_Hunting_Knife.",
    "items.Aetherhaven_Geode_Anvil.",
    "items.Aetherhaven_Purification_Powder.",
    "items.Aetherhaven_Gaias_Draught.",
    "items.Aetherhaven_Shard_Of_Gaia.",
    "items.Aetherhaven_Verdant_Catalyst.",
    "items.Aetherhaven_Sprinkler_"
)
$repExtraPrefixes = @("interactionHints.purifySpawn", "aetherhaven.gaiadraught.")

function Split-LinesByPrefix {
    param([string[]]$Lines, [string[]]$Prefixes)
    $matched = @()
    $rest = @()
    foreach ($line in $Lines) {
        if ($line.Trim() -eq "") { $rest += $line; continue }
        $hit = $false
        foreach ($p in $Prefixes) {
            if ($line.StartsWith($p)) { $hit = $true; break }
        }
        if ($hit) { $matched += $line } else { $rest += $line }
    }
    return @{ Matched = $matched; Rest = $rest }
}

function Take-PrefixBlock {
    param([string[]]$Lines, [string]$Prefix)
    $matched = @()
    $rest = @()
    foreach ($line in $Lines) {
        if ($line.StartsWith($Prefix)) { $matched += $line } else { $rest += $line }
    }
    return @{ Matched = $matched; Rest = $rest }
}

Get-ChildItem -Path $jewelryLangRoot -Directory | ForEach-Object {
    $locale = $_.Name
    $jewelryGeodePath = Join-Path $_.FullName "aetherhaven_jewelry_geode.lang"
    if (-not (Test-Path -LiteralPath $jewelryGeodePath)) { return }

    $jewelryLines = Get-Content -LiteralPath $jewelryGeodePath -Encoding UTF8
    $gaia = Take-PrefixBlock -Lines $jewelryLines -Prefix "aetherhaven.ui.gaiaStatue."
    $prof = Take-PrefixBlock -Lines $gaia.Rest -Prefix "aetherhaven.profession.kind."
    $repFromJewelry = Split-LinesByPrefix -Lines $prof.Rest -Prefixes ($repItemPrefixes + $repExtraPrefixes)
    Write-LangFile -Path $jewelryGeodePath -Lines $repFromJewelry.Rest

    $coreLocale = Join-Path $coreLangRoot $locale
    if (-not (Test-Path -LiteralPath $coreLocale)) { return }

    $uiShellPath = Join-Path $coreLocale "aetherhaven_ui_shell.lang"
    if ((Test-Path -LiteralPath $uiShellPath) -and $gaia.Matched.Count -gt 0) {
        $shell = Get-Content -LiteralPath $uiShellPath -Encoding UTF8
        if (-not ($shell -match 'aetherhaven\.ui\.gaiaStatue\.title')) {
            $shell += ""
            $shell += "# === UI: Statue of Gaia (revival panel) ==="
            $shell += $gaia.Matched
            Write-LangFile -Path $uiShellPath -Lines $shell
        }
    }

    $townPath = Join-Path $coreLocale "aetherhaven_town.lang"
    if ((Test-Path -LiteralPath $townPath) -and $prof.Matched.Count -gt 0) {
        $town = Get-Content -LiteralPath $townPath -Encoding UTF8
        if (-not ($town -match 'aetherhaven\.profession\.kind\.elder')) {
            $town += ""
            $town += "# === Villager profession labels (core UI) ==="
            $town += $prof.Matched
            Write-LangFile -Path $townPath -Lines $town
        }
    }

    $repOutDir = Join-Path $repLangRoot $locale
    New-Item -ItemType Directory -Force -Path $repOutDir | Out-Null
    $repLines = @("# Reputation-gated reward items (ReputationUnlocks pack)", "")

    $itemsPath = Join-Path $coreLocale "aetherhaven_items.lang"
    if (Test-Path -LiteralPath $itemsPath) {
        $split = Split-LinesByPrefix -Lines (Get-Content -LiteralPath $itemsPath -Encoding UTF8) -Prefixes $repItemPrefixes
        if ($split.Matched.Count -gt 0) {
            $repLines += $split.Matched
            Write-LangFile -Path $itemsPath -Lines $split.Rest
        }
    }

    $tailPath = Join-Path $coreLocale "aetherhaven_ui_journal_items_tail.lang"
    if (Test-Path -LiteralPath $tailPath) {
        $split = Split-LinesByPrefix -Lines (Get-Content -LiteralPath $tailPath -Encoding UTF8) -Prefixes @("items.Aetherhaven_Sprinkler_")
        if ($split.Matched.Count -gt 0) {
            $repLines += $split.Matched
            Write-LangFile -Path $tailPath -Lines $split.Rest
        }
    }

    if ($repFromJewelry.Matched.Count -gt 0) { $repLines += $repFromJewelry.Matched }
    Write-LangFile -Path (Join-Path $repOutDir "aetherhaven_reputation_unlocks.lang") -Lines $repLines
}

Write-Host "Lang split complete."
