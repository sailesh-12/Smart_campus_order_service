# Smart Campus: vendor dashboard (`./vendor-web`)

Next.js 16 (App Router), TypeScript strict, Tailwind 4, TanStack Query. Touch-friendly for a counter tablet.
Live order board with chime, pickup verification (code or QR), menu and stock, shop settings, analytics.

## Configuration

Copy `.env.example` to `.env.local`:

| Variable | Default | Meaning |
|---|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | Backend base URL (inlined at build time). |

The backend's CORS already allows `http://localhost:3000`.

## Run

```bash
npm install
```

```bash
npm run dev
```

Opens on http://localhost:3000. Production: `npm run build` then `npm start`.

| Command | What it does |
|---|---|
| `npm run gen:api` | Regenerates `src/api/schema.d.ts` from `{NEXT_PUBLIC_API_BASE_URL}/v3/api-docs` (backend running) |
| `npm run typecheck` | `tsc --noEmit` |
| `npm run lint` | ESLint (eslint-config-next) |
| `npm test` | Vitest: board actions/columns, new-order detection, campus-day and sales-per-hour maths, role guard, status mapping, money, errors |
| `npm run build` | Production build (also typechecks) |

## Seed login (backend dev profile)

| Role | Email | Password |
|---|---|---|
| Vendor (Main Canteen) | `canteen@campus.edu` | `Vendor@123` |
| Vendor (Rishabs) | `rishabs@campus.edu` | `Vendor@123` |
| Vendor (Metro Cafe) | `metro@campus.edu` | `Vendor@123` |

Only VENDOR accounts are accepted; student and admin accounts are refused at login.

## Token storage trade-off

The JWT is kept in `sessionStorage`:

- It survives a reload but ends when the tab is closed, and is not shared with other tabs. On a shared counter tablet,
  closing the tab logs the shop out.
- Any script running on the page can read it, so an XSS bug would expose it. An httpOnly cookie would avoid that, but
  the backend issues bearer tokens only. The app renders no user HTML and loads no third-party scripts, which keeps the
  XSS surface small.
- Any 401 on an authenticated call clears the session and returns to login with a notice.

## How it works

- **Shell**: `GET /vendor/me`; Open/Closed and Rush mode switches (`PATCH /vendors/{id}`); live connection indicator;
  "Sound on" toggle. Browsers block audio until the user interacts, so the toggle click unlocks Web Audio and the chime is
  synthesised (no sound file).
- **Live board**: `GET /vendor/orders` (active: PAID, ACCEPTED, PREPARING, READY) plus `/vendor/orders/stream`
  (`@microsoft/fetch-event-source`, so the Authorization header is sent). Every `order-status` event refetches over REST.
  Columns New / Accepted / Preparing / Ready; actions Accept, Start preparing, Mark ready, Reject (with a confirm that the
  student is refunded). No optimistic moves: cards move when the server answers, and INVALID_TRANSITION refetches the board.
  A new paid order chimes (if sound is on) and flashes. While the stream is down the board polls every 20 s. Tabs: Today
  (collected, rejected, cancelled today) and Uncollected.
- **Pickup**: type the code or scan the QR (`html5-qrcode`, loaded only in the browser), `POST /orders/pickup/verify`,
  then the items to hand over. UNCOLLECTED orders are accepted too.
- **Menu**: `GET /vendor/menu`, create/edit/delete, availability switch and exact stock editing; low and zero stock are
  highlighted. Deleting a dish that was ever ordered returns ITEM_IN_USE: mark it unavailable instead.
- **Settings**: average prep time, parallel capacity, slot size (`PATCH /vendors/{id}`). Rename/suspend is admin-only.
- **Analytics**: `GET /vendor/analytics?from&to` (revenue, order counts, top items) with recharts, plus sales per hour.
- Statuses use the same labels and colours as the other apps (`src/lib/status.ts`), always with the text label.
  Money is INR via `Intl` (en-IN) in integer paise; times are Asia/Kolkata.

### Next.js 16 note

Cache Components is on (scaffold default). Pages authenticate in the browser, so they cannot render on the server; each
`page.tsx`/`layout.tsx` under `(dash)` is a thin server file exporting `instant = false` and rendering a client view from
`src/views/`. Without that, the dev overlay reports the dashboard segments as dropped from instant-navigation validation.

## Assumptions

- "Today" means the campus day in Asia/Kolkata.
- Rejecting asks for confirmation every time; Accept/Start/Ready do not.
- Status counts in Analytics come from the analytics totals: collected, cancelled+rejected+expired, and the rest.

## TODO(contract)

- **Sales per hour** is not in `/vendor/analytics`; it is computed in the browser from `GET /vendor/orders?status=COLLECTED`.
- **No date filter** on `/vendor/orders`: the Today tab and the hourly chart download every order of a status and filter
  in the browser. Fine for a pilot; needs `from`/`to` (and paging) on the server as history grows.
- **Stream connects late (fixed)**: the backend now sends a comment line on subscribe, so the board shows Live within about a second instead of waiting for the 25 s heartbeat.
- The vendor stream only carries `order-status` (`{ orderId, vendorId, status, at }`), which is what the board needs.
- The error body schema is not in the OpenAPI document (typed in `src/api/errors.ts`); every OpenAPI field is optional.

## Verified / not verified

Verified here: typecheck, lint, 16 unit tests, production build, and in the browser against the running backend: login,
role-guarded redirect, board with live indicator reaching Live, menu with exact and low stock, analytics. Not exercised:
state changes (accept/reject/ready, pickup verify, menu edits), because they would write to the shared dev database, and the
camera QR scanner (no camera in this environment).
