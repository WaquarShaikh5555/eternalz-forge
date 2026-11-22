"""Expose small, synthetic-fixture previews through GitHub's allowed check API.

Full-resolution PNGs remain in CI artifacts. No real-account screens or credentials
are captured by these tests. Annotations are split below GitHub's 4 KB limit.
"""
import base64
from pathlib import Path
import sys

name = sys.argv[1]
if name not in {"sign-in-light", "workspace-light", "workspace-dark"}:
    raise SystemExit("Unknown test-fixture preview")
image = Path("design-screenshots") / f"{name}.jpg"
if image.exists():
    payload = base64.b64encode(image.read_bytes()).decode()
    if len(payload) > 30000:
        print(f"::warning::Preview {name} exceeds the annotation budget; use the PNG artifact.")
    else:
        for index in range(0, len(payload), 3000):
            print(f"::notice title=design-preview-{name}-{index // 3000}::{payload[index:index + 3000]}")
