"""Parse Newman / Postman CLI JSON exports into simple API test rows."""

from __future__ import annotations

import json
from pathlib import Path


def endpoint_label(entry: dict) -> str:
    req = entry.get("item", {}).get("request") or entry.get("request") or {}
    method = (req.get("method") or "GET").upper()
    url = req.get("url") or {}
    if isinstance(url, str):
        return f"{method} {url}"
    parts = [str(p) for p in (url.get("path") or []) if p]
    path = "/" + "/".join(parts) if parts else "/"
    return f"{method} {path}"


def execution_rows(run: dict | None) -> list[dict]:
    if not run:
        return []
    rows: list[dict] = []
    for entry in (run.get("run") or {}).get("executions") or []:
        response = entry.get("response") or {}
        assertions = entry.get("assertions") or []
        failed = any(a.get("error") for a in assertions)
        rows.append(
            {
                "endpoint": endpoint_label(entry),
                "code": response.get("code", "—"),
                "responseTimeMs": response.get("responseTime", "—"),
                "passed": not failed,
            }
        )
    return rows


def load_newman_run(path: Path) -> dict | None:
    if not path.is_file():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def aggregate_load_workers(load_dir: Path) -> list[dict]:
    """Merge worker JSON files into one row per endpoint (avg response time)."""
    by_endpoint: dict[str, dict] = {}
    for path in sorted(load_dir.glob("worker-*.json")):
        for row in execution_rows(load_newman_run(path)):
            key = row["endpoint"]
            bucket = by_endpoint.setdefault(
                key,
                {"endpoint": key, "codes": [], "times": [], "passed": True},
            )
            code = row["code"]
            if code != "—":
                bucket["codes"].append(int(code))
            time_ms = row["responseTimeMs"]
            if time_ms != "—":
                bucket["times"].append(float(time_ms))
            bucket["passed"] = bucket["passed"] and row["passed"]

    rows: list[dict] = []
    for bucket in by_endpoint.values():
        codes = bucket["codes"]
        times = bucket["times"]
        rows.append(
            {
                "endpoint": bucket["endpoint"],
                "code": codes[0] if len(set(codes)) == 1 and codes else "—",
                "responseTimeMs": round(sum(times) / len(times)) if times else "—",
                "passed": bucket["passed"],
            }
        )
    return sorted(rows, key=lambda r: r["endpoint"])
