#!/usr/bin/env bash
# Manual mixed diagnostic: raw provider hazards and product checks have distinct contracts.
set -uo pipefail
cd "$(dirname "$0")/.."
failed=0
# Atomic URI preservation is a separate diagnostic; generic providers remain destructive.
bash verification/document_io/run.sh --safety || failed=1
# Production recoverability must also pass; neither result hides the other.
bash verification/protection_store/run.sh || failed=1
bash verification/save_coordinator/run.sh || failed=1
bash verification/save_coordinator/run_memory.sh || failed=1
bash verification/save_lifecycle/run.sh --safety || failed=1
# Compile renderer fixtures but do not turn the nested-list assertion into an expected pass.
bash verification/renderer_golden/run.sh || failed=1
java -cp verification/build/renderer-golden com.example.markdowndesk.NestedListKnownFailure || failed=1
bash verification/contrast/run.sh --gate || failed=1
if [[ "$failed" != 0 ]]; then
 echo 'FAIL mixed safety diagnostic: inspect primitive hazards and product failures separately'
 echo 'Required production recoverability is checked by verification/run_host_checks.sh; no device/release acceptance implied'
fi
exit "$failed"
