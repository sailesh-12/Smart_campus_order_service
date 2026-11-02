# Smart Campus: admin dashboard (`./admin-web`)

Next.js 16 (App Router), TypeScript strict, Tailwind 4, TanStack Query.
Campus overview, canteen onboarding and suspension, complaints, payment issues and refunds, orders, audit log.

## CORS

The backend allows `http://localhost:3001` (and `127.0.0.1:3001`) in `SecurityConfig.corsConfigurationSource`. If you serve this dashboard from another origin, add it there; otherwise the browser blocks every request and the login page says the server may be blocking this site.

## Configuration

Copy `.env.example` to `.env.local`:

| Variable | Default | Meaning |
|---|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | Backend base URL (inlined at build time). |

## Run

```bash
npm install
```

```bash
npm run dev
```

Opens on http://localhost:3001. Production: `npm run build` then `npm start`.

| Command | What it does |
|---|---|
| `npm run gen:api` | Regenerates `src/api/schema.d.ts` from `{NEXT_PUBLIC_API_BASE_URL}/v3/api-docs` |
| `npm run typecheck` | `tsc --noEmit` |
| `npm run lint` | ESLint |
| `npm test` | Vitest: role guard, retry/resolve eligibility, manual refund rules, temporary password, missing endpoints, money, statuses |
| `npm run build` | Production build (also typechecks) |

## Seed login (backend dev profile)

| Role | Email | Password |
|---|---|---|
| Admin | `admin@campus.edu` | `Admin@123` |

Only ADMIN accounts are accepted. The JWT is kept in `sessionStorage` (same trade-off as the vendor dashboard: ends
with the tab, readable by page scripts); any 401 logs out.

## Screens

- **Overview**: `GET /admin/analytics` (revenue, orders, active canteens and students, open complaints, payment issues).
- **Canteens**: `GET`/`POST /admin/vendors`; suspend or reactivate (`PATCH /admin/vendors/{id}` `isSuspended`, with a
  confirm); reset the owner's password (`POST /admin/vendors/{id}/reset-password`). The dashboard generates a 12-character
  temporary password in the browser (`crypto.getRandomValues`), sends it, and shows it once with a Copy button.
- **Complaints**: `GET /admin/complaints?status=` with status tabs; a drawer shows the complaint, the order
  (`GET /orders/{id}`), a status + note form (`PATCH /admin/complaints/{id}`), and **Refund the full order**
  (`POST /admin/payments/{paymentId}/refund`, reason COMPLAINT, linked to the complaint).
- **Payment issues**: `GET /admin/payments/issues` (UNCONFIRMED and REFUND_FAILED, with age, checks, attempts, reason).
  Retry refund (REFUND_FAILED only), Resolve (note required; UNCONFIRMED only after 30 minutes), and a **Manual full refund**
  panel by order id (reason COMPLAINT or UNCOLLECTED_OVERRIDE; the latter only for UNCOLLECTED orders).
- **Orders** (`GET /admin/orders?limit=200`) and **Audit log** (`GET /admin/actions`): both optional in the brief; both
  are in this backend's OpenAPI document. If a backend lacks them, the page shows "not available" instead of an error.

### Money safety

Every money action opens a confirm dialog with the amount and "This cannot be undone", disables its buttons while the
request runs, and shows the server's error. Afterwards the wording follows the payment's new status: REFUNDED →
"Refund initiated" (never "money returned"); REFUND_FAILED → "Refund failed at the gateway… listed under Payment issues".
Only full refunds exist.

## TODO(contract)

- **Onboarding needs an owner user id**: `POST /admin/vendors` takes `ownerUserId`, but there is no admin endpoint to create
  or search VENDOR user accounts. The form asks for the id directly.
- **Issues list vs manual refund**: the issues list only contains UNCONFIRMED and REFUND_FAILED payments, which cannot take a
  manual refund (it needs SUCCESS). Manual refunds are therefore started from a complaint or by order id.
- **`unconfirmed-max-minutes`** (30 by default) is not exposed; the Resolve button uses 30 as a hint and the server decides.
- **Payment status of an order** is not in `OrderResponse`; the refund button cannot tell beforehand whether a payment was
  already refunded (the server answers with the current state, and the dashboard shows it).
- `GET /admin/actions` returns the raw entity and is not paged; `GET /admin/orders` has only `limit`.
- The error body schema is not in the OpenAPI document (typed in `src/api/errors.ts`); every OpenAPI field is optional.

## Verified / not verified

Verified: typecheck, lint, 10 unit tests, production build; in the browser, the login redirect and the CORS failure message.
Not verified: every admin screen against live data (CORS for port 3001 was added after these checks, and backend login was down because its Redis was unreachable).
A Node-based check of the admin endpoints was attempted but the backend could not log in at that moment: its hosted Redis
was unreachable (DNS timeouts on this machine), and login fails closed (429) by design when Redis is down.
