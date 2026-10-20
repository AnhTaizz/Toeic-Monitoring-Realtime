# MON-07 status/gap — kiểm chứng 20/10/2026

**USER_AUTHORIZED_EXPERIMENT**, attachment f09808ef. Không human approval A/B/C,
không policy/ADR/gate acceptance. Experiment OFF, FULL mặc định; USB physical0/12
NOT_RUN, MON07 chưa DONE. Không observer production/real capture/push/dashboard/rules,
offline60s/retention30ngày hoặc E1/E2. Dừng sau slice này.

## Source và phạm vi

- Base main **519063dd556345eaa3e213967846209a504a91e9** (PR85); nhánh/worktree riêng
  `feat/c-mon07-status-gap-20261020`, không reset/ghi đè/xóa worktree cũ.
- Runtime/build **1b84d300021109fac32ea40ae184ce2afab87dcc**. Test-only
  **1c008c84853384d4cd51e0a45fa5d866d052e8fd** tăng guard OFF cho cả status/gap.
  Không runtime change sau build. Publication/CI/PR/merge cuối được ghi trong PR,
  không gán source HEAD tài liệu cho artifact cũ.
- Kiểm main/open PR trước code, không status/gap PR trùng. Main maxV16; HEAD11PR
  đang mở maxV8–V15, không collisionV17. [Bảng contract trước code](../../docs/MON07_STATUS_GAP_EXPERIMENT.md).

`ObserverStatusSignal`, `SignalGap`, `Signal.parse/SUPPORTED_TYPES` thêm hai typed
records strict. Service/handler/history/capability và transport guards đã generic,
không cần copy service/queue/socket/retry/parser mới hoặc sửa RealtimeClient runtime.
V17 append-only mở mapping type/source/quality, không sửa V1–16/checksum/data cũ.

`ObserverStatusEmitter.start/observe/flush/stop` nhận MOCK status, dùng shared context.
Bốn source slots cố định, dedup status/reason,1s monotonic/source, coalesce latest;
caller flush từ worker có sẵn, không tạo worker. Các quan sát bị coalesce chưa là
event nên không phải queue drops; event admission bị bỏ chỉ thử một lần, không poll
retry. Lifetime claim/generation ngăn duplicate emitter/callback cũ. Không raw
exception/problem/command/serial/path/username hoặc sensor value0 giả.

`SignalDelivery(context,transport)` claim một queue/run, dùng cùng context/sequence
với mọi emitter. Constructors legacy không context vẫn local-only drops, không tự
dựng counter khác. `enqueue/tick/freezeGap/receive/close/gapStatus` dùng một map,
một scheduler, cùng socket/capability/ACK/retry. Không process behavior change.

## Ba ý nghĩa riêng

| Tình huống | Ý nghĩa và event |
|---|---|
| USB UNKNOWN/ERROR/UNSUPPORTED | Không đủ dữ liệu hoặc API không hỗ trợ; status nguồn, không count0/removal. USB adapter giữ last confirmed baseline. Không suy số lần cắm/rút trong khoảng lỗi. |
| Queue count/byte full | Chỉ event mới bị bỏ theo drop-new. Accumulator count/min/max local admission UTC; freeze SIGNAL_GAP khi reserve trống. Không đếm wrong scope/closed hoặc gap retry. |
| Mất heartbeat | Presence server UNKNOWN riêng, không tự suy nguồn lỗi hay droppedCount. |

READY chỉ INITIAL/RECOVERED COMPLETE; UNKNOWN chỉ NO_DATA/PAUSED_OFFLINE UNREADABLE;
UNSUPPORTED API_UNAVAILABLE UNSUPPORTED; ERROR PERMISSION_DENIED/SCAN_FAILED
UNREADABLE. PAUSED_OFFLINE chỉ enum, không cài policy60s. Activity source deferred,
chỉ bốn nguồn đã có value type thay cho năm nguồn catalog có activity chưa triển khai.

## Budget, retry và stop

- Data≤256/1MiB (configurable thấp hơn), một gap reserve≤8192bytes => default tổng
  **257entries/1056768serialized bytes**, accumulator fixed count/min/max,4status
  slots chưa materialize. Object overhead hữu hạn; không queue ngoài/disk.
- Flight/write≤2 tổng, control/data eligible luân phiên (flight1 cũng có test),
  không evict old/in-flight/failed/exhausted. ACK đến trước write completion vẫn
  giữ entry/budget tới completion, không mở thêm serialized RAM ngoài budget.
- IDs/payload/count/time/sequence frozen, observedAt=lastDroppedAt và origin
  UNSPECIFIED cho control summary. Time là local admission clock, không event
  observedAt cũ; wall clock lùi dùng min/max, không giả duration monotonic/cross-host.
- Accumulator chỉ reset sau internal reserved insertion. Drop mới vào batch khác;
  failed/exhausted giữ gap unconfirmed, manual retry cùng message. Không gap-báo-gap.
  Context/sequence exhaustion giữ accumulator rồi stop; MAX_INTEGER count stop rõ
  COUNT_LIMIT, không wrap/clamp. Caller không retry enqueue sau drop.
- GapStatus tách unreported+time, unconfirmed, acknowledgedDrops và discardedUnconfirmed.
  Status.problem là last diagnostic, không thay counters. Close/revoke không flush,
  giữ local report theo RAM, không chuyển attempt hoặc hứa sống qua crash. Owner
  đóng context/lease/realtime; emitter/USB adapter không đóng tài nguyên mượn.

## Các lượt đã chạy

| Lượt | Source | Kết quả |
|---|---|---|
| Baseline trước sửa | 519063d | 246PASS/0SKIP =33protocol+181client+32server |
| Unit đầu | work-in-progress trước1b84 | Protocol37PASS, client113tests/1FAIL: legacy fixture vẫn coi status là unknown type |
| Unit sau sửa fixture | work-in-progress trước1b84 | 158PASS/0SKIP; chưa bao gồm pre-write controls test thêm sau đó |
| Focused đầu | 1b84d30 | 334PASS/0SKIP,29 pipeline PG cases |
| Full verify + JAR | 1b84d30 | **1336total=1192PASS/144SKIP/0FAIL/ERROR**, PG opt-in false, không gọi skipped PASS |
| Focused cuối, OFF mạnh hơn | 1c008c8 | **334PASS/0SKIP**,29 pipeline PG cases, cùng runtime1b84 |
| JAR consistency | runtime1b84 | **10checks PASS**,4JAR SHA256 ghi receipt |

Không cộng repeated selections thành số test mới. Thêm21test methods (4protocol,
12control-client,1RealtimeClient,4PG); upgrade method cũ thành parameterized15/16,
tạo thêm một executed invocation khi PG enabled.29pipeline gồm28methods với hai
invocations upgrade; không chạy lại5001campaign hoặc gọi mọi matrix/hardware PASS.

Full logs phân biệt82protocol/35probe/794client/425server, skipped26client+118server
(GUI/package/PG opt-ins và2local symlink cases). Full totals lấy log đúng invocation,
không aggregate XML đã bị focused sau ghi lại. Focused XML được runner copy riêng,
23suite hashes trong receipt. Windows/JDK21/Maven3.9.15/PostgreSQL18.3 tại receipt.

### MOCK qua REAL WS/HTTP/PostgreSQL TEST

Input status/USB/display/focus/window MOCK; overflow xảy ra thật trong admission
queue client hữu hạn. Initial USB UNKNOWN không đổi baseline thiết bị. Một status
và display được giữ; window/focus bị drop2; gap dùng shared observationSequence5.
Mất ACK riêng gap SIMULATED; retry cùng envelope/time/IDs,1row/timelineSequence,
acknowledgedDrops2. History limit1/fixed-through3 không kéo row window commit sau.

Cả hai type kiểm canonical duplicate, changed-content CONFLICT, invalid private/
unknown/duplicate/quality/padded-byte wire reject/no insert; foreign/role/terminal/
expiry/revoke. Deferred COMMIT fault SIMULATED rollback row/counter và không success
ACK; retry sau bỏ fault commit đúng. Real revoke khi gap reserved dừng delivery/
worker/emitter, không flush. Generic barrier tests vẫn kiểm auth sau counter/session/
assignment lock waits; old typed/process/FULL/DELTA/presence regressions giữ.

Fresh schema ápV1–17. Upgrade15→17 và16→17 giữ nguyên byte representation display
row, counter và checksum V13–V16 đã áp được đối chiếu. OFF vẫn ápV17, không service/
capability/history lane; cả window/status/gap WS bị reject,0event/counter. Không DB dev.

Hai focused runs đều **ownedSchemasRemaining0/clusterStopExit0/password file absent/
postmaster.pid absent**. Fixture closes server/client/socket/lease/context; delivery
awaitStopped assertions PASS. Status emitter thêm0worker/listener. Không broad kill,
đọc `.env`, reset shared DB/volume hoặc root clean.

## Failures và giới hạn evidence

Hai baseline launcher attempts dừng **trước tests**: Maven cache mặc định C:\.m2 không
ghi được; sau pin cache thì PowerShell tách unquoted dotted -D option. Lượt quoted
246PASS, không tính setup failure thành feature FAIL/PASS. Unit đầu1FAIL là fixture
capability lỗi thời; giữ log/XML trước sửa. Thay fixture unsupported type thành
EXAM_ACTIVITY_SUMMARY và thêm explicit capability admission/pre-write test cho cả
status/gap; không đổi runtime expected thành sai semantics/skip/retry mù.

Receipt generator đầu đọc UTF-16 PowerShell log như UTF-8 bị lỗi decode; chưa tạo
receipt thành công. Đã decode theo BOM, raw không sửa. Không phải lỗi feature.
Runner local ban đầu có selector helper `send` không phải test; Surefire không chạy
helper. Final runner bỏ selector đó, receipt chỉ đếm test XML thực sự chạy.

Raw/log/JAR/XML ở local ignored `target/status-gap-check` và root
`target/mon07-status-gap-runs`;28artifact hashes/23final XML hashes và4JAR hashes tại
[receipt](receipt.json). Hash là provenance, **không phải link tải GitHub**. Public
chỉ report/receipt/source/docs, không raw credential/metadata phần cứng/artifact lớn.
CI exact publication HEAD và merge verification sẽ ghi trong PR sau khi chạy.

## Bàn giao

B: owner tạo shared context/delivery mới; emit focus/window/display và source status
cùng sequence/queue; gọi flush sau1s từ worker hiện có; stop generation trước dọn,
owner đóng delivery/context/lease/realtime. Review UI disclosure/quyền/lifecycle riêng.
C: ánh xạ USB health vào enum reason an toàn, không gửi raw problem; không nối provider
trong slice này. UNKNOWN không thay USB baseline/ref semantics. Trước production cần
A review schema/auth/transaction, B transport/lifecycle, C provider/cadence/no overlap/
weak identity và USB thật H01–12. Owner policy/ADR/gate pending; **FULL default,
experiment OFF, USB0/12NOT_RUN, MON07 chưa DONE**.
