Workflow Orchestration

1. Plan Node Default
•Enter plan mode for any non-trivial task (three or more steps, or involving architectural decisions).
•If something goes wrong, stop and re-plan immediately rather than continuing blindly.
•Use plan mode for verification steps, not just implementation.
•Write detailed specifications upfront to reduce ambiguity.

2. Subagent Strategy
•Use subagents liberally to keep the main context window clean.
•Offload research, exploration, and parallel analysis to subagents.
•For complex problems, allocate more compute via subagents.
•Assign one task per subagent to ensure focused execution.

3. Self-Improvement Loop
•After any correction from the user, update tasks/lessons.md with the relevant pattern.
•Create rules for yourself that prevent repeating the same mistake.
•Iterate on these lessons rigorously until the mistake rate declines.
•Review lessons at the start of each session when relevant to the project.

4. Verification Before Done
•Never mark a task complete without proving it works.
•Diff behavior between main and your changes when relevant.
•Ask: “Would a staff engineer approve this?”
•Run tests, check logs, and demonstrate correctness.

5. Demand Elegance (Balanced)
•For non-trivial changes, pause and ask whether there is a more elegant solution.
•If a fix feels hacky, implement the solution you would choose knowing everything you now know.
•Do not over-engineer simple or obvious fixes.
•Critically evaluate your own work before presenting it.

6. Autonomous Bug Fixing
•When given a bug report, fix it without asking for unnecessary guidance.
•Review logs, errors, and failing tests, then resolve them.
•Avoid requiring context switching from the user.
•Fix failing CI tests proactively.

Task Management
1.Plan First: Write the plan to tasks/todo.md with checkable items.
2.Verify Plan: Review before starting implementation.
3.Track Progress: Mark items complete as you go.
4.Explain Changes: Provide a high-level summary at each step.
5.Document Results: Add a review section to tasks/todo.md.
6.Capture Lessons: Update tasks/lessons.md after corrections.

Core Principles
•Simplicity First: Make every change as simple as possible. Minimize code impact.
•No Laziness: Identify root causes. Avoid temporary fixes. Apply senior developer standards.
•Minimal Impact: Touch only what is necessary. Avoid introducing new bugs.

Project Workflow (AmissProj)
1. Context: This project's orientation lives in README.md, SETUP.md and ROADMAP.md. Read them at the start of a session; do not ask the user to re-explain the project.
2. Goals: ROADMAP.md is the canonical long-term plan (phases turning this HS project into a full-stack, CV-ready app). Tick items off there as they ship.
3. Feature branches: Do each unit of work on its own branch off develop — feat/<name> (also fix/, chore/, docs/, test/, refactor/). PRs target develop (gh pr create --base develop); when a milestone is ready, a release PR merges develop into main. main is protected (PR + green CI build check) and stays runnable. Work in small, descriptive commits; push the branch and open the PR (gh CLI) for the user to review/merge.
4. Commits in PowerShell: use git commit -F <file> with the message written via Set-Content -Encoding ascii. Never embed double-quotes inside a -m here-string (PowerShell 5.1 splits the args). Always end commit messages with the Co-Authored-By trailer.
5. Definition of done: behaviour verified, ROADMAP/docs updated, branch pushed.
6. CV highlights: every feature PR must include an entry in tasks/cv-highlights.md for what it ships — resume-grade bullets plus a talking point, matching the file's existing format (newest at the bottom of its phase). Write it on the PR branch before opening the PR; if a merged PR was missed, backfill it in the next PR.
