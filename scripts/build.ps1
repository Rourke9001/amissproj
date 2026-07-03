# Builds the AmissProj reactor with Maven (via the committed Maven Wrapper);
# the Swing client packages as the single runnable uber-jar
# amiss-swing\target\AmissProj.jar. No global Maven install is required: the
# wrapper downloads a pinned Maven on first run. Needs a JDK 21+.
#
#   powershell -File scripts\build.ps1
#
# This is a thin convenience wrapper around `mvnw clean package`; the real build
# definition lives in the pom.xml files (reactor root + one per module).
$ErrorActionPreference = 'Stop'
$proj = Split-Path $PSScriptRoot -Parent
& (Join-Path $proj 'mvnw.cmd') -f (Join-Path $proj 'pom.xml') clean package
if ($LASTEXITCODE -ne 0) { throw "Build FAILED (mvnw exit $LASTEXITCODE)" }
Write-Host "Packaged -> $(Join-Path $proj 'amiss-swing\target\AmissProj.jar')"
