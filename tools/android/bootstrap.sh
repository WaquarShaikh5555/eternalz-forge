#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
python3 -m venv .venv
.venv/bin/python -m pip install --only-binary=:all: --no-binary=peewee -r tools/android/requirements.lock
