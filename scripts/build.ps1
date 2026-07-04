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

# mvnw needs JAVA_HOME, and the build targets Java 21 — if the shell's JAVA_HOME
# is missing or older than 21, point it at a 21+ JDK for this build.
. (Join-Path $PSScriptRoot 'find-java21.ps1')
$env:JAVA_HOME = Split-Path (Split-Path (Find-Java21) -Parent) -Parent

& (Join-Path $proj 'mvnw.cmd') -f (Join-Path $proj 'pom.xml') clean package
if ($LASTEXITCODE -ne 0) { throw "Build FAILED (mvnw exit $LASTEXITCODE)" }
Write-Host "Packaged -> $(Join-Path $proj 'amiss-swing\target\AmissProj.jar')"
