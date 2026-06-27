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

## Phase 2 / Maven build migration
- **No global Maven on this machine (only the JDK).** Don't assume `mvn` exists — add the
  **Maven Wrapper** so the build is self-contained. Without `mvn` you can't run
  `wrapper:wrapper`, so bootstrap it by unzipping the official
  `org.apache.maven.wrapper:maven-wrapper-distribution:<ver>-bin.zip` (mvnw, mvnw.cmd,
  `.mvn/wrapper/maven-wrapper.jar`) and hand-writing `maven-wrapper.properties`
  (`distributionUrl` + `wrapperUrl`). First `./mvnw` downloads Maven into `~/.m2/wrapper`.
- **Shading an uber-jar silently breaks `ServiceLoader` discovery.** SLF4J 2.x finds its
  backend via `META-INF/services/org.slf4j.spi.SLF4JServiceProvider` and JDBC finds the
  driver via `META-INF/services/java.sql.Driver`. maven-shade overwrites same-named files
  unless you add `ServicesResourceTransformer` to **merge** them — otherwise logging goes
  no-op and/or the MySQL driver isn't found, with no build error. Pair it with
  `ManifestResourceTransformer` for the `Main-Class`.
- **Shade vs signed / multi-release jars.** Exclude `META-INF/*.SF|*.DSA|*.RSA` (else the
  JVM rejects the repackaged jar: "Invalid signature file digest") and `module-info.class`
  (multi-release deps like mysql-connector-j) in a shade `<filter>`.
- **Vendoring a jar that isn't on Maven Central** (NetBeans `AbsoluteLayout`): install it
  into a *project-local* repo with `install:install-file ... -DlocalRepositoryPath=vendor-repo`
  (this does NOT pollute `~/.m2`, so the build genuinely tests resolution from the repo),
  and reference it via `<url>${project.baseUri}vendor-repo</url>` — `project.baseUri` emits
  a valid `file:///C:/...` URL on Windows. Delete the `_remote.repositories` and
  `maven-metadata-local.xml` files it drops; they're local-repo bookkeeping and aren't
  needed to resolve an exact pinned version from a file repo.
- **Maven resource path must match `getResource`.** Code loads art from
  `/amiss/resources/...`, so assets belong at `src/main/resources/amiss/resources/`
  (NOT `src/main/resources/amiss/`); `application.properties`/`logback.xml` sit at the
  resources root. Easy to drop a directory level when moving `src/amiss/resources`.
- **`beansbinding` was a dead NetBeans default dep** (zero imports) — dropped it rather
  than porting it to Maven. Grep for actual imports before re-declaring inherited jars.

## Phase 2 / service layer (PR B)
- **The IDE shows false-positive errors for the Maven `src/main/java` layout.** The VSCode
  Java extension (folder-based "invisible project") reports *"The declared package
  `amiss.service` does not match the expected package `main.java.amiss.service`"* and
  *"import amiss cannot be resolved" / "TimeService cannot be resolved to a type"* for
  brand-new/edited files until it reindexes. None of it is real — `./mvnw clean package`
  compiles clean. Same root cause as the old `org.netbeans`/`org.slf4j` squiggles. **Trust
  the build, not the diagnostics**; don't "fix" code to satisfy them.
- **Behaviour-preserving Swing refactor → keep `initComponents()` byte-identical.** Reopening
  a GUI in NetBeans regenerates `initComponents()` from the `.form`, so never hand-edit it.
  Prove you didn't: `git diff main --unified=0 -- 'src/main/java/amiss/*GUI.java' | grep -E
  'AbsoluteConstraints|\.setBounds|GroupLayout|initComponents|new javax\.swing\.'` must be
  **empty**. Edit only the field decls, constructor and button handlers.
- **Each GUI's generated `main()` is a dead stub** (the real entry point is `amiss.LoginGUI`;
  the shaded jar's `Main-Class` is set from pom `main.class`). Those stubs are the only thing
  forcing the per-screen `static User user; static DB db;` — delete the stub and the fields
  can become instance. When deleting the stub by exact match, note the source files are **LF**
  and the NetBeans look-and-feel comment line ending in `.../plaf.html ` carries a **trailing
  space** (confirm bytes with `sed -n … | cat -A`, not the editor view, which hides it).
- **Decouple "rules write to the UI" by returning a result object, not by passing widgets in.**
  Methods that mutated three widgets (`workMain`/`eatMain`) now return an immutable
  `ActionResult { message, timer, money }` (null = leave that label unchanged) and the screen
  applies it. Methods that only wrote a `"\nfailed…"` error string to a text area now **log via
  SLF4J** instead (matching the existing `CalcDuration` precedent) — normal play is unaffected
  since those branches only fire on a DB exception, and nothing branches on the error text.
- **Wire injected services through one composition root.** A `GameServices(User, DB)` builds the
  repositories once and constructs the services in dependency order (education → job; time, food;
  then stats). Each screen holds a single `GameServices` instead of `new`-ing five rules classes,
  so the dependency graph lives in exactly one place.

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
