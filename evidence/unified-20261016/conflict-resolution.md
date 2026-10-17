# Hòa PR #35/#38 với main B4/B5

Base đã fetch: main `c0fd9036ba340b0b0a686fb2ce637050b9a3b7b4`, PR #35 `11c5da9ad3094a103cd6db93693aa64bbb132eaa`, PR #38 `6d09d80263b567f05c67043bc423165eadd4026d`. Merge-base main với hai PR: `8f5ac40e6b4493d6a8e81084b735447d92da8a13`. #36/#37 đã merged; #35/#38 vẫn Draft lúc bắt đầu. Worktree/branch mới từ origin/main, không reset/rebase/force-push nguồn. Checkpoint merge #35 `456583b` chưa được dùng làm bằng chứng PASS; code được kiểm tiếp sau khi hòa #38.

## PR #35

- `docs/NHAT_KY.md`: Giữ nguyên cả hai phần lịch sử/bằng chứng độc lập, không kế thừa PASS cho bản ghép.
- `docs/KIEM_THU.md`: Giữ nguyên cả hai phần lịch sử/bằng chứng độc lập, không kế thừa PASS cho bản ghép.
- `evidence/README.md`: Giữ nguyên cả hai phần lịch sử/bằng chứng độc lập, không kế thừa PASS cho bản ghép.
- `docs/QUYET_DINH.md`: QD-28 MSI main giữ nguyên; shared writer của PR35 đổi số QD-30 vì trùng số; giữ QD-29 ID và toàn bộ QD-23.
- `docs/TIEN_DO.md`: Giữ các mốc nguồn có SHA; dòng A/B chuyển sang bản thống nhất IN_PROGRESS, không nhận PASS lịch sử.
- `client/src/main/java/vn/edu/toeic/client/dashboard/ProctorDashboardView.java`: Giữ sessionControls và close của B4; Reading/Listening Application từ35. Không mang nút demo SampleExamSeeder vào dashboard để chỉ còn một luồng import/tạo ca B4.

## PR #38

- `client/src/main/java/vn/edu/toeic/client/dashboard/ProctorDashboardView.java`: Một tab B4/main và một tab progress38; giữ scoped monitoring, style contrast và đóng đủ API/children. Không seed demo/khởi tạo controller38.
- `client/src/main/java/vn/edu/toeic/client/dashboard/ProctorSessionView.java`: Bỏ bản trùng chức năng từ38; chuyển guard và regression cần thiết vào controller/test B4 main.
- `client/src/main/java/vn/edu/toeic/client/dashboard/ProctorWorkflow.java`: Bỏ bản trùng chức năng từ38; chuyển guard và regression cần thiết vào controller/test B4 main.
- `client/src/test/java/vn/edu/toeic/client/dashboard/ProctorWorkflowTest.java`: Bỏ bản trùng chức năng từ38; chuyển guard và regression cần thiết vào controller/test B4 main.
- `scripts/package-windows.ps1`: Giữ toàn bộ pipeline MSI #37 đã đối chiếu; không mang Mode/AppImage/EXE builder38. Preflight/runtime launcher sẽ được bổ sung riêng và kiểm lại.
- `docs/QUYET_DINH.md`: Giữ QD28 MSI/QD30 writer và đề xuất QD29, E1 pending review; không tự phê duyệt.
- `docs/TIEN_DO.md`: Giữ mốc nguồn nhưng dòng hiện tại thuộc nhánh thống nhất; NOT RUN tới khi có test thật.
- `docs/KIEM_THU.md`: Giữ evidence B4/MSI main tại bảng LT04/IT01; các subset38 giữ ở báo cáo gốc. Kết quả unified chưa được tuyên bố PASS.

## Các điều chỉnh sau khi chọn nền

- B4 duy nhất là `client.proctor.ProctorSessionController/View/ApiClient` của #36. Chuyển guard READY 5 giây monotonic, khóa đổi ca khi start chưa rõ, đọc trạng thái server khi mất response, giữ nguyên retry intent, explicit confirmation và callback/close vào nền đó. Không khởi tạo workflow/controller B4 thứ hai. Raw legacy result dùng query status chỉ đọc, không gọi Listening v0 khi ID không tương thích.
- Progress của #38 vẫn dùng endpoint committed/scoped riêng. Controller progress mới chỉ quản lý một trang đọc, không điều khiển ca: một request đang chờ, cursor chỉ tiến sau response hợp lệ, lỗi giữ nguyên trang/readAt và gắn stale, 401 hết phiên, close bỏ callback. Có regression riêng và GUI production.
- Giữ `package-windows.ps1` MSI #37, upgrade UUID/version/asInvoker/manifest/per-user và production-JAR guard. Không lấy builder EXE/Mode của #38. Chuyển config runtime, x64/JavaFX/preflight và QA smoke sang hợp đồng MSI. `package-t3b1.ps1` giữ AppVersion/PlainImage/java.instrument của main và sửa absolute AudioSmoke.properties từ #38.
- User config tối đa 4 KiB đọc trước JavaFX qua Launcher, ngoài install. Thứ tự JVM property > environment > user file > default; ô login do người dùng chỉnh là giá trị sử dụng. Invalid file có thông báo an toàn, không lộ nội dung. Wrapper runtime có argument override; không nhúng URL/token/DB password vào JAR/MSI.
- TEST agent ngoài MSI được đổi sang CandidateExamView/shared writer thực; thêm app-image test opt-in có nhãn APP_IMAGE riêng. Cài/gỡ/quyền install vẫn chỉ thuộc test INSTALLED và không được kế thừa.
- Không sửa raw/checksum E1 cũ. Collector/harness/policy được đối chiếu trực tiếp với #38; smoke mới có runId/source riêng, không nhận số liệu lịch sử làm phép đo của bản ghép. FULL vẫn mặc định. Human A/C/QD-29/E1 review chưa có.

- Smoke C1 GUI sau merge: cleanup gọi stage.close khi Stage chưa được gán, che lỗi khởi tạo ban đầu. Cập nhật hai TEST harness C1/C2: null-safe cleanup và FX callback tối đa20s cho view B4/progress lớn hơn. Không đổi transaction assertion hoặc collector/policy/E1; owner C cần review test harness. Lượt FAIL giữ nguyên, nguyên nhân ban đầu không được suy diễn thành product PASS.
