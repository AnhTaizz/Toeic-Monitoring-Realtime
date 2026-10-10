# T2-C3 — trace quan sát, replay và oracle độc lập

## Nguồn và phạm vi

- Base main/C2 merge PR#19: `b2926eaf8ad3d2fe45e1d2fe339263b7486732ac`; main không thay khi fetch cuối trước PR. C1 PR#18 đã merge và nhánh C1 vẫn giữ. Nhánh mới `feat/t2-c3-monitoring-trace-replay`.
- Code/contract/test: `5844e88e91feb04f779acbe4b1b956d96697578a`. CLI cuối chạy trên SHA này với working tree sạch, UTC13:58:59 ngày10/10/2026. `artifacts/metadata.json` ghi source manifest, JAR SHA256, JDK21.0.10+7, Windows11 build26200, machine label ẩn danh, seed1234 và mức bằng chứng. Baseline Maven3.9.15/JDK21/PG18.6 đã dùng sẵn.
- Các regression chạy trước commit khi code còn uncommitted; C1/C2 metadata ghi đúng base HEAD và dirty filenames, không gọi chúng là clean-commit run. Đã đối chiếu toàn bộ Java source hashes và client JAR SHA256 của cả hai với metadata clean C3: giống nhau. Java code không sửa sau build cuối; script cuối chỉ thêm thông tin java-version trước commit.
- Không thay .env, tracker/kế hoạch/nguồn gốc, migration, exam lifecycle, delta/T2-C4/E1/E2. Không reset DB, kill process người dùng, viết benchmark ground truth hoặc dùng thời gian replay làm detection latency. SQL kiểm cuối: TEST schema0, public dev vẫn V7.

## Đã chạy thật

| Kiểm tra | Kết quả | Mức chứng cứ và giới hạn |
|---|---|---|
| Baseline `mvn package` | 425 PASS lúc20:21:36 UTC+7 | Chạy mới trên main/C2, không lấy số cũ |
| Final `mvn package` | **461 PASS**,0 fail/error/skip,20:52:54 UTC+7 | protocol32/client286/server142/spike1; 36 test mới |
| Python analyzer unit | 10 PASS | Hồi quy công cụ C4 cũ; không E1/E2 |
| Fixture + expected viết tay | PASS | MOCK 4 observations empty/Edge42/Edge42/empty; 1 business event, final empty/STALE |
| CLI clean/duplicate100/drop-middle/drop-first/mixed1234 | PASS | REPLAY full/event encoder production + parser/shared C1 reducer; transport/control/ACK/gate SIMULATED |
| Cùng seed1234/config/input chạy hai lần | PASS giống byte | `mixed.json` và `repeat.json`, schedule/ID/state/event/report toàn bộ |
| Full sai cố ý, scan2 xóa Edge khỏi payload | **FAIL đúng**, exit1 | Record3 expected/actual đều ACCEPTED nhưng tập state khác; oracle phát hiện. Bản nguồn không sửa |
| Bản sao trace đổi poll100→101, không đổi checksum | **bị từ chối đúng**, exit2 | `TRACE line 8: CHECKSUM_OR_INCOMPLETE`; không tạo report must-not-exist |
| CLI record6 scans100ms + đọc + replay | COMPLETE/PASS | Nguồn REAL ProcessHandle,8 record; business/delivered44; candidate gate mô phỏng, không GUI/socket |
| Owned Edge capture + stop + read/replay clean | COMPLETE/PASS | REAL Windows ProcessHandle/collector đang có/Edge riêng;5 record (3 scans);46 business/delivered events gồm browser nền sẵn có, không coi46 là46 Edge do test tạo |
| Owned Edge trace, seed1234 dup20/drop10/reorder35/reconnect3 | PASS | REPLAY/SIMULATED;46 business,40 delivered,6 missing được liệt kê; PASS là khớp lịch lỗi, không bảo đảm không mất event |
| C1 `smoke-t2c1.ps1 -Gui` | PASS | REAL Edge/ProcessHandle/PG/HTTP/WS/full/giám thị JavaFX component; duplicate/conflict/empty/reconnect/history; autonomous STALE/TTL/capacity/shutdown không HTTP refresh/manual maintain |
| C2 `smoke-t2c2.ps1 -Gui` | PASS | REAL Spring/PG/HTTP/WS/proctor component; source readings MOCK, held write/disconnect/ACK loss SIMULATED; late metadata/frozen dedup/gap/COMMIT rollback/auth/V7→V8 |
| C3 event `smoke-c3.ps1` | PASS | REAL ProcessHandle/owned Edge/WS/PG/ACK, server stop/restart; overflow MOCK/ACK loss SIMULATED; callback/worker cleanup |
| C4 `smoke-c4.ps1` | PASS | REAL byte/retry/error/cleanup + scan riêng; event/overflow MOCK, ACK suppression SIMULATED; summary COMPLETE |
| A4 `smoke-a4.ps1` | PASS | REAL heartbeat UNKNOWN/recovery/scope/commit rollback/owned JVM kill/cleanup; snapshots MOCK |

CLI cuối: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t2c3.ps1 -JavaHome <JDK21> -RealEdge`. Artifact run `e7f90965-955d-431c-ac91-7e5e2520faa4`; bản gốc còn ở ignored `client/target/t2c3-smoke/`. Ghi log gốc không sửa. Những file lớn được **gzip không mất dữ liệu**, mtime0; giải nén ra đúng original SHA256 trong checksums.json. Các transcript hồi quy/Maven/Python là dòng được trích nguyên văn từ log đầy đủ, không tự viết PASS; manifest ghi hash của cả bản gốc và excerpt.

## Acceptance chi tiết và counterexample

36 test mới: ReplayEngine11, TraceRecorder6, TraceReader13 invocations, ProcessCollectorTrace2, TraceCli3, HandwrittenTrace1. Các test cũ vẫn chạy trong461.

- Roundtrip empty khác SOURCE_FAILURE; source failure giữ last-known và baseline identity, không gửi empty hoặc tạo lại event mỗi poll. Identity: collector/PID/start kể cả thiếu start/PID reuse; start nanosecond giữ trong identity/full, event timestamp microsecond theo production.
- Oracle từ trace gốc, độc lập algorithm encoder/reducer. State kiểm tập khóa + metadata; history kiểm map ID/nội dung, không chỉ số lượng. Coverage baseline đòi1full/mỗi observation và event set đủ; business event khác network recoverable event.
- Duplicate cùng ID/content không tăng event. Drop full giữa epoch rồi full mới cao hơn thay toàn bộ state; drop full đầu thì subsequent full bị INVALID_INPUT/UNSYNCED. Reconnect OPEN mới, held full epoch cũ bị STALE; full không xóa history.
- 100% drop data không tạo gap/UNKNOWN giả; missing business events được công khai. No heartbeat/timeout/TTL in CLI. CLOSE đổi STALE theo control thật của mô hình, không đoán từ im lặng.
- Reader strict bounded: checksum/missing FINAL/missing record/duplicate JSON field/unknown private field/fractional index/bad filename/version/reversed time/CRLF/partial line/trailing FINAL/invalid UTF8/oversized line đều reject. Chỉ giữ safe basename/PID/start/quality/policy/collector và thời gian quan sát, không path/command/user/credential.
- Writer queue full drop-new/record gap → INCOMPLETE; writer I/O error không escape callback; file có sẵn không bị ghi đè; thiếu STOP/repeated close/blocked writer bounded close đều không báo COMPLETE. Tap cố ý throw không phá collector. Ghi đĩa ở worker riêng, queue/file/record/report đều giới hạn.
- Event/store/transport/auth/presence trong CLI có phần mô phỏng rõ. Shared parser/reducer là production; epoch binding adapter và event map headless không phải PostgreSQL hay proof commit ACK. ACK baseline chỉ giúp liệt kê message, không mô phỏng retry/ACK-loss trong mạng gây lỗi. OPEN/CLOSE reliable; drop/reorder chỉ full/event.

## Kiểm tra cuối và phần chưa nghiệm thu

- Diff check PASS, không dependency mới/message mạng/migration/secret ngoài scope. Raw trace/report không chứa full path, command line hoặc credential. Artifact hash + gzip restoration PASS; fixture exact bytes được giữ bằng .gitattributes. Test harness không lọt fat JAR production.
- CLI exploratory và final đều PASS các ca dương; ca âm FAIL/reject đúng. Không có product test FAIL chưa xử lý. Pipeline PowerShell ban đầu làm mất dấu phần tài liệu vừa append; đã sửa bằng UTF-8 và đọc lại trước commit, không làm đổi evidence/trace hay source logic.
- Human B review **NOT RUN**; A review reducer adapter **NOT RUN**. B cần xem collector tap/start-stop, seam ID cho full/event delivery, oracle/schedule và giới hạn mô phỏng. Không giả thành viên đã approve. ChatGPT planning/review **NOT RUN** theo lựa chọn không kết nối đã có của người dùng.
- Full candidate GUI/human end-to-end/LAN/second machine **NOT RUN**. MT01/MT07/MT08 toàn hệ thống giữ PARTIAL; không nâng MT02–MT06 vì chưa delta. C1/C2 proctor component PASS không thay cho GUI cả hệ thống. T2-C4/delta/E1/E2 chưa thực hiện.
- Người dùng ủy quyền push/PR/merge commit khi không blocker và giữ nhánh. SHA docs, PR, merge, main/HEAD cuối ghi trong báo cáo bàn giao và PR; không ghi một merge tương lai là đã thành công trong evidence này.

## Tái tạo từ artifact

Đọc [cách chạy](../../docs/MONITORING_TRACE.md). Dùng `handwritten-v1.jsonl` hoặc `owned-edge.jsonl` để replay mà không cần mở Edge. Report output phải mới. Với report `.json.gz`, dùng Python standard library `gzip.decompress()` rồi kiểm SHA256 của byte giải nén so với `originalSha256`. So `businessEvents`, `deliveredEvents`, `missingBusinessEvents`, `cleanChecks` và `faultChecks`; mỗi check ghi record nguồn, message, expected/actual outcome/state và match/reason. Không dùng replay để tuyên bố process ngắn không bị bỏ sót hoặc delta nhanh hơn.
