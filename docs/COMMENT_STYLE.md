# Comment conventions

How comments are written and maintained in this repo. Agreed 2026-07-07 during
the `chore/comment-cleanup` pass; apply these rules to all new and edited code.

## Principles

1. **Comments explain *why*, not *what*.** The code already says what it does.
   A comment earns its place by recording something the code cannot show:
   rationale, a gotcha, a workaround, a business rule, an external reference.
2. **Every distinct fact is sacred; every repeated one is noise.** When
   tightening a comment, facts (gotchas, ticket refs, cross-references,
   rationale) must survive. Repetition, asides, and filler must not.
3. **Never let a comment lie.** A stale comment is worse than none. If you
   change code, update or delete the comment that described it. If you *find*
   a comment that contradicts the code and don't know the intent, flag it in
   the PR rather than silently rewriting it.

## Rules by code generation

The repo has two comment generations; they are treated differently.

### Modern code (`amiss-api`, newer `amiss-core` services/infra, all tests)

- Class-level Javadoc states what the class is for and any non-obvious design
  decisions ("deliberately no X because Y"). Reference tickets as `KAN-nn`.
- Keep long rationale blocks **tight**: one paragraph per distinct decision,
  `<p><b>Heading:</b>` style for multi-topic docs (see `SecurityConfig`,
  `MySqlITSupport` for the calibration).
- A gotcha that will bite the next editor gets its own paragraph and says what
  breaks if ignored ("left in place, the container fails to boot").
- Method Javadoc only where the contract isn't obvious from signature + types.
  No `@param`/`@return` lines that restate the parameter name.
- Inline `//` comments: only for genuinely non-obvious lines (e.g. why
  `saveAndFlush` instead of `save`). Sentence case, no trailing period needed
  for fragments.

### Legacy code (Swing GUIs, `domain/model`, HS-era service methods)

- The original HS-project Javadoc (including getter/setter docs) is
  **kept as-is** — it is part of the project's history and the "before" story
  of the portfolio. Don't add more of it; don't churn it.
- The NetBeans license-template headers (`/* To change this license header...`)
  were removed repo-wide and must not come back with editor templates.
- NetBeans Form Editor generated blocks (`//GEN-BEGIN` ... `//GEN-END`,
  `initComponents()`) are machine-owned: never edit, reformat, or comment
  inside them.

## Formatting

- Javadoc for anything public that needs a doc; `//` for local notes.
- Wrap comment lines at ~100 characters, matching the surrounding file.
- Use `{@code ...}` for identifiers/literals and `{@link ...}` for real
  cross-references inside Javadoc.
- Frontend (TypeScript/React): same *why-not-what* principle; the codebase is
  intentionally light on comments — keep it that way.

## Checklist before committing

- [ ] Does each new comment say something the code can't?
- [ ] Did the comments touching changed code get updated with it?
- [ ] No name-restating `@param`/`@return` lines added?
- [ ] Nothing added inside generated Swing blocks?
