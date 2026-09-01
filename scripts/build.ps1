# Flapforge — build script (Windows PowerShell).
#
# Compiles the project with -Xlint:all -Werror, runs the default test suite and
# produces the self-contained jar at build\libs\flapforge-<version>-all.jar.
# Any extra arguments are forwarded to Gradle (e.g. scripts\build.ps1 --offline).
#
# Usage: scripts\build.ps1 [gradle options]
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
& .\gradlew.bat build fatJar @args
exit $LASTEXITCODE
