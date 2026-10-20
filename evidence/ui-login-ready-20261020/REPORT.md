# Login TOEIC Room và chẩn đoán READY — 20/10/2026

Phạm vi người dùng xác nhận: triển khai login theo mockup cáo và kiểm tra READY. Base
`main d2c8f01ca2063aa10d797c86e1c1e7e2b62d273a`; thay đổi làm việc chưa commit.
Owner B; A cần review phần trình bày đăng nhập và callback xác thực. Không đổi API,
role server, database schema, wire, policy Listening hoặc điều kiện START.

## Giao diện

Login mới dùng minh họa người dùng cung cấp, form JavaFX thật, CSS riêng, URL trong
“Kết nối máy chủ”, ẩn/hiện mật khẩu, chặn gửi lặp khi request đang chạy và xóa cả hai
ô mật khẩu khi server trả lời. Ảnh nạp nền. Một ứng dụng vẫn phục vụ hai role theo
response server. Ảnh tham chiếu ghi “Thí sinh” không được dùng để tự cấp role.
Phòng chờ V2 thêm mã ca/mã lượt do preparation trả về để đối chiếu với giám thị.

## READY trong ca người dùng cung cấp — chỉ đọc DB

- Ca đang theo dõi: `cdfde543-21ff-4ae4-9ad7-5373c78ddadd`, PROCTORED_V2/SCHEDULED.
- Lượt tương ứng: `a222e5c3-314f-441b-8553-186837b97c58`, ACTIVE, writer 1;
  không có bản ghi audio hoặc visual READY ở thời điểm đọc.
- Phòng chờ thí sinh đang hiện Audio + Visual READY. Đọc DB xác nhận lease còn hạn
  cho lượt `9149ff71-5ece-4700-9f81-90a2c1a0f11f`, writer 2, ca
  `904808d3-c902-4302-a732-a35acb5fc0c2`.
- Kết luận: hai cửa sổ đang thao tác hai ca/lượt khác nhau. Không sửa READY/epoch/DB
  hoặc bỏ gate. Thí sinh cần chọn lượt của ca giám thị đang theo dõi, hoặc giám thị
  theo dõi đúng ca thí sinh thực sự đang chuẩn bị; không tự START thay người dùng.

## Kiểm chứng

JDK 21.0.8 / JavaFX 21.0.12 / Maven 3.9.11 / Windows 11 x64.

Lệnh focused: bật `TOEIC_LOGIN_GUI=true`, `TOEIC_RICH_GUI=true`, chạy
`mvn -o -pl client -am test -Dtest=LoginSceneGuiTest,LoginApiClientTest,RichExamViewGuiTest,VisualReadyCoordinatorTest,AudioPreparationTest -Dsurefire.failIfNoSpecifiedTests=false`.

Lượt cuối **68 PASS, 0 FAIL, 0 ERROR, 0 SKIP**:
LoginSceneGuiTest 3, LoginApiClientTest 39, RichExamViewGuiTest 7,
VisualReadyCoordinatorTest 9, AudioPreparationTest 10. HTTP input/response MOCK;
JavaFX Stage và HTTP cục bộ thật, không ghi DB. Không cộng các lượt lặp thành case mới.
[Ảnh rộng](login-wide.png) 1367×820; [ảnh compact](login-compact.png) 726×523,
đã xem lại, không credential. Tương tác GUI là điều khiển lập trình, không nghiệm thu người dùng.

Lịch sử: lượt đầu 58 PASS/10 SKIP do biến GUI chưa được truyền đúng qua CMD/WSL;
không dùng làm GUI PASS. Lượt tiếp 68 PASS nhưng test resize chụp lại kích thước cũ;
đã giữ ảnh đầu trong ignored target và sửa test chờ resize thực, lượt cuối ở trên.
Log cục bộ `/tmp/toeic-login-ready-final-tests-20261020.log`; XML trong
`client/target/surefire-reports/`. Packaging và mở EXE ghi bổ sung sau khi thực hiện.

NOT RUN: full regression, installed MSI, LAN/máy thứ hai, hearing, production login
bằng tài khoản người dùng trong bản mới. Hai helper smoke hiện có được đổi sang ID
control để không phụ thuộc thứ tự input; chưa chạy hai smoke này trong phiên.
Không claim UI-01/UI-03/EX-06 DONE; tracker/giờ công/contract giữ nguyên.

## Đóng gói

`mvn -o -pl client -am package -DskipTests`: PASS (không tính là lượt test).
Jpackage app-image mới PASS, có bundled runtime; JAR trong image khớp SHA-256 với bản
vừa build. [Receipt](verification.json). Artifact local:
`jpackage-out/login-ready-20261020-2307/ToeicMonitor/ToeicMonitor.exe`.
Hai cửa sổ ca thi cũ được giữ; mở một cửa sổ login mới để người dùng tự đăng nhập.

Native EXE đã mở: PID17132, title TOEIC Room, Responding=true; server8080 trả401 trước xác thực. Không đăng nhập thay người dùng; hai cửa sổ ca thi trước vẫn giữ.
