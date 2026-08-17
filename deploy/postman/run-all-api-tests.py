#!/usr/bin/env python3
"""Run full UAT API suite (all ~170 OpenAPI ops). Delegates to run-lifecycle-api-tests.py."""

import importlib.util
from pathlib import Path

_path = Path(__file__).resolve().parent / "run-lifecycle-api-tests.py"
_spec = importlib.util.spec_from_file_location("lifecycle_tests", _path)
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)

if __name__ == "__main__":
    _mod.main()
