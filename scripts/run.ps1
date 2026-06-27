# Launches the game from the packaged uber-jar target\AmissProj.jar.
# Requires: (1) scripts\build.ps1 has been run, (2) MySQL is running with the
# amissdb schema loaded (see db\setup.sql / SETUP.md).
$ErrorActionPreference = 'Stop'
$proj = Split-Path $PSScriptRoot -Parent
$jar  = Join-Path $proj 'target\AmissProj.jar'
if (-not (Test-Path $jar)) { throw "Jar not found - run scripts\build.ps1 first: $jar" }
& java -jar $jar
