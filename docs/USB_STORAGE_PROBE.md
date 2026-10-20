# MON-02 — Probe USB storage độc lập trên Windows

## Luồng tích hợp TEST một lệnh — 21/10/2026

[USB integrated demo](USB_INTEGRATED_TEST_DEMO.md) đã nối provider CIM hiện có → snapshot
→ coordinator/USB adapter/status emitter → shared lane → server/PG TEST → COMMIT/ACK/
HTTP history. Chạy `.\scripts\demo-usb-integrated.ps1 -Mode Real -ReadUsbStorage` ở root;
không `.env`/DB dev/observer production. Actual MSFT15s STANDARD:10scan READY0,1READY
status committed/ACK/readback,0USBevent, cleanup0children/readers+workers stopped/0schema/
PGstop0. Không hotplug, physical0/12NOT_RUN, ADR/gates/ownerpending. Bridge TEST-only,
probe CLI và các runbook độc lập dưới đây giữ nguyên; “NOT INTEGRATED” bên dưới là
phạm vi lịch sử spike/kit, không mô tả lượt integrated TEST mới.

## Adapter MOCK → MON-07, 20/10/2026

[Adapter experiment](MON02_USB_ADAPTER_EXPERIMENT.md) chuyển snapshot đã phân loại sang
UsbStorageSignal qua queue/socket chung. Bridge dùng model probe **chỉ test scope**;
client không phụ thuộc monitoring-spike, CLI/CIM/resource cũ không bị copy hoặc nối vào
candidate production. Baseline một event tổng; READY rỗng không phát PRESENT count0.
Lỗi/UNKNOWN không removal, ref chỉ là correlation slot trong run và bị bỏ sau nguồn lỗi;
wire PARTIAL, không khẳng định disk index là identity phần cứng. Xem bảng semantics,
ownership run/sequence/queue và giới hạn index reuse trong tài liệu adapter.

Người dùng chưa có USB: toàn bộ transition input là MOCK, transport/DB TEST được kiểm
riêng. **Physical0/12 NOT_RUN, MON-02 chưa DONE, ADR/policy pending**, không yêu cầu cắm
thiết bị và không lấy readiness READY0/UNKNOWN hoặc CI làm bằng chứng hotplug. Các lệnh
probe/kit và H01–H12 bên dưới vẫn là runbook độc lập cho lượt phần cứng sau này.

## Kit main tích hợp — 20/10/2026

Ưu tiên mục này cho lượt mới; phần base/PR cũ bên dưới là lịch sử. Main đã tích hợp
PR70–74 tại `a6fba31178a1694f42df4a682d4a0ed205d77e45`. Không build nhánh spike cũ.
JDK21/Maven3.9/Windows PowerShell5.1 + CimCmdlets, token thường; không cần server/DB,
installer hoặc quyền admin. [Receipt lượt kit](../evidence/c-integrated-kit-20261020/REPORT.md).

Từ checkout sạch đúng SHA main đã chọn, PowerShell thường, chạy build **trước** khi tạo raw.
Build hiện có chạy `clean verify` chỉ module monitoring-spike: lưu evidence ở root `target`,
không để raw cần giữ trong `monitoring-spike/target`. Không chạy root clean.

```powershell
git status --short
git rev-parse HEAD
java -version
mvn -version
.\scripts\build-usb-probe.ps1 # thêm -MavenRepository nếu cache mặc định không ghi được
$receipt=Get-Content monitoring-spike/target/usb-probe-build.json -Raw | ConvertFrom-Json
$receipt.sourceSha
Get-FileHash monitoring-spike/target/monitoring-spike-0.1.0-SNAPSHOT.jar -Algorithm SHA256
$raw=Join-Path (Get-Location) ('target/usb-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $raw -ErrorAction Stop | Out-Null
# Mỗi lệnh trong terminal riêng: wrapper exit bằng mã Java; giữ tên file mới.
.\scripts\probe-usb-storage.ps1 -Method msft-disk -OutputFile "$raw/snapshot.jsonl" -MachineAlias WIN-A
.\scripts\probe-usb-storage.ps1 -Method win32-diskdrive -OutputFile "$raw/comparison.jsonl" -MachineAlias WIN-A
.\scripts\probe-usb-storage.ps1 -DurationSeconds 120 -OutputFile "$raw/watch.jsonl" -MachineAlias WIN-A
```

Nếu policy chặn script, không đổi/bypass policy. CLI trực tiếp ở mục Lệnh chạy dùng
UNVERSIONED cho khám phá; evidence pin chỉ nhận khi kiểm đầy đủ source/runtime receipt.
Receipt và JAR là local ignored, không gửi `.env` hoặc dump CIM. Trước/sau mỗi run ghi SHA,
hash receipt/JAR/JSONL, alias, config, runId, thời điểm thao tác và exit code trong notes local.
START phải đúng SHA receipt. FINISH phải childrenAlive=0/readersTerminated=true; Ctrl+C130
là dừng có chủ đích. Thiếu FINISH/forced kill giữ INCOMPLETE, không suy cleanup PASS.
Không đóng PowerShell/Java khác. Giữ raw bất biến local ignored, chỉ công bố bảng safe scalar
và checksum đã rà dữ liệu riêng tư; archive ngoài build chỉ sau khi có evidence thực cần giữ.

### H01–H12: thao tác để dành khi có phần cứng

**Lượt chuẩn bị này: Actual trống, cả 12 dòng NOT_RUN, 0/12.** Chép
[CSV hiện có](../evidence/v2-c-mon02/physical-pending.csv) vào run mới, thêm actual/evidence
ở bản local, không sửa lịch sử. Giữ ID/tiêu chí gốc. Không yêu cầu thao tác phần cứng lúc này.

Ký hiệu lệnh: **S** là snapshot MSFT ở trên với tên file mới; **W** là watch120s
ở trên với tên file mới; **W2** lặp W ở terminal/run mới; **V** là snapshot Win32.
Mỗi ca ghi caseId/runId/mốc thao tác/expected/actual/exit/FINISH và hash raw; `deviceRef`
chỉ random trong run, không serial/index/model cá nhân. Cleanup **K**: đợi hết120s hoặc
Ctrl+C đúng terminal, kiểm FINISH/owned child0/readers true, dừng process thử thuộc run;
không format disk, đổi partition/letter/ACL/provider hoặc xóa raw để đạt kết quả.

| ID | Thiết bị/điều kiện; lệnh | Thao tác và expected | Evidence; cleanup | PASS / FAIL / INCONCLUSIVE (khi thực chạy) |
|---|---|---|---|---|
| H01 | Máy Windows, người giữ máy xác nhận topology không USB storage; S | Ghi topology trước snapshot; READY/count0 hoặc UNKNOWN có nhãn thiếu classification | Notes topology + snapshot/FINISH; K | PASS nếu READY0 khớp topology; FAIL nếu báo USB sai; UNKNOWN là INCONCLUSIVE về absence, không PASS hardware |
| H02 | Một USB storage cắm sẵn, đủ phân loại; W | Start sau khi cắm; baseline READY có PRESENT_AT_START/ref random, không CONNECTED giả tại baseline | Mốc start + baseline/ref/FINISH; K, rút thiết bị theo chính sách máy sau run | PASS đúng baseline; FAIL sai kind/count hoặc lặp; UNKNOWN/thiếu baseline là INCONCLUSIVE |
| H03 | Một USB storage chưa cắm, baseline READY ổn định; W | Sau baseline cắm, giữ qua ít nhất hai scan thành công; CONNECTED một lần/ref mới | Mốc cắm + trước/sau + poll ổn định; K | PASS đúng transition/count; FAIL sai/lặp trên snapshot READY; thiếu classification/query lỗi là INCONCLUSIVE |
| H04 | USB đã xuất hiện trong W và biết ref | Rút, đợi hai scan READY; REMOVED đúng ref một lần, không lặp poll tiếp | Trước/rút/sau + ref + FINISH; K | PASS đúng reconcile; FAIL REMOVED sai/ref lặp hoặc tạo từ lỗi; thiếu scan READY là INCONCLUSIVE |
| H05 | Một USB storage, W; có người ghi từng lần | Cắm/rút nhanh có mốc riêng, không hứa polling thấy mọi transition; ghi cả lần bỏ sót | Notes từng lần so JSONL; K | PASS chỉ nếu coverage thực đáp ứng tiêu chí quan sát đã ghi; NOT_OBSERVED/thiếu mốc là INCONCLUSIVE, không quảng cáo lossless; observation sai là FAIL |
| H06 | Một USB storage, W, baseline READY | Một lần cắm và giữ qua nhiều poll; set ổn định không event lặp; không claim native debounce | Một mốc thao tác/nhiều scan/ref; K | PASS riêng idempotent polling; FAIL lặp với set không đổi; native notification/debounce vẫn NOT_IMPLEMENTED, thiếu READY là INCONCLUSIVE |
| H07 | Hai USB storage được phép, W | Cắm lần lượt, giữ mỗi bước qua scan rồi rút riêng; count và ref độc lập | Mốc bốn bước/count/ref; K | PASS khớp toàn chuỗi; FAIL lẫn ref/count; thiếu thiết bị giữ NOT_RUN, multi-LUN chưa rõ là INCONCLUSIVE |
| H08 | USB mouse/keyboard và topology không storage mới; W | Cắm/rút thiết bị nhập, không dùng thiết bị đang cần để điều khiển máy; không tăng storage/event | Loại thiết bị chung + mốc + snapshots; K | PASS không storage tăng qua READY; FAIL false storage; UNKNOWN là INCONCLUSIVE |
| H09 | Storage không drive letter hoặc UASP/bridge có sẵn được phép; S rồi V, W nếu cần | Không tự sửa letter/partition; chỉ USB khi đủ bus metadata, thiếu thì UNKNOWN/count null | Loại đường nối chung + status/problem/count, không dump CIM; K | PASS labeling đúng bằng chứng độc lập; FAIL đoán USB/absence; UNKNOWN đúng semantics nhưng hardware coverage INCONCLUSIVE |
| H10 | Một USB còn cắm và baseline thành công ở W; W2 | Dừng run đầu bằng K, start run mới; PRESENT_AT_START/ref mới, không tái dùng run/ref cũ | Hai START/baseline/FINISH và hashes; K cả hai | PASS tách run/ref đúng; FAIL dùng lại identity/sai baseline; UNKNOWN là INCONCLUSIVE |
| H11 | Windows có USB trong watch, PowerShell không liên quan vẫn mở; W | Ctrl+C khi query/watch đang hoạt động; không kill process theo tên | Mốc dừng/FINISH130/child0/readers true, xác nhận terminal khác còn; K | PASS cleanup đầy đủ; FAIL owned child còn/readers không dừng; thiếu FINISH hoặc forced kill là INCONCLUSIVE |
| H12 | USB đã có baseline, chỉ môi trường provider/permission lỗi được owner cấp sẵn; W | Ghi lỗi tự nhiên/được owner bố trí; không đổi ACL/service, không nâng admin; UNKNOWN/ERROR không REMOVED giả | Last READY + status/problem + recovery nếu có; K | PASS không false REMOVED và lỗi rõ; FAIL lỗi biến thành empty/removal; không tái hiện lỗi an toàn giữ NOT_RUN/INCONCLUSIVE. MOCK regression riêng không thay ca này |

H05/H06/H09 tách kết quả phần mềm và coverage thiết bị để tránh tiêu chí mâu thuẫn:
polling idempotent không chứng minh native debounce, UNKNOWN đúng không chứng minh absence,
bỏ sót giữa hai scan phải giữ giới hạn công khai. ADR-V2-05 và MON-02 chưa DONE.

20/10/2026 — Codex technical review: source aa260ef kiểm35tests/0SKIP và JAR/provider snapshots trên Windows STANDARD; MSFT READY0, Win32 UNKNOWN, child0/readers terminated. Hòa main chỉ docs/test ngoài probe, runtime probe giữ nguyên. Có thể merge như spike độc lập; physical0/12 NOT_RUN, ADR-V2-05/owner policy chưa approved. Budget classification10s chỉ thuộc TEST, CLI5s/UNKNOWN/no-false-REMOVED/finite output giữ nguyên; không khẳng định root cause CI cũ từ rerun PASS.

**SPIKE ONLY — NOT INTEGRATED — ADR-V2-05 OWNER REVIEW PENDING.**

Base `main@1d35b53729abade7b460d35ab07d042b6f9f3571`; nhánh riêng `spike/v2-c-mon02-usb-storage`. Chưa có probe/PR MON-02 khác khi search GitHub/ref/source đầu phiên. PR70/71 còn mở, PR72 Draft head `da89962e71c169d2ffbd0e9dfdd1ce460d00fb01`, chưa merge. Đã đọc [MON-07 proposal đúng SHA](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/blob/da89962e71c169d2ffbd0e9dfdd1ce460d00fb01/docs/MONITORING_MULTISIGNAL_CONTRACT_PROPOSAL.md); không kế thừa/approve schema đó trong probe này.

Đối chiếu [CLAUDE](../CLAUDE.md), AGENTS người dùng cung cấp (không có AGENTS khác trong source checkout), [entrypoint](../PLAN_V2_START_HERE.md), [BASE-04/MON-02/MON-07/QA-04](plan-v2/03_TASK_CARDS.md), [architecture](plan-v2/02_ARCHITECTURE_CONTRACT.md), [ownership](plan-v2/04_EXECUTION_OWNERSHIP.md), [ADR](plan-v2/07_RISKS_DECISIONS.md), [tiến độ](TIEN_DO.md), [nhật ký](NHAT_KY.md), [protocol](PROTOCOL.md), [QD](QUYET_DINH.md). V2 cho phép scope USB observation có thông báo, không phải approval API/hardware/retention/integration. Giữ FULL default và gate hiện tại.

## Lệnh chạy

Mở PowerShell **thường**, Java21/Maven đã có trên PATH, đứng tại repo root của nhánh probe. Không Run as Administrator để che AccessDenied, không sửa execution policy/registry. Nếu policy cơ quan không cho chạy script, ghi blocked và dùng Java CLI sau build được phép; không tự bypass policy.

```powershell
# Source đã commit sạch; chỉ module spike, tạo receipt local ignored sau test/package thành công.
.\scripts\build-usb-probe.ps1

# Snapshot ban đầu (duration=0), không cần thao tác USB.
.\scripts\probe-usb-storage.ps1 -Method msft-disk -MachineAlias WIN-A

# Theo dõi: khi thấy SNAPSHOT/READY mới bắt đầu ghi mốc thao tác và cắm/rút.
.\scripts\probe-usb-storage.ps1 -Method msft-disk -DurationSeconds 120 -IntervalMillis 1000 -MachineAlias WIN-A

# Provider phụ để đối chiếu, không tự gộp count giữa hai provider.
.\scripts\probe-usb-storage.ps1 -Method win32-diskdrive -DurationSeconds 30 -MachineAlias WIN-A

# Ghi trực tiếp vào file MỚI, không qua Tee/pipeline (tránh mất FINISH khi Ctrl+C).
.\scripts\probe-usb-storage.ps1 -DurationSeconds 120 -OutputFile target/usb-physical-NEW.jsonl -MachineAlias WIN-A
# Terminal khác: Get-Content target/usb-physical-NEW.jsonl -Wait

# Dừng sớm: Ctrl+C trong terminal. Kiểm FINISH/cleanup và không còn process do probe sở hữu.
```

Có thể redirect JSONL vào file mới trong thư mục ignored, không ghi đè evidence cũ. Từ PowerShell wrapper exit bằng mã Java: 0 hoàn thành, 2 UNSUPPORTED, 3 ERROR/cleanup lỗi, 4 UNKNOWN classification, 5 output cap, 64 arguments sai. Trong watch, lỗi đã gặp giữ mã severity cao nhất dù có recovery; order ERROR > UNSUPPORTED > UNKNOWN > READY. Ctrl+C thường cho process exit130; FINISH exitCode130 là yêu cầu dừng, không phải test hardware fail. OS kill cưỡng bức/terminal đóng có thể không hoàn tất shutdown hook; không bảo đảm cleanup/evidence COMPLETE cho trường hợp ấy.

Java CLI trực tiếp (hữu ích khi policy không cho `.ps1`, không sửa policy):

```powershell
java -cp monitoring-spike/target/classes vn.edu.toeic.monitoring.usb.UsbStorageProbe --help
java -cp monitoring-spike/target/classes vn.edu.toeic.monitoring.usb.UsbStorageProbe --method msft-disk --duration-seconds 30 --interval-millis 1000 --query-timeout-millis 5000 --max-lines 1000 --machine-alias WIN-A --source-sha UNVERSIONED
```

CLI trực tiếp không tự xác minh source SHA; dùng UNVERSIONED cho khám phá, không dùng làm evidence đã pin. Wrapper kiểm receipt hash và source sạch rồi dùng SHA đã build. JAR spike chạy được bằng `java -cp monitoring-spike/target/monitoring-spike-0.1.0-SNAPSHOT.jar <main-class-tren> ...`; không có nghĩa installer production đã chứa/support probe. Không đổi Maven exec entry point ProcessHandle cũ.

## Luồng chính và phân loại

1. Java tạo run ID và đọc cấu hình hữu hạn, spawn đúng một PowerShell con từ SystemRoot, không profile/interactive. Lệnh resource cố định, không nhận WQL/command tự do từ CLI.
2. **MSFT_DISK (đã code):** WQL chỉ `SELECT Number,BusType FROM MSFT_Disk`, namespace `root/Microsoft/Windows/Storage`. BusType=7 là Windows báo disk dùng USB bus; không dựa drive letter, chữ Removable, tên hoặc generic USB notification. Mouse/keyboard không thuộc disk class này. Đếm là **số disk storage Windows báo**, không cam kết một disk=một hộp USB vật lý (multi-LUN/bridge/card reader cần hardware test).
3. BusType 0/null/1(SCSI) hoặc enum không biết → UNKNOWN vì thiếu bằng chứng USB transport. Các bus enum đã biết khác (2–17, trừ7) là NON_USB trong phạm vi report provider; không tuyên bố phát hiện mọi bridge/virtualization. UASP/bridge phải kiểm thật, không suy từ tên. Fallback `WIN32_DISKDRIVE`: chỉ SELECT Index,InterfaceType; USB được phân loại khi disk-class InterfaceType=USB; SCSI/null/không biết → UNKNOWN, IDE/HDC/1394 là NON_USB. Không tự fallback rồi diễn giải count thấp hơn như quan sát đầy đủ.
4. Pipe nội bộ chỉ chứa disk number/index transient, class, status và runtime. Không select SerialNumber, PNPDeviceID, UniqueId, FriendlyName, label/path/drive contents. Chỉ disk index cần thiết cho diff được giữ RAM, không đưa index hoặc hash index ra JSONL. Provider có thể trả key metadata nội bộ trong CIM object; chương trình không truy cập/dump/persist chúng. Ref output là random UUID/run, mất khi stop; không bền qua restart.
5. Java chỉ chấp nhận snapshot đầy đủ có META và END/count đúng, ≤128 disks, unique valid uint32 index. Parse/timeout/stderr/provider error không biến thành empty scan. Với UNKNOWN/ERROR/UNSUPPORTED, `storageCount:null`; `observedStorageCount` chỉ lower bound disk USB phân loại được, không chứng minh không có USB. Không reconcile removals trên snapshot không đầy đủ.
6. Snapshot READY đầu tiên tạo PRESENT_AT_START cho disk USB quan sát được; tên này nghĩa **baseline thành công đầu tiên**, không khẳng định thiết bị đã có tại thời điểm process khởi động nếu scan đầu lỗi. Sau đó diff thành CONNECTED/REMOVED. Snapshot giống nhau không phát lặp, đây là idempotent reconciliation, **không phải** đã test native notification debounce. Các thay đổi thật thấy ở hai scan kế tiếp vẫn giữ, không time-debounce mất transition.
7. Disk number có thể tái dùng; không có serial/identity bền nên thay thiết bị cùng number giữa hai scan có thể không phân biệt. Polling có thể bỏ sót toàn bộ lần cắm/rút ngắn giữa hai scan. Scan lỗi tạo khoảng không biết; recovery so last successful snapshot chỉ cho biết chênh lệch quan sát, không phục dựng mọi thao tác. READY/count0 nghĩa provider báo không có disk USB trong snapshot ấy, không chứng minh không có thiết bị/hành vi.

## Giới hạn và tài nguyên

- Duration 0(snapshot) hoặc1–600s; interval250–60000ms; query timeout100–10000ms (default5000); maxLines4–2000 (default1000). Hai scan không overlap; interval là khoảng chờ **sau** query, không phải cam kết quét mỗi đúng1s.
- `queryMillis` là elapsed monotonic ở Java, gồm khởi động PowerShell/CIM/đọc pipe; không là latency từ OS phát hiện USB. `localAt` UTC wall clock local lúc emit, không timestamp gốc của PnP/thao tác. Ghi mốc thao tác thủ công riêng nếu muốn đối chiếu; không trừ clock hai máy.
- WQL dùng OperationTimeoutSec5, Java có timeout độc lập cả process; hai pipe reader daemon drain đồng thời, mỗi pipe≤16KiB. Chỉ fixed code lỗi, không echo exception/stderr. Child bị treo/output quá lớn đều cleanup; output JSONL≤maxLines kể cả một FINISH reserved.
- Không giữ native registration/subscription (NOT_USED); không query trên JavaFX, không observer trong ứng dụng thi. Normal exit/Ctrl+C dừng tracker trước, invalidate generation, kill đúng Process handle đã spawn, đóng streams và chờ readers/child hữu hạn; callback run cũ bị loại. Không process-name kill hoặc stop WMI service dùng chung. Không có descendant chủ đích; cleanup descendant nếu còn nhận diện dưới child là best effort Java ProcessHandle, chưa là Windows Job Object guarantee khi parent bị OS kill/reparent cực nhanh. C/B cần xem giới hạn này trước integration.
- FINISH có queryChildrenStarted/queryChildrenAlive/pipeReadersTerminated, nativeRegistration NOT_USED. Chỉ gọi cleanup đạt khi owned children0/readers terminated; forced JVM kill/thiếu FINISH giữ INCOMPLETE. Thời gian total có thể vượt duration bởi query đang chạy (tối đa timeout) và teardown hữu hạn; không hứa hard realtime.
- Build receipt local chứa SHA + hash source/runtime và wrapper reject stale/tampered file inventory. Không thêm dependency Java/native mới; deployment cần PowerShell/CimCmdlets/ACL provider trên host. Chưa kiểm runtime jpackage/installer hoặc Windows architecture khác ngoài máy đã chạy.

Riêng test MOCK `CimClassificationScriptTest` có process budget10s để tách mục tiêu classification khỏi độ chậm startup PowerShell; CLI default5s/resource OperationTimeoutSec5 không đổi. Test child treo150ms vẫn kiểm timeout/cleanup. Xem [hồ sơ sửa CI73](../evidence/v2-c-mon02/REPORT.md#19102026--sửa-ci-pr73-giữ-physical-pending); log cũ thiếu problem nên không khẳng định root cause chỉ từ5.102s.

## So sánh API chính thức

| Phương án | API và tình trạng | Điều cần thêm/giới hạn |
|---|---|---|
| Windows PnP/device notification | **NOT_IMPLEMENTED**, chỉ nghiên cứu RegisterDeviceNotificationW/WM_DEVICECHANGE/UnregisterDeviceNotification | Cần HWND/message loop hoặc service recipient + register/unregister; notification thay đổi không tự cung cấp disk/bus classification; vẫn cần baseline/enumeration. Có duplicate và nhiều interface cho một thao tác; không copy sample mở handle đọc/ghi thiết bị. Java21 cần bridge native được duyệt và kiểm packaging/cleanup trước chọn |
| MSFT_Disk CIM snapshot polling | **IMPLEMENTED**, provider ưu tiên để thử | BusType enum rõ, disk-number RAM-only, không drive letter. Có chi phí spawn/CIM, polling bỏ sót transient; SCSI/unknown conservatively UNKNOWN; hardware coverage chưa được giả PASS |
| Win32_DiskDrive CIM snapshot polling | **IMPLEMENTED**, provider phụ/đối chiếu | InterfaceType USB có bằng chứng disk-class, nhưng SCSI có thể không kết luận USB; không chọn bằng tên, không đoán absence; không sửa policy để chạy |
| WMI/CIM event subscription | **NOT_IMPLEMENTED** | Không có subscription để claim đã unsubscribe/test notification latency; polling đang chạy không được gọi event-driven |

Định hướng tạm thời: thử MSFT_Disk polling trước vì property bus cụ thể và Java không cần native dependency; Win32 chỉ fallback hạn chế khi có kết quả đủ phân loại, nếu không giữ UNKNOWN. Chưa đủ hardware evidence để APPROVE ADR-V2-05 hoặc chứng minh PnP kém hơn. Khi polling không bắt được required quick transitions, cần spike PnP + storage-class resolution riêng, không quảng cáo polling là lossless.

Nguồn đã đọc, truy cập19/10/2026:

- [Microsoft MSFT_Disk](https://learn.microsoft.com/en-us/windows-hardware/drivers/storage/msft-disk): scope disk, BusType USB7, disk Number có thể đổi sau restart.
- [Microsoft Win32_DiskDrive](https://learn.microsoft.com/en-us/windows/win32/cimwin32prov/win32-diskdrive): disk class, Index/InterfaceType enum; không dùng name/media label làm bằng chứng.
- [Microsoft Get-CimInstance](https://learn.microsoft.com/en-us/powershell/module/cimcmdlets/get-ciminstance): local CIM snapshots, query/property và OperationTimeoutSec. Probe gọi local, không ComputerName/remote credentials.
- [Microsoft RegisterDeviceNotificationW](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-registerdevicenotificationw), [registration example](https://learn.microsoft.com/en-us/windows/win32/devio/registering-for-device-notification), [UnregisterDeviceNotification](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-unregisterdevicenotification): chỉ khảo sát, không API thực thi ở probe hiện tại.
- [JDK21 Process](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Process.html), [ProcessHandle](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ProcessHandle.html): ProcessBuilder child, pipe/wait/terminate và scoped descendants; không global process enumeration để kill.

## Physical checklist — chưa có thao tác xác nhận thì NOT_RUN

Khi chạy physical: ghi source SHA/build receipt, run ID, Windows/runtime/token privilege, alias máy và loại thiết bị (flash drive/external disk, USB mouse/keyboard; không serial/model cá nhân). Ghi local UTC trước/sau thao tác trong notes riêng và đối chiếu JSONL; không sửa raw. Chỉ xuất summary đã lọc.

Dùng bảng H01–H12 mở rộng ở mục Kit main tích hợp đầu tài liệu: giữ nguyên ID/tiêu chí gốc, bổ sung lệnh, prerequisites, evidence/cleanup và điều kiện đánh giá; không duy trì bảng thao tác trùng lặp.

Raw/output thật đặt thư mục riêng ignored, không dump CIM object. Nếu có physical evidence khó tái tạo, người giữ máy lưu ngoài build, ví dụ thư mục archive riêng được phép, kiểm SHA256 từng file/copy và ghi receipt metadata; không chỉ giữ trong target. Lượt chưa có thao tác USB thật không tạo physical archive giả. Kiểm code/tests/runtime thực tế và source pin xem [report](../evidence/v2-c-mon02/REPORT.md).

## Trước integration

C/B review API Windows, classification/identity/polling loss/no-admin/cleanup trên phần cứng và runtime dự kiến; ADR-V2-05 còn PENDING. A review quyền/disclosure/retention và boundary session trước nối data. C/A/B chốt BASE-04/ADR-V2-04/MON-07 type/queue/dedup/ACK/consumer riêng, không lấy local JSONL làm wire đã duyệt. MON-01/BASE-02 physical/review còn pending. Probe không tích hợp WS/PG/dashboard, không MON-08, không E1/E2; MON-02 chưa DONE, không đổi backlog/gate hoặc merge PR70–72.
