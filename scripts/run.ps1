# Launches the game from the packaged uber-jar amiss-swing\target\AmissProj.jar.
# Requires: (1) scripts\build.ps1 has been run, (2) MySQL is running and
# bootstrapped (see db\bootstrap.sql / SETUP.md); the schema itself is
# migrated automatically at launch by Flyway.
$ErrorActionPreference = 'Stop'
$proj = Split-Path $PSScriptRoot -Parent
$jar  = Join-Path $proj 'amiss-swing\target\AmissProj.jar'
if (-not (Test-Path $jar)) { throw "Jar not found - run scripts\build.ps1 first: $jar" }

# The build targets Java 21, but PATH `java` / JAVA_HOME may still point at an
# older JDK (e.g. the Oracle jdk-20 shim), which dies with
# UnsupportedClassVersionError. Use JAVA_HOME only if it is 21+, else the newest
# 21+ Temurin install, else fall back to PATH and let it fail loudly.
. (Join-Path $PSScriptRoot 'find-java21.ps1')
$java = Find-Java21
& $java -jar $jar
