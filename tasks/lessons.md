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

## Git / version control
- Commit identity is repo-local (`git config user.email rourke9001@gmail.com`) so
  the personal account is used without touching global/work config.
- **Don't embed double-quotes inside a `git commit -m @'...'@` here-string** in
  PowerShell 5.1 — its native-argument quoting splits the message and git misreads
  tokens as pathspecs (`fatal: '5:' is outside repository`). Write the message to a
  file with `Set-Content -Encoding ascii` and use `git commit -F <file>` instead.
- Managing the MySQL service (`net start/stop MySQL97`) needs an elevated shell;
  a normal shell returns `System error 5: Access is denied`.
