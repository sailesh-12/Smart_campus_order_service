#!/usr/bin/env bash
# Creates smartcampus-secrets in a namespace from backend/.env (the file used for local runs), by mapping its
# keys to the variables create-secrets.sh expects. Values never leave your machine except into the cluster.
#
#   infra/scripts/secrets-from-env-file.sh smartcampus-dev            # reads backend/.env
#   infra/scripts/secrets-from-env-file.sh smartcampus-dev path/to/.env
#
# JWT_SECRET and PAYMENT_WEBHOOK_SECRET are generated when the file leaves them empty.
set -euo pipefail

namespace="${1:?usage: secrets-from-env-file.sh <namespace> [env-file]}"
script_dir="$(cd "$(dirname "$0")" && pwd)"
env_file="${2:-$script_dir/../../backend/.env}"
[ -f "$env_file" ] || { echo "No env file at $env_file (copy backend/.env.example to backend/.env and fill it in)" >&2; exit 1; }

# Parse KEY=VALUE lines without sourcing (keys like spring.rabbitmq.addresses are not valid shell names)
while IFS= read -r line || [ -n "$line" ]; do
  line="${line%$'\r'}"
  case "$line" in ''|'#'*) continue ;; esac
  key="${line%%=*}"
  value="${line#*=}"
  case "$key" in
    DB_URL|DB_USERNAME|DB_PASSWORD|DB_DIRECT_URL|REDIS_HOST|REDIS_PORT|REDIS_PASSWORD|REDIS_SSL|JWT_SECRET|PAYMENT_WEBHOOK_SECRET|MAIL_HOST|MAIL_USERNAME|MAIL_PASSWORD)
      export "$key=$value" ;;
    spring.rabbitmq.addresses|SPRING_RABBITMQ_ADDRESSES|RABBIT_ADDRESSES)
      export "RABBIT_ADDRESSES=$value" ;;
  esac
done < "$env_file"

if [ -z "${JWT_SECRET:-}" ]; then JWT_SECRET="$(openssl rand -base64 48)"; export JWT_SECRET; fi
if [ -z "${PAYMENT_WEBHOOK_SECRET:-}" ]; then PAYMENT_WEBHOOK_SECRET="$(openssl rand -hex 32)"; export PAYMENT_WEBHOOK_SECRET; fi
export REDIS_PORT="${REDIS_PORT:-6379}" REDIS_SSL="${REDIS_SSL:-true}"

exec "$script_dir/create-secrets.sh" "$namespace"
