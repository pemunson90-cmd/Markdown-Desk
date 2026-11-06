# Archive reader follow-up — local integration QA

**Date:** November 6, 2026  
**Base:** `release/2.4.2-claude-700mb-recovery-20261106` at `66615a4c4a063e26fcf018f4a05da36615a6363b` (code37).  
**Status:** Source changes locally compiled and tested; not a new device-accepted release.  
**Infrastructure:** Android SDK 36 platform and build-tools 36.1 recovered from owner's Library; Java 21 compiler with `--release 11`, D8 `--min-api 26`. No GitHub Actions.

## Problems corrected

1. **Stale Cancel:** the UI could call `cancel()` after a completed/failed/idle import and leave the coordinator permanently marked *cancelling*. The new synchronized guard rejects idle/terminal states and duplicate cancellation requests.
2. **Progress during huge messages:** byte and storage progress previously updated only after completed message/periodic message notifications, leaving a very large message apparently stalled. Counting input now throttles progress and free-space checks while bytes are read, even inside an exceptionally long message.
3. **Find jump:** search hits now open the matched message and its FTS text segment, with WebView highlighting. The selected segment and the immediately preceding overlap segment remain visible even at 20-part boundaries; tested for 2,000 matches.
4. **Bookmarks:** when 12 messages are visible, Mark asks which message to bookmark instead of silently saving the first one.
5. **Saved source links:** missing message IDs fail explicitly instead of opening message zero. Search and note jumps target the exact message.
6. **Reading Focus:** restores the expected full-screen reader by hiding archive header and navigation chrome as well as system status bar; Android Back exits Focus first. It does **not** reintroduce the earlier crashing custom window-insets handler.
7. **Selected passage:** selection must remain inside one message body; user note metadata is excluded from captured text. This doesn't yet fully map rendered Markdown character offsets to raw source offsets, so ambiguous links must remain fail-closed.
8. **Reading position:** one per-chat scroll position is recorded when leaving/stopping and restored after an activity recreation or returning to the chat, alongside the existing window index. This is an improvement, not a finished semantic anchor/reflow guarantee.
9. **Markdown sidecar filename collisions:** long conversation IDs sharing the same truncated prefix now use a stable SHA-256 suffix. Previously generated filenames for IDs at most 64 sanitized characters are unchanged for compatibility.

## Tests run locally — accurate claims

- **PASS**: Java `--release 11` compile of all archive sources against SDK36 android.jar plus *signature-only stubs* for unchanged Markdown reader classes. This is not an entire application source build.
- **PASS**: D8 build `--min-api 26`; resulting dex retains all 136 original archive class descriptors and adds two required compiler-generated helper classes; no descriptor overlaps with the base app's editor dex.
- **PASS**: `ArchiveCoreTest`: >305 message / >120k character conversation, nested text, malformed JSON rejection, annotation journal changes and corrupted tail, per-chat/master Markdown.
- **PASS**: `test_generation_sql.py`: v1-v2 migration, atomic generation swap, READY-only search, bounded cleanup, FTS4 overlap.
- **PASS**: `CoordinatorCancelGuardCheck`: idle, completed, and failed states do not transition to cancelling. This is an injected-object guard test, not Android UI execution.
- **PASS**: `LongIdIndexCheck`: deterministic, distinct and safe sidecar filenames for long IDs.
- **PASS**: `test_exact_hit_window.py`: FTS hit and previous overlap segment both present for 2,000 tested part ordinals.
- **NOT RUN**: full signed APK on Fold7, real Android ContentResolver/storage permission conditions, full Markdown editor regression, background-process kill or WebView integration. The prior 700 MB synthetic import results belong to code37; this follow-up does **not** claim a fresh 700 MB benchmark.

## Known product gaps still open

- A message window still displays up to 12 messages; long messages use 20-part continuation. Full seamless scroll/virtualization not implemented.
- Inline visual highlight rendering, typed cross-chat link pickers, full local notes/search indexing and complete Fold7 two-pane layout are not yet complete.
- Persisted reading position is pixel-based, not a stable paragraph anchor across reflow/reexport.
- Main app signing key not committed or used in hosted tests. The existing code37 test APK remains available until another candidate passes appropriate gates.
- Sidecar publication on unusual SAF providers, external edits to generated files, and failure recovery still need more tests.

## Continuity

Implement these on a **new descendant branch** from code37; do not overwrite the original tested code37, source tree, or existing stable JSON reader. Release/versionCode must advance above 37 before an in-place upgrade. No auto-merge with stale main.