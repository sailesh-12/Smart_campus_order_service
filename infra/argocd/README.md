# Argo CD

`project.yaml` limits the `smartcampus` AppProject to this repository and the namespaces
`smartcampus-dev`, `smartcampus-staging` and `smartcampus-prod`. One Application per environment renders
`infra/helm/backend` with `infra/helm/values-<env>.yaml`.

| Application | Sync | Self-heal / prune |
|---|---|---|
| `smartcampus-dev` | automatic | yes |
| `smartcampus-staging` | automatic | yes |
| `smartcampus-prod` | **manual** | n/a: a person runs the sync after a promotion |

All three create their namespace (`CreateNamespace=true`) and retry failed syncs with backoff (10 s, x2, max 3 min, 5 tries).

## Install (pinned)

```bash
kubectl create namespace argocd
kubectl apply -n argocd --server-side --force-conflicts -f https://raw.githubusercontent.com/argoproj/argo-cd/v3.5.6/manifests/install.yaml
kubectl -n argocd rollout status deployment/argocd-server --timeout=5m
```

Initial admin password and UI:

```bash
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
kubectl -n argocd port-forward svc/argocd-server 8443:443   # https://localhost:8443, user admin
```

## Register the project and applications

Create `smartcampus-secrets` in each namespace first (`infra/scripts/create-secrets.sh`), then:

```bash
kubectl apply -n argocd -f infra/argocd/project.yaml
kubectl apply -n argocd -f infra/argocd/application-dev.yaml
# staging / prod when those clusters or namespaces are ready:
kubectl apply -n argocd -f infra/argocd/application-staging.yaml -f infra/argocd/application-prod.yaml
```

If the repository is private, add it first: `argocd repo add https://github.com/sailesh-12/Smart_campus_order_service.git --username <user> --password <PAT>`.

## Check sync status

```bash
kubectl -n argocd get applications                       # SYNC STATUS and HEALTH per environment
argocd app get smartcampus-dev                            # resources, last sync, current image tag
argocd app history smartcampus-dev                        # which Git revisions were deployed
argocd app sync smartcampus-prod                          # prod: manual sync after a promotion
argocd app wait smartcampus-prod --health --timeout 600
```
