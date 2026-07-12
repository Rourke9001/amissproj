# AmissProj frontend

React + TypeScript SPA (Vite) that consumes the `amiss-api` REST API — the web
replacement for the Swing screens (ROADMAP Phase 3, KAN-19). The game rules are
**not** reimplemented here: React renders state and calls the API.

## Dev loop

Start the API first, then the dev server (it proxies `/api` to `:8080`, so
there is no CORS setup in dev):

```powershell
# from the repo root
.\mvnw -f amiss-api spring-boot:run

# in a second shell
cd frontend
npm install
npm run dev          # http://localhost:5173
```

## Scripts

| Script                 | What it does                                  |
| ---------------------- | --------------------------------------------- |
| `npm run dev`          | Vite dev server on `:5173` with the API proxy |
| `npm run build`        | typecheck + production bundle → `dist/`       |
| `npm run test`         | Vitest unit tests                             |
| `npm run lint`         | ESLint                                        |
| `npm run typecheck`    | `tsc -b`                                      |
| `npm run format`       | Prettier write                                |
| `npm run format:check` | Prettier check (CI runs this)                 |

## Layout

- `src/api/` — hand-rolled typed client (`http.ts` + per-resource modules);
  mirrors the Spring DTO records, RFC 7807 problem+json aware. Regenerate from
  OpenAPI once KAN-46 lands.
- `src/auth/` — JWT session plumbing: `tokenStore` (localStorage + expiry with
  clock skew; the API has no refresh endpoint, so expiry means re-login) and
  `AuthContext` (login/logout, 401 → logout).
- `src/routes/RequireAuth.tsx` — guard for authenticated routes.
- `src/pages/` — screens; Home is a simple landing page linking to `/login`
  or `/saves` depending on auth state; the saves screen and game itself are
  what the API backs.
