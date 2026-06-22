# Compiles AmissProj from src\ into build\classes and repackages dist\AmissProj.jar.
# No NetBeans or Ant required. Run from anywhere:  powershell -File scripts\build.ps1
$ErrorActionPreference = 'Stop'
$proj = Split-Path $PSScriptRoot -Parent

# Resolve the real JDK bin dir (the PATH 'javapath' shim exposes java/javac but not jar).
# cmd /c keeps java's stderr as plain text (PowerShell would otherwise wrap it as an error).
$props = cmd /c "java -XshowSettings:properties -version 2>&1"
$javaHome = (($props | Where-Object { $_ -match 'java\.home' }) -split '=', 2)[1].Trim()
if (-not $javaHome) { throw "Could not determine java.home - is a JDK on PATH?" }
$javac = Join-Path $javaHome 'bin\javac.exe'
$jar   = Join-Path $javaHome 'bin\jar.exe'

$out  = Join-Path $proj 'build\classes'
$libs = (Get-ChildItem (Join-Path $proj 'dist\lib\*.jar') | ForEach-Object FullName) -join ';'
New-Item -ItemType Directory -Force $out | Out-Null

$src = Get-ChildItem (Join-Path $proj 'src') -Recurse -Filter *.java | ForEach-Object FullName
Write-Host "JDK: $javaHome"
Write-Host "Compiling $($src.Count) source files..."
& $javac -cp $libs -d $out $src
if ($LASTEXITCODE -ne 0) { throw "Build FAILED (javac exit $LASTEXITCODE)" }
Write-Host "Compiled OK -> $out"

# Repackage the runnable jar so 'java -jar dist\AmissProj.jar' also works.
# (jar re-wraps the long Class-Path line to the manifest 72-col spec for us.)
$cp = (Get-ChildItem (Join-Path $proj 'dist\lib\*.jar') | ForEach-Object { 'lib/' + $_.Name }) -join ' '
$manifest = Join-Path $proj 'build\manifest.mf'
@(
    'Manifest-Version: 1.0'
    'Main-Class: amiss.LoginGUI'
    "Class-Path: $cp"
    ''
) | Set-Content -Encoding ASCII $manifest
& $jar cfm (Join-Path $proj 'dist\AmissProj.jar') $manifest -C $out .
if ($LASTEXITCODE -ne 0) { throw "Packaging FAILED (jar exit $LASTEXITCODE)" }
Write-Host "Packaged   -> $(Join-Path $proj 'dist\AmissProj.jar')"
