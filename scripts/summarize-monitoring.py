"""C4 JSONL -> reproducible JSON/CSV. Application WS bytes only; raw files stay untouched."""
import argparse
import collections
import csv
import hashlib
import json
import pathlib
import re
import sys
from datetime import datetime

SCHEMA = "monitoring-measurement-v1"
TYPES = {"HEARTBEAT", "PROCESS_OBSERVED", "MONITORING_GAP", "ACK", "ERROR", "MONITOR_WARNING", "MONITOR_PRESENCE", "UNKNOWN", "INVALID"}
RECORD_TYPES = {"MESSAGE", "BUSINESS_ACK", "OBSERVATION"}
ID = re.compile(r"[A-Za-z0-9_.:-]{1,128}\Z")
MAX_LONG = 2**63 - 1


def integer(value, minimum=0):
    if type(value) is not int or not minimum <= value <= MAX_LONG:
        raise ValueError("INVALID_INTEGER")
    return value


def identifier(value, nullable=False):
    if nullable and value is None:
        return
    if not isinstance(value, str) or not ID.fullmatch(value):
        raise ValueError("INVALID_ID")


def object_pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("DUPLICATE_FIELD")
        result[key] = value
    return result


def loads(line):
    return json.loads(line, object_pairs_hook=object_pairs,
                      parse_constant=lambda value: (_ for _ in ()).throw(ValueError("NONFINITE")))


def common(record):
    if not isinstance(record, dict) or record.get("schemaVersion") != SCHEMA or record.get("endpoint") not in {"CLIENT", "SERVER"}:
        raise ValueError("INVALID_SCHEMA")
    identifier(record.get("runId")); identifier(record.get("clockDomain"))
    timestamp = record.get("recordedAt")
    if not isinstance(timestamp, str) or not timestamp.endswith("Z") or len(timestamp) > 64:
        raise ValueError("INVALID_TIME")
    datetime.fromisoformat(timestamp)
    integer(record.get("elapsedNanos"))
    return record["runId"], record["endpoint"], record["clockDomain"]


def key(record):
    kind, direction, message, outcome = (record.get(name) for name in ("recordType", "direction", "messageType", "outcome"))
    if kind not in RECORD_TYPES or message not in TYPES:
        raise ValueError("INVALID_CATEGORY")
    valid = ((kind == "MESSAGE" and ((direction == "TX" and outcome in {"ATTEMPTED", "WRITE_COMPLETED", "WRITE_FAILED"})
                                    or (direction == "RX" and outcome == "RECEIVED")))
             or (kind == "BUSINESS_ACK" and direction == "RX" and message == "ACK" and outcome == "ACCEPTED")
             or (kind == "OBSERVATION" and direction == "RX" and message == "UNKNOWN" and outcome == "UNMEASURED"))
    if not valid:
        raise ValueError("INVALID_OUTCOME")
    return kind, direction, message, outcome


def data_record(record):
    category = key(record)
    integer(record.get("recordIndex"), 1)
    for name in ("messageId", "requestId", "traceId", "attemptId", "eventId", "gapId", "collectorSessionId"):
        if name not in record:
            raise ValueError("MISSING_FIELD")
        identifier(record[name], nullable=True)
    if "sequence" not in record or "bytesUtf8" not in record:
        raise ValueError("MISSING_FIELD")
    if record["sequence"] is not None:
        integer(record["sequence"])
    if category[0] == "MESSAGE":
        size = integer(record["bytesUtf8"])
    elif record["bytesUtf8"] is not None:
        raise ValueError("INVENTED_BYTES")
    else:
        size = 0
    return category, size


def terminal(record):
    status = record.get("status")
    if not isinstance(status, dict) or not isinstance(status.get("counters"), list) or len(status["counters"]) > 500:
        raise ValueError("INVALID_FINAL")
    for name in ("logDroppedCount", "logUnwrittenCount", "pendingWrites"):
        integer(status.get(name))
    for name in ("writerFailed", "flushTimedOut", "closed"):
        if type(status.get(name)) is not bool:
            raise ValueError("INVALID_FINAL")
    reported = {}
    for counter in status["counters"]:
        category = key(counter.get("key", {}))
        if category in reported:
            raise ValueError("DUPLICATE_COUNTER")
        reported[category] = [integer(counter.get("messages")), integer(counter.get("bytesUtf8"))]
        if category[0] != "MESSAGE" and reported[category][1] != 0:
            raise ValueError("INVENTED_BYTES")
    return status, reported


def summarize(paths):
    endpoints = []
    rows = []
    seen = set()
    issues = []
    for path in paths:
        raw = collections.defaultdict(lambda: [0, 0])
        info = None; final = None; reported = {}; previous_index = 0; previous_nanos = 0
        errors = []; hashed = hashlib.sha256(); line_number = 0
        try:
            with pathlib.Path(path).open("rb") as stream:
                while line := stream.readline(1024 * 1024 + 1):
                    hashed.update(line); line_number += 1
                    if len(line) > 1024 * 1024:
                        errors.append("OVERSIZED_LINE")
                        while chunk := stream.read(65536):
                            hashed.update(chunk)
                        break
                    if not line.endswith(b"\n"):
                        errors.append("TRUNCATED_LINE")
                    try:
                        record = loads(line.decode("utf-8")); identity = common(record)
                        kind = record.get("recordType")
                        if kind == "METADATA":
                            if info is not None or line_number != 1 or not isinstance(record.get("settings"), dict):
                                raise ValueError("INVALID_METADATA")
                            info = identity
                        elif info is None or identity != info or final is not None:
                            raise ValueError("INVALID_ORDER_OR_DOMAIN")
                        elif kind == "FINAL":
                            final, reported = terminal(record)
                        else:
                            category, size = data_record(record)
                            index = record["recordIndex"]
                            if index <= previous_index or record["elapsedNanos"] < previous_nanos:
                                raise ValueError("INVALID_ORDER")
                            if index != previous_index + 1:
                                errors.append("MISSING_RECORD_INDEX")
                            previous_index = index; previous_nanos = record["elapsedNanos"]
                            raw[category][0] += 1; raw[category][1] += size
                    except (ValueError, TypeError, AttributeError, OverflowError):
                        errors.append("MALFORMED_LINE")
        except OSError:
            errors.append("READ_FAILED")
        if info is None:
            issues.append({"inputSha256": hashed.hexdigest(), "issues": sorted(set(errors + ["MISSING_METADATA"]))}); continue
        if info in seen:
            issues.append({"runId": info[0], "endpoint": info[1], "clockDomain": info[2], "issues": ["DUPLICATE_ENDPOINT_FILE"]}); continue
        seen.add(info)
        if final is None:
            errors.append("MISSING_FINAL")
        elif (not final["closed"] or final["writerFailed"] or final["flushTimedOut"] or final["logDroppedCount"] or final["logUnwrittenCount"] or final["pendingWrites"]):
            errors.append("RECORDER_INCOMPLETE")
        if final is not None and dict(raw) != reported:
            errors.append("RAW_COUNTER_MISMATCH")
        for category in sorted(set(raw) | set(reported)):
            values = raw.get(category, [0, 0]); counter = reported.get(category)
            row = dict(zip(("runId", "endpoint", "clockDomain", "recordType", "direction", "messageType", "outcome"), info + category))
            row.update(rawMessages=values[0], rawBytesUtf8=values[1], counterMessages=counter[0] if counter else None, counterBytesUtf8=counter[1] if counter else None)
            rows.append(row)
        endpoints.append(dict(runId=info[0], endpoint=info[1], clockDomain=info[2], inputSha256=hashed.hexdigest(),
                              status="INCOMPLETE" if errors else "COMPLETE", issues=sorted(set(errors)),
                              logDroppedCount=final["logDroppedCount"] if final else None,
                              logUnwrittenCount=final["logUnwrittenCount"] if final else None,
                              pendingWrites=final["pendingWrites"] if final else None))
    totals = []
    for run in sorted({r["runId"] for r in endpoints}):
        chosen = [r for r in rows if r["runId"] == run and r["recordType"] == "MESSAGE" and r["direction"] == "TX"]
        totals.append(dict(runId=run,
                           rawTxAttemptedBytes=sum(r["rawBytesUtf8"] for r in chosen if r["outcome"] == "ATTEMPTED"),
                           rawTxWriteCompletedBytes=sum(r["rawBytesUtf8"] for r in chosen if r["outcome"] == "WRITE_COMPLETED"),
                           definition="Sum CLIENT TX + SERVER TX per outcome; RX and business ACK records excluded; JSON WebSocket only"))
    complete = bool(endpoints) and not issues and all(e["status"] == "COMPLETE" for e in endpoints)
    return dict(schemaVersion=SCHEMA, status="COMPLETE" if complete else "INCOMPLETE", endpoints=endpoints, issues=issues, totals=totals, rows=rows)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inputs", nargs="+")
    parser.add_argument("--output", required=True, help="Output base path, without extension")
    args = parser.parse_args(argv)
    paths = []
    for name in args.inputs:
        path = pathlib.Path(name)
        paths.extend(sorted(path.glob("*.jsonl")) if path.is_dir() else [path])
    result = summarize(paths)
    output = pathlib.Path(args.output); output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix(".json").write_text(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")
    columns = ["runId", "endpoint", "clockDomain", "recordType", "direction", "messageType", "outcome", "rawMessages", "rawBytesUtf8", "counterMessages", "counterBytesUtf8"]
    with output.with_suffix(".csv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=columns, lineterminator="\n"); writer.writeheader(); writer.writerows(result["rows"])
    print(f"Summary {result['status']}: endpoints={len(result['endpoints'])}, categories={len(result['rows'])}; WS JSON application bytes only")
    return 0 if result["status"] == "COMPLETE" else 2


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError):
        print("Summary failed: invalid input/output; raw content and private paths omitted", file=sys.stderr)
        sys.exit(2)
