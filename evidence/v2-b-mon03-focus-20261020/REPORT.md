# MON-03 focus monitoring — báo cáo 20/10/2026

## Nguồn và phạm vi

- Base: `d2c8f01` (`origin/main` khi tạo nhánh).
- Nhánh: `feat/v2-b-mon03-focus-monitoring-20261020`.
- Owner/reviewer theo Plan V2: B/C.
- Runtime opt-in; mặc định OFF. Không sửa DB/wire MON-07 và không bật observer USB/window/display.

## Kết quả mới

Lệnh focused:

```powershell
mvn --% -Dmaven.repo.local=C:\Users\Lenovo\.m2\repository -pl client -am -Dtest=FxFocusMonitorTest,FocusSignalAdapterTest,SignalRunContextTest,MonitoringDeliveryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Kết quả: **46 tests, 46 PASS, 0 failure, 0 error, 0 skip**, Maven exit 0.
Trong đó MON-03 trực tiếp có 9 case adapter/JavaFX; các case còn lại là
shared context và process lifecycle regression, không cộng thành 46 case MON-03 mới.

Full reactor `mvn clean test`: **1345 total = 1201 PASS + 144 SKIP, 0 failure/error**,
exit 0 (protocol 82, monitoring-spike 35, client 803/26 skip, server 425/118 skip).
Lượt `mvn test` không clean ngay trước đó dừng ở server test discovery do
`NoClassDefFoundError: VisualManifest` trong `target` cũ; protocol 82, spike 35 và client
803/26 skip đã qua. `clean test` biên dịch lại và loại lỗi cache; không sửa
production để né lỗi.

Phân loại evidence:

- JavaFX property/listener và clocks: TEST seam, không phải thao tác desktop thật.
- Signal input/transport trong unit tests: MOCK.
- Compile production client Java 21/JavaFX: ACTUAL.
- Manual Alt+Tab, role PROCTOR trên app đang chạy, PostgreSQL/HTTP/WS/history và máy thứ hai:
  **NOT RUN** trong lượt này.

## Đối chiếu acceptance

| Tiêu chí | Bằng chứng hiện tại | Trạng thái |
|---|---|---|
| Lost/restored + duration monotonic | Adapter tests kiểm exact signal/context/duration | PASS tự động |
| Dialog nội bộ + startup transition | Adapter + JavaFX binding tests | PASS tự động |
| Không scrape app ngoài | Source chỉ dùng JavaFX `Window`/ownership | PASS source review, chờ reviewer C |
| Không phát sau close | Deferred callback sau close và generation cũ bị bỏ | PASS tự động |
| Không observer role proctor | Coordinator chỉ được gọi trong candidate exam path | PASS source review, manual NOT RUN |
| Alt+Tab/outside-window demo | Checklist trong `docs/MON03_FOCUS_MONITORING.md` | NOT RUN |

Không đánh dấu MON-03 DONE trước manual desktop evidence và review C.
