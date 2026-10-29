# Local cluster (kind, or minikube)

Runs the **dev** environment on your machine through Argo CD, exactly as a real cluster would: Argo CD watches
`main` and deploys whatever `infra/helm/values-dev.yaml` says.

**Prerequisites:** Docker Desktop (4 CPUs / 8 GB recommended), `kubectl`, `kind` v0.33.0 (or minikube), `helm` v3.22.1,
optionally the `argocd` CLI. The cluster must reach **Neon, Upstash and CloudAMQP over the internet** (outbound
5432, 6379 TLS, 5671 TLS); nothing data-related runs inside the cluster.

The dev image must exist in GHCR, i.e. Backend CI/CD has run on `main` at least once. Until then the
Deployment shows `ImagePullBackOff` for the placeholder tag.

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

## 4. Argo CD

Follow "Install (pinned)" in [`../argocd/README.md`](../argocd/README.md).

## 5. Secrets for dev

Export the dev values (see the table in [`../README.md`](../README.md)), then:

```bash
infra/scripts/create-secrets.sh smartcampus-dev
```

## 6. Deploy dev through Argo CD

```bash
kubectl apply -n argocd -f infra/argocd/project.yaml -f infra/argocd/application-dev.yaml
kubectl -n argocd get application smartcampus-dev -w          # wait for Synced / Healthy
kubectl -n smartcampus-dev get pods,svc,ingress,hpa
```

## 7. Open the app

```bash
curl http://smartcampus.localtest.me/actuator/health/readiness   # {"status":"UP"}
curl http://smartcampus.localtest.me/vendors
```

`smartcampus.localtest.me` resolves to 127.0.0.1, which kind maps to the ingress controller. Point the frontends
at it with `NEXT_PUBLIC_API_BASE_URL=http://smartcampus.localtest.me` / `EXPO_PUBLIC_API_BASE_URL=...`.

Note: the backend's CORS list allows `localhost:3000/3001/8081`, so run the frontends from those ports.

## Clean up

```bash
kind delete cluster --name smartcampus
```
