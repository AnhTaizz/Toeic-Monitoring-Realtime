# Checklist nghiệm thu phát hành — việc agent không thể làm thay

Tất cả mục dưới đang **NOT RUN**. Mỗi mục cần người thật hoặc máy thật; agent không giả lập để đánh dấu PASS. Ghi kết quả vào `docs/KIEM_THU.md` kèm SHA, máy, OS, người chạy, ngày giờ.

Artifact dùng chung: MSI `ToeicMonitor-0.3.10.msi` từ `release-0.3.10/` (SHA256 trong `SHA256SUMS.txt` và `REPORT.md` mục E). Tính lại SHA256 sau khi chép sang máy khác rồi mới cài.

## 1. LAN hai máy (IT01, IT02, IT04) — B chạy, A/C xem

Làm theo `docs/LAN_TWO_MACHINE_RUNBOOK.md`. Máy 1: server + PostgreSQL. Máy 2: client cài từ MSI, không có IDE/JDK trên PATH.

| Bước | Thao tác | Cần ghi |
|---|---|---|
| 1 | `Get-FileHash` MSI trên máy 2 | SHA256 khớp manifest |
| 2 | Cài vào thư mục có dấu và khoảng trắng; ghi `http://<IP máy 1>:<port>` vào `%LOCALAPPDATA%\TOEIC Monitor\server-url.txt` (hoặc biến `TOEIC_SERVER_URL`) rồi mở `ToeicMonitor.exe` | Login thí sinh và giám thị được; OS build; `where.exe java` |
| 3 | 2 thí sinh + 1 giám thị; một thí sinh vào thi, chọn đáp án, rút cáp/tắt Wi-Fi 30 s rồi nối lại | Dashboard đổi sang UNKNOWN trong bao lâu (giây), trở lại SYNCED; autosave không mất câu đã ACK |
| 4 | Tắt server khi đang lưu, bật lại | Không có thông báo "đã lưu" giả; client reload theo server |
| 5 | Tạo ca có username/sessionId ASCII; thử tạo ca có sessionId chứa dấu cách | Ca ASCII tạo được; ca lỗi bị từ chối 400, không sinh lượt nào (QD-31) |

Không trừ timestamp giữa hai máy chưa đồng bộ giờ.

## 2. Nghe Listening bằng tai người (LT01–LT04) — B chạy

Theo `docs/ACCEPTANCE_B_MANUAL_MATRIX.md`. Người nghe thật xác nhận: âm thanh đúng file, không phát lại khi reconnect, gián đoạn thì dừng và khóa. Ghi thiết bị âm thanh, người nghe, kết quả từng bước. Xác nhận nghe tự động trong test là SIMULATED, không thay mục này.

## 3. Máy Windows sạch (IT01) — B chạy

Máy chưa từng cài JDK/IDE (hoặc VM mới). Cài MSI per-user, chạy, gỡ. Ghi: có cần quyền admin không, thư mục cài có bị ghi không (cache audio phải nằm ngoài thư mục cài), gỡ có để lại file trong thư mục cài không.

## 4. Tải 1/5/10 client × 3 lần (kế hoạch mục thực nghiệm tải) — A chạy

Headless clients, mỗi mức 3 run 120 s + warmup 15 s. Ghi tỉ lệ thành công/lỗi save, RTT p50/p95, CPU/RAM server. Client giả lập không phải 10 ứng dụng JavaFX thật.

## 5. E2 hoặc fallback FULL — C quyết

E2 chưa chạy. Nếu không đo E2 trước 27/10: giữ FULL mặc định (đang là mặc định), báo cáo ghi "delta không được bật vì chưa có bằng chứng E2", không viết delta tốt hơn.

## 6. Owner sign-off kỹ thuật

| Mục | Người | Câu cần trả lời |
|---|---|---|
| QD-31 | A, C (B xem thông báo) | Chấp nhận legacy ACTIVE chỉ đọc + 409 INVALID_STATE? |
| QD-32 | A, C | Chấp nhận 409 không retry cho lịch sử >5000, chưa phân trang? |
| QD-33 | B (A xem CI) | Chấp nhận policy cache mới, đóng PR45 là superseded? |
| QD-34 | A, B | Chấp nhận takeover không idempotent theo requestId? |
| C1 cleanup | C | Chấp nhận kết quả smoke C1 và chẩn đoán khóa profile Edge (mục B của REPORT)? |
| E1 | C (B xem phương pháp) | Xác nhận số kiểm lại trong `docs/E1_T4C1_MEASUREMENT.md` mục cuối? |
