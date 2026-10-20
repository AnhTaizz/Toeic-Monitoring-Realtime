# Proctor redesign — audit dữ liệu và kiến trúc (Checkpoint 1)

Agent 2, nhánh `feat/ui-proctor-redesign-20261020`, base EX-07 `93c70194c7940afd62155881e28b7ba49bfc5bf6`.
Tài liệu này chỉ thuộc namespace Agent 2. Không thay tracker, giờ công hay human approval dùng chung.

## 1. Mockup đã đọc

Đã đọc đủ 7 PNG 1600×900 trong ZIP `TOEIC_Proctor_7_Mockups_Redrawn.zip`, giải nén vào scratchpad, **không commit ảnh**:
01 Overview, 02 Candidate List, 03 Alert Management, 04 Monitoring History, 05 Candidate Detail, 06 Alert Detail, 07 Settings.

## 2. Nguồn dữ liệu backend thật (đọc từ code base EX-07)

| Dữ liệu UI | Endpoint / message | Class client tái sử dụng | Ghi chú |
|---|---|---|---|
| Scope, role | `GET /api/v1/auth/me` | `DashboardController` → `MonitoringApiClient.scope()` | Role khác PROCTOR → controller hết phiên |
| Roster + presence (ONLINE/UNKNOWN, reason, lastSeenAt, timeoutDetectedAt, revision) | `GET /api/v1/monitoring/attempts`, WS `MONITOR_PRESENCE` | `DashboardController`, `DashboardModel` | Chỉ lượt ACTIVE có giám sát; không có sessionId |
| Process đang quan sát (state hiện tại, tách khỏi lịch sử) | `GET .../{attempt}/state`, WS `MONITOR_STATE` | `DashboardController.select()` | Chỉ cho **một** lượt đang chọn |
| Event process | `GET .../{attempt}/events`, WS `MONITOR_WARNING` | `MonitoringApiClient.events()` | `observedAt` (client báo), `receivedAt` (server ghi), `deliveryStatus` LIVE/BUFFERED_OFFLINE/PREVIOUS_CONNECTION |
| Gián đoạn heartbeat | `GET .../{attempt}/interruptions` | `MonitoringApiClient.interruptions()` | `lastSeenAt`, `timeoutDetectedAt`, `recoveredAt` |
| Khoảng trống dữ liệu | `GET .../{attempt}/gaps` | `MonitoringApiClient.gaps()` | QUEUE_OVERFLOW, `droppedCount` |
| Giới hạn lịch sử | 409 khi > 5000 dòng | `MonitoringApiClient.HistoryLimitException` | Server từ chối thay vì cắt bớt |
| Tiến độ đã commit | `GET /api/v1/proctor/attempts/progress?limit=50` | `ProctorProgressController` + `ExamApiClient.getProctorProgress` | `answeredCount/totalQuestions`, `savedRevision`, `lastSavedAt`, `deadlineAt`, `state`, `sessionId`, `readAt` = `clock_timestamp()` server |
| Quản lý ca (import, tạo ca, READY, START, kết quả, tổ chức lại) | B4 v1 endpoints | `ProctorSessionView` / `ProctorSessionController` | Giữ nguyên, gắn vào mục "Điều khiển ca" |

## 3. Dữ liệu trong mockup **chưa có** API → UI ghi rõ, không bịa

| Mockup | Thiếu | Cách UI xử lý |
|---|---|---|
| 01/02/05 "Listening · Part 3", "Câu 32–70" | Không có API phần thi/câu hiện tại | Hiện `Phần thi: chưa có API` |
| 01/03/06 "Chưa xử lý / Đã xử lý", nút "Đánh dấu đã xử lý", ghi chú | Không có endpoint lưu trạng thái/ghi chú (MON-08/09) | Chỉ hiện trạng thái thật (nhận trực tiếp / đến muộn / đang gián đoạn / đã phục hồi / thiếu dữ liệu). Nút và ô ghi chú disabled kèm giải thích; không có ACK giả |
| 01 "Tiến trình ca thi" theo Listening/Reading 45 phút | Không có API theo section | Hiện tổng đáp án đã lưu toàn bài và số lượt ACTIVE/đã chốt |
| 03/04 "Cửa sổ mất focus" | MON-02–09 (focus, USB, fullscreen, display) chưa tích hợp production | Không tạo cảnh báo focus/USB, kể cả trong fixture `MOCK` (fixture chỉ dùng các loại backend đang có) |
| 04 "Thao tác giám thị" | Không có audit log thao tác giám thị | Không hiện |
| 07 "Đồng bộ thời gian" | Không đo độ lệch đồng hồ | Hiện "Chưa đo độ lệch; giờ quan sát do client báo, giờ nhận do server ghi" |
| 07 "Âm báo" | Chưa có đường phát âm an toàn được review | Switch disabled |
| Danh sách ca / tên đề | Không có API liệt kê ca cho giám thị | Topbar hiện `sessionId` lấy từ progress |

## 4. Kiến trúc cách ly

- Package mới `vn.edu.toeic.client.proctor.redesign`, CSS `client/src/main/resources/proctor-redesign/proctor-redesign.css`, mọi selector bắt đầu bằng `.proctor-redesign`. Stylesheet gắn ở **Parent** (`getStylesheets()` của root view), không gắn vào Scene, nên không đụng Candidate UI.
- Không sửa class hiện có. Dùng public API: `DashboardController(Role, DashboardApi, MonitoringTransport, ...)`, `ProctorProgressController(Gateway, Executor, ...)`, `MonitoringJson.push/event`, `ProctorSessionView`.
- Một socket duy nhất (RealtimeClient do app sở hữu). `RoomFeed` chỉ đăng ký thêm **listener** `onMessage` cho `MONITOR_WARNING`, không mở kết nối mới, không gửi gì lên socket.
- `RoomFeed` là adapter mỏng: tải events/interruptions/gaps theo từng lượt vì `DashboardController` chỉ tải cho lượt đang chọn. Tối đa 2 lượt song song, không poll định kỳ, merge theo `eventId`.
- Thời gian còn lại = `deadlineAt − readAt` (cả hai đồng hồ server) − thời gian đơn điệu (`nanoTime`) đã trôi qua từ lúc nhận. Quá 15 s chưa đọc lại hoặc dữ liệu stale → "Chưa xác nhận".
