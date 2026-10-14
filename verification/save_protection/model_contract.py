#!/usr/bin/env python3
"""Executable ADR-001 acceptance sketch. MODEL ONLY: imports no production code."""
from dataclasses import dataclass
import hashlib
import unittest

MAX = 8 * 1024 * 1024


def digest(data):
    return hashlib.sha256(data).hexdigest()


class Fault(Exception):
    pass


@dataclass(frozen=True)
class Request:
    document: str
    revision: int
    attempted: bytes
    baseline: str | None
    consent_hash: str | None = None


class Provider:
    """Destructive provider simulation; reads/writes are observable boundaries."""
    def __init__(self, source=b"original\r\n"):
        self.source = source
        self.reads = self.writes = 0
        self.failure = None
        self.change_on_read = {}
        self.change_before_write = None

    def read(self):
        self.reads += 1
        if self.reads in self.change_on_read:
            self.source = self.change_on_read[self.reads]
        if self.failure == "source-read" or (self.failure == "read-back" and self.writes):
            raise Fault("read revoked")
        return self.source

    def write(self, data):
        self.writes += 1
        if self.change_before_write is not None:
            self.source = self.change_before_write  # Unavoidable final-check race.
        self.source = b""  # Destructive stream open.
        if self.failure in ("zero", "partial"):
            self.source = data[:2] if self.failure == "partial" else b""
            raise Fault(self.failure)
        self.source = b"altered" if self.failure == "mismatch" else data
        if self.failure in ("flush", "close"):
            raise Fault(self.failure)


class Model:
    """In-memory durability/state machine, NOT filesystem persistence or M1 code.

    durable models entries surviving process restart only after directory sync.
    Quota costs are illustrative full-record+staging costs, not a disk format.
    """
    STEPS = ("write", "flush", "file-sync", "rename", "directory-sync", "validate")

    def __init__(self, provider=None, quota=64 * 1024 * 1024):
        self.provider = provider or Provider()
        self.quota = quota
        self.durable = {}
        self.events = []
        self.fault = None
        self.corrupt_role = None
        self.counter = 0
        self.published = None

    def step(self, event):
        self.events.append(event)
        if self.fault == event:
            raise Fault(event)

    @staticmethod
    def cost(record):
        return len(record.get("bytes", b"")) + 128

    def room(self, needed):
        if sum(self.cost(r) for r in self.durable.values()) + needed > self.quota:
            raise Fault("quota exhausted; protected entries retained")

    def persist(self, key, record):
        assert key not in self.durable, "immutable record overwritten"
        self.room(2 * self.cost(record))  # Staging plus committed record.
        for step in self.STEPS:
            self.step(key.split(":")[0] + "." + step)
            if step == "directory-sync":
                self.durable[key] = dict(record)
                if self.corrupt_role is not None and self.corrupt_role == record.get("role"):
                    self.durable[key]["bytes"] += b"corrupt"
        self.validate(key)

    def validate(self, key):
        record = self.durable.get(key)
        if record is None:
            raise Fault("missing committed record")
        if "bytes" in record:
            if len(record["bytes"]) != record["length"] or digest(record["bytes"]) != record["hash"]:
                raise Fault("corrupt envelope")
        if record.get("kind") == "prepared":
            for role in ("attempt", "original"):
                envelope = self.validate(record[role])
                if (envelope["hash"] != record[role + "_hash"] or
                        envelope["document"] != record["document"] or
                        envelope["revision"] != record["revision"]):
                    raise Fault("prepared reference identity/hash mismatch")
        return record

    def envelope(self, request, role, data, transaction):
        return dict(kind="envelope", role=role, bytes=data, length=len(data),
                    hash=digest(data), document=request.document,
                    revision=request.revision, transaction=transaction)

    def save(self, request, current_revision=None):
        if len(request.attempted) > MAX:
            raise Fault("oversized request")
        self.counter += 1
        tx = str(self.counter)
        attempt, original, prepared = "attempt:" + tx, "original:" + tx, "prepared:" + tx
        self.persist(attempt, self.envelope(request, "attempt", request.attempted, tx))
        source = self.provider.read()
        if len(source) > MAX:
            raise Fault("oversized source")
        try:
            source.decode("utf-8-sig", errors="strict")
        except UnicodeError as error:
            raise Fault("invalid UTF8 source") from error
        observed = digest(source)
        authorized = request.baseline if request.consent_hash is None else request.consent_hash
        if authorized is None or observed != authorized:
            raise Fault("conflict or unknown baseline")
        self.room(2 * (len(source) + 128) + 2 * 128)
        self.persist(original, self.envelope(request, "original", source, tx))
        self.persist(prepared, dict(kind="prepared", document=request.document,
            revision=request.revision, original=original, attempt=attempt,
            original_hash=observed, attempt_hash=digest(request.attempted)))
        self.validate(prepared)
        if digest(self.provider.read()) != observed:
            raise Fault("source changed after preparation")
        self.provider.write(request.attempted)
        if self.provider.read() != request.attempted:
            raise Fault("read-back mismatch")
        self.persist("outcome:" + tx, dict(kind="outcome", prepared=prepared))
        self.published = dict(document=request.document, revision=request.revision,
            hash=digest(request.attempted), dirty=current_revision not in (None, request.revision))

    def restart(self):
        # Only entries beyond the modeled directory-sync boundary survive.
        recovered = []
        for key in self.durable:
            if key.startswith("prepared:"):
                self.validate(key)
                recovered.append(key)
        return recovered

    def protected(self, role, transaction="1"):
        return self.validate(role + ":" + transaction)["bytes"]

    def prune_eligible_history(self):
        # Conservative sketch: orphans, prepared references and every envelope
        # remain pinned. Only explicitly eligible successful history can leave.
        for key, record in list(self.durable.items()):
            eligible = (record.get("kind") == "history" and record.get("successful")
                        and not record.get("named") and not record.get("sole"))
            if eligible:
                self.step("prune.before-delete")
                del self.durable[key]
                self.step("prune.after-delete")

    def restore_new_copy(self, envelope, destination, *, explicit=False):
        if not explicit or destination is self.provider:
            raise Fault("explicit distinct destination required")
        record = self.validate(envelope)
        prepared = "prepared:" + record["transaction"]
        if prepared in self.durable:
            self.validate(prepared)
        destination.write(record["bytes"])
        if destination.read() != record["bytes"]:
            raise Fault("restored copy mismatch")
        # No acknowledgement/pruning is inferred from a successful copy.


class Contract(unittest.TestCase):
    def setup_model(self):
        model = Model()
        request = Request("document-7", 3, b"\xef\xbb\xbf# edited \xf0\x9f\xa7\xad\r\n", digest(model.provider.source))
        return model, request

    def assert_no_mutation(self, model, original=b"original\r\n"):
        self.assertEqual(model.provider.writes, 0)
        self.assertEqual(model.provider.source, original)
        self.assertIsNone(model.published)

    def test_exact_versions_precede_provider_write(self):
        model, request = self.setup_model()
        original = model.provider.source
        model.save(request)
        self.assertEqual(model.protected("attempt"), request.attempted)
        self.assertEqual(model.protected("original"), original)
        self.assertEqual(model.provider.writes, 1)
        self.assertEqual(model.provider.source, request.attempted)
        self.assertEqual(model.published["revision"], 3)
        self.assertEqual(len(model.restart()), 1)

    def test_protection_fault_matrix(self):
        for role in ("attempt", "original", "prepared"):
            for step in Model.STEPS:
                with self.subTest(role=role, step=step):
                    model, request = self.setup_model()
                    model.fault = role + "." + step
                    with self.assertRaises(Fault):
                        model.save(request)
                    self.assert_no_mutation(model)
                    # Process restart cannot invent an unsynced prepared record.
                    for key in model.restart():
                        self.assertEqual(model.validate(key)["revision"], request.revision)

    def test_corrupt_protection_blocks_write(self):
        for role in ("attempt", "original"):
            with self.subTest(role=role):
                model, request = self.setup_model()
                model.corrupt_role = role
                with self.assertRaisesRegex(Fault, "corrupt"):
                    model.save(request)
                self.assert_no_mutation(model)

    def test_provider_fault_matrix_keeps_both_versions_no_rollback(self):
        for failure in ("zero", "partial", "flush", "close", "mismatch", "read-back"):
            with self.subTest(failure=failure):
                model, request = self.setup_model()
                original = model.provider.source
                model.provider.failure = failure
                with self.assertRaises(Fault):
                    model.save(request)
                self.assertEqual(model.provider.writes, 1)  # No rollback or retry.
                self.assertIsNone(model.published)
                self.assertEqual(model.protected("attempt"), request.attempted)
                self.assertEqual(model.protected("original"), original)
                self.assertEqual(model.restart(), ["prepared:1"])

    def test_outcome_failure_keeps_unresolved_preparation(self):
        for step in Model.STEPS:
            with self.subTest(step=step):
                model, request = self.setup_model()
                model.fault = "outcome." + step
                with self.assertRaises(Fault):
                    model.save(request)
                self.assertIsNone(model.published)
                self.assertEqual(model.restart(), ["prepared:1"])
                self.assertEqual(model.protected("attempt"), request.attempted)
                self.assertEqual(model.protected("original"), b"original\r\n")

    def test_unknown_baseline_conflict_and_stale_consent_block_write(self):
        for baseline, consent in ((None, None), ("wrong", None), ("wrong", "stale")):
            with self.subTest(baseline=baseline, consent=consent):
                model, request = self.setup_model()
                request = Request(request.document, request.revision, request.attempted, baseline, consent)
                with self.assertRaises(Fault):
                    model.save(request)
                self.assert_no_mutation(model)
                self.assertEqual(model.protected("attempt"), request.attempted)

    def test_exact_observed_consent_protects_current_source(self):
        model, request = self.setup_model()
        model.provider.source = b"external version\r\n"
        request = Request(request.document, request.revision, request.attempted, request.baseline, digest(model.provider.source))
        model.save(request)
        self.assertEqual(model.protected("original"), b"external version\r\n")

    def test_external_change_after_preparation_blocks_write(self):
        model, request = self.setup_model()
        model.provider.change_on_read[2] = b"external update"
        with self.assertRaises(Fault):
            model.save(request)
        self.assert_no_mutation(model, b"external update")
        self.assertEqual(model.protected("original"), b"original\r\n")

    def test_remaining_final_check_race_is_not_atomicity(self):
        model, request = self.setup_model()
        model.provider.change_before_write = b"late external update"
        model.save(request)
        self.assertEqual(model.protected("original"), b"original\r\n")
        self.assertNotEqual(model.protected("original"), b"late external update")
        # Documents a limit: generic provider APIs cannot protect an unseen late edit.

    def test_source_read_error_retains_attempt_no_write(self):
        model, request = self.setup_model()
        model.provider.failure = "source-read"
        with self.assertRaises(Fault):
            model.save(request)
        self.assert_no_mutation(model)
        self.assertEqual(model.protected("attempt"), request.attempted)

    def test_invalid_or_oversized_source_fails_closed(self):
        for source in (b"\xff", b"x" * (MAX + 1)):
            with self.subTest(length=len(source)):
                model, request = self.setup_model()
                model.provider.source = source
                with self.assertRaises(Fault):
                    model.save(request)
                self.assert_no_mutation(model, source)
                self.assertEqual(model.protected("attempt"), request.attempted)

    def test_oversized_attempt_blocks_every_provider_call(self):
        model, request = self.setup_model()
        request = Request(request.document, 3, b"x" * (MAX + 1), request.baseline)
        with self.assertRaises(Fault):
            model.save(request)
        self.assert_no_mutation(model)
        self.assertEqual(model.provider.reads, 0)
        self.assertEqual(model.durable, {})

    def test_quota_failure_keeps_earlier_recovery(self):
        model, request = self.setup_model()
        model.provider.failure = "partial"
        with self.assertRaises(Fault):
            model.save(request)
        before = {key: dict(value) for key, value in model.durable.items()}
        model.quota = sum(model.cost(r) for r in before.values())
        writes = model.provider.writes
        for _ in range(3):
            with self.assertRaisesRegex(Fault, "quota"):
                model.save(request)
        self.assertEqual(model.durable, before)
        self.assertEqual(model.provider.writes, writes)
        self.assertEqual(model.restart(), ["prepared:1"])

    def test_pruning_exempts_pending_named_sole_and_orphan_versions(self):
        model, request = self.setup_model()
        model.provider.failure = "partial"
        with self.assertRaises(Fault):
            model.save(request)
        model.durable["orphan:2"] = dict(kind="envelope", bytes=b"orphan")
        for name, named, sole in (("named", True, False), ("sole", False, True), ("eligible", False, False)):
            model.durable[name] = dict(kind="history", successful=True, named=named, sole=sole)
        before = set(model.durable)
        model.prune_eligible_history()
        self.assertEqual(set(model.durable), before - {"eligible"})
        self.assertEqual(model.protected("attempt"), request.attempted)
        self.assertEqual(model.protected("original"), b"original\r\n")

    def test_interrupted_prune_never_deletes_pending_protection(self):
        for event in ("prune.before-delete", "prune.after-delete"):
            with self.subTest(event=event):
                model, request = self.setup_model()
                model.save(request)
                model.durable["eligible"] = dict(kind="history", successful=True, named=False, sole=False)
                model.fault = event
                with self.assertRaises(Fault):
                    model.prune_eligible_history()
                self.assertEqual(model.protected("attempt"), request.attempted)
                self.assertEqual(model.protected("original"), b"original\r\n")
                self.assertEqual(model.restart(), ["prepared:1"])

    def test_missing_and_corrupt_envelopes_not_restorable(self):
        for fault in ("missing", "bytes", "hash", "length", "document", "revision"):
            with self.subTest(fault=fault):
                model, request = self.setup_model()
                model.save(request)
                if fault == "missing":
                    del model.durable["original:1"]
                else:
                    value = {"bytes": b"corrupt", "hash": "wrong", "length": 999,
                             "document": "other-tab", "revision": 99}[fault]
                    model.durable["original:1"][fault] = value
                with self.assertRaises(Fault):
                    model.restart()

    def test_restore_requires_explicit_distinct_destination(self):
        model, request = self.setup_model()
        model.save(request)
        for destination, explicit in ((Provider(), False), (model.provider, True)):
            with self.subTest(explicit=explicit):
                with self.assertRaises(Fault):
                    model.restore_new_copy("original:1", destination, explicit=explicit)
        self.assertEqual(model.provider.writes, 1)

    def test_restore_original_and_attempt_to_new_copy(self):
        model, request = self.setup_model()
        model.provider.failure = "partial"
        with self.assertRaises(Fault):
            model.save(request)
        before = model.provider.source
        for role, expected in (("original", b"original\r\n"), ("attempt", request.attempted)):
            with self.subTest(role=role):
                destination = Provider(b"")
                model.restore_new_copy(role + ":1", destination, explicit=True)
                self.assertEqual(destination.source, expected)
                self.assertEqual(destination.writes, 1)
                self.assertEqual(model.provider.source, before)
                self.assertEqual(model.restart(), ["prepared:1"])

    def test_corrupt_restore_and_failed_destination_retain_evidence(self):
        model, request = self.setup_model()
        model.save(request)
        destination = Provider(b"")
        model.durable["original:1"]["hash"] = "wrong"
        with self.assertRaises(Fault):
            model.restore_new_copy("original:1", destination, explicit=True)
        self.assertEqual(destination.writes, 0)
        for failure in ("partial", "mismatch", "read-back"):
            with self.subTest(failure=failure):
                clean, request = self.setup_model()
                clean.save(request)
                destination = Provider(b"")
                destination.failure = failure
                with self.assertRaises(Fault):
                    clean.restore_new_copy("attempt:1", destination, explicit=True)
                self.assertEqual(destination.writes, 1)
                self.assertIn("attempt:1", clean.durable)
                self.assertEqual(clean.provider.writes, 1)

    def test_later_revision_stays_dirty(self):
        model, request = self.setup_model()
        model.save(request, current_revision=4)
        self.assertEqual(model.published["revision"], 3)
        self.assertTrue(model.published["dirty"])
        self.assertEqual(model.protected("attempt"), request.attempted)


if __name__ == "__main__":
    print("MODEL ONLY — no production persistence, SAF, or release acceptance", flush=True)
    unittest.main(verbosity=2)
