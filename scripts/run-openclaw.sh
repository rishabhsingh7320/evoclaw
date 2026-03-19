#!/usr/bin/env bash
# Run an OpenClaw backend with API keys from repo root .env.
# Usage: ./scripts/run-openclaw.sh <backend-dir>
# Example: ./scripts/run-openclaw.sh openclaw4/backend

set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND="${1:?Usage: run-openclaw.sh <backend-dir>}"

if [[ -f "$ROOT/.env" ]]; then
  set -a
  source "$ROOT/.env"
  set +a
fi

cd "$ROOT/$BACKEND"
exec mvn spring-boot:run -q
