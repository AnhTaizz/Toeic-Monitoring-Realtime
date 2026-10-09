# Bằng chứng kiểm thử — T2-B1 đến T2-B4 (luồng thi JavaFX, PR #17)

- **Ngày chạy:** 09/10/2026 (UTC+7)
- **Người chạy:** phiên Agent làm vai B. Chưa có thành viên nào tự thao tác hay review; mục nào ghi "agent-driven" là do Agent điều khiển, không phải kiểm thử tay của nhóm.
- **Nhánh:** `test/javafx-full-ui-testing` (PR #17), base `main` `8eba405`
- **SHA code được kiểm:** `e362ed9` cho mục 2–4; lượt bổ sung ở mục 4b, bản build và smoke cuối chạy trên `d50891e` (thêm `probeSession`, làm mới danh sách lượt thi, nhãn xung đột)
- **Môi trường:** Windows 11 x64, Oracle JDK 21.0.8, Maven 3.9.11, PostgreSQL 18.6 (container `toeic-db`), Docker 29.4.3
- **Trạng thái chung:** T2-B1, T2-B2 **CODE_COMPLETE**; T2-B3, T2-B4 **PARTIAL** vì ba điểm phía server ở mục 5. `TRACKER.json` không đổi.

## 1. Lệnh đã chạy

```powershell
mvn test
mvn package

# Smoke với server và PostgreSQL thật (nạp DB_* từ .env trước, sau mvn package)
$gson = "$env:USERPROFILE\.m2\repository\com\google\code\gson\gson\2.13.2\gson-2.13.2.jar"
java -cp "client\target\test-classes;client\target\classes;protocol\target\classes;$gson" `
  vn.edu.toeic.client.exam.ExamFlowPostgresSmoke
```

## 2. Kết quả

| Phạm vi | Loại | Kết quả | Bằng chứng |
|---|---|---|---|
| `mvn package` toàn repo | JUnit | **PASS** 441/441 (protocol 23, client 292, server 125, spike 1); trước khi sửa là 376. Sau lượt bổ sung: 442/442 (client 293) | `mvn-package-summary.txt` |
| Test mới của màn thi | MOCK gateway + đồng hồ ảo; 1 test luồng thật | **PASS** 65/65 | 4 lớp `Exam*Test` trong `mvn-package-summary.txt` |
| `ExamFlowPostgresSmoke` | REAL Spring Boot jar + PostgreSQL + HTTP; không GUI, không WS | **PARTIAL**: 28 kiểm tra PASS, 3 GAP phía server, 0 FAIL | `exam-flow-smoke.txt` |
| Giao diện JavaFX thật | REAL app + server + PostgreSQL, agent-driven bằng Windows UI Automation | **PASS** các bước đã chạy ở mục 4 và 4b; phần hết giờ bị chặn bởi server | 9 ảnh `gui-*.png` |
| Thành viên tự thao tác GUI | Tay | **NOT RUN** | checklist mục 6 |
| Máy Windows thứ hai / LAN | Tay | **NOT RUN** | — |
| Review của A (A xem B) | Người | **NOT RUN** | — |

Smoke và lượt GUI đều chạy server trên một schema tạm (`t2b_smoke_*`, `t2b_gui_*`) và đã xóa đúng schema đó khi xong. Schema `public` của DB dev không bị đụng tới; không reset DB.

## 3. Mười hai tình huống bắt buộc và test tương ứng

Tất cả nằm trong `client/src/test/java/vn/edu/toeic/client/exam/`. Cột REAL ghi tình huống nào còn được chạy lại với server thật trong smoke.

| # | Tình huống | Test (MOCK) | REAL |
|---|---|---|---|
| 1 | Chọn và đổi đáp án | `selectingAndChangingAnAnswerRaisesRevisionAndSendsTheWholeMapOnce` | smoke, GUI |
| 2 | Xóa đáp án | `clearingAnAnswerRemovesItFromTheFullMap` | smoke, GUI |
| 3 | Sửa liên tục khi autosave trước chưa ACK | `changesMadeWhileASaveIsInFlightAreSentAsSoonAsItCompletes`, `ExamSessionThreadingTest` | smoke |
| 4 | ACK cũ đến sau khi revision trên máy đã tăng | cùng test ở dòng 3, `responseForAnotherRevisionIsNotTreatedAsAnAck` | — |
| 5 | Autosave timeout hoặc lỗi | `timeoutRetriesTheSameRequestThenStopsAndWaitsForTheUser`, `rejectedSaveIsNotRetriedAutomaticallyButANewChangeIsSent` | smoke |
| 6 | Retry không đổi payload | `retryKeepsItsPayloadWhenAnswersChangeMeanwhile`, `snapshotSentToServerIsImmutable` | smoke (mất ACK → `ALREADY_SAVED`) |
| 7 | Nộp khi autosave còn đang gửi | `submitWhileAutosaveIsInFlightFreezesTheFinalAnswersAndIgnoresTheLateSave` | — |
| 8 | Nộp trùng | `secondSubmitDoesNotSendASecondRequest`, `lostSubmitResponseIsResolvedFromServerStatusWithoutResending`, `unconfirmedSubmitIsResentUnchangedWhenServerIsStillActive` | smoke, GUI |
| 9 | Hết giờ trong lúc HTTP đang chạy | `deadlinePassingWhileASaveIsInFlightLocksEditingAndAsksTheServer`, `deadlinePassingWhileSubmitIsInFlightWaitsForTheSubmitOutcome`, `localClockExpiredButServerStillActiveKeepsEditingLockedAndStopsPolling` | smoke, GUI (dừng ở GAP 2) |
| 10 | Mất kết nối và kết nối lại | `losingTheWebSocketLocksEditingButDoesNotChangeWhatTheServerConfirmed`, `failedReconciliationKeepsEditingLockedAndRetriesWithABound` | GUI (tắt/bật server) |
| 11 | `writerEpoch` cũ | `staleWriterEpochBlocksThisSessionUntilItIsGrantedANewEpoch`, `submitFromAReplacedWriterIsNotReportedAsSubmitted` | smoke, GUI (hai cửa sổ) |
| 12 | Dọn dẹp khi đóng | `closingCancelsThePendingDebounce`, `closingIgnoresLateResultsAndFurtherCalls`, `closingStopsTheTimerThreadTheSessionOwns`, `closedClientRejectsCallsAndStopsItsWorkerThreads` | GUI (đóng cửa sổ, JVM thoát) |

AT11 (server 40 / client 42, mất ACK, writer mới): `at11ServerAt40ClientAt42KeepsLocalAnswersAndResendsTheSameRequest`, `at11LostAckIsMarkedSavedWhenServerAlreadyHoldsThePendingRevision`, và trong smoke với server thật ở revision 7 / client 9.

## 4. Giao diện thật, agent-driven (Windows UI Automation)

Client chạy từ `client-0.1.0-SNAPSHOT-all.jar`, server từ `server-0.1.0-SNAPSHOT.jar`. Đọc nhãn và trạng thái nút qua UI Automation; ảnh chụp bằng `PrintWindow` chỉ chứa cửa sổ ứng dụng.

| Bước | Quan sát được |
|---|---|
| Giám thị đăng nhập, bấm tạo đề và ca mẫu | Roster hiện hai lượt của ca mới |
| Thí sinh 1 đăng nhập, vào phòng thi | Màn thi mở, `Writer Epoch: 2`, `Chưa tính giờ`, 10 câu. Trên bản PR gốc `0a4ea38` bước này không thể qua vì lỗi parse `Instant` |
| Chọn A | 150 ms sau: `Chưa lưu — đang chờ gửi revision 1`. 3 giây sau: `Đã lưu (server xác nhận revision 1)`, đồng hồ bắt đầu `00:09:58` |
| Đổi sang C, bỏ chọn, chọn B | Lần lượt được xác nhận revision 2, 3, 4; `Đã chọn` về 0 rồi lên 1 |
| Bấm ô 9 trên bảng câu hỏi | Một trang có đoạn văn và cả câu 9, 10; trả lời hai câu, xác nhận revision 6 (`gui-exam-saved.png` là bố cục sau khi sửa) |
| Tắt server | `REALTIME: RECONNECTING`, lựa chọn bật 0/8, nhãn lưu giữ nguyên `Đã lưu … revision 6`, nút nộp vẫn bật |
| Bật lại server | `REALTIME: CONNECTED`, lựa chọn bật 8/8, revision không đổi |
| Nộp bài, xác nhận | Hộp thoại `SERVER ĐÃ GHI NHẬN BÀI NỘP`, 2/10 (Listening 0, Reading 2), khớp đáp án đã chọn (`gui-submit-confirm.png`, `gui-result-submitted.png`) |
| Thí sinh 2 mở cùng lượt trên hai cửa sổ | Cửa sổ thứ hai nhận `Writer Epoch: 3`; cửa sổ đầu sau lần sửa kế tiếp báo bị thay phiên ghi, lựa chọn bật 0/4, hiện nút lấy lại quyền ghi (`gui-writer-replaced.png`) |
| Bấm lấy lại quyền ghi | `Writer Epoch: 4`, nạp bản server revision 1, bộ đếm trên máy vẫn là 2; cửa sổ kia đến lượt bị thay |
| Đóng cửa sổ khi đang ở màn thi | JVM thoát trong 15 giây ở cả hai cửa sổ |
| Ca 25 giây, chờ về `00:00:00` | Lựa chọn bật 0/4, nút nộp tắt, không có hộp thoại nào mở, không tự nộp (`gui-deadline-locked.png`); sau 5 lần hỏi thì hiện `Server chưa chốt lượt thi…` và nút `Thử lại` (`gui-deadline-server-undecided.png`) |

### 4b. Lượt bổ sung cùng ngày (agent-driven)

Bốn nhánh trước đó mới có test MOCK, nay đã chạy trên giao diện thật. Dữ liệu của tình huống xung đột và thu hồi phiên được tạo bằng SQL trên schema tạm của lượt chạy, không phải trên schema `public`.

| Bước | Quan sát được |
|---|---|
| Sửa `answers_json` của lượt thi trên server (cùng `saved_revision`), tắt/bật server để client đối chiếu lại | Màn thi báo "Bản trên máy và bản trên server cùng revision nhưng khác nội dung", lựa chọn bật 0/4, nút nộp tắt, hiện nút "Nạp bản của server" (`gui-conflict.png`) |
| Bấm "Nạp bản của server", rồi chọn câu khác | Lựa chọn bật 4/4; lần lưu kế tiếp được server xác nhận revision 2, hàng trên server là `{"L1":"B"}` |
| Tắt server cho tới khi realtime hết lượt thử lại | `REALTIME: FAILED`, thông báo "Kết nối giám sát đã dừng hẳn…", lựa chọn bật 0/4, nhãn lưu giữ nguyên (`gui-realtime-failed.png`) |
| Bấm "Rời phòng thi", xác nhận | Về màn thí sinh; bật lại server, đăng nhập lại và vào lại lượt thi thì nhận `Writer Epoch: 3` và đúng bản server ở revision 2 |
| Thu hồi phiên đăng nhập của thí sinh khi đang ở màn thi | Ứng dụng tự về màn đăng nhập với dòng "Phiên hết hiệu lực. Hãy đăng nhập lại." (`gui-session-expired.png`). Trước khi thêm `probeSession`, màn thi chỉ báo mất kết nối và đứng yên |
| Nộp bài rồi bấm "Hoàn Tất & Thoát" | Kết quả server 0/10 khớp đáp án đã chọn; màn thí sinh tự làm mới danh sách và báo "Chưa được cấp lượt thi nào", nút vào phòng thi tắt |

Vẫn chưa chạy trên GUI: nộp bài khi mất response (đã chạy trong smoke REAL) và hiển thị kết quả `TIMED_OUT` (bị GAP 1 và 2 chặn).

## 5. Ba điểm phía server đang chặn nghiệm thu (cần vai A)

Vai B không sửa server. Client đã xử lý để không báo sai, nhưng không tự bù được.

1. **Lượt đã chốt bị trả 403 khi đọc trạng thái.** `JdbcAttemptScopeStore.canAccess` chỉ nhận `a.state = 'ACTIVE'` (dòng 17), và `ExamService.getAttemptStatus` (dòng 982) cùng `getCandidateExam` (dòng 341) đi qua kiểm tra đó. Chính thí sinh sở hữu cũng nhận `FORBIDDEN` sau khi lượt `SUBMITTED` hoặc `TIMED_OUT`. Hệ quả: không đọc lại được kết quả, không mở lại được lượt đã chốt, và sau này màn giám thị cũng không đọc được tiến độ lượt đã nộp. Smoke: dòng `GAP GET /status của lượt đã SUBMITTED trả FORBIDDEN`.
2. **Job hết giờ không chạy trong bản jar.** `ExamTimeoutService.processTimeouts` có `@Scheduled` (dòng 22) nhưng trong `server/src/main` không có `@EnableScheduling`; test của A gọi hàm này bằng tay (`ExamSubmitPostgresSmoke` dòng 181). Lượt quá hạn vẫn `ACTIVE` vô thời hạn, không có điểm hết giờ. Smoke: quá deadline hơn 3 giây, autosave bị `EXPIRED`, status vẫn `ACTIVE`.
3. **Deadline chỉ được đặt ở lần autosave đầu tiên** (`ExamService` dòng 138); ca tạo ra với `deadline_at` rỗng và response autosave không mang deadline. Thí sinh chưa chọn câu nào thì không bị tính giờ. Client phải đọc thêm status sau ACK đầu để biết deadline.

Ghi nhận thêm: mẫu JSON của `GET /status` trong `docs/PROTOCOL.md` và `docs/GIAO_DICH_VA_QUYEN.md` có `candidateUsername`, `startedAt`, `"score": null`, khác record `CandidateAttemptStatusResponse` thật; server từ chối `answerRevision = 0` khi nộp nên client nộp bài chưa sửa lần nào với revision 1.

## 6. Checklist cho thành viên tự kiểm (đang NOT RUN)

Chuẩn bị: `docker compose up -d --wait`, chạy server, mở hai client. Lưu ý chạy server bản này trên DB dev sẽ để Flyway áp V5–V7 lên schema `public`.

1. Giám thị bấm tạo đề và ca mẫu; thí sinh bấm làm mới danh sách rồi vào phòng thi.
2. Chọn, đổi, bỏ chọn; nhãn lưu phải đi từ "Chưa lưu" sang "Đã lưu (server xác nhận revision N)".
3. Bấm nhanh nhiều câu liên tiếp; revision server xác nhận cuối cùng phải bằng revision trên máy.
4. Tắt server giữa chừng: lựa chọn phải khóa, nhãn lưu không được tự đổi thành "Đã lưu". Bật lại: mở khóa.
5. Mở cùng tài khoản trên client thứ hai và vào cùng lượt; quay lại client đầu sửa một câu, phải thấy báo bị thay phiên ghi.
6. Nộp bài; kết quả phải khớp số câu đúng theo đề mẫu.
7. Tạo ca ngắn bằng `-Dtoeic.sample.durationSeconds=30` trên client giám thị; để hết giờ; client phải khóa, không tự nộp. Với server hiện tại sẽ dừng ở "Server chưa chốt lượt thi" (GAP 2).
8. Đóng cửa sổ khi đang thi; trong Task Manager không còn tiến trình `java` của client.
9. Lặp lại bước 1–6 với client trên máy Windows thứ hai qua LAN.
