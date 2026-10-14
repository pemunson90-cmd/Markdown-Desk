# Markdown Desk 2.2.5 — signed release source

Package: `com.example.markdowndesk` · versionName `2.2.5` · versionCode `18` · minSdk 26 · targetSdk 35.

## Change from 2.2.4

The Appearance dialog used to retain the previous theme's background and text styling after changing theme, while the main activity immediately changed. This created illegible foreground/background combinations until the dialog was closed and reopened.

- Theme selection now refreshes the *existing* Appearance dialog in place: title, dialog background, explanatory text, theme row labels, selected indicator, all settings buttons, text-size labels, and Done action.
- A custom, palette-controlled title replaces the platform-provided title to eliminate mismatched title colors.
- Controls use the new palette's readable text on a matching background. Every palette meets 4.5:1+ text contrast for these dialog foreground/background pairs in the source color values.
- The scroll position remains unchanged, including on a Fold/short screen. The existing scrollable layout is retained.
- Line numbers, typewriter and launch-splash toggle indicators also update when tapped.
- Transient Appearance controls are removed from the main toolbar's global recoloring registry, preventing the activity from retaining dismissed dialog buttons.

### Preserved

- All 9 palettes and all 25 splash illustrations (every packaged splash image byte-identical to the verified 2.2.4 APK).
- Main editor, writing/reading modes, typography, source storage, document persistence, and all other unchanged 2.2.4 code.
- The same canonical release signer, required for an in-place upgrade. **Do not uninstall the existing app.**

## Build and verification

- Built and signed in chat with the supplied original release PKCS#12 identity (private key excluded from source archive), Android SDK platform 36 + build-tools 36.1.0, targetSdk 35, JDK 17+/Java 11 release output.
- Android compilation, Android resource linking, D8 generation, zip alignment, v2/v3 APK signing and signature verification: PASS.
- Signed APK: `MarkdownDesk-2.2.5-release.apk`, SHA-256: `ec5e790343b45a56f2580f3f5ec97eae3fb3096e10e012e58eb76bc89e683601`.
- Release certificate SHA-256: `a163cea419108eb595f3dcb01de16feca878368d4ffc6efc58eb9a8358b6be01`; matches verified 2.2.4 APK and documented 2.2.3 identity.
- AAPT2 package inspection confirms version 2.2.5/code 18, minSDK 26, targetSDK 35.
- Existing Java 11 syntax parsing, existing 9 × 3 renderer tests, and scrolling/utility-layout guards: PASS.
- New live-Appearance source guards, exact theme coverage, and 4.5:1+ foreground/background color checks: PASS.
- No physical Fold install, runtime UI or emulator acceptance was performed in this session. Pat should confirm that switching Paper, Bubblegum, Forest, Ocean and Arcade (and remaining themes) updates the dialog visibly without reopening it, in both folded and unfolded states.

## Building from source

Use `apps/MarkdownDesk/build.sh MarkdownDesk-2.2.5-release` with `ANDROID_SDK_ROOT`, `ANDROID_PLATFORM`, `ANDROID_BUILD_TOOLS`, and `MARKDOWN_DESK_KEYSTORE`, `MARKDOWN_DESK_KEY_ALIAS`, `MARKDOWN_DESK_STORE_PASSWORD`, `MARKDOWN_DESK_KEY_PASSWORD` set to the *existing canonical release* key. The private signing archive is **not** in this package and should never be committed or shared. See the script for details.

The included `appearance-scroll.patch`, `apps/MarkdownDesk/CHECKPOINT.md` and `verification/SDK35_RESULTS.txt` document the previous 2.2.4 source phase and are retained for historical provenance, **not** the 2.2.5 release's validation. Current build results are in `verification/RELEASE225_RESULTS.txt`.
