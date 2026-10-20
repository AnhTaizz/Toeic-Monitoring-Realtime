# MON-04 window state — báo cáo 20/10/2026

## Nguồn và phạm vi

- Nhánh: `feat/v2-b-mon04-window-state-20261020`.
- Stacked base: MON-03 `d5c568a`; main gốc trước stack: `d2c8f01`.
- Owner/reviewer theo Plan V2: B/C.
- Runtime experiment OFF mặc định; không sửa protocol/DB/server MON-07.

## Kết quả

Focused regression:

```powershell
mvn --% -Dmaven.repo.local=C:\Users\Lenovo\.m2\repository -pl client -am -Dtest=FxWindowMonitorTest,WindowSignalAdapterTest,FxFocusMonitorTest,FocusSignalAdapterTest,SignalRunContextTest,MonitoringDeliveryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Kết quả: **55 PASS, 0 failure/error/skip**. Có 8 case MON-04 trực tiếp
(5 adapter + 3 JavaFX binding) và 1 case shared input-claim mới; phần còn lại là
MON-03/shared lifecycle regression, không cộng thành case MON-04 mới.

Full reactor `mvn clean test`: **1354 total = 1210 PASS + 144 SKIP, 0 failure/error**,
exit 0 (protocol 82, monitoring-spike 35, client 812/26 skip, server 425/118 skip).

## Đối chiếu acceptance

| Tiêu chí | Bằng chứng | Trạng thái |
|---|---|---|
| Baseline non-fullscreen không bị gọi là exit | Exact `INITIAL,false,false` test | PASS tự động |
| Minimize/restore | Exact combined-state adapter tests | PASS tự động |
| Fullscreen enter/exit chỉ sau state đã quan sát | Sequence false→true→false test | PASS tự động |
| Debounce | Duplicate adapter + same-pulse JavaFX tests | PASS tự động |
| Closing/disposal | Deferred callback bị hủy, listeners bị gỡ | PASS tự động |
| Không phạt/ép focus/fullscreen | Không có API mutation trong observer | PASS source review, chờ C |
| Manual window actions/history | Checklist trong `docs/MON04_WINDOW_STATE_MONITORING.md` | NOT RUN |

JavaFX properties/clocks/transport trong test là MOCK/TEST seam. Manual Stage, REAL WS/HTTP/PG,
history và reviewer C chưa chạy; không đánh dấu MON-04 DONE.
