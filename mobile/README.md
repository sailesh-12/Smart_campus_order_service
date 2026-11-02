# Smart Campus: student app (`./mobile`)

React Native (Expo SDK 58, expo-router), TypeScript strict, TanStack Query, Zustand.
Students browse canteens, order, pay (mock gateway), track live and collect with a code or QR.

## Configuration

Copy `.env.example` to `.env`:

| Variable | Default | Meaning |
|---|---|---|
| `EXPO_PUBLIC_API_BASE_URL` | `http://10.0.2.2:8080` | Backend base URL. `10.0.2.2` is the host machine from the Android emulator; use your PC's LAN IP for a real phone, `http://localhost:8080` for the iOS simulator. |
| `EXPO_PUBLIC_ENABLE_PUSH` | `false` | Registers the phone for push (`POST /devices`). Needs a development build with Firebase; not available in Expo Go. |

## Run

```bash
npm install
```

```bash
npm run android
```

`npm start` opens the Expo dev server (press `a` for Android). Expo Go works for everything except push and the
native QR/secure-store edge cases of very old devices. Push needs a dev build: `npx expo run:android`.

Android blocks plain `http://` in release builds; debug/dev builds allow it. Use HTTPS for a release build.

**No emulator?** Run it in a browser for a demo:

```bash
npm run web
```

It opens on http://localhost:8081 and talks to `http://localhost:8080` by default (the backend's CORS allows port 8081).
On the web the token is kept in `sessionStorage` (expo-secure-store has no web version); phones always use secure storage.
Camera, push and secure storage are phone-only.

## Scripts

| Command | What it does |
|---|---|
| `npm run gen:api` | Regenerates `src/api/schema.d.ts` from `{EXPO_PUBLIC_API_BASE_URL}/v3/api-docs` (backend must be running). Runs `openapi-typescript` through `npx` because it declares a TypeScript 5 peer and this SDK uses TypeScript 6. |
| `npm run typecheck` | `tsc --noEmit` |
| `npm run lint` | `expo lint` (eslint-config-expo, React 19 hook rules) |
| `npm test` | Jest (jest-expo): cart + idempotency key, pay outcomes, role guard, status mapping, money, errors |

## Seed logins (backend dev profile)

| Role | Email | Password |
|---|---|---|
| Student | `arun@student.edu` | `Student@123` |
| Vendor (not accepted here) | `canteen@campus.edu` | `Vendor@123` |
| Admin (not accepted here) | `admin@campus.edu` | `Admin@123` |

Only STUDENT accounts can log in; vendor and admin accounts are refused with a pointer to their dashboard.

## How it works

- **Auth**: JWT from `POST /auth/login` in `expo-secure-store`; `Authorization: Bearer` on every call; any 401 on an
  authenticated call logs out with a notice. Register always sends `role: "STUDENT"`. Forgot/reset password included.
- **Cart**: Zustand, persisted in AsyncStorage. One canteen per cart, at most 10 of each item, totals previewed in
  integer paise. The server's order total is the amount of record.
- **Idempotency-Key**: a UUID per checkout attempt (`store/cart.ts`). The same key is reused if "Place order" is retried
  for the same cart and slot (e.g. after a network error); it is cleared when the order is created and whenever lines or the
  slot change. Buttons are disabled while a request is running.
- **Pay**: `POST /orders` then `POST /orders/{id}/pay` from the tracking screen, with a hold countdown from `holdExpiresAt`.
  200 → paid; 402 → "Payment failed, items released", back to cart; 202 → "Confirming your payment, don't pay again", polls
  `GET /orders/{id}` every 3 s with Pay and Cancel hidden; 409 HOLD_EXPIRED → back to cart. There is no card form: the
  marked `PAYMENT SDK` spot in `app/(app)/order/[id].tsx` is where a real gateway's checkout would open.
- **Mock gateway amounts** (backend dev profile): totals ending in .01 decline, .02/.03 confirm after ~20 s, .05 succeed
  but the refund fails.
- **Live updates**: `react-native-sse` (sends the auth header). Menu stream patches availability in place; order stream
  updates status, estimate, refund and reminder banners. Reconnects with exponential backoff, closes in the background,
  and refetches over REST after every reconnect.
- **OUT_OF_STOCK** is a normal flow: the cart names the item, refetches the menu and marks sold-out lines.
- **Statuses**: one label/colour table (`lib/status.ts`), identical in all three apps; badges always show the text.
- **Time** is shown in Asia/Kolkata; money with `Intl.NumberFormat('en-IN', { currency: 'INR' })`.

## Assumptions

- `GET /vendors` returns a single `estimatedWaitMinutes`; the home screen shows it as a range with the server's ETA spread
  (`high = ceil(low × 1.3) + 1`).
- Slots are offered for today only (first 12 free ones). The backend accepts up to 2 days ahead.
- "Refund initiated" shows when a `payment-refunded` event arrives or the order is REJECTED (rejections are always refunded).
- Push registers the native device token (`getDevicePushTokenAsync`) because the backend sends through FCM directly.

## TODO(contract)

- The error body `{ timestamp, status, code, message, fieldErrors }` is not in the OpenAPI document (typed in `api/errors.ts`).
- SSE payloads are untyped (`SseEmitter`). `order-status` is confirmed; `order-eta`, `payment-refunded`, `order-reminder` and
  `menu-item-update` are typed in `api/adapters.ts` from the backend source. Differences from the brief: `payment-refunded` has
  no `studentId` and includes `amount` and `reason`; `order-eta` sends `estReadyAt` plus the remaining low/high minutes.
- `OrderResponse` has no payment or refund status, so a refund after a student cancel of a PAID order is only shown live.
- Every OpenAPI field is optional (springdoc marks nothing required); adapters treat ids as present.
- Password-reset links: the backend's `app.auth.reset-link-base` defaults to a web URL. Point it at `smartcampus://reset-password`
  (or a page that redirects there) so the emailed link opens the app; the code can also be pasted by hand.
- There is no server-side range for the vendor wait time.

## Not verified

- Not run on an emulator or device here (no Android SDK on this machine); it was run in the browser build (login, canteens, menu, history). Also verified: typecheck, lint, 27 unit tests,
  `expo export --platform android` bundle, `expo-doctor` (20/20), and the login/error/401 contract against the running backend.
- Push delivery (needs Firebase credentials and a dev build).
