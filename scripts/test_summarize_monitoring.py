"""MOCK known traces, hand-calculated bytes and incomplete-log detection; stdlib only."""
import copy
import hashlib
import importlib.util
import json
import pathlib
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("summary", pathlib.Path(__file__).with_name("summarize-monitoring.py"))
summary = importlib.util.module_from_spec(spec)
spec.loader.exec_module(summary)


def trace(endpoint="CLIENT", domain="MOCK-clock", rows=None):
    base = dict(schemaVersion=summary.SCHEMA, runId="MOCK-known", clockDomain=domain,
                endpoint=endpoint, recordedAt="2026-10-05T00:00:00Z", elapsedNanos=0)
    records = [dict(base, recordType="METADATA", settings=dict(workloadLabel="MOCK"))]
    counts = {}
    for index, (direction, kind, outcome, size) in enumerate(rows or [], 1):
        record = dict(base, recordType="MESSAGE", direction=direction, messageType=kind, outcome=outcome,
                      recordIndex=index, elapsedNanos=index, bytesUtf8=size, sequence=None)
        record.update({name: None for name in ("messageId", "requestId", "traceId", "attemptId", "eventId", "gapId", "collectorSessionId")})
        records.append(record)
        key = (direction, kind, outcome)
        totals = counts.setdefault(key, [0, 0]); totals[0] += 1; totals[1] += size
    counters = [dict(key=dict(recordType="MESSAGE", direction=d, messageType=t, outcome=o), messages=n, bytesUtf8=b)
                for (d, t, o), (n, b) in counts.items()]
    records.append(dict(base, recordType="FINAL", elapsedNanos=len(records), status=dict(counters=counters,
                        logDroppedCount=0, logUnwrittenCount=0, pendingWrites=0, writerFailed=False, flushTimedOut=False, closed=True)))
    return records


class SummaryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.folder = pathlib.Path(self.temp.name)

    def write(self, records, name="raw.jsonl"):
        path = self.folder / name
        path.write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in records), encoding="utf-8", newline="\n")
        return path

    def test_hand_calculated_retry_two_endpoints_no_tx_rx_double_count(self):
        # Two retries of a 100-byte event, one 20-byte ACK, RX duplicates TX observations.
        client = self.write(trace(rows=[("TX", "PROCESS_OBSERVED", o, 100) for _ in range(2) for o in ("ATTEMPTED", "WRITE_COMPLETED")]
                                  + [("RX", "ACK", "RECEIVED", 20)]), "client.jsonl")
        server = self.write(trace("SERVER", "MOCK-server", [("RX", "PROCESS_OBSERVED", "RECEIVED", 100)] * 2
                                  + [("TX", "ACK", o, 20) for o in ("ATTEMPTED", "WRITE_COMPLETED")]), "server.jsonl")
        result = summary.summarize([client, server])
        self.assertEqual("COMPLETE", result["status"])
        self.assertEqual(220, result["totals"][0]["rawTxAttemptedBytes"])
        self.assertEqual(220, result["totals"][0]["rawTxWriteCompletedBytes"])
        self.assertEqual(hashlib.sha256(client.read_bytes()).hexdigest(), result["endpoints"][0]["inputSha256"])

    def test_generated_outputs_reproducible_without_mutating_input(self):
        source = self.write(trace(rows=[("TX", "HEARTBEAT", "ATTEMPTED", 11), ("TX", "HEARTBEAT", "WRITE_FAILED", 11)]))
        before = source.read_bytes()
        for name in ("a", "b"):
            self.assertEqual(0, summary.main([str(source), "--output", str(self.folder / name)]))
        for extension in ("json", "csv"):
            self.assertEqual((self.folder / ("a." + extension)).read_bytes(), (self.folder / ("b." + extension)).read_bytes())
        self.assertEqual(before, source.read_bytes())
        self.assertEqual(0, summary.summarize([source])["totals"][0]["rawTxWriteCompletedBytes"])

    def test_drop_and_counter_mismatch_are_reported_without_repair(self):
        records = trace(rows=[("RX", "HEARTBEAT", "RECEIVED", 10)])
        records[-1]["status"]["logDroppedCount"] = 1
        records[-1]["status"]["counters"][0]["messages"] = 2
        result = summary.summarize([self.write(records)])
        self.assertEqual("INCOMPLETE", result["status"])
        self.assertIn("RAW_COUNTER_MISMATCH", result["endpoints"][0]["issues"])
        self.assertIn("RECORDER_INCOMPLETE", result["endpoints"][0]["issues"])
        self.assertEqual(1, result["rows"][0]["rawMessages"]); self.assertEqual(2, result["rows"][0]["counterMessages"])

    def test_truncated_and_missing_final(self):
        path = self.write(trace()[:-1]); path.write_bytes(path.read_bytes().rstrip(b"\n"))
        issues = summary.summarize([path])["endpoints"][0]["issues"]
        self.assertIn("TRUNCATED_LINE", issues); self.assertIn("MISSING_FINAL", issues)

    def test_malformed_and_secret_input_are_not_echoed(self):
        path = self.write(trace()); path.write_bytes(path.read_bytes() + b'{secret-password}\n')
        result = summary.summarize([path])
        self.assertEqual("INCOMPLETE", result["status"])
        self.assertNotIn("secret-password", json.dumps(result))

    def test_duplicate_endpoint_not_added_twice(self):
        path = self.write(trace(rows=[("TX", "HEARTBEAT", "ATTEMPTED", 10)]))
        result = summary.summarize([path, path])
        self.assertEqual("INCOMPLETE", result["status"]); self.assertEqual(10, result["totals"][0]["rawTxAttemptedBytes"])

    def test_validation_rejects_bad_ids_sequence_clock_and_invented_bytes(self):
        records = trace(rows=[("RX", "HEARTBEAT", "RECEIVED", 10)])
        for field, value in (("eventId", "private/path"), ("sequence", 1.5), ("sequence", True), ("clockDomain", "other"), ("bytesUtf8", -1)):
            with self.subTest(field=field, value=value):
                altered = copy.deepcopy(records); altered[1][field] = value
                self.assertEqual("INCOMPLETE", summary.summarize([self.write(altered)])["status"])

    def test_duplicate_fields_rejected(self):
        with self.assertRaises(ValueError): summary.loads('{"a":1,"a":2}')

    def test_oversized_line_hash_still_covers_whole_file(self):
        path = self.write(trace()); path.write_bytes(path.read_bytes() + b"x" * (1024 * 1024 + 1200))
        result = summary.summarize([path])
        self.assertIn("OVERSIZED_LINE", result["endpoints"][0]["issues"])
        self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), result["endpoints"][0]["inputSha256"])


if __name__ == "__main__": unittest.main()
