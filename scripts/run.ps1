# Flapforge — run from source (Windows PowerShell).
#
# Starts the game through Gradle. Every argument is passed to the game
# unchanged, so all launch flags work here:
#
#   --seed N           fixed RNG seed for a reproducible run
#   --world ID         start in the given world (e.g. storm_sky)
#   --bird ID          start with the given bird
#   --tier ID          difficulty tier (normal | hard | nightmare)
#   --scale N          initial window scale factor (integer)
#   --fullscreen       start in borderless fullscreen
#   --no-audio         disable the audio backend
#   --home DIR         use DIR instead of the default save/settings directory
#   --headless-run N   simulate N frames without a window and print a summary line
#                      (the state hash compared by CI arrives with the simulation)
#   --reset-save       delete the save file (a backup is kept) and start fresh
#   --lang CODE        UI language (auto | en | pt_BR)
#   --no-window        run without a window
#   --help, -h         print the usage text
#
# Flags whose feature has not landed yet are documented in docs/DEVELOPMENT.md.
#
# Usage: scripts\run.ps1 [flags]
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
$gameArgs = ($args | ForEach-Object { [string]$_ }) -join ' '
& .\gradlew.bat run "--args=$gameArgs"
exit $LASTEXITCODE
