#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/verification/build/save-protection"
mkdir -p "$OUT/probe-tmp"
TMPDIR="$OUT/probe-tmp" PYTHONDONTWRITEBYTECODE=1 python3 "$ROOT/verification/save_protection/durable_adapter_probe.py" "$@" 2>&1 | tee "$OUT/filesystem-probe-results.txt"
