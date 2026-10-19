# MON-02 — Probe USB storage độc lập trên Windows

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

| Case | Thao tác/expected | Trạng thái ban đầu |
|---|---|---|
| H01 | Không gắn USB storage, snapshot thường READY0 hoặc UNKNOWN nếu provider thiếu; operator xác nhận topology | NOT_RUN |
| H02 | Cắm sẵn USB rồi start → baseline PRESENT_AT_START; ref random | NOT_RUN |
| H03 | Baseline ổn định rồi cắm mới → CONNECTED một lần khi scan thấy | NOT_RUN |
| H04 | Rút sau khi đã quan sát → REMOVED cùng ref; không lặp ở poll tiếp | NOT_RUN |
| H05 | Cắm/rút nhanh nhiều lần, ghi cả lần polling bỏ sót; không biến NOT_OBSERVED thành PASS | NOT_RUN |
| H06 | Một thao tác/multiple notifications: polling không lặp set; native notifications/debounce chưa triển khai | NOT_RUN |
| H07 | Hai USB storage, cắm/rút riêng đúng ref/count; nếu thiếu thiết bị giữ NOT_RUN | NOT_RUN |
| H08 | USB mouse/keyboard không làm storage count/event tăng | NOT_RUN |
| H09 | Storage không drive letter/UASP/bridge/missing metadata; UNKNOWN khi chưa phân loại đủ | NOT_RUN |
| H10 | Restart với USB còn cắm → baseline mới/ref mới, không dùng ID run cũ | NOT_RUN |
| H11 | Ctrl+C giữa query/watch, FINISH và cleanup children0/readers true; không ảnh hưởng PowerShell khác | NOT_RUN |
| H12 | Provider/permission failure không REMOVED giả, không yêu cầu admin để giấu lỗi | NOT_RUN |

Raw/output thật đặt thư mục riêng ignored, không dump CIM object. Nếu có physical evidence khó tái tạo, người giữ máy lưu ngoài build, ví dụ thư mục archive riêng được phép, kiểm SHA256 từng file/copy và ghi receipt metadata; không chỉ giữ trong target. Lượt chưa có thao tác USB thật không tạo physical archive giả. Kiểm code/tests/runtime thực tế và source pin xem [report](../evidence/v2-c-mon02/REPORT.md).

## Trước integration

C/B review API Windows, classification/identity/polling loss/no-admin/cleanup trên phần cứng và runtime dự kiến; ADR-V2-05 còn PENDING. A review quyền/disclosure/retention và boundary session trước nối data. C/A/B chốt BASE-04/ADR-V2-04/MON-07 type/queue/dedup/ACK/consumer riêng, không lấy local JSONL làm wire đã duyệt. MON-01/BASE-02 physical/review còn pending. Probe không tích hợp WS/PG/dashboard, không MON-08, không E1/E2; MON-02 chưa DONE, không đổi backlog/gate hoặc merge PR70–72.
