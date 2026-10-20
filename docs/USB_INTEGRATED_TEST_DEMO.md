# USB integrated TEST demo — USER_AUTHORIZED_EXPERIMENT

Attachment e80d60fa cho phép một luồng CIM thật → snapshot → adapter/status → shared
delivery/RealtimeClient → server/PostgreSQL TEST → COMMIT/ACK → HTTP history. Không
owner approval/ADR-V2-05, không production USB observer; FULL mặc định/experiment OFF,
USB physical0/12 NOT_RUN, MON02/MON07 chưa DONE. Base main b68e19a gồm PR87/88 của B.

## Thiết kế trước code

Không module/native dependency/scanner mới: client chứa coordinator provider-agnostic,
server test classpath đã phụ thuộc client và monitoring-spike. Bridge TEST-only chuyển
UsbSnapshot từ **CimSnapshotSource hiện có**, không copy CIM/parser/resource. JUnit demo
entrypoint được một script điều phối từ đầu tới cuối với PG cluster/schema riêng;
không đưa provider vào client/server runtime JAR, không UI/push/candidate binding.

Scope do SeedDataInitializer + login/import đề READING MOCK/create-session API cấp,
không INSERT attempt/user/assignment để bỏ qua nghiệp vụ. Fixture cấp token bằng RAM,
fresh auth/me + capability + scoped heartbeat/lease trước start. Server bind127.0.0.1
port0, DB ngẫu nhiên do script tự init, không nhận URL DB/server chung, không .env.

| Input | Adapter/status | Expected |
|---|---|---|
| READY0 | baseline rỗng nội bộ + READY INITIAL/COMPLETE | status qua WS/ACK/history, không USB event |
| READY có USB | adapter baseline/diff hiện có + READY | PARTIAL/weak slot refs theo contract cũ |
| UNKNOWN | UNKNOWN/NO_DATA | Không giả count0/removal; giữ baseline xác nhận |
| ERROR | ERROR/PERMISSION_DENIED hoặc SCAN_FAILED | Chỉ reason enum safe, không raw problem/exception |
| UNSUPPORTED | UNSUPPORTED/API_UNAVAILABLE | Không suy máy không thiết bị |
| Recovery | READY/RECOVERED + observed diff | Không bịa thời điểm/chuyển đổi trong khoảng nguồn lỗi |

Coordinator sở hữu source, adapter, status emitter, một scan worker và một control
worker hữu hạn. Mượn context/delivery/transport/lease, không đóng chúng; runner owner
đóng cả nhóm sau kiểm pending/unconfirmed. Một context/sequence/queue duy nhất.
Scan fixed-delay sau query xong, không overlap; cadence là **delay**, không hứa1Hz khi
query chậm. Control flush100ms không bị query giữ, nhưng health mới chỉ biết khi query
trả về. Duration hữu hạn1..120s, cadence250..10000ms, timeout100..10000ms theo provider;
invalid config bị reject. Query timeout/16KiB pipes/cleanup tái sử dụng source cũ.

Stop/revoke/reconnect/socket generation đổi làm invalidate generation trước admission,
dừng schedules và async cleanup source đang scan trên control worker, ngoài FX/RT
callback. Không resume ngầm sau reconnect; owner phải fresh scope/capability/lease và
run mới. Callback cũ không vào run/attempt mới. Close/awaitStopped ngoài JavaFX thread;
duration stop chỉ coordinator, không flush queue sau stop. Runner đợi ACK/history khi
coordinator còn active, sau đó owner close delivery/context/lease/socket, báo pending/
dropped/unreported/discarded theo thực tế. RAM không sống qua crash.

Lệnh/prerequisites/actual/receipt bổ sung sau khi chạy, không placeholder PASS. MOCK
pipeline và REAL CIM chạy tách mode/output; không lấy count0 hoặc CI PASS làm hotplug.
