# T3-A3 · Verification 18/10/2026

## Source và phạm vi

- Branch: `feat/t3-a3-delta-server-hooks`, base `main`/`6ac8835`.
- A review các hook server đã tích hợp từ T3-C2/C4; không thay collector, encoder, reducer, migration hoặc cấu hình mặc định full.
- Thay đổi source duy nhất là regression restart trong `MonitoringStateServiceTest`; thêm contract và evidence T3-A3.
- PostgreSQL18.6 chạy trong container hiện có. Smoke dùng schema `TEST` ngẫu nhiên, tự xóa sau chạy; không reset hoặc sửa bảng phát triển.

## Kết quả

| Kiểm tra | Kết quả | Mức bằng chứng |
|---|---:|---|
| Targeted state/delta/presence/maintenance | 31 PASS, 0 fail/error/skip | MOCK scope/registry và clock; production services/reducer |
| Full `mvn package` | 912 PASS, 0 fail/error, 58 opt-in skip | Toàn reactor; protocol45, client635, server231, spike1 |
| `scripts/smoke-a3.ps1` | PASS | REAL PostgreSQL/HTTP/WS/Spring; process payload kiểm soát |
| `scripts/smoke-t3c2.ps1` | PASS | REAL PostgreSQL/HTTP/WS/Spring restart/client reconnect; readings MOCK, bad-order SIMULATED |

Targeted command:

```powershell
mvn -pl server -am "-Dtest=MonitoringStateServiceTest,MonitoringDeltaStateTest,MonitoringPresenceServiceTest,MonitoringStateMaintenanceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

## Acceptance đối chiếu

- **Epoch hook:** state RAM mang `serverInstanceId` và `syncEpoch`; test mới tạo hai store instance, xác nhận cả hai ID đổi. Full dùng epoch cũ bị `STALE`; OPEN và full sequence1 trong epoch mới được nhận.
- **Persistence hook:** event/gap/presence dùng PostgreSQL transaction; current state full/delta được ghi rõ chỉ bền trong RAM. Timeline DB vẫn còn sau server restart, không dùng history để đoán lại current process state.
- **Reconnect sau restart:** smoke C2 restart Spring thật trên cùng port; login/history giữ, client reconnect, nhận instance/epoch mới và gửi full phục hồi trước delta.
- **DB lỗi không ACK thành công:** deferred PostgreSQL COMMIT failure rollback row, trả `RETRYABLE_SERVER_ERROR`, không success ACK và không warning push. Sau recovery retry commit được.
- **Scope state/event:** session được revalidate mỗi message; role/attempt trước reducer/dedup; socket/collector/epoch trước apply. Proctor write, foreign/closed attempt, revoked token và duplicate sau scope revoke đều bị từ chối. Push giám thị revalidate token/role/assignment.
- **Không xây lại collector C:** không đổi production collector/encoder/reducer.

## Giới hạn trung thực

- LAN hai máy, GUI end-to-end và human C review: NOT RUN.
- Delta vẫn opt-in và full vẫn mặc định.
- State store vẫn RAM một JVM với coarse lock; không tuyên bố durability/distributed coordination.
- 58 test PostgreSQL/GUI opt-in của full package bị skip theo cấu hình mặc định; hai smoke PostgreSQL liên quan trực tiếp T3-A3 đã chạy riêng và PASS.
- Tracker/kế hoạch gốc giữ `TODO` cho tới khi quy trình review của nhóm cập nhật; không tự ghi duyệt thay C.
