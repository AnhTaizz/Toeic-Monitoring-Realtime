# EX-06 — TOEIC Room: render 7 Part bằng JavaFX (ca PROCTORED_V2)

**Trạng thái: CODE + TEST LOCAL PASS, chưa review/merge, chưa DONE.**
- Nhánh: `feat/ex06-javafx-rich-renderer-20261020`, tạo từ EX-07 `93c7019`.
- Nhánh này phụ thuộc EX-07 → EX-05 → EX-03; chưa phần nào trong số đó merge vào `main`.
- **NOT RUN:** nghe thật bằng loa, đóng gói Windows/MSI, LAN, máy thứ hai.
- Đề dùng để kiểm thử là fixture MOCK `mock-ex02-200`, không phải đề TOEIC thật.

## 1. Luồng client

`ToeicClientApplication`: thí sinh bấm vào phòng thi, client gọi `RichEntry.probe` (GET `/api/v2/attempts/{id}/preparation`).
- Trả 200: mở `RichAttemptView`, tức TOEIC Room V2.
- Trả 409 `INVALID_STATE`: đi luồng legacy cũ, gồm `ExamEntry` và `CandidateExamView`, không đổi gì. Lượt V2 đã kết thúc cũng đi nhánh này để xem kết quả qua status v1.

`RichAttemptView` xử lý theo thứ tự:
1. **Takeover một lần** qua `POST /api/v1/attempts/{id}/takeover`. Trước START server không tạo deadline.
2. Tạo **một** `AudioPreparationView` dùng chung writer. Đây vẫn là player Listening duy nhất. Có hai khác biệt so với legacy:
   - `ListeningApi.exam()` đi qua `RichExamSource` (GET v2 + bridge §7.1), không gọi GET exam v1.
   - Có `ListeningPlayGate` chặn trước `MediaPlayer.play()`.
3. **Lobby** (`RichLobbyPane`) và `VisualReadyCoordinator`, mỗi 2000 ms:
   - GET preparation.
   - Khi manifest hoặc epoch đổi: tải ảnh Part 1 qua `GET /api/v2/attempts/{id}/images/{mediaId}?visualManifestId=`, kiểm size/SHA-256/chữ ký/pixel, decode trên worker. Sau đó gắn ảnh vào scene dưới màn che (đã clip), chờ hai pulse thật.
   - Chỉ khi `AudioPreparation` có Audio READY do server ACK cho đúng writer mới gửi `POST /api/v2/attempts/{id}/visual/ready` ATTEST. Mỗi lần renew dùng `requestId` mới.
   - Lỗi mạng hiển thị UNVERIFIED, không bao giờ hiển thị READY.
   - Reconnect trước START: gửi INVALIDATE rồi chuẩn bị lại.
4. **START** đến từ preparation `IN_PROGRESS` hoặc tin WS `LISTENING_START`. Khi đó client mới gọi GET `/api/v2/attempts/{id}/exam` một lần (renderer và player dùng chung kết quả) và GET status v1 để lấy revision/answers. Sau đó mở `RichExamView` với `ExamSession` hiện có.
5. **Làm bài:**
   - Autosave/submit/timeout/writerEpoch/answerRevision giữ nguyên logic `ExamSession`.
   - Đồng hồ tính theo `deadlineAt` của server.
   - Màn tự chuyển theo đoạn audio đang phát. Không có nút tua hay phát lại.

HTTP, file, SHA-256 và decode ảnh chạy trên worker: `RichImageStore` 2 luồng, `VisualReadyCoordinator` 1 luồng, worker hiện có của Listening/ExamApiClient. Ảnh chỉ nằm trong RAM, theo scope attempt/writer/epoch/manifest; sau START scope là `visualManifestId` đã chốt. Đóng view, logout hay đổi màn đều dừng worker.

## 2. Đối chiếu 7 mockup

| Mockup | UI thực tế (`RichExamView`) | Khác biệt có chủ đích |
|---|---|---|
| 01 Part 1 Photographs | Thẻ ảnh bên trái (ảnh thật từ server), thẻ câu bên phải "Câu N / 100", A–D "Đáp án X", ghi chú "chỉ phát qua audio" | Hiện TEXT block nếu server cấp. Câu không có ảnh hiện cảnh báo trung thực. |
| 02 Part 2 | Chip câu lân cận (Đang xem/Đã chọn), thẻ giữa với A/B/C chỉ nhãn | Không có văn bản câu hỏi giả |
| 03 Part 3 | Cột trái: hướng dẫn + danh sách nhóm (trạng thái "Đang nghe"/"Đã chọn x/3"); bên phải: "Questions a–b" + 3 thẻ câu | Banner hiện thông báo thật của player và tiến độ theo đoạn, không có thời gian phát giả |
| 04 Part 4 | Như Part 3; figure tùy chọn của group hiện phía trên các câu | — |
| 05 Part 5 | Câu lớn, 4 lựa chọn, chip lân cận, nút trước/sau ở footer | — |
| 06 Part 6 | Dải chip nhóm 131–134…, passage (tài liệu) bên trái, 4 thẻ câu bên phải cuộn riêng | Không tô ô trống như mockup vì passage là TEXT nguyên văn server cấp. Option dài (câu chèn) xuống dòng nguyên văn. |
| 07 Part 7 | Tài liệu 1/2/3 trong khung riêng có tiêu đề, giữ TEXT + IMAGE và thứ tự; nút tài liệu cuộn tới khung; bên phải chip câu + thẻ câu | Hiện tất cả tài liệu xếp dọc trong một vùng cuộn, tab dùng để nhảy tới |

Phần chung: header gồm logo, tên đề, kết nối, đồng hồ server, tên/username từ login; thanh Part dạng stepper; nút "Chọn câu" mở bảng 200 câu; footer gồm trạng thái lưu, vị trí, "Đã chọn x/200", trước/sau, Nộp bài. Không có tên, giờ, đáp án hay trạng thái kết nối cố định nào lấy từ mockup.

Ảnh chụp thật nằm trong `evidence/ex06-20261020/screens/`:
- `part1..7.png`: test GUI, dữ liệu MOCK, gateway giả.
- `e2e-*.png`: server và PostgreSQL thật.

## 3. Kiểm thử thực tế (20/10/2026, cloud Linux, OpenJDK 21.0.12, Xvfb, **PostgreSQL 16.15**)

| Test | Kết quả | Nội dung |
|---|---|---|
| `RichExamModelTest` | 9/9 PASS | 200 câu/7 Part 6/25/39/30/30/16/54, đơn vị P3/P4/P6, Part 1/2 chỉ nhãn, Part 7 ba tài liệu, bridge Listening 100 câu, chặn `correctOption/answerKeyStatus/sourceUrl/localFile`, đề hỏng bị từ chối, option câu chèn giữ nguyên văn |
| `RichImageStoreTest` | 6/6 PASS | Size/SHA/chữ ký/pixel, `corrupt.png` bị từ chối, đổi scope thì xóa cache, 403 không cache, đổi scope giữa lúc tải thì không publish ảnh cũ, đóng thì dừng worker, một GET đề v2 dùng chung và không gọi GET exam v1 |
| `VisualReadyCoordinatorTest` | 9/9 PASS | Trước START không GET đề; chưa có Audio READY thì không ATTEST; asset duy nhất; renew bằng requestId mới; đổi epoch/manifest thì chuẩn bị lại; STALE hoặc đổi writer thì dừng; lỗi mạng thì UNVERIFIED; INVALIDATE; probe V2/legacy |
| `RichExamViewGuiTest` (Stage thật) | 6/6 PASS | Render 7 Part; đáp án còn sau khi chuyển Part; autosave gửi toàn bộ map; ảnh 403 có retry; cổng play mở sau IMAGE_ATTACHED→LAYOUT_DONE→PULSE1→PULSE2; ảnh bị che hoặc bị từ chối thì FAIL |
| `RichAttemptE2eGuiTest` (server/PG/WS thật) | 1/1 PASS, chạy 3/3 lần | Xem mô tả bên dưới |

Chi tiết lượt E2E:
1. Import fixture qua `POST /api/v2/exams/import`, nhận 201 VERIFIED; tạo ca bằng `POST /api/v2/sessions`.
2. Trước START, GET exam v2 trả 409.
3. Bấm các nút audio thật: tải → thử loa → nghe được → READY.
4. Lobby báo Audio + Ảnh READY, readiness `allReady=true`; giám thị START qua Listening v1.
5. Màn thi mở với 200 câu. 54 đoạn Listening phát hết qua player duy nhất (đi qua 6 cổng ảnh Part 1), server xác nhận "phát xong".
6. Chọn đáp án Part 5/7 trên UI; status v1 của server trả `{"q101":"B","q147":"D"}` revision 2.

Lưu ý về môi trường của lượt E2E:
- **Âm thanh:** container không có thiết bị âm thanh nên dùng ALSA `null` qua `~/.asoundrc` của container. MediaPlayer chạy thật nhưng không ai nghe. **Nghe thật: NOT RUN.**
- **Audio fixture:** là WAV im lặng 10 ms.

`mvn clean verify`: xem `docs/KIEM_THU.md` mục 20/10.

Test GUI bật bằng `TOEIC_RICH_GUI=true`; E2E bật bằng `TOEIC_RICH_E2E_URL` + `TOEIC_SEED_PASSWORD`. CI không bật hai biến này nên các test đó SKIP ở CI.

## 4. Còn thiếu / chờ

- **Chờ EX-07:** EX-07 phải được review/merge. Thứ tự Flyway V13/V14/V15 vẫn đang chờ A/C quyết.
- **Chờ EX-01 (đề thật):** chưa có đề thật đủ điều kiện, xem [ZENLISH_TO_V2_IMPORT_GAPS.md](ZENLISH_TO_V2_IMPORT_GAPS.md). Vì vậy EX-08 BLOCKED.
- **Proctor UI cho V2 chưa có:** catalog, tạo ca và START là phần UI-04. Demo hiện dùng script hoặc HTTP.
- **Sau START, takeover hoặc reclaim:** player cũ vẫn giữ writer cũ. Server đánh dấu Listening gián đoạn đúng chính sách no-replay, nhưng client chưa tạo lại audio view theo writer mới.
- **Ngoài phạm vi EX-06:** màn đăng nhập và trang chủ theo mockup TOEIC Room (vẫn giao diện cũ); MON-*.
- **Tương tác Part 1 trong lúc chờ ảnh:** nếu thí sinh rời màn Part 1 khi cổng đang chờ ảnh (tối đa 3 s), cổng FAIL và Listening bị khóa (MEDIA_ERROR). Hành vi này đúng fail-closed nhưng cần B/A xác nhận UX.

## 5. Demo trên Windows (dữ liệu MOCK)

```powershell
# 0. DB như README (docker compose up -d --wait), nạp DB_* từ .env vào terminal
mvn package
$env:TOEIC_SEED_PASSWORD = "<mật khẩu MOCK của bạn>"
$env:TOEIC_AUDIO_ROOT = "$PWD\docs\plan-v2\fixtures\ex02\assets\audio"
$env:TOEIC_IMAGE_ROOT = "$PWD\docs\plan-v2\fixtures\ex02\assets\images"
java -jar server\target\server-0.1.0-SNAPSHOT.jar          # terminal 1

# 1. Terminal 2: import đề MOCK + tạo ca V2 cho candidate1
.\scripts\demo-ex06-v2.ps1 -ServerUrl http://127.0.0.1:8080

# 2. Terminal 3: mở client, đăng nhập candidate1, chọn lượt thi vừa in ra, bấm vào phòng thi
mvn -pl client -am javafx:run
#    Ở lobby: "Tải / chuẩn bị lại audio" → "Thử loa" → "Tôi nghe được" → "Xác nhận sẵn sàng";
#    chờ 4 dấu ✓ (kết nối, âm thanh, ảnh Part 1, server READY).

# 3. Terminal 2: giám thị bắt đầu (server từ chối 409 nếu chưa đủ READY)
.\scripts\demo-ex06-v2.ps1 -ServerUrl http://127.0.0.1:8080 -Start -SessionId <sessionId>
```

Mỗi lần demo cần một ca mới (chạy lại bước 1). `demo-ex06-v2.ps1` **chưa được chạy trên Windows**; trên Linux, luồng tương đương đã chạy bằng curl và test E2E.
