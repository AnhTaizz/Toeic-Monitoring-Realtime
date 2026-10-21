# MON-05 display monitoring — báo cáo 20/10/2026

## Nguồn và phạm vi

- Nhánh: `feat/v2-b-mon05-display-monitoring-20261020`.
- Stacked base: MON-04 `0ae71bf`; main gốc trước stack: `d2c8f01`.
- Owner/reviewer theo Plan V2: B/C.
- Runtime experiment OFF mặc định; không sửa DB/server và không mở lane khi capability thiếu.

## Kết quả

Focused regression:

```powershell
mvn --% -Dmaven.repo.local=C:\Users\Lenovo\.m2\repository -pl client -am -Dtest=FxDisplayMonitorTest,DisplaySignalAdapterTest,FxWindowMonitorTest,WindowSignalAdapterTest,FxFocusMonitorTest,FocusSignalAdapterTest,SignalRunContextTest,ControlDeliveryTest,MonitoringDeliveryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Kết quả: **76 PASS, 0 failure/error/skip**. MON-05 có 9 case trực tiếp (4 adapter + 5
JavaFX polling/status); các case còn lại là regression cho MON-03/MON-04 và shared
lifecycle/delivery, không cộng thành case MON-05 mới. Maven báo `BUILD SUCCESS`, exit 0;
PowerShell in thêm một dòng `Access is denied.` trước log Maven nhưng không làm thiếu/chặn test.

Full reactor `mvn clean test`: **1363 total = 1219 PASS + 144 SKIP, 0 failure/error**,
exit 0 (protocol 82, monitoring-spike 35, client 821/26 skip, server 425/118 skip).

## Đối chiếu acceptance

| Tiêu chí | Bằng chứng | Trạng thái |
|---|---|---|
| Baseline count khi monitoring bắt đầu | Immediate successful poll phát exact `INITIAL` | PASS tự động, MOCK |
| Phát khi count thay đổi, bỏ duplicate | Adapter + polling callback sequence | PASS tự động, MOCK |
| Refresh hữu hạn | Cấu hình chỉ nhận 1.000–30.000 ms, mặc định 2.000 ms | PASS tự động |
| Lifecycle sạch | Timeline stop, late callback ignored, adapter generation invalidated | PASS tự động, MOCK |
| Unknown/unsupported không thành một màn hình | NO_DATA/API_UNAVAILABLE/error mappings không phát count giả | PASS tự động, MOCK |
| Display/status chung run/sequence/queue | Test với `ObserverStatusEmitter` và delivery thật | PASS tự động, MOCK input |
| Cắm/rút màn hình thứ hai | Checklist trong `docs/MON05_DISPLAY_MONITORING.md` | NOT RUN |
| REAL WS/HTTP/PG + proctor history | Chưa chạy trên nhánh này | NOT RUN |
| Human review C | Chưa có chữ ký | NOT RUN |

JavaFX `Screen` chỉ là báo cáo của toolkit/OS, không đảm bảo nhận mọi topology, adapter ảo hay
đường xuất hình. Không đọc nội dung màn hình và không tự phạt/hủy bài. Physical display,
REAL transport/persistence/proctor và reviewer C chưa chạy; không đánh dấu MON-05 DONE.
