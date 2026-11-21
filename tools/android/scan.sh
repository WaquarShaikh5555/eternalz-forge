#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
if [[ ! -x "$ROOT/.venv/bin/semgrep" ]]; then
  echo 'Run tools/android/bootstrap.sh first.' >&2; exit 2
fi
export SEMGREP_SEND_METRICS=off SEMGREP_ENABLE_VERSION_CHECK=0
exec "$ROOT/.venv/bin/semgrep" scan --metrics=off --disable-version-check --oss-only \
  --config "$ROOT/tools/android/rules.yml" --error "$@"
