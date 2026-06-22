# Lessons

Patterns to remember for this project (per CLAUDE.md self-improvement loop).
Add to this after any correction or non-obvious gotcha.

## Environment / tooling
- **JDK 20 is installed; project targets Java 1.8.** Source compiles fine on 20
  (only a benign `JPasswordField.getText()` deprecation note).
- **`jar.exe` is NOT on PATH**, even though `java`/`javac` are. Windows' Oracle
  "javapath" shim only exposes java/javac/javaw. Resolve the real JDK bin from
  `java.home` (see `scripts/build.ps1`) and call `jar.exe` by full path.
- **PowerShell 5.1 + `2>&1` on a native exe** wraps stderr as ErrorRecords and,
  with `$ErrorActionPreference='Stop'`, aborts the script. Capture such output via
  `cmd /c "java ... 2>&1"` to keep it as plain text.

## Database
- MySQL **9.x removed `mysql_native_password`** and defaults to `caching_sha2_password`;
  the original **Connector/J 5.1.22 (2012) cannot authenticate** to it. Modern
  Connector/J (8/9) is mandatory.
- The original `tbljobs` / `tblhelp` data was never in source control (it lived in
  NetBeans' managed DB). It is **reconstructed** in `db/setup.sql` — job names and
  `location` strings are taken verbatim from the code (must match exactly); the
  numeric columns are tunable design values.
