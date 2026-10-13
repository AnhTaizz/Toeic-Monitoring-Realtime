# T2-B5 — Thiết kế màn tiến độ làm bài trên dashboard giám thị

**Trạng thái: ĐỀ XUẤT của B, ngày 13/10/2026. Chưa cài, chưa được A thống nhất phần API.** Tài liệu này để A và B chốt contract trước khi viết code. Bản do Agent soạn; thành viên vai B và A chưa duyệt.

## 1. Việc cần làm

Tiêu chí nghiệm thu trong `TRACKER.json` (T2-B5, 2 giờ, B làm, A review, phụ thuộc T2-B4 và T1-B3):

> Màn giám thị hiển thị tiến độ đã được server xác nhận; trạng thái chờ lưu không bị gọi là đã lưu; ảnh/log minh họa luồng thật.

Hợp đồng (`02_HOP_DONG.md`, bảng thành phần) giao cho màn giám thị: "danh sách thí sinh, trạng thái, tiến độ đã lưu, timeline".

## 2. Nguyên tắc dữ liệu

1. Mọi con số tiến độ lấy từ server, đúng bản server đã commit. Dashboard không nhận số liệu do máy thí sinh tự báo.
2. Máy giám thị không biết thí sinh đang có thay đổi chờ lưu hay không, nên màn này không có cột "đang lưu" và không dùng chữ "đang làm tới câu…". Chữ dùng trên màn là "đã lưu trên server".
3. Mỗi lần đọc có giờ đọc. Đọc lỗi thì giữ số cũ nhưng ghi rõ "dữ liệu cũ từ HH:mm:ss", không trình bày như số hiện tại.
4. Điểm chỉ hiện khi lượt đã chốt (`SUBMITTED`, `TIMED_OUT`). Không có đáp án đúng, không có nội dung từng câu.
5. Tiến độ làm bài và trạng thái giám sát process là hai nguồn khác nhau. Mất heartbeat (UNKNOWN) không làm đổi số tiến độ, và ngược lại.

## 3. Màn hình

Thêm một tab "Tiến độ làm bài" trên dashboard giám thị, cạnh bảng roster hiện có. Không sửa bảng roster và các tab process.

```
Tiến độ làm bài — số liệu server đã xác nhận · đọc lúc 08:31:05 (3 giây một lần)

Thí sinh    Lượt thi                   Bài thi     Đã lưu trên server   Lần lưu cuối   Còn lại   Kết quả
Thí sinh 1  SESSION-...-candidate1     ĐANG THI    7 / 10 câu · rev 9   08:30:58       12:41     —
Thí sinh 2  SESSION-...-candidate2     ĐANG THI    0 / 10 câu · rev 0   chưa lưu       14:02     —
Thí sinh 1  SESSION-...-candidate1     ĐÃ NỘP      10 / 10 câu · rev 14 08:12:20       —         8 / 10
Thí sinh 2  SESSION-...-candidate2     HẾT GIỜ     4 / 10 câu · rev 4   08:03:11       —         2 / 10

Thay đổi thí sinh chưa được server xác nhận không hiện ở đây.
```

| Cột | Ý nghĩa | Ghi chú |
|---|---|---|
| Bài thi | `ACTIVE` → "ĐANG THI", `SUBMITTED` → "ĐÃ NỘP", `TIMED_OUT` → "HẾT GIỜ" | Lượt chưa vào thi (chưa có deadline) ghi "CHƯA VÀO" |
| Đã lưu trên server | số câu có đáp án trong bản đã commit / tổng số câu, kèm `savedRevision` | Không phải số câu thí sinh đang chọn trên máy |
| Lần lưu cuối | giờ server của lần autosave được chấp nhận gần nhất | "chưa lưu" khi revision 0 |
| Còn lại | `deadlineAt` trừ giờ hiện tại, đếm lùi trên máy giám thị | Chỉ để tham khảo; server mới là nơi quyết định hết giờ |
| Kết quả | `correctCount / totalQuestions` | Chỉ khi lượt đã chốt |

Trạng thái của cả bảng: "Đang đọc…", "Đã đọc lúc …", "Không đọc được, dữ liệu cũ từ …" (kèm lý do đã lọc, không in token).

## 4. Dữ liệu cần và nguồn hiện có

| Dữ liệu | Hiện lấy được ở đâu | Còn thiếu |
|---|---|---|
| Danh sách lượt của giám thị | `GET /api/v1/auth/me` → `attemptScope` | Chỉ có lượt `ACTIVE`. Lượt vừa nộp biến mất khỏi danh sách (đã kiểm ở nghiệm thu 13/10: roster 6 → 5 sau một lượt nộp) |
| Trạng thái, `savedRevision`, `deadlineAt`, `submittedAt`, điểm | `GET /api/v1/attempts/{id}/status`, giám thị được phân công đọc được cả lượt đã chốt | Mỗi lượt một request |
| Số câu đã có đáp án | Đếm trong trường `answers` của response status | Response chứa cả map đáp án; màn tiến độ không cần nội dung đó |
| Tổng số câu | `GET /api/v1/attempts/{id}/exam`; trường `totalQuestions` của status chỉ có nghĩa sau khi chốt (lượt ACTIVE đang trả 0) | Thêm một request mỗi lượt |
| Giờ lưu cuối | Có trong bảng `exam_autosave_requests.decision_at` | Chưa endpoint nào trả ra |
| Tên thí sinh | Roster monitoring hiện có | — |

## 5. Hai phương án API

### Phương án 1 — không đổi server

Client giám thị, mỗi chu kỳ: lấy `attemptScope`, rồi gọi `GET status` cho từng lượt; `GET exam` một lần cho mỗi lượt để biết tổng số câu.

- Được: không cần A làm gì, làm được ngay.
- Mất: N+1 request mỗi chu kỳ; lượt đã chốt rời khỏi `attemptScope` nên chỉ còn trên màn nếu client tự nhớ mã lượt trong phiên đó (mở lại app là mất); không có "lần lưu cuối"; client giám thị nhận cả map đáp án dù không dùng.

### Phương án 2 — một endpoint đọc cho giám thị (đề xuất với A)

```
GET /api/v1/proctor/attempts/progress?limit=1..100&afterAttemptId=<mã lượt cuối của trang trước>
```

Chỉ role `PROCTOR`. Trả các lượt mà giám thị này được phân công, trạng thái `ACTIVE`, `SUBMITTED`, `TIMED_OUT`. Phân trang kiểu keyset theo `attemptId` giống API lịch sử lượt thi (QD-23) để hai API có cùng cách dùng.

```json
{
  "readAt": "2026-10-13T01:31:05.120Z",
  "attempts": [
    {
      "attemptId": "SESSION-1-candidate1",
      "sessionId": "SESSION-1",
      "candidateUsername": "candidate1",
      "candidateDisplayName": "Thí sinh 1",
      "state": "ACTIVE",
      "savedRevision": 9,
      "answeredCount": 7,
      "totalQuestions": 10,
      "lastSavedAt": "2026-10-13T01:30:58.004Z",
      "deadlineAt": "2026-10-13T01:43:46.000Z",
      "submittedAt": null,
      "correctCount": null
    }
  ],
  "nextAfterAttemptId": null
}
```

Yêu cầu B đặt ra cho endpoint này, đối chiếu với bất biến của dự án:

- Xác thực và kiểm phân công trước khi trả dữ liệu; giám thị khác hoặc thí sinh gọi thì 403; token sai hoặc đã thu hồi thì 401.
- Không có đáp án đúng, không có map đáp án. `correctCount` là `null` khi lượt còn `ACTIVE`.
- Số liệu là bản đã commit. `readAt` lấy một lần bằng `clock_timestamp()`.
- Chỉ đọc, không lấy khóa hàng attempt, để không chặn save/submit/timeout.
- Header `Cache-Control: no-store`; tham số lạ hoặc lặp bị từ chối như API lịch sử.

Phần A cần quyết khi cài: đếm `answeredCount` trong SQL hay trong Java; `lastSavedAt` lấy bằng truy vấn con trên `exam_autosave_requests` hay thêm cột vào `monitoring_attempts` (cách sau cần migration).

### Khuyến nghị

Chọn phương án 2. Nó cho đủ bốn thứ tiêu chí cần (số đã lưu, trạng thái, lượt đã chốt vẫn còn trên màn, giờ lưu cuối) bằng một request, và không đưa map đáp án sang máy giám thị. Nếu A không kịp trước mốc đóng băng 27/10 thì B làm phương án 1 và ghi rõ ba giới hạn ở trên trong báo cáo; phần client ở mục 6 được viết để đổi nguồn dữ liệu mà không sửa màn hình.

## 6. Phần client của B

| Thành phần | Việc |
|---|---|
| `AttemptProgressRow` (record) | Một dòng của bảng, đúng các trường ở mục 5 |
| `ProgressGateway` (interface) | `CompletableFuture<List<AttemptProgressRow>> read()`; có hai bản cài tương ứng hai phương án |
| `ProgressPoller` | Một `ScheduledExecutorService` một luồng, gọi `read()` theo chu kỳ `toeic.progress.pollMillis` (mặc định 3000); không chạy trên JavaFX Application Thread; đăng xuất, đổi màn hoặc đóng app thì dừng; kết quả về trễ của phiên cũ bị bỏ |
| `ProctorProgressView` | Bảng và dòng trạng thái ở mục 3; chỉ nhận dữ liệu qua `Platform.runLater` |

Không thêm thư viện. Không đổi collector, WebSocket hay luồng thi của thí sinh.

Test tự động dự kiến: ánh xạ JSON → dòng (kể cả `null`), chữ hiển thị cho từng trạng thái, đếm lùi không âm, đọc lỗi thì giữ số cũ và gắn nhãn "dữ liệu cũ", kết quả về trễ sau khi dừng bị bỏ, poller dừng khi đóng. Dùng `HttpServer` cục bộ làm server giả và ghi nhãn `MOCK`.

## 7. Bằng chứng luồng thật cần có khi nghiệm thu

Chạy server JAR và PostgreSQL thật, một giám thị và hai thí sinh:

1. Thí sinh 1 chọn 3 câu. Sau tối đa một chu kỳ đọc, giám thị thấy "3 / 10 · rev 3"; so với hàng trong DB.
2. Ngắt WebSocket của thí sinh 1 rồi nối lại (client khóa sửa trong lúc ngắt): số trên màn giám thị không đổi trong lúc đó.
3. Chặn response autosave của thí sinh 1 để client ở trạng thái chờ lưu: màn giám thị chỉ đổi khi server đã commit, và đổi đúng theo DB.
4. Thí sinh 1 nộp: dòng chuyển "ĐÃ NỘP", có kết quả, vẫn còn trên bảng.
5. Thí sinh 2 để hết giờ: dòng chuyển "HẾT GIỜ".
6. Tắt server: bảng ghi "dữ liệu cũ từ …"; bật lại thì đọc tiếp.

Lưu ảnh màn hình giám thị, log thao tác và kết xuất DB cho từng bước. Ảnh có bảng process phải che tên tiến trình và PID trước khi đưa lên repo.

## 8. Câu hỏi B cần A trả lời

1. A nhận phương án 2 không, và kịp trước 27/10 không?
2. Lượt đã chốt ở lại danh sách tới khi nào: hết ca thi, hay tới khi giám thị đăng xuất?
3. Giám thị có được thấy `correctCount` ngay khi lượt chốt không, hay chỉ thấy trạng thái?
4. `lastSavedAt` lấy từ `exam_autosave_requests` (không migration) có được không?

## 9. Ngoài phạm vi

Đẩy tiến độ qua WebSocket, xem đáp án từng câu, thao tác lên lượt thi từ màn này, thống kê theo ca. Ý tưởng thêm thì đề xuất riêng, không cài trong T2-B5.
