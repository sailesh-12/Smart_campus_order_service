# Smart Campus Food Ordering

Students order food from campus canteens on their phone, pay, track the order live and collect it with a pickup
code or QR. Canteen staff run a live order board; campus admins onboard canteens and handle complaints and refunds.

| Part | Folder | Stack | Runs on |
|---|---|---|---|
| Backend API | [`backend/`](backend/) | Spring Boot 4, Java 21, PostgreSQL, Redis, RabbitMQ, Flyway | http://localhost:8080 |
| Student app | [`mobile/`](mobile/) | React Native (Expo), TypeScript | Android emulator / phone, or a browser at http://localhost:8081 |
| Vendor dashboard | [`vendor-web/`](vendor-web/) | Next.js 16, TypeScript, Tailwind | http://localhost:3000 |
| Admin dashboard | [`admin-web/`](admin-web/) | Next.js 16, TypeScript, Tailwind | http://localhost:3001 |
| Deployment | [`infra/`](infra/), [`.github/workflows/`](.github/workflows/) | Helm, Argo CD, GitHub Actions | see [CI/CD](#cicd-and-deployment) |

Live updates (order board, order tracking) use Server-Sent Events. Payments go through a **mock gateway** in the
dev profile, so nothing real is charged.

---

## 1. Prerequisites

| Tool | Version | Check |
|---|---|---|
| Git | any recent | `git --version` |
| Java (JDK) | **21** (e.g. [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21)) | `java -version` |
| Node.js | **24** (CI uses 24.14.1) with npm | `node -v` |
| Docker Desktop | any recent, running | `docker compose version` |

Maven is **not** needed: the backend ships the Maven wrapper (`mvnw` / `mvnw.cmd`).

Docker runs PostgreSQL, Redis and RabbitMQ for you. No Docker? See [Option B](#option-b-hosted-services-no-docker).

For the student app on Android you also need Android Studio with an emulator, or the Expo Go app on a phone.
The browser version needs nothing extra.

> **Windows:** run the commands in PowerShell. If `npm` fails with *"running scripts is disabled on this
> system"*, use `npm.cmd` instead of `npm` (e.g. `npm.cmd install`), or allow local scripts once with
> `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`.

---

## 2. Get the code

```bash
git clone https://github.com/sailesh-12/Smart_campus_order_service.git
cd Smart_campus_order_service
```

> **Windows:** if the clone fails with *"Filename too long"*, run `git config --global core.longpaths true` and
> clone again.

---

## 3. Start the databases (Option A: Docker, recommended)

From the repository root:

```bash
docker compose up -d
docker compose ps        # wait until postgres, redis and rabbitmq show "healthy"
```

This starts PostgreSQL (5432), Redis (6379) and RabbitMQ (5672, management UI on http://localhost:15672,
`guest` / `guest`) with exactly the settings the backend uses by default. Data survives restarts; `docker compose
down -v` wipes it.

### Option B: hosted services (no Docker)

Create free instances of [Neon](https://neon.tech) (PostgreSQL), [Upstash](https://upstash.com) (Redis) and
[CloudAMQP](https://www.cloudamqp.com) (RabbitMQ), then copy the template and fill it in:

```bash
cd backend
cp .env.example .env      # Windows PowerShell: Copy-Item .env.example .env
```

`backend/.env` is gitignored and loaded automatically. Never commit it.

---

## 4. Run everything (4 terminals)

Open four terminals in the repository root.

**Terminal 1: backend**

```bash
cd backend
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```

The first start downloads dependencies (a few minutes). It is ready when the log shows `Started BackendApplication`.
On first start Flyway creates the tables and loads demo data (canteens, menus, users).

Check it:
- Health: http://localhost:8080/actuator/health → `{"status":"UP"}`
- API docs (Swagger UI): http://localhost:8080/swagger-ui.html
- Canteens: http://localhost:8080/vendors

**Terminal 2: vendor dashboard**

```bash
cd vendor-web
npm install
npm run dev
```

Open http://localhost:3000.

**Terminal 3: admin dashboard**

```bash
cd admin-web
npm install
npm run dev
```

Open http://localhost:3001.

**Terminal 4: student app**

```bash
cd mobile
npm install
npm run web          # in a browser at http://localhost:8081
# or: npm run android  (Android emulator; press "a" if it does not open by itself)
```

The app talks to `http://localhost:8080` in the browser and `http://10.0.2.2:8080` (your PC, seen from the
emulator) on Android. For a real phone (Expo Go), copy `mobile/.env.example` to `mobile/.env.local` (gitignored)
and set `EXPO_PUBLIC_API_BASE_URL=http://<your-PC-LAN-IP>:8080` (`ipconfig` / `ip addr`; Metro prints the same IP
as `exp://<ip>:8081`), then restart with `npx expo start -c`. The web dashboards read `.env.local`
(`NEXT_PUBLIC_API_BASE_URL`), which you only need if the backend is not on localhost:8080.

Keep these ports: the backend only accepts browser requests from 3000, 3001 and 8081 (CORS).

---

## 5. Log in (demo accounts)

Created by the dev profile's seed data:

| Role | Email | Password | Use in |
|---|---|---|---|
| Student | `arun@student.edu` | `Student@123` | student app |
| Student | `priya@student.edu` | `Student@123` | student app |
| Vendor (Main Canteen) | `canteen@campus.edu` | `Vendor@123` | vendor dashboard |
| Vendor (Rishabs) | `rishabs@campus.edu` | `Vendor@123` | vendor dashboard |
| Vendor (Metro Cafe) | `metro@campus.edu` | `Vendor@123` | vendor dashboard |
| Admin | `admin@campus.edu` | `Admin@123` | admin dashboard |

Each app only accepts its own role.

---

## 6. Try the whole flow

1. **Vendor dashboard** (http://localhost:3000): log in as `canteen@campus.edu` and leave the order board open.
2. **Student app** (http://localhost:8081): log in as `arun@student.edu`, open *Main Canteen*, add items, check out
   and pay.
3. The order appears on the vendor board immediately (with a chime). Move it **Accept → Preparing → Ready**;
   the student app updates live.
4. The student app shows a pickup **code** and **QR**. On the vendor dashboard, verify the pickup with that code;
   the order becomes **Collected**.
5. **Admin dashboard** (http://localhost:3001): log in as `admin@campus.edu` to see the campus overview, orders,
   payment issues, complaints and the audit log.

The mock gateway decides the payment result from the last two digits (paise) of the order total: `.01` is
declined, `.02`/`.03` settle after 20 s, `.05` succeeds but its refund fails; anything else succeeds at once.
Full table: [backend/README.md](backend/README.md#mock-payment-gateway-dev-only).

---

## 7. Run the tests

| Where | Command | What runs |
|---|---|---|
| `backend/` | `./mvnw test` | unit tests + JaCoCo coverage (`target/site/jacoco/index.html`); no database needed |
| `backend/` | `./mvnw verify` | unit + integration tests; needs Docker (Testcontainers starts PostgreSQL and RabbitMQ) and Redis on localhost:6379 (`docker compose up -d redis`) |
| `vendor-web/`, `admin-web/` | `npm test`, `npm run lint`, `npm run typecheck`, `npm run build` | Vitest, ESLint, TypeScript, production build |
| `mobile/` | `npm test`, `npm run lint`, `npm run typecheck` | Jest, ESLint, TypeScript |

On Windows use `.\mvnw.cmd` instead of `./mvnw`. CI runs all of these on every pull request.

---

## 8. Troubleshooting

| Problem | Fix |
|---|---|
| Backend fails with `Connection refused` to 5432 / 6379 / 5672 | The databases are not running: `docker compose up -d` and wait for "healthy". |
| `Port 8080 was already in use` | Stop the other process, or another backend you started earlier. |
| Browser shows a CORS error | Run the frontends on their default ports (3000, 3001, 8081). |
| Student app: "can't reach the server" on login | Emulator: keep `10.0.2.2`. Real phone: set your PC's LAN IP in `mobile/.env.local` and restart with `npx expo start -c` (the IP changes when you switch networks). Open `http://<PC-IP>:8080/actuator/health` in the phone's browser to test: if it does not load, allow inbound TCP 8080 in Windows Firewall (admin PowerShell: `New-NetFirewallRule -DisplayName "SmartCampus backend 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow`), or the Wi-Fi isolates devices (campus/office networks): use a phone hotspot, or USB with `adb reverse tcp:8080 tcp:8080` and `http://localhost:8080`. |
| Expo Go: `Cannot find native module ...` | Run `npx expo install --fix` in `mobile/`, update Expo Go on the phone, then `npx expo start -c`. |
| Login says too many attempts (429) | The login rate limit is 10 per minute; wait a minute. |
| Demo data missing | The seed only loads with the `dev` profile (the default). `docker compose down -v` and start again for a clean database. |

---

## Project layout

```
backend/      Spring Boot API: modules auth, vendor, menu, order, payment, notify, admin (see backend/README.md)
mobile/       Expo student app
vendor-web/   Next.js vendor dashboard
admin-web/    Next.js admin dashboard
infra/        Helm chart, per-environment values, Argo CD apps, local kind cluster, load-test demo
docs/         CI/CD guide
docker-compose.yml   local PostgreSQL, Redis, RabbitMQ
```

More detail per part: [backend](backend/README.md) (API, state machine, business rules),
[mobile](mobile/README.md), [vendor-web](vendor-web/README.md), [admin-web](admin-web/README.md).

## CI/CD and deployment

GitHub Actions tests every pull request (backend tests, SonarCloud quality gate, frontend checks, Helm lint,
Gitleaks and Trivy security scans). A merge to `main` builds one Docker image per commit, pushes it to
`ghcr.io/sailesh-12/smart_campus_order_service/backend:<commit-sha>` and points the dev environment at it; Argo
CD deploys it. Staging and prod are promoted manually.

- Pipeline, secrets, promotion and rollback: [docs/ci-cd.md](docs/ci-cd.md)
- Kubernetes (Helm, Argo CD, secrets): [infra/README.md](infra/README.md)
- Run the dev environment on a local Kubernetes cluster with Argo CD, Prometheus and Grafana: [infra/local/README.md](infra/local/README.md)

## License

[MIT](LICENSE)
