# T3-C4 — nghiệm thu LAN vật lý cho B/C

19/10/2026: **LAN NOT RUN, 0/41; người thao tác/review máy thật PENDING**. FULL baseline, DELTA experimental. Bộ mới từ clean main `cfbba580886ede7b1b5a4f56875197b10f284514` sau PR47/57/58/59; không dùng kit3044/MSI0.3.10 lịch sử. MSI0.3.11 cùng source, runtime Java21 đi kèm; kit JAR phục vụ gate có JDK21 chỉ định tường minh. Không đổi protocol v0 hoặc quyết định DELTA gate.

| Artifact | SHA256 |
|---|---|
| client-0.1.0-SNAPSHOT-all.jar (cùng byte MSI) | `17bc89963dbd8439749dbb3a00436cba5f807e6044999d2050d586a78a5f95bd` |
| server-0.1.0-SNAPSHOT.jar | `f90a4dea2bd00a5a9d8d6e992492659e6867dbb87ec1e9593c7a5317ce2baf42` |

Hai JAR và test-support build từ source trên, đã chạy clean test/package với PG thật. Faults do TEST instrumented child/probe vẫn SIMULATED; không dùng chúng thay lượt F rút LAN thật. Guard strict source/hash trong Common.ps1 được cập nhật bằng source và hash build đã kiểm, không nới điều kiện. Kit cũ và evidence giữ nguyên trong lịch sử/target.

## 1. Nhận và kiểm bộ cuối

Nhận `T3C4-cfbba58.zip`, `.zip.sha256.json` và MSI0.3.11 từ `target/stage3-release-20261019/` trên máy build, qua USB/kênh riêng được nhóm cho phép. ZIP/MSI ngoài Git; chưa có bằng chứng đã chuyển sang B. Lấy ZIP/manifest/MSI hash độc lập từ `evidence/stage3-execution-20261019/artifacts.json` trên main GitHub hoặc bàn giao cuối, không chỉ tin checksum đi cùng file. Không gửi `.env`, token, profile hoặc raw máy build.

Trên cả A/B, PowerShell thường:

```powershell
$zip=Read-Host 'Duong dan ZIP nhan tu may build'
$expectedZip=Read-Host 'ZIP SHA256 doc doc lap tu artifacts.json tren main'
if($expectedZip -notmatch '^[0-9a-f]{64}$' -or (Get-FileHash -LiteralPath $zip).Hash.ToLowerInvariant() -cne $expectedZip){throw 'ZIP_HASH_MISMATCH_STOP'}
$bundle='C:\ToeicGate\T3C4-cfbba58'
if(Test-Path $bundle){throw 'BUNDLE_EXISTS_NO_OVERWRITE'}
Expand-Archive -LiteralPath $zip -DestinationPath C:\ToeicGate
$expectedManifest=Read-Host 'Manifest SHA256 doc doc lap tu artifacts.json'
if($expectedManifest -notmatch '^[0-9a-f]{64}$' -or (Get-FileHash "$bundle\artifact-manifest.json").Hash.ToLowerInvariant() -cne $expectedManifest){throw 'MANIFEST_HASH_MISMATCH_STOP'}
. "$bundle\scripts\lan-t3c4\Common.ps1"
$verified=Get-LanBundle $bundle
if($verified.files.Count -ne 22){throw 'UNEXPECTED_BUNDLE_FILE_COUNT'}
$kit=Join-Path $bundle 'scripts\lan-t3c4'
$run='C:\ToeicGate\runs\physical-01'
$jdk=Read-Host 'Duong dan JDK21 chi dinh rieng cho gate JAR'
$serverUrl=Read-Host 'Origin IPv4 LAN that cua A va port'
& "$kit\Initialize-T3C4LanRun.ps1" -BundleDirectory $bundle -OutputDirectory $run
```

Ghi biên nhận hash/source trên cả máy; 41case và reviewer vẫn NOT RUN/PENDING. Không sửa checksum nếu không khớp. Kit có JAR/script/test-support, không phải bằng chứng MSI máy hai. Lượt MSI riêng phải chạy launcher không IDE/JDK trên PATH, Unicode path và cài/chạy/gỡ; B ghi Actual, A xem evidence. Nội dung checklist/tiêu chí dưới đây giữ theo PR52; Source SHA trong template đã đổi sang bản cuối mà không điền kết quả.

## 2. Environment preflight

- **Máy A vật lý:** PostgreSQL TEST riêng + Spring server + PROCTOR + CANDIDATE_A. **Máy B vật lý:** CANDIDATE_B. Hai candidate khác tài khoản/attempt, cùng assigned proctor. Dùng LAN IPv4 thật cho mọi client, kể cả client trên A. DB chỉ trên A; máy B không cần credential DB.
- Owner cấp DB TEST riêng, đề/ca thi và audio hợp lệ theo QD-29; không dùng attempt legacy chỉ có monitoring. Dành attempt mới cho Reading, Listening và các probe SIMULATED; không dùng probe trên attempt của F. Duration phải đủ toàn lượt, không gia hạn/reset deadline giữa case.
- Kiểm port, interface, đường đi, firewall chỉ đọc. Chỉ owner được phép quyết định đổi rule/adapter. Bộ kit không đổi firewall, service, registry hoặc adapter. Không dùng localhost/127.0.0.1 giữa hai máy, VPN/VM/hai cửa sổ không chứng minh hai Windows vật lý cùng LAN.

Trên A, cấu hình TEST trong process PowerShell riêng. Không Start-Transcript lúc nhập secret; không ghi env ra console/file. Các giá trị người vận hành nhập ở máy, không commit.

```powershell
$env:DB_HOST='127.0.0.1' # PostgreSQL cung may A, khong phai URL client
$env:DB_PORT=Read-Host 'Port PostgreSQL TEST da duoc owner cap'
$env:DB_NAME=Read-Host 'DB TEST rieng, ten bat dau t3c4_test_'
if($env:DB_NAME -notmatch '^t3c4_test_[A-Za-z0-9_]+$'){throw 'Chi dung DB TEST rieng'}
$db=Get-Credential -Message 'DB TEST account'
$env:DB_USER=$db.UserName; $env:DB_PASSWORD=$db.GetNetworkCredential().Password; $db=$null
$seed=Read-Host 'Mat khau seed TEST rieng do owner cap' -AsSecureString
$env:TOEIC_SEED_PASSWORD=([Management.Automation.PSCredential]::new('TEST',$seed)).GetNetworkCredential().Password
$env:TOEIC_AUDIO_ROOT=Read-Host 'Thu muc audio TEST rieng tren may A'
$origin=[Uri]$serverUrl
Get-NetIPAddress -AddressFamily IPv4 | Select-Object InterfaceAlias,IPAddress
Get-NetTCPConnection -State Listen -LocalPort $origin.Port -ErrorAction SilentlyContinue
Get-NetFirewallProfile | Select-Object Name,Enabled,DefaultInboundAction
# Thu muc private nay co the chua log IP/tai khoan: khong commit raw.
New-Item -ItemType Directory -Force "$run\private-server" | Out-Null
& "$jdk\bin\java.exe" '-Duser.timezone=UTC' '-jar' "$bundle\server\target\server-0.1.0-SNAPSHOT.jar" `
  "--server.address=$($origin.Host)" "--server.port=$($origin.Port)" '--toeic.state.delta-enabled=false' `
  *> "$run\private-server\FULL.log"
```

Chạy server trong cửa sổ riêng, giữ PID sở hữu và xem log riêng để xác nhận startup/Flyway/TEST DB. Khi kết thúc, Ctrl+C đúng cửa sổ server; xóa env secret của cửa sổ đó hoặc đóng cửa sổ. Không stop DB chung. Server FULL/fallback là lượt đầu. Lượt DELTA tách riêng chỉ thay flag runtime thành `--toeic.state.delta-enabled=true`, ghi restart và instance mới; không sửa properties mặc định.

Sau server sẵn sàng, trên A và B tương ứng:

```powershell
# A thay MACHINE_B bang MACHINE_A; chay ca hai may.
& "$kit\Test-T3C4LanPreflight.ps1" -ServerUrl $serverUrl -Machine MACHINE_B `
  -BundleDirectory $bundle -RunDirectory $run -JavaHome $jdk
```

Preflight yêu cầu origin không loopback, A có địa chỉ local đó còn B không có; hash/JDK21 đúng, TCP tới A và `/api/v1/auth/me` không token trả401. Đây chỉ là transport preflight, chưa chứng minh DB/WS/assignment hoặc cable loss. Kiểm login qua GUI, một OPEN/FULL và proctor HTTP200 để chứng minh các phần còn lại. Nếu listener/firewall/route lỗi, dừng case, báo owner; không tự mở port hoặc tắt bảo vệ.

## 3. Chạy A–H trên desktop thật

Mở ba PowerShell riêng. A chạy PROCTOR và CANDIDATE_A, B chạy CANDIDATE_B. Đăng nhập qua GUI, role do server quyết định. Giữ nguyên `$run` và profile theo role qua reconnect/relaunch; không xóa marker/cache để nghe lại.

```powershell
& "$kit\Start-T3C4LanClient.ps1" -ServerUrl $serverUrl -BundleDirectory $bundle `
  -RunDirectory $run -JavaHome $jdk -Role CANDIDATE_B -Mode Full
# May A: lap lenh voi -Role PROCTOR va -Role CANDIDATE_A o hai cua so rieng.
```

| Nhóm | Bước và expected cần ghi |
|---|---|
| A1 | Candidate chọn attempt hợp lệ, start/stop; proctor chỉ dashboard, không collector. |
| A2 | Server không delta, client Full; rồi server có capability nhưng client không opt-in: đều FULL. |
| A3 | Server không capability, client `-Mode DeltaExperimental`: fallback FULL; reconnect phải đánh giá capability server hiện tại. |
| A4 | Lượt TEST server flag true + client opt-in: DELTA_EXPERIMENTAL, OPEN/FULL đầu epoch và đối soát FULL; mode cố định trong session. Không dùng lượt này thay kết luận bật DELTA production. |
| B | B mở/đóng ứng dụng TEST đúng policy; đọc PID/start/name bằng `Get-Process -Name msedge | Select-Object Id,ProcessName,StartTime`. Chỉ đóng cửa sổ/process TEST do mình tạo. Đối chiếu ProcessHandle, dashboard và eventId; process tồn tại qua nhiều poll không tạo event lặp. UI dùng “quan sát thấy”. |
| C/D | Dùng fault console mục 5 trên attempt riêng; giữ raw private, checkpoint trước/sau, nhãn SIMULATED. Retry giữ nguyên ID/envelope; RESYNC giữ last-known, scan mới FULL; phản hồi muộn không sửa generation cũ. |
| E | Ghi baseline; đóng/mở candidate TEST cùng profile, epoch/collector mới, candidate còn lại tiếp tục. Restart đúng Spring TEST trong cửa sổ sở hữu; hai client reconnect, serverInstanceId/epoch mới, FULL mới, event/gap DB giữ. Listening không tự phát lại. |
| F | Thực hiện đầy đủ mục 4, không seam hoặc protocol probe. |
| G | Stop/start rồi logout và đóng từng app TEST; lưu thời điểm, worker trước/sau, pending/listener nếu có diagnostics. Với PID đã xác minh sở hữu: `jcmd <PID> Thread.print` lưu riêng; đối chiếu toeic-process-collector, toeic-monitoring-delivery, toeic-full-snapshot. Sau stop không còn worker monitoring cũ; realtime/HTTP dùng chung có thể còn tới logout/exit. Không kill Java/Edge hàng loạt. |
| H | Owner cấp tài khoản candidate khác và proctor không assignment. GUI không đủ: gửi GET state/events/gaps/interruptions với token trong RAM qua `Invoke-LanHttp`, phải403; thiếu token401. Candidate/PROCTOR gửi MONITORING_FULL ngoài quyền phảiERROR FORBIDDEN. Gửi JSON/type/ID sai qua WS của attempt TEST, ghi phản hồi và A vẫn hoạt động. Dùng probe/harness mục 5 cho subset, không giả các nhánh chưa thử. |

## 4. F — mất LAN vật lý và phục hồi

Chạy một lượt Reading, rồi một lượt Listening đang phát trên attempt mới. Lượt outage dài phải vượt ngân sách retry và ghi nhận cách reconnect thực tế. Không reset profile, bài cũ hoặc deadline để làm case PASS. Poll mặc định1s, heartbeat2s, presence timeout6s/state stale6s (scan scheduler có độ trễ); đọc cấu hình thực trước run, không đặt PASS bằng một lần sleep6s. Ghi thời điểm server lastSeen/timeout và UI; không trừ timestamp hai máy chưa đồng bộ để suy latency.

Trên A mở cửa sổ checkpoint; credential chỉ trong RAM:

```powershell
$attemptA=Read-Host 'Attempt CANDIDATE_A TEST'
$attemptB=Read-Host 'Attempt CANDIDATE_B TEST'
$proctor=Get-Credential -Message 'Assigned TEST proctor'
function Checkpoint([string]$name) {
  & "$kit\Capture-T3C4LanCheckpoint.ps1" -ServerUrl $serverUrl -RunDirectory $run `
    -Checkpoint $name -AttemptA $attemptA -AttemptB $attemptB -Credential $proctor
}
Checkpoint F-READING-BEFORE
# Cac lenh tiep theo chi chay sau thao tac vat ly tuong ung.
```

1. **F01:** hai candidate ACTIVE và proctor cùng hoạt động; cả A/B có state SYNCED/presence ONLINE, lưu một đáp án hợp lệ ở mỗi candidate; checkpoint và SQL baseline. Mọi endpoint cần thiết200;403/500 hoặc thiếu candidate làm case BLOCKED/FAIL, không dùng digest null làm bằng chứng.
2. **F02:** B rút cáp LAN thật có người chứng kiến. Nếu dùng Wi-Fi, chỉ owner máy được phép ngắt bằng thao tác trực tiếp; ghi interface/link và loại trừ route dự phòng được phép. Không dùng kill app, stop server, PAUSE, ACK_DROP hoặc firewall block thay rút mạng. Video/ảnh link mất + thời điểm thao tác giữ riêng; bản công bố che IP/SSID/tên máy/người.
3. **F03/F04:** A tiếp tục thao tác/save; proctor thấy B UNKNOWN (presence) và STALE (process state), giữ last-known, không kết luận gian lận. Chạy `Checkpoint F-READING-OFFLINE-1`, rồi `Checkpoint F-READING-OFFLINE-2` sau khi A có cập nhật mới; chứng minh A tiến triển trong lúc B không liên lạc. Nếu B vẫn tới server, kiểm route dự phòng, không kết luận đã mất LAN.
4. **F07:** trong lúc B offline thao tác Reading theo contract và chụp trạng thái local pending; không báo SAVED/ACK khi chưa có phản hồi tương ứng. ACK FULL/DELTA chỉ state RAM; ACK event/gap cần SQL COMMIT. Measurement không chứa toàn bộ payload/DB commit, nên không đủ để chứng minh “không false ACK”; kết hợp UI, client correlation, server nhận/commit và checkpoint. Thiếu evidence này giữ NOT RUN/PARTIAL cho F07.
5. **F05/F06:** B nối cáp lại. Ghi network route/TCP phục hồi; client re-auth, OPEN/epoch mới, FULL scan mới rồi SYNCED. Retry tự động có giới hạn; nếu FAILED/exhausted, dùng nút kết nối lại theo UI, ghi rõ manual reconnect. Không tự khẳng định reconnect vô hạn. Lấy `Checkpoint F-READING-RECOVERED`.
6. **F08/F09:** so eventId/gapId và interruption trước/trong/sau. Event retry giữ một identity; không bỏ history khi FULL mới. Mất heartbeat sinh interruption, không tự động là MONITORING_GAP/QUEUE_OVERFLOW; chỉ đòi gap khi thật có overflow. Đáp án committed/deadline/writer và result không bị reset; server digest có thể tăng do save hợp lệ sau phục hồi, phải đối chiếu thao tác/ack, không ép mọi digest bất biến. Không takeover để nối lại cùng app.
7. **F10/F11:** trên attempt Listening mới, audio đang phát, người thật nghe và ghi nhận. Lặp checkpoint `F-LISTENING-BEFORE`, `F-LISTENING-OFFLINE`, `F-LISTENING-RECOVERED`; outage dài qua retry budget. Audio dừng/khóa khi phát hiện gián đoạn, cờ interrupted có trên server, reconnect/relaunch không autoplay/resume/replay; Reading và committed Listening giữ theo contract. Đừng bấm xác nhận “nghe được” thay người thật. Reorganization nếu cần là ca/attempt mới, ngoài lượt đối chiếu bài cũ.
8. **F12/G:** stop/logout sau recovery, CLOSE hoàn tất trong giới hạn cấu hình kể cả transport lỗi; không overlap worker khi start lại. Ghi UI và jcmd trước/sau, profile vẫn giữ. Đóng đúng app/server TEST của run; không tự xóa DB, audio marker hoặc process khác.

SQL đọc trên A (owner cấp PGHOST/PGPORT/PGDATABASE/PGUSER ngoài Git, `-W` hỏi password):

```powershell
# Chay truoc va sau; -o file rieng theo checkpoint, khong ghi de.
& 'C:\PATH-TO-POSTGRES\bin\psql.exe' -X -W -v ON_ERROR_STOP=1 `
  -v "attempt_a=$attemptA" -v "attempt_b=$attemptB" `
  -f "$bundle\docs\templates\t3c4-lan\verify-history.sql" -o "$run\F-READING-BEFORE-sql.txt"
```

SQL chỉ đọc, rollback cuối; cần đủ hai row và duplicate IDs=0. Count=0 không chứng minh retry/commit đã chạy. Hash MD5 SQL và SHA256 checkpoint là hai định dạng khác nhau; chỉ so mỗi loại với chính nó. Checkpoint lọc tên/answers/process details; raw UI/server/WS/SQL bổ sung vẫn cần C kiểm duyệt riêng trước công bố.

## 5. Fault TEST và MT02–MT06

Các lệnh dưới đây **chưa chạy trong Task 2**. Dùng attempt TEST riêng, không có collector khác, server delta flag true cho lượt này. Chạy console trên B với origin IP thật thì transport tới A là thật; payload/fault vẫn MOCK/SIMULATED, không thay F.

```powershell
& "$kit\Start-T3C4LanFaultConsole.ps1" -ServerUrl $serverUrl -BundleDirectory $bundle `
  -RunDirectory $run -JavaHome $jdk -DedicatedTestAttempt
```

Console tự login candidate TEST qua prompt, nhập từng dòng JSON, chờ GATE_REPLY rồi lấy checkpoint/ảnh ở proctor. Không publish GATE_REPLY thô vì có label/tên attempt. Thay placeholder tại máy, không commit tên thật.

```json
{"id":"s1","action":"START","attempt":"ATTEMPT_TEST_RIENG"}
{"id":"s2","action":"STATUS"}
{"id":"c1","action":"ACK_DROP_ONCE"}
{"id":"c2","action":"STATUS"}
{"id":"c3","action":"RESUME"}
{"id":"c4","action":"RELEASE_OLD_ACK"}
{"id":"c5","action":"ACK_DROP_ALL"}
{"id":"c6","action":"STATUS"}
{"id":"c7","action":"ALLOW_ACK"}
{"id":"c8","action":"RESUME"}
{"id":"d1","action":"FUTURE_BASE"}
{"id":"d2","action":"STATUS"}
{"id":"d3","action":"RESUME"}
{"id":"m2","action":"REPLAY_STATE"}
{"id":"g1","action":"STOP"}
{"id":"g2","action":"CLOSE"}
```

Không paste toàn khối liên tiếp. ACK_DROP_ONCE đợi ít nhất retry thấy cùng ID và checkpoint không apply hai lần; RESUME cho scan mới. ACK_DROP_ALL đợi exhaustion theo config, không đổi sang PASS vì transport đã nhận ACK bị seam che; restart monitoring nếu trạng thái yêu cầu. FUTURE_BASE giữ source sau khi sửa wire một lần, đợi UNSYNCED/RESYNC rồi RESUME mới có FULL. Late ERROR/RESYNC không được console này inject; dùng regression `RealtimeClientTest`/`FullSnapshotDeliveryTest` trên source Task 1 và ghi TEST evidence riêng, không ghi đã thử trên LAN.

Probe sau dùng WebSocket chuẩn, bearer chỉ trong header, timeout10s/exchange, response được lọc. Nó gửi observations giả và replay có chủ ý, rồi CLOSE trong finally; không chạy trên bài F.

```powershell
$probeAttempt=Read-Host 'Attempt TEST rieng khong co collector khac'
& "$kit\Invoke-T3C4LanProtocolProbe.ps1" -ServerUrl $serverUrl -RunDirectory $run `
  -Attempt $probeAttempt -DedicatedTestAttempt
```

| Hợp đồng | Lệnh/subset và phần phải bổ sung |
|---|---|
| MT02 | Probe replay delta cùng envelope, ID khác payload CONFLICT, state revision/receivedAt không đổi. Event/gap cần replay cùng eventId/gapId qua harness và SQL unique/commit; chạy `scripts/smoke-a3.ps1` theo hướng dẫn script trên checkout đồng bộ để thử deferred COMMIT/rollback (TEST fault SIMULATED), không trên DB của F. |
| MT03 | Probe future base → BASE_MISSING RESYNC/UNSYNCED giữ last-known; console kiểm client parser/callback bỏ base rồi FULL scan mới. |
| MT04 | Probe kết nối lại OPEN/epoch mới + FULL20, replay delta epoch cũ → STALE và canonical state không đổi. |
| MT05 | Probe collector/epoch cũ không overwrite; mục E chạy restart candidate/server thật với checkpoint, server instance và history DB. Probe không tự restart server. |
| MT06 | Probe FULL20, FULL19 STALE và cùng seq khác payload CONFLICT; bổ sung ba trace ít đổi/nhiều đổi/reconnect với oracle state/event bằng full/delta qua lệnh bên dưới. Không coi chỉ hai process set bằng nhau là đủ toàn case. |

Trên checkout đồng bộ có Maven/JDK và fixtures đã version-control, chạy lại bộ replay/hook; các script smoke tạo server/DB TEST riêng hoặc loopback, do đó gắn đúng nhãn, không tính thêm physical case:

```powershell
# Doc param/help trong script; chi chay khi DB TEST va quyen tao schema da san sang.
Get-Help .\scripts\smoke-a3.ps1 -Full
Get-Help .\scripts\smoke-t3c2.ps1 -Full
.\scripts\smoke-a3.ps1 -JavaHome $jdk
.\scripts\smoke-t3c2.ps1 -JavaHome $jdk
.\scripts\smoke-t3c3.ps1 -JavaHome $jdk
```

`smoke-t3c3` gồm ba fixture MOCK, duplicate/drop/reorder/reconnect và negative controls; lưu seed/raw/summary/hash/source của lượt mới. `smoke-a3` cung cấp commit-before-ACK, `smoke-t3c2` restart/resync. Chỉ tổng hợp PASS toàn MT khi B/C đối chiếu đủ expected, evidence live subset + replay oracle + DB, không cộng PASS cũ của Task 1. Nếu tìm bug tái hiện được, giữ evidence, tạo corrective PR riêng kèm regression; không sửa DELTA tại nhánh bàn giao.

## 6. Handoff và kết luận do B/C điền

B điền cases.csv, topology và nghe/GUI thật; C đối chiếu protocol/epoch/ACK/event/gap/SQL/worker và review file production PR47 (FullSnapshotDelivery.java, RealtimeClient.java cùng regression). A cần xem nếu phát hiện auth/transaction/contract server. Không yêu cầu owner approve thay người khác; PR47 vẫn chờ C, CI PASS không thay chữ ký.

Trước công bố, giữ raw riêng bất biến, tạo bản dẫn xuất có checksum, che token/tài khoản/IP/hostname/SSID/answers; review cả ảnh và log, không chỉ grep. Báo cáo cần LAN PASS/FAIL/PARTIAL/NOT RUN, số physical case thực chạy, case MOCK/SIMULATED, source/runtime/JAR/ZIP hashes hai máy, blocker và human B CONFIRMED/PENDING. Không tự cập nhật TRACKER, QD owner approval hoặc quyết định FULL/DELTA gate. Nếu chưa có máy B: giữ **NOT RUN / 0 / B PENDING**.
