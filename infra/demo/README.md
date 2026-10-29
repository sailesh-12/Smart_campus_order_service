# Demo scenarios (local kind cluster, dev environment)

Prerequisite: the local setup in [`../local/README.md`](../local/README.md) is done and
`kubectl -n argocd get application smartcampus-dev` shows **Synced / Healthy**.

Handy in a second terminal for all three:

```bash
kubectl -n smartcampus-dev get pods -w
```

Dev normally runs 1 to 2 pods. For the demos give it the staging-sized autoscaler (2 to 6) through Argo CD
parameters, and remove them afterwards:

```bash
argocd app set smartcampus-dev -p autoscaling.minReplicas=2 -p autoscaling.maxReplicas=6
# afterwards:
argocd app unset smartcampus-dev -p autoscaling.minReplicas -p autoscaling.maxReplicas
```

(Without the argocd CLI: change the two numbers in `infra/helm/values-dev.yaml` on a branch, merge, and revert
afterwards; that is the GitOps way, just slower.)

---

## 1. Peak lunch traffic: the HPA goes 2 → 4 → 6

```bash
kubectl -n smartcampus-dev get hpa -w                    # terminal A: TARGETS and REPLICAS
kubectl top pods -n smartcampus-dev                      # terminal B, repeat while k6 runs
k6 run -e BASE_URL=http://smartcampus.localtest.me infra/demo/lunch-rush.js   # terminal C
# no k6 installed:
docker run --rm -i --network host -e BASE_URL=http://smartcampus.localtest.me grafana/k6:1.3.0 run - < infra/demo/lunch-rush.js
```

What to show:
- CPU passes the 70 % target during the 250-user stage; the HPA adds 2 pods per 30 s (4, then 6).
- New pods only receive traffic once `/actuator/health/readiness` is UP, so `http_req_failed` stays near 0.
- After the rush the HPA waits 5 minutes (`scaleDown.stabilizationWindowSeconds: 300`) before removing pods one per minute.

If it does not scale on a fast laptop, raise the `target` values in `lunch-rush.js` or lower the pod CPU limit.

---

## 2. A failed deployment that never takes the service down

Break the readiness probe on purpose, through Git:

```bash
git switch -c demo/broken-readiness
cat >> infra/helm/values-dev.yaml <<'EOF'
probes:
  readiness:
    path: /actuator/health/does-not-exist   # demo: new pods never become Ready
EOF
git commit -am "demo: break readiness probe" && git push -u origin demo/broken-readiness
# merge the PR (or, for a quick demo, point the dev Application at this branch:
#   argocd app set smartcampus-dev --revision demo/broken-readiness)
```

Watch:

```bash
kubectl -n smartcampus-dev rollout status deployment/smartcampus-backend      # stalls
kubectl -n smartcampus-dev get pods                                           # new pod 0/1 Running, old pods 1/1
while true; do curl -s -o /dev/null -w "%{http_code}\n" http://smartcampus.localtest.me/vendors; sleep 1; done
```

What to show:
- `maxUnavailable: 0`: the old pods keep serving (`200` every second) while the new pod stays `0/1`.
- After 10 minutes the Deployment reports `ProgressDeadlineExceeded`; Argo CD shows the app **Degraded**.

Recover with a Git revert (the same as any production rollback):

```bash
git revert --no-edit HEAD && git push            # or revert the merge commit on main
# (if you used --revision: argocd app set smartcampus-dev --revision main)
kubectl -n smartcampus-dev rollout status deployment/smartcampus-backend      # completes
```

---

## 3. A pod disappears and the service stays up

```bash
while true; do curl -s -o /dev/null -w "%{http_code} " http://smartcampus.localtest.me/actuator/health/readiness; sleep 0.5; done
kubectl -n smartcampus-dev delete pod "$(kubectl -n smartcampus-dev get pods -l app.kubernetes.io/name=backend -o jsonpath='{.items[0].metadata.name}')"
```

What to show:
- The curl loop keeps printing `200`: the Service only routes to Ready pods, and the second pod carries the traffic.
- The ReplicaSet starts a replacement at once; it joins the Service after its startup and readiness probes pass.
- The deleted pod gets a graceful shutdown: `preStop` sleeps 5 s so the endpoint is removed first, then Spring
  drains in-flight requests (`terminationGracePeriodSeconds: 40`).
- Open SSE streams on the deleted pod reconnect to the other pod and the apps refetch over REST.

The PodDisruptionBudget (`minAvailable: 1`) protects against *voluntary evictions* such as `kubectl drain`;
a direct `kubectl delete pod` is not an eviction, which is why the second replica matters here.
