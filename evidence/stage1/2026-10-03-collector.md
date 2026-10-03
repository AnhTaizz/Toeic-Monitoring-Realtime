# Bằng chứng triển khai T1-C2 — 03/10/2026

- Code: working tree dựa trên HEAD `79fd1855b9aa70bca932a26502d43cbd69d9580a`; thay đổi chưa commit.
- Người chạy: Codex; Windows, Temurin OpenJDK 21.0.10+7, Maven.
- Lượt đầu `mvn test -B -ntp` bị chặn truy cập cache Maven; lượt được cấp quyền chạy thành công.
- `mvn test -B -ntp`: exit 0, BUILD SUCCESS, 5 reactor entries. AuthService 2, LoginApiClient 39, ProcessObservation 1, PollingProcessCollector 5; tổng 47, failures/errors/skipped đều 0.
- Collector test: lọc policy không phân biệt hoa/thường; bảo toàn khóa process và chất lượng dữ liệu; từ chối proctor và interval không hợp lệ; quét ngoài luồng gọi và stop interrupt scan đang chờ; lỗi quét không hủy vòng sau; process Java thật xuất hiện/thoát được quan sát qua PID.
- Fixture ở test lọc là MOCK; test process thật dùng ProcessBuilder trên máy chạy test, không cần DB/GUI. Chu kỳ test 10 ms; UI mặc định 1.000 ms.
- `mvn package -DskipTests -B -ntp`: exit 0, BUILD SUCCESS, client fat JAR chứa dependency monitoring. Có cảnh báo Shade module-info/resource trùng; chưa kiểm runtime GUI hoặc package máy khác.
- Báo cáo Surefire: `collector-test.txt`, `process-observation-test.txt`, `client-test.txt`, `auth-test.txt` trong cùng thư mục evidence; là bản sao đầu ra thực, không sửa số liệu.
- GUI candidate/proctor, mở/đóng Edge thủ công, logout/đóng cửa sổ: NOT RUN. Đã nối lifecycle trong source, chưa coi là GUI PASS.
- Chưa gửi mạng; chưa có event/retry/ACK; MT01 tổng thể vẫn NOT RUN. Tracker không đổi; chờ B review và kiểm GUI.
