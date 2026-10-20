# MON-07 — Status/gap, USER_AUTHORIZED_EXPERIMENT 20/10/2026

Người dùng (attachment f09808ef) ủy quyền slice protocol/client/server/test/docs MOCK,
push/PR/merge commit khi CI/rules cho phép; không phải approval A/B/C hoặc policy.
Base main519063d; experiment OFF/FULL mặc định, USB0/12 NOT_RUN, MON07 chưa DONE.

## Contract trước code

| Type | Source | Details | Quality/bounds/semantics |
|---|---|---|---|
| OBSERVER_STATUS | WINDOWS_USB_STORAGE/JAVAFX_EXAM_FOCUS/JAVAFX_EXAM_WINDOW/JAVAFX_DISPLAY | status READY/UNKNOWN/UNSUPPORTED/ERROR; reason INITIAL/RECOVERED, NO_DATA/PAUSED_OFFLINE, API_UNAVAILABLE, PERMISSION_DENIED/SCAN_FAILED tương ứng | READY COMPLETE; UNKNOWN/ERROR UNREADABLE; UNSUPPORTED UNSUPPORTED. Không free text/count/device identity. Bốn nguồn đã có value type; EXAM_LOCAL_ACTIVITY còn deferred. PAUSED_OFFLINE chỉ mã, không triển khai policy60s. |
| SIGNAL_GAP | CLIENT_SIGNAL_DELIVERY | reason QUEUE_OVERFLOW; droppedCount1..9007199254740991; firstDroppedAt/lastDroppedAt | COMPLETE; min/max giờ UTC client tại admission bị bỏ, không thời điểm cắm/rút; first<=last. Không process/heartbeat/source failure. |

Envelope/schema1 và bound8192/4096/1024 UTF-8/time UTC microsecond giữ nguyên.
Typed validation/canonical equality, auth/transaction/COMMIT-ACK/history dùng service
chung. V17 append-only sau kiểm main/open PR chỉ tớiV16, không sửa checksum cũ; OFF
vẫn có thể apply DDL Flyway. Chỉ DB TEST cách ly, không .env/DB dev.

## Delivery/ownership/budget

Owner truyền cùng SignalRunContext cho emitter và SignalDelivery mới (constructor
context). Constructors legacy không context giữ local-only drop accounting, không
tự dựng sequence/run khác. Không production caller mới. Một queue/socket/retry chung.
Data tối đa256entries/1MiB (configurable thấp hơn); một frozen gap reserve tối đa8192
byte => tổng257entries/1056768serialized bytes + accumulator fixed count/min/max times.
Hai flight/write tổng kể cả gap. Gap không đẩy mất data/in-flight/failed. Scheduling
luân phiên control/data eligible để không starvation khi flight1; không bảo đảm tiến
triển khi socket treo, nhưng không overlap retry của write chưa kết thúc.

Drop-new chỉ khi count/byte data đầy. Closed/wrong scope/context stopped không tăng
overflow; caller không enqueue gap vào data queue. Cumulative count và accumulator
bounded MAX_INTEGER; exhaustion dừng lane rõ COUNT_LIMIT, không wrap/silent clamp.
Freeze gap dùng shared sequence/IDs, observedAt=lastDroppedAt, origin UNSPECIFIED
(control tóm tắt admission, không gán socket hiện tại cho observation cũ). Chỉ reset
accumulator sau internal reserved insertion thành công. Drop sau freeze vào batch
mới, gap cũ immutable đến ACK hợp lệ. Gap failed/exhausted giữ reserve/unconfirmed,
không tính gap retry/drop vào droppedCount, không gap-báo-mất-gap.

Close/revoke dừng/forget, không flush; status giữ số discarded frozen-gap drops và
unreported accumulator/time cục bộ. RAM không sống qua crash; không gửi bù attempt
mới. Adapter/emitter không sở hữu queue/context/transport dùng chung.

## Status emitter

Explicit start/generation; tối đa4source slots, mỗi slot một latest pending observation.
Initial phát ngay; dedup status/reason; các thay đổi trong cửa sổ1s monotonic được
coalesce latest rồi caller flush/observe sau hạn, không worker mới. Intermediate
observations chưa thành event không phải overflow drops. Một lần admission thất bại
được delivery tính drop; emitter ghi nhận lần thử và không enqueue lại mỗi poll.
Stop clears slots/invalidate generation; run/context/delivery closed ngừng nhận.
UNKNOWN không thay baseline USB, không false removal/count0; mất heartbeat là presence
server riêng. Chưa tự nối status emitter vào USB/FX/provider production.

## Kiểm chứng/bàn giao

Kết quả/source/hashes ghi sau khi chạy. Input MOCK; REAL WS/HTTP/PostgreSQL TEST;
ACK loss/COMMIT fault SIMULATED. Không hardware/hotplug/LAN/E1/E2, push/dashboard/rules,
offline60s/retention30ngày. B dùng chung context/queue và explicit flush/status lifecycle;
C nối status từ kết quả scan chỉ sau review provider/cadence/identity/hardware riêng.
