# Local cluster: kind + Argo CD + Prometheus + Grafana

Runs the **dev** environment on your machine exactly as a real cluster would: Argo CD watches `main` and deploys
whatever `infra/helm/values-dev.yaml` says, and the kube-prometheus-stack (Prometheus, Grafana, kube-state-metrics,
node-exporter) monitors it. Nothing data-related runs in the cluster: the backend uses the hosted Neon, Upstash
and CloudAMQP services from `backend/.env`, so the cluster needs outbound internet (5432, 6379 TLS, 5671 TLS).

When you are done you have:

| URL | What |
|---|---|
| http://smartcampus.localtest.me/actuator/health | the backend, served by Kubernetes through ingress-nginx |
| http://grafana.localtest.me | Grafana (dashboard "Smart Campus backend") |
| http://prometheus.localtest.me | Prometheus (Status → Targets, Alerts) |
| https://localhost:8443 | Argo CD UI (port-forward) |

`*.localtest.me` always resolves to 127.0.0.1, and kind maps ports 80/443 of your PC to the ingress controller.

## 0. Install the tools (once)

Resources: Docker Desktop with **4 CPUs and 8 GB memory** or more (Settings → Resources). Tested versions:
kind v0.33.0, kubectl matching Kubernetes 1.37, helm v3.22.1.

**Windows** (PowerShell; Docker Desktop needs admin rights and WSL 2, and may ask for a restart):

```bash
winget install -e --id Docker.DockerDesktop
winget install -e --id Kubernetes.kubectl
winget install -e --id Kubernetes.kind
winget install -e --id Helm.Helm
winget install -e --id argoproj.argocd
```

Open a **new** terminal afterwards so the tools are on PATH, start Docker Desktop, and check:

```bash
docker version
kubectl version --client
kind version
helm version
```

macOS: `brew install --cask docker` and `brew install kubectl kind helm argocd`. Linux: see each tool's docs.

Run the remaining commands in **Git Bash** (Windows) or any shell (macOS/Linux) from the repository root.
The scripts in `infra/scripts` are bash.

## 1. Create the cluster

```bash
kind create cluster --config infra/local/kind-config.yaml
kubectl cluster-info --context kind-smartcampus
```

minikube instead: `minikube start --cpus 4 --memory 8g --kubernetes-version v1.37.0 && minikube addons enable ingress && minikube addons enable metrics-server`, then skip steps 2 and 3.

## 2. ingress-nginx (pinned)

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.15.1/deploy/static/provider/kind/deploy.yaml
kubectl -n ingress-nginx wait --for=condition=ready pod -l app.kubernetes.io/component=controller --timeout=180s
```

## 3. metrics-server (pinned; needed by the HPA)

```bash
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/download/v0.9.0/components.yaml
# kind kubelets use self-signed certificates
kubectl -n kube-system patch deployment metrics-server --type=json \
  -p '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
kubectl -n kube-system rollout status deployment/metrics-server
kubectl top nodes   # works after about a minute
```

## 4. Argo CD (pinned)

```bash
kubectl create namespace argocd
kubectl apply -n argocd --server-side --force-conflicts -f https://raw.githubusercontent.com/argoproj/argo-cd/v3.5.6/manifests/install.yaml
kubectl -n argocd rollout status deployment/argocd-server --timeout=5m
kubectl -n argocd rollout status statefulset/argocd-application-controller --timeout=5m
```

Admin password (user `admin`), then open the UI in a second terminal and keep it running:

```bash
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d; echo
kubectl -n argocd port-forward svc/argocd-server 8443:443     # https://localhost:8443 (accept the self-signed certificate)
```

## 5. Monitoring: Prometheus + Grafana (through Argo CD)

The Grafana admin password lives only in the cluster. Choose one and create the Secret:

```bash
kubectl create namespace monitoring
kubectl -n monitoring create secret generic grafana-admin \
  --from-literal=admin-user=admin --from-literal=admin-password='choose-a-password'
```

Register the `platform` project and the monitoring Application (kube-prometheus-stack, chart pinned in
`infra/argocd/application-monitoring.yaml`):

```bash
kubectl apply -n argocd -f infra/argocd/project-platform.yaml -f infra/argocd/application-monitoring.yaml
kubectl -n argocd get application monitoring -w        # wait for Synced / Healthy (2-4 minutes), Ctrl+C
kubectl -n monitoring get pods
```

Install monitoring **before** the backend: the backend chart only renders its ServiceMonitor and alert rules when
the Prometheus Operator CRDs already exist.

## 6. Secrets for dev

From your filled-in `backend/.env` (the same file you use to run the backend locally):

```bash
infra/scripts/secrets-from-env-file.sh smartcampus-dev
```

(Or export the variables yourself and run `infra/scripts/create-secrets.sh smartcampus-dev`; see
[`../README.md`](../README.md).)

## 7. Deploy dev through Argo CD

```bash
kubectl apply -n argocd -f infra/argocd/project.yaml -f infra/argocd/application-dev.yaml
kubectl -n argocd get application smartcampus-dev -w          # wait for Synced / Healthy, Ctrl+C
kubectl -n smartcampus-dev get pods,svc,ingress,hpa,servicemonitor
```

The first start takes 1-2 minutes (Flyway migrations, Spring startup); the readiness probe keeps the pod out of
the Service until it is ready. If the pod shows `ImagePullBackOff`, the dev image tag does not exist yet: Backend
CI/CD must have run on `main` at least once.

## 8. Open everything

```bash
curl http://smartcampus.localtest.me/actuator/health/readiness   # {"status":"UP"}
curl http://smartcampus.localtest.me/vendors
curl -s http://smartcampus.localtest.me/actuator/prometheus | head   # the metrics Prometheus scrapes
```

- **Grafana:** http://grafana.localtest.me, user `admin` and the password from step 5 → Dashboards →
  **Smart Campus backend** (environment `smartcampus-dev`). Kubernetes dashboards (nodes, pods, workloads) come
  with the stack.
- **Prometheus:** http://prometheus.localtest.me → Status → Targets: `serviceMonitor/smartcampus-dev/...` is
  **UP**. Alerts tab: the `smartcampus-backend` rules (backend down, 5xx rate, p95 latency, heap).
- **Argo CD:** https://localhost:8443: the `smartcampus-dev` and `monitoring` apps, their resource trees and
  sync history.

Point the frontends at the cluster with `NEXT_PUBLIC_API_BASE_URL=http://smartcampus.localtest.me` /
`EXPO_PUBLIC_API_BASE_URL=...` (run the web apps on ports 3000/3001/8081 for CORS).

## 9. See GitOps work

Merge any backend change to `main`. Backend CI/CD builds the image and commits the new tag to
`infra/helm/values-dev.yaml`; within about 3 minutes Argo CD notices the commit and rolls the pods
(`kubectl -n smartcampus-dev get pods -w`). Press **Refresh** in the Argo CD UI to make it look immediately.

For the load test, failed-rollout and pod-replacement demos (watch them in Grafana), see
[`../demo/README.md`](../demo/README.md).

## Troubleshooting

| Problem | Fix |
|---|---|
| `localtest.me` does not open | Port 80 is taken on your PC (IIS, Skype, another web server); stop it and recreate the cluster. |
| `monitoring` app stuck / CRD errors | `kubectl -n argocd get application monitoring -o yaml` → status.conditions. Re-sync from the UI; `ServerSideApply=true` is required for the CRDs. |
| No ServiceMonitor in smartcampus-dev | Monitoring was installed after the backend: Argo CD → smartcampus-dev → **Refresh** (hard refresh) so the chart re-renders with the CRDs present. |
| Target is DOWN with 404 | The running image predates the Prometheus endpoint. It appears once a build of `main` with `micrometer-registry-prometheus` is deployed. |
| Pods `Pending` | Not enough memory: give Docker Desktop 8 GB or more (Settings → Resources) and restart it. `kubectl describe pod <name>` shows the reason. |
| Backend `CrashLoopBackOff` | `kubectl -n smartcampus-dev logs deploy/smartcampus-backend`; usually a wrong value in `smartcampus-secrets` (rerun step 6, then `kubectl -n smartcampus-dev rollout restart deploy`). |

## Clean up

```bash
kind delete cluster --name smartcampus
```
