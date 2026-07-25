# DevForge

See [README.md](../README.md) for project overview and setup.

## Scripts

- `npm run dev` — start Vite dev server
- `npm run build` — production build
- `npm test` — run Vitest unit tests
- `npm run test:watch` — watch mode

## Frontend structure

- `src/features/` — feature modules (UI + co-located tests)
- `src/shared/api/` — HTTP client and resource-specific API functions
- `src/shared/components/` — reusable presentational components
- `src/shared/types/` — shared TypeScript contracts aligned with backend DTOs

Keep API access in `shared/api` and out of components where possible so UI remains decoupled from transport details.
