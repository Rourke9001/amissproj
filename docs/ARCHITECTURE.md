# AmissProj — Architecture

This document is the map of the codebase after the **Phase 3 clean-architecture refactor**.
It covers the folder structure, the layer boundaries and the dependency rule, and *why* the
code is shaped this way. Nothing here changes gameplay — the refactor was behaviour-preserving
(all 87 tests stay green); it reorganises the code so the game logic is framework-independent
and a Spring Boot backend can be dropped in later without touching the rules.

## The layers

The code follows **Clean Architecture**: dependencies point **inward**, toward the domain.
An outer layer may depend on an inner one; an inner layer never knows about an outer one.

```
   presentation  ─┐
                  ├─►  application  ─►  domain
   infrastructure ─┘        ▲
                            └── infrastructure also implements application's ports
```

| Layer | Package | Responsibility | May depend on |
|-------|---------|----------------|---------------|
| **domain** | `amiss.domain.*` | Entities and pure rules. No Swing, JDBC, config or 3rd-party frameworks. | nothing (pure Java) |
| **application** | `amiss.application.*` | Use-cases (`service`) + **ports** (repository interfaces). The game rules. | domain |
| **infrastructure** | `amiss.infrastructure.*` | Adapters: JDBC repositories, DB connection, config, security. Implements the ports. | application, domain |
| **presentation** | `amiss.presentation.*` | Swing UI — one delivery mechanism among many. | application, domain |

## Maven modules (Phase 3 reactor)

The layers map onto a multi-module Maven reactor, so each delivery mechanism is
its own artifact and the rules ship UI-free:

| Module | Contains | Depends on |
|--------|----------|------------|
| `amiss-core` | `domain` + `application` + `infrastructure` packages, the Flyway migrations, the whole unit-test suite | — |
| `amiss-swing` | the `presentation` package (Swing client), UI images, `application.properties`, `logback.xml`; shades the runnable `AmissProj.jar` | `amiss-core` |
| `amiss-api` | Spring Boot REST API — another presentation adapter over the same core; `PersistenceConfig` is its `GameContext` counterpart, `Jdbc` runs in pooled (`DataSource`) mode | `amiss-core` |
| `amiss-coverage` | JaCoCo `report-aggregate` for CI; no code | the code modules |

Core exposes only the SLF4J facade; each app picks its logging backend (Swing
bundles logback, Spring Boot brings its own) — so the modules can't fight over
logging versions.

## Folder structure

```
amiss-core/src/main/java/amiss/
  domain/
    model/        User, UserGoals, ActionResult        (plain entities / value types)
    board/        Board, Location                      (the 13-stop ring board model)
    validation/   Validation                           (pure input rules)
  application/
    port/         UserRepository, UserStatsRepository, (repository INTERFACES — the seam)
                  JobRepository, HelpRepository
    service/      TimeService, EducationService, FoodService, JobService,
                  StatsService, GameServices           (the game rules)
  infrastructure/
    persistence/jdbc/  Jdbc                             (JDBC helper, was `DB`)
                       JdbcUserRepository, JdbcUserStatsRepository,
                       JdbcJobRepository, JdbcHelpRepository   (implement the ports)
    persistence/flyway/ FlywayMigrator                  (startup schema migration)
    config/       Config                               (env / properties)
    security/     PasswordHasher                       (BCrypt)
    GameContext                                        (composition root)
amiss-swing/src/main/java/amiss/
  presentation/
    ui/           LoginGUI (entry point), MainGameGUI, the location screens,
                  HelpGUI, HighScoreGUI, OpenLocation  (Swing)
    assets/       Assets                               (classpath image loader)
```

Tests mirror these packages under `amiss-core/src/test/java/`.

## The load-bearing change: repository ports

Before, the services depended on **concrete** JDBC repositories (`amiss.repository.*`), which
wrapped the `DB` class. That is a hard dependency from the rules onto the database — the thing
that makes "swap in a real backend" a rewrite.

Now each repository is split in two:

- an **interface** in `amiss.application.port` (e.g. `UserRepository`) — what the rules need;
- a JDBC **adapter** in `amiss.infrastructure.persistence.jdbc` (e.g. `JdbcUserRepository`
  `implements UserRepository`) — how it's done today.

The services depend only on the interface. The concrete adapter is chosen in one place, the
composition root. This is the *Dependency Inversion Principle*: both the rules and the database
now depend on an abstraction the rules own.

**Why it matters for scaling to full-stack:** replacing JDBC with Spring Data / JPA (or an
in-memory fake for a test) means writing new adapters and a new `GameContext` — the entire
`domain` and `application` layers, and every service test, are untouched. A REST controller
becomes just another `presentation` adapter alongside Swing.

**Ports throw a technology-neutral exception (KAN-17).** The four ports used to declare
`throws SQLException`, a JDBC concept leaking one level into the application layer. They now
throw the unchecked `amiss.application.port.PersistenceFailureException` instead; the `Jdbc`
helper (`infrastructure.persistence.jdbc`) is the one place that catches `SQLException` and
translates it into that exception, at the JDBC/application boundary. Every service's
`catch (SQLException)` fallback became `catch (PersistenceFailureException)` with the same
body, so behaviour is unchanged — only the exception type crossing the port is.

## The composition root: `GameContext`

`amiss.infrastructure.GameContext` is the single seam where a persistence technology is chosen.
It opens the JDBC connection, builds the `Jdbc*Repository` adapters, and hands the presentation
layer either the ports it needs or a per-player `GameServices`. The Swing screens now hold a
`GameServices` (built once at login and threaded through `OpenLocation`) instead of a raw `DB`,
so **no JDBC leaks into the UI**. `LoginGUI`, `HelpGUI` and `HighScoreGUI` build a `GameContext`
instead of calling `new DB()`.

`GameServices` builds the five services in dependency order (education → job; time, food; then
stats) from the ports it is given — it no longer constructs repositories itself.

## Architectural improvements at a glance

| Concern | Before | After |
|---------|--------|-------|
| Separation | flat `amiss` package mixed Swing, JDBC, config, entities | four layers with a one-way dependency rule |
| Coupling | services → concrete JDBC repositories | services → ports (interfaces); JDBC is an implementation detail |
| Wiring | every screen did `new GameServices(user, db)` | one composition root (`GameContext`); UI holds `GameServices` |
| UI / DB | screens passed a raw `DB` around | no JDBC type in the presentation layer |
| Testability | rules unit-tested against concrete repos | rules unit-tested against port interfaces (same 87 tests, cleaner seam) |
| Scalability | a Spring backend meant reworking the rules | swap `infrastructure` + add a REST adapter; rules unchanged |

## Known interim simplifications (deliberate, documented)

- **UI constructs the composition root.** `LoginGUI`/`HelpGUI`/`HighScoreGUI` `new GameContext()`
  directly. That is fine (the outermost layer is allowed to wire the app), but a `main` bootstrap
  could own it instead once there is more than one entry point.
```

