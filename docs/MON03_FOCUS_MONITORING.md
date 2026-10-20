# MON-03 — quan sát focus cửa sổ thi candidate

## Phạm vi runtime

MON-03 chỉ khởi tạo trong luồng candidate sau khi `CandidateMonitoringSession` đã cấp
`collectorSessionId` cho một attempt ACTIVE. Luồng proctor không gọi coordinator này.
Tính năng dùng chung capability, heartbeat đã xác nhận và WebSocket của MON-07;
không mở transport hay lease thứ hai.

Runtime mặc định **tắt**. Cả server và candidate client chỉ bật khi JVM có:

```text
-Dtoeic.monitoring.signal-experiment-enabled=true
```

`FxFocusMonitor` chỉ đăng ký `focusedProperty()` của Stage thi và danh sách
Stage/Popup JavaFX do Stage đó sở hữu. Nó không liệt kê cửa sổ hệ điều hành,
không đọc title, process hay nội dung của ứng dụng bên ngoài.

Mỗi run phát một baseline `INITIAL`, sau đó chỉ phát khi trạng thái đổi.
Thay đổi focus trong pulse khởi tạo/đổi scene được gộp vào baseline hiện tại.
Dialog/Popup của chính Stage thi mang context `EXAM_INTERNAL_DIALOG`; các trường hợp
còn lại là `OTHER_OR_UNKNOWN`. Thời gian mất focus chỉ tính bằng `System.nanoTime()`
trong cùng run và chỉ gửi khi focus trở lại.

Observer dừng khi rời view, logout/stop app, transport FAILED, capability không hỗ trợ,
hoặc heartbeat của đúng attempt/collector bị từ chối. Callback cũ mang generation cũ
không thể phát event vào run mới.

## Chạy kiểm tra tự động

```powershell
mvn --% -pl client -am -Dtest=FxFocusMonitorTest,FocusSignalAdapterTest,SignalRunContextTest,MonitoringDeliveryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Bộ test kiểm baseline/startup, lost/restored, duration monotonic, dialog nội bộ,
duplicate, queue overflow, callback sau close và dọn collector identity.

## Checklist demo Alt+Tab thủ công

1. Khởi động server và candidate client với cùng flag opt-in phía trên.
2. Đăng nhập role CANDIDATE, chọn attempt ACTIVE và vào màn thi. Ghi SHA, Windows/JDK,
   attempt ID và thời gian bắt đầu; không ghi token/mật khẩu.
3. Alt+Tab ra một ứng dụng lành tính, chờ, rồi quay lại. Kiểm history có lost và
   restored cùng observer run, sequence tăng và duration hợp lý. Không mong đợi tên
   hay nội dung ứng dụng bên ngoài.
4. Mở dialog nộp/rời màn thi rồi hủy; kiểm cặp event mang
   `EXAM_INTERNAL_DIALOG`, không sinh duplicate.
5. Rời attempt, sau đó Alt+Tab thêm lần nữa; history không được có event mới.
6. Đăng nhập PROCTOR ở client sạch; thao tác Alt+Tab và xác nhận không có
   focus observer/event phía proctor.

Phải ghi kết quả thủ công là ACTUAL hoặc NOT RUN; unit test không thay thế demo desktop.
