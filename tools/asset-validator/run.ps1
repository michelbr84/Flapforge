# Validates assets/manifest.json. Every argument is forwarded to AssetValidator.
#   tools\asset-validator\run.ps1 --quiet
$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Push-Location $root
try {
    $toolArgs = $args -join ' '
    & .\gradlew.bat assetValidator "-PtoolArgs=$toolArgs"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}
