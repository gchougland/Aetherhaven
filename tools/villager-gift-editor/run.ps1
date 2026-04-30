# Run from: tools/villager-gift-editor
# Same launcher pattern as tools/villager-schedule-editor/run.ps1
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$env:PYTHONPATH = Join-Path $PSScriptRoot "src"
python -m gift_editor.main @args
