# T3-C4 — tích hợp candidate thật và chuẩn bị gate delta

Ngày chạy thực tế: 12–13/10/2026. Mốc gate kế hoạch: **20/10**, chưa diễn ra. FULL vẫn mặc định ở client và server. Kết quả kỹ thuật local không thay nghiệm thu LAN hoặc human B review. QD-19 là quyết định bàn giao mới; QD-10 giữ nguyên quyết định presence.

## Chọn mode và vòng đời

`ToeicClientApplication.candidateMonitoringControls()` tạo `CandidateMonitoringSession` thật. `start()` chỉ chạy sau HTTP cấp scope mới, role CANDIDATE và lượt ACTIVE. PROCTOR không tạo collector. Một lần Start chọn một mode cố định:

| Điều kiện | Mode / lý do |
|---|---|
| Không bật `toeic.realtime.delta-experimental` | FULL / OPT_IN_OFF |
| Bật cờ + ACK heartbeat hợp lệ trên socket hiện tại xác nhận hỗ trợ | DELTA / CAPABILITY_CONFIRMED |
| Capability chưa biết | FULL / CAPABILITY_PENDING_FULL_FALLBACK |
| Server cũ thiếu marker, server tắt delta hoặc transport không hỗ trợ | FULL / SERVER_OR_TRANSPORT_UNSUPPORTED_FULL_FALLBACK |

`RealtimeClient.deltaCapability()` phân biệt UNKNOWN/SUPPORTED/UNSUPPORTED, gắn generation socket. Chỉ ACK heartbeat có correlation hợp lệ và toàn payload hợp lệ mới xác nhận. Reconnect xóa capability; DELTA chờ xác nhận kết nối mới trước OPEN/full/delta, rồi đồng bộ epoch mới từ scan mới. Đang DELTA mà server xác nhận không hỗ trợ: dừng với DELTA_CAPABILITY_LOST. Bấm Dừng, rồi Bắt đầu để chọn FULL. FULL không tự biến thành DELTA khi capability xuất hiện.

`DeltaSnapshotDelivery` dùng chung worker/lifecycle của `FullSnapshotDelivery`; cùng collector gửi lịch sử qua `MonitoringDelivery`. FULL thay cả danh sách; DELTA gửi thêm/bớt so với state đã ACK. Full đối soát30s và refresh2s cần scan thành công mới; heartbeat chỉ xác nhận liên lạc, không làm mới danh sách process. Snapshot quá tuổi không được dùng như scan mới.

`RealtimeClient.onMonitoringAccessRejected()` phát callback nội bộ khi ERROR FORBIDDEN/UNAUTHORIZED khớp heartbeat pending, attempt và trace. Notice có collector và generation socket. Coordinator kiểm đúng lượt/collector/socket trước dừng collector/state/heartbeat. RETRYABLE_SERVER_ERROR không bị coi là mất quyền. Đây không phải wire message mới. Callback cũ không được dừng lượt mới.

Stop/logout đợi worker kết thúc, tháo listener/heartbeat lease và bỏ queue RAM với số lượng chưa xác nhận hiển thị. Kill JVM mất queue RAM; restart không giả phục hồi queue. Full/delta ACK chỉ xác nhận RAM server; PROCESS_OBSERVED/gap ACK xác nhận transaction DB đã commit. DB giữ lịch sử, không giữ current state qua server restart.

Bổ sung ổn định hóa PR #47 ngày 18/10: `FullSnapshotDelivery.stopped()` đợi worker và bước giải phóng correlation CLOSE. Thời gian chờ write CLOSE tối đa bằng `MonitoringDelivery.Settings.ackTimeout` (mặc định 5 giây); write lỗi hoặc không hoàn thành vẫn giải phóng correlation. CLOSE đã hết hạn còn nằm trong hàng đợi bị guard từ chối trước khi ghi. Đây là đóng best-effort, không phải chờ ACK server. Adapter giữ dấu correlation đã hủy trong cache tối đa `maxPendingAcks`, bỏ qua phản hồi ACK/ERROR/RESYNC lặp trong cửa sổ đó; retry cùng request ID và reconnect xóa dấu tương ứng. ERROR xác thực toàn phiên không có request ID vẫn chấm dứt kết nối. [Regression và bàn giao C](../evidence/pr47-stabilize-20261018/REPORT.md).

## Chạy demo bằng PowerShell

Từ thư mục dự án, JDK21, PostgreSQL đã sẵn sàng và `.env` local hợp lệ:

```powershell
mvn package
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
# Terminal máy server: mặc định FULL
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-monitoring-server.ps1 -JavaHome $jdk -Port 8080
# Terminal candidate hoặc proctor: thay bằng origin server thực tế
$serverUrl = 'http://<IP-may-server>:8080'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-monitoring-client.ps1 -JavaHome $jdk -ServerUrl $serverUrl -Mode Full
```

`<IP-may-server>` là chỗ cần điền, không phải IP đã kiểm. ServerUrl chỉ origin HTTP/HTTPS, không path/token/query. Client đăng nhập bằng tài khoản/lượt đã được nhóm cấp; scripts không tạo fixture trên DB dev. Chọn lượt ACTIVE, Bắt đầu giám sát; đọc nhãn FULL/DELTA và lý do.

Thử DELTA: dừng server do bạn sở hữu, chạy lại với `-DeltaExperimental`, rồi mở client với `-Mode DeltaExperimental`. Chờ kết nối/capability và bấm Bắt đầu. Nếu bấm sớm khi capability UNKNOWN, phiên chọn FULL; Dừng/Bắt đầu để chọn lại. Không bật delta mặc định sau demo.

Quay về FULL: bấm Dừng trên candidate, chờ nút Bắt đầu dùng được. Mở lại client với `-Mode Full`; server bỏ `-DeltaExperimental`. Giám thị cũng dùng ServerUrl ấy, đăng nhập PROCTOR; không cần bật delta ở proctor để đọc MONITOR_STATE.

## Tự chạy bộ kiểm local

```powershell
mvn package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t3c4.ps1 -JavaHome $jdk
```

Smoke tự tạo schema `delta_live_gate_test_<UUID>`, TEST users/attempts và server port riêng, hai candidate JVM thật + proctor JavaFX Application thật trên **một máy Windows**. Không sửa `.env`, không reset DB dev, không dừng server/client của người dùng. Mở Edge headless với profile riêng; chỉ dọn process/schema/socket do lượt chạy sở hữu. Không thay tài khoản production bằng mật khẩu TEST.

Normal candidate dùng default factory production. Candidate instrumented vẫn dùng production coordinator/collector/encoder/transport; nguồn đọc delegate REAL ProcessHandle, chỉ có TEST fault seams. Source pause/exception, base bị đổi và ACK bị ẩn khỏi delivery là SIMULATED, dù network/server/DB chạy REAL. Đó không phải đo mất gói mạng vật lý. PROCTOR dùng default Application, bảng thật và screenshot.

Artifacts: `server/target/t3c4-live/T3C4-<UUID>/`, metadata SHA/JAR/config/topology, report cases, screenshot, wire fault và raw measurement/trace. Log cứng bị kill không có FINAL/END sẽ INCOMPLETE; giữ nguyên, không sửa thành COMPLETE. Measurement không chứng minh latency/tiết kiệm byte/E1/E2.

## Checklist hai máy và review còn thiếu

Ghi lại máy A/server+PG, máy B/candidate, vị trí proctor; IP/port thực tế, JDK từng máy, HEAD sạch và SHA256 từng JAR. Candidate khác có thể trên máy A. Hai cửa sổ cùng máy không là LAN hai máy. Không trừ timestamp hai máy để giả đồng hồ đồng bộ.

| Ca | Thao tác / kết quả cần thấy |
|---|---|
| Start/capability/OPEN/full/ACK | DELTA chỉ khi opt-in + socket xác nhận; PROCTOR không collector |
| Process thêm/bớt | Mở/đóng Edge; current list đổi, lịch sử event giữ; so PID/start/metadata từ nguồn độc lập |
| Không đổi / full đối soát | Quan sát tối thiểu31s; scan mới/refresh được ACK, FULL thực tế sau30s; ONLINE không thay freshness |
| FULL fallback | Opt-in off, server off/cũ/missing marker/pending; FULL chạy, mode cố định đến Start mới |
| Mất ACK thử nghiệm | Server đã áp, ẩn ACK khỏi delivery; retry nguyên ID/payload/base/seq; duplicate không touch revision/freshness |
| RESYNC thực | Future base có kiểm soát; last-known UNSYNCED, callback chờ scan mới, FULL rồi SYNCED trên bảng giám thị |
| Hết retry | Dừng sau5attempts; fresh scan -> OPEN epoch mới -> full1 |
| Restart / old callbacks | Kill candidate riêng, restart server riêng; epoch mới, event/gap DB còn, candidate khác không bị dừng |
| Source lỗi/treo | HB vẫn ONLINE, state tự STALE6s; TTL production5phút (local TEST12s); không refresh HTTP/manually maintain để tạo PASS |
| Quyền/DB | Wrong role/scope, attempt/token/assignment bị thu hồi trước duplicate; lỗi query/commit không ACK giả |
| Cleanup | Stop/logout/socket callbacks cũ; worker/listener/stream đóng; dọn tài nguyên thuộc test |

Local automation không cưỡng bức OS PID reuse hoặc mọi metadata biến đổi; unit/replay chứng minh logic riêng. Physical packet loss, assignment revoke trên LAN, UI người thật, B approve, toàn luồng thi/Listening/IT02–IT04/E1/E2: chưa được suy PASS.

A cần review heartbeat scope notification, auth/binding trước duplicate và maintenance/restart. B cần review factory/mode, parser/callback generation, base/ACK/RESYNC, UX fallback và checklist LAN. T3-A3 không tự đánh dấu DONE. **Gate chưa chốt** vì LAN hai máy/human B review chưa chạy; không coi thiếu môi trường là delta thất bại. Có thể merge code với delta tắt mặc định khi không còn blocker kỹ thuật/GitHub.

## Ví dụ dễ giải thích

Thí sinh mở Edge. `ProcessHandleSnapshotSource.scan()` đọc process thật. `ProcessCollector` tạo snapshot — ảnh chụp danh sách tại một lần quét. Hai delivery nhận cùng snapshot: event ghi lịch sử mở Edge; delta cập nhật danh sách đang chạy. ACK là thông điệp xác nhận. Nếu server đã nhận delta nhưng ACK bị ẩn trong test, delivery gửi lại chính message ấy; server nhận ra trùng và ACK lại, không áp hai lần. Khi server báo RESYNC — yêu cầu đồng bộ lại — client bỏ base/pending cũ, chờ scan mới và gửi FULL. Callback là hàm được gọi khi phản hồi mạng đến; generation/epoch phân biệt phiên cũ và phiên hiện tại để phản hồi trễ không làm sai state. Lock là cơ chế chỉ cho một luồng sửa dữ liệu dùng chung tại một thời điểm; không giữ khóa state khi ghi socket/publish. RAM mất khi JVM dừng; DB giữ lịch sử đã commit.

## Runbook LAN hai máy cho gate 20/10/2026

Source chuẩn bị: `2b6c9cec1a8e4114c3f9d30117ece2a3f5e404f0`, base PR #43 `772c6e9864cd6ce0ad45aad0433f5bfce53fee54`. FULL vẫn là mặc định. Các lệnh dưới đây chưa phải bằng chứng LAN cho đến khi chạy trên hai máy Windows vật lý và điền báo cáo.

### Máy A — server và proctor

```powershell
cd D:\Toeic-Monitoring-Realtime
git rev-parse HEAD
git status --short
docker compose up -d --wait

$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
mvn -B -DskipTests package

Get-NetIPAddress -AddressFamily IPv4 |
  Where-Object { $_.IPAddress -notlike '127.*' -and $_.AddressState -eq 'Preferred' } |
  Select-Object InterfaceAlias,IPAddress
Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
Get-NetFirewallRule -Enabled True -Direction Inbound |
  Where-Object DisplayName -Match 'TOEIC|8080' |
  Select-Object DisplayName,Action,Profile

# Lượt FULL/fallback: không thêm -DeltaExperimental.
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-monitoring-server.ps1 `
  -JavaHome $jdk -BindAddress 0.0.0.0 -Port 8080

# Lượt DELTA riêng:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-monitoring-server.ps1 `
  -JavaHome $jdk -BindAddress 0.0.0.0 -Port 8080 -DeltaExperimental
```

Mở client proctor trên máy A bằng `scripts/start-monitoring-client.ps1 -ServerUrl http://<IP-LAN-A>:8080`. Proctor chỉ đọc dashboard và không được tạo collector. Chỉ owner máy A thay firewall nếu kiểm tra read-only cho thấy cần mở port.

### Máy B — candidate từ app-image đã đóng gói

Artifact local do C chuẩn bị, không commit vào Git:

- `target/t3c4-lan-prep/2b6c9ce/ToeicMonitor-T3C4-2b6c9ce.zip`
- SHA-256 ZIP: `9e920fba5d6440f002ef51f2ba629b49ddbb7dae12177a4c6282c1e2b34c4667`
- SHA-256 JAR: `b3febc078d845e181bffb914c08a7ad49e78250ee693075fd35a67eead40452f`

Máy build thiếu Windows SDK `mt.exe`, nên launcher chuẩn bị chưa được vá manifest UTF-8. Giải nén vào đường dẫn ASCII ngắn như `C:\ToeicGate`; không dùng artifact này để tuyên bố IT01 đường dẫn Unicode.

```powershell
$zip = 'C:\ToeicGate\ToeicMonitor-T3C4-2b6c9ce.zip'
Get-FileHash -Algorithm SHA256 $zip
Expand-Archive -LiteralPath $zip -DestinationPath C:\ToeicGate\run
cd C:\ToeicGate\run\ToeicMonitor
$serverUrl = 'http://<IP-LAN-A>:8080'

powershell -NoProfile -ExecutionPolicy Bypass -File .\smoke-lan-preflight.ps1 `
  -ServerUrl $serverUrl -ClientJar .\app\client-0.1.0-SNAPSHOT-all.jar `
  -DeviceLabel MACHINE-B

# FULL mặc định bằng runtime đi kèm app-image.
powershell -NoProfile -ExecutionPolicy Bypass -File .\start-packaged-client.ps1 `
  -ServerUrl $serverUrl -ClientDirectory .

# DELTA thử nghiệm dùng chính JAR đã đóng gói; máy B cần JDK 21 cho lượt này.
$jdk = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
& "$jdk\bin\java.exe" "-Dtoeic.server.url=$serverUrl" `
  '-Dtoeic.realtime.delta-experimental=true' `
  -jar .\app\client-0.1.0-SNAPSHOT-all.jar
```

Không hard-code URL vào artifact. Không ghi username Windows, token, mật khẩu hay IP LAN vào Git. Tài khoản/attempt TEST phải do owner cấp và tách khỏi DB dev.

### Trình tự nghiệm thu và evidence cần điền

1. Chạy A1–A4, rồi B với một process đúng production policy trên máy B; ghi PID/start/name từ nguồn đọc độc lập.
2. Chạy C và D bằng seam hiện có, ghi rõ `SIMULATED`; không gọi là mất gói LAN vật lý.
3. Chạy E bằng restart candidate rồi restart server. Candidate còn lại phải tiếp tục hoạt động.
4. Chạy F bằng thao tác ngắt LAN có kiểm soát trên máy B; không kill process thay cho ngắt LAN. Chờ UNKNOWN/STALE, nối lại và xác nhận FULL phục hồi cùng HTTP recovery.
5. Chạy G bằng API/fixture TEST, không sửa DB dev. Ghi số event/gap chưa ACK và kiểm worker/listener/correlation đã dọn.
6. Chạy H với proctor chưa assignment, candidate khác, JSON/ID ngoài scope; socket khác phải tiếp tục hoạt động.
7. Mỗi case ghi alias máy, Windows/runtime, source SHA, mode, REAL/MOCK/SIMULATED, expected, actual, timestamp UTC và đường dẫn raw local. Không dùng timestamp giữa hai máy để suy latency nếu đồng hồ chưa được chứng minh đồng bộ.

Checklist human B: mode/capability và fallback FULL; callback/generation sau reconnect; không gọi mạng trên JavaFX thread; dashboard dedupe và HTTP recovery; dọn listener/worker khi logout/close; hiển thị SYNCED/UNSYNCED/STALE/UNKNOWN; cách chạy hai máy và evidence LAN. Chỉ đổi review sang PASS khi thành viên B thực sự đọc/chạy và để lại ghi nhận.

Trạng thái chuẩn bị ngày 17/10/2026: local smoke 42/42 PASS trên một máy; LAN hai máy `NOT RUN`; human B review `NOT RUN`; gate `PARTIAL / NOT DECIDED`. Không tạo quyết định mới trong `QUYET_DINH.md` trước khi đủ ba điều kiện MT02–MT06, LAN thật và review B.
