# T3-B3 · Gián đoạn và mở lại Listening

Candidate dừng và hủy `MediaPlayer` ngay khi realtime rời `CONNECTED` hoặc JavaFX báo lỗi media. Coordinator chuyển sang `BLOCKED`, hủy heartbeat, ghi marker cục bộ và gửi event `INTERRUPTED` qua HTTP với `NETWORK_LOST` hoặc `MEDIA_ERROR`. Nếu lần gửi đầu thất bại, cùng request được giữ để thử lại khi kết nối phục hồi.

Khi mở view hoặc reconnect, client gọi `GET /api/v1/attempts/{attemptId}/listening/status` cho scope đăng nhập. `INTERRUPTED` luôn giữ khóa và yêu cầu chờ giám thị; `COMPLETED` giữ trạng thái hoàn tất. Nếu server còn `ARMED` hoặc `PLAYING` trong một phiên mới/mở lại, client coi đây là phiên dang dở, báo `NETWORK_LOST` và khóa. Không nhánh status nào tạo `Media`, gọi `play()` hay dựng lại playlist.

Policy này nằm trong `ListeningPlayback`/`ListeningPlaybackPane`. Nó chỉ nghe connection state và lifecycle Listening, không gọi `ExamSession` và không thay đổi reconnect/autosave của Reading. T2-B4 chưa có trên nhánh phụ thuộc tại thời điểm triển khai, nên gate tích hợp chung Reading + Listening vẫn cần chạy sau khi hai nhánh được ghép.

Marker JSON theo attempt/writer/start lưu nguyên nhân, request ID, thời điểm và việc playback đã thực sự bắt đầu. Marker chống replay từ B2 không bị xóa. Giám thị muốn tổ chức lại phải dùng attempt/start mới theo lifecycle server.

Kiểm chứng tự động gồm lỗi media, mất mạng trong lúc phát, retry/reconnect, mở lại khi server `PLAYING`/`INTERRUPTED`, HTTP status nghiêm ngặt và JavaFX MediaPlayer thật trong app-image. Package smoke dùng API/realtime fixture; full Maven dùng PostgreSQL thật cho các suite backend hiện có. Review A, rút mạng vật lý/kill process thủ công, LAN/máy thứ hai và nghe bằng người thật vẫn phải ghi riêng.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-t3b1.ps1 -JavaHome '<JDK21>' -Postgres -PackageSmoke -DatabasePort 5433
```

Script còn tên T3-B1 vì B2/B3 mở rộng cùng pipeline audio/package. Xem [bằng chứng T3-B3](../evidence/t3-b3/2026-10-14-verification.md).
