# Explicit workspace favorites metadata

`bash verification/workspace_favorites/run.sh` with existing JDK17 on PATH compiles
actual WorkspaceFavoritesStore against existing Android JSON and minimal URI/prefs
boundaries, then runs107 assertions under32MiB heap. Initial RED: production source
absent. Final107 PASS; actual SDK37 API compile and diff check PASS. No other suite,
APK/build, Activity/provider/device/Fold or real UI evidence is claimed.

API: WorkspaceFavoritesStore(Context), read(), pin(Uri,label,Kind), unpin(Uri).
Favorite fields are immutable uri,label,kind; returned list immutable. Kind is
DOCUMENT or TREE. New explicit pin appends; same exact URI/same kind replaces label
in place; kind conflict refuses. Identity is exact URI string, never title/content/
path/decoded IDs. DOCUMENT tree-derived child is allowed, without grant lookup.
TREE validates tree syntax; initial selected-root provenance belongs to future UI.
Metadata grants no access and never takes/releases grants or contacts a provider.

Hard caps64 records/URI4096UTF16/label256/serialized32768/depth8. Empty/null or invalid
surrogate label refuses. Strict fixed-schema preflight bounds recursion/containers/
arrays/object fields and decoded duplicate keys before org.json; no last-wins keys,
comments/singlequotes/bare syntax, floats, unknown/missing fields/kinds/versions or
trailing input. Duplicate exact URI records refuse. Malformed stored bytes are left
exactly intact and all methods refuse with no commit; no reset/salvage/eviction.

One private static lock covers read/modify/checked commit across store instances.
Commit false/throw may already change Android in-memory preferences: failure never
claims rollback or durable success. Unpin commits even when already absent, so a
retry can persist removal after an earlier false/throwing commit changed memory.
This chooses the contract's permitted idempotent checked-write behavior; no other
stores are touched. Fake Context/key assertions prohibit unrelated preferences API.
No provider/resolver API exists in this host Context boundary.

Coverage: absent/reopen/mixed kinds/stable order/repin/conflict/unpin/immutability;
64/65 cap and existing update/remove at capacity; exact encoded URI distinctions;
child DOCUMENT;4096/4097URI,256/257label/Unicode/invalid scheme/tree; escaped labels;
32768 exact/over raw and escaping-driven write cap; malformed/trailing/version/type/
unknown/duplicate/escaped-duplicate fields, duplicate URI/conflicting kind and65 raw
records;24001-unit12000-array attack/depth9; preservation across all three APIs;
commit false/throw with changed memory and explicit removal retry; independent store
instances concurrent pins without lost updates. No recents/root/history data paths.

Actual SDK compilation:

```
/workspace/.command-library-env/jdk-17.0.16+8/bin/javac --release 11 \
 -cp /workspace/.command-library-env/android-sdk/platforms/android-37.0/android.jar \
 -d /tmp/workspace-favorites-sdk-compile \
 apps/MarkdownDesk/src/com/example/markdowndesk/WorkspaceFavoritesStore.java
```

Frozen source blob7fd3539a4e80ebe78aaabd3e028569fb737e6b0c. No commits. Real favorites
pin/list/normal-open/common-tree-browser UI is a later root-serial feature packet;
store readiness alone is not user-visible M3 completion.
