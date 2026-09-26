# ThirdEye AI — frontend

AI-powered code review & test-generation dashboard, built for the IBM Bob 2.0 hackathon.
Frontend only — no backend or AI logic. All data in `src/data/mockData.js` is mocked so the
UI is fully explorable, and every component is written to make wiring up a real API later
straightforward (see "Connecting a real backend" below).

## Stack

- React 18 + Vite 5
- Tailwind CSS 3 (custom design tokens — see `tailwind.config.js`)
- react-router-dom 6 (hash routing, so it works from a static file:// build too)
- lucide-react for icons

## Setup

```bash
npm install
npm run dev       # http://localhost:5173
```

For future API integration, copy `.env.example` to `.env.local` and set
`VITE_API_BASE_URL` to the Spring Boot server base URL. The API service layer is in
`src/services/api/`; it provides a shared fetch helper and resource modules. Resource
modules currently accept caller-supplied paths and request options because endpoint
paths, methods, and response shapes have not been defined. The UI continues to use
`src/data/mockData.js` until those contracts are agreed and the pages are wired up.

```bash
npm run build      # production build to /dist
npm run preview    # preview the production build locally
```

## Structure

```
src/
  data/mockData.js      # all mock data — projects, findings, diff, tests, report
  components/           # shared, presentational, no page-specific logic
    Sidebar.jsx          # collapsible left nav
    Topbar.jsx            # project selector dropdown
    SeverityBadge.jsx     # critical/high/medium/low pill
    ProgressRing.jsx      # SVG circular progress (review %, risk score)
    StatCard.jsx
    Button.jsx
  pages/
    Landing.jsx            # marketing/overview screen with "Start code review" CTA
    Dashboard.jsx           # sidebar + topbar shell, renders nested routes via <Outlet>
    Overview.jsx             # review progress, stage tracker, human approval controls
    CodeDiff.jsx              # file list + line-level diff viewer
    Findings.jsx               # security/quality findings, severity + category filters
    Tests.jsx                   # generated test results, pass/fail/pending tabs
    Report.jsx                   # final report — risk score, findings + test summary
```

## Routes

- `/` — landing page
- `/dashboard/overview` — review status, progress, approval controls
- `/dashboard/diff` — code diff viewer
- `/dashboard/findings` — findings list with filters
- `/dashboard/tests` — generated test results
- `/dashboard/report` — final report

`Dashboard.jsx` holds the currently selected project in state and passes it down to every
tab via `useOutletContext()`, so swapping the project in the topbar updates the whole
dashboard.

## Design tokens

Defined in `tailwind.config.js` under `theme.extend.colors`: `ivory`, `peach`, `sand`,
`terracotta`, `plum`, `coral`, `ink`. Fonts are Fraunces (serif, headings) and Inter (sans,
UI/body), loaded from Google Fonts in `index.html`; JetBrains Mono is used only inside the
diff viewer and test file paths.

## Connecting a real backend later

Every page currently imports static data from `src/data/mockData.js`. To wire up a real API:

1. Replace the imports in each page with a data-fetching hook (e.g. `useEffect` + `fetch`,
   or React Query/SWR) that calls your review service and shapes the response to match the
   existing mock shapes (see the exported objects in `mockData.js` for the expected fields).
2. `Overview.jsx`'s approval buttons currently just set local state — swap the `setDecision`
   calls for a call to your approval endpoint, and keep the same UI states
   (`approved` / `changes` / `blocked`).
3. `CodeDiff.jsx` expects a `diffFile` object per selected file with a `lines` array of
   `{ type: 'add' | 'del' | 'context', old, new, text }` — a unified-diff parser can produce
   this shape directly.
4. `Tests.jsx` expects each test to include a `status` of `passed` / `failed` / `pending` —
   this maps naturally onto a CI test-runner's JSON output.

No component currently holds business logic that assumes mock data specifically — they all
just render whatever shape they're given, so the swap should be additive.
