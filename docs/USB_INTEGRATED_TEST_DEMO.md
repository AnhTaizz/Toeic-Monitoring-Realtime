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

## Chạy một lệnh

Tại repo root sau checkout/build source mới, cần Windows PowerShell5.1 được phép chạy
script local (không tự đổi policy), Java21/JAVA_HOME, Maven3.9 trên PATH và PostgreSQL18
tools tại `C:\Program Files\PostgreSQL\18\bin` (có thể đổi `-PostgresBin`). Không cần
DB/server dev đang chạy, Docker hoặc USB. Script build test classpath bằng Maven,
không root clean, không đọc `.env`, không nhận URL DB/server chung.

```powershell
.\scripts\demo-usb-integrated.ps1 -Mode Real -ReadUsbStorage
```

Đây là opt-in rõ ràng đọc CIM thật. Script thông báo provider/duration/delay/timeout
trước khi đọc. Default MSFT_DISK vì BusType phân loại USB trực tiếp và lượt trước
Win32 InterfaceType/SCSI có thể UNKNOWN; không tự fallback hoặc chốt ADR. Chỉ SELECT
Number/BusType (hoặc Index/InterfaceType nếu chọn WIN32_DISKDRIVE); không serial/path/
volume/file/device writes. Disk slots chỉ RAM/pipe nội bộ, không wire/history/log.

Ví dụ đổi cấu hình (các đường dẫn có thể chứa khoảng trắng, không shell metachar):

```powershell
.\scripts\demo-usb-integrated.ps1 -Mode Real -ReadUsbStorage -Provider MSFT_DISK -DurationSeconds 15 -CadenceMillis 1000 -QueryTimeoutMillis 5000 -PostgresBin 'C:\Program Files\PostgreSQL\18\bin'
# Chỉ input MOCK, không CIM read; cùng coordinator/auth/API/WS/PG thật:
.\scripts\demo-usb-integrated.ps1 -Mode Mock
```

`-JavaHome`, `-MavenCommand`, `-MavenRepository` hỗ trợ tool/cache khác. Output tự tạo
unique `target/usb-integrated-usb-...`; nếu đặt `-OutputDirectory` thì phải là folder
**chưa tồn tại dưới checkout target**. Mode Real thiếu -ReadUsbStorage hoặc Mode Mock
có flag đó bị reject; không đọc provider/DB. Output đã tồn tại bị reject trước setup.

DB là cluster mới do run sở hữu, random port30000–38999/DBname nonce/bind127.0.0.1.
Password bootstrap qua stdin trong single-user mode **offline, chưa có socket**, rồi
đổi HBA sang SCRAM trước pg_ctl start. Không plaintext password file/CLI/URL. User/app
scope do seeder/API, không SQL INSERT application user/attempt/assignment.

## Đọc kết quả và dừng

Script tự stop sau duration, rồi đóng resources và PostgreSQL TEST, giữ output:

- `demo-summary.json`: source SHA, REAL_CIM hoặc MOCK, actual readiness/count/query,
  committed/ACK/drop/pending và source/readers/workers cleanup. READY/count0 phải có
  READY status/COMPLETE được ACK/readback, không USB event. UNKNOWN/ERROR/UNSUPPORTED
  là availability nguồn, không chứng minh không thiết bị; count local null.
- `history-*.private.json`: HTTP paged history thực đọc lại, canonical equality với
  frozen messages, fixed-through; không token/password/CIM object. Local ignored,
  không fake GitHub download link. DB/schema đã teardown nên không URL sống sau run.
- `result.json`: run/mode/source/start/end/exit/ownedSchemasRemaining/clusterStopExit/
  postmasterPidAbsent. `reports.private/*.xml`, raw logs/pgdata/JAR giữ local ignored.
  `VERIFIED` không physical hotplug; bất kỳ FAIL/thiếu summary/cleanup không sạch
  giữ INCOMPLETE, không rerun tới PASS rồi xóa lỗi.

Dừng sớm từ terminal thứ hai bằng output path đã in:

```powershell
Set-Content -LiteralPath '<output-da-in>\stop.request' -Value STOP
```

REAL runner kiểm stop file100ms, invalidate generation, close source/queue/context/
lease/socket và teardown TEST. Metadata exit130/outcome CANCELLED/pipelineVerified=false;
PowerShell trả nonzero (hiện1 do exception incomplete), không tính là completed pipeline
PASS. Đã kiểm file-stop sau một quan sát thật; **không tuyên bố đã kiểm phím Ctrl+C/
đóng cứng console**. Wrapper finally cũng phát stop file nếu bị hủy, nhưng nếu Maven
chưa thoát trong15s thì báo mavenStopped=false và cần giữ INCOMPLETE, không broad kill.

## Actual đã kiểm 20–21/10/2026

Runtime coordinator `fdc90a1`, build/test/demo cuối `e7cd9dc`:
baseline194PASS; unit204PASS;4MOCK network cases PASS (9timeline events/status, actual
WS authorization rejection sau admin revoke fixture, REAL business deadline và
SIMULATED deferred COMMIT rollback/retry). REAL MSFT15s có **10scan READY/count0 STANDARD**,
server COMMIT/ACK/history đúng **1 OBSERVER_STATUS READY/INITIAL COMPLETE**,0USB events,
pending/drop/unreported0. Children0/readers terminated/scan+control+delivery workers
stopped/0schema/PGstop0. REAL stop-file case1scan CANCELLED, cleanup sạch, không completed
pipeline PASS. Fullverify1220PASS/149SKIP,15JAR checks; CLI classpath --help exit0 không scan.
Xem [report/receipt](../evidence/c-usb-integrated-20261021/REPORT.md) cho source/hashes/
lỗi setup đã giữ; không cộng repeated runs hay gọi count0/CI là hotplug.

## Bàn giao B

B có thể đọc status/USB từ HTTP history đã có, phân biệt availability/USB PARTIAL/
queue gap/presence; không UI/push trong task này. Test source bridge nằm server test
classpath (đã có test-scope dependencies), client/server runtime JAR không chứa CIM.
Coordinator là provider-agnostic ở client, không auto start từ CandidateExamSignalMonitoring.
Trước production cần chọn composition/packaging được review, disclosure/quyền, provider/
cadence/weak identity và USB thật H01–12. Theo RealtimeClient hiện tại, run.observerSessionId
phải trùng collector ID của lease đã confirm; không tạo context/run thứ hai cho USB.
Context/sequence/delivery dùng chung mọi emitter; coordinator hiện sở hữu một status
emitter (claim lifetime), nên host chung cần điều phối quyền sở hữu status trước khi
ghép status của focus/window. Stop coordinator không đóng lane mượn; owner phiên phải
close delivery/context/lease/realtime, kiểm discarded/unreported, không flush sang run mới.
