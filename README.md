# Markdown Desk

A local-first Markdown editor, reader, and JSON workspace for Android phones and foldables.

**2.5.0 — Unified Workspaces** brings Markdown documents, LLM conversations, and general JSON into one tabbed workspace. Android 15 or newer is required.

## Download and install

Get the signed APK from [GitHub Releases](https://github.com/pemunson90-cmd/Markdown-Desk/releases). Download the `.apk` asset, not the source-code ZIP.

1. Open the APK on your Android device and allow installation from your browser or file manager if Android asks.
2. Install it over your existing Markdown Desk installation. The original signing identity is preserved; do not uninstall to update.
3. Open your documents using Android’s file picker and grant access only to the files or folders you want to use.

The 2.5.0 APK is `MarkdownDesk-2.5.0-phone-v39.apk` (versionCode **39**, package `com.example.markdowndesk`). It is the same signed APK accepted by the owner after phone testing.

## What you can do

- **Write and read Markdown:** Write, Read, Split, and Focus modes; tabs, text controls, search, saving, history, and recovery.
- **Read LLM archives:** coherent conversation views for supported Claude exports, linear message archives, and ChatGPT conversation graphs, with alternate branches retained.
- **Inspect any valid JSON:** navigate objects, arrays, and values without requiring a particular LLM format. Unknown archive schemas remain available through the general JSON Inspector.
- **Annotate your reading:** bookmarks, six highlight colors, notes, backlinks, per-source Markdown indexes, and a master links index.
- **Search across workspaces:** indexed conversations, annotations, and authorized documents and folders, with navigation to the matching passage and return to results.
- **Export and share:** Markdown and PDF transcripts, printing, copying, sharing, and existing DOCX functionality.
- **Keep your layout:** session restoration, source-bound navigation, scalable typography, nine themes, and the existing splash collection, with phone and foldable layouts.

## Large files and privacy

Imports use streaming and disk-backed indexing, with cancellation, restart, and reimport safeguards. Large JSON values are paged rather than loaded whole into memory. Local verification included 700 MB-class inputs under constrained Java heaps; actual speed depends on your device and file provider.

Documents stay local. No mandatory cloud account is required. JSON sources are read-only; annotations are stored separately. Export important documents and notes as ordinary files when you need portable copies.

**Private application-state transfer between separate installations is not included.** Copying a Markdown file does not transfer private history or the entire workspace. Uninstalling or clearing app storage can remove private data.

## Verification and feedback

The release passed the applicable local host regressions, importer/exporter safety checks, and SDK 37 AAPT2/Java/D8 compilation, followed by signature and alignment verification. The owner reported the signed build working and approved publication. This is not a claim of exhaustive testing on every device or Android file provider.

Report bugs through [Issues](https://github.com/pemunson90-cmd/Markdown-Desk/issues), including the app version, Android version, device, steps, and any error text. Remove private content from examples.

## Development

Read [AGENTS.md](AGENTS.md) and the [live execution ledger](docs/handoffs/LIVE_WORK_LEDGER.md) before changing the project. The ledger is authoritative; older roadmap and handoff entries are historical.

- [Build instructions](docs/BUILD.md)
- [Release gates](docs/RELEASE_GATES.md)
- [2.5.0 verification evidence](docs/verification/PHONE_CANDIDATE_20261107.txt)
- [APK identity and checksum](docs/verification/PHONE_CANDIDATE_20261107.json)

Use the maintained local build and verification scripts. Do not run GitHub Actions for ordinary development or release preparation. Signing keys and credentials must never enter the repository.
