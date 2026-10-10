# T2-C2 — kiểm chứng 10/10/2026

Base `683adfc9397ab63d6ec211116edcb9e77af4bd0f` (PR#18 đã merge). Fetch lại trước commit vẫn cùng main. Nhánh `feat/t2-c2-monitoring-late-events-and-gaps`; code/contract `fdac531834dc57c8d0001555225b69d7699942ac`. Người chạy: Codex Agent, không thay human A/B review. Git/PR/merge cuối xem PR liên kết với nhánh và báo cáo bàn giao; không dùng SHA docs về sau làm SHA đo runtime.

## Môi trường và provenance

- Windows11 build26200 AMD64, máy thử ẩn danh LOCAL-WINDOWS-TEST-01, Temurin21.0.10, Maven3.9.15, PostgreSQL18.6 trong Docker.
- Spring/PG/HTTP/WS localhost REAL, schema `late_events_test_UUID` do test sở hữu, port server ngẫu nhiên. `.env` chỉ đọc; public/dev vẫn migration7, C2 TEST schemas0 sau chạy. Không reset DB hoặc sửa migration cũ. V8 được kiểm thật từ schema V7 chứa event có sẵn, giữ nguyên identity/observedAt và nguồn UNSPECIFIED.
- Metadata [run-metadata.json](run-metadata.json): run `T2C2-c1de406a-82fc-4993-9374-da1fdfec8270`, UTC `2026-10-10T10:34:20.5353534Z` (17:34 UTC+7), code HEAD fdac531, workingTree rỗng. 187 source/script/migration hashes đối chiếu file chạy; nội dung Git SHA trùng sau chuẩn hóa CRLF/LF. JAR hashes ghi riêng. Không thay source hành vi sau build; docs/evidence commit kế tiếp không thay source.
- Client poll/heartbeat100ms, presence timeout1200ms/scan25ms; full stale2000ms/scan25ms/TTL5000ms. Queue event10 cho reconnect, capacity1 cho overflow; in-flight1, ACK400ms, retry5/backoff200–400ms. Đầu vào process có PID cố định và thời gian đã ghi, schema ID ngẫu nhiên để cô lập; không phải E1/E2.
- RAW6 endpoint: 5 client recorder + 1 server recorder (bao gồm GUI và socket kiểm revoke). Không sửa raw. [summary.json](summary.json), [summary.csv](summary.csv) sinh bằng `scripts/summarize-monitoring.py`: COMPLETE,63 categories; tất cả FINAL có drop/unwritten/pending0. TX attempted184467 byte, writeCompleted184467 byte; đây là JSON WebSocket toàn workload, không overhead TCP/TLS hay số đo hiệu suất/delta. RX/BUSINESS_ACK không cộng đôi.
- Sinh lại JSON/CSV từ raw đã copy: byte-identical. [checksums.sha256](checksums.sha256) bao phủ exact bytes của artifacts, metadata, summaries và ảnh; verification/checksum tự thân không nằm trong manifest.

## Kết quả mới trên C2

| Kiểm tra | Kết quả | Phân loại / phạm vi |
|---|---|---|
| Maven package toàn reactor | PASS425/425 | protocol32/client250/server142/spike1; tăng12 so với suite413 của C1, không dùng suite413 làm PASS C2. [Transcript](maven-package.txt), kết thúc17:28:07 UTC+7 |
| Python analyzer tests | PASS10 | Chạy mới trong phiên C2, [transcript](python-tests.txt) |
| Legacy event + V7→V8 | PASS | REAL DB/HTTP/WS; legacy và event có sẵn UNSPECIFIED, không bịa nguồn |
| Client A mất liên lạc → tự UNKNOWN, B vẫn ONLINE và event ACK | PASS | REAL timeout worker, không gọi scan/maintain/HTTP để gây chuyển trạng thái; ngắt socket/giữ write SIMULATED, readings MOCK |
| Reconnect ONLINE + giữ lastSeen/timeoutDetected/recoveredAt/interruption | PASS | REAL lease/store/push; không đoán chính xác thời điểm dừng |
| Event trên kết nối trước/offline gửi bù có nhãn đúng | PASS | Production collector worker/delivery/Realtime/WS/PG REAL; readings MOCK, giữ write SIMULATED |
| Late retry DB1/warning1, đổi PID CONFLICT | PASS | REAL commit+unique+WS proctor warning; eventId/origin/payload/time đóng băng |
| LIVE retry trên socket mới giữ metadata lần đầu | PASS | REAL; client date2099 cố ý lệch không bị dùng suy ra độ trễ |
| Event muộn không thay full mới | PASS | REAL state HTTP before/after bằng nhau; epoch full không áp để loại event lịch sử |
| Deferred COMMIT lỗi → rollback/ERROR, không ACK/warning giả; retry thành công | PASS | REAL PostgreSQL constraint trigger chỉ TEST, không tắt DB dùng chung |
| Offline gửi bù đủ → droppedCount0/gap0 | PASS | Không tự đổi timeout thành số event mất |
| Queue1/3 observations → drop2, gap retry một dòng, đổi count CONFLICT | PASS | Input MOCK; ACK đầu bị giấu khỏi delivery SIMULATED; REAL WS/DB/GET gap |
| Full mới giữ nguyên event/gap/interruption | PASS | REAL, so exact history counts trước/sau full; Stop có thể gây interruption mới nên không gán cứng tổng luôn1 |
| Giám thị thấy cột Cách nhận + tab gap/count2 | PASS | REAL JavaFX proctor component/HTTP/controls; đã mở và kiểm ảnh. Không candidate GUI toàn app |
| Proctor/foreign/assignment revoke/CLOSED/SUBMITTED/TIMED_OUT/token revoke | PASS | REAL, cả nhánh duplicate; không nới scope; UNAUTHORIZED callback có thể không có requestId vì auth chạy trước parse |
| Stop/generation/send-guard/selection/logout/old callback | PASS | MOCK/latch/virtual time trong unit, REAL close/revoke/worker cleanup trong integration |
| Source/file-boundary/secret/JAR checks | PASS | Không credential thật/private process path/command line trong artifact; JAR production không có harness/test; .env/tracker/kế hoạch/V1–V7/C1 maintenance không đổi |

[Integration transcript](integration.txt). Ảnh thật: [event muộn](t2c2-late-events.png), [gap overflow](t2c2-gaps.png). Roster UNKNOWN trên ảnh là sau Stop, timeout hợp lệ; khôi phục ONLINE trước Stop đã được assertion riêng. Mốc2099 là timestamp client lệch cố ý trong test, không phải thời gian thực tế.

## Hồi quy chạy mới trên code C2

- [A3](regression-a3.txt): auth/legacy/commit ACK/warning/dedup/conflict/concurrent retries/revoke/timeline REAL PASS.
- [A4](regression-a4.txt): hard-kill JVM con sở hữu REAL → tự UNKNOWN, B tiếp tục, reconnect/recovered history, nhiều socket/old callback/revoke/COMMIT failures/shutdown PASS; process source MOCK. Không gọi disconnect là kill thật.
- [C3](regression-c3.txt): ProcessHandle/owned Edge REAL, event/ACK/retry/server restart/gap COMMIT+scope/cleanup PASS; overflow inputs MOCK, lost ACK SIMULATED.
- [C4](regression-c4.txt): production byte hooks/REAL HTTP/WS/PG/counts/cleanup PASS; source overflow MOCK, ACK loss SIMULATED; summary COMPLETE3 endpoint26 categories. Không đo CPU/memory/miss-rate hay delta.
- [B3 -Gui](regression-b3.txt): Stage proctor REAL/controls, owned child hard-kill/UNKNOWN/recovery/stale/reconnect/HTTP403/401/cleanup PASS; source MOCK, không full candidate GUI.
- [C1 -Gui](regression-c1.txt): ProcessHandle/Edge thật, full/epoch/order/dedup/auth/state-event separation, proctor Stage REAL PASS. Giữ socket+heartbeat sống, không full/CLOSE/HTTP state read/manual maintain → tự STALE push, TTL4000ms/capacity1 OPEN từ bị từ chối đến ACK; Spring close dọn full RAM/worker PASS. Không bật EnableScheduling hoặc sửa ExamTimeoutService.

Những transcript này thuộc lượt C2 mới; evidence C1/A4/C3/C4/B3 cũ chỉ là tham chiếu lịch sử. Hồi quy chạy trước code commit, cùng nội dung Java/JAR; chỉ bổ sung manifest SQL/machine/seed ở smoke wrapper trước commit, rồi chạy C2 lại trên clean fdac531.

## Negative test và các lỗi đã sửa

- Chạy harness C2 mới với server JAR của main683adfc: FAIL `LEGACY_DELIVERY_METADATA` vì timeline chưa có deliveryStatus, [excerpt thật](before-fix.txt). Chỉ chứng minh điểm thiếu này; không nói toàn bộ late flow đã được chạy trên server cũ. BeforeFix JAR giữ ignored, không commit binary.
- Build exploratory từng gặp class test trong target có `Unresolved compilation problem`/MockScheduler; clean rebuild thành công. Không dùng output lỗi làm PASS.
- Real wire phát hiện Gson bỏ field null: OFFLINE/UNSPECIFIED phải chấp nhận thiếu observationConnectionId. Đã sửa shared parser và thêm unit roundtrip với Gson thật; CONNECTED vẫn bắt buộc ID.
- Assertion harness sai khi luôn đòi interruption tổng1 sau Stop: Stop ngừng scoped heartbeat nên có thể timeout thêm. Sửa so lịch sử trước/sau full; reconnect trước Stop vẫn đòi interruption1 và recoveredAt.
- Harness revoke ban đầu chờ response có requestId: server auth trước parse trả UNAUTHORIZED không tương quan. Đã chờ ERROR callback trên socket riêng/heartbeat30s và yêu cầu FAILED, không bỏ qua auth failure. Hai lượt C2 sau sửa đều PASS; lượt dùng cho raw/ảnh là clean fdac531.

## Acceptance và giới hạn

- MT01 PARTIAL ở cấp toàn hệ thống: component/process/full/event/collector gate đã kiểm, candidate GUI toàn app/LAN chưa chạy.
- MT07 PARTIAL ở cấp toàn luồng: toàn kiểm tra kỹ thuật C2 late/dedup/gap/full-preserves-history và proctor component PASS; candidate GUI/LAN acceptance NOT RUN.
- MT08 PARTIAL ở cấp toàn luồng: owned hard-kill tự UNKNOWN/reconnect/history REAL trên A4/B3 mới PASS; candidate GUI/LAN NOT RUN.
- Human A/B review tổng thể NOT RUN; không tạo review giả. ChatGPT planning/review NOT RUN theo lựa chọn không kết nối trước đó của người dùng.
- Chưa đo mạng giữa hai máy, chưa kiểm candidate login→monitoring UI→outage→reconnect bằng tay. Không gọi localhost là LAN. Không làm T2-C3–C5/delta/E1/E2. Queue RAM không sống qua app kill; full state RAM một server JVM như C1.
- Metadata origin do client báo; không phải chứng cứ chống client đã bị chỉnh sửa. Event trước ACK heartbeat đầu/legacy UNSPECIFIED; LIVE chỉ cùng socket, không hứa đến nhanh. Gap HTTP tối đa5000; vượt giới hạn báo lỗi/stale, không giả trả đầy đủ. Không có push gap riêng; cần refresh khi overflow mới.

## A/B cần review

- A: V8, `MonitoringEventService.store`, `MonitoringGapService.timeline`, `MonitoringTimelineController`, `RealtimeWebSocketHandler` — schema/permission/transaction/commit ACK và thêm connectionId.
- B: `MonitoringTransport`, `RealtimeClient`, `DashboardApi`, `MonitoringApiClient`, `MonitoringJson`, `MonitoringData`, `DashboardController`, `DashboardModel`, `ProctorDashboardView` — nguồn socket, gửi có generation guard, HTTP gap, nhãn và lifecycle.
- Shared/C: `EventOrigin`, `ProcessEvent`, `MonitoringMessage`, `MonitoringDelivery`, test và harness. Không có thư viện mới.

## Tái chạy/demo

```powershell
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
mvn package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t2c2.ps1 -JavaHome $jdk -Gui
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-a4.ps1 -JavaHome $jdk
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t2c1.ps1 -JavaHome $jdk -Gui
python scripts/test_summarize_monitoring.py
```

DB đã chạy, .env local có sẵn. C2 tự mở GUI proctor, dựng hai candidate/TEST server/schema, điều khiển outage/reconnect, bù event và phục hồi full, refresh gap, assertion và cleanup; kết thúc PASS/COMPLETE. Readings MOCK và faults SIMULATED có nhãn. Để xem lâu hơn có thể xem hai ảnh hoặc chạy app theo README; đừng dùng Stop như mất mạng vì Stop chủ động bỏ pending/dọn phiên. A4 kiểm hard-kill thật; C1 kiểm process/full thật. Không cần reset DB.
