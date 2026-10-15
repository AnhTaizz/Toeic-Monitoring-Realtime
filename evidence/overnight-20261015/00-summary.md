# B overnight — bàn giao 15/10/2026

Đã thực hiện phần khả thi của cả 6 phase trên worktree anh em `toeic-b-overnight-20261015`. **Các kiểm tra kỹ thuật local PASS; nghiệm thu hệ thống PARTIAL, chưa có human review A/B/C.** Không đổi tracker, kế hoạch, `nguon/`, migrations hoặc `.env`; không bật delta và không merge/approve thay người thật.

Java source đã kiểm: `c1601b55b7b40cd84238921e87355108cfaea5fd`. Clean test và package đều **848 PASS, 0 failure/error/skip** (protocol 44, client 579, server 224, spike 1), gồm các suite opt-in với PostgreSQL 18.6 thật. Môi trường: Oracle JDK 21.0.8, JavaFX 21.0.12, Maven 3.9.11, Windows 11 10.0.26200.9711. Analyzer dùng Python 3.14.4: **4 PASS**, có SHA256 riêng vì bổ sung báo cáo sau khi đóng băng Java/JAR. Các gateway MOCK và fault SIMULATED được ghi riêng, không dùng để kết luận production/LAN PASS.

| Phase | Kết quả cụ thể | Checkpoint local và chứng cứ |
|---|---|---|
| 01 — phụ thuộc A | Kiểm tra ID ACTIVE mới trước mutation; collision không mở lại attempt final; 405/Allow; progress đọc committed data theo assignment, no-store, không lộ answers/epoch. Targeted 49 và hồi quy cuối PASS. QD-29 PROPOSED, NOT HUMAN APPROVED. | `5072bc9` · [kiểm chứng](phase-01/verification.md) |
| 02 — phụ thuộc C | Audit raw ID/scope/FULL; mở rộng controlled harness với measurement window, resource sampler, dry-run/smoke/full và checksum/analyzer. Targeted 22 PASS; C review còn chờ. | `524f606` · [kiểm chứng](phase-02/verification.md) |
| 03 — AT11/shared writer | Kiểm tra chính xác server revision 40/client 42; chặn response sau COMMIT; giữ nguyên retry intent; takeover từ app mới; bỏ qua ACK cũ sau close. Final ExamFlow 41 checks/0 GAP; GUI và C regression PASS. AT11 toàn case vẫn PARTIAL. | `f31ba3d`, `9bbe106` · [matrix](phase-03/AT11_matrix.md) |
| 04 — proctor B4/T2-B5 | Import/create/READY/start/status/reorg qua API thật; progress committed có pagination/stale/401/close. GUI 11 nhóm PASS với controls trong viewport và DB assertions. Reorg không reset answers/deadline/revision cũ. | `0057d81` · [kiểm chứng](phase-04/verification.md) |
| 05 — Windows B5 | Scripts/config/negative checks đã có; parser 3 và negative checks 5 PASS. Raw ASCII native app-image: Media 8 PASS ở source `0057d81`, bundled runtime 21.0.8, API/WS MOCK. **Unicode launcher/installer BLOCKED do thiếu mt.exe/WiX**; máy thứ hai, cài/gỡ và nghe thật NOT RUN. | `546ed17` · [kiểm chứng](phase-05/verification.md) |
| 06 — E1 và gate cuối | Clean/package 848, Flow 41, GUI 11, C1/C2 có GUI PASS trên final JAR. E1 **12/12 run REAL Windows COMPLETE**, 4 poll periods × 3 repeats, warmup 15s + measurement 120s/run. Có 360 eligible, 287 detected, 62 missed, 11 inconclusive, 0 censored; mọi run cleanup=true. Phân tích lặp byte-identical, corrupt-copy bị từ chối. E1 vẫn PARTIAL về kết luận miss rate đầy đủ. | `c1601b5` và evidence cuối · [kiểm chứng](phase-06/verification.md) |

## Thay đổi chính và kiểm thử

- A/protocol: `ExamService`, `ListeningService`, `ApiExceptionHandler`, `ProctorProgressController/Service` và `ProctorProgressPage/Row`; bổ sung boundary/PG/HTTP tests. A/C cần review contract và QD-29.
- B/client: `ExamApiClient`, `ProctorWorkflow`, `ProctorSessionView`, `ProctorProgressView`, dashboard và style; bổ sung workflow/API/AT11/GUI tests.
- C/harness: `ControlledProcessHarness`, `E1Cli`, `E1Window`, `E1Resources`; analyzer và tests tại `scripts/benchmarks/e1/`. Production collector/FULL default không đổi.
- Windows: `package-windows.ps1`, `start-packaged-client.ps1`, `smoke-package-windows.ps1`, `test-package-preflight.ps1`; sửa absolute config path trong pipeline `package-t3b1.ps1` hiện hữu. Thêm LAN preflight chỉ đọc, runbook và manual matrix.
- Hồ sơ đã cập nhật: README, TIEN_DO, NHAT_KY, KIEM_THU, PROTOCOL, QUYET_DINH, VAI_A/B/C, hướng dẫn và evidence từng phase. [Audit công bố](phase-06/publication-audit.json) kiểm tra phạm vi, credential, local links và dữ liệu nhạy cảm.

Lệnh Maven đã chạy thật: `mvn -o -B -ntp clean test` và `mvn -o -B -ntp package`, với PostgreSQL opt-in. Các lệnh Java/PowerShell/Python, thời gian, source/binary hashes và kết quả từng runner nằm trong `test-summary.txt`, `source-and-artifacts.json` và các báo cáo verification của từng phase. [Counts cuối](phase-06/test-counts.json) được đọc từ Surefire XML, không kế thừa số test của PR #35. CI và human review NOT RUN.

## E1 và giới hạn phép đo

[Phân tích sinh từ raw](phase-06/e1/analysis.md), [12 dòng run summary](phase-06/e1/summary.csv), [36 dòng theo duration](phase-06/e1/summary-by-duration.csv), metadata/events/provenance nằm cùng thư mục. Raw ground-truth/observations/resources/manifest giữ nguyên ở `jpackage-out/e1/` trong worktree; public chỉ có metadata an toàn, derived data không PID/process name và checksum của raw local.

CPU là phần trăm một core, tách collector thread với parent process. Memory là heap parent, **không phải RSS**. Latency chỉ áp dụng cho detected samples, đo từ parent launch API tới scan completion (cận trên), không phải kernel start; missed samples không được gán latency=0. Target hold 200/800/3000ms khác actual process lifetime. Suite dùng một parent JVM nên heap/GC/JIT có thể carryover; background workstation không đóng băng. Chỉ có 3 repeats trên một máy với fixture/policy TEST. Candidate gate của harness SIMULATED; Windows ProcessHandle/owned children REAL. Không dùng kết quả để suy diễn hành vi thí sinh hoặc mọi máy/process.

Có 11 sample PID-only thiếu start trong observation (8 ở target hold 200ms, 3 ở 800ms), trong khi parent ground truth đọc được start của cả 11. Nguyên nhân OS/API cụ thể chưa được chứng minh. Không quy unknown thành miss/detected; một số mean miss rate thiếu đủ 3 run. Báo cáo có bounds min..max trên toàn bộ eligible samples; đây không phải confidence intervals. E1 **PARTIAL về khả năng kết luận miss rate đầy đủ**, cần C review, không chỉ thiếu chữ ký.

E2/full-vs-delta, load 1/5/10 của A, gate 20/10 và LAN **NOT RUN**. FULL vẫn mặc định; E1 không thay thế gate delta hoặc human approval.

## Những lượt lỗi và chỗ chưa có bằng chứng

- Baseline PostgreSQL ban đầu connection refused vì Docker chưa chạy; đã bật Docker Desktop/service db hiện hữu, không reset volume. Test sau đó dùng schema riêng và dọn resources do test sở hữu.
- Một targeted controlled-child test INCOMPLETE ở phase 02: giữ log, cause chưa chứng minh; thêm chẩn đoán và retest cùng tập PASS, không bỏ test. Full 848 sau đó PASS.
- Phase 03 có lambda compile FAIL đã sửa; C1 runner có classpath quá rộng gây fixture override, đã sửa staging hẹp. Phase 04 GUI selector nhầm progress/roster đã sửa bằng ID, rồi kiểm tra viewport trước click/chụp ảnh.
- Phase 05 phát hiện .NET relative path lệch PowerShell Set-Location, đã sửa thành absolute path. SDK/WiX thật vẫn thiếu. Không gọi ASCII app-image là installer hoặc auto hearing confirmation là nghe thật.
- Raw SMOKE đầu phase 02 đặt trong target đã bị clean xóa ngoài ý muốn; summary/hash cũ giữ nguyên và có đính chính. Không dựng lại raw; đã chạy SMOKE/final E1 mới và giữ ngoài target.

## Git và công bố

Base ban đầu: integration/PR #35 `11c5da9`, origin/main `8f5ac40`. Ref cuối: main lên `0b9c5fa` do PR #36 B4; integration vẫn `11c5da9` và merge-base target không đổi. **B4 overnight cần đối chiếu với B4 mới upstream trước merge**; [chi tiết và kế hoạch](UPSTREAM_DIVERGENCE.md). Source đã kiểm chưa tích hợp với main mới.

Nhánh checkpoint `agent/b-overnight-followups-20261015` giữ nguyên các commit local. Commit `9bbe106` từng chứa ảnh process/PID nên **không push nhánh checkpoint này**; cây cuối đã bỏ ảnh đó. Nhánh công bố `agent/b-overnight-followups-20261015-public` được tạo mới từ tree đã audit, với parent integration `11c5da9`; không rewrite/reset/force-push checkpoint. Java runtime code khớp source `c1601b5`; analyzer có SHA riêng. File chưa commit và worktree khác được giữ nguyên.

Draft PR: **chưa tạo tại lúc ghi evidence; sẽ cập nhật sau khi remote xác nhận**. Không sửa PR #35/main/PR #36, không mark ready hoặc merge. Nhánh public phục vụ review các đề xuất, chưa đồng nghĩa merge-ready.

## Việc cần người thật trước nghiệm thu

1. A/C review ID issuance/collision/405/progress scope/finalized/metrics và quyết định QD-29; C review E1 plan/window/ground truth/CPU/heap/latency/carryover. B review UI/Media/lifecycle và hòa B4 với PR #36. Agent không xác nhận hộ A/B/C.
2. Có mt.exe/WiX 3 hợp lệ, build Unicode launcher/installer từ source mới rồi thử cài/chạy/gỡ trên máy được phép, không IDE; ghi source/binary/OS. Không tự xóa audio marker khi upgrade/uninstall.
3. Nghe loa thật, rút mạng/kill đúng app test, chạy 2 candidate + proctor trên LAN 2 máy; thực hiện AT11/LT01–LT04/IT01–IT04/MT01/07/08 theo [manual matrix](../../docs/ACCEPTANCE_B_MANUAL_MATRIX.md) và [LAN runbook](../../docs/LAN_TWO_MACHINE_RUNBOOK.md).
4. Có evidence/review thật rồi mới quyết định merge và trạng thái tracker; hạn 31/10/2026, gate delta 20/10 vẫn riêng.
