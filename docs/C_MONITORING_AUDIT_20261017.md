# Audit vai C — 2026-10-17

## Nguồn và phạm vi

- Nhánh audit: `fix/c-monitoring-audit-20261017`.
- Base: `review/c-e1-methodology-t4c1` tại `304bc509bf26966499d6d0fb98a922794743b2ee` (HEAD của Draft PR #41 khi bắt đầu audit).
- PR #41 khi kiểm tra: OPEN, DRAFT, chưa merge, target `qa/b-pr39-acceptance-20261016`.
- Phạm vi đã đọc: collector/identity/policy, event queue/retry/gap, FULL/DELTA/reducer/RESYNC, state/event/presence/gap server, measurement, trace/replay/oracle và harness/analyzer E1.
- Không sửa auth, bài thi, Reading, Listening, dashboard, installer hoặc contract. Không chạy E1 đủ 12 lượt, E2, load test, LAN hay human gate.

Baseline trên đúng base: `protocol` 45, `client` 605, `server` 217; tổng 867 test, 0 lỗi, 46 test PostgreSQL bị skip theo profile.

## Findings

| Mức | Khu vực | Điều kiện và tác động | Trạng thái |
| --- | --- | --- | --- |
| P1 | `MonitoringDelivery` | Server trả `FORBIDDEN`/`UNAUTHORIZED` hoặc session báo access rejection. Delivery chuyển inactive nhưng worker định kỳ và hai listener transport vẫn sống đến khi bên ngoài gọi `stop()`. Pending correlation khác cũng còn giữ. | Đã tái hiện bằng timeout khi chờ `stopped()`. Đã sửa: hủy timer, đóng listener, quên toàn bộ correlation và shutdown worker; vẫn giữ số lượng RAM chưa xác nhận để báo khi stop. |
| P1 | `ObservationJoiner` | Một `SOURCE_FAILURE` cũ, đã được một lần quét khỏe thay thế trước khi trial bắt đầu, vẫn làm mọi ca thiếu observation sau đó thành `INCONCLUSIVE`. Điều này che definite miss trong E1. | Đã tái hiện: expected 3 definite miss nhưng actual 0. Đã sửa: chỉ lỗi nguồn nằm trong bracket khỏe ngay trước/sau trial mới vô hiệu kết luận thiếu. |
| P1 | E1 machine binding | Harness luôn ghi hằng `LOCAL-HARNESS-01`, nên hai máy khác nhau vẫn có cùng chữ ký chiến dịch. Analyzer có thể không phát hiện dữ liệu trộn máy. | Đã sửa: full run bắt buộc `-Dtoeic.e1.machineAlias=<alias-khong-nhay-cam>`; metadata ghi alias thật; analyzer từ chối thiếu/`UNSPECIFIED` và phát hiện alias trộn. |
| P2 | Event/interruption HTTP history | Timeline event và interruption đọc toàn bộ lịch sử PostgreSQL vào RAM; gap đã có giới hạn nhưng hai API này chưa có. Lịch sử lớn có thể làm phản hồi và heap tăng không giới hạn. | Đã sửa theo contract hiện có của gap: `LIMIT 5001`, trả lỗi khi vượt 5000, không cắt im lặng. Chưa chạy load 5001 row theo giới hạn audit. |
| P2, owner A/B | `RealtimeClient.SocketListener.onText` | Nhận WebSocket chỉ giới hạn số ký tự; gửi client và nhận server đều kiểm byte UTF-8. Payload nhiều ký tự đa byte có thể lớn hơn giới hạn byte dự kiến dù số ký tự vẫn hợp lệ. | Finding điểm ghép, chưa sửa vì file là adapter A/B và cần thống nhất tên/config contract (`maxMessageChars` so với byte). Vị trí: `client/.../realtime/RealtimeClient.java`, `onText`, dòng quanh 639. |

Các nghi vấn không tái hiện được không được ghi là lỗi đã sửa. Raw E1 lịch sử được PR #41 nhắc tới không có trong workspace, nên audit này không xác nhận lại kết quả chiến dịch lịch sử.

## Thay đổi chính

- `MonitoringDelivery`: thêm trạng thái quiesced và một đường cleanup dùng chung cho terminal rejection/stop.
- `ObservationJoiner`: gắn `SOURCE_FAILURE` với khoảng quét thật sự bao quanh từng trial.
- `ControlledProcessHarness`, `E1Cli`, `analyze.py`: yêu cầu machine alias rõ ràng cho full campaign và kiểm chữ ký môi trường.
- `MonitoringEventService`, `JdbcPresenceStore`: giới hạn response history giống `MonitoringGapService`.
- Thêm regression tests cho cleanup terminal, source failure đã được supersede và machine alias/campaign mixing.

## Kiểm chứng đã tự chạy

### Trước bản sửa

Test tập trung mới thất bại đúng hai lỗi logic:

- `MonitoringDeliveryTest.permissionErrorStopsWorkerAndSubscriptionsButRetainsDiscardAccounting`: timeout chờ worker dừng.
- `ObservationJoinerTest.sourceFailureSupersededByHealthyPreTrialScanDoesNotInvalidateTrial`: expected 3 definite miss, actual 0.

### Sau bản sửa

- `mvn -pl protocol,client,server -am test`: 870 test, 0 lỗi; 46 PostgreSQL profile tests skip (`protocol` 45, `client` 608, `server` 217).
- `mvn -pl protocol,client,server -am package -DskipTests`: BUILD SUCCESS, không chạy `clean`.
- Python E1 analyzer: 5/5 PASS.
- Python monitoring summary: 10/10 PASS.
- T2-C1 PostgreSQL TEST: PASS FULL, reconnect/epoch, autonomous STALE, TTL/capacity release và shutdown cleanup. Artifact ignored: `server/target/t2c1-runtime/T2C1-7c14c390-818a-4a70-81e0-086b837c6188`.
- T2-C2 PostgreSQL TEST: PASS event dedup/conflict, retry, commit/rollback, gap, presence và quyền. Artifact ignored: `server/target/t2c2-runtime/T2C2-75468945-0cb1-4924-b492-f876e7cab85f`.
- T3-C2 PostgreSQL TEST: PASS DELTA/RESYNC, freshness, restart, token revocation và measurement. Artifact ignored: `server/target/t3c2-runtime/T3C2-ee3b5106-4573-4a29-81a6-9ad9300de18e`.
- T2-C4 rút gọn (`PerDuration=2`): PASS ground truth/trace/join/checksum/cleanup; đây không phải E1 campaign. Artifact ignored: `client/target/t2c4-smoke/b5b30086-6c1f-49b3-91a1-a7a33c6dd3e6`.
- T3-C1: PASS so sánh FULL/DELTA, reconnect và negative input; MOCK/SIMULATED, không phải mạng thật.
- T3-C3: 72 case + 4 negative checks PASS và report lặp lại byte-identical; MOCK/SIMULATED. Artifact ignored: `client/target/t3c3-smoke/c3990649-711c-4527-8b41-4c449accf386`.

Docker Compose chỉ được khởi động lại với volume hiện có. Các smoke dùng schema TEST riêng và tự xóa schema; không reset DB dev, không sửa `.env`, không xóa volume.

## Chưa chạy và giới hạn kết luận

- Không chạy E1 full 12 lượt, E2, load test 5001 history rows, T3-C4 GUI/human gate, LAN máy thứ hai hoặc nghiệm thu A/B.
- 46 test yêu cầu PostgreSQL profile trong Surefire mặc định bị skip; thay vào đó ba smoke PostgreSQL TEST liên quan C đã chạy thật.
- T3-C1/T3-C3 dùng fixture MOCK và fault SIMULATED; chúng kiểm oracle/replay chứ không chứng minh mạng thật.
- Không có raw E1 lịch sử để kiểm checksum/reanalysis lại chiến dịch cũ.
- Kết quả này chỉ xác nhận những đường đã đọc và test; không phải tuyên bố phần C hoặc toàn hệ thống không còn lỗi.

## Lệnh tái hiện trọng tâm

```powershell
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"

mvn -pl client -am test "-Dtest=MonitoringDeliveryTest,ObservationJoinerTest,E1ArtifactsTest" "-Dmaven.repo.local=$env:USERPROFILE/.m2/repository"
python -m unittest discover -s scripts/benchmarks/e1 -p "test_analyze.py"
python -m unittest scripts/test_summarize_monitoring.py
```
