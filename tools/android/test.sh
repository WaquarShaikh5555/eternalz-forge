#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
export SEMGREP_SEND_METRICS=off SEMGREP_ENABLE_VERSION_CHECK=0
.venv/bin/python -m unittest discover -s tests/tooling -p 'test_*.py' -v
for fixture in tests/tooling/rules.kt tests/tooling/manifest/AndroidManifest.xml; do
  .venv/bin/semgrep scan --test --config tools/android/rules.yml "$fixture" \
    --metrics=off --disable-version-check --oss-only
done
.venv/bin/python -m pip check
