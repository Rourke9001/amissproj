# Launches the game from compiled classes in build\classes.
# Requires: (1) scripts\build.ps1 has been run, (2) MySQL is running with the
# amissdb schema loaded (see db\setup.sql / SETUP.md).
$ErrorActionPreference = 'Stop'
$proj = Split-Path $PSScriptRoot -Parent
$out  = Join-Path $proj 'build\classes'
$libs = (Get-ChildItem (Join-Path $proj 'dist\lib\*.jar') | ForEach-Object FullName) -join ';'

& java -cp "$out;$libs" amiss.LoginGUI
