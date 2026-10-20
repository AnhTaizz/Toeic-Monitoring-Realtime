# Trang chủ thí sinh và chọn đúng ca READY — 20/10/2026

Người dùng yêu cầu sửa tiếp trang chủ còn giao diện tối và kiểm tra READY. Base main
d2c8f01; giữ login/records từ lượt trước và script server dirty có sẵn. Owner B; A review
metadata/scope/entry. Không sửa protocol/server/collector/schema/gate READY hoặc START.

## Thay đổi

CandidateHomePane/CSS sáng với ảnh cáo người dùng cung cấp; preview tên đề, số câu,
thời lượng/trạng thái từ server, mã ca và mã lượt có thể sao chép. Dropdown dùng metadata
trang lịch sử đầu có giới hạn để ghi mã ca/tên đề, không thêm ID ngoài fresh auth scope.
ID chưa có metadata vẫn ghi “Lượt”; preview đọc riêng lượt được chọn. Nhiều lượt ACTIVE
thì không tự chọn UUID đầu tiên. Giữ lựa chọn rõ ràng còn quyền khi refresh; mất quyền
thì xóa chọn nếu còn nhiều lượt. Nút vào phòng chờ khóa khi chưa chọn. Metadata nạp
ở worker, generation/close guard loại phản hồi cũ. Network lock bao control của tab.
Ảnh nạp nền, viewport giữ tỷ lệ; lịch sử/kết quả và manual monitoring vẫn có.
Trang chủ không gửi READY, không tải đề trước START hoặc tự cấp writer/role.

## Chẩn đoán runtime — chỉ đọc

Ca đang theo dõi: cdfde543-21ff-4ae4-9ad7-5373c78ddadd / lượt
a222e5c3-314f-441b-8553-186837b97c58: SCHEDULED/ACTIVE writer1, chưa audio/visual READY.
Ca 904808d3-c902-4302-a732-a35acb5fc0c2 / lượt9149ff71-5ece-4700-9f81-90a2c1a0f11f:
SCHEDULED/ACTIVE writer2, audio/visual writer2, lease còn hạn khi đọc. UI có hai cửa sổ
candidate: một ở portal, một ở phòng chờ ca904808d3 đã READY. Đây là snapshot lúc đọc.
Không chuyển READY giữa ca, không xác nhận nghe được hoặc START thay người dùng.

## Kiểm thử và giới hạn

Focused cuối + package: **84 PASS, 0 FAIL/ERROR/SKIP**, Windows/JDK21.0.8/Maven3.9.11.
AttemptRefresh13, HomeGui3, LoginGui3, LoginApi39, RichGui7, Visual9, Audio10.
MOCK input/API, Stage và HTTP cục bộ thật, thao tác lập trình, không ghi DB.
Bật TOEIC_LOGIN_GUI/TOEIC_RICH_GUI=true; chạy `mvn -o -pl client -am package
-Dtest=CandidateHomeGuiTest,AttemptRefreshTest,LoginSceneGuiTest,LoginApiClientTest,RichExamViewGuiTest,VisualReadyCoordinatorTest,AudioPreparationTest
-Dsurefire.failIfNoSpecifiedTests=false`. Không cộng các lượt lặp thành case mới.
[Ảnh rộng](home-wide.png)1367×820 và [ảnh compact](home-compact.png)806×613 đã xem lại.
Lượt đầu3FAIL do ba tham số test còn đòi auto-select ca đầu: cập nhật kỳ vọng theo UX mới;
giữ XML/ảnh trong ignored client/target/candidate-home-first-run và log cũ ở /tmp.
Log cuối /tmp/toeic-candidate-home-verified-20261020.log; XML client/target/surefire-reports.
Full suite/MSI/LAN/máy hai/hearing/production signin trên image mới NOT RUN.
Không DONE/tracker/hours/commit/push/merge. Native image/open bổ sung sau khi thực hiện.

## Native image

App-image `jpackage-out/home-ready-20261020-2338/ToeicMonitor` build PASS, bundled Java21,
JAR hash khớp bản package84PASS. [Receipt](verification.json).
Lượt gọi Python jpackage ban đầu bị automatic permission review timeout trước thực thi;
retry bằng jpackage trực tiếp PASS. Read-only pre-close probe cũng timeout trước thực thi,
retry psql trực tiếp PASS: cả hai lượt vẫn SCHEDULED/ACTIVE, revision0, đáp án rỗng.
Không suy ra các thao tác này không an toàn từ timeout. Không force kill/clean/DB reset.

Native launch PASS: đóng bình thường PID17132/28448 sau pre-start/no-answer check; không kill. Hai cửa sổ mới PID16748/13340, title TOEIC Room, responsive=true, để người dùng tự đăng nhập. READY cũ ở ca904808d3 sẽ hết lease sau khi đóng; phải chuẩn bị đúng ca trong bản mới.
