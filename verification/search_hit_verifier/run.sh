#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out=$(mktemp -d)
trap 'python3 -c "import shutil,sys;shutil.rmtree(sys.argv[1])" "$out"' EXIT
mapfile -t stubs < <(find verification/document_io/stubs -name '*.java' -print)
javac --release 11 -d "$out" "${stubs[@]}" verification/search_hit_verifier/stubs/android/os/Handler.java verification/workspace_directory_loader/stubs/android/os/Looper.java apps/MarkdownDesk/src/com/example/markdowndesk/{DocumentIO,SearchHitVerifier}.java verification/search_hit_verifier/SearchHitVerifierCheck.java
java -Xmx96m -cp "$out" com.example.markdowndesk.SearchHitVerifierCheck
for mode in reject supplier blocked terminal runtime fatal; do
 java -Xmx32m -cp "$out" com.example.markdowndesk.SearchHitVerifierCheck "$mode"
done
