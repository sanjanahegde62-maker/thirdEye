# Frontend Patch Files

These files go into the `thirdeye-ai-frontend` project. Copy each file to the path shown.

## Files to copy

| Source (this directory)       | Destination in frontend project            |
|-------------------------------|---------------------------------------------|
| `BobIntegration.jsx`          | `src/pages/BobIntegration.jsx`             |
| `Overview.jsx`                | `src/pages/Overview.jsx`                   |
| `Report.jsx`                  | `src/pages/Report.jsx`                     |
| `Tests.jsx`                   | `src/pages/Tests.jsx`                      |
| `tests-api.js`                | `src/services/api/tests.js`                |

After copying, add the route and sidebar entry (see below), then run `npm run dev`.

## App.jsx changes required

Add the import and route for BobIntegration:

```jsx
import BobIntegration from './pages/BobIntegration'
// inside <Route path="/dashboard"> add:
<Route path="bob" element={<BobIntegration />} />
```

## Sidebar.jsx changes required

Add the nav item for the Bob Integration page:

```jsx
import { Bot } from 'lucide-react'
// in navItems array, add:
{ to: '/dashboard/bob', label: 'IBM Bob', icon: Bot },
```

After copying, run `npm run dev` in the frontend project to verify.

## What changed

### Overview.jsx
- **Was**: 100% hardcoded mock data from `mockData.js`
- **Now**: Fetches the latest review for the active project, polls every 3s while PENDING/ANALYZING,
  shows real progress ring + stage pipeline, real top findings, real stats, error + loading states.
  Human approval panel only shown when COMPLETED.

### Report.jsx
- **Was**: 100% hardcoded mock data
- **Now**: Fetches latest review + real findings + summary from `/findings/summary`.
  Mock-only fields (risk score, coverage delta, approver) are clearly labelled as demo data.

### Tests.jsx
- **Was**: 100% hardcoded `generatedTests` from mockData
- **Now**: Fetches generated tests from `/api/reviews/{id}/tests`, calls
  `POST /api/reviews/{id}/tests/generate` when user clicks "Generate tests".
  A clear banner explains tests are MOCK stubs (not executed).

### tests-api.js (new)
- Real API calls for `getTests()` and `generateTests()`.
