# T3-B3 · Kiểm chứng gián đoạn và mở lại Listening

Ngày 14/10/2026 (Asia/Bangkok), branch `feat/t3-b3-listening-interruption-recovery`, kế thừa T3-B2 `b89667d`, source code/test `d22b0c5793710ad27f9f9421a612ff37f5c062a6`. [Hướng dẫn](../../docs/LISTENING_INTERRUPTION_RECOVERY.md), [metadata](metadata.json), [hash artifact](artifact-hashes.json).

Code complete cho T3-B3. Tracker giữ `IN_PROGRESS` vì review A, rút mạng vật lý, kill/mở app thủ công, LAN/máy thứ hai và nghe bằng người thật chưa chạy. T2-B4 chưa có trong nhánh/lịch sử phụ thuộc, nên policy Listening được giữ riêng và chưa gọi kết quả này là gate Reading + Listening hoàn chỉnh.

| Phần | Kết quả | Bằng chứng / phạm vi |
|---|---|---|
| Maven toàn reactor + PostgreSQL | PASS669, 0 fail/error/skip | Protocol43 + client424 + server201 + spike1; [transcript](maven-package.txt), [tổng hợp](test-results.json) |
| Coordinator/API T3-B3 | PASS10 | ListeningPlayback7 + ListeningApiClient3; HTTP loopback/API fixture MOCK, filesystem/journal REAL, callback media SIMULATED |
| LT02 lỗi media | PASS tự động | `mediaFailed` dừng đường phát, khóa, marker và event `INTERRUPTED/MEDIA_ERROR`; không có `beginSegment` sau khóa |
| LT03 reconnect/mở lại | PASS tự động | mất mạng khi PLAYING gửi `NETWORK_LOST`; status `PLAYING`/`INTERRUPTED` không dựng playlist, không STARTED lại |
| App-image Unicode | PASS8, exit0 | [Kết quả](package-result.txt), [stdout](package-stdout.txt), [stderr](package-stderr.txt); production view/cache/journal/JavaFX PCM WAV/runtime21.0.9 REAL, API/transport fixture MOCK |
| Dấu gián đoạn | PASS | [Marker](interrupted-marker.json) có reason `NETWORK_LOST`, requestId, attempt/start/writer và `playbackStarted=true` |
| Visual QA | PASS | [Ảnh trạng thái khóa](screenshots/listening-network-interrupted.png) sau disconnect/reconnect; không có control phát lại |
| Thử thủ công | NOT RUN | Chưa rút cáp/Wi-Fi, kill process rồi mở lại với server thật, nghe loa, chạy LAN/máy thứ hai hoặc review A |

Full package bật `TOEIC_PG_INTEGRATION=true`; suite server hiện có chạy Spring/Flyway/PostgreSQL thật. Package smoke phát WAV bằng MediaPlayer thật, chờ event STARTED, đổi transport sang DISCONNECTED, kiểm event/marker `NETWORK_LOST`, nối lại, GET status và gửi lại start cũ; số STARTED không tăng. API và transport của package smoke là fixture, nên đây chưa thay thế ca rút mạng/kill process với server thật.

Lần full đầu dùng JDK25: toàn bộ test Listening qua nhưng hai case `ControlledProcessHarnessTest` cũ thất bại do khác hành vi ProcessHandle. Chạy lại bằng Microsoft JDK21.0.9 chuẩn đã ghi trong evidence B2 thì toàn bộ 669 test và package smoke đều qua. Lượt thất bại được ghi rõ, không dùng làm kết quả nghiệm thu.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-t3b1.ps1 -JavaHome 'C:\Users\Lenovo\.jdks\ms-21.0.9' -Postgres -PackageSmoke -DatabasePort 5433
```

Script vẫn mang tên T3-B1 vì B2/B3 mở rộng cùng pipeline audio client và app-image. Tổ chức lại sau gián đoạn thuộc T3-B4; client không xóa marker hoặc cấp quyền nghe lại cho start cũ.
