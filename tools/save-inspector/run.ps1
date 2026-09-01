# Prints and validates a profile directory. Every argument is forwarded to SaveInspector.
#   tools\save-inspector\run.ps1 --home $env:APPDATA\Flapforge
$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Push-Location $root
try {
    $toolArgs = $args -join ' '
    & .\gradlew.bat saveInspector "-PtoolArgs=$toolArgs"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}
