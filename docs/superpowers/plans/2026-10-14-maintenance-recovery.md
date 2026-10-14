# Maintenance recovery projection implementation plan

> **For agentic workers:** Use superpowers:subagent-driven-development or superpowers:executing-plans; execute each bounded task with its checks before the next.

**Goal:** Validate a committed prune intent after interrupted group removal and expose only verified survivors, without deleting or publishing any file.

**Architecture:** Recognize fixed typed intent files within the existing control allowance. A dedicated maintenance opener validates stable authority and an explicitly passed candidate projection before installing a filtering capability. Physical accounting remains separate from the canonical survivor proof.

**Tech stack:** Existing Java11 source, JDK17 host tooling, Android minSdk26, native v1 envelopes, no new dependency or signing input.

**Spec:** `docs/decisions/ADR-002-retention-journal.md`, reviewed inert `HistoryPruneIntent`, and its verification README. ADR-003's separate-root/accounting proposal is historical and superseded.

## Global constraints

- Existing package2.2.5/code18/min26/target35, source provenance, modes/themes/assets, native v1 bytes and protected-save semantics remain intact.
- Exactly one shared1MiB/256-entry administrative allowance, including enrollment marker/stages, policy and intent. Registration/policy cap16KiB; complete intent cap64KiB/256members.
- Fixed `history_control/prune.intent` and `.stage`; no arbitrary path, stage promotion, unlinks, pins, executor or Activity activation in this slice.
- Unknown/incomplete/changed authority, members or survivors fail closed. Only `NoSuchFileException` proves absence.
- Parent monitor and existing mutation lease precede callback-capable work; a candidate projection never becomes provisional global read authority.
- Physical Android, sudden power loss, SAF/Fold/TalkBack, signing and upgrade are separate NOT RUN gates.

## Review focus

- A partial group cannot become a fabricated outstanding completion reservation.
- Same-byte replacement or changed intent cannot be hidden by a cached membership set.
- Control/physical usage changes from partial removal cannot invalidate a correctly defined survivor fingerprint.
- Reentrant reads during validation cannot observe an unvalidated projection; nested mutation remains rejected.
- Unknown files, retained intent stages and missing references outside the closure cannot be explained away.

### Task1: Bounded typed intent discovery

**Files:** `HistoryControlStore.java`, narrow `HistoryCapacity.java` guards; `MaintenanceRecoveryCheck.java`.

**Interfaces:** `Inspection` exposes immutable committed intent/stage evidence separately from registration/policy chain. Ordinary reconciliation rejects maintenance presence before any stage promotion.

- [x] Native registered fixture observes RED: valid committed intent currently destroys independently valid chain evidence.
- [x] Recognize only the fixed intent names, decode with64KiB limit, retain16KiB authority limits, charge both names under shared bounds.
- [x] Keep valid chain evidence available while maintenance blocks ordinary authority; malformed/conflicting stages remain retained and blocked.
- [x] Check ordinary registration/open/reconcile/policy/admission cannot bypass intent or promote its stage.
- [x] Run discovery tests and existing control/authority groups; commit scoped coherent source.

### Task2: Explicit candidate projection and unaffected proof

**Files:** new `HistoryTombstoneView.java`, `HistoryMaintenanceRecovery.java`; narrow `HistoryStorage`, `ProtectionStore`, `HistoryCatalog`, `HistoryMetadataIO`, `HistoryIdentity`, `HistoryIndex`, `HistoryGraph` reader hooks.

**Interfaces:** package-private `HistoryMaintenanceRecovery.unaffectedFingerprint(Graph, intentClosure)` and `open(store,catalog,identity,index,durability): RecoveryView`. Explicit internal candidate-view overloads feed validated sources/metadata enumeration; ordinary public readers require installed validated binding.

- [x] Write native intact/partial/all-absent closure fixtures and compute pre-intent survivor proof using the same canonical helper.
- [x] Add `history-maintenance-unaffected-v1`: registered role names, sorted survivor envelopes, survivor semantic edges/identities and surviving reservations. Exclude whole closure regardless physical presence, profile/constructor hints, all maintenance/control transient usage, and physical bytes/slots of removed members. Stable authority binds separately.
- [x] Validate every still-present member by nofollow exact length/hash; validate existing removed-reference envelopes without resolving intentionally absent members. Preserve all outside-closure semantic validation.
- [x] Dedicated opener validates roles/bootstrap/enrollment/policy/intent, then read-only sync/readback and fresh validation before installation. It does not acknowledge ordinary writer readiness.
- [x] Filter only exact validated closure from logical enumeration/reservations; direct tombstoned lookup fails. Report raw physical charges separately.
- [x] Treat installed view as filtering capability, not validity cache: every read rechecks current intent/control/bundle and exact present member identity/bytes before hiding. Replaced/corrupt members fail.
- [x] Run projection, authority-drift, callback/isolation and malformed-member tests; inspect all ordinary reader entry points; commit.

### Task3: Fresh-process and failure evidence

**Files:** maintenance fixture/runner/README, fresh-JVM driver and probe.

- [x] Reopen intact, synthetic partial and all-absent closures twice in fresh JVMs; assert exact survivors, no phantom reservation, no extra mutation and physical accounting.
- [x] Reject changed member/anchor/identity/unaffected record, outside missing edge, unknown/symlink/nonregular/I/O failure, changed marker/policy/intent, every retained stage, conflicting intent and full/overflow allowance.
- [x] Cover valid intent larger than16KiB within64KiB and oversized input rejection without allocation from untrusted length; retain constrained-heap native fixtures.
- [x] Independent critical persistence review checks final source SHA and all read/filter boundaries; resolve actual findings.
- [x] Integrator ran the coherent full host/unsigned gate at `98577d5`; production-path runner registered and status reconciled.
- [ ] Complete required hosted verification before merge. No automatic execution or device acceptance follows.
