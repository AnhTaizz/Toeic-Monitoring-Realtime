# Findings và before/after

Line dưới đây theo source sửa `9bfc75e`; cột reproduction mô tả source trước sửa. Log đầy đủ nằm local ignored `jpackage-out/pr39-qa-20261016/`, không publish path người dùng/process/credential.

| ID / Severity | File:Line | Scenario / root cause | Impact | Reproduction / BEFORE | Fix / AFTER |
|---|---|---|---|---|---|
| QA39-01 / **P1 gate packaging TEST** | `scripts/smoke-windows-installer.ps1:65`; `ToeicClientApplication.java:68` | Smoke chờ `TOEIC Monitor`, Application dùng `TOEIC Realtime Monitoring Platform`. | App thật mở nhưng gate timeout, không tới runtime suite; không phải lỗi title làm app không chạy. | MSI035 mới/source093; baseline-msi-install-01 FAIL `Login window did not open...`; scope TEST được gỡ đúng ProductCode. | Commit dcbdf85 chờ title thật; gate sau sửa xem release/INSTALLATION_TEST_REPORT. Không đổi package script nền. |
| QA39-02 / **P2 cấu hình LAN** | `ClientSettings.java:35` | UTF-8 BOM của PowerShell5 không bị Java trim loại; URI invalid, trong khi wrapper .NET đọc được. | Direct EXE fallback localhost, người dùng cần nhập lại URL dù file hợp lệ. | Regression `windowsPowerShellUtf8BomConfigMatchesDirectExeAndWrapper`: expected192.0.2.10, actual127.0.0.1; config-bom-before-01 có1FAIL/5. | Commit0a00d0f bỏ một BOM đầu stream rồi giữ validator/4KiB; config-bom-after-01 **5PASS**, file không đổi. |
| QA39-03 / **P1 lifecycle/cache** | `AudioFiles.java:51`, `AudioPreparation.java:64` | Check exists → createDirectory TOCTOU; worker vừa đóng có thể tiếp tục sau manifest và tranh cache với view mới. | View audio mới chuyển ERROR dù tài nguyên hợp lệ, không tới FILES_READY/READY; không có false READY. | final-clean-test-01 FAIL sharedWriterClose...; stress65case:10FAIL close/reopen +1ERROR FileAlreadyExists trong barrier16reader. Lượt targeted26 đầu chưa tái hiện, vẫn giữ log. | Commit9bfc75e: catch riêng FileAlreadyExists rồi kiểm directory/realpath, guard current sau manifest. Cùng stress **65PASS**, targeted CI **26PASS**; giữ test file thường không được coi directory. |

P0=0 tìm thấy; hai P1 và một P2 ở bảng đã sửa. Chỉ ba commit sửa lỗi B; không sửa backend A/monitoring C hoặc tự approve contract.

Các mục chưa nghiệm thu: QD29/progress contract (A/C), E1-method/gate (C), physical LAN/human hearing/thiết bị codec và owner review (A/B/C). Đây là gate owner/manual riêng, không biến chúng thành lỗi code P0 hoặc PASS tự động.

Mọi FAIL trung gian và artifact035/036 được giữ;036 có race cache trước sửa, chỉ037 là ứng viên cuối. Bản đếm QA đầu của failed reactor từng trộn XML cũ server/spike; đã xuất corrected-v2 từ XML archive + khoảng START/END. Lượt FAIL thực chỉ protocol44/client601, client1FAIL, server/spike **NOT RUN**. Không dùng bản đếm đầu để tuyên bố PASS.
