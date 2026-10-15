# Tích hợp Reading, Listening và history

Nhánh `integration/reading-listening-20261015` tạo từ `origin/main` `8f5ac40` đã gồm Listening B1–B3. History merge từ PR #28 `2ca4121`; Reading lấy từng file đã đối chiếu từ nhánh local `fix/b-history-id-cursor-20261014` `65f5123` (kế thừa B `d425182`). Không merge toàn bộ PR #17 hoặc đưa pipeline audio spike cũ vào.

## Quyền ghi và vòng đời

Candidate vào phòng thi qua `ExamEntry`: đọc trạng thái, kiểm đề, rồi takeover đúng một lần nếu ACTIVE. History/result chỉ đọc server, không takeover. `CandidateExamView` sở hữu một `ExamSession` và một model đáp án cho cả Reading/Listening. Nếu đề có Listening, view tạo `AudioPreparationView` cho đúng attempt và truyền writer hiện tại; chuẩn bị và READY không gọi acquireWriter.

Writer bị thay, bài đã chốt hoặc màn thi đóng: đóng audio/MediaPlayer, bỏ subscription/worker và không nhận callback generation cũ. Lấy lại quyền ghi chỉ do thao tác rõ ràng của ExamSession; audio mới nhận epoch được server cấp và cần chuẩn bị lại. Reconnect đối chiếu status; không takeover hoặc autoplay. Marker reserve vẫn chặn start trùng qua lần mở view/app.

Server báo Listening gián đoạn: player dừng; session đọc status rồi giữ nguyên các đáp án Listening server đã commit. Thay đổi Reading chưa lưu vẫn được giữ với revision không giảm và payload full-map mới; không gửi lại payload Listening cũ. Lưu/nộp/timeout/quyền/khóa DB và deadline vẫn do backend quyết định.

## ID và URL

History/cursor dùng raw UTF-8, tối đa128 Unicode code point, không trim/normalize; thứ tự unsigned UTF-8 đúng PostgreSQL COLLATE C. `GET /api/v1/attempts/status?attemptId=...` là alias chỉ đọc, kiểm owner/assigned proctor tại service như route cũ. Chỉ một query attemptId; thiếu/rỗng/NUL/129/lặp/field lạ bị từ chối. Client dùng alias cho slash và dot-segment, percent-encode đúng một lần. Không thay Tomcat để nhận encoded slash trong path.

Scope HTTP có thể chứa ID DB hợp lệ và không được làm cả login/history thất bại. Scope này không cấp quyền gửi monitoring: envelope/heartbeat v0 và collector IDs giữ regex ASCII cũ. UI từ chối vào thi với ID chưa tương thích monitoring; không mở một bài thi không giám sát. A/B/C cần chốt phương án ID cho lượt ACTIVE đặc biệt; alias status không tự mở rộng protocol monitoring. Các đường ghi/audio cho slash chưa được đổi trong phiên này.

## Kiểm chứng và tái chạy

Trên Windows/JDK21 và PostgreSQL cấu hình trong `.env`, bật `TOEIC_PG_INTEGRATION=true`, chạy `mvn -B -ntp clean test` rồi `mvn -B -ntp package`. Các runtime test tự tạo/xóa schema riêng, không reset public/volume. Runner của phiên dùng cache Maven offline; credentials chỉ qua biến môi trường.

Smoke dùng server Boot JAR để cung cấp JDBC runtime, client fat JAR và test classes:

```powershell
java -Dloader.path=client/target/test-classes,client/target/client-0.1.0-SNAPSHOT-all.jar `
  -Dloader.main=vn.edu.toeic.client.integration.ReadingListeningPostgresGuiSmoke `
  -cp server/target/server-0.1.0-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher
```

Cần nạp DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD/TOEIC_SEED_PASSWORD trước; không đưa chúng vào argv/log. Harness tạo schema `integration_gui_*`, đề/WAV TEST, chạy Application/Media/JAR/HTTP/WS/PG thật và dọn đúng server/schema nó sở hữu. Click xác nhận nghe là SIMULATED; không thay người nghe thủ công. GUI manual, LAN hai máy, codec MP3/M4A, installer và E1/E2 vẫn cần nghiệm thu riêng.

Kết quả cuối và SHA/code/artifact/log nằm ở [evidence mới](../evidence/integration/2026-10-15-verification.md). Không dùng số test của các nhánh nguồn làm bằng chứng bản ghép.
