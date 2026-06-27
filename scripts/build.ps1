# Builds AmissProj with Maven (via the committed Maven Wrapper) into the single
# runnable uber-jar target\AmissProj.jar. No global Maven install is required:
# the wrapper downloads a pinned Maven on first run.
#
#   powershell -File scripts\build.ps1
#
# This is a thin convenience wrapper around `mvnw clean package`; the real build
# definition lives in pom.xml.
$ErrorActionPreference = 'Stop'
$proj = Split-Path $PSScriptRoot -Parent
& (Join-Path $proj 'mvnw.cmd') -f (Join-Path $proj 'pom.xml') clean package
if ($LASTEXITCODE -ne 0) { throw "Build FAILED (mvnw exit $LASTEXITCODE)" }
Write-Host "Packaged -> $(Join-Path $proj 'target\AmissProj.jar')"
