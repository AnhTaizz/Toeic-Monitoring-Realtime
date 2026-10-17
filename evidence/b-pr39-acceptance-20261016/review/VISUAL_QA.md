# Visual QA thực tế

Đã chạy Application JavaFX thật source `9bfc75e`, run `fb4a7d409c8d` trên Windows11/JDK21/JavaFX21.0.12, PostgreSQL/HTTP/WS thật. Agent đã mở xem các PNG mới bằng image viewer. Đây là quan sát của agent, **không phải human/owner approval**. Ảnh lấy Scene thật, không mockup; fixture accounts/exam/WAV là TEST.

| Ảnh mới | Điều đã quan sát |
|---|---|
| [Reading saved](images/01-reading-saved.png) | 1380×850: câu/đáp án/palette/timer/nút nộp và nhãn saved đều đọc được, revision do ACK. |
| [Submitted](images/02-reading-submitted.png) | Final khóa đáp án/nộp, có nút thoát; không coi timer client là quyết định server. |
| [History](images/03-history.png) | 860×760: tab/dòng SUBMITTED/thời điểm/nút refresh nhìn thấy; nút kết quả khóa khi chưa chọn dòng là expected. |
| [Reading + Listening](images/04-reading-listening.png) | Hai phần cùng màn; audio đang phát và Reading vẫn thao tác được; preparation controls khóa. |
| [Interrupted](images/05-interrupted.png) | Realtime phục hồi nhưng Listening vẫn khóa, có thông báo server interruption; palette/Reading còn hoạt động. |
| [Progress](images/07-progress.png) | 1140×780: ACTIVE/final cùng progress committed; scrollbar ngang tới cột cuối, trạng thái UNKNOWN không kết luận gian lận. |
| [B4 start](images/08-session-start.png) | Tab điều khiển hiển thị ACK start; form có scroll, GUI đã đưa nút READY/start vào viewport trước click. |
| [Reorganization](images/09-reorganization.png) | Mapping old→new hiển thị, roster có lượt mới UNKNOWN/chưa heartbeat. |

Login760×560 và progress stale503 được chụp thêm bằng renderer JavaFX dùng đúng JAR cuối; HTTP rows là **MOCK**, không chứng minh DB integration. Xem provenance/ảnh bổ sung được thu ở bước kết thúc. Stale phải giữ dòng/revision42/readAt cũ và báo chưa xác nhận. Caption loading có thể thay đổi giữa polling và snapshot, không suy thành ACK mới.

Quan sát không blocking: quick-fill account ở login có ellipsis; form B4 dài cần scroll; progress nhiều cột dùng scrollbar ngang. Chưa sweep DPI/màn720×520/đề dài/thiết bị vật lý hoặc xác nhận khả năng nghe. Các case đó giao B trong checklist; không sửa cosmetic ngoài phạm vi stabilization.

Screenshot process/PID `06-proctor.png` và raw C1/C2 không publish. Xác nhận nghe, dialog click và socket seam automation gắn SIMULATED; server restart trong GUI là restart đúng process TEST thật. Physical LAN/human hearing **NOT RUN**.

Ảnh bổ sung source9bfc75e đã mở xem: [login](images/10-login.png), [progress loading/confirmed row MOCK](images/11-progress-confirmed-MOCK.png), [progress stale503 MOCK](images/12-progress-stale-MOCK.png). Renderer giữ revision42/readAt/dòng cũ sau503; không có operator nghe thật.

EXE037 installed trên PostgreSQL TEST riêng: [History](images/13-installed-history.png), [reorganization](images/14-installed-reorganization.png) mới, agent đã xem. Source9bfc75e và private harness hash ở release/HARNESS_PROVENANCE.json. Không human approval.
