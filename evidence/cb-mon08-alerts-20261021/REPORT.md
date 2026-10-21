# MON-08 C+B — slice rule/aggregation → timeline (21/10/2026)

Base `1507591b0260f8b9a8b67ed6025a77f820687563` (PR93), nhánh `feat/cb-mon08-alert-rules-20261021`. Runtime cuối `1e4b1ca8661651bd5ec020c282037e509ff038aa`; build/green integration source `9e31d5062234ef121479ec8ae6f2879f9d19c4d4` (commit sau runtime chỉ sửa TEST reserved-gap fixture). Publication sau chỉ docs/ảnh/receipt. [Bảng rule/contract/demo](../../docs/MON08_EXPERIMENTAL_ALERTS.md), [receipt/hash](receipt.json). CI/PR/merge cuối ở PR; không tự ghi pending thành PASS trong file này.

## Review source và implementation

Kiểm main/open PR thật trước sửa: main1507591, chưa MON08 PR trùng; không merge PR EX/UI. AlertsPane/RoomModel cũ chỉ projection process/presence/gap, không engine MON08. Policy process-policy-v1 có whitelist đã dùng; không có ngưỡng ưu tiên MON08 APPROVED. User cho phép C+B thử nghiệm, commit/push/PR/merge commit nếu CI/rules đạt; không thay chữ ký/human review.

Lõi thuần stateless4rule OBSERVATION/SOURCE_UNAVAILABLE/DATA_GAP/PRESENCE_INTERRUPTION. Ngưỡng mặc định3/6, bucket60s/window300s/sourcecap200 đều EXPERIMENTAL, bounds và ý nghĩa tại runbook. Server read model từ committed MVCC snapshot, scoped ACTIVE-only/auth trước đọc, không cache/migration/persistent pipeline. Mỗi source cap+1 probe, 100group/5ref. Client/API/parser + panel nhóm nhỏ trong timeline, cùng lifecycle/scheduler/budget cũ. FULL/defaultOFF giữ nguyên; không sửa raw/process state/queue/retry/COMMIT-ACK/observer/protocol module/dependency. SignalService chỉ mở helper auth + parser trong package để reuse, không nới write guard.

## Ví dụ có thể đối chiếu

- Oracle tính tay:6 event focus LOST khác ID, cùng attempt/run/source/bucket, cutoff cố định `2026-10-21T01:05:00Z` →1 nhóm HIGH_ATTENTION/count6/evidence5/limited=true. Thêm retry cùng ID hoặc đảo input vẫn1nhóm/count6. Một lần chuyển app vô hại →INFO/count1, không verdict.
- Pipeline run `usb-a5e3875cb7d5425f`:6 focus MOCK có observedAt cũ1ngày +USB1+UNKNOWN1+gap1+process1 =10 committed input ban đầu; screenshot thật thể hiện focus HIGH_ATTENTION/count6, gap REVIEW/count1. USB đơn lẻ INFO/count1 được assertion riêng. Gap có droppedCount3 từ **actual admission overflow**, không mock loss counter; ACK-loss SIMULATED, retry cùng payload không nhân count. Bộ bucket oracle độc lập tính từ timestamps history gốc và đối chiếu priority/count, không gọi engine để tạo expected.
- Sau21display MOCK bổ sung:30 typed committed, read cap TEST20 →20typed+1process=21 input; sourceLimited=[SIGNAL], không gọi khoảng đầy đủ. Raw UI31rows vẫn còn gap/UNKNOWN; nhóm INFO20display không biến thành HIGH chỉ vì có nhiều event. Source cap TEST20 là override để kiểm bound, default vẫn200.
- Uncommitted/rollback qua authenticated SignalService transaction fixture không tạo summary. Đây là TEST transaction fault simulation, không SQL INSERT event/scope bypass. Scope/session/assignment qua seeder/API. DirectSQL chỉ revoke TEST login để chứng minh denial.

## Kết quả thực chạy

| Kiểm | Source/lượt raw local | Kết quả |
|---|---|---|
| Baseline tập trung trước sửa | main1507591 sạch; target/mon09-check/baseline-mon08 | 95PASS (43client+52server),0SKIP |
| Focused rule/mapper/parser/controller/shared | WIP cuối trước no-store/wording; focused-corrected | 134PASS/0SKIP (50client+84server) |
| MON08 real WS/HTTP/PG/JavaFX Scene | source9e31d50; mon08-integrated-gap-fixed | 1PASS; hai attempts, scope403, real reconnect/IDs/counts, revoke401/clear, rollback invisible, source cap |
| MON08 OFF / demo MON09 cũ | source9e31d50; mon08-alert-off-regression | 1PASS; raw A19/B1/actual3drops; new laneOFF |
| Toàn reactor verify/package, không clean | source9e31d50; full-verify-mon08 | **1302PASS/155SKIP/0FAIL/ERROR** =1457total |
| Legacy dashboard JavaFX | source9e31d50; legacy-gui-mon08 | **12PASS**, MOCK API/Scene, không OS input |
| JAR/source/resources/dependencies | source9e31d50/runtime1e4 | **21/21checks**;4JARhash trong receipt |
| Portable EXE app-image | build9e31/runtime1e4 | đúng login window, bundled JVM, đóng bình thường; không login/network/installer acceptance |

Full module total/skip: protocol86/0, spike35/0, client870/27, server466/128. Opt-in PG/GUI/CIM/packaged/installed không bật trong full verify;155SKIP không tính PASS. Hai case PG phía trên là invocation riêng, không cộng lượt lặp thành số test mới. Unit gồm threshold dưới/đúng/vượt, actualUSB/focus/window mapping, window lower-inclusive/cutoff-exclusive, run/type/attempt isolation, late receipt, input order/duplicate/conflict, missing process metadata/foreign policy, UNKNOWN/gap/presence, config/input/group/evidence bounds, harmless example, exact large counts; consumer có header/body cap, scope/generation/lateuncancellable/stale/revoke/cancel/close. Final full verify gồm thay đổi no-store/wording và BigInteger.

## FAIL giữ nguyên và sửa theo bằng chứng

- `rule-unit`: compile lỗi test helper gọi doesNotHaveBean với nhiều Class; sửa overload test, không đổi assertion ý nghĩa. `rules-unit-fixed`21PASS, các bổ sung sau được full verify.
- `gap-overflow-before`: WIP implementation **1ERROR Arithmetic long overflow**,2 legacy gap hợp lệ Long.MAX_VALUE; giữ source hash/bản source/XML/log. Sửa tổng dẫn xuất BigInteger +decimal string có bound, không nới wire hoặc sửa count gốc; oracle exact18446744073709551614 PASS sau sửa.
- source6344160/`mon08-integrated-first`: fixture nhầm `/auth/me.user.id` thay vì `userId`,1ERROR; sửa test tại5ff546b.
- source5ff546b/`mon08-integrated-auth-fixed`: assertion bắt response400 endpoint mới thiếu no-store; implementation controller1e4b1ca thêm no-store cho controller invalid/denied errors, không đổi expected hoặc global auth. Rollback/uncommitted oracle đã qua trước lỗi này.
- source1e4b1ca/`mon08-integrated-no-store-fixed`: fixture cố enqueue SignalGap trực tiếp bị SignalDelivery từ chối đúng thiết kế. Test9e31 tạo **overflow queue thật** để delivery tự freeze reserved gap; không nới guard, vẫn expected9typed commits/3drops.

Không rerun mù tới PASS, không skip/swallow hoặc tăng timeout. Tất cả FAIL giữ raw ignored và cleanup0schema/PGstop0/PIDabsent; các source/run không bị đổi nhãn thành green source. Client/server artifact cũ không được gán merge SHA.

## Ảnh và demo

[Groups — MOCK observations, real pipeline](screens/groups-mock.png) · [Coverage LIMITED — cap nguồn](screens/limited-mock.png). Ảnh JavaFX Scene1600×1000, đã rà: chỉ generic MOCK seed display names/TEST UUIDs; không token/password/IP/answers/hardware identifiers/raw CIM/user paths. Không OS screenshot/human acceptance. Bảng nhóm có scroll, chọn dòng mở đủ giải thích/policy/window/evidence, raw history bên dưới và nút đối chiếu; filter raw không che coverage/group. Source cả2ảnh9e31, không gán SHA publication/merge.

Một lệnh tại checkout mới: `.\scripts\demo-mon08-alerts.ps1`; mặc định cửa sổ20s rồi cleanup. Xem runbook prerequisites/config/flags. EXE portable ASCII path chỉ local, hash trong receipt; không giả GitHub download link. Raw logs/PGdata/JAR/ảnh chưa rà chỉ local ignored, localOnlyManifest là hash đối chiếu, không phải artifact tải được trên GitHub.

## Cleanup và phần chưa nghiệm thu

Hai green runs đều sourceDirty=false, schemaRemaining0, PGstop0, postmasterPID absent. Delivery/view/API/listener/timer/worker fixture sở hữu đóng; shared transport chủ sở hữu đóng. Không `.env`, DB dev, broadkill/volume reset. Không observer thật/hardware/CIM mới trong slice này; input MOCK, storage/transport/JavaFX thật. Defaultflag matrix chứng minh cần cả2flag; FULL default/experimentsOFF không đổi.

Batch delivery có thể là false positive cho burst server-received; priority không là kết luận. Current process state/presence/Listening không được engine suy từ lịch sử. Cần nhóm duyệt thresholds/false positives/policy và B/A/C review trước production. **USB0/12**, physical second-display/desktop OS/manual/LAN hai máy **NOT_RUN**; localhost không là LAN. MON08 technical slice, chưa DONE; gates/ADR/owner policy pending, offline60s/retention30ngày chưa approved. Không backlog/hours update, không task khác/E1/E2.
