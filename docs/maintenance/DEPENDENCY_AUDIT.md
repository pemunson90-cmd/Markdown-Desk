# Dependency and expansion audit — 2026-11-07

Scope: read-only inspection of the shipped build path, local verification inputs, and agent entry documentation. No installs, app builds, dependency upgrades, advisory-database queries, or vulnerability scans were performed. These findings are not a security clearance or a claim that every future expansion is unblocked.

## Concrete findings

| Finding | Evidence | Disposition |
| --- | --- | --- |
| Standalone packaging and source compilation selected different build-tools defaults. | `apps/MarkdownDesk/build.sh` defaulted to36.0.0; `compile_native_sources_only.sh` and `first_signed_phone_candidate.sh` defaulted to36.1.0. The recovered SDK contains36.1.0. | BUILD sets 36.1.0 explicitly and build.sh now uses the same 36.1.0 default as source verification. This is a build reliability fix, not a toolchain upgrade. |
| First-candidate automation is tied to the completed release. | `first_signed_phone_candidate.sh` requires integration/unified-workspaces-20261106, accepts code>38, and names output2.5.0. Published versionCode is39. | Do not use it unchanged for the next release: a new update must exceed39 and target its actual authorized source. This is a future-release workflow blocker, not a defect in the released APK. |
| Old entry documentation could restart completed work or trigger irrelevant builds. | CODEX_START requested one-batch stopping and automatic M0–M7 continuation; BUILD showed2.2.5 unsigned build/rebuild recipes and said signing was not executed. AGENTS retained obsolete campaign authorization. | Replaced current entry instructions with the actual accepted2.5.0 baseline, live-ledger routing, and scoped local verification. Historical provenance remains in Git and existing archives. |
| Optional browser probes have an external host requirement. | `verification/reader_layout.cjs` requires Playwright; no package manifest/lockfile was found in the inspected repository. | Treat as optional, separately provisioned diagnostics. Do not silently install latest tooling or make it a release gate. Pin it if future authorized work makes it maintained verification. |

## Dependency boundary

The maintained APK build compiles project/shared Java against Android's platform JAR and packages application resources/assets and DEX. No Gradle/Maven application dependency graph or bundled external JAR input was found in this build path. Therefore a broad dependency-upgrade campaign has no demonstrated purpose here.

The old Vaadin Android JSON JAR is a **host-test implementation**, pinned and SHA-256 verified by setup and applicable runners. It is not packaged. Its age alone is not evidence of a shipped-app vulnerability; Android's runtime implementation remains a distinct boundary.

The tested environment uses JDK17, SDK37, and build-tools36.1.0. Preserve those known working inputs unless a concrete requirement or defect justifies change. The original signing identity is available only through private credentials; it must never be regenerated or committed.

## Expansion constraints worth carrying forward

- A new LLM export schema can remain readable in general JSON mode; coherent conversation rendering needs an explicit schema adapter and representative fixture. “Any JSON” does not imply every arbitrary schema can be inferred as a conversation.
- Physical selection handles, file-provider behavior, print UI, and Fold layouts require device checks for affected changes. Repeating host suites cannot resolve those questions.
- Cross-installation private application-state export/import remains owner-deferred. Do not accidentally activate incomplete portable-state infrastructure while extending ordinary exports.

No production-code defect was claimed or tested in this documentation/dependency slice. The separate focused bug review owns runtime findings.
