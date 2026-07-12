# Lessons

Prescriptive patterns for this repo (per CLAUDE.md self-improvement loop).

**Adding a lesson:** document the working solution, not the debugging journey —
write it for someone hitting the problem fresh. Include a failed approach only
if it's a genuine trap (looks right, breaks non-obviously), compressed to one
line under **Avoid**. One entry per lesson: extend the existing entry rather
than appending a variation elsewhere.

## Windows & PowerShell 5.1

### PowerShell 5.1 mangles native-command stderr
- **Context:** capturing output from a native exe (`java`, `git`, `mysql`) in PS 5.1.
- **Solution:** don't redirect — the tool harness captures stderr for you. If
  streams must merge, run via `cmd /c "java ... 2>&1"` so it stays plain text.
- **Avoid:** `java ... 2>&1` directly in PS 5.1 — each stderr line becomes an
  ErrorRecord and `$ErrorActionPreference='Stop'` aborts the script even when
  the exe succeeded.

### No `<` input redirection
- **Context:** loading a SQL file: `mysql -u root -p < file.sql`.
- **Solution:** PS 5.1 has no `<` (parser error). Use the Bash tool or
  `cmd /c "mysql ... < file.sql"`.

### Invoke-WebRequest on this machine
- **Context:** hitting the API from PS 5.1.
- **Solution:** always `-UseBasicParsing` (no IE initialisation here); read an
  error response's body via `$_.Exception.Response.GetResponseStream()`.
- **Avoid:** `-SkipHttpErrorCheck` — PowerShell 7-only.

### Git commit messages from PowerShell
- **Context:** any multi-line or quoted commit message.
- **Solution:** write the message to a file (`Set-Content -Encoding ascii`, or a
  Bash heredoc) and `git commit -F <file>`.
- **Avoid:** double-quotes inside `git commit -m @'...'@` — PS 5.1 native-arg
  quoting splits the message and git misreads tokens as pathspecs
  (`fatal: '5:' is outside repository`).

### mvnw.cmd mangles `&` inside `-D` args
- **Context:** `-Dflyway.url=jdbc:...?useSSL=false&allowPublicKeyRetrieval=...`
  run locally.
- **Solution:** drop the query params — a plain `jdbc:mysql://host/db` URL
  connects fine. CI workflows run bash, where quoting works.
- **Why:** PowerShell passes the quoted arg intact; the `.cmd` batch layer
  re-parses it and splits at each `&`.

### Run mvnw from the PowerShell tool
`cmd /c "mvnw.cmd ..."` from Git Bash prints the cmd banner and runs nothing.
`.\mvnw.cmd test` in PowerShell works (stderr is captured — don't add `2>&1`).

### MySQL service control needs elevation
`net start/stop MySQL97` from a normal shell returns
`System error 5: Access is denied` — it needs an elevated shell.

### Killing the right JVM *(merges 3 entries)*
- **Context:** stopping the API or a game process without knifing IDE tooling.
- **Solution:** find the owner of the port and stop that pid:
  `Get-NetTCPConnection -LocalPort 8080 -State Listen` → `Stop-Process -Id …`.
- **Avoid:** `Stop-Process -Name java,javaw` — the VS Code Java language server
  and SonarLint are also `java.exe`. Matching CommandLine on `'amiss'` — the
  workspace path (`…\Amiss Proj\…`) appears in the language server's command
  line too. Matching `'spring-boot'`/`'amiss-api'` — the Boot child launches
  with `-cp @<argfile>` in %TEMP%, so neither string appears; you kill the mvnw
  wrapper and the orphaned server keeps answering as a silently stale build.

### Don't truncate a log an open appender holds
`Clear-Content` on `logs/amiss.log` while a JVM holds it writes garbage /
reads empty. Kill the writer first, or `Remove-Item` the log so the appender
recreates it.

### sed with an SQL apostrophe
An SQL-escaped name like `'Black''s Market'` edits cleanly with a
double-quoted sed program — `sed -i "s/'Market'/'Black''s Market'/g"` — the
single quotes are literal and nothing in the expression expands.

## Environment & agent workflow

### Pick the JDK by its release file *(merges 2 entries)*
- **Context:** anything that launches `java` or `mvnw` on this machine.
- **Solution:** user-level `JAVA_HOME` points at Temurin 21 (user-level
  overrides machine-level). Scripts select a JDK by parsing its `release` file
  for `JAVA_VERSION` — see `scripts/find-java21.ps1`. If a shell lacks it, set
  `$env:JAVA_HOME` explicitly before `.\mvnw.cmd`.
- **Avoid:** trusting PATH `java` or machine `JAVA_HOME` — the Oracle javapath
  shim's parent is not a JDK home, stale machine-level values linger, and a
  `release=21` jar on an older JVM dies with `UnsupportedClassVersionError` —
  silently when double-clicked (no console).

### Trust the build, not the IDE diagnostics *(merges 3 entries)*
- **Context:** VS Code Java reports "cannot be resolved" / wrong-package errors
  on code that's actually fine (new deps, moved files, fresh modules).
- **Solution:** the folder-based "invisible project" lags the real classpath
  until it reindexes. If `./mvnw clean verify` is green, the diagnostics are
  noise — never edit code to satisfy them.

### WebFetch returns 402 Payment Required
- **Context:** any WebFetch call fails with 402.
- **Solution:** it's the fetch service's quota, not the target site — retrying
  won't help. Read the page with Claude-in-Chrome (`tabs_create_mcp` →
  `navigate` → `get_page_text`); subagents can too if told to load the tools
  via ToolSearch. Sandboxed `curl` has no egress (exit 43). Known hit:
  jonesinthefastlane.fandom.com (the canonical rules wiki).

### The Jones wiki is canonical for rules questions
jonesinthefastlane.fandom.com settles cost/balance questions — check it before
inventing a value (it settled eat = 0 and the wage×8 pro-rated shift pay).
WebFetch 402s on fandom — use the Chrome tools.

### Pin scaffolder majors in handoff packets
- **Context:** any `npm create` / `npx` scaffolder in a subagent brief.
- **Solution:** pin the major (`npm create -y vite@7 … -- --template react-ts`)
  and state what the output must contain, so template drift trips a stop
  condition instead of an improvisation.
- **Why:** create-vite@9 silently swapped ESLint for oxlint between
  spec-writing and execution — "append X to the ESLint config" had nothing to
  append to.

### Stale build artifacts *(merges 2 entries)*
- **Context:** single-module Maven runs, or a working tree after a branch switch.
- **Solution:** after changing amiss-core, run
  `.\mvnw -B -pl amiss-core install -DskipTests` before any single-module
  amiss-api run will see it; after switching branches in a tree a subagent
  built on, `clean` before trusting results.
- **Why:** `-pl amiss-api` resolves core from `~/.m2` (a stale snapshot →
  phantom `ClassNotFoundException`); stale `target/` classes once produced 58
  phantom compile errors a clean run didn't have.

### Verify, then commit — separate commands
Never chain `… | grep … && git commit && git push` — the grep eats the build's
exit code and the push happens even on red.

## Maven & the reactor

### Maven Wrapper, not a global Maven
No global `mvn` exists on this machine — everything runs through the committed
wrapper (`.\mvnw.cmd`). (Bootstrapping a wrapper without mvn: unzip the
official `maven-wrapper-distribution:<ver>-bin.zip` and hand-write
`maven-wrapper.properties`.)

### Uber-jar shading traps *(general knowledge — Boot repackage replaced shade here)*
If a maven-shade uber-jar ever returns: add `ServicesResourceTransformer`
(merges `META-INF/services` — SLF4J's provider and JDBC's driver discovery
break **silently** without it), `ManifestResourceTransformer` for the
Main-Class, and shade filters excluding `META-INF/*.SF|*.DSA|*.RSA` (signed
deps → "Invalid signature file digest") and `module-info.class`
(multi-release deps).

### Keep the logging backend out of shared modules
amiss-core exposes only slf4j-api (logback-classic in test scope for
logback-test.xml); each app carries its own backend. This kills the
nearest-wins clash with Boot's managed logback before it exists.

### Single-module plugin goals need `-pl`
Invoking a plugin goal reactor-wide re-runs it per module — e.g.
`flyway:migrate` needs `-pl amiss-core` now that the plugin config lives there.

### Newer dependency bytecode is a runtime floor, not a compile blocker *(merges 2 entries)*
`--release N` restricts the JDK platform API only; it doesn't stop compiling
against third-party jars with newer bytecode (Mockito 5's Java-11 bytecode
compiled fine into the old release-8 build; Flyway 11 needs a Java 17+
runtime). A dependency's requirement moves the runtime floor, nothing else.

## Spring Boot & MVC

### Importing the Boot BOM (not the parent) loses `-parameters`
Spring MVC resolves `@PathVariable`/`@RequestParam` names reflectively —
without the flag every request dies with "Name for argument … not specified".
The Boot parent sets it; a BOM-only build must add `<parameters>true</parameters>`
to maven-compiler-plugin (done reactor-wide in root pluginManagement).

### Pin every @WebMvcTest slice
Unqualified `@WebMvcTest` instantiates every `@Controller` in the app — always
`@WebMvcTest(controllers = X.class)`.

### Security inside web slices
- **Context:** any `@WebMvcTest` with spring-boot-starter-security on the
  classpath (the slice gets Boot's default lockdown: 401s/CSRF on every POST).
- **Solution:** `@Import(SecurityConfig.class)` on every slice; authenticate
  protected routes with the spring-security-test `jwt()` post-processor.
- **Avoid:** `addFilters=false` — it hides the very rules the security tests
  exist to verify.

### Nimbus JWT defaults
`JwtEncoder` defaults its JWS header to RS256 even with an HMAC secret — pass
`JwsHeader.with(MacAlgorithm.HS256)` explicitly. `Jwt#getIssuer()` returns
`java.net.URL`; read a plain-string issuer via `getClaimAsString("iss")`.

## Spring Data JPA

### saveAndFlush where the contract promises translated exceptions
- **Context:** JPA `save()` with an assigned id (no IDENTITY generation to
  force a flush).
- **Solution:** use `saveAndFlush()` in adapters whose port contract promises
  translated exceptions.
- **Why:** the INSERT defers to commit, so a try/catch inside the
  @Transactional method returns before the SQL runs and constraint violations
  escape as raw DataAccessExceptions. Found by the Testcontainers ITs —
  exactly what they're for.

### @DataJpaTest lies about @Modifying code
The default test-wrapping transaction's first-level cache masks JPQL UPDATEs
(stale reads). Run port-contract ITs with
`@Transactional(propagation = NOT_SUPPORTED)` so each call manages its own
transaction like production; clean up rows manually.

### Testcontainers MySQL 9
`MySQLContainer` mounts a legacy my.cnf with `innodb_log_file_size` (removed
in MySQL 9) — the container won't boot; override with
`withConfigurationOverride()` pointing at a near-empty conf dir. Use the
singleton-container pattern (static start, never stop; Ryuk reaps it) —
per-class `@Container` restarts leave the cached Spring context's pool
pointing at a dead container. A static analyzer flags the never-closed
container field as a "resource leak" — expected for this idiom (Ryuk reaps
it at JVM exit), not a defect; don't add a close/stop call.

### A test needing two migration targets in one run gets its own container
`LegacyToSaveMigrationIT` (KAN-54) needed to stop Flyway at an old version,
seed data, then migrate the rest of the way — the shared `MySqlITSupport`
singleton is unusable for this because every Spring-based `*IT` in the same
JVM run drives it straight to the latest schema at context startup, so
"target an old version" silently no-ops once another test has already gone
first. Give a test like this its own dedicated container (same singleton-start
idiom, just not shared) rather than fighting execution order.

## Testing

### Pin maven-surefire-plugin 3.x
Pre-3.x Surefire doesn't discover JUnit 5. Pinned (3.2.5) so the run is
reproducible across Maven versions; Jupiter artifacts align via the
`junit-bom` import.

### STRICT_STUBS is the MockitoExtension default
Every `when(…)` must be used by that test or it throws
`UnnecessaryStubbingException`. Stub only what the path under test touches —
early-return branches must not stub the calls they short-circuit past.
Multiple uses of one stub are fine.

### You can't mock toString/equals/hashCode
When code under test calls `toString()` on a collaborator, test with the real
collaborator over mocked repositories (mirroring the composition-root wiring)
instead of mocking the collaborator.

### Stub void methods with doThrow
`doThrow(…).when(mock).method(args)` — not `when(…).thenThrow` — to cover
`catch` fallback branches behind void port calls.

### Silence expected-exception log noise
Tests that make ports throw hit the services' `log.warn(…, ex)` lines and dump
stack traces into Surefire output. `src/test/resources/logback-test.xml` with
`<logger name="amiss" level="OFF"/>` — Logback prefers it on the test
classpath; the main logging config is untouched.

### Prove a DB-free test DB-free
- **Context:** context/`@SpringBootTest`-style tests meant to run without a
  database.
- **Solution:** run them once with `AMISS_DB_URL` pointed at a dead port. The
  JPA-off recipe: `ddl-auto=none` + explicit dialect +
  `hibernate.boot.allow_jdbc_metadata_access=false`.
- **Avoid:** trusting "green locally" — the local MySQL may be silently
  answering (a validate-at-startup connection only failed in DB-less CI).
  Also: surefire `-Dtest` takes comma-separated patterns — a `+`-joined string
  matches nothing, and with `failIfNoSpecifiedTests=false` that "passes".

### Characterization discipline
Characterization tests pin current behaviour, quirks included. A genuine
defect found while characterizing gets documented + pinned, not silently
fixed; a deliberate fix is its own approved commit with a regression test
(precedent: the clothes-purchase validate-before-charge fix).

### Mutation RED for a brand-new integration test
- **Context:** TDD-ing a new IT/file with no prior broken behaviour to fail
  against (e.g. `LegacyToSaveMigrationIT`, KAN-54 — the migrations it proves
  were already committed and reviewed, so the first honest run just passes).
- **Solution:** write the test, confirm it's green, then deliberately mutate
  one assertion per method to a wrong expected value and re-run. The failure
  output's "but was" side must show the *genuinely correct* post-migration
  value — proof the assertion reads real state, not a tautology — then revert
  the mutation for the real GREEN run.
- **Why:** a passing test on the first try is not evidence it asserts
  anything; this is the RED step for tests that can't fail the normal way.

## Database & Flyway

### MySQL 9 dropped mysql_native_password
It defaults to `caching_sha2_password`; ancient Connector/J (5.x) cannot
authenticate. Modern Connector/J 8/9 is mandatory.

### Seed data is a reconstruction
The original job/help reference data was never in source control (it lived in
NetBeans' managed DB) — migration seed values are reconstructed: names
verbatim from code, numeric columns tunable design values.

### BCrypt hashes are always 60 chars
The password column must be `VARCHAR(60)`+ or hashes silently truncate.

### Least privilege means cleanup needs root
The runtime `amiss` user has SELECT/INSERT/UPDATE only — deleting test rows
needs root. (Verified at runtime: DELETE as `amiss` is denied — the point.)

### Flyway 11 on MySQL 9
Works with a "newer than supported" warning; Flyway 9/10.x hard-fail on
MySQL 9. Flyway 11 needs a Java 17+ runtime (see the bytecode-floor rule).

### Migrator vs runtime credentials
Migrations run as `amiss_migrator` (DDL+DML) at startup; the runtime user
keeps zero DDL. `baselineOnMigrate`/`baselineVersion=1` upgrades pre-Flyway
installs without touching saves; reference reseeds are idempotent
DELETE+INSERT so clean and baselined DBs converge.

### MySQL service containers in Actions
Connections from the runner arrive via the Docker bridge, NOT localhost —
mirror bootstrap accounts as `'user'@'%'`. ubuntu-latest has a mysql client
preinstalled (the container provides the server).

## Git & CI

### Repo-local commit identity
`git config user.email rourke9001@gmail.com` (repo-local) uses the personal
account without touching global/work config.

### git add aborts wholesale on any bad pathspec
One pathspec matching nothing fails the ENTIRE add — a later commit then
silently contains only what was previously staged. Check `git status --short`
before committing multi-path adds.

### Fix the mvnw execute bit in the index
Windows checkouts don't preserve the bit and ubuntu runners fail at `./mvnw`:
`git update-index --chmod=+x mvnw` once.

### Unanchor module build-output ignores
`/target/` only ignores the root target — a reactor needs `target/`
(unanchored) or every module's build output shows up untracked.

### Track shared .vscode config
Ignore per-user state but track shared config: `.vscode/*` then
`!.vscode/settings.json` / `!.vscode/extensions.json`.

### .gitattributes: refresh the stat cache, order the rules
`git status` stays noisy after adding an eol rule until
`git add --renormalize <dir>` (stages nothing) clears the cached stat data.
Keep `*.png binary` AFTER `frontend/** text eol=lf` — the later line wins.

### Private-repo plan gates
Branch protection (rulesets and classic) returns 403 on the free plan
("Upgrade to Pro or make public") — ready-to-apply rulesets live in
`.github/rulesets/`. Actions/raw badge images also don't render in a private
README (camo can't fetch them).

### Coverage badges vs protected main
CI publishes the JaCoCo SVGs to an orphan `badges` branch
(`git switch --orphan` + force-push); the README hotlinks the raw URLs; the
badge job runs only on pushes to main.

### Stacked PRs
Base PR(n+1) on PR(n)'s branch so the diff stays clean and PR(n)'s new
workflows run on it; when PR(n) merges and its branch is deleted, GitHub
auto-retargets. Merge with merge commits (repo norm) so retargeted diffs stay
empty.

### Bulk package restructure recipe
`git mv` first, then rewrite every declaration in one pass: derive each file's
package from its directory (`dirname | sed 's|src/main/java/||; s|/|.|g'`) and
sed the first `package …;` line. ~40 files fixed deterministically; only class
renames/imports need real thought.

### A dropped/renamed table can still be live in a workflow file
`.github/workflows/migrations.yml` hardcoded `SELECT COUNT(*) FROM tbljobs`
as a seed-data check; V6 (KAN-54) dropped that table and nothing local caught
it — `mvnw verify` never runs this workflow's raw SQL, only `gh pr checks`
after push did. When a migration drops or renames a table/column, grep
`.github/workflows/*.yml` (and any other non-Java script) for the old name —
these references aren't part of the Maven build and won't show up before CI.

## API & SPA contract rules

### Charged rejections mutate state *(merges 2 entries)*
- **Context:** this API deliberately charges time/money on some rejection
  paths (original-game parity): a move landing exactly on 0 minutes is
  persisted but answered 409; a failed job application is a 200 with
  `hired:false` and the interview minutes spent.
- **Solution:** the client applies server state from every response —
  including outcome-shaped "failures" — and on error responses invalidates and
  refetches (`invalidateQueries`) instead of trusting the cache. Render
  charged rejections as outcomes in the feed, not errors.
- **Why:** a client that only updates on success shows a stale clock; found
  live in Chrome — unit tests with mocked api modules can't catch it.

### Pair `background` with `color` in new CSS (KAN-45)
- **Context:** `index.css` sets `color-scheme: light dark` and nothing else on
  `body`/`:root` — every page renders on the browser's dark-mode UA default
  (white-ish text, near-black canvas) unless a rule overrides it.
- **Solution:** any new rule that sets one of `background`/`color` explicitly
  must set the other too (or use `opacity` for de-emphasis instead of a literal
  color, matching `.hud-stat dt`/`.store-row-detail` in `board.css`). Before
  shipping new CSS, grep it for an explicit `background:`/`color:` with no
  paired rule in the same block.
- **Why:** `.win-banner` set `background: #fff` with no `color` → inherited
  white text on a white card, invisible — caught only by a live browser
  screenshot, not Testing Library (which doesn't render actual CSS). The same
  PR's `.save-meta` had the mirror-image bug (`color: #555`, no `background`),
  low-contrast on the implied dark canvas. Testing Library asserts the DOM
  tree, never pixel contrast — this class of bug needs an actual rendered
  screenshot to catch.

### Verify a DTO field's unit against the core rule *(merges 3 entries)*
- **Context:** writing UI copy or a derived field for any money/time/progress
  value.
- **Solution:** read the core arithmetic before interpreting the field, and
  check derivations against live values before trusting them.
- **Avoid:** trusting the field name — `hourlyWage` was a per-shift payout;
  `educationProgress` was a 1-based study count, not a percentage.

### Read a getter's body before exposing it on the wire
Legacy "getters" sometimes mutate (`FoodService.getEat()` consumed a stored
food week). Never call an unread legacy accessor from an assembler/DTO path.

### Mock the port type
Service tests mock the repository interface (the port), so extracting ports
and swapping adapter tech leaves tests untouched — proven when 245 tests
passed unmodified through the JDBC→JPA swap. Keep port exception contracts
aligned with the services' catch branches.

### One composition root *(merges 2 entries)*
Exactly one place names concrete adapters and wires services (today:
`PersistenceConfig` in amiss-api). Services take ports only — a persistence or
framework swap is a new root + adapters, rules untouched.

### Model structure, not special cases
The board is a ring (`Board`, movement cost = min(cw, ccw) steps) — that
deleted the grid wrap-toll special cases and their provably-dead branch in one
move, and scales to any stop count.
