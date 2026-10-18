# Release acceptance & monitoring gate — 18/10/2026

**RELEASE BLOCKED; FULL vẫn là baseline.** Đây là packet Task 2 để B/C tiếp tục nghiệm thu, không phải chữ ký người thật. Đối chiếu GitHub lúc 15:27 UTC (22:27 UTC+7); xem [checkpoint](github-checkpoint.json). Main production f86403489bdd01887b926f2f9e4b72ddf05d1423. Nhánh riêng review/release-acceptance-20261018; test fix 9bd052a34d17d05a8dd7d7310ffb9667e91eebb0. Agent thực hiện trên LOCAL_BUILD_HOST, không điều khiển máy Windows vật lý thứ hai.

| Hạng mục | Trạng thái |
|---|---|
| PR47 | **C_REVIEW_PENDING**, OPEN / Ready for Review, mergeable; chưa merge |
| Main source SHA | f86403489bdd01887b926f2f9e4b72ddf05d1423 |
| MSI build | **BLOCKED**: PR47 chưa qua gate; chưa tìm thấy WiX 3 / SDK mt.exe |
| Second-machine MSI | **SECOND_MACHINE_NOT_RUN** |
| LAN 41 cases | **0 PASS / 0 FAIL / 41 NOT RUN** |
| Human B review | **PENDING** |
| T3-C4 gate | **PENDING**, chưa có quyết định owner C |
| Production mode | **FULL**; DELTA chỉ experimental opt-in, không promote |
| T3-C5 | **REVIEW_PENDING**, patch đề xuất cho báo cáo PR51 đã kiểm áp dụng |
| PR52 | OPEN / Draft; base vẫn gate/t3-c4-lan-20261017; chưa retarget |
| E2 | **NOT RUN**; không kết luận hiệu năng |

## 1. Integration gate và blocker

PR47 HEAD 3044e5bc963f7ac9d8cdc4d8410696c301080534, source sửa 3d938209ff3a4aa0d92dba7fca0e606fbc0b5245, base main f864034. [CI39093801431](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39093801431) SUCCESS đúng HEAD. API trả 0 review, 0 comment; không có C/B xác nhận. Giữ Ready for Review và nhánh C1/C4, không merge, không approve thay C.

Hai P1 đã được [Task1/PR55](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/blob/e3fdfea82a1b69d12b0270008b64db92243050bc/evidence/stage3-cross-review-20261018/STAGE3_CROSS_REVIEW.md) tái lập:
- MAIN-CLOSE: main chưa có giới hạn write CLOSE và lọc correlation muộn; chờ PR47 và C/B review. Bảy regression trên PR47 PASS, ba baseline regression trên main FAIL là evidence Task1, không chạy lại.
- QD33 audio concurrent publication: 467 lỗi / 1280 operations trong bốn batch Windows, final hash đúng không xóa lỗi ghi. B cần corrective PR riêng và regression. Chưa sửa trong Task2. Claim “không P1” của stabilization17/10 là kết luận lịch sử đã bị phát hiện mới thay thế.

Không phát hiện thay đổi wire contract trong hai production file PR47 khi đối chiếu diff. Điều đó không thay review C/B. Khi owner giải quyết blocker và xác nhận, phải đọc lại HEAD/reviews/CI/main; chỉ merge commit với expected HEAD SHA, rồi kiểm tree, ancestry và FULL default. Không thực hiện nhánh điều kiện đó trong phiên này.

PR51 b057b0f, PR52 c58931e, PR53 b4177a7 đều OPEN/Draft, CI SUCCESS đúng HEAD (run39063829880,39097078572,39112594829). PR53 technical ACCEPT QD31/32 là evidence cũ; không nhận owner approval. PR51/52 cũng 0 review/comment. PR55 e3fdfea CI39139325117 **FAIL**: giữ nguyên kết quả này.

## 2. Kiểm chứng tự chạy Task2

Chi tiết expected/actual, SHA, thời điểm, máy, người thực hiện và nhãn ở [acceptance-checks.json](acceptance-checks.json), [environment.json](environment.json), [39 source bindings](source-bindings.json). Không cộng evidence Task1/PR47/PR52 vào số test mới.

| Kiểm | Kết quả mới | Nhãn / giới hạn |
|---|---|---|
| CandidateModeSessionTest trước sửa | 8 PASS / 0 FAIL / 0 SKIP | REAL collector/delivery workers, MOCK process readings/transport; chỉ diagnostic, không xóa CI FAIL |
| CandidateModeSessionTest sau sửa | 8 PASS / 0 FAIL / 0 SKIP | Cùng 8 case, không phải 16 case khác nhau; Maven compile/test reactor protocol+client SUCCESS |
| JDK/tool inventory | Java21.0.10, jpackage21.0.10, x64; java/javac/jar/jlink/jpackage có | REAL; JavaFX21.0.12 từ POM, chưa xác minh runtime MSI mới |
| Installer PreflightOnly | BLOCKED ở Resolve-Path WiX | REAL; mt.exe cũng không thấy trên PATH/vị trí chuẩn/portable đã biết, không chứng minh vắng trên toàn ổ đĩa |
| PR52 structure/ancestry | 9 script,4 template,41 ID duy nhất; mọi row NOT RUN; 32 file handoff, không production | STATIC; không chạy lại bộ kit29/29 |
| C5 patch | git apply --check PASS đúng b057b0f, checkout PR51 vẫn sạch | STATIC; chưa áp dụng hoặc push vào nhánh owner |
| Packet kiểm tra | Source/hash/link/PowerShell syntax và diff kiểm riêng khi xuất bản | STATIC, kết quả lưu trong packet-validation.json |

CI55 test tại CandidateModeSessionTest.java:69 thấy PROCESS_OBSERVED PID1 nhưng chưa có PID2. Source gọi observe cho event và state, sau đó hai worker gửi độc lập. Test đợi MONITORING_DELTA được gửi rồi kiểm event ngay, thiếu điều kiện chờ event. Test fix chỉ thêm bounded await cho PROCESS_OBSERVED PID2 trước assertion, vẫn kiểm cả hai PID và mode FULL/DELTA. Không đổi timeout, retry test hoặc production. Một lượt local PASS chưa chứng minh đã hết mọi vấn đề timing; CI của nhánh nghiệm thu cần theo dõi riêng.

Lệnh hai lượt (trước/sau sửa, không chạy full suite):
```powershell
mvn -B -o -pl client -am "-Dtest=CandidateModeSessionTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Raw log mới ở target/release-acceptance trong worktree này, có hash trong acceptance-checks.json. Inventory PowerShell đầu bị lỗi PATH/serialize metadata và đã dừng, log tạm quá lớn do agent tạo đã dọn; inventory cuối thu gọn bằng Python. Đây là lỗi thao tác thu thập, không là product regression. Không chạy PostgreSQL, full Maven, MSI installed runtime, nghe thật, LAN, E1/E2 campaign hoặc load trong Task2. Không tạo schema/cluster/install, không sửa .env/DB/firewall/adapter/registry. Các test worker đã đóng qua fixture.

## 3. MSI và artifact có thật

Đã đọc WINDOWS_INSTALLER, ma trận B, stabilization REPORT/MSI_PROVENANCE/ACCEPTANCE_CHECKLIST. MSI0.3.10 source3702b6d và hash d69a6f5944471bcb2ae2323e953cbb759d1a5e4752f27ed07917aa2df2355881 là **lịch sử**, không chứa PR47. Artifact MSI cũ chưa tìm lại trong output/archive đã kiểm, không nhận hash/installed PASS mới.

Preflight có thật dùng package-windows.ps1 -PreflightOnly -AppVersion0.3.11; dừng vì thiếu WiX portable. Không tải/cài thêm tool hoặc build final từ PR47 chưa merge. Khi đủ gate, dùng [handoff](HANDOFF_B_C.md) để build sạch từ final main, chọn version mới hơn0.3.10 (0.3.11 chỉ đề xuất nếu chưa được dùng), kiểm build-info.sourceCommit/dirty/FULL, manifest UTF-8/asInvoker/DPI, JAR/JavaFX natives/runtime rồi SHA256SUMS. App-image không thay MSI.

ZIP T3C4-3044e5b cần hash 5b04dcb10d46ba760749af29b2abdeee126a9748512abe4d18b78e0d4881685a; **NOT AVAILABLE** trong output/recovery đã kiểm, chưa hash được ZIP này trong phiên. Đã kiểm thư mục root target/jpackage-out, recovery surviving-root-target, C:/ToeicGate và mục lục remaining-targets.zip. Hai app-image cũ ToeicMonitor-T3C4-2b6c9ce.zip / 772c6e9.zip lần lượt hash9e920fba…34c4667 / 9aab5caa…098711; không khớp, không thay thế. Không biết có bản copy ở vị trí khác.

Không đóng lại ZIP hoặc tạo kit mới. B/C cần tìm đúng artifact từ người giữ máy build. Nếu thất lạc thật, đây là lý do kỹ thuật để owner cho tái tạo từ source phù hợp và công bố manifest/hash mới; không giữ checksum cũ cho ZIP mới. Khi final main đổi, kit3044 vẫn chỉ chứng minh source3044/runtime3d93820; cần owner đánh giá khác biệt và version hóa kit nếu cần.

## 4. C5: một báo cáo hiện có, patch chờ owner

Không tạo C5 thứ hai hoặc sửa trực tiếp branch người khác. [C5_PR51.patch](C5_PR51.patch) chỉ sửa docs/MONITORING_SYNC_REPORT_DRAFT.md trên base b057b0fa5c4f99f00dfaa29d49c2030f1e8a5f16. Dùng checkout detached sạch ngoài target để kiểm; checkout và branch PR51 giữ nguyên. Owner kiểm HEAD chưa đổi trước apply. B vẫn phải review sau khi áp dụng.

| Nội dung đối chiếu | Kết quả / patch |
|---|---|
| T3-A3/A4 | ACK/push sau commit, restart đổi instance/epoch, cách ly nhiều client: đã có; giữ nhãn loopback lịch sử |
| PR47 CLOSE/correlation | Sửa mô tả 2b6c9ce/draft/đổi base đã cũ thành3044/3d93820, timeout write/worker/guard/cache có giới hạn |
| RAM ACK và DB ACK | FULL/DELTA RAM khác event/gap/presence DB commit; best-effort CLOSE không bảo đảm server ACK |
| FULL/DELTA epoch/sequence | OPEN epoch mới + FULL trước DELTA; base=current, sequence=current+1; duplicate không làm mới state |
| Reconnect/RESYNC | Bỏ base/capability cũ, fresh scan; giữ last-known khi UNSYNCED; không suy state từ DB history |
| STALE/freshness/TTL | Default6s/300s, worker500ms riêng; heartbeat không làm mới process state; không cam kết SLA LAN |
| History/quyền | ACTIVE/assigned proctor; hơn5000 trả409 INVALID_STATE retryable:false, không truncate; chưa phân trang |
| Known limitations | Thêm P1 cache/main CLOSE, CI55, raw E1 NOT AVAILABLE, E2/LAN/B review pending |
| Evidence | Sửa câu E1 “chưa chạy” thành có lịch sử nhưng raw hiện thiếu; 927/4 là ghép cũ, 928/5 và42/42 là evidence PR47, không tự chạy mới |

Dashboard **chưa thể tải lại toàn bộ lịch sử vượt5000 qua endpoint này**; cần owner xem xét pagination riêng, không đổi contract tại Task2. QD10 body là presence; QD19 là DELTA opt-in/live gate. Bảng mục lục QD cũ còn gán QD10 cho delta nhưng không được dùng để ghi đè quyết định presence.

## 5. Bàn giao và sequencing

[HANDOFF_B_C](HANDOFF_B_C.md) chỉ dẫn tới kit PR52 hiện có và checklist MSI, không thay 41 case. Nhóm F phải là mất LAN vật lý của B trong khi candidate A/proctor vẫn hoạt động; checkpoint, auth/OPEN/epoch/FULL, không false ACK, answers đã ACK giữ, Listening không autoplay, cleanup sở hữu.

[MODE_DECISION_DRAFT](MODE_DECISION_DRAFT.md) dành C xác nhận và B review; không ghi owner decision vào QD hoặc tracker. FULL baseline; không promote DELTA/E2. PR52 chỉ retarget main sau PR47 MERGED và kiểm ancestry/diff/CI/source kit; điều kiện chưa xảy ra nên không retarget hoặc merge vào PR47.

Nhật ký Task2: thêm test wait và packet trên nhánh riêng, giữ mọi nhánh nguồn, báo cáo lịch sử, raw và shared docs. Không approve/merge/close/markReady PR nguồn, không cập nhật kế hoạch/tracker/QD hoặc sửa A/B production. ChatGPT planning/review NOT RUN theo opt-out. Việc tiếp theo thuộc B/C: sửa cache P1, review PR47, cung cấp công cụ/artifact, chạy máy2/LAN và review C5/gate.
