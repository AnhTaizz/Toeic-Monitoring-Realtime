# MON-06 — activity và idle trong ứng dụng thi

## Phạm vi và quyền riêng tư

MON-06 là task P1, owner/reviewer B/C, phụ thuộc MON-07 và EX-06. Nhánh này được triển khai
theo yêu cầu người dùng nhưng vẫn cần reviewer C trước khi đánh dấu DONE. Runtime dùng chung
`CandidateExamSignalMonitoring`, run/sequence/queue/capability với MON-03/04/05 và mặc định OFF:

```text
-Dtoeic.monitoring.signal-experiment-enabled=true
```

Hai màn thi legacy và rich chỉ phát marker nội bộ có một trong hai kind:

- `NAVIGATION`: người dùng chuyển sang nhóm/đơn vị/câu khác.
- `ANSWER_ACTION`: `ExamSession` thực sự chấp nhận chọn, đổi hoặc bỏ đáp án.

Marker không có question ID, option ID, prompt, answer key hoặc text. Monitor không đăng ký
key event, không đọc phím bấm, clipboard, URL/browser, chat, file riêng hay nội dung màn hình.
Thao tác bị `ExamSession` từ chối và chọn lại cùng đáp án không tăng count.

## Typed summary và thời gian

`EXAM_ACTIVITY_SUMMARY`, source `JAVAFX_EXAM_ACTIVITY`, chỉ chứa:

```text
navigationCount, answerActionCount, idleMillis?, reason
```

Hai count là tổng tích lũy của attempt/run, không phải timeline từng câu. Snapshot đầu là
`INITIAL`. Snapshot định kỳ là `INTERVAL`. Khi idle lần đầu vượt threshold, monitor phát
`IDLE_THRESHOLD` đúng một lần cho tới khi có activity hợp lệ; đây là số đo trung tính, không
phải nhãn gian lận hay lệnh phạt.

Monitor dùng monotonic clock. Nếu clock chạy lùi hoặc khoảng giữa hai tick vượt giới hạn,
nó phát `PARTIAL/CLOCK_GAP` và bỏ `idleMillis`, sau đó rebase; không biến khoảng không quan sát
được thành bằng chứng idle. Idle hợp lệ bị giới hạn wire ở 0..86.400.000 ms.

| Property | Mặc định | Miền hợp lệ |
|---|---:|---:|
| `toeic.monitoring.activityReportMillis` | 30.000 ms | 5.000–300.000 ms |
| `toeic.monitoring.activityIdleMillis` | 60.000 ms | 10.000–3.600.000 ms |
| `toeic.monitoring.activityMaxClockGapMillis` | 10.000 ms | 2.000–300.000 ms |

Timeline kiểm tra mỗi 1 giây. Khi attempt/lane đóng, handler và Timeline được gỡ, callback cũ
không phát thêm. Activity chỉ khởi động sau khi capability quảng cáo type này. Server cũ thiếu
activity vẫn giữ MON-03/04/05; nếu capability activity bị mất sau khi đã chạy, activity được
retire cho phần còn lại của run thay vì bind lần hai.

Flyway V18 chỉ mở CHECK constraint cho type/source/quality mới và không sửa migration cũ.
Flag OFF vẫn có thể apply DDL theo chính sách Flyway hiện hành, nhưng không đăng ký signal service.

## Kiểm tra

Focused logic + transport + JavaFX Stage:

```powershell
$env:TOEIC_RICH_GUI='true'
mvn --% -Dmaven.repo.local=C:\Users\Lenovo\.m2\repository -pl client -am -Dtest=ActivitySignalTest,ActivitySignalAdapterTest,FxActivityMonitorTest,SignalRunContextTest,RealtimeClientTest,RichExamViewGuiTest#acceptedNavigationAndAnswerEmitOnlyPrivacyMinimalKinds -Dsurefire.failIfNoSpecifiedTests=false test
```

Checklist manual/REAL integration:

1. Bật experiment ở server/client, vào attempt ACTIVE và xác nhận baseline `INITIAL,0,0,0`.
2. Chuyển câu/nhóm và chọn/đổi/bỏ đáp án; đối chiếu count tăng, payload không có ID/nội dung.
3. Chờ qua idle threshold, xác nhận một `IDLE_THRESHOLD`; tiếp tục thao tác rồi chờ lại để xác
   nhận threshold được reset.
4. Submit/timeout/rời ca, tiếp tục thao tác UI khác và xác nhận không có summary mới.
5. Qua REAL WS/HTTP/PostgreSQL, kiểm history/capability V18, ACK sau COMMIT và quyền candidate/proctor.
6. Ghi SHA, Windows/JDK/PostgreSQL, attempt ID và ACTUAL/NOT RUN; không ghi credential.

JavaFX Stage test dùng UI thật nhưng gateway/data là MOCK. Nó không thay REAL WS/PG hay human review.
