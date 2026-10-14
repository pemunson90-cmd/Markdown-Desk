#!/usr/bin/env bash
# Desired-safety assertions: these remain red until their real defects are fixed.
set -uo pipefail
cd "$(dirname "$0")/.."
failed=0
bash verification/document_io/run.sh --safety || failed=1
bash verification/save_lifecycle/run.sh --safety || failed=1
# Compile renderer fixtures but do not turn the nested-list assertion into an expected pass.
bash verification/renderer_golden/run.sh || failed=1
java -cp verification/build/renderer-golden com.example.markdowndesk.NestedListKnownFailure || failed=1
bash verification/contrast/run.sh --gate || failed=1
if [[ "$failed" != 0 ]]; then
 echo 'FAIL safety/spec gate: do not claim M0 complete or production readiness'
fi
exit "$failed"
