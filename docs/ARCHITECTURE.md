# AmissProj — Architecture

This document is the map of the codebase after the **Phase 3 clean-architecture refactor**.
It covers the folder structure, the layer boundaries and the dependency rule, and *why* the
code is shaped this way. Nothing here changes gameplay — the refactor was behaviour-preserving
(all 87 tests stay green); it reorganises the code so the game logic is framework-independent
and a Spring Boot backend can be dropped in later without touching the rules.

> **Update (July 2026, KAN-51):** the Swing client and the core's JDBC adapters it wired
> (`amiss-swing`, `GameContext`, `infrastructure.persistence.jdbc`, `Config`,
> `FlywayMigrator`) have been **retired** — the payoff of the ports described below is that
> their removal touched no rule code. The API's `PersistenceConfig` (Spring Data JPA
> adapters, KAN-34) is now the only composition root, and Spring Boot's auto-configured
> Flyway the only migration runner. Sections below describing the Swing wiring are kept as
> the historical rationale for the port seam.

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
| **presentation** | `amiss.api.web.*` (+ the React SPA over HTTP) | Delivery mechanisms — REST controllers/DTOs; the retired Swing client was another. | application, domain |

## Maven modules (Phase 3 reactor)

The layers map onto a multi-module Maven reactor, so each delivery mechanism is
its own artifact and the rules ship UI-free:

| Module | Contains | Depends on |
|--------|----------|------------|
| `amiss-core` | `domain` + `application` + `infrastructure.security` packages, the Flyway migration sources, the whole unit-test suite | — |
| `amiss-api` | Spring Boot REST API — the presentation adapter over the core; `PersistenceConfig` is the composition root wiring Spring Data JPA adapters to the ports | `amiss-core` |
| `amiss-coverage` | JaCoCo `report-aggregate` for CI; no code | the code modules |

(`amiss-swing`, the original desktop client module, was retired with KAN-51.)
Core exposes only the SLF4J facade; Spring Boot brings the logging backend —
so the modules can't fight over logging versions.

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
    security/     PasswordHasher                       (BCrypt)
amiss-core/src/main/resources/
  db/migration/   V1__... V2__...                      (Flyway migration sources)
amiss-api/src/main/java/amiss/api/
  config/         PersistenceConfig (composition root), SecurityConfig, ...
  persistence/jpa/ entities + Spring Data repos + Jpa*Repository port adapters
  web/            controllers + DTOs (the REST presentation layer)
```

Tests mirror these packages under `amiss-core/src/test/java/`.

## The load-bearing change: repository ports

Before, the services depended on **concrete** JDBC repositories (`amiss.repository.*`), which
wrapped the `DB` class. That is a hard dependency from the rules onto the database — the thing
that makes "swap in a real backend" a rewrite.

Now each repository is split in two:

- an **interface** in `amiss.application.port` (e.g. `UserRepository`) — what the rules need;
- an **adapter** implementing it — originally the core's `Jdbc*Repository` classes, today
  the API's Spring Data JPA adapters (`amiss.api.persistence.jpa`, KAN-34).

The services depend only on the interface. The concrete adapter is chosen in one place, the
composition root. This is the *Dependency Inversion Principle*: both the rules and the database
now depend on an abstraction the rules own.

**Why it matters — proven twice:** the KAN-34 swap from JDBC to Spring Data JPA, and then
the KAN-51 deletion of the entire JDBC/Swing side, each touched adapters and composition
roots only — the `domain` and `application` layers and every service test were untouched.

**Ports throw a technology-neutral exception (KAN-17).** The four ports used to declare
`throws SQLException`, a JDBC concept leaking one level into the application layer. They now
throw the unchecked `amiss.application.port.PersistenceFailureException` instead; the adapter
layer is the one place that catches the technology's exceptions (originally `SQLException`
in the JDBC helper, today Spring's `DataAccessException` family in the JPA adapters) and
translates them at the boundary. Every service's fallback branch is unchanged — only the
exception type crossing the port is.

## The composition root: `PersistenceConfig`

`amiss.api.config.PersistenceConfig` is the single seam where the persistence technology is
chosen: each port gets a thin adapter over the generated Spring Data interfaces. Controllers
hold a per-player `GameServices` built by `GameServicesFactory` — **no persistence type
leaks into the web layer**. (The Swing era's equivalent was `GameContext`, which wired the
core's `Jdbc*Repository` adapters over a single connection; both it and the adapters were
retired with the Swing client, KAN-51.)

`GameServices` builds the services in dependency order (education → job; time, food; then
stats) from the ports it is given — it never constructs repositories itself.

## Architectural improvements at a glance

| Concern | Before | After |
|---------|--------|-------|
| Separation | flat `amiss` package mixed Swing, JDBC, config, entities | four layers with a one-way dependency rule |
| Coupling | services → concrete JDBC repositories | services → ports (interfaces); persistence is an implementation detail |
| Wiring | every screen did `new GameServices(user, db)` | one composition root (`PersistenceConfig` + `GameServicesFactory`) |
| UI / DB | screens passed a raw `DB` around | no persistence type in the presentation layer |
| Testability | rules unit-tested against concrete repos | rules unit-tested against port interfaces (same tests, cleaner seam) |
| Scalability | a Spring backend meant reworking the rules | proven: JPA swap (KAN-34) and Swing removal (KAN-51) touched no rules |

