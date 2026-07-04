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

## Phase 2 / JUnit 5 + Mockito tests
- **Default Maven (pre-3.x Surefire) does not run JUnit 5.** Pin
  `maven-surefire-plugin` 3.x explicitly (we use 3.2.5) so the JUnit Platform engine is
  discovered. Maven 3.9.9's default is already 3.x, but pinning makes the test run
  reproducible across Maven versions / CI runners. Align the Jupiter artifacts with the
  `org.junit:junit-bom` import rather than versioning each one.
- **`maven.compiler.release=8` is fine with Mockito 5 / JUnit 5.** `--release 8` only
  restricts the *JDK platform* API to 8; it does not stop compiling test code against
  third-party jars whose bytecode is newer (Mockito 5 is Java 11 bytecode). Mockito 5
  needs a Java 11+ *runtime*, which is satisfied because tests run on the installed JDK 20
  even though `main` targets Java 8. So no need to drop to Mockito 4.
- **`JAVA_HOME` is not set in this shell, and PATH `java` is the Oracle javapath shim**
  (`C:\Program Files\Common Files\Oracle\Java\javapath`), whose parent is NOT a JDK home —
  so `mvnw` aborts with "JAVA_HOME not found". Set it to the real JDK for the command:
  `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-20'` before `.\mvnw.cmd`.
- **Run `mvnw` via the PowerShell tool, not `cmd /c "mvnw.cmd ..."` from Git Bash** — the
  latter just printed the cmd banner and ran nothing here. `.\mvnw.cmd test` in PowerShell
  works (the tool captures stderr for you; don't add `2>&1`).
- **MockitoExtension defaults to STRICT_STUBS**: every `when(...)` stub must actually be
  used by that test or it throws `UnnecessaryStubbingException`. Stub only what a given
  path touches (e.g. the "Not Enough Time" branch must not stub `getCash`/`getSalary`,
  because it short-circuits before reaching them). Multiple *uses* of one stub are fine.
- **You can't mock `toString()`/`equals()`/`hashCode()` with Mockito.** `StatsService.workMain`
  calls `job.toString()`, so the work/eat orchestration is tested with **real** collaborator
  services (`JobService`/`TimeService`/`FoodService`) built over **mocked repositories**,
  mirroring the `GameServices` wiring — not with mocked services.
- **Stub a void repo method to throw with `doThrow(...).when(mock).method(args)`** (not
  `when(...).thenThrow`), to cover the services' `catch (SQLException)` fallback branches
  (e.g. `buy` → "failed to purchase", `setCash` → "failed to update cash").
- **Silence expected-exception log noise with `src/test/resources/logback-test.xml`.**
  Tests that make repos throw `SQLException` hit the services' `log.warn("...", ex)` lines,
  which dump stack traces all over Surefire output. Logback prefers `logback-test.xml` on
  the test classpath over the main `logback.xml`, so set `<logger name="amiss" level="OFF"/>`
  there — the main logging config is untouched. (Verified: 0 stack traces, build still green.)
- **Characterization-test findings (documented, not fixed — fixing changes behaviour):**
  (1) `TimeService.getNewTime` only zero-pads `mins == 0` (→ `"00"`), so a single-digit
  minute renders unpadded — 2h 5m is `"2:5"`, not `"2:05"`. (2) `TimeService.getMulti`'s
  third `else if` is **dead code**: its condition (`|oldCol-col|==3 && row!=oldRow` with both
  rows interior) is a strict subset of the first `if`, which always fires first — so 100%
  branch coverage of `getMulti` is unreachable, and that's expected, not a gap to chase.

## Phase 3 / board & turn-timer rework (PR2)
- **Model the loop as a ring, not a grid + special-cases.** `domain.board.Board` holds the 13
  stops in clockwise order; movement cost = `min(cw, ccw)` ring steps. This deleted `TwoDGrid`
  and `TimeService.getMulti`'s hard-coded 4×4 wrap tolls (incl. its dead branch) in one move and
  scales to any stop count. The 5×4 render keeps `(row,col)` in `xpos/ypos` (no DB migration) and
  the constructor clamps any stale/invalid saved cell back to home `(0,0)`.
- **Relabel the time unit instead of rescaling.** The old model's `getNewTime(6)` for work etc.
  already matched the reference's 6h once you read the stored `time` as **hours** not 10-min
  blocks. So the timer rework was: budget 600/720→**60/72**, format `"H:MM"`→`"Nh"` (round-end
  `"0:00"`→`"0h"`), keep `"Not Enough Time"`, add the **+2h building-entry** on each move, and
  re-cost only the actions that differ (apply-job 1→4, shopping 1→0). New-user seed 720→72 in
  `JdbcUserRepository` **and** `db/setup.sql`.
- **Rename sync is load-bearing.** A work screen shows its Work button only if
  `loc.equals(<name>)` matches `tbljobs.location` verbatim. Renaming Fast Food/Appliance/Market
  meant editing the GUI `loc.equals` **and** the SQL in lockstep, then reloading `setup.sql` as
  root. Verified against live MySQL with a headless probe (Cook→"Monolith Burgers", etc.).
- **`sed` an SQL apostrophe with a double-quoted program.** `"Black's Market"` in SQL is
  `'Black''s Market'`; run `sed -i "s/'Market'/'Black''s Market'/g"` (double quotes so the
  single quotes are literal — no `$`/backtick in the expression to worry about).
- **Verify a Swing change by constructing the frame headlessly-ish.** A tiny probe on the shaded
  jar's classpath (`new MainGameGUI(user, services)` then `dispose()`, guarded for
  `HeadlessException`) exercises the real 5×4 board-building loop against live data without
  needing to click — catches NPEs/clamp/wiring bugs that unit tests on `Board` alone can't.

## Phase 3 / clean-architecture refactor (PR1)
- **Bulk package restructure: `git mv` + a path-derived `package` sweep.** After moving files,
  rewrite every declaration in one pass — for each `*.java`, derive the package from its
  directory (`dirname | sed 's|src/main/java/||; s|/|.|g'`) and `sed -i -E "0,/^package .*;/s//package X;/"`.
  Fixes ~40 files' packages deterministically; then only class-renames/imports need real logic.
- **Ports keep the test mocks valid for free.** The service tests `@Mock` the repository *type* and
  pass it to the service constructor. Turning each repo into an interface (`amiss.application.port`)
  with a `Jdbc*` adapter means Mockito now mocks the **interface** — still assignable, so all 87
  tests compiled with only an import change. Keep `throws SQLException` on the ports so the services'
  existing `catch` branches (and the tests asserting them) stay byte-valid; map it to a neutral
  exception later, not now.
- **`.form` files are gone, so `amiss.Assets.icon(...)` FQNs inside `initComponents()` are safe to
  rewrite.** Moving `Assets` to `presentation.assets` required editing those fully-qualified calls;
  since NetBeans no longer regenerates `initComponents`, a pure reference update there is fine.
- **Shade transformers survive a Main-Class package move — but re-verify.** After moving
  `LoginGUI`, update pom `<main.class>` and confirm the shaded jar still has the right `Main-Class`
  **and** merged `META-INF/services/{java.sql.Driver,org.slf4j.spi.SLF4JServiceProvider}`
  (`unzip -p target/AmissProj.jar META-INF/...`). Smoke-launch logs `Jdbc - Connection Successful`.
- **The composition root belongs in `infrastructure` (`GameContext`), not `application`.** It's the
  one place that names concrete adapters + opens the connection; services take ports only, so a
  Spring/JPA swap is a new context + adapters with the rules untouched.

## Phase 3 / multi-module reactor + Java 21 (KAN-27 PR1)
- **Installing Temurin 21 via winget does NOT make it the default `java` — and the machine
  had `JAVA_HOME=C:\Program Files\Java\jdk-20` set globally.** The Oracle javapath shim
  (JDK 20) stays first on PATH, so a release=21 jar dies instantly with
  `UnsupportedClassVersionError` — silently when double-clicked (no console). Worse,
  "prefer JAVA_HOME" script logic faithfully picks the stale jdk-20. Fixes: user-level
  `JAVA_HOME` now points at Temurin 21 (user-level overrides machine-level), and
  `scripts/find-java21.ps1` picks a JDK by parsing its `release` file for
  `JAVA_VERSION="(\d+)` — never trust JAVA_HOME/PATH without checking the major version.
- **Never blanket `Stop-Process -Name java,javaw`.** The VS Code Java language server and
  SonarLint both run as `java.exe`; a name-based kill knifes the IDE tooling (it silently
  restarts, losing state). Match the target instead:
  `Get-CimInstance Win32_Process | Where CommandLine -match 'AmissProj\.jar'` → stop by Id.
- **Truncating a log file an open FileAppender holds writes garbage.** `Clear-Content` on
  `logs/amiss.log` while an (orphaned) JVM still held it made subsequent reads look empty.
  Kill the writer first, or `Remove-Item` the log so the appender recreates it.
- **Root-anchored `.gitignore` entries don't cover module dirs.** `/target/` only ignores
  the root target; after the reactor split it must be `target/` (unanchored) or every
  module's build output shows up as untracked.
- **`${project.baseUri}` resolves per-module.** The vendored-jar repository URL moved from
  `${project.baseUri}vendor-repo` (root pom) to `${project.baseUri}../vendor-repo` in
  amiss-swing — a parent-declared URL would point each module at a different directory.
- **Keep the logging backend out of the shared core module.** amiss-core exposes only
  slf4j-api (logback-classic in test scope for logback-test.xml); amiss-swing carries
  logback at runtime scope. This kills the nearest-wins clash with Spring Boot's managed
  logback before it exists, instead of version-pinning around it.
- **Invoking a single-module plugin goal reactor-wide re-runs it per module** — the
  Migrations workflow's `./mvnw flyway:migrate` needed `-pl amiss-core` once the flyway
  plugin/config moved into that module.

## Phase 3 / Spring Boot API (KAN-29)
- **Importing the Boot BOM (not the Boot parent) loses `-parameters`.** Spring MVC resolves
  `@PathVariable`/`@RequestParam` names reflectively; without the flag every request dies with
  "Name for argument ... not specified, and parameter name information not available via
  reflection". The Boot *parent* sets `maven.compiler.parameters`; a BOM-only build must add
  `<parameters>true</parameters>` to maven-compiler-plugin itself (done reactor-wide in the
  root pluginManagement).
- **`.\mvnw -f amiss-api spring-boot:run` resolves amiss-core from `~/.m2`, not the reactor.**
  After changing core, a single-module run can fail with `ClassNotFoundException` for
  brand-new core classes because the installed snapshot is stale. Run
  `.\mvnw -B install -DskipTests` first (or run the goal after a full `install`).
- **An unqualified `@WebMvcTest` instantiates every `@Controller` in the app** — adding the
  first real controller broke the pre-existing ProblemDetail contract test, whose slice
  suddenly needed the controller's beans. Always pin the slice:
  `@WebMvcTest(controllers = X.class)`.

## Phase 2 / GitHub Actions CI + Flyway migrations
- **`mvnw.cmd` mangles `&` inside `-D` args.** PowerShell passes the quoted arg fine, but
  the `.cmd` batch layer re-parses it: `-Dflyway.url=jdbc:...?useSSL=false&allow...` splits
  at each `&` ("'allowPublicKeyRetrieval' is not recognized..."), and Flyway sees user
  `'null'`. Locally, drop the query params (a plain `jdbc:mysql://host/db` URL connects
  fine); in CI the workflows run bash, where quoting works.
- **`git add` aborts the ENTIRE add if any pathspec matches nothing.** Adding a list that
  included the already-`git rm`-ed `db/setup.sql` failed with `fatal: pathspec ... did not
  match` and staged none of the other files — the commit then silently contained only the
  previously-staged deletion. Check `git status --short` before committing multi-path adds
  (recovered via `git reset <base>` + redo, since nothing was pushed).
- **Private-repo gotchas:** branch protection (rulesets AND classic) returns 403
  "Upgrade to GitHub Pro or make this repository public" on the free plan — the intended
  ruleset is committed at `.github/rulesets/protect-main.json`, apply when public/Pro.
  Actions/raw badge images also won't render in a private README (camo can't fetch them).
- **`mvnw` was committed non-executable (100644)** — Windows checkouts don't preserve the
  bit, and ubuntu runners then fail at `./mvnw`. Fix it in the index once:
  `git update-index --chmod=+x mvnw`.
- **Coverage badges can't live on a protected main** — CI publishes the JaCoCo SVGs to an
  orphan `badges` branch (`git switch --orphan` + force-push) and the README hotlinks the
  raw URLs; the badge job runs only on pushes to main.
- **Flyway 11 + MySQL 9.7 works with a warning** ("newer than this version of Flyway...
  latest supported is 8.1") — older Flyway (9/10.x) hard-fails on MySQL 9. Flyway 11 needs
  a **Java 17+ runtime** (the game now requires JDK 17+ to run) but compiles fine into a
  `--release 8` build — same principle as the Mockito 5 lesson: newer dependency bytecode
  is a runtime floor, not a compile blocker.
- **Migrations run as a separate `amiss_migrator` account at startup** (DDL+DML), keeping
  the runtime `amiss` user at SELECT/INSERT/UPDATE — the prod-style split of migration vs
  runtime credentials. `baselineOnMigrate`/`baselineVersion=1` upgrades pre-Flyway installs
  without touching saves; V2 reseeds reference data via DELETE+INSERT so it's identical on
  clean and baselined DBs.
- **MySQL service containers in Actions:** connections from the runner arrive via the
  Docker bridge, NOT localhost — `'user'@'localhost'` accounts from bootstrap.sql won't
  match; CI mirrors them as `'user'@'%'`. The ubuntu-latest image has a `mysql` client
  preinstalled (no server needed — the container provides it).
- **Stacked PRs:** base PR2 on PR1's branch so PR1's new workflows run on PR2 and the diff
  stays clean; when PR1 merges and its branch is deleted, GitHub auto-retargets PR2 to the
  original base. Merge with merge commits (this repo's norm) so the retargeted diff stays
  empty of PR1's changes.

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
