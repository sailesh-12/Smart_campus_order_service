# infra/: GitOps for the Smart Campus backend

Everything Argo CD deploys lives here; the repository itself is the GitOps repository.

```
infra/
├── helm/
│   ├── backend/            Helm chart (Deployment, Service, Ingress, HPA, PDB, ServiceAccount, ConfigMap)
│   ├── values-dev.yaml     image.tag edited by backend-ci.yml (deploy-dev)
│   ├── values-staging.yaml image.tag edited by promote.yml
│   └── values-prod.yaml    image.tag edited by promote.yml
├── argocd/                 AppProjects (smartcampus, platform) + one Application per environment + monitoring
├── scripts/
│   ├── create-secrets.sh   builds the smartcampus-secrets Secret from local env vars
│   ├── secrets-from-env-file.sh  same, reading backend/.env
│   └── set-image-tag.sh    the only way CI edits a values file
├── local/                  kind / minikube walkthrough
└── demo/                   lunch-rush autoscaling, failed rollout, pod replacement
```

Data services are external managed services (Neon Postgres, Upstash Redis, CloudAMQP RabbitMQ). Nothing
here deploys a database, cache or broker. Monitoring (Prometheus + Grafana, kube-prometheus-stack) is an Argo CD
Application of its own: [`argocd/application-monitoring.yaml`](argocd/application-monitoring.yaml); the backend chart adds a
ServiceMonitor, alert rules and a Grafana dashboard (`metrics.*` values, enabled in dev).

## Secrets

No secret value is in Git. Each namespace needs a Secret named `smartcampus-secrets`, created with
`infra/scripts/create-secrets.sh <namespace>` from environment variables on your machine:

| Key | Used as | Notes |
|---|---|---|
| `DB_URL` | `DB_URL` → `spring.datasource.url` | Neon pooled JDBC URL, `?sslmode=require` |
| `DB_DIRECT_URL` (optional) | `SPRING_FLYWAY_URL` | Neon direct URL for migrations; falls back to `DB_URL` |
| `DB_USERNAME`, `DB_PASSWORD` | datasource and Flyway | |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL` | `spring.data.redis.*` | Upstash: `REDIS_SSL=true` |
| `RABBIT_ADDRESSES` | `SPRING_RABBITMQ_ADDRESSES` | CloudAMQP `amqps://user:pass@host/vhost` (one URL carries user, vhost and TLS) |
| `JWT_SECRET` | `app.jwt.secret` | at least 32 characters, different per environment |
| `PAYMENT_WEBHOOK_SECRET` | `app.payment.webhook-secret` | |
| `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD` (optional) | `spring.mail.*` | **required in prod**: the prod profile sends mail over SMTP and will not start without a host |

Use a **separate Neon database (or branch) per environment.** The dev profile loads seed data (Flyway
`db/seed`); staging and prod must not, and Flyway's validation fails if an environment sees seed migrations
it does not have locally.

Later: replace the script with **Sealed Secrets** (commit encrypted `SealedSecret` objects under `infra/`) or
**External Secrets Operator** (sync from a cloud secret manager). The chart only references the Secret by name,
so neither needs a chart change.

### Private GHCR package

If the `backend` package in GHCR is private, give each namespace a pull secret and reference it:

```bash
kubectl -n smartcampus-dev create secret docker-registry ghcr-pull \
  --docker-server=ghcr.io --docker-username=<github-user> --docker-password=<PAT with read:packages>
```

and in the values file: `imagePullSecrets: [{ name: ghcr-pull }]`. Alternatively make the package public
(GitHub → Packages → backend → Package settings → Change visibility).
