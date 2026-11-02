#!/usr/bin/env bash
# Creates (or updates) the smartcampus-secrets Secret in one namespace from LOCAL environment variables.
# No values are stored in this file or in Git. Run it against the cluster you mean to change.
#
#   export DB_URL='jdbc:postgresql://<neon-pooled-host>/<db>?sslmode=require'
#   export DB_USERNAME=... DB_PASSWORD=...
#   export REDIS_HOST=... REDIS_PORT=6379 REDIS_PASSWORD=... REDIS_SSL=true
#   export RABBIT_ADDRESSES='amqps://<user>:<password>@<host>/<vhost>'
#   export JWT_SECRET="$(openssl rand -base64 48)" PAYMENT_WEBHOOK_SECRET="$(openssl rand -hex 32)"
#   infra/scripts/create-secrets.sh smartcampus-dev
#
# Optional: DB_DIRECT_URL (Neon direct, non-pooled URL used by Flyway), MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD
# (MAIL_HOST is required for the prod profile).
set -euo pipefail

namespace="${1:?usage: create-secrets.sh <namespace>}"
required=(DB_URL DB_USERNAME DB_PASSWORD REDIS_HOST REDIS_PORT REDIS_PASSWORD REDIS_SSL RABBIT_ADDRESSES JWT_SECRET PAYMENT_WEBHOOK_SECRET)
optional=(DB_DIRECT_URL MAIL_HOST MAIL_USERNAME MAIL_PASSWORD)

missing=()
for name in "${required[@]}"; do
  [ -n "${!name:-}" ] || missing+=("$name")
done
if [ "${#missing[@]}" -gt 0 ]; then
  echo "Missing environment variables: ${missing[*]}" >&2
  exit 1
fi
if [ "${#JWT_SECRET}" -lt 32 ]; then
  echo "JWT_SECRET must be at least 32 characters" >&2
  exit 1
fi

args=()
for name in "${required[@]}" "${optional[@]}"; do
  if [ -n "${!name:-}" ]; then
    args+=("--from-literal=${name}=${!name}")
  fi
done

kubectl create namespace "$namespace" --dry-run=client -o yaml | kubectl apply -f -
# Render client-side and apply, so re-running updates the Secret instead of failing
kubectl -n "$namespace" create secret generic smartcampus-secrets "${args[@]}" --dry-run=client -o yaml \
  | kubectl apply -f -
echo "smartcampus-secrets updated in $namespace (keys: $(printf '%s ' "${required[@]}"))"
