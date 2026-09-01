# Runs the balancing simulation. Every argument is forwarded to BalancingSim.
#   tools\balancing\run.ps1 --seeds 200 --skill average --csv build\balancing.csv
$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Push-Location $root
try {
    $toolArgs = $args -join ' '
    & .\gradlew.bat balancing "-PtoolArgs=$toolArgs"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}
