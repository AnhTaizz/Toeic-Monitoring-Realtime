# MON-02 — Kết quả probe USB storage

**SPIKE_IMPLEMENTED — TECHNICALLY_CHECKED — PHYSICAL_REVIEW_PENDING.** 19/10/2026; không MON-02 DONE, chưa approve ADR-V2-05.

Base `main@1d35b53729abade7b460d35ab07d042b6f9f3571`; branch `spike/v2-c-mon02-usb-storage`. Source kiểm cuối **`30166b3d50218a570ca523bde452a15a58dfcf0e`**, sau source đầu `276c4fae7d30ea9fd0e3036a95e16e66019c4930`. Commit hồ sơ sau source chỉ thêm docs/evidence. PR70/71/72 không merge hoặc chỉnh; proposal MON-07 đọc từ PR72 `da89962e71c169d2ffbd0e9dfdd1ce460d00fb01`, không coi đã duyệt.

## Đã làm thật

- Thêm standalone Java21 CLI trong `monitoring-spike`, không đổi entry point ProcessHandle hiện có. Hai provider **đã code/chạy**: MSFT_Disk Number/BusType và Win32_DiskDrive Index/InterfaceType, qua PowerShell/CIM local có timeout. Không dependency Java/native mới, không service/subscription, không WS/PG/dashboard.
- Có snapshot/watch, count/status, PRESENT_AT_START/CONNECTED/REMOVED theo diff, ref random/run, unknown/error không xóa baseline, generation chống callback cũ. CLI có duration/query/output cap, exit codes, stop/cleanup và file sink CREATE_NEW không ghi đè evidence.
- Build wrapper chạy `clean verify` **riêng module spike**, phát receipt gắn SHA + hash source/class/resource; run wrapper từ chối stale/tampered source/runtime. JAR spike chạy thật trên Java21/Windows, chưa kiểm hoặc thay bộ cài production.
- [Runbook/lệnh/physical checklist/API sources](../../docs/USB_STORAGE_PROBE.md), [module README](../../monitoring-spike/README.md), [unit cases](unit-tests.json), [runtime có run ID/source/time](runtime-summary.json), [artifact metadata](build-receipt.json).

## Kết quả có provenance

Host alias `WIN-C-PROBE`, Windows11/OS version10.0/amd64, Java21.0.10, PowerShell5.1.26100.9711. Child tự kiểm token Windows trả **STANDARD**, không elevated. Điều này chứng minh truy vấn hoạt động với token không Admin trên host này; không thu username/group membership và không tuyên bố mọi ACL/provider/máy Windows đều cho phép.

| Kiểm source30166b3 | Actual | Diễn giải |
|---|---|---|
| Isolated `monitoring-spike clean verify` | **35 PASS, 0 FAIL/ERROR/SKIP**, JAR SUCCESS | 34 test USB mới +1 ProcessObservation test cũ, không phải full reactor |
| Test thiết bị/diff/classification | MOCK | Bao gồm snapshot baseline, repeated refresh/dedup, hai storage, transition nhanh giữa các snapshot, error không removal giả, missing metadata, UNKNOWN, stop/restart/old callback |
| Timeout/overlap/output/close tests | Process/pipe thật, payload MOCK | PowerShell resource thật với6 MOCK-CIM scenarios; Java child thật kiểm timeout/flood/stderr/close giữa query/readers; không hardware PASS |
| MSFT snapshot | READY, observed storage0, exit0; run `a43a54c7-0c76-45c2-a2ce-b4a3f46ed1ed` | Provider báo không có disk USB ở lần đọc; chưa có operator xác nhận topology vật lý |
| MSFT watch6s | 5 snapshot READY/count0, exit0; run `c4e0563f-00b5-4255-8a05-992609f27da6` | Không thao tác cắm/rút đã xác nhận; không event USB, không giả hardware negative PASS |
| Win32 snapshot | UNKNOWN, storageCount=null, observedStorageCount0, exit4; run `5e5d3773-e87a-4481-b06b-77fe0cf90ce0` | Disk được provider báo SCSI, không đủ bằng chứng phân loại USB; không thay bằng READY0 |
| Query timeout100ms | QUERY_TIMEOUT/ERROR, exit3; run `3675ac38-0b0a-42f5-87da-51b042001f48` | Fault cấu hình chủ đích, không lỗi API USB đã chứng minh; child được dọn |
| maxLines4 | 4 records gồm FINISH/outputLimited=true, exit5; run `fb658c90-bad9-4c03-b9b8-30590205e421` | Không output vô hạn, có mã khác completed |
| JAR snapshot | READY/count0, exit0 | Java21 `-cp` JAR spike, resource có trong artifact; không suy production packaging PASS |
| Ctrl+C direct Java file sink | FINISH exit130, 20 query child đã tạo, còn sống0/readers terminated; run `1c996b98-a6c5-4f63-b684-df58e44874aa` | Ctrl+C thật trong terminal riêng, token STANDARD; shell tool trả1 khi interrupt, không gán shell1 thành Java business error |
| Existing output file | exit3, SHA256 file cũ giữ nguyên | CREATE_NEW reject, không overwrite raw |
| Tampered receipt runtime hash | Reject trước launch; receipt gốc khôi phục byte-identical | Tamper là TEST trên metadata build local, không sửa raw log |
| Invalid CLI duration601 | exit64, fixed sanitized message | Không echo argument/private path |

Mọi **7 run cuối có FINISH** đều childrenAlive0/readers terminated; nativeRegistration NOT_USED. QueryMillis trong summary là thời lượng parent gồm startup/CIM/pipe, không phải OS-event latency/benchmark. Không đo USB arrival latency/CPU hoặc E1/E2. Metadata [35 test](unit-tests.json) được sinh từ5 JUnit XML, không công bố system properties/environment riêng tư của XML.

## Kết quả âm và giới hạn giữ nguyên

- Lần Maven sandbox đầu fail **trước test** vì cache trỏ tới C:\\.m2 không ghi được; chạy lại với cache người dùng đã có, không sửa Maven/Windows config toàn máy. Ngữ cảnh sandbox cũng chặn chạy `.ps1`; script được chạy trong ngữ cảnh người dùng cho phép với token STANDARD, không đổi/bypass ExecutionPolicy. Không gọi đây là lỗi USB hoặc dùng elevation Administrator che AccessDenied provider.
- Source276c4fa có34 test PASS; Ctrl+C qua `Tee-Object` sinh file24 record **không FINISH**, run `fc4214c0-d62a-47bd-943f-3376afe5a907`: giữ **INCOMPLETE**, không dựng FINISH hoặc claim cleanup PASS từ file đó. Source30166b3 thêm direct file output và kiểm lại Ctrl+C có FINISH thật. Không sửa/xóa raw cũ hoặc cộng lượt lặp thành số test duy nhất.
- Windows PnP/device notification **NOT_IMPLEMENTED**, chỉ nghiên cứu tài liệu Microsoft về register/window-message/unregister + bước classification cần thêm. WMI/CIM event subscription **NOT_IMPLEMENTED**. Không nói đã đo notification/debounce/native handle cleanup của hai cách chưa code.
- Polling không lossless: hotplug ngắn giữa scan có thể mất; dùng disk number transient nên cùng number được tái dùng giữa scan có thể không phân biệt thay thiết bị. SCSI/bridge/UASP/unknown vẫn cần hardware evidence; count là số disk provider báo, không đảm bảo số hộp USB vật lý. Không dựa drive letter/name/Removable label.
- PowerShell child có timeout/output bound; normal close/timeout/Ctrl+C đã kiểm. Không bảo đảm OS force-kill/reparent qua Java ProcessHandle như Windows Job Object; không có descendant chủ đích/native registration, không kill process khác hoặc WMI service dùng chung. User nên ghi trực tiếp bằng `-OutputFile`, không Tee, và giữ INCOMPLETE khi thiếu FINISH.

## Physical và lựa chọn API

**0/12 physical cases NOT_RUN** trong [checklist](physical-pending.csv). Đã hỏi khả năng cắm/rút nhưng chưa có xác nhận thao tác/thiết bị trong phiên; không kết luận người dùng chắc chắn không có USB. REAL Windows provider query không thay kiểm USB gắn sẵn/cắm/rút/two-storage/mouse/keyboard/UASP/no-drive-letter/restart với hardware. Không tạo USB giả để ghi hardware PASS; không có ảnh/raw thiết bị thật cần archive riêng khó tái tạo trong lượt này.

| Tiêu chí | MSFT_Disk polling — code/chạy | Win32_DiskDrive polling — code/chạy | PnP notification — chỉ khảo sát |
|---|---|---|---|
| Phân loại | Disk class + BusType7; host READY0, UNKNOWN khi bus thiếu/SCSI | Disk class + InterfaceTypeUSB; host SCSI→UNKNOWN | Generic change không đủ; cần enumeration/classify bổ sung |
| Baseline/cắm/rút | Diff/metadata MOCK PASS; real provider hoạt động, hardware transition NOT_RUN | Snapshot thực sự chạy; hardware transition NOT_RUN | NOT_IMPLEMENTED |
| Trùng/bỏ sót | Repeated snapshots không lặp, observed transitions giữ; polling có thể bỏ sót rất nhanh | Giống diff, nhưng coverage classification hẹp hơn | Native duplicates/missed events chưa thử |
| Quyền | STANDARD token trên host này | STANDARD token trên host này, classification chưa đủ | Không khẳng định đã kiểm no-admin |
| Runtime/dependency | JDK21 + Windows PowerShell/CIM đã có, không thêm Java/native dependency | Như trái | Nếu chọn Java bridge phải review dependency/architecture/packaging riêng |
| Cleanup | Normal/timeout/close/Ctrl+C có evidence, không native registration | Snapshot child cleanup đạt; dùng chung bounded source | Unregister/message-loop cleanup chưa code/test |

**Khuyến nghị tạm thời:** chọn **MSFT_Disk CIM polling làm phương án chính để kiểm tiếp trên hardware**, vì bus enum cụ thể hơn provider Win32 trên host này và không thêm native dependency. Win32 chỉ là fallback có giới hạn khi tự phân loại đủ; UNKNOWN phải hiện rõ, không hạ chất lượng âm thầm. Chưa đủ evidence để quyết định PnP hay polling là phương án production cuối; **ADR-V2-05 PENDING**, không human approval.

C/B cần chạy physical checklist trên token thường/runtime dự kiến, đánh giá polling loss/index reuse/UASP/cleanup trước integration. A xem quyền/disclosure/retention/lifetime; C/A/B cần chốt BASE-04/MON-07 contract và dependency MON-01/BASE-02. JSONL này chỉ local schema, không gửi lên server hoặc dashboard, không đổi production protocol/schema/process policy/FULL default/DELTA gate.

## Publication/evidence

[raw-manifest](raw-manifest.json) chỉ có relative name/size/SHA256 của **29 raw/log/XML/receipt file local ignored**; không phải GitHub download links. JAR/compiled runtime cũng local, chỉ hash ở build-receipt. Sanitized summaries không có serial/volume label/path cá nhân/username/token hoặc raw device identifiers; thực tế query chỉ Number/BusType hoặc Index/InterfaceType. Không commit binary/raw XML/logs, không sửa `.env`/DB/migration/backlog/tracker.

Generator summary ở `target/summarize-mon02.ps1` local ignored, chỉ đọc raw và trích allowed fields/case status/checksums; không sửa raw. Public digest nằm [public-sha256.json](public-sha256.json). Kiểm publication thực tế: 24 file đúng allowlist, 19 local link targets tồn tại, 5 JSON parse được; 6 public hash khớp cả bytes staged Git và 29 raw hash giữ nguyên; diff whitespace đạt, 0 known-secret/private-path/raw-binary/large-file finding. Report/runbook/source refs và JSON/diff/privacy được kiểm trước push; CI mới của Draft PR xem GitHub riêng, không thay physical review. Dừng sau probe/Draft PR, không merge hoặc triển khai MON-07/MON-08/E1/E2.


## 19/10/2026 — Sửa CI PR73, giữ physical pending

Các mục trên là evidence lịch sử source30166b3; không thay số liệu cũ. Test source mới **`1e759245f9e9b9b13ac868384e5d0892f104f77e`**, [summary mới/case/hash](ci73-fix-20261019.json).

- **Failure gốc:** [CI39258040529](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39258040529), HEADfa764c9, clean verify FAIL;35module tests/1failure, MSFT classification expected READY/actual ERROR sau5.102s. Log không có problem/query elapsed/cleanup nên **chưa chứng minh nguyên nhân chính xác** là cold startup hoặc process timeout; không loại parse/pipe chỉ bằng thời gian. Runner WindowsServer2025/Temurin21.0.12 khác host local Windows11/Java21.0.10.
- **Đã chứng minh ranh giới budget:** ProcessBuilder.start diễn ra trước waitFor; budget waitFor gồm startup PowerShell + script tới process exit, sau đó mỗi pipe future có thêm1s. queryMillis còn gồm parent launch/đọc pipe, được lấy trước cleanup; thời gian JUnit còn có close/assertion. Baseline diagnostic với5s PASS một lần trong JVM/PS process mới, không fresh VM/cache reset. Thí nghiệm MOCK delay5500ms đúng resource trả QUERY_TIMEOUT/query5072ms ở budget5s và READY/query5872ms ở10s; cả hai children0/readers terminated. Đây là fault kiểm soát, không tái hiện tự nhiên nguyên nhân của CI cũ.
- **Sửa chỉ test:** classification budget10s hữu hạn (max source hiện hỗ trợ, có headroom startup), giữ expected/classification và test child treo150ms. Mọi assertion nhận diagnostic scalar sau close: status/problem/queryMillis/privilege/PS+Java+OS runtime/budget/childrenAlive/readersTerminated; không in command encoded, stderr/exception, CIM object hoặc disk identifiers. CLI default5s, resource OperationTimeoutSec5, source/parser/cleanup/output cap/workflow đều không đổi; không retry/skip hoặc swallow lỗi.
- **Local:** focused test1case ×3 lượt định trước đều PASS, không cộng thành3case; module clean verify/JAR source1e75924 **35PASS/0FAIL/ERROR/SKIP**, gồm timeout/output/cleanup/UNKNOWN/no-false-REMOVED/old-callback regression. Chỉ clean module, không root clean. Receipt mới pin source1e75924;15runtime file hash so bản30166b3 có0khác biệt. Giữ JAR/report/receipt cũ trong ignored backup với hash gốc.
- **Windows thật:** chạy lại MSFT snapshot READY0, Win32 UNKNOWN/nullcount và JAR snapshot, token STANDARD; source/runID/hash ở summary, cả3run children0/readers terminated. Code runtime không đổi nên các đường Ctrl+C/watch/cap lịch sử có thể đối chiếu bằng15hash; không cần gọi lại toàn bộ7run chỉ vì sửa test.
- **CI mới:** [CI39264645975](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39264645975) source1e75924 **SUCCESS**, clean verify toàn reactor trên runner sạch; monitoring-spike35PASS/0FAIL/ERROR/SKIP, classification6case suite3.612s. PASS này không tự chứng minh root cause gốc. Commit hồ sơ sau source chỉ docs/evidence; CI của HEAD hồ sơ được xác minh riêng trong [PR73/checks](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/73/checks).
- **Bảo toàn:**29raw cũ khớp hash; log CI FAIL/SUCCESS, XML và experiment mới giữ local ignored (manifest trong summary, không download link GitHub). Không đổi .env/DB/production/PNP/MON07/08/E1/E2/ADR-V2-05 hoặc FULL gate. **Physical0/12 NOT_RUN**, không dùng CI/READY0 làm hotplug evidence; PR giữ Draft, chưa human approval/merge.
