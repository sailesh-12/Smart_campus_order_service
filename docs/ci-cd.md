# CI/CD

GitHub Actions builds and tests; this repository's `infra/` folder is the GitOps source; Argo CD makes each
cluster match it. One image per commit is built once and promoted dev → staging → prod, never rebuilt, never
tagged `latest`.

## The pipeline

```
pull request ──► CI (per changed area)                              ┐
                  backend-ci: unit-tests ─┐                          │ all must pass
                              integration-tests ─┴► sonarqube ─► image (build + Trivy, not pushed)   │ (branch protection)
                  frontend-ci: lint, typecheck, test, build (changed apps)                           │
                  infra-ci:    helm lint + helm template (dev/staging/prod)                          │
                  security:    gitleaks (full history) + Trivy fs + Trivy config                     ┘
        │ merge
        ▼
push to main ──► backend-ci again ─► image: build ─► Trivy ─► push ghcr.io/<owner>/<repo>/backend:<sha>
                                   └► deploy-dev: commit "image.tag: <sha>" to infra/helm/values-dev.yaml
                                                 └► smoke test /actuator/health/readiness (if DEV_BASE_URL set)
        ▼
Argo CD (smartcampus-dev, auto-sync) ─► rolling update in smartcampus-dev
        ▼
promote.yml (manual, workflow_dispatch: environment + image_tag)
   ├─ staging: check the tag exists in GHCR ─► commit values-staging.yaml ─► Argo CD auto-syncs
   └─ prod:    required reviewers approve ─► same ─► someone runs `argocd app sync smartcampus-prod`
```

| Workflow | Trigger | Jobs |
|---|---|---|
| `backend-ci.yml` | PR + push to main, `backend/**` only; superseded runs cancelled | `unit-tests` (Surefire `*Test`, JaCoCo) → `integration-tests` (Failsafe `integration/*IT`, Testcontainers Postgres + RabbitMQ, `redis:7` service) → `sonarqube` (quality gate) → `image` (Trivy, push on main) → `deploy-dev` (main) |
| `security.yml` | every PR + push to main, no path filter | `gitleaks` (full history), `trivy` (dependencies + configuration) |
| `frontend-ci.yml` | `mobile/**`, `vendor-web/**`, `admin-web/**` | matrix over the changed apps: `npm ci`, lint, typecheck, test, build (web apps) |
| `infra-ci.yml` | `infra/**` | `helm lint --strict` + `helm template` per environment; Argo CD manifests parse and reference existing values files |
| `promote.yml` | manual | promote an existing image tag to staging or prod |

Tests never use the hosted services or any secret: unit tests have no Spring context; integration tests get
throwaway Postgres/RabbitMQ containers and the CI Redis service, with the `test` profile, and Maven points
`spring.config.import` at a non-existent file so a developer's `backend/.env` is never read.

The deploy-dev commit touches only `infra/**`, which is not in backend-ci's path filter, so it never retriggers
the backend pipeline (and commits pushed with `GITHUB_TOKEN` do not start workflows anyway).

## Secrets and variables to create

Repository → Settings → Secrets and variables → Actions:

| Name | Kind | Used by | Value |
|---|---|---|---|
| `SONAR_TOKEN` | secret | backend-ci `sonarqube` | SonarQube/SonarCloud token with "Execute Analysis" |
| `SONAR_HOST_URL` | **variable** | backend-ci `sonarqube` | `https://sonarcloud.io` (not sensitive; a secret of the same name also works) |
| `GITOPS_TOKEN` | secret | `deploy-dev`, `promote.yml` | GitHub App installation token or fine-grained PAT with **Contents: read and write** on this repo; needed only if `main` is protected (see below) |
| `DEV_BASE_URL` | variable | `deploy-dev` smoke test | public URL of dev, e.g. `https://dev.smartcampus.example.com`; leave unset for a local cluster |

SonarCloud: organisation `sailesh-12`, project key `sailesh-12_Smart_campus_order_service` (set in `backend/pom.xml`).
Turn **Automatic Analysis off** in the SonarCloud project (Administration → Analysis Method); CI analysis is rejected while it is on.

`GITHUB_TOKEN` covers GHCR push (`packages: write`, image job only) and the promote existence check (`packages: read`).

GitHub Environments (Settings → Environments):
- `dev`: no rules (used by deploy-dev for visibility).
- `staging`: optional reviewers.
- `prod`: **required reviewers** (one or more people); optionally restrict to the `main` branch.

Cluster side (never in GitHub): the `smartcampus-secrets` Secret per namespace, see [`infra/README.md`](../infra/README.md).

## Branch protection for `main`

Settings → Branches → rule for `main`:
- Require a pull request (1 approval), dismiss stale approvals.
- Require status checks to pass, and require branches to be up to date. Required checks:
  - `Backend CI/CD / unit-tests`, `Backend CI/CD / integration-tests`, `Backend CI/CD / sonarqube`, `Backend CI/CD / image`
  - `Security / gitleaks`, `Security / trivy`
  - `Frontend CI / check (mobile)`, `Frontend CI / check (vendor-web)`, `Frontend CI / check (admin-web)`
  - `Infra CI / helm (dev)`, `Infra CI / helm (staging)`, `Infra CI / helm (prod)`, `Infra CI / argocd`

  Path-filtered workflows do not report when their files are untouched. Either mark only the always-running
  checks (`Security / *`) as required, or (GitHub rulesets) require the checks only "when the workflow runs".
- Do not allow force pushes or deletions.

### How the bot commit gets past branch protection

`deploy-dev` and `promote.yml` push one-line commits to `main`. A protected `main` rejects them unless one of:

1. **GitHub App (recommended).** Create an App with *Contents: read & write*, install it on this repo, add it to
   the rule's **bypass list**, and put an installation token in `GITOPS_TOKEN` (e.g. generate it with
   `actions/create-github-app-token` in the workflow). Commits are attributed to the App and auditable.
2. **Fine-grained PAT** of a bot/admin account that is on the bypass list, stored as `GITOPS_TOKEN`. Simple, but
   tied to a person and must be rotated.
3. **Auto-merge PR.** Change the workflow to push a branch and open a PR with auto-merge; the required checks run
   on it (infra-ci) and it merges itself. Slowest, but needs no bypass at all.

Without `GITOPS_TOKEN` the workflows fall back to `GITHUB_TOKEN`, which works only while `main` is unprotected.

## Promote

Actions → **Promote** → Run workflow:
- `environment`: `staging` or `prod`
- `image_tag`: the full commit SHA running in dev (`grep 'tag:' infra/helm/values-dev.yaml`, or the
  `chore(dev): deploy backend <sha>` commit, or the `Backend CI/CD` run).

The workflow checks that `ghcr.io/<owner>/<repo>/backend:<tag>` exists, edits only `image.tag` in
`infra/helm/values-<env>.yaml`, and commits. Staging syncs automatically. Prod waits for the environment
reviewers, then needs a manual `argocd app sync smartcampus-prod` (or the Sync button).

## Roll back

Every deployment is one commit that changes one line, so a rollback is a revert:

```bash
git log --oneline -- infra/helm/values-prod.yaml        # find "chore(prod): promote backend <sha>"
git revert <that commit>                                  # restores the previous image.tag
git push                                                  # via a PR if main is protected
argocd app sync smartcampus-prod                          # prod only; dev/staging sync themselves
```

Same for dev (`chore(dev): deploy backend ...`) and staging. Configuration changes in `infra/` roll back the same
way. `argocd app rollback` also works in an emergency, but Argo CD with auto-sync will move back to Git, so the
revert is the durable fix.

## Known gaps (decisions needed outside this repository)

- **Leaked credentials:** the Neon, Upstash and CloudAMQP credentials committed in `94df2c9` are still in git
  history. They were removed from the working tree, and `.gitleaksignore` lists that historical finding so the
  full-history scan can pass. **Rotate all three.** Rewriting history is the only way to drop the ignore entry.
- **Multi-pod live updates:** staging and prod set `app.events.transport=rabbit` because each pod only holds its
  own SSE connections. The backend currently declares the RabbitMQ exchange/queue for this mode but has no
  publisher/listener, so events still stay inside one pod. Until that relay exists, clients on another pod see
  updates only after reconnecting/refetching (REST stays correct).
- **No real payment gateway:** staging and prod run without the mock gateway; payments stay UNCONFIRMED until a
  real `PaymentGateway` implementation is added.
- **Push in prod:** the prod profile selects FCM; mount the service-account file and set `FCM_CREDENTIALS_PATH`,
  otherwise pushes fail (logged, never breaking orders).
