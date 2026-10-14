# T3-B2 · Phát Listening theo lệnh server

Nhánh `feat/t3-b2-listening-playback` kế thừa T3-B1 vì playback dùng đúng writer, manifest và cache đã được server xác nhận READY. Contract server là [T3-A2](LISTENING_LIFECYCLE.md). Client không phát từ response HTTP start, status GET hoặc reconnect.

## Luồng candidate

1. Candidate hoàn tất T3-B1 và giữ `PlaybackContext(attemptId, writerEpoch, manifest)` của lần READY hiện tại.
2. `RealtimeClient` chỉ publish `LISTENING_START` khi envelope v0 đúng scope; `messageId=startId`, requestId null, payload có attempt/session/manifest/writer/timestamp hợp lệ. Message sai bị loại trước coordinator.
3. Worker `toeic-listening-playback` so lệnh với context READY, đọc và SHA-256 lại mọi file local, rồi gọi `GET /api/v1/attempts/{attemptId}/exam`. Mỗi câu LISTENING phải tham chiếu đúng một file trong manifest và mỗi file phải có câu hỏi.
4. Trước khi giao file cho JavaFX Media, client tạo marker local bằng `CREATE_NEW` theo hash của attempt/writer/startId. Marker đã tồn tại chặn phát lại sau restart. Trong cùng tiến trình, startId đang chuẩn bị hoặc đã xử lý cũng bị loại.
5. UI phát các file theo thứ tự manifest. Khi đổi file, phần câu hỏi chỉ hiển thị các câu có `audioFile` tương ứng, theo `orderIndex`. DTO candidate không chứa đáp án đúng.
6. Callback `onPlaying` ghi `playbackStartedAt` vào marker trên worker và gửi event STARTED. Client gửi HEARTBEAT mỗi 2 giây trong khi phát; khi file cuối kết thúc, gửi COMPLETED. Mỗi response HTTP phải khớp request/attempt/start/writer và phase server mong đợi.

Việc kiểm file, đọc đề, journal và HTTP không chạy trên JavaFX thread. UI chỉ tạo/dọn `MediaPlayer`, chuyển đoạn và render snapshot. Client không hứa các máy bắt đầu cùng một sample; mỗi máy phát cục bộ sau khi nhận lệnh.

## Dấu local và chống phát lặp

Marker nằm dưới `<audio-cache>/playback-started/<sha256>.json`. Trạng thái reserve được ghi trước khi Media được tạo. Khi Media thực sự vào `onPlaying`, worker thay marker bằng bản có `playbackStarted=true` và timestamp local. Timestamp này là bằng chứng vận hành phía client; server vẫn dùng thời gian của server khi chấp nhận STARTED.

Nếu ứng dụng dừng sau reserve nhưng trước `onPlaying`, marker vẫn chặn nghe lại. Đây là lựa chọn fail-closed để không biến crash thành quyền replay. Tổ chức lại phải tạo attempt/start mới theo T3-A2.

## Phạm vi B2 và bàn giao B3

B2 khóa playback khi Media hoặc event server lỗi. Nó chưa gửi INTERRUPTED cho `MEDIA_ERROR`/`NETWORK_LOST`, chưa đọc status để render policy sau reconnect và không tự resume. Các hành vi đó thuộc T3-B3. Heartbeat hiện dùng HTTP production; monitoring heartbeat không thay thế nó. MP3/M4A nằm trong allow-list backend nhưng package smoke B2 chỉ nghiệm thu WAV PCM.

## Kiểm chứng

```powershell
# Full Maven + PostgreSQL thật; script hiện giữ tên B1 vì được mở rộng theo chuỗi audio client.
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-t3b1.ps1 -JavaHome '<JDK21>' -Postgres -DatabasePort 5433

# Component JavaFX/Media trong app-image Unicode và khoảng trắng.
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t3b1-package.ps1 -JavaHome '<JDK21>' -OutputDirectory '<output mới>'
```

Kết quả ngày 14/10/2026: Java666 PASS, 0 skip khi bật PostgreSQL; package smoke PASS7. Xem [bằng chứng T3-B2](../evidence/t3-b2/2026-10-14-verification.md). Review A, LAN/máy thứ hai và nghe thủ công chưa chạy.
