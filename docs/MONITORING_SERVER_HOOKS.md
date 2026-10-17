# T3-A3 · Hook server cho monitoring delta và lỗi server

## Ranh giới sở hữu

T3-A3 không cài lại collector, encoder hoặc reducer. Module của C gọi các hook server đã có:

- `AuthorizationService`: role và attempt scope được kiểm tra trước binding, cache chống trùng và dữ liệu trả về.
- `MonitoringStateService`: state hiện tại chỉ bền trong RAM của một server instance; `serverInstanceId` và `syncEpoch` đổi sau restart/reconnect. State cũ bị từ chối `STALE`, client phải `OPEN` rồi gửi full mới trước delta.
- `MonitoringEventService` và `MonitoringGapService`: event/gap lịch sử được lưu PostgreSQL trong transaction. Handler chỉ ACK sau khi lời gọi qua transactional proxy đã trả về, tức commit đã thành công.
- `MonitoringPresenceService`: heartbeat/timeout dùng persistence hook; rollback không được publish trạng thái giả hoặc gửi ACK thành công.
- `RealtimeSessionRegistry`: push cho giám thị luôn revalidate token, role và assignment hiện tại. Push là best-effort; timeline PostgreSQL là đường phục hồi authoritative.

State full/delta được ACK sau khi state RAM authoritative đã chấp nhận, không tuyên bố durability PostgreSQL. Event/gap/presence có durability PostgreSQL. Hai loại ACK này không được nhập làm một cam kết durability chung.

## Thứ tự bắt buộc

1. Revalidate session trên từng WS message.
2. Kiểm tra role và attempt scope.
3. Kiểm tra socket/collector/server epoch đang active.
4. Parse và áp dụng reducer hoặc chạy transaction persistence.
5. Khi thành công mới ACK; khi DB/commit lỗi trả `RETRYABLE_SERVER_ERROR`, không ACK thành công.
6. Push giám thị sau accept/commit; người nhận được kiểm tra assignment lại ngay trước push.

Duplicate không được đi vòng qua bước 1–3. Server restart không phục hồi state RAM từ event history và không chấp nhận tiếp delta dựa trên epoch cũ.

## Kiểm tra

Kiểm tra nhanh không cần PostgreSQL:

```powershell
mvn -pl server -am "-Dtest=MonitoringStateServiceTest,MonitoringDeltaStateTest,MonitoringPresenceServiceTest,MonitoringStateMaintenanceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Kiểm tra tích hợp cần PostgreSQL đang chạy và `.env` cục bộ:

```powershell
mvn package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-a3.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t3c2.ps1
```

`smoke-a3.ps1` chứng minh event commit/rollback, dedup, auth và timeline. `smoke-t3c2.ps1` chứng minh restart Spring thật, persisted login/history, server instance/epoch mới, reconnect và full resync. Các smoke sở hữu schema `TEST` riêng, không reset dữ liệu phát triển.

## Giới hạn

- Store state vẫn là RAM đơn JVM với khóa coarse-grained hiện tại; đây không phải distributed store.
- Delta vẫn opt-in; full là mặc định.
- Không suy diễn rằng heartbeat làm mới state process hoặc event history có thể dựng lại state hiện tại.
- LAN hai máy và review của C là gate riêng; không được ghi là đã chạy nếu chỉ có test local.
