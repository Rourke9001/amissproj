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

## Build / resources
- **`javac` does not copy non-`.java` resources.** Images under `src` won't reach
  `build/classes` (or the jar) unless the build copies them. `scripts/build.ps1` now
  cleans `build/classes` then copies every non-`.java` file from `src`, preserving the
  package path, so `getClass().getResource("/amiss/resources/...")` works from both the
  classes dir and the jar. Load assets via the classpath, never absolute file paths.
- **The `org.netbeans cannot be resolved` red errors were an IDE-classpath issue, not a
  bug.** `AbsoluteLayout`/`AbsoluteConstraints` live in the committed
  `dist/lib/AbsoluteLayout.jar` and compile fine. The fix is `.vscode/settings.json`
  with `java.project.referencedLibraries: ["dist/lib/*.jar"]` (folder-based "invisible
  project" — there's no pom/gradle to infer the classpath).
- **`.vscode/` was git-ignored**, which would have hidden the classpath fix from the
  repo. Keep ignoring per-user state but track shared config:
  `.vscode/*` then `!.vscode/settings.json` / `!.vscode/extensions.json`.
- **NetBeans `.form` files are designer-only** (never compiled or loaded). Once GUIs are
  hand-edited outside NetBeans they're dead weight — and dangerous, since reopening in
  NetBeans regenerates `initComponents()` from the `.form` and clobbers hand edits.

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

## Phase 1 / backend hardening
- **BCrypt hashes are always 60 chars** — the `password` column must be
  `VARCHAR(60)`+ or hashes silently truncate. `setup.sql` widens it with an
  idempotent `ALTER` for DBs created before hashing.
- **Adding deps without Maven:** drop the jar in `dist/lib/` (fetched from Maven
  Central) — `scripts/build.ps1` globs `dist/lib/*.jar` onto both the compile
  classpath and the jar `Class-Path`, so no build-script change is needed. Phase 1
  added slf4j-api 2.0.13, logback-core/classic 1.3.14 (the Java-8 line — 1.4+
  needs Java 11) and jbcrypt 0.4.
- **The IDE (Java extension) lags behind newly-added jars** — `org.slf4j` /
  `org.mindrot` show "cannot be resolved" until the language server reindexes, but
  `scripts/build.ps1` compiles fine. Trust the build, not the red squiggles (same
  root cause as the old `org.netbeans` classpath issue).
- **PowerShell 5.1 has no `<` input redirection** — `mysql -u root -p < file.sql`
  is a parser error. Use the Bash tool (Git Bash) or `cmd /c "mysql ... < file.sql"`.
- **The least-privilege `amiss` user has no DELETE/DDL** — cleaning up test rows
  needs root, not the app connection. (Verified at runtime: DELETE as `amiss` is
  denied, which is the point.)
- **try-with-resources + a returning catch can make the trailing `return` unreachable.**
  Converting `try { if (rs.next()) return x; } catch { return s; } return s;` to
  `try { return db.queryForX(...); } catch { return s; }` — drop the now-dead
  trailing `return`, or javac errors with "unreachable statement".
