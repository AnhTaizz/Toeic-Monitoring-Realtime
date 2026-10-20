# Kit main tích hợp — 20/10/2026

User ủy quyền kit/test/docs và sửa tool có bằng chứng, commit/push/PR, review rồi merge
commit nếu CI/quy định cho phép; không ký human approval. Không USB, physical0/12 NOT_RUN;
LAN0/41/B PENDING/BASE03 NOT_DECIDED/MON01 pending physical/FULL default/experiment OFF.
Không tracker DONE, observer/USB→MON07/push/dashboard/offline/retention/ADR changes.

## Nguồn và phạm vi

Fetched main `a6fba31178a1694f42df4a682d4a0ed205d77e45`, không có commit mới sau PR74.
Worktree riêng nhánh `test/c-integrated-kit-20261020`; root main/worktrees khác giữ nguyên.
Windows11 x64 build26200, PowerShell5.1.26100.9711/RemoteSigned, token STANDARD,
Temurin21.0.10+7, Maven3.9.15. PostgreSQL/server **không khởi động** trong lượt kit.
Đã đọc CLAUDE/hướng dẫn user, TIEN_DO/NHAT_KY/VAI_C, taskcards/gate/runbook/receipt review C.
User đã opt out codex-with-chatgpt; không dùng hoặc tuyên bố ChatGPT review.

## Lỗi tool, tái hiện và sửa

`New-T3C4LanBundle.ps1`/`Common.ps1` khóa BASE03 source463cbad/runtime834f683/JAR cũ.
Từ main tích hợp, builder legacy từ chối `RUNTIME_CHANGED_CORRECTIVE_REVIEW_REQUIRED`
trước khi tạo output (raw baseline-failure.txt giữ ignored). Đây là guard đúng cho artifact
cũ nhưng thiếu đường build hiện hành; không sửa assertion/hash để nhận JAR cũ thành main.

Thêm `-BuildFromHead`: yêu cầu HEAD sạch descendant origin/main đã fetch, Maven package
từ source đó, pin SHA/hash JAR vào Common **bản xuất** và ba template run. Legacy source
pins giữ nguyên. `-SkipArchive` dùng thư mục kit hiện có, không ZIP/installer trùng lặp.
Guard output/archive tồn tại trước build, CreateNew cho receipt/run/checkpoint vẫn giữ.
Không Java/POM/SQL/timeout/production default changes.

Regression tại `Test-T3C4LanKit.ps1`: MOCK file bytes, pin source giữ nguyên, legacy từ
chối bundle khác, export nhận đúng manifest, tamper JAR bị từ chối. Không network/device.

## Kiểm mới tự chạy (không dùng lại số lịch sử)

| Kiểm | Source / lệnh | Kết quả / giới hạn |
|---|---|---|
| Module USB | main a6fba311, `scripts/build-usb-probe.ps1`, cache Maven local | 35PASS/0FAIL/ERROR/SKIP, JAR + receipt14source/15runtime. clean chỉ module mới, raw ngoài module target |
| Consumer | a6fba311, `mvn -pl client -am -Dtest=DashboardControllerTest,DashboardModelTest,MonitoringApiClientTest,MonitoringJsonTest,LateEventsAndGapsTest,FullStateDashboardTest -Dsurefire.failIfNoSpecifiedTests=false test` | 65PASS/0FAIL/ERROR/SKIP; model/HTTP fixtures MOCK, không GUI manual/PG/LAN |
| Script regression | thay đổi kit trên main, `Test-T3C4LanKit.ps1` | 33 checks PASS; parse9scripts, MOCK files/maps/origins, JDK21 thật; không LAN |
| MSFT snapshot | a6fba311 wrapper/receipt default5s/duration0 | READY/count0, query1316ms, STANDARD, child0/readers true. Không topology/hotplug acceptance |
| Win32 snapshot | cùng source/config | UNKNOWN/count null/CLASSIFICATION_INCOMPLETE, query407ms, exit4, child0/readers true |
| Output cap / duplicate | cùng receipt-verified classes, CLI sourcea6fba311/duration10s/interval250/max4 | 4JSONL gồm FINISH, outputLimited true/exit5/child0/readers true; trùng output exit3, hash gốc không đổi |

Giữ log invocation consumer đầu tiên FAIL **trước chạy test**: PowerShell tách property
không quote thành lifecycle `.failIfNoSpecifiedTests=false`; lệnh sửa quote mới chạy65PASS.
Không retry test lỗi đến PASS. Không rerun full campaign/benchmark/DB/GUI smoke vì runtime
không đổi; lịch sử real WS/PG/JavaFX có tại [review C](../c-technical-review-20261020/REPORT.md).
Ctrl+C thật không chạy lại; test close/timeout/output/readers trong35PASS là MOCK child,
H11 physical vẫn NOT_RUN. Provider READY0 không chứng minh USB detection/hotplug.

## Handoff

[USB runbook](../../docs/USB_STORAGE_PROBE.md): giữ H01–H12, mỗi ca có thiết bị/điều kiện,
lệnh, thao tác, expected, evidence/cleanup và PASS-FAIL-INCONCLUSIVE; Actual trống,
[physical CSV](../v2-c-mon02/physical-pending.csv) vẫn0/12NOT_RUN. Không yêu cầu cắm/rút.

[GUI/LAN runbook](../../docs/T3_C4_PHYSICAL_LAN_ACCEPTANCE.md): cùng artifact/hash A/B,
TEST-only env không `.env`, server port/bind, seed accounts/assigned session API hiện có,
ba app terminals/preflight/checkpoints/cleanup. Phân loại đầy đủ41case:24local-subset và
17two-machine để hoàn tất; không nâng physical từ local/SIMULATED/history.

Raw/log/JAR/kit duy nhất giữ `target/kit-check` của worktree này (ignored), không public
link tới ổ local. Receipt safe scalar/checksum bổ sung sau build pinned commit; không
copy artifact lịch sử. Không credential/token/serial/CIM object/hostname/IP/ảnh/log thô
trong public diff. Main trước thay đổi và published-source tương đương runtime nếu chỉ
docs/tools thay đổi; không lấy SHA publication giả làm source JAR.

### Build pinned commit và kiểm bản xuất

Source kit build **`7cd231e995d49a9a39ef27608d7c2e02d50f76b1`** là descendant main a6fba311;
diff production Java/resources/POM bằng0. Lệnh
`New-T3C4LanBundle.ps1 -BuildFromHead -SkipArchive -OutputDirectory target/kit-check/integrated-bundle`
thành công: reactor package **skip tests**, không clean/root raw/DB/server. Kit22file,
manifest/JAR SHA256 và bốn JAR local tại [receipt](receipt.json); không upload JAR/ZIP.
Receipt build USB/tests/provider trước đó sourcea6fba311 giữ nguyên, không gán thành7cd231e.

Test kit **đúng commit7cd231e** 33PASS; bản xuất33PASS nữa (cùng suite lặp, không66unique).
Export manifest xác minh mọi file +hai JAR; init tạo đúng41NOT RUN/SHA mới, review.json
đúng SHA nhưng B/C PENDING; run source đúng; init/builder từ chối ghi đè, run hash không đổi,
không ZIP. Public receipt chứa hash raw **local ignored**, không giả URL GitHub cho raw.
Workflow giữ nguyên clean verify, thêm bước script33checks MOCK/no network. CI exact
publication HEAD và kết quả merge được ghi ở PR; chưa lấy CI lịch sử làm kết quả mới.

Technical review Codex: source defaults vẫn kiểm legacy, export pins chỉ đổi trong copy,
nguồn sạch trước build, HEAD descendant main đã fetch; builder không đọc `.env`, không
đổi protocol/migration/runtime/default. Hash manifest cần kênh độc lập khi nhận, không là
chữ ký owner. Diff chỉ tools/test/workflow/docs/evidence; rà pattern credential/private key
và nội dung mới không có secrets/raw/media/binary. Không thay `.env`, DB dev, tracker hoặc
physical status; không tạo approval GitHub hay ký A/B/C.

Bước C tiếp theo không USB: cùng B thực hiện subset GUI lifecycle FULL trên một Windows
với TEST DB/đề/audio/assigned attempts do A cấp, ghi actual stop/logout/submit/timeout và
worker cleanup theo MON01. Dependency: A cấp TEST scope, B thao tác GUI; chỉ kiểm/chụp safe
evidence, không observer mới/gate/DONE và không gọi đây là LAN. Chưa triển khai trong task kit.
