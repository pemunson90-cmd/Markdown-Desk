#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/save-protection"
mkdir -p "$OUT"
PYTHONDONTWRITEBYTECODE=1 python3 "$ROOT/verification/save_protection/model_contract.py" "$@" 2>&1 | tee "$OUT/model-results.txt"
