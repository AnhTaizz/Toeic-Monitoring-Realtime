# Kiểm chứng ứng dụng thống nhất — 16/10/2026

Source tích hợp: `24f70bd49e63ed6e768fcc6d70206cd13799330d`; checkpoint kiểm cuối: `b755646c6e540bc82808e67e3080ffc0cfb2d53b`. Commit sau merge chỉ sửa hai TEST harness C1/C2 (null-safe cleanup, chờ FX20s). Production Java/POM/scripts không đổi giữa hai SHA; [source audit](source-audit.json), [manifest source](source-manifest.json), [từng conflict](conflict-resolution.md). Commit hồ sơ bàn giao sau checkpoint không thay code; HEAD/URL Draft được ghi tại publication.

Nhánh mới `integrate/unified-reading-listening-b4-b5-20261015`, tạo từ origin/main `c0fd9036ba340b0b0a686fb2ce637050b9a3b7b4`. PR35 head `11c5da9ad3094a103cd6db93693aa64bbb132eaa`; PR38 head `6d09d80263b567f05c67043bc423165eadd4026d`. Merge-base main với hai PR là `8f5ac40e6b4493d6a8e81084b735447d92da8a13`; #35 là ancestor của #38. #36/#37 đã merged; #35/#38 vẫn Draft khi fetch. Merge35 checkpoint `456583b`, merge38 tại24f70bd; không dùng ours/theirs hàng loạt. Main local ban đầu `6a41031` và 5 file/thư mục untracked được giữ. Worktree cũ giữ nguyên; chỉ pointer worktree mới đổi tương đối để Git Windows/WSL cùng đọc được. Không reset/clean/rebase/force-push nhánh cũ.

## Kết quả chạy mới

| Kiểm | Source / điều kiện | Kết quả thật |
|---|---|---|
| Maven `-o -B -ntp clean test` cuối | b755646, PG=true, proctorGUI=true, app-image opt-in | **872 PASS, 1 skip**, 0 failure/error; tổng873. protocol44/client600/server227PASS+1skip/spike1. |
| Maven `-o -B -ntp package` cuối | Cùng source và opt-in | **872 PASS, 1 skip**, 0 failure/error; JAR mới đã tạo. |
| Clean test lượt03 | 24f70bd, chưa bật package opt-in | 871 PASS, 2skip. Không cộng các lượt lặp thành test mới. |
| PostgreSQL18.6 | Suite server và schema TEST riêng, Flyway thật | validation/QD29, auth, finalized/raw history/query alias, committed progress, khóa/revision/Listening regression PASS. Không reset shared DB/volume. |
| B4/LT04 | `proctorControlsLt04RealGuiBeforeAndAfterAutomaticTimeout` | GUI/API thật; tổ chức lại, quyền, old answers/deadline/result trước/sau timeout PASS subset. |
| Shared writer/AT11 | ExamFlow trên JAR24f70bd, production source bằng b755646 | **41 checks PASS, 0 GAP**; exact40/42, lost send/lost ACK, same writer reconnect, takeover+late ACK. Loss seam SIMULATED; HTTP/PG thật. |
| Concurrency AT01/07/09/10 | JAR thật, PostgreSQL, latch/lock | PASS; owner/foreign, quyết định sau khóa/deadline/takeover. |
| GUI toàn Application | Application/Media/JAR/PG/HTTP/WS/server restart thật | **11 nhóm PASS**: Reading/save/submit/history, committed progress, B4READY/start/reorg, shared writer Listening, interruption/no replay, FULL roster/state. TEST exam/WAV; speaker confirmation SIMULATED. |
| Progress regression | Unit controller5 + backend read tests trong suite + GUI | Confirmed page/cursor/readAt, error giữ trang+stale, 401, close, MVCC không đọc uncommitted/no row lock, finals/scope/whitelist/count PASS. Unit faults/pages MOCK. |
| Package preflight | Pipeline MSI main, không chạy installer | 4parser PASS,5negative PASS,1validconfig PASS; 2negative REAL,3MOCK (wrong arch/missingruntime/oversize). Tool probe thiếu WiX/SDK thật, không lấy fixture missing-wix làm proof toolchain. |
| Native Audio QA | Raw ASCII app-image source24f70bd, bundled Oracle21.0.8 | **8 checks PASS**; JavaFX Media/WAV thật; API/WS MOCK, hearing SIMULATED. Cache/audio Unicode có thử, native launcher dưới parent Unicode chưa thử. |
| Production EXE + PG | External TEST agent ngoài JAR/MSI, host JAVA_HOME/PATH-JDK bỏ khỏi child | PASS trong targeted01 và hai suite cuối: B4, FULL SYNCED, READY/playback, writer2 chung, Reading save trong/sau Listening interrupted, frozen Listening, reconnect/normalclose/worker cleanup. Candidate2 READY là MOCK attestation qua API thật; socket close SIMULATED fault. **APP_IMAGE_INSTALL_NOT_RUN**. |
| C2 + GUI | Spring/PG/HTTP/WS/collector worker thật | PASS; readings MOCK, disconnect/write/ACK loss SIMULATED. Raw summary COMPLETE:6endpoints/64categories; không dùng làm E2/performance. |
| C1 + GUI riêng | b755646, owned Edge, lượt cuối không bật JFR | **PASS** `c1-final-01`: Edge thật/full/epoch/reconnect/autoSTALE/TTL/capacity/history/shutdown. Diagnostic JFR cũng PASS; raw summary lượt thường COMPLETE:6endpoints/64categories ([CSV](c1-final-summary.csv)). Lượt01/02/03 vẫn giữ FAIL (Stage null, rồi profile cleanup FileSystemException); nguyên nhân transient ban đầu chưa chứng minh dứt điểm, C review độ ổn định harness. Không đổi policy/assertion để ép PASS. |
| E1 dry/short smoke mới | Source b755646; suite `E1-a90b5fa3-9b35-470b-8c38-431fbca2d429` | Dry VALID; SMOKE COMPLETE/resourcesCleaned=true, 3trial/3detected, poll500ms, warmup1s+measure15s. **Không E1 final/E2/gate delta**. |
| Python analyzer | `scripts/benchmarks/e1/test_analyze.py` | 4 tests PASS, analyzer không đổi so #38. |
| MSI / install / uninstall / Unicode parent EXE | Windows SDK mt.exe và WiX3 không có trong môi trường | **BLOCKED / NOT RUN** trên bản ghép; không kế thừa MSI PASS của #37. |

[Counts từng class và từng lượt](test-counts.json), [log local SHA/start/end/command](local-log-provenance.json), [JAR/EXE/runtime hashes](artifact-provenance.json), [kết quả GUI](gui-result.json), [Audio8](audio-package-results.txt), [E1 smoke](e1-smoke-provenance.json). Logs/XML/JFR/raw/binary gốc ở ignored `jpackage-out/unified-20261015/`, không đưa credentials/process metadata lên Git. App-image QA chứa TEST audio launcher, không phải MSI release. Production JAR không có TEST class/agent; bytecode class giữa JAR final và JAR trong image đã đối chiếu bằng nhau, không chỉ tin tên file.

## Failure và giới hạn giữ lại

Targeted client01:52test/1FAIL do queue test READY cũ; sửa helper, targeted02:56PASS. TargetedPG01:1FAIL do helper xác nhận dialog đánh giá predicate hai lần; sửa, lượt02:55PASS/2skip. Đây là trước freeze, không dùng thay suite cuối. Clean01: runner phục hồi thiếu DB_HOST; clean02: Docker chưa chạy,3PG init errors; khôi phục env test và start đúng container toeic-db/volume cũ rồi clean03/04 PASS. Rawimage01: PowerShell nhận native tool không đúng PATHEXT, jar chạy nhưng không nhận exit; probe chuẩn hóa process PATHEXT xác nhận, không sửa package script. Rawimage02 tạo image rồi dừng đúng SDK manifest gate; QA ASCII sau đó chạy riêng. Log FAIL/probe không bị ghi đè, không đổi chúng thành PASS.

C1 lượt01/02/03 giữ FAIL riêng, cleanup profile có FileSystemException; probe sau lượt02 thấy0owned browser/0readonly entry và dọn được đúng1profile do test tạo. Không suy từ cleanup này thành toàn bộ C1 assertions PASS hoặc kết luận chắc nguyên nhân OS. Lượt c1-diagnostic-01 với JFR và c1-final-01 không JFR sau đó đều PASS toàn bộ, gồm autonomous STALE/TTL. Human C vẫn cần review độ ổn định của harness/GUI wait/cleanup; không tuyên bố nguyên nhân ban đầu đã được chứng minh. JFR diagnostic không là E1 hoặc benchmark. Export JSON quá lớn đã dừng chủ động sau khi capture và C1 thường hoàn tất; JFR gốc giữ riêng, partial JSON không dùng làm proof.

## E1 lịch sử bất biến

Hash đủ84file của suite lịch sử `E1-2ff52082-3ead-4bbb-9963-10e9efe07b7e` trước hòa và sau kiểm: **không đổi**; [danh sách SHA](historical-e1-integrity.json). Analyzer chạy vào thư mục derived mới,14CSV byte-identical. Không sửa raw, checksums hoặc số360/287/62/11 lịch sử; không gọi chúng là phép đo trên bản ghép. Collector/harness/policy đo E1 bằng #38; smoke mới có runId và SHA riêng. E1 full12 trên bản thống nhất **NOT RUN**, phương pháp/human C review vẫn pending.

## Nghiệm thu thủ công / owner review

- A: backend cấp ID/QD29, collision/final immutability, progress authorization/MVCC/count/lastSavedAt và writerEpoch/transaction; QD29 vẫn **PROPOSED, NOT HUMAN APPROVED**.
- B: UI duy nhất, controls retry/READY5s/generation, progress stale/pagination, shared writer, runtime URL/config/file chooser, Media và MSI pipeline #37. Không có controller #38 hoặc demo seeder chạy song song.
- C: default FULL/epoch/presence hooks, C1 initial FAIL/cleanup/FX harness và final PASS, E1 methodology/oracle/identity/resource metrics và historical integrity. Không bật delta production.
- [Ma trận manual](../../docs/ACCEPTANCE_B_MANUAL_MATRIX.md): người nghe thật, rút cáp/kill app thật, hai candidate GUI/LAN hai máy, installer/quyền install/cài-gỡ máy không IDE, MP3/M4A, E2/load và review A/B/C đều **NOT RUN** hoặc PARTIAL như evidence gốc; không ký thay reviewer, không sửa tracker/task acceptance.

Không merge main hoặc đóng PR cũ. Draft mới nhắm main chỉ phục vụ review A/B/C; xem [publication](publication.md) để biết HEAD công bố. Kết quả tổng thể **PARTIAL**, không tuyên bố ứng dụng/MSI đã nghiệm thu toàn hệ thống.
