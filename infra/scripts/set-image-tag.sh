#!/usr/bin/env bash
# Sets image.tag in a Helm values file, touching only that line.
# Usage: infra/scripts/set-image-tag.sh infra/helm/values-dev.yaml <40-char git sha>
set -euo pipefail

file="${1:?values file}"
tag="${2:?image tag (git sha)}"

if [[ ! "$tag" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Image tag must be a full 40-character commit SHA, got: $tag" >&2
  exit 1
fi

# The values files keep exactly one line of the form '  tag: "<sha>"' inside the image: block
count=$(grep -cE '^  tag: ' "$file" || true)
if [ "$count" != "1" ]; then
  echo "Expected exactly one '  tag:' line in $file, found $count" >&2
  exit 1
fi

sed -i -E "s|^  tag: .*|  tag: \"${tag}\"|" "$file"
grep -nE '^  tag: ' "$file"
