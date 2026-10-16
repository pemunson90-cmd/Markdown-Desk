#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
out=$(mktemp -d)
trap 'python3 -c "import shutil,sys;shutil.rmtree(sys.argv[1])" "$out"' EXIT
javac --release 11 -d "$out" verification/workspace_search_loader/stubs/android/content/Context.java verification/workspace_search_loader/stubs/android/os/Handler.java verification/workspace_directory_loader/stubs/android/os/{Looper,CancellationSignal}.java verification/workspace_tree_reader/stubs/android/{net/Uri,provider/DocumentsContract,database/Cursor,os/OperationCanceledException}.java verification/workspace_search/stubs/android/content/ContentResolver.java apps/MarkdownDesk/src/com/example/markdowndesk/{WorkspaceSearchLoader,WorkspaceLiteralSearch,WorkspaceTreeReader,WorkspaceDirectoryLoader}.java verification/workspace_search/WorkspaceSearchCheck.java verification/workspace_search_loader/WorkspaceSearchLoaderCheck.java
java -Xmx32m -cp "$out" com.example.markdowndesk.WorkspaceSearchLoaderCheck
for mode in reject cursor cursor-cancel cursor-reject-delivery stream stream-cancel stream-reject-delivery cursor-consumer-throws fatal-stream fatal-cursor fatal-cursor-close-error fatal-closed; do
 java -Xmx32m -cp "$out" com.example.markdowndesk.WorkspaceSearchLoaderCheck "$mode"
done
