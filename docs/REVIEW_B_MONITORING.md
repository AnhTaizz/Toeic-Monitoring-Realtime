# B review bàn giao C1–C5, 11/10/2026

Reviewer: **Codex Agent phụ trách Vai B theo yêu cầu người dùng**. Đây là review source/docs/evidence và regression do agent chạy; **human B/A approval NOT RUN**, không ghi duyệt thay thành viên và không gửi GitHub review. Nguồn C: main `12e2838`; nguồn B: PR #17 `41da669`; [phương pháp C5](MONITORING_BASELINE_METHOD.md), [báo cáo tích hợp](../evidence/b-integration/2026-10-11-verification.md).

## Kết quả

Không thấy blocker mới trong C1–C5 ngăn tích hợp client B sau giải quyết dashboard conflict. Chấp nhận mô tả baseline **trong phạm vi đã chứng minh**. Chưa đủ điều kiện chạy benchmark/nghiệm thu toàn hệ thống. Hai lỗi B ở ranh giới vào thi đã sửa: fresh scope không được tự chế bằng Set.of(selected); collector đang giám sát A không được dùng khi mở B; lỗi start monitoring phải chặn entry. Regression thực dùng worker C và scope/transport MOCK.

| Nội dung | Đối chiếu và nhận xét |
|---|---|
| Policy/phạm vi | ProcessPolicy và FullSnapshotPayload chia sẻ 8 tên case-insensitive; ProcessHandleSnapshotSource liệt kê tất cả trước lọc, chỉ giữ filename/start/userAvailable. Không giữ path/arguments/username. Thiếu filename không chọn được production process, vẫn vào diagnostics. Notepad không thuộc policy; empty không chứng minh sạch máy. TEST root Java do harness sở hữu khác production; network/replay production từ chối TEST. |
| Identity/quality | ProcessIdentity gồm collector/PID/start; khác start hay collector là khác process. Thiếu start không giải quyết hoàn hảo PID reuse. ProcessReading.COMPLETE cần tên/start/userAvailable; UNREADABLE không được diễn giải là không có process. Full giữ precision của start; event chuẩn hóa microsecond. OwnedProcessPolicy giữ identity tới stop và joiner yêu cầu start khác-null; PID-only/thiếu coverage trả INCONCLUSIVE. |
| Full/reconnect | OPEN epoch theo socket/user/collector, full đầu1; full sau có thể nhảy sequence; duplicate stable/conflict/old epoch được kiểm. Full ACK chỉ RAM một JVM. Reconnect bỏ full cũ, cần scan mới và OPEN/full1; không dùng heartbeat sống để nói state SYNCED. Dashboard gộp theo serverInstanceId/revision, giữ current state khi stale. Các tab và cleanup current/gap được giữ trong merge. |
| Event/gap/presence | Event ổn định qua retry; metadata nguồn kết nối đóng băng, LIVE không có nghĩa không trễ. Event/gap commit DB mới ACK theo handler/service; state không xóa history. Overflow drop-new/gap khác heartbeat interruption; queue RAM không survive kill. Gap mới cần HTTP refresh. Server permission/transaction đã đọc đối chiếu, regression DB **không chạy lại** trong lượt này. |
| Lifecycle | Collector gate candidate; worker riêng fixed delay; stop future chờ poll/callback kết thúc, không block FX. Full/event hủy listeners/pending/generation; send guard kiểm socket và lifecycle trước ghi. Heartbeat lease của collector không đóng WS dùng chung. App release đóng exam view/clock/subscription, coordinator, dashboard, HTTP và realtime. Fixture regression kiểm worker/listener/lease cleanup; FX smoke kiểm late ACK không render sau close. |
| Ownership harness/trace | Ground truth từ parent ProcessBuilder/READY/GO/waitFor và nanoTime cùng JVM, không từ collector. CREATE_NEW giữ artifact cũ; bounded queue/run/cleanup, chỉ dọn root/known descendants của harness. TraceRecorder writer tách khỏi collector, drop/error không giả COMPLETE. Collector stopped future **không đồng nghĩa** trace writer đã flush; khi bật trace phải kiểm finished/FINAL/checksum riêng. Hard-kill parent/OS Ctrl+C còn NOT RUN; không hứa cleanup arbitrary descendant sau parent kill. |
| Evidence/metric | 200ms là hold sau GO, không phải đời process kernel200ms. OBSERVED và NOT_OBSERVED cần chung eligibility trước tính tỷ lệ; INCONCLUSIVE riêng. scanDuration không là CPU; clock hai máy không trừ tùy ý. C3 PASS với fault schedule có thể còn missingBusinessEvents. WS UTF-8 JSON bytes không gồm HTTP/handshake/TCP/TLS/audio; attempted/completed/RX không cộng đôi. E1 thiếu window/sampler/analyzer, E2 thiếu delta/correctness/oracle. |

## Evidence thực đã kiểm lại

- Ledger C1: 13 file; scheduler fix: 14; C2:22; C3:27 và 3 gzip restoration; C4:28 — mọi hash khớp. Không sửa artifact gốc. C5 verification/doc-checks đọc đối chiếu, không chuyển audit tài liệu thành runtime PASS.
- C4 join chạy mới bằng JAR tích hợp trên raw đã lưu: COMPLETE,27 observed/3 notObserved/0 inconclusive; output byte-identical. **Không tạo 30 process mới**, không chạy lại phép đo C4/E1.
- C3 fixture viết tay: replay PASS; mutation full có chủ đích trả FAIL/exit1 và oracle phát hiện state mismatch. Đây là REPLAY/MOCK/SIMULATED, không PostgreSQL/network/GUI acceptance.
- Suite tích hợp kiểm RealtimeClient, full delivery, dashboard state/gap và ExamSession; FX component kiểm màn thi và các tab proctor. Production candidate→proctor/LAN còn NOT RUN.
- Giữ nguyên evidence C4 event concurrent FAIL và rerun isolated PASS; chưa chứng minh nguyên nhân. Desktop integration cần chạy tuần tự, không dùng rerun PASS để xóa failure cũ.

## Trước phép đo/nghiệm thu tiếp theo

Human B chốt workload, mốc bắt đầu/cửa sổ/warmup, chung eligibility và bins theo lifetime proxy; instrument resource/correlation/endpoint inventory bằng task riêng rồi kiểm fixture tính tay. Không đổi TEST raw thành production bằng sửa schema. Delta cần MT02–MT06 và oracle đúng trước E2. Các checkbox benchmark trong C5 **giữ chưa xác nhận**; E1/E2 **NOT RUN**. Checklist live theo [NGHIEM_THU_VAI_B](NGHIEM_THU_VAI_B.md).
