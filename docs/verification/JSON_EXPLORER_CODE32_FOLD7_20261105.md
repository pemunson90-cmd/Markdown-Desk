# JSON Explorer: Fold7 observed issues and code32 repair

**Source:** Owner's real Samsung Galaxy Fold7 screenshots during 2.3.3 (code31) testing, November 5, 2026.
**New implementation:** 2.3.4-json-foldfix1, versionCode 32.
**Scope:** In-place, same package/signing identity; isolated JSON Explorer changes.

## Direct observation from owner
1. JSON Explorer top navigation ("Back", "Object Explorer", "Open") overlaps Android status-bar icons. The first list is readable, and selection `$[0]` works.
2. "Read as Claude conversation" opens, but preview text is light on a white system dialog, almost invisible; action buttons also sit uncomfortably near the bottom edge.
3. The owner's system picker offers an unexpectedly narrow source list under "Recent": only Markdown Desk, which appears to expose Trash. Other apps offer Samsung My Files; internal storage remains accessible in Markdown Desk but takes unnecessary navigation.

## Changes
- Explicit Android 30+ window-insets ownership for the JSON Explorer root. Pad by status-bar/cutout top and by max(navigation-bar, keyboard) bottom; set readable light/dark system icon styles. Old API devices preserve default fitted content.
- Modal uses matching document palette, custom readable title, safe accent/button contrast, bounded window size and shortened **Save .md** action.
- A two-option JSON picker offers persistent **Browse device files** (ACTION_OPEN_DOCUMENT) and read-only **Browse with another app** (ACTION_GET_CONTENT). The second route may offer Samsung My Files and never attempts to persist access or write back to JSON. It is a choice, not a claim that every Samsung setup will expose My Files.
- Files > Explore JSON now starts the explorer and presents that choice rather than bypassing it through the old DocumentsUI-only launch.
- Restore selected JSON document URI and navigation path after activity recreation (fold/unfold/rotation).
- VersionCode 32 to permit updating over the owner-installed code31.

## Acceptance conditions
- Fold7 compact and unfolded: no status-bar or navigation-bar overlap; header and buttons can be tapped.
- Transcript readable with owner's dark palette, actions fully visible and not behind Android gesture/navigation area.
- Both picker choices work as offered; note the available providers and whether My Files appears under the alternate chooser.
- Open existing Claude export, browse children, preview conversation, copy and Save .md to a NEW file. The original JSON's bytes must remain unchanged.
- Existing Markdown editing, Files, tabs, themes, bookmarks and previous documents retained; no uninstall/clear data.
- Build and focused host regressions are separate from actual phone acceptance.

## Limits
- The alternative app-provider chooser has only transient read access. It is for JSON read-only viewing, not the normal persistent Markdown editor flow.
- No full export indexing, cross-page search or export beyond the existing 200-message preview is claimed.
- Do not put owner's private Claude export or private signing material in the public repo.

## Code32 build and private signing evidence (same day)
- [Android unsigned compilation and focused host regression PASS, workflow run 42135663168](https://github.com/pemunson90-cmd/Markdown-Desk/actions/runs/42135663168), candidate source commit `3ec1bc9d0b67b9f9a2f057d4b689d6c20f46dcfb`.
- Artifact `markdown-desk-json-code32-unsigned` (ID 13466551066) retrieved from the exact workflow; privately signed on the operator's machine with the original owner Library `MarkdownDesk-release.p12`. No secret transferred to GitHub.
- Signature verification: **v2 PASS**, **v3 PASS**; ZIP alignment **PASS**.
- Certificate SHA-256: `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`.
- Signed APK: `MarkdownDesk-2.3.4-json-foldfix-signed.apk`, SHA-256 `1bcf0639f20bc0b8ccdbe01ce0db8d7d7e1f3dcd16d2f82ea86397530928fc9a`, package `com.example.markdowndesk`, versionCode **32**.
- The downloadable signed APK is a **Fold7 candidate**; physical code32 owner acceptance, including appearance of My Files through the alternate chooser, has **not** yet been recorded.
