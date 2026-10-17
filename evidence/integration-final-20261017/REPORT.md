# Unified release candidate — 17/10/2026

## A. Actual Git State

Đây là rehearsal local trên `test/unified-final-20261017`, không phải merge các PR nguồn hoặc merge main. Root HEAD `6a410316` và năm file/thư mục untracked ban đầu giữ nguyên. Worktree cũ không bị reset/prune/remove. Main remote `07c4024ee728ce533149d8c0175e35084ee8931a` đã nhận CI PR44.

| PR | HEAD | Base branch | Phân loại |
|---|---|---|---|
|39|093429ab1abb19f9c6d37be9b86ae8e00f11a284|main|REQUIRED_FOR_UNIFIED_CODE|
|40|3fc5bb4e8ef93b60bf479fc52fe725e4b8f072af|PR39|REQUIRED_FOR_UNIFIED_CODE|
|41|304bc509bf26966499d6d0fb98a922794743b2ee|PR40|REQUIRED_FOR_EXPERIMENTS|
|42|7af7046512d008a289df3877c0d43f4207634821|PR40|REQUIRED_FOR_UNIFIED_CODE|
|43|772c6e9864cd6ce0ad45aad0433f5bfce53fee54|PR41|REQUIRED_FOR_UNIFIED_CODE; NEEDS_OWNER_DECISION phần history|
|45|a6524823c6a8d97cc7652fe4b776279db17d7d55|main|NEEDS_OWNER_DECISION/BLOCKED_BY_CONFLICT; workflow OPTIONAL_CI_OR_TOOLING|

PR39–43 còn OPEN/Draft; PR45 OPEN, không Draft. Không tìm thấy owner approval trong review submissions, comments hoặc inline threads tại preflight; HEAD được kiểm lại cuối phiên. Không dùng mergeable=true làm approval. API PR39 vẫn ghi base_sha c0fd903; nguồn rehearsal dùng origin/main mới07c4024 được fetch và kiểm Git, không lấy checkpoint API cũ thay main mới.

## B. Integration Graph

`PR42 → PR40`, cùng với `PR43 (đã chứa PR41) → PR40`, rồi đưa toàn bộ nội dung vào RC chứa PR39 và latest main. PR39 là ancestor của PR40 nên thao tác đưa PR39 vào branch rehearsal không thêm commit/patch lặp. Tất cả HEAD39/40/41/42/43 là ancestor của RC.

- `50b477ef3a39ab9e911df5bf0a55526424d46f60`: rehearsal F01 vào nền40; tree bằng42.
- `44f1172`: ghép C stack; conflict chỉ docs/NHAT_KY.md và docs/TIEN_DO.md, giữ cả hai ghi chép, không chọn một phía production.
- Source được test/build MSI: `c224ba8572ae453ad97e0be4f85914e112041805`.
- Source tree: `22858b425acfd182ea794626b4aa6d319bd1baae`.
- PR45 ngoài RC. Merge-tree có conflict AudioFiles.java; không giải quyết semantic conflict tự động. Không nhân đôi #17/#28/#35/#38.

Hồ sơ bàn giao được commit riêng sau kiểm thử; source/test/build/resources và scripts phải bằng c224ba8. Không gán metadata MSI cho commit hồ sơ mới.

## C. Review & Merge Decisions

| Gate | Trạng thái | Điều kiện còn thiếu |
|---|---|---|
|42 → 40|READY_FOR_OWNER|A/B xác nhận F01 và test chạm phần A|
|43 → 41|BLOCKED cho merge thật|C xác nhận patch mới; A quyết định contract/UX history >5000|
|41 → 40|READY_FOR_OWNER về bằng chứng kỹ thuật; chưa được merge|C xác nhận raw/rejoin/CPU và B review phương pháp; phụ thuộc43|
|40 → 39|BLOCKED cho merge thật|Cả F01/C stack và approvals tương ứng chưa nằm trong remote40|
|45|NEEDS_CHANGES; DEFER khỏi RC|B hòa policy junction với fix race; review CI riêng|
|QD-29|PENDING|A/C chưa quyết định|
|Unified regression|PASS automated local|Manual/LAN/soak NOT RUN|
|MSI|REBUILT_AND_TESTED local|Máy thứ hai NOT RUN|
|PR39 → main|NO_GO hiện tại|Owner/contract/CI/acceptance chưa đủ; phiên này cấm merge main|
|Project release|NOT_READY|Manual/LAN/hearing/load/E2/report/submission chưa nghiệm thu|

PR43 terminal cleanup: dưới lock tắt authorized/sendingAllowed và tăng generation trước cleanup; worker dừng, đóng listener và quên transport correlations, giữ RAM để discard accounting. Reconnect không bật authorized lại; callback generation cũ không được retry. Tests cleanup/permission/stop và regression FULL/epoch/gap PASS theo XML. Đây là kiểm tra kỹ thuật, không thay owner C approval; không tuyên bố đã chạy mọi interleaving của race.

History: quyền PROCTOR/scope trước query, LIMIT5001 và trả đủ5000 hoặc lỗi toàn bộ khi5001. PostgreSQL/HTTP tests kiểm cả hai endpoints, thứ tự, attempt khác,401/403 và lỗi không lộ nội bộ. HTTP500 RETRYABLE_SERVER_ERROR hiện tại không tự khắc phục bằng retry nếu dữ liệu vẫn quá ngưỡng; dashboard giữ stale/error, không có pagination. A cần chấp nhận hoặc yêu cầu thay đổi contract riêng.

PR45: probe public AudioFiles.file với REAL filesystem, MOCK manifest,16reader×8vòng trên source a652482 có18/128 lỗi FileAlreadyExistsException; cùng probe RC giữ fix40 có0/128 lỗi. Đây là regression đã tái hiện, không phải đo audio playback. Source PR45 bỏ catch concurrent creation và thay boundary: tin ancestor ngoài root, kiểm attributes root trở xuống. Không tự hòa policy hoặc cherry-pick workflow.

## D. QD-29 Decision

Xem DECISION_MATRIX.md: trạng thái PROPOSED_FOR_A_C_APPROVAL. ID ACTIVE mới ASCII1..128, không trim/transliterate; validate cả batch trước mutation; create/reorganization cùng boundary; collision không reopen FINAL; legacy chỉ đọc history/status theo auth hiện hữu. Đề xuất chặn legacy ACTIVE không tương thích trước khi vào/resume thi, hướng dẫn giám thị cấp lượt mới; không migrate/cancel dữ liệu tự động. A/C quyết định legacy/status/message/test/dataset, B review thông báo. Chưa APPROVED.

`takeoverWriter` không idempotent theo requestId: mất ACK rồi retry có thể tăng epoch thêm. Row lock bảo vệ writer, FINAL/deadline vẫn được kiểm; không tự mở rộng protocol khi chưa có quyết định/bug được xác nhận. A/B cần hiểu recovery; không tuyên bố đã sửa hành vi này.

## E. Regression Evidence

| Suite | Source | PASS | FAIL/ERROR | SKIP | Nhãn |
|---|---|---:|---|---:|---|
| Maven clean test | c224ba8 | 904 | 0/0 | 2 | REAL PG/GUI + MOCK/SIMULATED |
| Maven package | c224ba8 | 904 | 0/0 | 2 | REAL PG/GUI + MOCK/SIMULATED |
| MSI0.3.9 installed FullSuite | c224ba8 | 906 | 0/0 | 0 | REAL installed/PG/GUI + MOCK/SIMULATED |
| ExamFlow/AT11 standalone | c224ba8 | 41 checks | 0 | 0 | REAL PG/HTTP; loss SIMULATED |
| PostgreSQL concurrency | c224ba8 | AT01/07/09/10 | 0 | 0 | REAL PG/latch/HTTP |
| Application GUI | c224ba8 | 11 nhóm | 0 | 0 | REAL GUI/HTTP/Media; hearing SIMULATED |
| C1 FULL / C2 late-gap-UNKNOWN | c224ba8 | PASS từng harness | 0 | 0 | REAL PG/WS/GUI; readings/faults MOCK/SIMULATED |
| Python analyzer | c224ba8 | 12 | 0 | 0 | MOCK fixtures, phương pháp |

81 suite XML/906case mỗi lượt; protocol45/client626/server234/spike1. Clean/package skip đúng app-image và installed khi chưa có artifact mới. Installed FullSuite sau đó bật cả hai opt-in và đạt906/0skip. Tests history bounds2, terminal cleanup và F01 mapping/GUI là subset đã nằm trong tổng. Xem *-counts.json/*-run.json/RAW_LOG_INDEX.json, raw local tại target/integration-final-20261017/runs-actual. Theo source/SHA riêng, historical897/898 và C_REVIEW300 không được cộng hoặc gán cho source mới.

Tổng từng lượt lấy XML archive, không cộng các lượt lặp thành số testcase duy nhất; focused regressions là subset của tổng. Standalone smoke và Python không cộng vào JUnit.

REAL: PostgreSQL18.1 cluster riêng loopback/cổng tự chọn, Spring/HTTP/WS, JavaFX controls/Application, Media/Edge/ProcessHandle theo từng harness, app-image/installed runtime khi chạy thật. MOCK: DTO/process readings/gateway fixture. SIMULATED: mất ACK, fault injection/socket seam, xác nhận nghe tự động. LAN hai máy, người nghe thật, load campaign, E1 collection mới, E2: NOT RUN. C1 dùng TEST STALE1200ms/TTL4000ms, không phải soak hay TTL production đầy đủ. Historical FAIL cleanup Edge vẫn FAIL, root cause UNRESOLVED.

E1: 84hash nguyên vẹn trước/sau. Java mới rejoin12/12 exit0, cùng byte join gốc. Analyzer mới 12tests PASS; hai lần tái phân tích sinh17file giống byte. Tổng360eligible/287detected/62missed/11inconclusive/0censored không đổi. CPU time-weighted đúng theo tổng deltaCPU/tổng deltaWall; coverage mỗi run khoảng99,58%, resourceStatus OK. Collector CPU mean một core cũ→mới:250ms29,886→29,881%;500ms18,855→18,851%;1000ms10,319→10,325%;2000ms5,556→5,544%. Thay đổi nhỏ không thay nhận định trade-off trong dữ liệu này. Machine alias cũ là hằng, không thể dùng guard mới để chứng minh retroactively cùng máy vật lý. Scope vẫn collector-only/TEST-owned, parent launch→scan-end và heap JVM; không full-app overhead hoặc lợi ích DELTA. Owner C cần xác nhận kết luận mới; raw/source c1601b5 giữ nguyên, không lấy source RC gán vào raw.

CI Windows: GitHub run39006248748 SUCCESS, checkout synthetic merge e068390 (PR45 a652482 với main07c4024), Temurin21.0.12/Windows2025. Không phải CI của RC c224ba8. RC giữ workflow Ubuntu của main; GitHub Actions trên RC local NOT RUN. PG opt-in/JavaFX/MSI được kiểm local, không suy từ Maven CI mặc định.

Các probe orchestration trước test có lỗi chờ child tree/pipe pg_ctl hoặc exitCode null; giữ output ở runs/runs-final/runs-verified, không tính là product FAIL/PASS. Wrapper cuối dùng Process trực tiếp, cluster/output mới. Probe artifact ban đầu nhận nhầm JavaFX DepthTest là test và một lần đọc khi FullSuite đang clean; đã sửa bộ lọc ở control script và chỉ dùng hậu kiểm sau pipeline, không sửa product/JAR.

## F. MSI

**REBUILT_AND_TESTED (local).** MSI0.3.9, source `c224ba8572ae453ad97e0be4f85914e112041805`, source sạch khi build.

- MSI SHA256: `bef7528f77b35695904812b2959ea389b6ac790ba6fc8f9f91fc665632af604e`.
- Packaged client JAR SHA256: `27976c77f914b64cb575cbbafda3212af7a802f682cc6b3ba4589148f32db1bd`.
- Server JAR mới sau FullSuite SHA256: `0867dcb445e63e96b919da96bf9985178620fd6e9761d078078e7f29b4489f57`.
- 5439class trong JAR packaged bằng từng byte JAR build lại sau FullSuite;0mismatch, không đổi class set. So với MSI0.3.8 có8class thay đổi, nên không dùng proof/artifact0.3.8 thay source mới.
- Runtime Oracle21.0.8/JavaFX21.0.12/Windows natives đủ; launcher UTF-8, FULL mặc định, không TEST agent hoặc secret config trong production JAR. TEST agent phục vụ automation nằm ngoài artifact.
- Cài product TEST tạm bằng user thường, Unicode/spaces, chạy normal EXE không agent/JAVA_HOME, bundled JVM, deny-write install, FullSuite906PASS/0skip, close/uninstall PASS. Script từ chối thay app người dùng đã cài. Không thay app/firewall/OS locale người dùng.
- Binary mới giữ ở workspace chính `target/integration-final-20261017/release-0.3.9/ToeicMonitor-0.3.9.msi`, metadata và SHA256 cạnh nó. Không ghi đè0.3.8 hoặc phát hành artifact.
- Hậu kiểm0Edge TEST/0schema/0product TEST; cluster riêng đã dừng. Máy thứ hai/LAN/manual hearing NOT RUN. Xem MSI_PROVENANCE.json và cleanup.json.

## G. Blockers

- P0/P1 code RC: không phát hiện mới trong phạm vi đã review/test. PR45 có regression cache tái hiện và chưa được nhập.
- P2/contract: history5000/5001/retryable UX; QD29/legacy ACTIVE; RealtimeClient vẫn giới hạn inbound chars thay UTF-8 bytes (finding A/B đã ghi trong PR43), chưa tự sửa.
- Review: thiếu owner A/B/C, approvals cho stack C mới và F01; không dùng A_REVIEW/C_REVIEW cũ để approve PR43.
- Experimental: E1 mới chỉ tái phân tích raw lịch sử, còn owner confirmation/phạm vi; E2/load/LAN/hearing/soak không nâng PASS; FULL mặc định.
- Submission: báo cáo/README/video, nghiệm thu máy thứ hai và xác nhận bàn giao chưa có. MSI local PASS không hoàn tất IT01 máy khác.

## H. Exact Next Actions

1. A/C chốt QD29, legacy ACTIVE và chấp nhận/sửa contract history5001; B xác nhận thông báo/dataset.
2. C xác nhận patch41/43 và kết luận từ raw/rejoin/CPU mới; A review phần HTTP/history, B review phương pháp.
3. B review F01 và sửa/hòa PR45 theo policy đã chốt, giữ regression race/junction; kiểm CI trên đúng source sau đó.
4. Khi có approvals, merge42→40,43→41,41→40,40→39 đúng expected HEAD và kiểm ancestry/tree; không cherry-pick lặp. Rehearsal hiện có đủ39–43.
5. Đối chiếu source39 cuối với RC, chạy lại phần thay đổi nếu có; owner hoàn tất LAN/hearing/load/E2/report gates và mới đề nghị merge main. Agent không merge main trong nhiệm vụ này.

## I. Final Verdict

Có bản unified TEST chứa đủ code A/B/C từ39–43 và latest main, còn thiếu owner approvals để merge thật. PR41/43 không bị bỏ sót; PR45 cố ý để ngoài vì conflict/regression. QD29 chưa approved. Production class khác MSI0.3.8 nên cần MSI mới; trạng thái bản0.3.9 ở mụcF. PR39 hiện chưa đủ điều kiện đề nghị merge main/release. E2 không có evidence thì giữ FULL, không tuyên bố DELTA tốt hơn.
