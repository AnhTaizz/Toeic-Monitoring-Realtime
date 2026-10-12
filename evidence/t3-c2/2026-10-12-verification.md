# T3-C2 · Reducer delta verification12/10/2026

## Source và phạm vi

- Base fetch mới nhất `144fe559c5cf2d5a9608d3cf804cd2adb22bca70`, T3-C1 PR#24 đã merge. Nhánh `feat/t3-c2-monitoring-delta-reducer`; chưa có branch/PR C2 khi bắt đầu. Main vẫn144fe55 khi fetch kiểm cuối. PR17/23 không sửa hoặc merge trong task.
- Implementation/contract/harness `1c3f982`; bổ sung oracle missing-base `cca9003`, before-first-full `e241cc6`, sửa constructor version oracle `4042b1c`. Final source `4042b1c`; evidence/docs commit và Git PR/merge cuối xem PR/final. Không reset/amend source SHA đã ghi vào raw.
- JDK Temurin21.0.10+7/Maven3.9.15/Windows11 build26200/PG18 volume hiện có. Docker chưa chạy trong phiên đầu; bật Docker Desktop ẩn và compose với volume cũ, không reset shared DB hoặc đổi credential. .env/tracker/originalplans/migrations/exam source không sửa. A/B hook changes tối thiểu nằm ở handler/transport/state, không giả T3-A3 DONE.
- Final delta run `T3C2-8e57770e-105b-4396-bd30-75cdac832d14`, source4042b1c: metadata có command/OS/JDK/config/source manifest233files/JAR SHA. Working tree ghi đúng4doc guides chưa commit; **không gọi clean toàn checkout**, runtime/source đã commit. Code không sửa sau final package/smokes. Jar hashes/source manifest đối chiếu khớp current; không unresolved compiler stub/retired SequentialDeltaReplayAdapter trong JAR đã kiểm.
- Full regression `T2C1-0296a5be-0936-4743-b957-a7bdb4564ea3`; late/gap `T2C2-8b90aee9-7d05-4956-ba9e-76163a42bc57`; measurement `C4-0fc06036-5892-4c64-9915-30a2c3d58ffb`; CLI `271c19c0-b95e-49a4-965b-80d2d2c29663`. Tất cả final chạy sau source4042b1c, tuần tự.

## Implementation và phụ thuộc

MonitoringStateReducer thuần production chung full/delta, cache64ID typed canonical digest, atomic working-copy validation, RESYNC/UNSYNCED last-known khi thiếu base, full cao phục hồi, old binding/version không poison. FullStateReducer là facade compatibility C1; FullSnapshotReducer server boundary chuyển ErrorCode. Replay thay adapter giả bằng lõi production; oracle independent raw cập nhật type/version/needs-full/noop refresh, test missing-first-full/drop intermediate/full recovery/late delta.

Tận dụng AuthorizationService, SessionAuthenticationService, C1 active user/attempt/socket/collector/epoch binding, store RAM/global lock, registry assigned-proctor, worker maintenance/TTL/capacity/shutdown. Bổ sung MonitoringStateService.delta(), handler ACK/RESYNC, DTO MonitoringResyncPayload, RealtimeClient capability/parser, FullSnapshotDelivery resync/freshness. Scope/auth trước cả cache; ACK RAM sau authoritative acceptance, duplicate no apply/push/touch. Event/gap transaction commit ACK không đổi. Lõi không socket/SQL, writes/push ngoài store lock; scope query trong writer giữ cơ chế coarse lock hiện có, không claim throughput hoặc giao dịch RAM+DB.

Default server delta-enabled=false, client delta-experimental=false, candidate factory vẫn full. Opt-in phải có cả local flag và correlated server heartbeat ACK `monitoringDelta=true`, reset khi reconnect. Chỉ harness tạo DeltaSnapshotDelivery sau negotiation; capability không cấp quyền hoặc thay gate.

Resync reply có request/attempt/trace/collector/epoch/currentSequence và reasonBASE_MISSING. Message chưa áp không ACK. Duplicate đã áp khi needsFull ACK kèm resync object; không khôi phục bằng duplicate. Parser/encoder kiểm binding/correlation; clear base/pending, fresh scan rồi full cao trong epoch; current0/TTL/restart/STale/long hết → OPEN/full1 mới. Retry giữ nguyên whole message.

Freshness: unchanged successful scan mới sau2s ACK interval → empty delta seq+1. Snapshot observationNanos phải cùng clock với ticker/age0..<refresh; aged/cached snapshot không trở thành mới qua observe(). Full30s chỉ xét trên scan mới, không tự tick latest cũ; lỗi/hung không tạo refresh. Server new accepted state touch, duplicate không gia hạn; heartbeat chỉ presence. Baseline6s/TTL5phút giữ nguyên. Smoke scale stale1200/TTL4000/poll100/refresh200, reconciliation30000ms; không benchmark hoặc so latency clock hai máy.

## Kiểm tra mới đã chạy

| Kiểm tra | Kết quả | Mức / giới hạn |
|---|---|---|
| Baseline mvn test trên144fe55 |531PASS lúc20:31:23 UTC+7| Chạy mới |
| Final mvn package/source4042b1c |**554PASS**,0fail/error/skip,21:25:37 UTC+7| protocol43/client361/server149/spike1;23new invocations |
| Python analyzer unit |10PASS| Script known-types thay đổi, không E2 |
| Core6/measurement2/client5+parser2/oracle1/server7 mới |23PASS| Snapshot/clock/scope/socket MOCK; real executors/latch, production code |
| Full-first/delta add-remove/noop/dup/conflict/common64ID/invalid set atomic/old/full recovery |PASS| Expected viết tay; cache/metadata/PID cases + hồi quy C1 |
| OPEN/full/CLOSE versus waiting delta/maintenance |PASS| Latch xác lập order; không sleep để tạo race |
| HTTP/WS full1 → delta2 → retry, invalidremove/conflict, missingbase → RESYNC → full5 |PASS| REAL Spring/PG/auth/HTTP/WS/parser, manual process data MOCK |
| Scope CLOSED before duplicate, proctor write denied, global token revoke |PASS| REAL JDBC update trong TEST/schema/token scope; no fake ACK |
| Unchanged real collector worker MOCK source/noop keeps SYNCED; full interval30s |PASS| Thời gian hoạt động vượt stale1200ms; new-state accepted only |
| Hung source/HB ONLINE → STALE tự push |PASS| Không CLOSE/HTTP state read/manual maintain trong phaseauto; source hang latch |
| Future-base injection → client parser/encoder fresh full in same epoch |PASS| Inbound resync và callback REAL; deliberately bad base SIMULATED |
| SOURCE_FAILURE/HB ONLINE → STALE/TTL tombstone → fresh scan/old epoch rejected/OPEN/full1 |PASS| Scheduler thật, old base discarded; history retained |
| Spring server restart same port/persisted login → reconnect/new instance/epoch/full |PASS| REAL server contexts/network; event/gap retained |
| Core replay + independent oracle/manual fixture/metadata/event IDs-content |PASS| REPLAY network/ACK/control SIMULATED; no fake source-derived answer |
| Delta/full mutation và TEST/future-config refusal |FAIL đúng exit1 / refused exit2| Giữ negative reports, không lấy làm product failure |
| REAL ProcessHandle6scans100ms capture + replay full/delta |COMPLETE/PASS45events| Source REAL; gate/ACK/network replay SIMULATED, không live OS delta |
| Full-Gui C1 / late-gap-Gui C2 |PASS| REAL Edge/full/proctor component ởC1; C2 readings MOCK/fault SIMULATED; candidate whole GUI NOT RUN |
| EventC3 / presenceA4 / measurementC4 hồi quy |PASS| REAL PG/WS/owned Edge or JVM; faults/overflow/readings đúng nhãn từng transcript |
| Measurement DELTA/RESYNC known types + no extra counters |PASS| Delta4endpoints/57categories COMPLETE/drop-unwritten-pending0; BUSINESS_ACK bytes null |
| Default full/late-gap/measurement regression types |Không có DELTA/RESYNC| Confirm default chưa bật delta |
|69artifact hashes/gzip/source233manifest/JARs/summary regenerate |PASS| [Artifact audit](artifact-audit.json), JSON/CSV giống byte |
| DB read-only audit |test_schemas=0/public_version=7| Prefix đúngdelta_state/full_snapshot/late_events/c3/a4/c4; không reset DB |

Final command: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t3c2.ps1 -JavaHome <JDK21>`. Script scope TEST own/new GUID, .env chỉ đọc; command/config trong metadata. [Guide](../../docs/MONITORING_DELTA_REDUCER.md) có lệnh startup và self-test. [Transcripts](transcripts/) lưu build/Python/CLI/all regressions/failures; [checksums](checksums.json) ledger path/hash. Raw trace/measurement/metadata/summary/report giữ exact bytes (gzip mtime0 lossless nếu lớn), originalSha256 kiểm bytes phục hồi. Transcripts decode đúng UTF16 BOM PowerShell→UTF8/LF/trim whitespace/prefix aliases, markedsanitized và originalSha256 file local gốc. Không sửa raw để PASS.

## Failure và sửa trong phiên

- Test C1 cũ3case giả định có thể tick latest sau30s hoặc dùng queued snapshot rất cũ. C2 đổi contract scan freshness; cập nhật test để nộp observation mới đúng clock, thêm cached/hung/sourcefailure cases. Không bỏ case khỏi suite.
- Core expected test đầu nhầm old version còn trong cache/different payload là STALE; theo contract là CONFLICT. Sửa expected, giữ case outside-window STALE riêng.
- Test scheduled pump dùng helper observationNanos=Long.MAX_VALUE, không cùng System.nanoTime. Khi thêm clock-domain/age validation, test FAIL; fixture clock thật được sửa, production không nới guard.
- Oracle Version thêm message type nhưng một call constructor còn2args, gây unresolved compilation problem trong compiled class và22test lỗi. Sửa source constructor, tái biên dịch đúng2generated classes (không clean workspace/artifacts); final554 và JAR stub audit PASS. Giữ [transcript](transcripts/initial-oracle-constructor-failed.txt). Code commit4giữ quá trình sửa, không giả source trước PASS.
- Transcript lỗi constructor có22ký tự U+FFFD ở dấu trang trí giữa tên test và lỗi. Đối chiếu UTF-16 BOM log gốc `t3c2-final-build.txt`/originalSHA cho thấy cả22ký tự đã có ở nguồn; bản sao giữ nguyên, không sửa bằng chứng. Các transcript khác không NUL/U+FFFD; auditor cho phép đúng ngoại lệ nguồn này, không nhầm thành lỗi decode bản sao.
- Smoke manifest đầu kể source đã xóa trong git index, ResolvePath fail trước launch. Thêm TestPath filter, metadata giữ dirty deletion. DB smoke tiếp theo failMIGRATION khi Docker chưa chạy; bật Docker/compose volume cũ rồi LIVE PASS. Không sửa credential/migration hoặc reset dữ liệu.
- Exploratory live run sourceprecommit giữ4endpoints55categories ởdelta-exploratory, final57categories. Counts theo run, không chọn số đẹp/performance. Không âm thầm thay failed raw thành PASS.

## Review và giới hạn

[Diff/link/privacy audit](final-audit.json) kiểm phạm vi/secret/private paths/link và preserved original plans/migrations/exam. A cần review auth beforedup, active binding, RAM/global lock/TTL/epoch/publish/ACK; B cần capability correlation, resync parser/handler/guard, observation clock và lifecycle. T3-A3 dùng được một số hooks nhưng task A/human review chưa DONE; không giả approval hoặc IT04 toàn hệ thống.

REAL network ởlocalhost, readings delta MOCK; REAL ProcessHandle/owned Edge có ở CLI/full/event regression riêng. Không live delta hai desktop/processOS thật, candidate GUI toàn app, LAN/máy2, MT02–MT06 cảgate/T3-C3campaign, E1/E2. Human A/B review và ChatGPT planning-review **NOT RUN** theo opt-out trước đó. ACK state chỉ RAM, không durability hay chống client sửa; freshness theo receipt/new accepted observation, không OS timestamp/latency guarantee. Chưa tối ưu global lock hoặc thêm distributed store.

User cho phép commit/push/PR/merge commit khi không còn blocker trong C2 và GitHub required checks đáp ứng, giữ local/remote branch và sync main an toàn. GitHub final checks/PR/merge SHA trong PR/final; không coi không có CI/review là humanapproval. **Dừng T3-C2**, fulldefault; không làm gate/C3/C4/E1/E2 hoặc sửa/merge PR17/23.
