# MON-01 — checklist B và nghiệm thu process trên LAN thật

20/10/2026 — Lượt mới dùng [kit main tích hợp](T3_C4_PHYSICAL_LAN_ACCEPTANCE.md#kit-từ-main-tích-hợp--lối-chạy-hiện-hành-20102026)
và [receipt mới](../evidence/c-integrated-kit-20261020/REPORT.md), không lấy ZIP/SHA lịch sử
bên dưới làm build main hiện hành. Không đổi/bypass execution policy; nếu policy chặn
script, ghi BLOCKED/nhờ owner cung cấp cách được phép. FULL/experiment OFF, human B/BASE02
pending, MON01 chưa DONE; 41case LAN vẫn0, USB physical vẫn0/12NOT_RUN.

Trạng thái chuẩn bị 19/10/2026: **0 case vật lý đã chạy; B PENDING; MON-01 chưa DONE; BASE-02 approval còn chờ**.
[Execution report](../evidence/v2-c-mon01/REPORT.md) · [Checklist CSV riêng](../evidence/v2-c-mon01/physical-pending/cases.csv) · [Review trống](../evidence/v2-c-mon01/physical-pending/review.json).

## Bộ dùng cho lượt B

Dùng bộ BASE-03 đã kiểm, runtime source `834f683e50b22e214ab63bbf7d046a7d418b8242`, bundle source `463cbadc50465c6ca2f2d4c0a0701dcb9c38e034`. MON-01 source test `f78b40776d0e414e2a631f9e0eb0725b2f1033aa` chỉ thêm test/wrapper; production không đổi. Bộ này không chứa test bổ sung MON-01, nhưng chạy đúng production runtime đang được kiểm.

ZIP `T3C4-BASE03-463cbad.zip`, SHA256 `5ecba6fb2a09181e3399f3db5f7a1efc1706dbba73e048b017f52a97c29684c6`; manifest SHA256 `f51626affd497c739fdf3a2c6bb02a1de726aeb986617f8c73fdea7d528b8d8f`. [JAR hashes và manifest](../evidence/v2-c-base03/artifacts.json). C giữ bản bền vững tại `D:\Toeic-Evidence-Archive\BASE03-before-MON01-20261019T102703Z-51d0baf1\worktree-snapshot\target\base03-handoff\T3C4-BASE03-463cbad.zip`; raw archive chỉ tài khoản người sao lưu truy cập. Chưa chuyển ZIP sang B.

C/B đối chiếu hash độc lập khi nhận. Không dùng MSI/ZIP cfbba58 từ phần lịch sử STAGE3_FINAL_HANDOFF thay bộ BASE-03. MON-01 không build/cài MSI và không thay acceptance installer.

## Chạy đúng runbook hiện hành

Theo [T3_C4_PHYSICAL_LAN_ACCEPTANCE](T3_C4_PHYSICAL_LAN_ACCEPTANCE.md): mục1 nhận/hash/init; mục2 preflight/TEST DB; mục3 candidate/proctor; mục4 F cable loss/rejoin; mục5 fault console/probe chỉ trên attempt riêng, nhãn SIMULATED. Mở process-scoped `powershell -NoProfile -ExecutionPolicy Bypass` trước các snippet nếu policy mặc định chặn script; không đổi policy máy.

MON-01 dùng **server FULL, client -Mode Full**. Không bật DELTA. Trên A: PostgreSQL TEST riêng, Spring, PROCTOR và CANDIDATE_A. Trên B: CANDIDATE_B trên Windows vật lý khác. Hai account/attempt khác nhau, proctor đúng assignment. Mọi client dùng cùng IPv4 LAN thật của A, DB credential chỉ trên A; không sửa .env dùng chung hoặc DB dev.

B tạo run directory mới trên mỗi máy, dùng Initialize-T3C4LanRun và preflight của bundle. Giữ 41case BASE-03 cũ đúng trạng thái; checklist MON-01 riêng chỉ bổ sung việc nghiệm thu task này. Topology/OS/JDK/operator/IP thật lưu private; bản công bố dùng alias/che thông tin. Không điền Windows máy B hoặc receipt thành PASS trước khi chạy.

## Trình tự và bằng chứng

1. Ghi source/runtime/JAR/ZIP hash trên cả A/B; TEST DB/attempt/assignment, pid server sở hữu. PROCTOR vào dashboard không có collector; candidate chỉ start sau scope hợp lệ.
2. B mở một process TEST thuộc policy (ưu tiên Edge profile riêng), ghi PID/start/name từ Get-Process hoặc ProcessHandle độc lập. Không đóng Edge/Java không thuộc lượt thử. Process hiện ở đúng attempt; event có ID, observedAt client và receivedAt server riêng.
3. Giữ process qua ít nhất vài poll: state có thể refresh, event appearance không lặp mỗi poll. Chụp/ghi correlation và SQL read-only của owner: same eventId một row. Đóng đúng process TEST: current list mất process, history giữ. Vắng một observation không chứng minh máy không có process đó.
4. B/C kiểm metadata: UNREADABLE hiển thị thiếu thông tin, nullable start không biến thành “sạch/gian lận”. Nếu không có metadata thiếu tự nhiên, dùng evidence TEST MOCK của MON-01 để review kỹ thuật; không tự gọi đó là một hiện tượng vật lý đã quan sát.
5. PROCTOR được phân công đọc state/events/gaps đúng attempt. Candidate/PROCTOR ngoài quyền và proctor khác assignment bị403; thiếu token401. Không đưa token vào URL, log, screenshot hoặc CSV. Candidate A tiếp tục hoạt động khi thử từ chối B.
6. Lưu checkpoint BEFORE bằng Capture-T3C4LanCheckpoint và SQL read-only theo runbook. B **tự thao tác rút cáp LAN thật** sau phối hợp rõ ràng với người giữ máy; ghi link/topology/chứng kiến và loại trừ đường đi dự phòng. Không dùng seam/kill app/firewall block thay physical loss; không tự đổi adapter/firewall.
7. A vẫn ONLINE và cập nhật/save được; B chuyển UNKNOWN theo heartbeat timeout, process state STALE và last-known còn nhãn cũ. Ghi lastSeenAt/timeoutDetectedAt do server; không suy đó là thời điểm chính xác collector dừng, không kết luận gian lận.
8. Nối lại B: re-auth/capability theo kết nối mới, OPEN/epoch mới, fresh FULL rồi SYNCED. Nếu vượt retry budget, dùng reconnect button đúng UI và ghi manual action. Không giả reconnect vô hạn; không reset profile/deadline/attempt.
9. Checkpoint RECOVERED + SQL: events/gaps/interruption cũ không mất, eventId/gapId không trùng. Heartbeat outage tự nó không phải QUEUE_OVERFLOW và không có droppedCount suy đoán. ACK FULL là RAM; ACK event/gap là COMMIT. Không false ACK/SAVED khi offline.
10. Stop rồi start lại không overlap worker; logout/app close dọn collector/delivery/heartbeat leases/listeners. Có thể dùng jcmd đúng PID sở hữu để đối chiếu toeic-process-collector, toeic-monitoring-delivery, toeic-full-snapshot; HTTP/realtime dùng chung có thể còn trước logout, không nhầm chúng với observer đã dừng.
11. Trên attempt TEST mới, B thử submit/timeout qua luồng ứng dụng thật, xác minh observer tự dừng khi scope ACTIVE mất. MON-01 automation mới chỉ chuyển trạng thái DB TEST giả lập rồi quan sát WS/worker thật, không thay bước này.
12. B ghi Actual/PASS-FAIL/evidence vào CSV và ký review.json sau review. C đối chiếu protocol/time/DB/workers; A xác nhận dependency BASE-02/auth/transaction nếu cần. Listening/hearing/MSI và đầy đủ F của BASE-02/03 vẫn theo runbook gốc, không được tự nâng bằng việc xong checklist process.

## Điều kiện dừng

Sai hash/source, môi trường không phải TEST, không đủ hai máy, không có người thao tác hoặc thiếu quyền hợp lệ: giữ BLOCKED/NOT_RUN tương ứng. Giữ raw bất biến ngoài Git, che dữ liệu ở bản dẫn xuất và thêm SHA256. Không sửa checksum hoặc assertion để đạt PASS. Không tự thay FULL, protocol, migration, tracker, ký B hoặc chuyển MON-07.
