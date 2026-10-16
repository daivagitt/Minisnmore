#!/usr/bin/env bash
# Fail if miniandmore-commerce-api Path= predicates drift across gateway YAML profiles.
# Usage: REPO_ROOT=/var/www/uat1/mini_ms_test bash deploy/scripts/verify-gateway-commerce-route-parity.sh
set -euo pipefail

REPO_ROOT="${REPO_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
export REPO_ROOT

python3 << 'PY'
from __future__ import annotations

import re
import sys
from pathlib import Path

root = Path(__import__("os").environ["REPO_ROOT"]) / "services" / "gateway"
canon_file = root / "src/main/resources/config/application.yml"

ROUTE_BLOCK = re.compile(
    r"id: miniandmore-commerce-api\n\s+uri:.*\n\s+predicates:\n\s+- Path=([^\n]+)"
)

def extract(path: Path) -> str | None:
    if not path.is_file():
        return None
    text = path.read_text(encoding="utf-8")
    m = ROUTE_BLOCK.search(text)
    return m.group(1) if m else None

def segments(path_value: str) -> list[str]:
    return [s.strip() for s in path_value.split(",") if s.strip()]

canon = extract(canon_file)
if not canon:
    print(f"FAIL missing commerce Path in {canon_file}", file=sys.stderr)
    sys.exit(1)

canon_segments = segments(canon)
required_paths = [
    root / "src/main/resources/config/application.yml",
    root / "src/main/resources/config/application-dev.yml",
    root / "src/main/resources/config/application-uat.yml",
    root / "src/main/resources/config/application-uat-isolated.yml",
    root / "src/main/resources/config/application-prod.yml",
    root / "src/main/resources/config/application-prod-isolated.yml",
    root / "src/main/docker/central-server-config/application.yml",
]
catalog_example = root / "src/main/resources/config/application-catalog-split.yml.example"

errors: list[str] = []

for path in required_paths:
    value = extract(path)
    if value is None:
        errors.append(f"{path}: no miniandmore-commerce-api Path=")
    elif value != canon:
        errors.append(f"{path}: Path= differs from {canon_file.name}")

example_value = extract(catalog_example)
if example_value is None:
    errors.append(f"{catalog_example}: no miniandmore-commerce-api Path=")
else:
    example_set = set(segments(example_value))
    missing = [s for s in canon_segments if s not in example_set]
    if missing:
        errors.append(
            f"{catalog_example.name}: missing canonical segments: {', '.join(missing)}"
        )

if errors:
    print("Gateway commerce route parity check FAILED:", file=sys.stderr)
    for line in errors:
        print(f"  - {line}", file=sys.stderr)
    sys.exit(1)

print("OK  gateway commerce Path= parity ({0} profiles + catalog-split superset)".format(len(required_paths)))
PY
