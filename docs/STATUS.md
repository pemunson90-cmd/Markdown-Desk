# Current project status

## M0.1 / issue #1 — started, blocked (2026-10-14)

- Repository: `pemunson90-cmd/Markdown-Desk` (public).
- Audit base commit: `60f3acbdfca399e99f5f1754a4d66b3fddd1f4b4`.
- Working branch: `codex/issue-1-baseline-audit`.
- Read `AGENTS.md`, kickoff, roadmap, import protocol and release gates.
- Repository contains nine tracked planning/configuration files; no app source,
  manifest, build script or host-test runner. Source import is **not complete**.
- Exact `MarkdownDesk-2.2.5-source.zip` is absent from attached workspace files.
  This session has no callable ChatGPT Library retrieval tool and no attached
  artifact reference. Requested the exact archive; stopped import/build work
  under the kickoff's explicit missing-source stop condition.
- Reused the configured Android environment for the audit. JDK 17.0.16+8 works
  through `JAVA_HOME/bin`; existing SDK contains platform 37.0 and build-tools
  36.0.0, not the required 36.1.0. No toolchain replacements installed.
- No source reconstruction, application changes, signing key generation,
  version bump, baseline tag, CI setup or release artifact publication.
- Existing functionality (including Focus/View) remains untouched but **not
  verified**. No app exists in this checkout to exercise.

See [M0.1 audit](M0_1_AUDIT.md) for executed checks and missing inputs.

## Next actions after inputs arrive

1. Audit exact archive integrity, paths, checksums and public asset clearance.
2. Import approved source bytes with relative layout intact; record inventory
   and SHA-256 provenance before any build/test scaffolding changes.
3. Reuse JDK 17 and the existing SDK; resolve required 36.1.0 tools, then establish
   clean unsigned builds and run supplied host checks. No release signer needed.
4. Verify manifest/payload and preservation of existing modes, themes and splash
   assets; distinguish host, emulator and physical Fold evidence.
5. Complete issue #1 gates before tagging `v2.2.5-baseline` or starting issue #2.

Older versions and SDK archives are a secondary provenance request. None were
retrieved from Library or uploaded; historical/recovery archives require privacy
and redistribution review before publication to this public repository.
