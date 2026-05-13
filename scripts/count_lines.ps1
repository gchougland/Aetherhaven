<#
.SYNOPSIS
  Counts lines in the repository, with JSON reported separately from other files.

.DESCRIPTION
  Walks the repo from the root (parent of scripts/), skips common generated/vendor
  directories, skips known binary extensions, and sums line counts. JSON (*.json)
  is tallied on its own; everything else counted is reported as non-JSON lines.
  *.lang translation files are excluded from all totals.

.PARAMETER Root
  Repository root. Defaults to the parent directory of this script.

.EXAMPLE
  .\scripts\count_lines.ps1
#>
param(
    [string] $Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ExcludeDirNames = [System.Collections.Generic.HashSet[string]]::new(
    [string[]]@(
        ".git", ".gradle", "build", "out", "bin", ".idea", "node_modules",
        "run", ".cursor", "__pycache__", ".vs"
    ),
    [System.StringComparer]::OrdinalIgnoreCase
)

$BinaryExtensions = [System.Collections.Generic.HashSet[string]]::new(
    [string[]]@(
        ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".bmp", ".tga",
        ".jar", ".class", ".zip", ".7z", ".rar", ".exe", ".dll", ".so", ".dylib",
        ".woff", ".woff2", ".ttf", ".otf", ".eot", ".mp3", ".wav", ".ogg",
        ".bin"
    ),
    [System.StringComparer]::OrdinalIgnoreCase
)

$SkipLineCountExtensions = [System.Collections.Generic.HashSet[string]]::new(
    [string[]]@(".lang"),
    [System.StringComparer]::OrdinalIgnoreCase
)

function Test-ExcludedDirectory {
    param([string] $FullPath)
    $dir = [System.IO.Path]::GetFileName($FullPath.TrimEnd('\', '/'))
    return $ExcludeDirNames.Contains($dir)
}

function Get-LineCount {
    param([string] $FilePath)
    $n = 0
    $reader = $null
    try {
        $reader = [System.IO.StreamReader]::new($FilePath)
        while ($null -ne $reader.ReadLine()) { $n++ }
    }
    catch {
        return -1
    }
    finally {
        if ($null -ne $reader) { $reader.Dispose() }
    }
    return $n
}

$jsonLines = [long]0
$jsonFiles = 0
$otherLines = [long]0
$otherFiles = 0
$skippedBinary = 0
$skippedLang = 0
$skippedErrors = 0

$stack = [System.Collections.Stack]::new()
$stack.Push($Root)

while ($stack.Count -gt 0) {
    $current = [string]$stack.Pop()
    foreach ($item in [System.IO.Directory]::EnumerateFileSystemEntries($current)) {
        $attr = [System.IO.File]::GetAttributes($item)
        if (($attr -band [System.IO.FileAttributes]::Directory) -ne 0) {
            if (-not (Test-ExcludedDirectory -FullPath $item)) {
                $stack.Push($item)
            }
            continue
        }

        $ext = [System.IO.Path]::GetExtension($item)
        if ($BinaryExtensions.Contains($ext)) {
            $skippedBinary++
            continue
        }

        if ($SkipLineCountExtensions.Contains($ext)) {
            $skippedLang++
            continue
        }

        $lines = Get-LineCount -FilePath $item
        if ($lines -lt 0) {
            $skippedErrors++
            continue
        }

        if ($ext -eq ".json") {
            $jsonLines += $lines
            $jsonFiles++
        }
        else {
            $otherLines += $lines
            $otherFiles++
        }
    }
}

$totalLines = $jsonLines + $otherLines
$totalFiles = $jsonFiles + $otherFiles

Write-Host "Root: $Root"
Write-Host ""
Write-Host "Non-JSON lines : $($otherLines.ToString('N0'))  ($otherFiles files)"
Write-Host "JSON lines     : $($jsonLines.ToString('N0'))  ($jsonFiles files)"
Write-Host "Total lines    : $($totalLines.ToString('N0'))  ($totalFiles files)"
Write-Host ""
Write-Host "Skipped binary extensions: $skippedBinary files"
Write-Host "Skipped .lang files        : $skippedLang files"
if ($skippedErrors -gt 0) {
    Write-Host "Skipped read errors      : $skippedErrors files"
}
