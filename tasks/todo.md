# Tasks — AmissProj revival

## Goal 1: Get the old NetBeans project running locally again

### Plan
- [x] Review the exported project; identify entry point, DB usage, dependencies
- [x] Confirm toolchain on this machine (JDK 20 present; no Ant/MySQL)
- [x] Locate dependencies — found `dist/lib` already bundles AbsoluteLayout + beansbinding
- [x] Replace dead MySQL Connector/J 5.1.22 with modern 9.7.0 (from the user's bundle)
- [x] Modernise `DB.java` (driver class + connection URL; surface connection errors)
- [x] Reverse-engineer the schema from inline SQL → `db/setup.sql`
- [x] Reconstruct reference data (`tbljobs`, `tblhelp`) — lost with the old DB
- [x] Add `scripts/build.ps1` (javac + jar, no NetBeans/Ant) and `scripts/run.ps1`
- [x] Verify clean compile on JDK 20 and that the driver loads (only the absent
      server errors out)
- [x] Write `SETUP.md` runbook
- [x] **USER:** installed MySQL 9.7.1 Community (service `MySQL97`, root password = `password`)
- [x] Aligned `DB.java` to `root`/`password`; loaded schema via `db\setup.sql`
- [x] Confirmed app prints "Connection Successful" against the live DB
- [x] Verified the game's exact signup INSERTs + a job lookup + FK cascade cleanup
- [x] Launched the game GUI (login window opens)
- [ ] **USER:** play-test — create a player, take a Janitor job, work, study
      (final confirmation the reconstructed seed values feel right)
- [ ] (optional) Install MySQL Workbench for a DB GUI — https://dev.mysql.com/downloads/workbench/

### Review
*Goal 1 complete: the project builds with plain javac (no NetBeans/Ant), the JDBC
driver is modernised to Connector/J 9.7, and the app connects to MySQL 9.7.1 and
runs. Schema columns/types are derived directly from the code's SQL (reliable);
`tbljobs`/`tblhelp` row values are a reconstruction (originals unrecoverable) and
can be tuned after play-testing. Credentials are root/`password` for now —
replacing root with a least-privilege user is the first Goal 2 item.*

---

## Goal 2 (later): "fix what I can" — modernise for a banking-sector portfolio
See SETUP.md §8. Highest value first:
- [ ] Parameterise all SQL (`PreparedStatement`) — kills the pervasive SQL injection
- [ ] try-with-resources for all JDBC `ResultSet`/`Statement`
- [ ] Dedicated least-privilege DB user instead of `root`
- [ ] Externalise DB config (properties/env) out of `DB.java`
- [ ] Separate game logic from Swing so rules are unit-testable
