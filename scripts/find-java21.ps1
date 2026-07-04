# Shared helper: locate a Java 21+ java.exe (dot-source, then call Find-Java21).
# The build targets Java 21, but this machine's PATH java / JAVA_HOME may still
# point at an older JDK, which fails with UnsupportedClassVersionError.
# Preference order: JAVA_HOME (if 21+) > newest 21+ JDK under
# C:\Program Files\Eclipse Adoptium > bare `java` from PATH (fails loudly).

function Get-JdkMajorVersion([string]$jdkHome) {
    # Every modern JDK ships a `release` file with JAVA_VERSION="21.0.11" etc.
    $release = Join-Path $jdkHome 'release'
    if (Test-Path $release) {
        $m = Select-String -Path $release -Pattern 'JAVA_VERSION="(\d+)'
        if ($m) { return [int]$m.Matches[0].Groups[1].Value }
    }
    return 0
}

function Find-Java21 {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe')) -and
            (Get-JdkMajorVersion $env:JAVA_HOME) -ge 21) {
        return Join-Path $env:JAVA_HOME 'bin\java.exe'
    }
    $temurin = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue |
        Where-Object { (Get-JdkMajorVersion $_.FullName) -ge 21 } |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($temurin) { return Join-Path $temurin.FullName 'bin\java.exe' }
    return 'java'
}
