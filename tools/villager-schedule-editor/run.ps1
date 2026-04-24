# Run from: tools/villager-schedule-editor
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$env:PYTHONPATH = Join-Path $PSScriptRoot "src"
python -m schedule_editor.main @args
