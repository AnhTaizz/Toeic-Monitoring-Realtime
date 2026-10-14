# Kiểm chứng tích hợp Reading/Listening/history — 15/10/2026

**PARTIAL cấp nghiệm thu toàn hệ thống; integration gate local PASS, sẵn sàng Draft PR cho A/B/C review.** [Draft PR #35](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/35) đã tạo trên main8f5ac40; không merge main. Human review, LAN hai máy, rút mạng/kill app thủ công, installer và E1/E2/load NOT RUN. ID ACTIVE đặc biệt còn contract QD29; code từ chối mở bài không giám sát.

## Source và môi trường

- Branch `integration/reading-listening-20261015`, worktree `target/integration-reading-listening-20261015/worktree` trong workspace gốc.
- Base main `8f5ac40e6b4493d6a8e81084b735447d92da8a13`, fetch lại vẫn cùng SHA. PR34 đã merge; PR17/28 giữ nguyên, không chỉnh nhánh người khác.
- Merge history PR28 `2ca4121d4aaf184ec4ee600d351131a7ef0493af` tại81ae662. Reading lấy từng file từ local65f5123 (kế thừa d425182), không merge toàn PR17/nhánh cũ. Source9115776; runtime sửa visual-lock387ed99; GUI harness cuối d809a00. Commit bàn giao sau đó chỉ docs/evidence, không gán SHA docs làm SHA đã chạy test.
- Windows11 x64 **10.0.26200.9457**, Oracle **JDK21.0.8**, JavaFX21.0.12, Maven3.9.11, PostgreSQL**18.6** Docker. V1–V10 migrate trong schema owned; không reset public/volume. DB dùng cấu hình hiện có, credentials chỉ env.
- [Metadata](metadata.json), [source byte manifest](source-files.json), [JAR hashes](artifact-hashes.json), [XML counts tổng hợp](test-counts.json), [raw→derived provenance](log-provenance.json). Raw đầy đủ/SQL/XML/native server logs/audio/journal giữ local tại thư mục `raw/` cạnh worktree; đường dẫn từng run và SHA trong provenance.

## Thay đổi thực tế

| Phần | Thay đổi và lý do |
|---|---|
| Server/protocol A | History theo PR28: owner-only, keyset UTF8/C,7field DTO. Alias GET `/attempts/status?attemptId=` đọc slash/dot an toàn bằng query; kiểm principal/quyền như route cũ, một parameter, no-store, không nới Tomcat. Không migration hay thay save/submit/timeout transaction. |
| Client B | Reading/history mới, cùng Application với pipeline Listening B1–B3 từ main. CandidateExamView/ExamSession là owner writer; audio nhận epoch, không takeover thứ hai. Status authoritative, callback generation/close guards, worker/subscription/Media cleanup. |
| Listening/answers | Server-interrupted dừng player; model giữ Listening theo bản đã commit và giữ Reading local với revision không giảm. Reconnect/history không autoplay hoặc acquire writer. Playback updates khóa nút prepare; Media onReady bỏ callback cũ. |
| Scope/monitoring A/B/C | Scope HTTP raw không làm login/history sập; heartbeat/envelope v0 vẫn ASCII. UI từ chối entry ID không tương thích monitoring. FULL vẫn mặc định; collector/event/gap/retry/epoch/reducer production không viết lại. |

Conflict history chỉ NHAT_KY/QUYET_DINH/TIEN_DO, giữ cả hai phần. Application compose chủ đích, không lấy bản Reading cũ ghi đè audio main. QD28 giải thích writer/lifecycle; QD29 ghi contract pending. Tài liệu nguồn/kế hoạch/tracker/.env/migration/nhánh/worktree khác giữ nguyên. [Guide](../../docs/INTEGRATION_READING_LISTENING.md).

## Kiểm thử đã chạy thật

| Command hoặc harness | Source/mức | Kết quả mới | Bằng chứng |
|---|---|---|---|
| `mvn -B -ntp -o clean test` |9115776, PostgreSQL bật |**833 PASS,0fail/error/skip** |[Clean output](maven-clean-test.txt) |
| `mvn -B -ntp -o package` |387ed99 sau visual-fix, PostgreSQL bật |**833 PASS,0fail/error/skip**, JARs thật |[Final package](maven-package-final.txt), hashes/counts |
| Targeted client |MOCK gateway/callback và file/HTTP worker thật theo test |142 PASS sau sửa assertions URL |Raw exploratory retained, không cộng142 vào833 |
| Runtime PostgreSQL subset |29exam/history +10audio +10Listening; Spring/HTTP/PG/WS/Flyway thật |49 PASS, subset của833 |Source test names/counts trong final package |
| `ReadingListeningPostgresGuiSmoke` |d809a00, exact final JAR hashes; Application/Media/JAR/PG/HTTP/WS thật |**8 nhóm PASS**,6ảnh; cleanup owned server/schema |[GUI stdout](gui-production.txt), [result](gui-result.json), [metadata](gui-metadata.json) |
| `ExamFlowPostgresSmoke` |d809a00 + final JAR, HTTP/PG/owner worker thật; mất response/network wrap SIMULATED |**28 PASS,0 GAP** |[Final flow](exam-flow-final.txt), [metadata](exam-flow-metadata.json) |
| `ExamConcurrencyPostgresSmoke` |d809a00/final server, PostgreSQL locks/takeover thật |AT01/07/09/10 kỹ thuật PASS |[Concurrency](concurrency-postgresql.txt) |
| `FullSnapshotSmoke` |d809a00/final JAR, worker/Spring/PG/WS/owned Edge/ProcessHandle thật; faults MOCK |FULL/reconnect/epoch/autoSTALE/TTL/capacity/cleanup PASS |[Full](monitoring-full.txt) |
| `LateEventsAndGapsSmoke` |d809a00/final JAR, Spring/PG/HTTP/WS/worker thật; readings MOCK/loss SIMULATED |UNKNOWN/recovery/late/dedupe/conflict/gap/permissions/cleanup PASS |[Late/gap](monitoring-late-gap.txt) |

833 = protocol43 + client569 + server220 + spike1. Không coi tất cả833 là production/GUI; unit fixtures có MOCK. Không cộng runtime subset hoặc repeats thành số test khác. Source387ed99 là runtime được package; d809a00 chỉ sửa harness assertion theo nhãn UI và capture lỗi. Core source/API của28-flow giữ tương thích, nhưng flow cuối được chạy lại trên đúng final JAR hashes để không kế thừa số nhánh cũ. Hai device trong flow là2model cùng máy/JVM, không phải hai máy LAN.

GUI thực tế: Reading autosave và timer; server restart/reconnect giữ epoch; submit/final/history chỉ đọc; mở mixed attempt; audio SHA256/READY với cùng writer; Media WAV thật và STARTED, Reading autosave trong lúc phát; start lặp; restart server giữa audio → Interrupted, không resume/replay, Listening bị khóa nhưng Reading lưu tiếp; proctor chọn roster row và nhãn quan sát đã đồng bộ, HTTP trạng thái chính xác SYNCED. Xác nhận nghe bằng click tự động là **SIMULATED**, không human hearing. Đề/WAV là dữ liệu TEST đi qua backend thật.

Ảnh cuối: [Reading](screenshots/01-reading-saved.png), [submit](screenshots/02-reading-submitted.png), [history](screenshots/03-history.png), [coexistence](screenshots/04-reading-listening.png), [interruption](screenshots/05-interrupted.png), [proctor](screenshots/06-proctor.png). Không thay GUI manual hoặc IT01/LAN.

## Các lượt lỗi được giữ

- PS native runner treo; cmd quoting lần đầu exit255: môi trường/wrapper, không test PASS. Raw local giữ nguyên; runner chuyển nativecmd, không sửa credentials/cấu hình OS.
- Targeted đầu FAIL5: assertions vẫn yêu cầu path cho slash/dot, trong khi sửa chọn query alias. Assertions mới kiểm exact safe query round-trip/fixed origin/auth;142PASS mới, không xóa test.
- [GUI seed readiness FAIL](failed-gui-seed-readiness.txt): cổng HTTP mở trước seed transaction commit. Harness chờ count3users trong schema owned, không đổi auth hoặc giả login.
- GUI ban đầu PASS8 limited checks; visual QA thấy nút prepare chưa theo playback và ảnh proctor loading. Sửa runtime387ed99 rồi package833PASS lại; siết roster/state UI. [Literal status label FAIL](failed-gui-literal-status-label.txt): UI đang dùng nhãn quan sát, không literal SYNCED. d809a00 kiểm nhãn quan sát thực + exact HTTP SYNCED; final8PASS, không đổi UI để né test.
- [C1 driver thiếu heartbeatflag FAIL](failed-full-missing-heartbeat-flag.txt): default2s lớn hơn presence800ms của smoke. Dùng heartbeat200ms như script đã xác minh, rồi C1PASS; không thay heartbeat/timeout production hoặc source C.

Raw không chỉnh tay; bản public là excerpt/copy UTF-8/LF có provenance SHA và thay prefix private khi cần. LF công khai được chuẩn hóa riêng để SHA khớp blob Git, không sửa raw gốc. Không dùng776/669 của các nhánh riêng làm PASS của nhánh này. Credential/token audit trước commit:0match actual secret values (không ghi giá trị vào report).

## Còn thiếu và owner review

| Mức | Việc | Owner |
|---|---|---|
| P0 |Không còn blocker đã chứng minh ở luồng ASCII/mixed local đã kiểm; không tuyên bố mọi môi trường đã nghiệm thu |A/B/C review |
| P1 |ID ACTIVE có slash/space/Unicode vượt monitoring-v0: history/query status đọc được; UI từ chối vào thi không giám sát. Đường ghi/audio slash chưa đổi; cần quyết định QD29 |A/B/C |
| P1 nghiệm thu |Human A/B/C, rút mạng/kill app/thử nghe, LAN hai máy, gate delta20/10 |Nhóm |
| P2 |sessionId+username quá128 có thể500+rollback; sai HTTP method còn500; roster selected-row contrast cần B review |A/B |

Reading/Listening/History **READY cho review local**; Monitoring FULL **READY ở phần kỹ thuật đã kiểm**, cả MT/LT/IT/LAN vẫn **PARTIAL**. Integration **PARTIAL cấp toàn hệ thống**, source/evidence đủ Draft PR. Python unit không chạy lại (source không đổi); E1/E2/load/installer/MP3-M4A NOT RUN. Không đổi status27case/TRACKER hay bật delta production. Không merge main nếu chưa được người dùng chấp thuận.
