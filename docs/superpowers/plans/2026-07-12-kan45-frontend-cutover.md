# KAN-45 Frontend Cutover (PR 6) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **No Advisor Protocol in this plan (unlike the KAN-54/PR-5 advisor-run plan it
> follows).** PR 5's extra Fable-gated review layer existed because of destructive,
> one-way DDL and an auth/credential contract change — neither applies here. This is
> a frontend-only rewrite against an already-shipped, already-reviewed API; ordinary
> subagent-driven-development (implementer + task-reviewer per task, one final
> whole-branch review at the end on the most capable available model) is the right
> amount of rigor.

**Goal:** Cut the React SPA over from the retired per-user `/api/players/{username}/...`
routes to the save-scoped `/api/saves/{saveId}/...` API that shipped in PR 5 (KAN-54,
merged 2026-07-12): a saves list/create/delete screen, a two-step hidden-requirements
Employment Office, a degree-tree University panel, per-save goal bars, a win banner, and
removal of the retired highscores feature.

**Architecture:** No new frontend architecture — this follows the existing hand-rolled
`api/*.ts` + TanStack Query + React Router structure exactly. The only structural
addition is a save-selection step in the URL (`/saves` → `/play/:saveId`) where today
there's an implicit single game per account.

**Tech Stack:** React 19, TypeScript 5.8 (strict), Vite 7, React Router 7, TanStack Query
5, Vitest 4 + Testing Library, ESLint 9 + Prettier 3 (flat config). No MSW, no shared UI
library, no CSS modules, no path aliases — every import is relative.

**Spec:** `docs/superpowers/specs/2026-07-07-jobs-degrees-saves-design.md` (Frontend
section, lines 175-192) and JIRA KAN-45 (full description quoted below) are normative.
**Parent plan:** `docs/superpowers/plans/2026-07-07-jobs-degrees-saves.md` (Task 6, lines
321-341) sketched this PR at a file-list level; this plan supersedes that sketch with
exact code. **Backend reference:** `docs/superpowers/plans/2026-07-11-kan54-api-cutover-advisor-run.md`
and `.superpowers/sdd/progress.md` on the (now-merged, now-deleted) `feat/saves-employment-api`
branch document exactly what PR 5 shipped — that ledger file was local/gitignored and may
no longer exist; this plan's "Backend reality check" section below already extracted
everything from it that matters for the frontend.

**JIRA KAN-45 (verbatim, fetched 2026-07-12, status was "To Do"):**
> Rescoped 2026-07-07 (this was the design input the ticket was on hold for): highscores
> are RETIRED — meaningless when every save has its own goal targets (Rourke's call). The
> leaderboard half of the old scope is deleted, not built.
>
> Scope (plan Task 6 / PR 6, merged immediately after KAN-54 — that PR breaks the SPA's
> routes):
> - Saves screen after login: list / continue / delete; New game → four-goal setup
>   (Wealth/Happiness/Education/Career, 10–100 sliders + Randomise).
> - All api modules re-path to `/api/saves/{saveId}`; query keys `['save', saveId]`.
> - Employment Office panel: two-step (pick workplace → jobs with name + wage only); every
>   Apply enabled; NO requirement badges or disabled buttons anywhere; officer-style
>   rejection copy (multi-reason, No Openings).
> - University panel: 11-degree course tree (earned/available/locked + prereqs).
> - HUD: per-save goal bars; degrees shown; experience/dependability never rendered.
> - Win banner on the first `won` transition; play continues after winning (wiki rule).
> - Delete highscores page/api/nav + HomePage leaderboard.
>
> Acceptance: a player who meets all four goals at a week rollover sees the win flow;
> requirements are undiscoverable before applying (UI and network tab).

## Global Constraints

- Branch `feat/frontend-saves-employment` off `develop`; PR targets `develop`
  (`gh pr create --base develop`). Stacked/merged immediately — PR 5 already shipped, so
  `develop`'s SPA is currently broken; this PR fixes it. Only Rourke merges.
- Commit trailer: check `git log -3` on `develop` before your first commit and match
  whatever convention is already there (it was `Co-Authored-By: Claude Opus 4.8
  <noreply@anthropic.com>` as of 2026-07-12, but verify fresh — don't trust this plan's
  memory of it). Stage explicit paths only (never `git add -A`/`git add .`); message via a
  temp file + `git commit -F <file>`.
- Frontend build/verify, in this exact order (matches `.github/workflows/ci.yml`'s
  `frontend` job — keep every task's end-state clean through this whole sequence, not
  just `test`): `cd frontend && npm run lint && npm run format:check && npm run typecheck
  && npm test && npm run build`.
- No backend changes in this plan. If you find a backend gap (missing endpoint, wrong DTO
  shape, a route that doesn't behave as this plan says), that's an escalation — stop and
  report it rather than working around it; PR 5 is merged and reviewed, so a real gap
  there is either a plan error or a genuine bug worth a separate fix.
- Hidden-information contract: the new backend DTOs (below) structurally cannot carry
  `experience`/`dependability`/job-requirement fields — there is nothing to "hide" in the
  UI because the data was never sent. Never add a field to any local type that isn't in
  the DTOs this plan defines; if you find yourself wanting one, that's a sign of a
  misunderstanding, not a missing feature — stop and ask.
- Charged-rejection convention (existing, keep it): every mutation's `onSuccess` calls
  `queryClient.setQueryData(['save', saveId], response.state)` — never
  `invalidateQueries` on success. `onError` invalidates `['save', saveId]` for any action
  that can charge time/money server-side even on a "rejection" (apply, work, move,
  enroll, study, eat, groceries, clothes, bank, rent, end-week all do); a pure
  client-side-validated action (e.g. amount parsing) that never reaches the server on
  failure does not need an error-path invalidation.
- No shared UI library exists (plain HTML elements + two global stylesheets:
  `src/index.css` for page chrome, `src/game/board.css` for game-screen/HUD/panels). New
  screens get their own small stylesheet imported once at the top-level component that
  owns that screen — don't scatter styles for one screen across multiple files.
- Match the existing test house style exactly (all verified against real files in this
  codebase, not invented): Vitest + Testing Library, `vi.mock('../../api/xxx', () => ({
  fnName: vi.fn() }))` + `vi.mocked(fnName)`, `beforeEach(() => vi.resetAllMocks())`, a
  **fresh** `new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: {
  retry: false } } })` per test (never shared/module-level), a local fixture builder
  function per test file (this codebase does not share fixtures across test files —
  don't introduce a shared one unilaterally), `userEvent.click`/`waitFor`/`screen.findByRole('alert')`
  for error assertions, `queryClient.invalidateQueries` spied via `vi.spyOn` where the
  error-path convention above applies.

---

### Task 1: API client layer — save-scoped routes, new DTOs, saves CRUD

**Files:**
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/api/http.ts` (add one export, `errorMessage`)
- Modify: `frontend/src/api/http.test.ts` (one-line fix, see Step 6)
- Modify: `frontend/src/api/player.ts`, `frontend/src/api/bank.ts`, `frontend/src/api/rent.ts`,
  `frontend/src/api/jobs.ts`, `frontend/src/api/university.ts`, `frontend/src/api/food.ts`,
  `frontend/src/api/clothes.ts`
- Create: `frontend/src/api/saves.ts`
- Test: `frontend/src/api/saves.test.ts`
- Delete: `frontend/src/api/highscores.ts`
- Not touched: `frontend/src/api/auth.ts`, `frontend/src/api/board.ts` (both call unscoped
  routes — confirmed unchanged by PR 5).

**Interfaces produced (for every later task):**
- Every type in the new `types.ts` below (`SaveStateDto`, `SaveSummaryDto`, `GoalsDto`,
  `GoalDto`, `JobListingDto`, `CourseDto`, etc.).
- `listSaves(): Promise<SaveSummaryDto[]>`, `createSave(request: CreateSaveRequest):
  Promise<SaveSummaryDto>`, `deleteSave(saveId: number): Promise<void>` — `api/saves.ts`.
- `errorMessage(err: unknown): string` — `api/http.ts`.
- `getSaveState(saveId: number): Promise<SaveStateDto>`, `move(saveId: number, target:
  string): Promise<MoveResponse>`, `endWeek(saveId: number): Promise<EndWeekResponse>` —
  `api/player.ts` (filename unchanged, signatures changed from `username: string`).
- `deposit`/`withdraw(saveId: number, amount: number): Promise<BankTransactionResponse>` —
  `api/bank.ts`.
- `payRent(saveId: number): Promise<RentPaymentResponse>` — `api/rent.ts`.
- `getJobs(location?: string): Promise<JobListingDto[]>`, `applyForJob(saveId: number,
  jobId: number): Promise<ApplyResponse>`, `work(saveId: number): Promise<WorkResponse>` —
  `api/jobs.ts`.
- `getCourses(saveId: number): Promise<CourseDto[]>`, `enroll(saveId: number, degreeId:
  number): Promise<EnrollResponse>`, `study(saveId: number): Promise<StudyResponse>` —
  `api/university.ts`.
- `getFoodCatalog(): Promise<FoodCatalogDto>`, `eat(saveId: number, item: string):
  Promise<EatResponse>`, `buyGroceries(saveId: number, pack: string):
  Promise<GroceriesResponse>` — `api/food.ts`.
- `getClothesCatalog(): Promise<ClothingItemDto[]>`, `buyClothes(saveId: number, item:
  string): Promise<ClothesResponse>` — `api/clothes.ts`.

- [ ] **Step 1: Replace `frontend/src/api/types.ts` in full.**

```typescript
export interface RegisterRequest {
  username: string;
  password: string;
}
export interface RegisterResponse {
  username: string;
}
export interface LoginRequest {
  username: string;
  password: string;
}
export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
}
export interface MeResponse {
  username: string;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
}

export interface LocationDto {
  id: string;
  name: string;
  ringIndex: number;
  row: number;
  col: number;
}
export interface TravelDto {
  minutesPerStep: number;
  enterBuildingMinutes: number;
}
export interface BoardDto {
  stops: LocationDto[];
  travel: TravelDto;
}

export interface JobDto {
  name: string;
  hourlyWage: number | null;
  location: string | null;
}

export interface GoalDto {
  current: number;
  target: number;
  met: boolean;
}
export interface GoalsDto {
  wealth: GoalDto;
  happiness: GoalDto;
  education: GoalDto;
  career: GoalDto;
}

export interface CurrentCourseDto {
  id: number;
  name: string;
  studiesDone: number;
}

export interface SaveStateDto {
  id: number;
  label: string;
  round: number;
  timeMinutes: number;
  timeDisplay: string;
  weekOver: boolean;
  cash: number;
  bank: number;
  debt: number;
  rentDue: boolean;
  foodWeeks: number;
  clothing: number;
  job: JobDto;
  location: LocationDto;
  degreesEarned: string[];
  currentCourse: CurrentCourseDto | null;
  goals: GoalsDto;
  won: boolean;
}

export interface SaveSummaryDto {
  id: number;
  label: string;
  round: number;
  cash: number;
  won: boolean;
  updatedAt: string;
}
export interface GoalTargets {
  wealth: number;
  happiness: number;
  education: number;
  career: number;
}
export interface CreateSaveRequest {
  label: string;
  goals?: GoalTargets;
  random?: boolean;
}

export interface MoveRequest {
  target: string;
}
export interface MoveResponse {
  target: string;
  steps: number;
  minutesCharged: number;
  state: SaveStateDto;
}

export interface EndWeekResponse {
  round: number;
  fed: boolean;
  rentDue: boolean;
  debtCharged: boolean;
  won: boolean;
  state: SaveStateDto;
}

export interface BankTransactionResponse {
  operation: string;
  amount: number;
  state: SaveStateDto;
}
export interface RentPaymentResponse {
  amountPaid: number;
  minutesCharged: number;
  state: SaveStateDto;
}

export interface JobListingDto {
  id: number;
  name: string;
  location: string;
  wage: number;
}
export interface ApplyResponse {
  hired: boolean;
  reasons: string[];
  minutesCharged: number;
  job: string;
  wage: number | null;
  state: SaveStateDto;
}
export interface WorkResponse {
  status: 'OK' | 'FIRED';
  warning: boolean;
  job: string;
  pay: number;
  netPaid: number;
  garnished: number;
  minutesCharged: number;
  state: SaveStateDto;
}

export interface CourseDto {
  id: number;
  name: string;
  status: 'EARNED' | 'AVAILABLE' | 'LOCKED';
  prereqName: string | null;
  enrolled: boolean;
  studiesDone: number;
}
export interface EnrollResponse {
  feePaid: number;
  state: SaveStateDto;
}
export interface StudyResponse {
  studiesDone: number;
  studiesRemaining: number;
  degreeCompleted: string | null;
  minutesCharged: number;
  state: SaveStateDto;
}
```

  **The five food/clothes DTOs below are UNCHANGED by PR 5 except the embedded state
  field.** Do not invent their field names — open the current `types.ts` (via `git show
  develop:frontend/src/api/types.ts`) and copy `MenuItemDto`, `FoodPackDto`,
  `FoodCatalogDto`, `ClothingItemDto` byte-for-byte into the new file; only change
  `EatResponse`/`GroceriesResponse`/`ClothesResponse`'s `state: PlayerStateDto` field to
  `state: SaveStateDto`, keeping every other field on those three response types
  unchanged.
  **Delete** (no successor): `HighscoreEntry`, `PlayerStateDto`, `StatsDto`, `GoalDto`'s
  old two-field shape (replaced above), `CoursesDto` (the new `GET
  /saves/{saveId}/courses` returns `CourseDto[]` directly, not a wrapper object).

- [ ] **Step 2: Add `errorMessage` to `frontend/src/api/http.ts`.** Open the file, find
  the existing `export class ApiError extends Error { ... }`, and add this export after
  it (this is the exact body every panel currently duplicates locally — verified
  identical across `BoardScreen.tsx`, `WorkAction.tsx`, `BankPanel.tsx`, and six panel
  files):

```typescript
export function errorMessage(err: unknown): string {
  return err instanceof ApiError
    ? (err.problem.detail ?? err.problem.title ?? err.message)
    : 'Could not reach the server.';
}
```

  Do not change anything else in `http.ts` — `apiFetch`/`ApiError`/`onUnauthorized` are
  unaffected by this whole plan (the wrapper is resource-agnostic; only the per-resource
  modules below change).

- [ ] **Step 3: Delete `frontend/src/api/highscores.ts`.**

- [ ] **Step 4: Create `frontend/src/api/saves.ts`.**

```typescript
import { apiFetch } from './http';
import type { CreateSaveRequest, SaveSummaryDto } from './types';

export function listSaves(): Promise<SaveSummaryDto[]> {
  return apiFetch<SaveSummaryDto[]>('/saves');
}

export function createSave(request: CreateSaveRequest): Promise<SaveSummaryDto> {
  return apiFetch<SaveSummaryDto>('/saves', { method: 'POST', body: request });
}

export function deleteSave(saveId: number): Promise<void> {
  return apiFetch<void>(`/saves/${saveId}`, { method: 'DELETE' });
}
```

- [ ] **Step 5: Write `frontend/src/api/saves.test.ts` (RED then GREEN)** — this is the
  only api module in this task that gets a dedicated test file, matching the existing
  convention that only `http.ts` has direct unit tests today; `saves.ts` is new and has
  no consuming component yet to exercise it indirectly, so it needs its own thin test.
  Follow `frontend/src/api/http.test.ts`'s exact mocking style (`vi.stubGlobal('fetch',
  ...)`), which you should read first for the fixture-response-builder pattern it uses.

```typescript
import { describe, expect, it, vi } from 'vitest';
import { createSave, deleteSave, listSaves } from './saves';

function fakeResponse(status: number, body: unknown): Response {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('api/saves', () => {
  it('listSaves calls GET /api/saves', async () => {
    const saves = [{ id: 1, label: 'Save 1', round: 1, cash: 100, won: false, updatedAt: '2026-01-01T00:00:00Z' }];
    const fetchMock = vi.fn().mockResolvedValue(fakeResponse(200, saves));
    vi.stubGlobal('fetch', fetchMock);

    await expect(listSaves()).resolves.toEqual(saves);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/saves');
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: undefined });

    vi.unstubAllGlobals();
  });

  it('createSave POSTs the request body to /api/saves', async () => {
    const created = { id: 2, label: 'New', round: 1, cash: 100, won: false, updatedAt: '2026-01-01T00:00:00Z' };
    const fetchMock = vi.fn().mockResolvedValue(fakeResponse(201, created));
    vi.stubGlobal('fetch', fetchMock);

    const request = { label: 'New', random: true };
    await expect(createSave(request)).resolves.toEqual(created);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/saves');
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body as string)).toEqual(request);

    vi.unstubAllGlobals();
  });

  it('deleteSave DELETEs /api/saves/{id} and resolves undefined on 204', async () => {
    const fetchMock = vi.fn().mockResolvedValue(fakeResponse(204, undefined));
    vi.stubGlobal('fetch', fetchMock);

    await expect(deleteSave(7)).resolves.toBeUndefined();
    expect(fetchMock.mock.calls[0][0]).toBe('/api/saves/7');
    expect((fetchMock.mock.calls[0][1] as RequestInit).method).toBe('DELETE');

    vi.unstubAllGlobals();
  });
});
```

  Run: `cd frontend && npx vitest run src/api/saves.test.ts` — expected FAIL first
  (`saves.ts` doesn't exist yet if you write the test before Step 4; if you did Step 4
  first, write this test, temporarily rename `saves.ts`'s export to confirm the test
  actually fails on a real assertion, then rename back — either order is fine as long as
  you have genuine RED evidence, not just a passing first run). Then GREEN: same command,
  expected PASS, 3/3.

  Note the exact assertion style: this codebase's `apiFetch` prefixes every path with
  `/api` internally (confirmed in `http.ts`), so the mock's captured URL is `/api/saves`,
  not `/saves` — match `http.test.ts`'s existing assertions for the precise expected URL
  shape rather than guessing.

- [ ] **Step 6: Fix the stray highscores reference in `frontend/src/api/http.test.ts`.**
  Read the file, find the test asserting no Bearer header is sent for an unauthenticated
  request (it uses `/highscores` as an arbitrary example path — the test's actual intent
  has nothing to do with highscores, it just needs any unscoped GET route). Change the
  literal path string from `/highscores` to `/board` (still a real, unscoped route after
  PR 5). Do not change anything else in this test.

- [ ] **Step 7: Rewrite the six thin api modules.** Each of these is a mechanical
  signature change — `username: string` params become `saveId: number`, and every
  `` `/players/${encodeURIComponent(username)}/...` `` path template becomes
  `` `/saves/${saveId}/...` ``. Full new content for each (replace the file entirely —
  each is short enough that a full replacement is clearer than a diff):

`frontend/src/api/player.ts`:
```typescript
import { apiFetch } from './http';
import type { EndWeekResponse, MoveResponse, SaveStateDto } from './types';

export function getSaveState(saveId: number): Promise<SaveStateDto> {
  return apiFetch<SaveStateDto>(`/saves/${saveId}`);
}

export function move(saveId: number, target: string): Promise<MoveResponse> {
  return apiFetch<MoveResponse>(`/saves/${saveId}/move`, { method: 'POST', body: { target } });
}

export function endWeek(saveId: number): Promise<EndWeekResponse> {
  return apiFetch<EndWeekResponse>(`/saves/${saveId}/end-week`, { method: 'POST' });
}
```

`frontend/src/api/bank.ts`:
```typescript
import { apiFetch } from './http';
import type { BankTransactionResponse } from './types';

export function deposit(saveId: number, amount: number): Promise<BankTransactionResponse> {
  return apiFetch<BankTransactionResponse>(`/saves/${saveId}/bank/deposit`, {
    method: 'POST',
    body: { amount },
  });
}

export function withdraw(saveId: number, amount: number): Promise<BankTransactionResponse> {
  return apiFetch<BankTransactionResponse>(`/saves/${saveId}/bank/withdraw`, {
    method: 'POST',
    body: { amount },
  });
}
```

`frontend/src/api/rent.ts`:
```typescript
import { apiFetch } from './http';
import type { RentPaymentResponse } from './types';

export function payRent(saveId: number): Promise<RentPaymentResponse> {
  return apiFetch<RentPaymentResponse>(`/saves/${saveId}/rent/pay`, { method: 'POST' });
}
```

`frontend/src/api/jobs.ts`:
```typescript
import { apiFetch } from './http';
import type { ApplyResponse, JobListingDto, WorkResponse } from './types';

export function getJobs(location?: string): Promise<JobListingDto[]> {
  const query = location ? `?location=${encodeURIComponent(location)}` : '';
  return apiFetch<JobListingDto[]>(`/jobs${query}`);
}

export function applyForJob(saveId: number, jobId: number): Promise<ApplyResponse> {
  return apiFetch<ApplyResponse>(`/saves/${saveId}/jobs/apply`, {
    method: 'POST',
    body: { jobId },
  });
}

export function work(saveId: number): Promise<WorkResponse> {
  return apiFetch<WorkResponse>(`/saves/${saveId}/work`, { method: 'POST' });
}
```

`frontend/src/api/university.ts`:
```typescript
import { apiFetch } from './http';
import type { CourseDto, EnrollResponse, StudyResponse } from './types';

export function getCourses(saveId: number): Promise<CourseDto[]> {
  return apiFetch<CourseDto[]>(`/saves/${saveId}/courses`);
}

export function enroll(saveId: number, degreeId: number): Promise<EnrollResponse> {
  return apiFetch<EnrollResponse>(`/saves/${saveId}/courses/${degreeId}/enroll`, {
    method: 'POST',
  });
}

export function study(saveId: number): Promise<StudyResponse> {
  return apiFetch<StudyResponse>(`/saves/${saveId}/courses/study`, { method: 'POST' });
}
```

  For `frontend/src/api/food.ts` and `frontend/src/api/clothes.ts`: keep
  `getFoodCatalog()`/`getClothesCatalog()` exactly as they are today (both call unscoped
  routes, confirmed unchanged by PR 5) — only change `eat`, `buyGroceries`, and
  `buyClothes` from a `username: string` first param to `saveId: number`, and their path
  templates from `` `/players/${username}/...` `` to `` `/saves/${saveId}/...` ``, same
  pattern as above. Read the current files first and edit in place rather than
  reconstructing them from scratch, since this plan doesn't have their exact current
  catalog-fetching code verified byte-for-byte.

- [ ] **Step 8: Verify.** `cd frontend && npm run typecheck` — expected: errors in every
  file this task didn't touch yet (BoardScreen, Hud, WorkAction, all panels, App.tsx,
  LoginPage) because they still call the old signatures. **This is expected and correct**
  — those errors are exactly the checklist Task 2/3/5 work through; do not try to silence
  them in this task. Confirm the errors are ONLY in those not-yet-touched files and
  nothing under `api/` itself, then run `npx vitest run src/api/` (scoped to this task's
  own files) — expected PASS.

- [ ] **Step 9: Commit.**

```bash
cd frontend
git add src/api/types.ts src/api/http.ts src/api/http.test.ts src/api/player.ts \
  src/api/bank.ts src/api/rent.ts src/api/jobs.ts src/api/university.ts src/api/food.ts \
  src/api/clothes.ts src/api/saves.ts src/api/saves.test.ts
git rm src/api/highscores.ts
git status --short
```
  Write the commit message to a temp file: `feat: save-scoped API client + saves CRUD (KAN-45)`,
  body noting this is Task 1 of the frontend cutover and the rest of the app won't
  typecheck until Task 2/3/5 land (expected, not a regression). `git commit -F <file>`.

---

### Task 2: Game screen — save-scoped state, Employment two-step flow, University course tree

**A note on precision in this task:** for files this plan is *editing* (not creating),
every instruction below gives the exact current code (verified against the real files) and
the exact replacement — read the named file first and locate the quoted snippet before
editing; don't reconstruct the whole file from this plan's description. For
`EmploymentOfficePanel.tsx` and `UniversityPanel.tsx`, the new UI (two-step workplace
grid, course tree) doesn't exist in any form today, so those two get complete new file
content instead.

**Files:**
- Modify: `frontend/src/game/panels/types.ts`
- Modify: `frontend/src/game/BoardScreen.tsx` (+test)
- Modify: `frontend/src/game/Hud.tsx` (+test)
- Modify: `frontend/src/game/WorkAction.tsx` (+test)
- Modify: `frontend/src/game/panels/BankPanel.tsx`, `RentOfficePanel.tsx`,
  `MonolithBurgersPanel.tsx`, `BlacksMarketPanel.tsx`, `QTClothingPanel.tsx`,
  `HomePanel.tsx` (+ their tests) — mechanical prop-shape only
- Modify (full rewrite): `frontend/src/game/panels/EmploymentOfficePanel.tsx` (+test),
  `frontend/src/game/panels/UniversityPanel.tsx` (+test)
- Not touched: `frontend/src/game/panels/DefaultPanel.tsx` (no API/prop-field usage to
  break), `frontend/src/game/panels/StorePanel.tsx` (already resource-agnostic),
  `frontend/src/game/panels/registry.ts` (maps location id → component, no signature
  change), `frontend/src/game/formatMinutes.ts`, `frontend/src/game/ring.ts` (both
  already save-agnostic, confirmed unchanged by PR 5).

**Interfaces produced (for Task 3+):** `BoardScreen` takes `{ saveId: number }` instead of
`{ username: string }`. Every `PanelProps` consumer takes `saveId: number` instead of
`username: string`.

- [ ] **Step 1: `frontend/src/game/panels/types.ts` — full replacement (8 lines today):**

```typescript
import type { SaveStateDto } from '../../api/types';

export interface PanelProps {
  saveId: number;
  player: SaveStateDto;
  onNotify: (message: string) => void;
}
```

- [ ] **Step 2: `frontend/src/game/BoardScreen.tsx` — targeted edits.** Read the file
  first. Make these exact changes, leaving everything else (hotspot positioning math,
  notification feed rendering, `role="log"`, `MAX_NOTIFICATIONS`, styling) untouched:
  - Component signature: `{ username: string }` → `{ saveId: number }` (the prop this
    component receives from its caller — Task 3 updates the caller).
  - `import { getPlayerState, move, endWeek } from '../api/player'` →
    `import { getSaveState, move, endWeek } from '../api/player'`.
  - `import type { PlayerStateDto, ... } from '../api/types'` → replace `PlayerStateDto`
    with `SaveStateDto` in this import and everywhere else in the file it's used as a
    type annotation.
  - `useQuery({ queryKey: ['player', username], queryFn: () => getPlayerState(username) })`
    → `useQuery({ queryKey: ['save', saveId], queryFn: () => getSaveState(saveId) })`.
  - `moveMutation`'s `mutationFn: (target: string) => move(username, target)` →
    `mutationFn: (target: string) => move(saveId, target)`; its `onSuccess` /
    `onError`'s `queryClient.setQueryData(['player', username], ...)` /
    `invalidateQueries({ queryKey: ['player', username] })` → `['save', saveId]` in both
    places.
  - `endWeekMutation`'s `mutationFn: () => endWeek(username)` → `mutationFn: () =>
    endWeek(saveId)`, same `['player', username]` → `['save', saveId]` swap in its
    `onSuccess`/`onError`.
  - Every prop passed down to `Hud`, `WorkAction`, and `resolvePanel(...)`'s component
    that was `username={username}` becomes `saveId={saveId}`.
  - Add `import { errorMessage } from '../api/http';` and replace this file's local
    `errorMessage` function definition with the import (delete the local copy — this is
    Task 1's Step 2 export, now consumed here for the first time).

- [ ] **Step 3: `frontend/src/game/BoardScreen.test.tsx` — targeted edits.** This is the
  largest test file (409 lines) and mocks every api module up front. Read it first, then:
  every literal `'alice'` (or whatever username fixture string it uses) passed as the
  component prop becomes a numeric save id fixture (e.g. `42`); every `['player', 'alice']`
  (or equivalent) query-key assertion becomes `['save', 42]`; every mocked
  `getPlayerState`/`move`/`endWeek` call-argument assertion
  (`toHaveBeenCalledWith('alice', ...)`) becomes `toHaveBeenCalledWith(42, ...)`; the
  `vi.mock('../api/player', ...)` mock factory's `getPlayerState` becomes `getSaveState`.
  The player-state fixture object built in this file must be updated to the new
  `SaveStateDto` shape from Task 1 (in particular: `job` is never `null` — use `{ name:
  'Unemployed', hourlyWage: null, location: null }` for the unemployed case, not `null`
  itself; `goals` uses the new `{wealth,happiness,education,career}` keys, each a full
  `GoalDto{current,target,met}`; add `won: false`, `degreesEarned: []`, `currentCourse:
  null`, `label`, `id`). Run `npx vitest run src/game/BoardScreen.test.tsx` after editing
  — expected PASS once Steps 4-9 below also land (this file mocks the panels too, so it
  won't be fully green until the panel files are updated — that's fine, come back to
  confirm green after Step 9).

- [ ] **Step 4: `frontend/src/game/Hud.tsx` — targeted edits.** Read the file first (118
  lines). Three changes:
  1. Replace the `GOALS` array:
```typescript
const GOALS: { key: keyof GoalsDto; label: string }[] = [
  { key: 'wealth', label: 'Wealth' },
  { key: 'happiness', label: 'Happiness' },
  { key: 'education', label: 'Education' },
  { key: 'career', label: 'Career' },
];
```
     (was `{ key: 'cash', label: 'Cash' }, { key: 'happiness', ... }, { key:
     'workExperience', ... }, { key: 'education', ... }` — `cash`/`workExperience` don't
     exist on the new `GoalsDto`.) Wherever this array is mapped to render a bar, it reads
     `player.goals[key].current` / `.target` — both still exist on the new `GoalDto`, no
     change needed to the rendering loop itself beyond the array above (the new `GoalDto`
     also has a `met` boolean now; optionally style a met goal's bar differently, e.g. a
     `met` CSS class — not required by the acceptance criteria, use your judgment, don't
     over-build it).
  2. Replace the `job === null ? 'Unemployed' : ...` conditional. The new backend never
     sends `job: null` (an unemployed save gets `{name: 'Unemployed', hourlyWage: null,
     location: null}`) — so the equivalent, correct check is now on the *value*, not
     presence:
```typescript
player.job.hourlyWage === null ? player.job.name : `${player.job.name} — R${player.job.hourlyWage}/h at ${player.job.location}`
```
     (adapt to match this file's exact existing JSX/string-building style once you've
     read it — the point is: stop checking `job === null`, check `job.hourlyWage === null`
     instead, since `job` itself is now always an object.)
  3. Replace any line reading `stats.education` / `stats.educationProgress` /
     `stats.happiness` / `stats.workExperience` (the old `StatsDto`, deleted in Task 1) —
     there is no successor `stats` field. Happiness's current value is
     `player.goals.happiness.current`. Education is now `player.degreesEarned` (a
     `string[]` of earned degree names — render as a list or comma-joined string, e.g.
     `player.degreesEarned.length > 0 ? player.degreesEarned.join(', ') : 'None yet'`) plus
     `player.currentCourse` (nullable — when non-null it has `{id, name, studiesDone}`;
     render e.g. `Studying ${player.currentCourse.name} (${player.currentCourse.studiesDone}/10)`
     when present). Do not render `experience`/`dependability` anywhere — they don't exist
     on this DTO, so there's nothing to accidentally leak, but don't add a stat row for
     anything not listed in `SaveStateDto` (Task 1, Step 1).

- [ ] **Step 5: `frontend/src/game/Hud.test.tsx` — targeted edits.** Update the fixture
  `PlayerStateDto` object to `SaveStateDto` shape (same field changes as Step 3's
  BoardScreen fixture). Update any assertion that checked for old goal labels ("Cash",
  "Work Experience") to the new labels ("Wealth", "Career"). Update any assertion on the
  old `job === null` "Unemployed" rendering to construct the fixture with `job: { name:
  'Unemployed', hourlyWage: null, location: null }` instead of `job: null` (this will not
  compile against the new `SaveStateDto` type otherwise). Run `npx vitest run
  src/game/Hud.test.tsx` — expected PASS.

- [ ] **Step 6: `frontend/src/game/WorkAction.tsx` — targeted edits.** Read the file first
  (53 lines). Three changes:
  1. Guard condition `if (player.job === null || player.location.name !==
     player.job.location) return null;` → `if (player.job.hourlyWage === null ||
     player.location.name !== player.job.location) return null;` (same null-sentinel fix
     as Hud's Step 4.2 — `job.hourlyWage === null` is the new "unemployed" signal).
  2. `mutationFn: () => work(username)` → `mutationFn: () => work(saveId)` (component
     props: `username` → `saveId`, matching the shared `PanelProps` from Step 1).
  3. The success-message branch reading `res.debtDocked` and `res.hourlyWage` (both
     gone from the new `WorkResponse`) must be rewritten against the new fields:
     `res.status` (`'OK' | 'FIRED'`), `res.warning` (boolean), `res.pay`, `res.netPaid`,
     `res.garnished`. Suggested message logic (adapt to this file's existing string
     style once read):
```typescript
if (res.status === 'FIRED') {
  onNotify(`You've been fired from ${res.job}.`);
} else if (res.garnished > 0) {
  onNotify(`Worked a shift as ${res.job}: earned R${res.pay}, R${res.garnished} garnished for debt (net R${res.netPaid}).${res.warning ? ' Final warning.' : ''}`);
} else {
  onNotify(`Worked a shift as ${res.job}: earned R${res.netPaid}.${res.warning ? ' Final warning.' : ''}`);
}
```

- [ ] **Step 7: `frontend/src/game/WorkAction.test.tsx` — targeted edits.** Same fixture
  update as Steps 3/5. Update the `work` mock's call-argument assertion to the numeric
  saveId. Update any assertion on the old success-message text to match Step 6.3's new
  branching (a `FIRED` case, a `garnished > 0` case, and a plain-pay case are the three
  behaviors worth a test each if the existing file only tested one — check what's there
  and add the missing cases rather than assuming full coverage already exists). Run `npx
  vitest run src/game/WorkAction.test.tsx` — expected PASS.

- [ ] **Step 8: Six mechanical panel updates.** For each of `BankPanel.tsx`,
  `RentOfficePanel.tsx`, `MonolithBurgersPanel.tsx`, `BlacksMarketPanel.tsx`,
  `QTClothingPanel.tsx`, `HomePanel.tsx` (and their `.test.tsx` files): read the file,
  then apply exactly this transform — every destructured `username` prop becomes
  `saveId`; every call to `deposit`/`withdraw`/`payRent`/`eat`/`buyGroceries`/`buyClothes`
  passes `saveId` instead of `username` as the first argument (matching Task 1's Step 7
  new signatures); replace each file's local `errorMessage` function with `import {
  errorMessage } from '../../api/http';` (same as BoardScreen's Step 2 change — these
  panels are one directory deeper, so the relative path has an extra `../`). `HomePanel.tsx`
  has no API calls (display-only per the research) — it still needs the `PanelProps`
  destructuring updated from `username` to `saveId` even though it may not use either
  value directly (check whether it's actually unused after the change; if so, and the
  linter/`noUnusedParameters` complains, destructure only `{ player }` and drop `saveId`
  entirely from that one file's destructuring — don't keep an unused variable to satisfy
  a mechanical pattern). For each test file: fixture update (Step 3's pattern), mock
  call-argument assertions updated to numeric saveId, query key assertions (`['food']`,
  `['clothes']` are unscoped and unaffected — only `['player', username]`-shaped keys, if
  any appear in these specific files, become `['save', saveId]`). Run `npx vitest run
  src/game/panels/BankPanel.test.tsx src/game/panels/RentOfficePanel.test.tsx
  src/game/panels/MonolithBurgersPanel.test.tsx src/game/panels/BlacksMarketPanel.test.tsx
  src/game/panels/QTClothingPanel.test.tsx src/game/panels/HomePanel.test.tsx` — expected
  PASS, 6/6 files green.

- [ ] **Step 9: `frontend/src/game/panels/EmploymentOfficePanel.tsx` — full replacement**
  (the old single-flat-list-with-requirement-badges UI is gone; this is a genuine
  rewrite, not a mechanical edit). A note on "every Apply enabled": this means never
  disabling or badging a job based on hidden requirement data (there is none to check
  client-side) — it does NOT mean the button must stay clickable mid-request; disabling
  it for the duration of its own pending mutation is normal double-submit protection, not
  a requirements gate, and is expected here.

```tsx
import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { applyForJob, getJobs } from '../../api/jobs';
import { errorMessage } from '../../api/http';
import type { PanelProps } from './types';

const REJECTION_COPY: Record<string, string> = {
  NOT_ENOUGH_EDUCATION: "You don't have the degree this position needs.",
  NOT_ENOUGH_EXPERIENCE: "You don't have enough work experience for this position.",
  POOR_WORK_HISTORY: "Your work history doesn't meet our standards for this position.",
  NO_OPENINGS: 'Sorry, there are no openings right now, try again another day.',
};

function rejectionMessage(reasons: string[]): string {
  if (reasons.length === 0) {
    return 'Application declined.';
  }
  return reasons.map((reason) => REJECTION_COPY[reason] ?? 'Application declined.').join(' ');
}

export function EmploymentOfficePanel({ saveId, onNotify }: PanelProps) {
  const [workplace, setWorkplace] = useState<string | null>(null);
  const queryClient = useQueryClient();

  const jobsQuery = useQuery({ queryKey: ['jobs'], queryFn: () => getJobs(), staleTime: Infinity });

  const workplaces = useMemo(() => {
    if (!jobsQuery.data) return [];
    const seen = new Set<string>();
    const result: string[] = [];
    for (const listing of jobsQuery.data) {
      if (!seen.has(listing.location)) {
        seen.add(listing.location);
        result.push(listing.location);
      }
    }
    return result;
  }, [jobsQuery.data]);

  const jobsAtWorkplace = useMemo(
    () => (jobsQuery.data ?? []).filter((listing) => listing.location === workplace),
    [jobsQuery.data, workplace],
  );

  const applyMutation = useMutation({
    mutationFn: (jobId: number) => applyForJob(saveId, jobId),
    onSuccess: (res) => {
      queryClient.setQueryData(['save', saveId], res.state);
      onNotify(res.hired ? `Hired as ${res.job} at R${res.wage}/h.` : rejectionMessage(res.reasons));
    },
    onError: (err) => {
      queryClient.invalidateQueries({ queryKey: ['save', saveId] });
      onNotify(errorMessage(err));
    },
  });

  if (jobsQuery.isPending) {
    return <p className="panel-muted">Loading job listings...</p>;
  }
  if (jobsQuery.isError) {
    return <p role="alert">{errorMessage(jobsQuery.error)}</p>;
  }

  if (workplace === null) {
    return (
      <div className="employment-panel">
        <h3>Employment Office</h3>
        <p>Which workplace are you interested in?</p>
        <ul className="workplace-list">
          {workplaces.map((location) => (
            <li key={location}>
              <button type="button" onClick={() => setWorkplace(location)}>
                {location}
              </button>
            </li>
          ))}
        </ul>
      </div>
    );
  }

  return (
    <div className="employment-panel">
      <h3>{workplace}</h3>
      <button type="button" className="link-button" onClick={() => setWorkplace(null)}>
        Back to workplaces
      </button>
      <ul className="job-list">
        {jobsAtWorkplace.map((listing) => (
          <li key={listing.id} className="job-listing">
            <span>{listing.name}</span>
            <span>R{listing.wage}/h</span>
            <button type="button" disabled={applyMutation.isPending} onClick={() => applyMutation.mutate(listing.id)}>
              Apply
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
```

- [ ] **Step 10: `frontend/src/game/panels/EmploymentOfficePanel.test.tsx` — full
  replacement.** This is a strong, specific starting point — run it, and if an assertion
  doesn't match your actual rendered output (e.g. exact button accessible name), fix the
  assertion to match reality rather than changing the component to match a wrong guess in
  this plan.

```tsx
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { EmploymentOfficePanel } from './EmploymentOfficePanel';
import { applyForJob, getJobs } from '../../api/jobs';
import type { ApplyResponse, JobListingDto, SaveStateDto } from '../../api/types';

vi.mock('../../api/jobs', () => ({ getJobs: vi.fn(), applyForJob: vi.fn() }));

function playerFixture(overrides: Partial<SaveStateDto> = {}): SaveStateDto {
  return {
    id: 1, label: 'Save 1', round: 1, timeMinutes: 3600, timeDisplay: '60h', weekOver: false,
    cash: 100, bank: 0, debt: 0, rentDue: false, foodWeeks: 0, clothing: 1,
    job: { name: 'Unemployed', hourlyWage: null, location: null },
    location: { id: 'EMPLOYMENT_OFFICE', name: 'Employment Office', ringIndex: 7, row: 3, col: 1 },
    degreesEarned: [], currentCourse: null,
    goals: {
      wealth: { current: 1, target: 100, met: false },
      happiness: { current: 0, target: 29, met: false },
      education: { current: 1, target: 15, met: false },
      career: { current: 0, target: 39, met: false },
    },
    won: false,
    ...overrides,
  };
}

const JOB_LISTINGS: JobListingDto[] = [
  { id: 4, name: 'Cook', location: 'Monolith Burgers', wage: 5 },
  { id: 5, name: 'Clerk', location: 'Monolith Burgers', wage: 6 },
  { id: 1, name: 'Clerk', location: 'Z-Mart', wage: 5 },
];

function renderPanel(saveId = 1) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
  const onNotify = vi.fn();
  render(
    <QueryClientProvider client={queryClient}>
      <EmploymentOfficePanel saveId={saveId} player={playerFixture()} onNotify={onNotify} />
    </QueryClientProvider>,
  );
  return { queryClient, invalidateSpy, onNotify };
}

describe('EmploymentOfficePanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(getJobs).mockResolvedValue(JOB_LISTINGS);
  });

  it('lists workplaces with no requirement/experience/dependability text anywhere', async () => {
    renderPanel();
    expect(await screen.findByRole('button', { name: 'Monolith Burgers' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Z-Mart' })).toBeInTheDocument();
    expect(screen.queryByText(/experience/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/dependability/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/require/i)).not.toBeInTheDocument();
  });

  it('drills into a workplace: shows only name + wage, every Apply button enabled', async () => {
    renderPanel();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Monolith Burgers' }));

    expect(screen.getByText('Cook')).toBeInTheDocument();
    expect(screen.getByText('R5/h')).toBeInTheDocument();
    const applyButtons = screen.getAllByRole('button', { name: 'Apply' });
    expect(applyButtons).toHaveLength(2);
    applyButtons.forEach((button) => expect(button).toBeEnabled());
  });

  it('reports a hire with the wage and writes the fresh save state into the cache', async () => {
    const response: ApplyResponse = {
      hired: true, reasons: [], minutesCharged: 240, job: 'Cook', wage: 5,
      state: playerFixture({ job: { name: 'Cook', hourlyWage: 5, location: 'Monolith Burgers' } }),
    };
    vi.mocked(applyForJob).mockResolvedValue(response);
    const { onNotify, queryClient } = renderPanel();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Monolith Burgers' }));
    await user.click(screen.getAllByRole('button', { name: 'Apply' })[0]);

    await waitFor(() => expect(onNotify).toHaveBeenCalledWith('Hired as Cook at R5/h.'));
    expect(applyForJob).toHaveBeenCalledWith(1, 4);
    expect(queryClient.getQueryData(['save', 1])).toEqual(response.state);
  });

  it('renders officer-style copy for a multi-reason rejection', async () => {
    const response: ApplyResponse = {
      hired: false, reasons: ['NOT_ENOUGH_EXPERIENCE', 'POOR_WORK_HISTORY'],
      minutesCharged: 240, job: 'Cook', wage: null, state: playerFixture(),
    };
    vi.mocked(applyForJob).mockResolvedValue(response);
    const { onNotify } = renderPanel();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Monolith Burgers' }));
    await user.click(screen.getAllByRole('button', { name: 'Apply' })[0]);

    await waitFor(() =>
      expect(onNotify).toHaveBeenCalledWith(
        "You don't have enough work experience for this position. Your work history doesn't meet our standards for this position.",
      ),
    );
  });

  it('renders No Openings copy for that specific rejection reason', async () => {
    const response: ApplyResponse = {
      hired: false, reasons: ['NO_OPENINGS'], minutesCharged: 240, job: 'Cook', wage: null,
      state: playerFixture(),
    };
    vi.mocked(applyForJob).mockResolvedValue(response);
    const { onNotify } = renderPanel();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Monolith Burgers' }));
    await user.click(screen.getAllByRole('button', { name: 'Apply' })[0]);

    await waitFor(() =>
      expect(onNotify).toHaveBeenCalledWith('Sorry, there are no openings right now, try again another day.'),
    );
  });

  it('invalidates save state and reports the error on a network/server failure', async () => {
    vi.mocked(applyForJob).mockRejectedValue(new Error('week over'));
    const { invalidateSpy, onNotify } = renderPanel();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Monolith Burgers' }));
    await user.click(screen.getAllByRole('button', { name: 'Apply' })[0]);

    await waitFor(() => expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['save', 1] }));
    expect(onNotify).toHaveBeenCalledWith('Could not reach the server.');
  });
});
```

  Run: `npx vitest run src/game/panels/EmploymentOfficePanel.test.tsx` — expected PASS,
  6/6.

- [ ] **Step 11: `frontend/src/game/panels/UniversityPanel.tsx` — full replacement.**
  Remember the accepted spec deviation from the backend cutover's Gate A (logged on the
  now-merged PR 5): study takes no degree id, it operates on whichever course is
  currently enrolled (`player.currentCourse`), matching `api/university.ts`'s
  `study(saveId)` signature from Task 1. Only `enroll` takes a degree id.

```tsx
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { enroll, getCourses, study } from '../../api/university';
import { errorMessage } from '../../api/http';
import type { PanelProps } from './types';

const STATUS_LABEL: Record<string, string> = {
  EARNED: 'Earned',
  AVAILABLE: 'Available',
  LOCKED: 'Locked',
};

export function UniversityPanel({ saveId, player, onNotify }: PanelProps) {
  const queryClient = useQueryClient();
  const coursesQuery = useQuery({ queryKey: ['courses', saveId], queryFn: () => getCourses(saveId) });

  const enrollMutation = useMutation({
    mutationFn: (degreeId: number) => enroll(saveId, degreeId),
    onSuccess: (res) => {
      queryClient.setQueryData(['save', saveId], res.state);
      queryClient.invalidateQueries({ queryKey: ['courses', saveId] });
      onNotify(`Enrolled, R${res.feePaid} fee paid.`);
    },
    onError: (err) => {
      queryClient.invalidateQueries({ queryKey: ['save', saveId] });
      onNotify(errorMessage(err));
    },
  });

  const studyMutation = useMutation({
    mutationFn: () => study(saveId),
    onSuccess: (res) => {
      queryClient.setQueryData(['save', saveId], res.state);
      queryClient.invalidateQueries({ queryKey: ['courses', saveId] });
      onNotify(
        res.degreeCompleted
          ? `Studied, graduated with a degree in ${res.degreeCompleted}!`
          : `Studied, ${res.studiesRemaining} sessions to go.`,
      );
    },
    onError: (err) => {
      queryClient.invalidateQueries({ queryKey: ['save', saveId] });
      onNotify(errorMessage(err));
    },
  });

  if (coursesQuery.isPending) {
    return <p className="panel-muted">Loading course board...</p>;
  }
  if (coursesQuery.isError) {
    return <p role="alert">{errorMessage(coursesQuery.error)}</p>;
  }

  return (
    <div className="university-panel">
      <h3>Hi-Tech U</h3>
      {player.currentCourse && (
        <p className="current-course">
          Studying {player.currentCourse.name} ({player.currentCourse.studiesDone}/10){' '}
          <button type="button" disabled={studyMutation.isPending} onClick={() => studyMutation.mutate()}>
            Study
          </button>
        </p>
      )}
      <ul className="degree-list">
        {coursesQuery.data.map((course) => (
          <li key={course.id} className="degree-row">
            <span className="degree-name">{course.name}</span>
            <span className="degree-status">{STATUS_LABEL[course.status]}</span>
            {course.status === 'LOCKED' && course.prereqName && (
              <span className="degree-prereq">Requires: {course.prereqName}</span>
            )}
            {course.status === 'AVAILABLE' && !course.enrolled && (
              <button type="button" disabled={enrollMutation.isPending} onClick={() => enrollMutation.mutate(course.id)}>
                Enroll
              </button>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
```

- [ ] **Step 12: `frontend/src/game/panels/UniversityPanel.test.tsx` — full
  replacement.**

```tsx
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { UniversityPanel } from './UniversityPanel';
import { enroll, getCourses, study } from '../../api/university';
import type { CourseDto, EnrollResponse, SaveStateDto, StudyResponse } from '../../api/types';

vi.mock('../../api/university', () => ({ getCourses: vi.fn(), enroll: vi.fn(), study: vi.fn() }));

function playerFixture(overrides: Partial<SaveStateDto> = {}): SaveStateDto {
  return {
    id: 1, label: 'Save 1', round: 1, timeMinutes: 3600, timeDisplay: '60h', weekOver: false,
    cash: 100, bank: 0, debt: 0, rentDue: false, foodWeeks: 0, clothing: 1,
    job: { name: 'Unemployed', hourlyWage: null, location: null },
    location: { id: 'HI_TECH_U', name: 'Hi-Tech U', ringIndex: 6, row: 3, col: 3 },
    degreesEarned: [], currentCourse: null,
    goals: {
      wealth: { current: 1, target: 100, met: false },
      happiness: { current: 0, target: 29, met: false },
      education: { current: 1, target: 15, met: false },
      career: { current: 0, target: 39, met: false },
    },
    won: false,
    ...overrides,
  };
}

const COURSES: CourseDto[] = [
  { id: 1, name: 'Junior College', status: 'AVAILABLE', prereqName: null, enrolled: false, studiesDone: 0 },
  { id: 3, name: 'Business Administration', status: 'LOCKED', prereqName: 'Junior College', enrolled: false, studiesDone: 0 },
];

function renderPanel(player: SaveStateDto = playerFixture()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const onNotify = vi.fn();
  render(
    <QueryClientProvider client={queryClient}>
      <UniversityPanel saveId={1} player={player} onNotify={onNotify} />
    </QueryClientProvider>,
  );
  return { queryClient, onNotify };
}

describe('UniversityPanel', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(getCourses).mockResolvedValue(COURSES);
  });

  it('renders each degree with its earned/available/locked status and prereq', async () => {
    renderPanel();
    expect(await screen.findByText('Junior College')).toBeInTheDocument();
    expect(screen.getByText('Available')).toBeInTheDocument();
    expect(screen.getByText('Business Administration')).toBeInTheDocument();
    expect(screen.getByText('Locked')).toBeInTheDocument();
    expect(screen.getByText('Requires: Junior College')).toBeInTheDocument();
  });

  it('enrolls by degree id and applies the returned state', async () => {
    const response: EnrollResponse = { feePaid: 50, state: playerFixture({ currentCourse: { id: 1, name: 'Junior College', studiesDone: 0 } }) };
    vi.mocked(enroll).mockResolvedValue(response);
    const { onNotify, queryClient } = renderPanel();
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Enroll' }));

    await waitFor(() => expect(onNotify).toHaveBeenCalledWith('Enrolled, R50 fee paid.'));
    expect(enroll).toHaveBeenCalledWith(1, 1);
    expect(queryClient.getQueryData(['save', 1])).toEqual(response.state);
  });

  it('study takes no degree id and reports graduation', async () => {
    const response: StudyResponse = {
      studiesDone: 10, studiesRemaining: 0, degreeCompleted: 'Junior College', minutesCharged: 360,
      state: playerFixture({ degreesEarned: ['Junior College'] }),
    };
    vi.mocked(study).mockResolvedValue(response);
    const { onNotify } = renderPanel(playerFixture({ currentCourse: { id: 1, name: 'Junior College', studiesDone: 9 } }));
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Study' }));

    expect(study).toHaveBeenCalledWith(1);
    await waitFor(() =>
      expect(onNotify).toHaveBeenCalledWith('Studied, graduated with a degree in Junior College!'),
    );
  });
});
```

  Run: `npx vitest run src/game/panels/UniversityPanel.test.tsx` — expected PASS, 3/3.

- [ ] **Step 13: Full task verification.** `cd frontend && npm run typecheck` — expected
  clean EXCEPT for `App.tsx`, `pages/GamePage.tsx`, and `pages/HomePage.tsx` (Task 3/5's
  job — they still pass `username` where `BoardScreen` now wants `saveId`; confirm the
  remaining errors are only in those three files). `npx vitest run src/game/` — expected
  PASS, every file in `src/game/` green.

- [ ] **Step 14: Commit.**

```bash
cd frontend
git add src/game/panels/types.ts src/game/BoardScreen.tsx src/game/BoardScreen.test.tsx \
  src/game/Hud.tsx src/game/Hud.test.tsx src/game/WorkAction.tsx src/game/WorkAction.test.tsx \
  src/game/panels/BankPanel.tsx src/game/panels/BankPanel.test.tsx \
  src/game/panels/RentOfficePanel.tsx src/game/panels/RentOfficePanel.test.tsx \
  src/game/panels/MonolithBurgersPanel.tsx src/game/panels/MonolithBurgersPanel.test.tsx \
  src/game/panels/BlacksMarketPanel.tsx src/game/panels/BlacksMarketPanel.test.tsx \
  src/game/panels/QTClothingPanel.tsx src/game/panels/QTClothingPanel.test.tsx \
  src/game/panels/HomePanel.tsx src/game/panels/HomePanel.test.tsx \
  src/game/panels/EmploymentOfficePanel.tsx src/game/panels/EmploymentOfficePanel.test.tsx \
  src/game/panels/UniversityPanel.tsx src/game/panels/UniversityPanel.test.tsx
git status --short
```
  Commit message: `feat: game screen cutover to save-scoped state (KAN-45)` — body notes
  the Employment two-step flow and University course tree are new UX, not ports of the
  old panels, and that `App.tsx`/`GamePage.tsx`/`HomePage.tsx` remain broken until Task
  3/5 (expected). `git commit -F <file>`.

---

### Task 3: Saves screen, New Game setup, and routing

**Files:**
- Create: `frontend/src/pages/SavesPage.tsx` + test
- Create: `frontend/src/game/NewGameSetup.tsx` + test
- Create: `frontend/src/pages/saves.css`
- Create: `frontend/src/pages/PlayPage.tsx` (replaces `GamePage.tsx` — `git mv` first,
  then rewrite)
- Delete: `frontend/src/pages/GamePage.tsx` (via the `git mv` above)
- Modify: `frontend/src/App.tsx` (routes + nav link)
- Modify: `frontend/src/pages/LoginPage.tsx` (one-line redirect target change)

**Interfaces produced:** `SavesPage`, `NewGameSetup`, `PlayPage` as default-less named
exports, matching every other page/component in this codebase.

- [ ] **Step 1 (RED): `frontend/src/game/NewGameSetup.test.tsx`.** Write this first (the
  component doesn't exist yet, so this fails on import — that's your RED evidence, no
  mutation trick needed here unlike a migration IT).

```tsx
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NewGameSetup } from './NewGameSetup';
import { createSave } from '../api/saves';
import type { SaveSummaryDto } from '../api/types';

vi.mock('../api/saves', () => ({ createSave: vi.fn() }));

function renderNewGameSetup() {
  const onCreated = vi.fn();
  const onCancel = vi.fn();
  render(<NewGameSetup onCreated={onCreated} onCancel={onCancel} />);
  return { onCreated, onCancel };
}

describe('NewGameSetup', () => {
  beforeEach(() => vi.resetAllMocks());

  it('renders four goal sliders defaulting to 50', () => {
    renderNewGameSetup();
    const sliders = screen.getAllByRole('slider');
    expect(sliders).toHaveLength(4);
    sliders.forEach((slider) => expect(slider).toHaveValue('50'));
  });

  it('Randomise sets each slider to a value between 10 and 100', async () => {
    renderNewGameSetup();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Randomise' }));

    for (const slider of screen.getAllByRole('slider')) {
      const value = Number((slider as HTMLInputElement).value);
      expect(value).toBeGreaterThanOrEqual(10);
      expect(value).toBeLessThanOrEqual(100);
    }
  });

  it('Start Game sends the current slider values and calls onCreated with the new save id', async () => {
    const created: SaveSummaryDto = { id: 5, label: 'Save 1', round: 1, cash: 100, won: false, updatedAt: '2026-01-01T00:00:00Z' };
    vi.mocked(createSave).mockResolvedValue(created);
    const { onCreated } = renderNewGameSetup();
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: 'Start Game' }));

    await waitFor(() => expect(onCreated).toHaveBeenCalledWith(5));
    expect(createSave).toHaveBeenCalledWith({
      label: 'Save 1',
      goals: { wealth: 50, happiness: 50, education: 50, career: 50 },
    });
  });

  it('Cancel calls onCancel without creating a save', async () => {
    const { onCancel } = renderNewGameSetup();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(onCancel).toHaveBeenCalled();
    expect(createSave).not.toHaveBeenCalled();
  });
});
```

  Run: `cd frontend && npx vitest run src/game/NewGameSetup.test.tsx` — expected FAIL
  (module not found).

- [ ] **Step 2 (GREEN): create `frontend/src/game/NewGameSetup.tsx`.**

```tsx
import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { createSave } from '../api/saves';
import { errorMessage } from '../api/http';
import type { GoalTargets } from '../api/types';

const MIN_GOAL = 10;
const MAX_GOAL = 100;

interface NewGameSetupProps {
  onCreated: (saveId: number) => void;
  onCancel: () => void;
}

function randomGoal(): number {
  return MIN_GOAL + Math.floor(Math.random() * (MAX_GOAL - MIN_GOAL + 1));
}

const SLIDERS: { key: keyof GoalTargets; label: string }[] = [
  { key: 'wealth', label: 'Wealth' },
  { key: 'happiness', label: 'Happiness' },
  { key: 'education', label: 'Education' },
  { key: 'career', label: 'Career' },
];

export function NewGameSetup({ onCreated, onCancel }: NewGameSetupProps) {
  const [label, setLabel] = useState('');
  const [goals, setGoals] = useState<GoalTargets>({ wealth: 50, happiness: 50, education: 50, career: 50 });

  const createMutation = useMutation({
    mutationFn: () => createSave({ label: label.trim() || 'Save 1', goals }),
    onSuccess: (save) => onCreated(save.id),
  });

  function updateGoal(key: keyof GoalTargets, value: number) {
    setGoals((prev) => ({ ...prev, [key]: value }));
  }

  function randomise() {
    setGoals({ wealth: randomGoal(), happiness: randomGoal(), education: randomGoal(), career: randomGoal() });
  }

  return (
    <section className="new-game-setup">
      <h1>New Game</h1>
      <label className="field">
        Save name
        <input
          type="text"
          value={label}
          onChange={(e) => setLabel(e.target.value)}
          placeholder="Save 1"
          maxLength={100}
        />
      </label>
      {SLIDERS.map(({ key, label: sliderLabel }) => (
        <label key={key} className="field goal-slider">
          {sliderLabel}: {goals[key]}
          <input
            type="range"
            min={MIN_GOAL}
            max={MAX_GOAL}
            value={goals[key]}
            onChange={(e) => updateGoal(key, Number(e.target.value))}
          />
        </label>
      ))}
      <div className="new-game-actions">
        <button type="button" onClick={randomise}>
          Randomise
        </button>
        <button type="button" disabled={createMutation.isPending} onClick={() => createMutation.mutate()}>
          Start Game
        </button>
        <button type="button" onClick={onCancel}>
          Cancel
        </button>
      </div>
      {createMutation.isError && <p role="alert">{errorMessage(createMutation.error)}</p>}
    </section>
  );
}
```

  Run: `npx vitest run src/game/NewGameSetup.test.tsx` — expected PASS, 4/4.

- [ ] **Step 3 (RED): `frontend/src/pages/SavesPage.test.tsx`.**

```tsx
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { SavesPage } from './SavesPage';
import { deleteSave, listSaves } from '../api/saves';
import type { SaveSummaryDto } from '../api/types';

vi.mock('../api/saves', () => ({ listSaves: vi.fn(), createSave: vi.fn(), deleteSave: vi.fn() }));

const SAVES: SaveSummaryDto[] = [
  { id: 1, label: 'Save 1', round: 3, cash: 250, won: false, updatedAt: '2026-01-01T00:00:00Z' },
  { id: 2, label: 'Winner', round: 8, cash: 5000, won: true, updatedAt: '2026-01-02T00:00:00Z' },
];

function renderSavesPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <SavesPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { queryClient };
}

describe('SavesPage', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(listSaves).mockResolvedValue(SAVES);
  });

  it('lists every save with its round, cash, and won status', async () => {
    renderSavesPage();
    expect(await screen.findByText('Save 1')).toBeInTheDocument();
    expect(screen.getByText('Winner')).toBeInTheDocument();
    expect(screen.getByText(/Won!/)).toBeInTheDocument();
  });

  it('shows an empty-state message when there are no saves', async () => {
    vi.mocked(listSaves).mockResolvedValue([]);
    renderSavesPage();
    expect(await screen.findByText('No saves yet, start a new game.')).toBeInTheDocument();
  });

  it('deletes a save after confirmation and refetches the list', async () => {
    vi.mocked(deleteSave).mockResolvedValue(undefined);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    renderSavesPage();
    const user = userEvent.setup();

    const deleteButtons = await screen.findAllByRole('button', { name: 'Delete' });
    await user.click(deleteButtons[0]);

    await waitFor(() => expect(deleteSave).toHaveBeenCalledWith(1));
  });

  it('does not delete when the confirmation is declined', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    renderSavesPage();
    const user = userEvent.setup();

    const deleteButtons = await screen.findAllByRole('button', { name: 'Delete' });
    await user.click(deleteButtons[0]);

    expect(deleteSave).not.toHaveBeenCalled();
  });

  it('New Game shows the goal-setup flow instead of the list', async () => {
    renderSavesPage();
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'New Game' }));

    expect(screen.getByRole('heading', { name: 'New Game' })).toBeInTheDocument();
    expect(screen.queryByText('Save 1')).not.toBeInTheDocument();
  });
});
```

  Run: `npx vitest run src/pages/SavesPage.test.tsx` — expected FAIL (module not found).

- [ ] **Step 4 (GREEN): create `frontend/src/pages/SavesPage.tsx`.**

```tsx
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { deleteSave, listSaves } from '../api/saves';
import { errorMessage } from '../api/http';
import { NewGameSetup } from '../game/NewGameSetup';
import './saves.css';

export function SavesPage() {
  const [creating, setCreating] = useState(false);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const savesQuery = useQuery({ queryKey: ['saves'], queryFn: listSaves });
  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteSave(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['saves'] }),
  });

  if (creating) {
    return (
      <NewGameSetup
        onCreated={(saveId) => navigate(`/play/${saveId}`)}
        onCancel={() => setCreating(false)}
      />
    );
  }

  return (
    <section className="saves-page">
      <h1>Your Saves</h1>
      {savesQuery.isPending && <p>Loading saves...</p>}
      {savesQuery.isError && <p role="alert">{errorMessage(savesQuery.error)}</p>}
      {savesQuery.data && savesQuery.data.length === 0 && <p>No saves yet, start a new game.</p>}
      {savesQuery.data && savesQuery.data.length > 0 && (
        <ul className="saves-list">
          {savesQuery.data.map((save) => (
            <li key={save.id} className="save-row">
              <span className="save-label">{save.label}</span>
              <span className="save-meta">
                Round {save.round} - R{save.cash}
                {save.won ? ' - Won!' : ''}
              </span>
              <button type="button" onClick={() => navigate(`/play/${save.id}`)}>
                Continue
              </button>
              <button
                type="button"
                className="danger"
                disabled={deleteMutation.isPending}
                onClick={() => {
                  if (window.confirm(`Delete "${save.label}"? This cannot be undone.`)) {
                    deleteMutation.mutate(save.id);
                  }
                }}
              >
                Delete
              </button>
            </li>
          ))}
        </ul>
      )}
      {deleteMutation.isError && <p role="alert">{errorMessage(deleteMutation.error)}</p>}
      <button type="button" onClick={() => setCreating(true)}>
        New Game
      </button>
    </section>
  );
}
```

  Run: `npx vitest run src/pages/SavesPage.test.tsx` — expected PASS, 5/5.

- [ ] **Step 5: create `frontend/src/pages/saves.css`** (imported by `SavesPage.tsx`
  above; covers both `SavesPage` and `NewGameSetup` since they're always shown as
  alternates of the same screen). Baseline, functional styling — adjust to taste, this
  isn't asserted by any test:

```css
.saves-page,
.new-game-setup {
  max-width: 560px;
  margin: 0 auto;
  padding: 1.5rem;
}

.saves-list {
  list-style: none;
  padding: 0;
  margin: 1rem 0;
}

.save-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  border: 1px solid #ccc;
  border-radius: 6px;
  margin-bottom: 0.5rem;
}

.save-label {
  font-weight: 600;
  flex: 1;
}

.save-meta {
  color: #555;
  font-size: 0.9rem;
}

.goal-slider {
  display: block;
  margin: 1rem 0;
}

.goal-slider input[type='range'] {
  width: 100%;
}

.new-game-actions {
  display: flex;
  gap: 0.75rem;
  margin-top: 1.5rem;
}
```

- [ ] **Step 6: rename `GamePage.tsx` to `PlayPage.tsx` and rewrite.**

```bash
cd frontend
git mv src/pages/GamePage.tsx src/pages/PlayPage.tsx
```

  Replace the file's content in full (the old file read `username` from `useAuth()`; the
  new one reads `saveId` from the route param instead, so it no longer needs `useAuth()`
  at all):

```tsx
import { useParams } from 'react-router';
import { BoardScreen } from '../game/BoardScreen';

export function PlayPage() {
  const { saveId } = useParams<{ saveId: string }>();
  const id = Number(saveId);
  if (!saveId || Number.isNaN(id)) {
    return <p role="alert">Unknown save.</p>;
  }
  return <BoardScreen saveId={id} />;
}
```

- [ ] **Step 7: `frontend/src/App.tsx` — targeted edits.** Read the file first. Make
  these exact changes:
  - Import: replace `import { GamePage } from './pages/GamePage';` with `import {
    SavesPage } from './pages/SavesPage'; import { PlayPage } from
    './pages/PlayPage';`.
  - Route table: replace the single `<Route path="/game" element={<RequireAuth><GamePage
    /></RequireAuth>} />` with two routes:
```tsx
<Route
  path="/saves"
  element={
    <RequireAuth>
      <SavesPage />
    </RequireAuth>
  }
/>
<Route
  path="/play/:saveId"
  element={
    <RequireAuth>
      <PlayPage />
    </RequireAuth>
  }
/>
```
  - Nav header: replace `<Link to="/game">Game</Link>` with `<Link to="/saves">Saves</Link>`
    (same conditional-rendering wrapper the original had, whatever that was — read it and
    keep the surrounding logic, only the target path and label text change).

- [ ] **Step 8: `frontend/src/pages/LoginPage.tsx` — one-line edit.** Find
  `location.state.from.pathname ?? '/game'` (the post-login redirect default) and change
  the fallback to `'/saves'`. Do not change anything else in this file — the rest of its
  redirect/validation/error-handling logic is unaffected by this cutover.

- [ ] **Step 9: Full task verification.** `cd frontend && npm run typecheck` — expected
  clean across the WHOLE project now (this is the task that resolves the last of the
  errors Task 1/2 left dangling in `App.tsx`/`GamePage.tsx`/`HomePage.tsx` — `HomePage.tsx`
  itself is still Task 5's job, but it should already typecheck fine since its only issue
  is content, not types; if it still errors, check whether it imports something Task 1
  deleted (`HighscoreEntry`) — if so, that's fine to leave broken until Task 5, note it in
  this task's commit body). `npx vitest run src/game/NewGameSetup.test.tsx
  src/pages/SavesPage.test.tsx` — expected PASS.

- [ ] **Step 10: Commit.**

```bash
cd frontend
git add src/game/NewGameSetup.tsx src/game/NewGameSetup.test.tsx src/pages/SavesPage.tsx \
  src/pages/SavesPage.test.tsx src/pages/saves.css src/pages/PlayPage.tsx src/App.tsx \
  src/pages/LoginPage.tsx
git status --short
```
  Commit message: `feat: saves screen, new-game setup, and /play routing (KAN-45)`.
  `git commit -F <file>`.

---

### Task 4: Win banner

**Files:**
- Create: `frontend/src/game/WinBanner.tsx` + test
- Modify: `frontend/src/game/BoardScreen.tsx` (add the dismiss-state + conditional render)
- Modify: `frontend/src/game/BoardScreen.test.tsx` (one new test case)
- Modify: `frontend/src/game/board.css` (append two rules)

**Design decision (not spelled out in the spec/JIRA, made explicit here so it's not
re-litigated at implementation time):** "first `won` transition, dismissible, play
continues" is satisfied by a plain dismissed-flag in `BoardScreen`'s local component
state — the banner shows whenever `player.won` is true and the flag isn't set yet;
dismissing sets the flag for the rest of that `/play/:saveId` visit (unmounting and
re-navigating back in — e.g. via the saves list — will show it again once per visit,
which is correct: a player returning to an already-won save they haven't acknowledged
yet in this session should still see it). No need to track the exact false-to-true edge
via `useEffect`/previous-value comparison; this simpler local-flag approach satisfies the
acceptance criterion without extra machinery.

- [ ] **Step 1 (RED): `frontend/src/game/WinBanner.test.tsx`.**

```tsx
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { WinBanner } from './WinBanner';

describe('WinBanner', () => {
  it('renders a win message and calls onDismiss when closed', async () => {
    const onDismiss = vi.fn();
    render(<WinBanner onDismiss={onDismiss} />);
    const user = userEvent.setup();

    expect(screen.getByRole('dialog', { name: 'You won' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Keep Playing' }));

    expect(onDismiss).toHaveBeenCalled();
  });
});
```

  Run: `cd frontend && npx vitest run src/game/WinBanner.test.tsx` — expected FAIL
  (module not found).

- [ ] **Step 2 (GREEN): create `frontend/src/game/WinBanner.tsx`.**

```tsx
interface WinBannerProps {
  onDismiss: () => void;
}

export function WinBanner({ onDismiss }: WinBannerProps) {
  return (
    <div className="win-banner-overlay" role="dialog" aria-label="You won">
      <div className="win-banner">
        <h2>You Won!</h2>
        <p>Every goal met. Keep playing to see how far you can go.</p>
        <button type="button" onClick={onDismiss}>
          Keep Playing
        </button>
      </div>
    </div>
  );
}
```

  Run: `npx vitest run src/game/WinBanner.test.tsx` — expected PASS, 1/1.

- [ ] **Step 3: `frontend/src/game/BoardScreen.tsx` — targeted edit.** Read the file
  (already touched once in Task 2). Add:
  - `import { WinBanner } from './WinBanner';`
  - A new piece of local state alongside whatever `useState` calls already exist for the
    notification feed: `const [winBannerDismissed, setWinBannerDismissed] =
    useState(false);`
  - In the component's returned JSX, once `player` (the resolved `['save', saveId]` query
    data) is available and being rendered, add as a sibling of the main screen content:
    `{player.won && !winBannerDismissed && (<WinBanner onDismiss={() =>
    setWinBannerDismissed(true)} />)}`. Exact placement within the JSX tree doesn't
    matter functionally (the banner is a fixed-position overlay via CSS below), so put it
    wherever reads most naturally alongside the existing `EndWeekModal` conditional
    render.

- [ ] **Step 4: `frontend/src/game/board.css` — append.**

```css
.win-banner-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}

.win-banner {
  background: #fff;
  padding: 2rem;
  border-radius: 8px;
  text-align: center;
  max-width: 360px;
}
```

- [ ] **Step 5: `frontend/src/game/BoardScreen.test.tsx` — add one test case.** Using
  whatever `playerFixture`/render helper the file already has (updated in Task 2), add:

```tsx
it('shows the win banner once the save has won, and dismisses it without leaving the screen', async () => {
  vi.mocked(getSaveState).mockResolvedValue(playerFixture({ won: true }));
  renderBoardScreen();
  const user = userEvent.setup();

  expect(await screen.findByRole('dialog', { name: 'You won' })).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: 'Keep Playing' }));

  expect(screen.queryByRole('dialog', { name: 'You won' })).not.toBeInTheDocument();
});
```

  Adjust the mock/helper names to match whatever this file actually calls them after
  Task 2's edits (`getSaveState`, `renderBoardScreen` or equivalent — read the file's
  current names rather than assuming these exact identifiers). Run `npx vitest run
  src/game/BoardScreen.test.tsx` — expected PASS, every case including the new one.

- [ ] **Step 6: Commit.**

```bash
cd frontend
git add src/game/WinBanner.tsx src/game/WinBanner.test.tsx src/game/BoardScreen.tsx \
  src/game/BoardScreen.test.tsx src/game/board.css
git status --short
```
  Commit message: `feat: win banner on the first won transition (KAN-45)`. `git commit -F
  <file>`.

---

### Task 5: Highscores removal + HomePage

**Files:**
- Modify: `frontend/src/pages/HomePage.tsx` (full replacement — the leaderboard body is
  gone; `api/highscores.ts` and `HighscoreEntry` were already deleted in Task 1)
- Create: `frontend/src/pages/HomePage.test.tsx` (didn't exist before — this is
  effectively new content since the old component's entire purpose is deleted)
- Modify: `frontend/README.md` (one stale doc line)

**Interfaces:** none — this task has no consumers, it's the end of the deletion chain
Task 1 started (`api/highscores.ts` + `HighscoreEntry` type already gone).

- [ ] **Step 1: replace `frontend/src/pages/HomePage.tsx` in full.**

```tsx
import { Link } from 'react-router';
import { useAuth } from '../auth/AuthContext';

export function HomePage() {
  const { username } = useAuth();

  return (
    <section className="home-page">
      <h1>Amiss</h1>
      <p>A browser port of the 1990 economic sim Jones in the Fast Lane.</p>
      <Link to={username === null ? '/login' : '/saves'} className="cta-button">
        {username === null ? 'Log In to Play' : 'Play'}
      </Link>
    </section>
  );
}
```

- [ ] **Step 2: write `frontend/src/pages/HomePage.test.tsx`.**

```tsx
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { HomePage } from './HomePage';

const mockUseAuth = vi.fn();
vi.mock('../auth/AuthContext', () => ({ useAuth: () => mockUseAuth() }));

describe('HomePage', () => {
  it('links to /login when signed out', () => {
    mockUseAuth.mockReturnValue({ username: null });
    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>,
    );
    expect(screen.getByRole('link', { name: 'Log In to Play' })).toHaveAttribute('href', '/login');
  });

  it('links to /saves when signed in', () => {
    mockUseAuth.mockReturnValue({ username: 'alice' });
    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>,
    );
    expect(screen.getByRole('link', { name: 'Play' })).toHaveAttribute('href', '/saves');
  });
});
```

  Run: `cd frontend && npx vitest run src/pages/HomePage.test.tsx` — expected PASS, 2/2.

- [ ] **Step 3: `frontend/README.md` — fix the stale doc line.** Find the line describing
  `HomePage` as rendering `GET /api/highscores` (research located it around line 43;
  confirm the exact line number in the current file, it may have shifted). Replace with a
  short, accurate sentence: Home is a simple landing page linking to `/login` or `/saves`
  depending on auth state; the saves screen and game itself are what the API backs.

- [ ] **Step 4: full-project verification.** `cd frontend && npm run lint && npm run
  format:check && npm run typecheck && npm test && npm run build` — expected: every
  step clean, this is the first point in the whole plan where the ENTIRE frontend CI
  sequence should pass end-to-end. If `lint`/`format:check` flag anything in files this
  plan touched, fix it now (don't defer formatting fixes to Task 6).

- [ ] **Step 5: Commit.**

```bash
cd frontend
git add src/pages/HomePage.tsx src/pages/HomePage.test.tsx README.md
git status --short
```
  Commit message: `feat: remove highscores, simplify HomePage (KAN-45)` — body notes this
  completes the deletion chain started in Task 1 (`api/highscores.ts`, `HighscoreEntry`
  type). `git commit -F <file>`.

---

### Task 6: Live verification and PR

**Files:** none (verification only).

- [ ] **Step 1: Full reactor + frontend build, one more time from a clean state.**
  `cd frontend && rm -rf dist && npm run lint && npm run format:check && npm run
  typecheck && npm test && npm run build` — expected: BUILD SUCCESS, every test file
  green, `dist/` produced. This should already be true from Task 5's Step 4 — this step
  exists to catch anything a later task's rebase/merge might have disturbed.

- [ ] **Step 2: Boot both servers.** Confirm MySQL97 is running
  (`Get-Service MySQL97`; escalate to Rourke if stopped, matching the KAN-54 precedent).
  Backend: `.\mvnw.cmd -B -pl amiss-api spring-boot:run` in the background; wait for
  `GET http://localhost:8080/actuator/health` → `{"status":"UP"}`. Frontend: `cd frontend
  && npm run dev` in the background; wait for the Vite dev server's listed local URL
  (default `http://localhost:5173`) to respond.

- [ ] **Step 3: Live browser walkthrough (Claude-in-Chrome or manual — whichever this
  session has available), covering the JIRA acceptance criterion end to end:**
  1. Navigate to the dev server root. Confirm the new `HomePage` renders (not a
     leaderboard) with a "Log In to Play" link.
  2. Register + log in a disposable account (e.g. `kan45smoke` — pick a name not already
     used by a prior session's leftover data).
  3. Confirm you land on `/saves` (not `/game`) showing "No saves yet, start a new game."
  4. Click "New Game," move a slider, click "Randomise" and confirm the sliders visibly
     change, then "Start Game." Confirm you land on `/play/{id}` and the board renders.
  5. Move to the Employment Office. Confirm the workplace list shows **no** wage
     requirement, experience, or dependability text anywhere — then open the browser's
     Network tab, click into a workplace, and inspect the raw `GET /api/jobs` response
     body directly: confirm it contains only `id`/`name`/`location`/`wage` keys (this is
     the JIRA acceptance criterion's explicit "network tab" check, not just a UI check).
     Apply for a job; confirm you get hired or see officer-style rejection copy (not a
     raw enum name like `NOT_ENOUGH_EXPERIENCE`).
  6. Move to Hi-Tech U. Confirm the course tree shows Junior College and Trade School as
     Available with no prereq, and higher-tier degrees as Locked with a prereq name.
     Enroll in one, then Study — confirm the study button needs no degree selection (the
     accepted no-degree-id deviation).
  7. Confirm the HUD's four goal bars read "Wealth/Happiness/Education/Career" (not the
     old "Cash/Work Experience") and that no experience/dependability figure is displayed
     anywhere on screen.
  8. **Win flow:** reaching all four goals through normal play within this verification
     step is impractical (targets can require many rollovers). Instead: as root, directly
     `UPDATE tblsave SET cash = <goal_wealth * 100>, dependability = <value satisfying
     the career formula>, ... WHERE id = <your test save's id>` to bring every stat at or
     above its goal target — read `GoalService`/the career/wealth/education stat formulas
     in `amiss-core/src/main/java/amiss/application/service/save/` first to compute
     correct values rather than guessing (this plan does not restate those formulas; they
     were normative in the parent plan's Task 4 and haven't changed). Trigger `end-week`
     from the UI (spend remaining time first if needed) and confirm the `WinBanner`
     appears with "You Won!" and that dismissing it returns to normal play with the save
     still marked won (goal bars show `met`, if you added that styling in Task 2).
  9. Clean up: `DELETE FROM tblsave WHERE owner='kan45smoke'; DELETE FROM tbluser WHERE
     name='kan45smoke';` as the `amiss_migrator` account (least-privilege `amiss` cannot
     `DELETE`, matching the KAN-54 precedent).
  10. Stop both dev servers: the frontend Vite process by its own Ctrl-C/job control; the
      backend by finding the actual port-8080 listener's PID (`Get-NetTCPConnection
      -LocalPort 8080 -State Listen` → `Stop-Process -Id ...`) — never by process name
      (see `tasks/lessons.md`, "Killing the right JVM").

  If any step in this walkthrough fails, that's an escalation — fix forward if it's a
  small, obviously-scoped bug in this plan's own new code; if it implicates something
  outside this plan's files (e.g. a real backend gap), stop and report rather than
  expanding scope.

- [ ] **Step 4: Final whole-branch review.** Generate the review package (from the
  subagent-driven-development skill directory): `scripts/review-package $(git merge-base
  develop HEAD) HEAD`, then dispatch a code-reviewer subagent (or consult on the most
  capable available model) per `superpowers:requesting-code-review`, covering: does every
  file in the "Highscores — exact deletion inventory" section (this plan's research
  phase) show as deleted/updated; does the hidden-requirements contract hold (grep the
  whole diff for `reqExperience|reqDependability|reqClothing` — should be zero hits
  outside comments/this plan file); is the charged-rejection/cache convention
  (`setQueryData` on success, `invalidateQueries` on error) followed consistently across
  every new/modified mutation. Fix any findings via ONE fix subagent with the complete
  list, re-verify, re-review.

- [ ] **Step 5: Push and open the PR.**

```bash
git push -u origin feat/frontend-saves-employment
gh pr create --base develop
```
  Body covers: the route/query-key cutover (`/players/{username}` → `/saves/{saveId}`,
  `['player', username]` → `['save', saveId]`), the Employment two-step redesign and
  University course tree (new UX, not ports), the win banner design decision (dismissed-flag,
  not edge-detection — Task 4's note), highscores removal, and confirmation this PR
  restores the SPA (broken since PR 5/KAN-54 merged). Watch checks: `gh pr checks --watch`;
  fix reds via a fix subagent, matching the KAN-54 precedent (a CI-only check catching
  something no local run does is plausible here too, e.g. a `paths:`-filtered workflow
  this plan didn't anticipate — investigate before assuming the workflow itself is wrong).

- [ ] **Step 6: JIRA + bookkeeping.** Comment the PR link on KAN-45 (Atlassian MCP tools).
  Transition KAN-45 to Done only after Rourke merges (not on PR open — match the
  established convention of moving a ticket to Done on merge, not on PR creation). Tick
  the `PR 6` line in `tasks/todo.md`'s Plan section and this Goal's session block (once
  merged, with the PR number, matching every other entry's `*(#NN, merged YYYY-MM-DD)*`
  style). Add a `tasks/cv-highlights.md` section for this PR (the Employment two-step
  hidden-requirements UI, the course-tree/win-flow work, and the fact that this closes
  out the whole "Jones-parity jobs & degrees, save slots, wiki win rules" goal spanning
  PRs 1-6). Report to Rourke: PR link, the win-banner design decision (not spelled out in
  the spec, made explicit in Task 4), and any leftovers surfaced during the live
  walkthrough. Do NOT merge.

---

## Self-review notes

- **Spec coverage:** every JIRA KAN-45 bullet maps to a task — saves list/continue/delete
  + goal-setup sliders/Randomise → Task 3; api re-path + query keys → Task 1 (client) +
  Task 2 (consumers); Employment two-step/no-badges/officer-copy → Task 2 Steps 9-10;
  University course tree → Task 2 Steps 11-12; HUD goal bars/degrees/no hidden stats →
  Task 2 Step 4; win banner → Task 4; highscores removal → Task 1 (api) + Task 5 (page).
  The acceptance criterion's "network tab" check is explicit in Task 6 Step 3.5, not left
  implicit.
- **Sequencing note for whoever executes this:** Task 2 must land before Task 3, because
  `PlayPage`/`BoardScreen`'s prop contract (`saveId`, not `username`) is a Task 2 product
  Task 3's `PlayPage` consumes — reversing the order leaves an intermediate state that
  doesn't typecheck. Tasks 1 → 2 → 3 are a strict chain; Task 4 depends only on Task 2
  (touches the same `BoardScreen.tsx`, do not run Task 4 in parallel with anything else
  touching that file); Task 5 depends only on Task 1 (the `api/highscores.ts` deletion) and
  is otherwise independent — it could run in parallel with Task 3/4 if using a
  multi-session workflow, but subagent-driven-development within one session runs tasks
  sequentially anyway, so this only matters if a future executor chooses to parallelize.
- **Type consistency check:** `saveId: number` is used identically as the first
  mutation-function argument in every api module (Task 1), every `PanelProps` consumer
  (Task 2), `PlayPage`'s route-param parsing (Task 3) — no file uses `username` after
  Task 3 lands except deliberately-untouched `AuthContext`/`tokenStore`/`LoginPage`'s
  session-identity concerns, which are orthogonal to save selection.
- **No backend changes anywhere in this plan** — confirmed every task's file list is
  under `frontend/`, matching this plan's own Global Constraints.

