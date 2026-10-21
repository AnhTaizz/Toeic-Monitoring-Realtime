# MON-05 — quan sát số màn hình do JavaFX báo cáo

## Thiết kế runtime

MON-05 dùng chung `CandidateExamSignalMonitoring`, `SignalRunContext`, `SignalDelivery`,
capability và scoped heartbeat với MON-03/MON-04. Nhánh triển khai được xếp chồng trên MON-04
để focus, window, display và observer status cùng một run/sequence/queue. Runtime vẫn mặc định
OFF và chỉ bật khi cả server/client có:

```text
-Dtoeic.monitoring.signal-experiment-enabled=true
```

`FxDisplayMonitor` đọc `Screen.getScreens().size()` trên JavaFX Application Thread ngay khi
monitoring bắt đầu, sau đó polling mỗi 2 giây. Có thể cấu hình bằng
`toeic.monitoring.displayPollMillis`, nhưng chỉ nhận khoảng 1.000–30.000 ms; giá trị ngoài
khoảng bị từ chối khi khởi tạo. Công việc mỗi nhịp chỉ lấy kích thước danh sách Screen và đưa
snapshot sang adapter; monitor không đọc nội dung màn hình, ảnh chụp, cửa sổ ứng dụng khác,
định danh phần cứng hoặc hình học màn hình.

Snapshot hợp lệ đầu tiên phát `DISPLAY_COUNT_OBSERVED` với `reason=INITIAL`; chỉ khi số lượng
thay đổi mới phát `reason=CHANGED`. Giá trị lặp bị bỏ. Count 0 chỉ được giữ nếu chính JavaFX trả
về 0 trong một lần đọc thành công; lỗi API không được chuyển thành 0 hoặc 1.

Mỗi lần đọc còn phát trạng thái sức khỏe `OBSERVER_STATUS` cho nguồn `JAVAFX_DISPLAY`:

| Tình huống | State/reason |
|---|---|
| Lần đọc thành công đầu tiên | `READY/INITIAL` |
| Thành công lại sau lỗi | `READY/RECOVERED` |
| Count ngoài miền 0..32 | `UNKNOWN/NO_DATA` |
| API không được hỗ trợ | `UNSUPPORTED/API_UNAVAILABLE` |
| Bị từ chối quyền | `ERROR/PERMISSION_DENIED` |
| Lỗi runtime khi đọc | `ERROR/SCAN_FAILED` |

Khi monitor đóng, `Timeline` dừng, adapter bị vô hiệu hóa và callback cũ không thể phát event.
Các lỗi ở adapter/status/delivery không bị gắn nhầm thành lỗi cảm biến. Đây là số màn hình
JavaFX/OS *báo cáo*, không phải bằng chứng vật lý tuyệt đối rằng thí sinh chỉ dùng một màn hình.

## Kiểm tra tự động (MOCK)

```powershell
mvn --% -Dmaven.repo.local=C:\Users\Lenovo\.m2\repository -pl client -am -Dtest=FxDisplayMonitorTest,DisplaySignalAdapterTest,FxWindowMonitorTest,WindowSignalAdapterTest,FxFocusMonitorTest,FocusSignalAdapterTest,SignalRunContextTest,ControlDeliveryTest,MonitoringDeliveryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Các supplier, clock và callback trong test là MOCK/TEST seam. Chúng kiểm tra baseline, dedupe,
change, recovery, phân loại lỗi, giới hạn polling, disposal và việc display/status dùng chung
run/sequence/queue; chúng không thay thế kiểm tra màn hình thật.

## Checklist màn hình vật lý

1. Ghi SHA, Windows/JDK, loại kết nối màn hình và attempt ID; không ghi token/mật khẩu.
2. Bật signal experiment ở server và candidate client; vào một attempt ACTIVE với một màn hình.
3. Xác nhận history nhận baseline `DISPLAY_COUNT_OBSERVED`, `reason=INITIAL`, count đúng với
   số Screen mà JavaFX đang thấy, kèm `JAVAFX_DISPLAY READY/INITIAL`.
4. Kết nối màn hình thứ hai thật, bật/extend màn hình trong Windows nếu cần và chờ hơn một chu kỳ
   polling. Xác nhận đúng một `CHANGED` khi count đổi; callback lặp không tạo event trùng.
5. Ngắt màn hình thứ hai, chờ hơn một chu kỳ và xác nhận transition ngược lại trong history.
6. Nếu môi trường/API không quan sát được thay đổi, ghi `UNKNOWN`/`UNSUPPORTED` cùng giới hạn,
   không ghi thành count 1 hoặc PASS phần cứng.
7. Kết thúc attempt/đóng Stage, tiếp tục cắm/rút và xác nhận không có event mới.
8. Ghi riêng REAL WS/HTTP/PostgreSQL, proctor timeline và reviewer C là ACTUAL hay NOT RUN.

Kết quả desktop phải ghi ACTUAL hoặc NOT RUN. Không dùng test MOCK để đánh dấu physical PASS.
