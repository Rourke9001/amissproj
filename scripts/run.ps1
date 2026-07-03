# Launches the game from the packaged uber-jar amiss-swing\target\AmissProj.jar.
# Requires: (1) scripts\build.ps1 has been run, (2) MySQL is running and
# bootstrapped (see db\bootstrap.sql / SETUP.md); the schema itself is
# migrated automatically at launch by Flyway.
$ErrorActionPreference = 'Stop'
$proj = Split-Path $PSScriptRoot -Parent
$jar  = Join-Path $proj 'amiss-swing\target\AmissProj.jar'
if (-not (Test-Path $jar)) { throw "Jar not found - run scripts\build.ps1 first: $jar" }
& java -jar $jar
