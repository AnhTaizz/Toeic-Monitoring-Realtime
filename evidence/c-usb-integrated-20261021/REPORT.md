# USB integrated TEST — CIM → COMMIT/ACK/history

**USER_AUTHORIZED_EXPERIMENT** attachment e80d60fa, 20–21/10/2026. Không human approval
A/B/C, ADR-V2-05/policy/gate acceptance hoặc physical hotplug. FULL mặc định, MON07
experiment OFF trong cấu hình default; MON02/MON07 chưa DONE, USB physical0/12 NOT_RUN.

## Kết quả cụ thể

Đã có **một lệnh demo** tự dựng PostgreSQL/server TEST, tạo scope bằng seeder/API,
đọc CIM thật hữu hạn, gửi signal/status qua shared lane và kiểm HTTP history rồi teardown:

```powershell
.\scripts\demo-usb-integrated.ps1 -Mode Real -ReadUsbStorage
```

Chạy tại root, Windows/JAVA_HOME Java21/Maven3.9/PostgreSQL18 tools; xem
[prerequisites/flags/stop](../../docs/USB_INTEGRATED_TEST_DEMO.md). Mặc định MSFT_DISK,
duration15s/fixed-delay1000ms/queryTimeout5000ms; thông báo trước đọc và không cần USB.
Không DB/server dev/Docker/.env. Không token/password trên command line, URL hoặc
report; bootstrap password qua stdin offline rồi SCRAM trước listen, không plaintext
password file. Không default candidate observer, UI/push/rules/MON08/E1/E2 hoặc policy60s/30d.

**Actual REAL cuối:** 10scan, READY/local observed storage count0, STANDARD,
PowerShell5.1.26100.9711. Chỉ1row **OBSERVER_STATUS/WINDOWS_USB_STORAGE/COMPLETE**,
details `{"status":"READY","reason":"INITIAL"}`, timelineSequence1 được COMMIT,
ACK1 và canonical HTTP history readback. **0USBevent**, không PRESENT count0 hoặc
synthetic removal. Count0 là số local provider quan sát, không field count giả trong
status hoặc chứng minh vật lý máy không có thiết bị.

After close: child0/pipeReadersTerminated=true/scan+control+delivery workers stopped,
pending/drop/unreported/unconfirmedGap0; ownedSchemasRemaining0/clusterStopExit0/
postmaster.pid absent. Không còn server/schema/cluster của lượt TEST đang chạy.

## Source và ranh giới module

- Base main **b68e19afca151e53dd03cb3ff5741d4d08e5ea73**, sau B PR87/88; fetch/audit
  open PR không có integrated USB task trùng. Worktree/branch riêng
  `feat/c-usb-integrated-demo-20261020`, không reset/rebase/ghi đè/xóa công việc cũ.
- First composition **36cc749123f2b88ded899d3443e8435baf337572**; coordinator runtime
  cuối **fdc90a130330b0264d01925a04eb6dd72e426e36**; test logging/stop marker
  **33e4a7eb866aca07c2bb35393da54b7fd3814071**; final executable demo/build/history
  **e7cd9dc6578a70a4b6ab3adfaff34ccdb9d96863**. Sau fdc không đổi client runtime.
- Publication HEAD/PR/CI/merge SHA/tree ghi trong PR sau khi thực hiện; không gán
  source SHA docs mới cho log/JAR lịch sử. Receipt giữ đúng source mỗi invocation.

Client giữ `UsbScanCoordinator` provider-agnostic, Source interface/immutable Reading;
**không dependency CIM**. `CimUsbScanSource` và `UsbSnapshotProjection` chỉ server test
classpath, vốn đã có test-scope client/monitoring-spike dependencies. Không module/lib/
native framework mới. Projection cũ được tách một chỗ rồi Mock wrapper delegate;
không copy provider/parser/resource hoặc tạo scanner thứ hai. Client/server runtime
JAR không chứa CIM provider/TEST demo. Probe CLI/parser/classification/timeout/output
cap/process cleanup giữ nguyên. Không migration/typed DTO/service/auth/transport change.

## Luồng và ownership

1. Script init own cluster/DB loopback nonce, Spring TEST bind127.0.0.1:port0/schema UUID,
   bật lane **chỉ instance TEST**. Seeder hiện có tạo MOCK accounts, HTTP login PROCTOR/
   CANDIDATE → import READING MOCK → create-session API → `/auth/me` xác nhận attempt.
   Không SQL INSERT application user/attempt/assignment để cấp quyền trái luồng.
2. Một RealtimeClient/socket, scoped heartbeat lease + capability HTTP fresh, confirm
   cùng observerSessionId/collector lease ID; một SignalRunContext/SignalDelivery chung.
   Coordinator chỉ start khi scope/lease/capability ready, giữ socket generation.
3. Source gọi nguyên CimSnapshotSource/MSFT_DISK. Query chỉ Number/BusType; projection
   lọc classification USB, incomplete giữ UNKNOWN. Adapter baseline/diff và status
   mapping tạo immutable typed events chung sequence/origin/IDs. Wire không raw slots,
   serial/path/model/username hoặc exception. Status reason enum, không raw problem.
4. Queue/socket/retry/capability admission+pre-write guard → server auth/transaction →
   COMMIT → ACK; assigned PROCTOR ACTIVE đọc paged no-store/fixed-through history.
   Test so toàn bộ canonical signal content với frozen messages, ID/sequence không đổi.
5. Coordinator sở hữu source, USB adapter/status emitter, scan/control workers;
   mượn context/delivery/lease/socket. Stop invalidate generation trước dọn, close source
   async trên control worker, chặn old callback. Runner owner đóng lane và kiểm pending/
   drop/unreported. Không gửi bù sau revoke/attempt mới hoặc gọi flush sau owner stop.

Một scan worker fixed-delay không overlap, một control worker flush100ms/duration/
shutdown, không source IO/chờ shutdown trên FX. Cadence là delay sau query, không hứa
1Hz khi startup/query chậm. Health mới chỉ biết khi query trả về, flush pending cũ
không bị query giữ. Config cadence250..10000ms/query100..10000ms/duration1..120s; trường
hợp queryTimeout>duration được dừng bởi source.close khi duration hết, không overlap.
Provider giữ timeout gồm startup/process wait, finite pipe16KiB mỗi stream và bounded
reader/child cleanup. Coordinator không thêm query/retry implementation khác.

Status READY INITIAL/RECOVERED COMPLETE; UNKNOWN NO_DATA UNREADABLE, UNSUPPORTED
API_UNAVAILABLE UNSUPPORTED, ERROR PERMISSION_DENIED/SCAN_FAILED UNREADABLE. UNKNOWN/
ERROR không đổi USB baseline thành rỗng hoặc removal; hồi phục chỉ observed diff tại
scan khỏe. Weak disk slot/ref semantics PARTIAL theo contract cũ, không physical ID.
Queue overflow vẫn SIGNAL_GAP count thật; heartbeat/presence UNKNOWN là luồng riêng.

## Kiểm chứng đã chạy

| Lượt | Source | Actual |
|---|---|---|
| Baseline trước sửa | b68e19a | 194PASS/0SKIP =37protocol+34probe+118client+5server |
| Unit/shared/provider đầu | worktree trước commit36cc749 | 204PASS/0SKIP,10coordinator tests mới; không gán như final source |
| MOCK integrated fixed | fdc90a1 | 4PASS/0SKIP,0schema/PGstop0 |
| REAL đầu | 33e4a7e | 1completed run PASS,10scan READY0/1status/0USB,cleanup sạch |
| MOCK integrated cuối | e7cd9dc | 4PASS/0SKIP,canonical history+XML archived,0schema/PGstop0 |
| REAL cuối | e7cd9dc | 1completed run PASS,10scan READY0/1status/0USB,history/XML,cleanup sạch |
| REAL file-stop | e7cd9dc | 1scan rồi CANCELLED/metadata130/shell1/pipelineVerified=false,cleanup sạch; không completed run PASS |
| Full verify/JAR | e7cd9dc | **1369total=1220PASS/149SKIP/0FAIL/ERROR**, PG/CIM/GUI opt-in false |
| Artifact boundaries | e7cd9dc | **15checks PASS**,4JAR SHA256, standalone probe explicit-classpath --help exit0 |

Không cộng repeated runs/scans/frames thành test mới. Thêm15test methods:10unit và
5guarded PG methods (4MOCK +1REAL), actual REAL/CANCELLED tách rõ. Full có5PG methods
SKIP do explicit gate, không nhận chúng PASS; các ca đó đã chạy riêng như bảng. Final
coordinator unit10PASS được đối chiếu XML từ full invocation; baseline/unit đầu giữlog.
Full totals lấy log invocation chính xác, không cộng XML cũ. Runtime không đổi sau
fdc; JAR pin e7, không cần chạy lại provider chỉ vì docs publication đổi HEAD.

### MOCK/SIMULATED qua mạng/DB thật

Timeline độc lập expected: READY{1} → {1,2} → unchanged → ERROR → recovery{2,3} → empty.
Actual9messages, common observationSequence1..9:6USB events có counts1,2,2,2,0,0 và
3status READY INITIAL/ERROR SCAN_FAILED/READY RECOVERED. ERROR không removal; recovery
không dựng thời điểm cắm/rút trong khoảng thiếu data. Retry ACK loss SIMULATED đầu
tiên giữ nguyên envelope/IDs/payload/time, chỉ1stored row/sequence, tổng9commits/9ACK.

Revoke fixture chỉ UPDATE token revocation trong TEST để **thu hồi**, không tạo/grant
scope; actual scoped WS authorization denial stops blocked source/queue/worker,0write.
Business timeout tạo ca2s và takeover API thật, scheduler chốt TIMED_OUT, heartbeat
scope denial dừng nguồn. Deferred COMMIT fault SIMULATED trong schema TEST rollback
event/counter0, không success ACK; bỏ fault thì retry cùng message commit1/history1.
Các SQL fault/revoke không bypass auth; mọi application scope từ seeder/API.

Unit dùng controlled queue/latch: slow query không overlap, stop khi scan chờ và
late READY99 không enqueue, reconnect không resume old run, delivery revoke dừng
source độc lập scan, duration cleanup, double start/ready gate, queue admission/drop,
shared sequence và unknown/unsupported không count0, finite config/scope validation.
Provider regression query timeout/output cap/cleanup/classification cũ đều chạy lại,
không đổi timeout production hoặc expected để né lỗi.

### REAL source và dừng

Source REAL không dùng ScriptedSource/MOCK snapshot. App seed/session là MOCK TEST
scope, transport/HTTP/WS/PG/CIM đều thật. Real observer0USB không tạo USB event, chỉ
status READY được DB/ACK/history xác nhận. Actual privilege STANDARD, không cần/nâng
admin để che lỗi. Method MSFT là lựa chọn thử nghiệm dựa BusType/readiness, không
PnP/Win32/hardware compatibility/ADR acceptance.

File-stop được parent driver đợi scan-observed.flag rồi tạo stop.request, không dựa
sleep để chứng minh scan concurrency. Actual CANCELLED sau1scan, resources stopped,
metadata130 và PowerShell1 do runner báo incomplete; không gọi đó là completed
pipeline PASS. Chưa kiểm phím Ctrl+C/đóng cứng console, không nhận H11 PASS. Nếu stop
trước query/worker hoàn tất hoặc cleanup false thì giữ INCOMPLETE, không broad kill.

## Failures giữ nguyên

1. **36cc749 runner setup:** PostgreSQL log READY nhưng redirected pg_ctl capture chờ
   EOF từ handle kế thừa; Maven/feature tests chưa chạy. Đã stop duy nhất own cluster
   bằng absolute data path được kiểm. Sau đó createdb thất bại vì cluster đã dừng,
   final cleanup stop1/pid absent phản ánh manual stop, không automatic cleanup PASS.
   **fdc90a1 fix:** startup helper NoCapture + exit receipt/log riêng, không pipe wait;
   các lượt sau setup/teardown đều0. Giữ raw/result lỗi, không xóa/rerun tới PASS rồi
   nhận lỗi feature đã sửa. Không source provider/timeout/ACL/firewall change.
2. Probe CLI đầu gọi `java -jar ... --help`, thất bại “no main manifest attribute”.
   Đây là sai invocation; probe JAR vốn dùng explicit classpath/entrypoint như runbook.
   Lệnh đúng `java -cp <probe.jar> vn.edu.toeic.monitoring.usb.UsbStorageProbe --help`
   exit0, không đọc CIM, source/pom/CLI không bị sửa để che lỗi.

## Evidence và cleanup

[Receipt](receipt.json) chứa125local artifact hashes, source từng run, exact full
totals/JAR checks và safe actual HTTP row projection. Raw/history/XML/JAR/log/pgdata
ở local ignored `target/usb-integrated-*`/`target/usb-integrated-check`, **không phải
GitHub download links**. Source/report/receipt/runbook public; không publish raw CIM
objects, private identifiers/token/password hoặc artifact lớn. Không sửa raw để tạo
kết quả, không reset DB dev, đọc `.env`, xóa shared volume hoặc kill process theo tên.

Mọi completed/cancelled run sau fix: ownedSchemas0/PGstop0/pid absent, source child0/
readers terminated/scan+control+delivery worker stopped. App/schema/server/socket/
lease/context đã teardown; password không file plaintext/command line. Old worktrees/
branches/raw được giữ. CI exact publication HEAD/final review/PR/merge/tree sẽ ghi
trong PR; no self-approval/bypass/force-push/delete branch.

## Bàn giao và giới hạn

Final source review trước publication: kiểm start/stop/generation/lane guards,
projection/error mapping, ownership, script DB/output guards, test oracle và packaging.
Không tìm thấy lỗi chặn đã xác minh trong phạm vi TEST này. Diff không đụng server
production, protocol source, provider source, POM, workflow hoặc backlog. Rà 18 file
trong diff gồm docs/evidence không phát hiện credential/private path/binary ngoài phạm vi;
125 artifact local khớp hash/size receipt. Historic prefix NHAT_KY/QUYET_DINH/VAI_C được
giữ nguyên sau canonical CRLF/LF. Đây là review kỹ thuật của Codex, không human A/B/C
approval. Exact publication HEAD và CI được kiểm tiếp trong PR trước merge.

B dùng existing history API cho status/USB/PARTIAL, không UI/push trong task này.
Run ID phải trùng collector lease ID confirmed, một context/sequence/queue; coordinator
mượn lane, owner phải close lane khi kết thúc phiên và xem pending/drop/unreported.
Coordinator hiện sở hữu status emitter lifetime claim; ghép status focus/window sau
này cần host ownership chung. Cung cấp Source binding được review khi tích hợp production,
không copy provider hoặc đưa test bridge vào app bằng default flag.

Trước production cần A auth/schema/lifecycle review, B packaging/disclosure/owner,
C provider/cadence/weak identity/H01–12, ADR-V2-05/policy/gate. **Không USB thật, chưa
hotplug/UASP/multi-LUN/phone/installer/LAN/production exam capture acceptance.** Giữ
USB physical0/12 NOT_RUN, FULLdefault/experimentOFF, MON02/MON07 chưa DONE. Dừng sau slice.
