# AmissProj

A Java **Swing** desktop game — a remake of the classic *Jones in the Fast Lane*.
You move around a small city, get a job, earn and spend money, study at university,
pay rent and chase your goals across rounds. All game state is persisted in **MySQL**.

> Originally built as a high-school project in NetBeans. This repo revives it on a
> modern toolchain (JDK 20 + MySQL 9 + Connector/J 9) and is being incrementally
> cleaned up as a Java refresher / portfolio piece.

---

## Tech stack

| | |
|---|---|
| Language / UI | Java 8 source, Swing (`amiss` package) |
| Database | MySQL 8.4+ / 9.x (`amissdb`) |
| JDBC driver | MySQL Connector/J 9.7 (`dist/lib/`) |
| Build | plain `javac` + `jar` via `scripts\build.ps1` (no NetBeans/Ant needed) |
| Entry point | `amiss.LoginGUI` |

## Prerequisites

- A **JDK 8 or newer** (developed on JDK 20).
- **MySQL Server 8.4 LTS or 9.x**, running locally on port `3306`.

## Quick start

```powershell
# 1. Create the database (one-time). Enter your MySQL root password when asked.
mysql -u root -p < db\setup.sql

# 2. Make sure src\amiss\DB.java has your MySQL root password (default: "password").

# 3. Build and run.
powershell -File scripts\build.ps1
powershell -File scripts\run.ps1
```

On launch the console prints `Connection Successful`. In the login window, type a
username + password and click **Logging In** — it offers to create the user. Once
created, the city screen opens. (Tip: take a *Janitor* job first — it needs no
education or special clothes.)

Full first-time setup, including installing MySQL and troubleshooting, is in
**[SETUP.md](SETUP.md)**.

---

## Starting & stopping

### The MySQL server
It was installed as a Windows **service** (`MySQL97`) set to start automatically on
boot, so normally it's already running. To control it manually, use an **elevated**
(Run as administrator) PowerShell:

```powershell
net start MySQL97     # start the database
net stop  MySQL97     # stop it (frees memory when you're not playing)
Get-Service MySQL97   # check status
```
(You can also use the **Services** app — `services.msc` — and start/stop *MySQL97*.)

### The game
- **Start:** double-click `dist\AmissProj.jar`, or run `powershell -File scripts\run.ps1`.
- **Stop:** close the game window. If a window is left over, end it with:
  ```powershell
  Stop-Process -Name javaw     # closes the running game (Java GUI process)
  ```

---

## Build from source

```powershell
powershell -File scripts\build.ps1   # compiles src\ -> build\classes and rebuilds dist\AmissProj.jar
powershell -File scripts\run.ps1     # runs from the freshly compiled classes
```
`build.ps1` finds the JDK automatically and packages the jar with the right
classpath — no NetBeans or Ant required.

## Project structure

```
src/amiss/        Java source (Swing GUIs + game logic + DB wrapper)
db/setup.sql      Database schema + seed data (jobs, help text)
scripts/          build.ps1 (compile+package), run.ps1 (launch)
dist/lib/         third-party jars (committed; all redistributable)
nbproject/        original NetBeans project files (optional, for the IDE)
SETUP.md          detailed setup & troubleshooting guide
tasks/todo.md     roadmap & progress
```

## Roadmap

Done: revived the build, modernised the JDBC driver, reconstructed the database.
Next up (see **[tasks/todo.md](tasks/todo.md)** for detail):

- [ ] Replace string-concatenated SQL with **parameterised `PreparedStatement`s** (fixes SQL injection)
- [ ] Use a **least-privilege DB user** instead of `root`
- [ ] Close JDBC resources with try-with-resources
- [ ] Externalise DB config out of `DB.java`
- [ ] Separate game logic from Swing so the rules can be unit-tested

## Notes

The contents of the `tbljobs` / `tblhelp` tables are a **reconstruction** — the
originals lived only in the old NetBeans database and were lost. Job names and
locations are taken verbatim from the code; the numeric values are tunable.
