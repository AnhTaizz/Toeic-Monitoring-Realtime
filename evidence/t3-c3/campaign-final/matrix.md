# T3-C3 matrix generated from summary.json

Seed 20261012; source d813cb15b83ec640f3894486ff4c2defef268398; status PASS. Each cell: full/delta PASS; missing counts; common current-observation checkpoints.

| Scenario | low-change | high-change | reconnect |
|---|---|---|---|
| clean | PASS/PASS; missing 0/0; common 70 | PASS/PASS; missing 0/0; common 69 | PASS/PASS; missing 0/0; common 69 |
| conflict-id | PASS/PASS; missing 0/0; common 70 | PASS/PASS; missing 0/0; common 69 | PASS/PASS; missing 0/0; common 69 |
| conflict-sequence | PASS/PASS; missing 0/0; common 70 | PASS/PASS; missing 0/0; common 69 | PASS/PASS; missing 0/0; common 69 |
| drop-first | PASS/PASS; missing 0/0; common 65 | PASS/PASS; missing 0/0; common 64 | PASS/PASS; missing 0/0; common 64 |
| drop-state | PASS/PASS; missing 0/0; common 41 | PASS/PASS; missing 0/0; common 65 | PASS/PASS; missing 0/0; common 65 |
| duplicate | PASS/PASS; missing 0/0; common 70 | PASS/PASS; missing 0/0; common 69 | PASS/PASS; missing 0/0; common 69 |
| event-drop | PASS/PASS; missing 1/1; common 70 | PASS/PASS; missing 1/1; common 69 | PASS/PASS; missing 1/1; common 69 |
| late-after-full | PASS/PASS; missing 0/0; common 41 | PASS/PASS; missing 0/0; common 65 | PASS/PASS; missing 0/0; common 65 |
| reconnect-old | PASS/PASS; missing 0/0; common 66 | PASS/PASS; missing 0/0; common 65 | PASS/PASS; missing 0/0; common 65 |
| reorder | PASS/PASS; missing 0/0; common 41 | PASS/PASS; missing 0/0; common 65 | PASS/PASS; missing 0/0; common 65 |
| retry-resync | PASS/PASS; missing 0/0; common 42 | PASS/PASS; missing 0/0; common 66 | PASS/PASS; missing 0/0; common 66 |
| seeded | PASS/PASS; missing 1/1; common 8 | PASS/PASS; missing 1/1; common 11 | PASS/PASS; missing 1/1; common 0 (NO_COMMON_SYNC_CHECKPOINT) |

Acceptance replay only. ACK enumeration before faults; RESYNC recorded/no feedback; no fault-network ACK-loss/retry/presence/TTL/DB/live gate/E1/E2.
PASS with missing events means the delivered map matches permanent drop; it does not restore business history.
Seeded late data may arrive after reliable CLOSE, so lastDataState can be STALE. Targeted cases recover SYNCED.
