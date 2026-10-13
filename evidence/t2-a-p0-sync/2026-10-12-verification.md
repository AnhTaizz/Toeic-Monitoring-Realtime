# Backend A — đồng bộ main T3-C1, 12/10/2026

## Nguồn và phạm vi

- Nhánh A: `fix/backend-exam-p0-20261011`, worktree riêng `Toeic-Monitoring-Realtime-a-p0`.
- Fetch đầu/cuối: main `144fe559c5cf2d5a9608d3cf804cd2adb22bca70`, remote A `6cfe7c4b46ee550497e33591b891f214e8df08b3`, PR17 `2f5fc66d1220f1e860f7c3beced86ef3f294ac46`.
- Merge commit production đã kiểm: **`cadd98d1a49cb5b4eb0f22f68f346dfd682386c9`**, parents A6cfe7c4 + main144fe55. Không rebase/force-push hoặc merge main/PR17.
- Maven test/package và C1/C2/CLI chạy code sau merge cadd98d. Sau kiểm thử chỉ thêm tài liệu/evidence và rule giữ byte cho thư mục evidence mới; Java giữ nguyên, có [manifest](a-source-hashes.json).
- Checkout tích hợp tạm mới `Toeic-Monitoring-Realtime-ab-p0-sync-20261012`, detached từ PR17; merge A/main và áp fixture test-only cũ. HEAD build/smoke **`59404d69daed0c2261169809ba237f36ee8b6c50`**, [manifest](integrated-source-hashes.json). Không sửa nhánh B, B4 hoặc checkout tích hợp cũ.

## Conflict đã giải quyết

| File | Cách giải quyết |
|---|---|
| `.gitattributes` | Giữ cả rule bảo toàn byte evidence A và T3-C1 C; rule mới cho evidence sync được thêm riêng sau test |
| `docs/PROTOCOL.md` | Giữ sửa P0 (read history, takeover deadline, owned exam scheduler) và contract delta candidate/gate/freshness của C, cùng các cập nhật C tự merge |
| `docs/QUYET_DINH.md` | Giữ QD-16 delta của C và QD-18 deadline của A; giữ nguyên nội dung các quyết định |
| `docs/KIEM_THU.md` | Giữ evidence P0 A và evidence T3-C1 C, phân biệt kết quả lịch sử với lượt sync này |

[Patch resolution](merge-resolution.patch). Không có conflict code. Client/protocol/scripts của A trùng origin/main; server production giữ nguyên sửa P0 đã kiểm trên A6cfe7c4. Encoder, transport, test27/8/3 của C không bị xóa/vô hiệu hóa. 60 artifact C và 26 artifact A cũ giữ nguyên checksum. Production vẫn full; không bật delta hoặc sửa server delta/freshness.

QD-16 writerEpoch của **B trên PR17** trùng số QD-16 delta của **C trên main**. Đã làm rõ chủ sở hữu trong [review A/B](../../docs/REVIEW_A_PR17.md), không tự renumber hoặc đổi hợp đồng. Khi B tích hợp tài liệu cần phối hợp mã riêng. Checkout detached giữ nội dung B/C/A, chỉ conflict tài liệu KIEM_THU/NHAT_KY/QUYET_DINH/TIEN_DO; không có conflict code.

## Tests thực sự chạy lại

Windows/WSL, JDK21.0.8, Maven3.9.11; **PostgreSQL18.1 native Windows**, cluster test sở hữu riêng, loopback56261, DB p0_test. Credential chỉ qua môi trường, không đọc/reset DB dev hoặc đóng gói secret. Mỗi runtime/smoke tạo schema ngẫu nhiên và dọn đúng schema sở hữu.

| Kiểm tra | Kết quả | Evidence |
|---|---|---|
| A `mvn -B test` | **548 PASS**, 0failure/error/skipped | [log](mvn-test.log), [counts](a-test-counts.json) |
| A `mvn -B package` | **548 PASS**, 5modules build/package thành công, 0failure/error/skipped | [log](mvn-package.log), [counts](a-package-counts.json) |
| Detached PR17 + A/main `mvn -B package` | **620 PASS**, 0failure/error/skipped | [log](mvn-integrated-package.log), [counts](integrated-test-counts.json) |
| ExamRuntimePostgresTest | **11 PASS trong mỗi lần Maven trên**, production Spring/HTTP/PG và scheduler tự chạy | Logs Maven + [source](../../server/src/test/java/vn/edu/toeic/server/exam/ExamRuntimePostgresTest.java) |
| C regression mới | **38 PASS** = payload3 + delivery27 + replay8, có trong cả ba Maven runs | Logs Maven; không bỏ/skip test C |
| B ExamFlowPostgresSmoke | **28 PASS, 0 GAP**, JAR server mới + HTTP/PostgreSQL/ExamEntry/ExamSession | [log](b-exam-flow-smoke.log), [audit](smoke-audit.json) |
| C1 FullSnapshotSmoke | **PASS**: real ProcessHandle/owned Edge/full/ACK/WS/PG, epoch/reconnect/dedup/scope/history, autonomous STALE/TTL/capacity và shutdown | [log](c1-smoke.log); heartbeat TEST200ms cho presence TEST1200ms |
| C2 LateEventsAndGapsSmoke | **PASS**: real HTTP/WS/PG/collector-worker; readings MOCK, outage/write/ACK-loss SIMULATED | [log](c2-smoke.log) |
| T3-C1 CLI replay | **PASS**: 11replay commands có đúng expectedexit; 3 cross-mode comparisons; real ProcessHandle6scans | [log](delta-cli.log), [artifacts](delta-artifacts.json), [runner](run-delta-cli.py) |
| Python analyzer | **10 PASS** | [log](python-tests.log) |

548 = protocol35/client353/server159/spike1; 620 = protocol35/client425/server159/spike1. Tăng đúng38 so với A510/integration582 trước merge T3-C1. 11PG đã tính trong totals, không cộng thêm lần nữa. Bộ PG chỉ chạy khi `TOEIC_PG_INTEGRATION=true`; không bật thì không được báo PG PASS.

C CLI dùng cùng tham số/comparator của smoke-t3c1.ps1, runner riêng để tạo output mới và metadata Git đúng trong worktree WSL. Handwritten full/delta và dup100/reconnect-at3 cùng canonical processes/metadata và event IDs/content. Cố ý full/delta sai trả exit1; unsupported delta fault/TESTschema trả exit2 (kết quả âm mong đợi). Legacy full faults vẫn PASS. Capture ProcessHandle REAL nhưng replay ACK/network/epoch/adapter đều SIMULATED; không phải live delta, gate/benchmark hoặc E1/E2. Raw artifacts lớn gzip mtime0, có hash byte nén và hash bytes giải nén; không sửa report/raw.

[Packaged server hashes](packaged-server-hashes.json) xác nhận **88 production entries** trong server JAR A và checkout tích hợp trùng byte; raw JAR hash có thể khác timestamp/package metadata. [Metadata](metadata.json) ghi chính xác SHA khi build, không gán log runtime cho commit docs về sau.

## Ba GAP cũ và deadline bài trắng

| Tình huống | Assertion/log sau sync |
|---|---|
| GAP GET status SUBMITTED403 | Smoke success branch kiểm SUBMITTED, correctCount2 và answers không đổi; ExamEntry.open trả Finished. Hai PASS lines có trong log. PG kiểm owner/assigned proctor đọc ACTIVE/SUBMITTED/TIMED_OUT, foreign/revoked403/unauth401 |
| GAP scheduler không chốt | Smoke FINAL phải TIMED_OUT, correctCount1 từ revision đã ACK và thông báo phần chưa xác nhận không được tính. PG test tự đợi server chuyển trạng thái, không gọi processTimeouts() |
| GAP nộp trễ không có kết quả | Smoke vẫn thực sự gửi submit trễ, assert không báo SUBMITTED và TIMED_OUT khi có result; log ghi phase FINAL. Các nhánh AWAITING_SERVER/ENDED báo GAP vẫn giữ trong source |
| P0 bài chưa trả lời không có deadline | Smoke kiểm deadline !=null và bằng GET status trước autosave. PG unansweredAttemptTimesOutByAutonomousRuntimeScheduler chỉ takeover/poll status, không save/submit/manual job: TIMED_OUT, revision0, answers{}, score0/2, timestamp và đọc final PASS |

Đã đối chiếu source PR17 với fixture tạm: toàn bộ assertion/failure/GAP branches cho final status, timeout và submit trễ giữ nguyên; chỉ chờ confirmedRevision1 thay vì nhận nhầm initial SAVED/revision0. [Fixture cũ tái dùng](../t2-a-p0/pr17-smoke-fixture.patch) cập nhật kỳ vọng deadline khi entry và dọn schema qua JDBC cùng DB.

28PASS không tăng vì success paths thêm2assertion final-status/reopen +1TIMED_OUT/scoring, trong khi3diagnostic PASS của broken-server paths (2expired-yet-ACTIVE,1awaiting-server message) không còn là luồng chạy. Diagnostic branches không bị xóa/skip; cả3flows đều chạy và kết thúc đúng. Bài trắng còn được kiểm riêng ở cả3Maven runs, không suy ra từ smoke có đáp án.

PostgreSQL race chứng minh **hai query FOR UPDATE thật cùng chờ lock**: HTTP submit và autonomous scheduler, xác minh pg_stat_activity; giữ lock qua deadline thật rồi release. Submit bị409EXPIRED/INVALID_STATE, scheduler chốt chỉ answers đã xác nhận, submit record0. Retry/idempotency/late-write/final immutability và một deadline cho concurrent entry/reconnect/takeover đều PASS.

## Scheduler exam và monitoring

C1 source kiểm chỉ1ScheduledAnnotationBeanPostProcessor/1annotation job exam và bean examTimeoutScheduler riêng; các check phải đúng để smoke exit0. Socket/scoped heartbeat tiếp tục sống khi full ngừng: C tự push STALE, tự TTL4000ms và nhả capacity1, không manual maintain/HTTP refresh. Không nới timeout production. C2 tự UNKNOWN/reconnect/recovery và lịch sử không đổi. Runtime PG close xác nhận exam-timeout, presence-timeout và state-maintenance workers đều dừng; unit kiểm scheduler disable/shutdown và monitoring lifecycle vẫn chạy. Không thêm scheduler thứ hai cho job C.

## Cleanup, giới hạn và chạy lại

[Trước](pg-before.log) và [sau](pg-after.log): schema TEST còn lại0, publictables0; cuối lượt clientconnections0. [Stop](pg-stop.log): dừng đúng cluster test đã khởi động, giữ nguyên data directory/credential files/worktrees/logs. Không reset DB thật, không prune/xóa worktree hay file local của nhóm.

**NOT RUN:** human review, full candidate→proctor GUI, GUI server-off/reconnect, LAN, app-image/audio máy thứ hai, live delta negotiation/server reducer/resync/freshness gate, E1/E2/benchmark. T2-B3/B4 vẫn PARTIAL; không kết luận nghiệm thu hoặc merge PR17.

Để chạy lại: dùng DB PostgreSQL TEST với quyền tạo/xóa schema; cấp DB_HOST/PORT/NAME/USER/PASSWORD và TOEIC_SEED_PASSWORD qua môi trường, bật TOEIC_PG_INTEGRATION=true, rồi `mvn -B test` và `mvn -B package`. Không dùng schema public/dev hoặc Flyway clean. [Hướng dẫn cũ đầy đủ](../t2-a-p0/2026-10-11-verification.md#chạy-lại-postgresql-regression-và-smoke-b). Với smoke B: checkout **tạm** từ PR17, merge A đã sync, giải quyết docs giữ các phía, áp fixture patch, package, chạy ExamFlowPostgresSmoke từ root với client test/classes, protocol classes và JDBC/Gson dependencies. C1/C2 dùng classpath server tests + client/protocol; C1 thêm `-Dtoeic.realtime.heartbeatMillis=200`. CLI dùng `scripts/smoke-t3c1.ps1 -JavaHome <JDK21> -RealScan` hoặc runner đã ghi; không cần DB.

Toàn bộ kiểm thử yêu cầu đã PASS trên local. **Chưa push; chưa sửa PR23/PR17 remote; chờ người dùng duyệt SHA và cho phép push thường.**
