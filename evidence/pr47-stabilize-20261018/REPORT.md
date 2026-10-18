# PR #47 — ổn định và tích hợp main, 18/10/2026

**READY_FOR_C_REVIEW — chưa merge.** C chưa xác nhận review; HEAD và CI bàn giao cuối được ghi trong PR và phản hồi của phiên. Không merge khi còn thiếu C approval. Đây là kiểm chứng integration của PR #47, không phải nghiệm thu gate LAN hoặc quyết định FULL/DELTA.

## Git và phạm vi

- [PR #47](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/47), nhánh `gate/t3-c4-lan-20261017`, đã đổi base từ `fix/c-monitoring-audit-20261017` sang `main`.
- HEAD đầu: `5d81aa04925387fe90c3b7ac53aef14f6824b417`.
- Main đã fetch và dùng tích hợp: `f86403489bdd01887b926f2f9e4b72ddf05d1423`.
- Merge commit `167caf312767566417ad55840a008833844e1f15` có hai parent chính xác là HEAD PR47 ban đầu và main trên. Không rebase, cherry-pick hoặc force-push.
- Source runtime/test cuối: `3d938209ff3a4aa0d92dba7fca0e606fbc0b5245`. Các commit hồ sơ sau đó không đổi source, dependency, script hoặc resource chạy.
- Workspace gốc ở `main` có file chưa commit; giữ nguyên. Làm trong checkout Git độc lập tại `target/pr47-stabilize-20261018` (clone dùng chung object chỉ đọc), không sửa worktree cũ.
- Conflict chỉ ở `docs/NHAT_KY.md` và `docs/TIEN_DO.md`; giữ đầy đủ cả mục C4 và evidence A/B/C mới của main. Đối chiếu final diff với main: hai file production phía client, test/harness và hồ sơ C4; không kéo lại code cũ của PR39/46 hoặc server hooks.

## Lỗi tái hiện và sửa

1. CLOSE write không hoàn thành khiến `stopped()` không hoàn thành. Regression trước sửa: `stuckCloseWriteIsBoundedAndCannotWriteAfterNextDeliveryStarts` timeout. Sau sửa, chờ write tối đa bằng `settings.ackTimeout`, dùng future copy để không hoàn thành thay future của transport. Guard riêng vô hiệu hóa CLOSE còn xếp hàng khi hết hạn; worker và listener được dọn. CLOSE vẫn best-effort, không đợi ACK server.
2. Cache correlation đã hủy bị xóa khi nhận phản hồi muộn đầu tiên; phản hồi lặp tiếp theo, đặc biệt ERROR UNAUTHORIZED, có thể làm adapter FAILED. Regression trước sửa thấy CONNECTED thành FAILED. Giữ dấu trong cache tối đa `maxPendingAcks`; retry cùng ID bỏ dấu, reconnect xóa cache. ERROR xác thực toàn phiên không có request ID vẫn làm FAILED đúng hợp đồng.
3. ACK hoặc deadline có thể đã lấy correlation khỏi pending trước `forgetPending`. Regression riêng trước sửa nhận hai thông báo từ chối ACK muộn. Giờ `forgetPending` vẫn ghi dấu hủy khi pending không còn.

Fixture delta cũ giữ mọi guarded send, nên sau khi CLOSE cũng có guard, test chờ 2 giây trong khi CLOSE timeout mặc định 5 giây. Lượt focused đầu sau sửa còn 1 ERROR này; giữ log. Chỉnh fixture chỉ giữ state write cũ, cho CLOSE hoàn thành; test CLOSE treo riêng kiểm cả đang write và còn xếp hàng. Không tăng timeout của test để che lỗi.

## Kiểm thử mới trên source cuối

| Lượt | Kết quả | Tính chất |
|---|---|---|
| Focused client monitoring + RealtimeClient | **148 PASS, 0 SKIP** | MOCK transport/clock; worker/lifecycle thật ở các case tương ứng |
| Riêng RealtimeClient trong focused | **63 PASS** | MOCK socket, không coi là tích hợp server |
| Server monitoring + authenticated network/concurrent send | **118 PASS, 4 SKIP** | Monitoring core/REAL embedded HTTP-WS; 4 PG opt-in chưa bật ở lượt này |
| `mvn --batch-mode --no-transfer-progress clean test` | **928 PASS, 5 SKIP, 0 failure/error** | Oracle JDK 21.0.8, Maven 3.9.11, Windows, JavaFX 21.0.12, PostgreSQL 18.1 TEST riêng |
| `mvn --batch-mode --no-transfer-progress package` | **928 PASS, 5 SKIP, 0 failure/error** | Chạy lại toàn suite, tạo server JAR và client shaded JAR mới |
| PostgreSQL suites trong mỗi full run | **67 PASS, 3 SKIP** | Audio10, Listening15/3skip, Exam38, MonitoringHistory4; đều dùng schema sở hữu riêng |
| Smoke T3-C2 mới | **PASS**, exit 0 | REAL Spring/PG/HTTP/WS/restart, process readings MOCK, fault injection SIMULATED |
| Smoke T3-C4 mới | **42/42 PASS**, exit 0 | Một Windows host, REAL Application/ProcessHandle/Edge/PG/HTTP/WS; fault seams SIMULATED |

Full run: protocol45 PASS; client640 PASS/2 SKIP; server242 PASS/3 SKIP; monitoring-spike1 PASS. Năm SKIP: tạo symlink không đủ quyền; ScoreResultDialog GUI opt-in; Listening GUI, installed client và app-image opt-in. Không sửa MSI hoặc bật các opt-in ngoài phạm vi để tạo số PASS.

Hai lượt clean thử cấu hình ban đầu FAIL vì thiếu `DB_HOST`, rồi cổng 5433 trong `.env` cũ không có server. Giữ log, không tính là lỗi source hoặc PASS integration. Sau đó tạo cluster PostgreSQL 18.1 TEST riêng trên `127.0.0.1:55447`; chỉ cấu hình `.env` ignored của checkout riêng, không đổi `.env` gốc hay restart DB dùng chung. Hai lượt full sau đó PASS như bảng.

Lượt focused incremental cũ có 147 case trước khi clean; không dùng nó thay lượt 148 case sau build sạch. Các dòng `MEASUREMENT_WRITER_FAILED` và SQL rollback ERROR trong log thuộc fault case có assertion, không phải kết quả suite FAIL.

## C review đúng phần production

- [FullSnapshotDelivery.java — owner C](../../client/src/main/java/vn/edu/toeic/client/monitoring/FullSnapshotDelivery.java): tách worker termination khỏi hoàn tất stop; hạn chờ CLOSE; guard hủy write xếp hàng; generation/epoch; giải phóng correlation khi write thành công, lỗi hoặc timeout.
- [RealtimeClient.java — owner B](../../client/src/main/java/vn/edu/toeic/client/realtime/RealtimeClient.java): cache hủy có giới hạn, retry/reconnect cleanup; lọc ACK/ERROR/RESYNC muộn lặp; giữ xử lý UNAUTHORIZED toàn phiên.
- Regression: FullSnapshotDeliveryTest, RealtimeClientTest và một dòng điều chỉnh fixture DeltaSnapshotDeliveryTest. C cần xem phần C; B cần xem adapter thuộc B. Không thay review owner bằng kết quả agent.
- Đối chiếu [T3-A3 server hooks](../../docs/MONITORING_SERVER_HOOKS.md): auth/scope trước dedup; state RAM; event/gap commit-before-ACK; restart cần epoch/OPEN/full mới. Không sửa hook/reducer production phía A/C hoặc wire contract.

## Evidence và giới hạn

[runs.json](runs.json) sinh từ Surefire XML và exit code thật; chứa hash log gốc, bản công bố và lý do SKIP. Log native có UTF-16 hoặc Windows-1252; bản công bố chuyển UTF-8/LF, thay prefix workspace/user. Với Maven full/server, chỉ công bố dòng tổng kết suite/build; bản đầy đủ, XML từng lượt và raw smoke giữ nguyên trong thư mục ignored. Không sửa raw lịch sử hoặc lấy fixture MOCK/SIMULATED làm bằng chứng production PASS.

CI source runtime `3d93820`: [PR CI run 39092734121 — SUCCESS](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39092734121). CI cho HEAD bàn giao sau commit hồ sơ: xem [Checks của PR #47](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/47/checks); URL/kết quả cuối được ghi trong PR và phản hồi của phiên.

LAN hai máy, human C/B review, E1/E2, load và installer: **NOT RUN** trong phiên này. FULL tiếp tục mặc định, DELTA thử nghiệm; không cập nhật QD owner approval, TRACKER hoặc trạng thái nghiệm thu task. Không đóng PR41/42/43/45. Không phát hiện blocker production trong phạm vi đã kiểm; thiếu C approval là điều kiện dừng merge. Trạng thái **READY_FOR_C_REVIEW**, chưa merge.

Hai smoke mới: [run ID, JAR/checksum và phạm vi](smokes.json), [42 assertion T3-C4](smoke-t3c4-report.json). C4 run `T3C4-230e2b78-8b0c-4166-a5b4-8e39ca62453e`; C2 run `T3C2-fcc7759e-b7ce-431c-b14f-a4462afb632e`. C4 measurement **INCOMPLETE** do case hard-kill có chủ đích; raw giữ nguyên, không dùng để so hiệu năng.

Hậu kiểm [cleanup.json](cleanup.json): 0 schema TEST ngoài mặc định, cluster PostgreSQL do phiên sở hữu đã dừng, `.env` của checkout riêng đã phục hồi. Không restart service hoặc sửa dữ liệu dùng chung.
