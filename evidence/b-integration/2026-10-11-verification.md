# Vai B — ổn định PR #17 với main C1–C5, 11/10/2026

Local integration branch `stabilize/pr17-main-20261011`, worktree riêng `Toeic-Monitoring-Realtime-b-stabilize`; base PR17 `41da669703a53581359ebaac1f90544c7c09aa45`, main fetched `12e28386169c42b88883f23a5053baeb8fb0c3c9`. PR17 vẫn open/unmerged; PR9 closed/unmerged HEAD568be80. GitHub trả mergeable=false cho PR17 trước tích hợp, reviews và commit-status lists rỗng; không suy ra thiếu/đủ branch protection từ những list đó. Chưa push, chưa cập nhật PR/GitHub review, chưa merge main/auto-merge.

## Bảo vệ local và phạm vi

Workspace gốc đang main82557bf (behind3), ba untracked file `scripts/launcher-manifest.ps1`, `scripts/test-launcher-manifest.ps1`, `task.txt` được giữ nguyên byte/hash. Nhánh B4 local568be80 không bị checkout/cherry-pick/reset. Worktree PR17 cũ tại `Toeic-Monitoring-Realtime-pr17` sạch khi kiểm qua git-dir/work-tree tường minh; metadata gitdir do Windows ghi bị WSL list đánh dấu prunable. Không prune/repair/xóa worktree đó; dùng worktree mới và nhánh local mới từ remote PR17 để không đổi index/HEAD của workspace cũ. Không stash/clean/xóa dữ liệu local, không đọc/in credential .env.

Không sửa backend/protocol/runtime/migration/parent POM so với main12e2838. Không dependency mới; không T2-B5, Listening, delta hoặc benchmark. Phần server/C được đưa vào bằng merge main, không viết lại thay A.

## Tích hợp và sửa lỗi client

Merge main bằng `--no-ff`, giữ hai parent. Bảy conflict: README.md, ProctorDashboardView.java, docs/KIEM_THU.md, NHAT_KY.md, PROTOCOL.md, QUYET_DINH.md, TIEN_DO.md. Dashboard giữ imports Gap/full, cột deliveryStatus, bốn tab, current/gap render và cleanup C; đồng thời giữ seeder/sample action/theme/ExamApiClient lifecycle B. Tài liệu giữ history/evidence hai phía. QD12/13 của C giữ nguyên; hai quyết định B đổi QD16/17 và sửa reference đang dùng để tránh trùng; evidence cũ không sửa.

- Gỡ mật khẩu demo từ ba quick-account buttons: chỉ chọn username, xóa password và focus để người dùng nhập. Gỡ password fallback từ hai manual smoke client. Security scan source và final client JAR không thấy giá trị demo cũ; test harness không lọt production JAR.
- Luồng B cũ có thể giữ collector A nhưng vào exam B, hoặc nuốt exception khi start giám sát và tiếp tục ExamEntry. Fresh `/auth/me` nay bắt buộc trước entry; guard kiểm role/scope và cùng active attempt; không tự chế scope từ selected; reuse cùng collector không update scope khi C đang chờ FULL/event ACK. Start failure chặn entry. Selection/refresh bị khóa khi active; Stop chờ worker future ngoài FX thread trước mở lại lựa chọn. Callback entry dùng view/request/session generation để bỏ callback cũ.
- Thêm6 `ExamMonitoringIntegrationTest`: wrong attempt, same attempt với OPEN chưa ACK, fresh scope bị revoke, whole server scope khi start, proctor gate, start failure. REAL collector/full/event workers, MOCK scope/readings/transport; cleanup assertions lease/listeners/worker. Lỗi được phát hiện khi review nguồn tích hợp; không bịa đã tái hiện full GUI/DB của lỗi trước sửa.
- Thêm manual `ClientIntegrationGuiSmoke` dùng JavaFX controls thật, gateway/WS state fixture. Không có test UI chạy ngầm trong suite JUnit mặc định.

## Lượt chạy thật

Windows native Oracle JDK21.0.8/Maven3.9.11 từ WSL, source chưa commit trong lúc merge. [Run metadata và hashes source/JAR](run-metadata.json) ghi đúng dirty/in-progress; merge commit về sau không được giả là SHA đã chạy lúc đó. Không đổi Java source sau build cuối. Log có normalize LF/trim trailing whitespace và thay prefix workspace/user profile; original hash và sanitized hash lưu riêng.

| Kiểm tra | Kết quả | Artifact/phạm vi |
|---|---|---|
| Fresh worktree Maven package, lượt đầu | PASS565, 5module | Suite C493 + B66 + guard6; build trước thêm manual smoke class |
| Final `mvn -B package` | **PASS565**,0 failure/error/skipped, 5module | [Full sanitized transcript](maven-package.txt), [Surefire counts](java-test-counts.json): protocol32/client390/server142/spike1; final manual smoke class được compiler kiểm. package chạy tests, không có lượt mvn test riêng |
| Python analyzer | **PASS10** | [Transcript](python-tests.txt), không E1/E2 |
| Login controls/credential | **PASS REAL FX** | Cả ba shortcut xóa password đang nhập; không login server. [GUI component log](gui-component.txt) |
| CandidateExamView + ExamSession | **PASS REAL FX** | Chọn đáp án→autosave ACK; RECONNECTING khóa; CONNECTED→status reconciliation→unlock→lưu; close trước ACK không render muộn. Gateway/WS state MOCK/SIMULATED; không PostgreSQL/socket |
| ProctorDashboardView | **PASS REAL FX** | Tab current/event/interruption/gap và action B còn; close dọn owned workers. DISCONNECTED fixture, không HTTP/WS/DB |
| Realtime/full/dashboard/ExamSession regressions | **PASS trong565** | RealtimeClient54, FullSnapshotDelivery7, full-state dashboard4, late/gap3, ExamSession43/threading1, API14/paper-entry8, guard6. AuthenticatedNetworkTest36 dùng Spring/Tomcat/socket thật + MOCK stores; không DB acceptance |
| C1/C2/C3/C4 ledger integrity | **PASS104 file**,3gzip restoration | [Checks](evidence-integrity.json):13+14+22+27+28. C5 report/doc audit đọc đối chiếu; không nâng runtime acceptance |
| C4 archived raw join | **PASS byte-identical** | [CLI](c4-join.txt),27/3/0 như report cũ; không tạo30child mới, không E1 |
| C3 handwritten fixture replay | **PASS REPLAY** | [CLI](c3-replay.txt), sourceMOCK/faultSIMULATED, independent oracle |
| C3 full mutation | **EXPECTED FAIL/exit1** | [CLI](c3-mutation-expected-fail.txt), oracle phát hiện state/event mismatch; không product regression chưa xử lý |
| Client production JAR | **PASS** | Hash final trong checks/metadata; demo password cũ và manual/unit harness entries0 |
| Fresh PostgreSQL C1/C2/exam smokes | **NOT RUN** | Docker Desktop Linux engine named pipe không tồn tại; không reset/shared schema hoặc khởi động DB để giả bằng chứng |
| Full production GUI/human acceptance/LAN/second-machine app-image/audio | **NOT RUN** | Component fixture không thay toàn luồng |
| E1/E2/performance/delta | **NOT RUN** | Công cụ/contract/correctness chưa đủ, không thêm implementation |

Final package có shade/module-info overlap, JavaFX unnamed-module, Mockito dynamic-agent/CDS warnings; chúng không làm fail tests/package. Component smoke không có failure; nguyên nhân của failure lịch sử C4 concurrent event vẫn chưa chứng minh, giữ original evidence.

## Review C và phần còn thiếu

[Review B source/docs/evidence](../../docs/REVIEW_B_MONITORING.md): policy8/TEST, identity/PID reuse/quality, epoch/sequence/full RAM, frozen event origin/commit ACK/gap, collector/trace/transport lifecycle và harness ownership phù hợp mô tả C5. Không thấy blocker mới của C ngăn tích hợp; giới hạn giữ nguyên. Reviewer là agent Vai B, human B/A approval NOT RUN, chưa sign-off E1/E2. Trace COMPLETE cần writer finished/FINAL/checksum riêng; collector stop future không chứng minh flush. Kernel latency/CPU/RSS và Ctrl+C/hardkill harness parent chưa nghiệm thu.

Blocker A vẫn còn trong source: (A1) owner đọc status/exam lượt chốt bị guard ACTIVE403, (A2) ExamTimeoutService Scheduled chưa được kích hoạt, (A3) deadline chỉ đặt khi autosave đầu. Không fix backend; chưa gọi smoke DB cũ28PASS+3GAP là kết quả mới.

[Checklist candidate→proctor/LAN và điều kiện merge](../../docs/NGHIEM_THU_VAI_B.md) kèm đề xuất B4 riêng: stage chỉ production JAR, port source đã đối chiếu, xử lý manifest UTF-8, smoke image mới và audio TEST riêng, kiểm máy thứ hai không IDE/JDK/JRE + LAN + người thật nghe. PR9 cũ không được trộn trực tiếp; helper chưa commit được giữ nguyên. Không chạy package/audio trong lượt này.

Ready to push bản ổn định: sau user grant và recheck remote41da669. Ready to merge PR17 như luồng thi đã nghiệm thu: **NO** — cần A xử lý3blocker, smoke production/GUI sau fix, A review và GitHub required checks/reviews nếu có. LAN/package status phải ghi rõ. Agent không được merge main. Git HEAD cuối và parent ghi trong báo cáo final; không đặt SHA tương lai vào báo cáo trước commit.

## Tái chạy

Tại worktree, `mvn -B package`; `python -m unittest discover -s scripts -p 'test_*.py' -v`. Manual FX smoke (TEST classes không vào image):

```powershell
java '-Dtoeic.exam.autosaveDebounceMillis=100' -cp 'client\target\test-classes;client\target\client-0.1.0-SNAPSHOT-all.jar' vn.edu.toeic.client.exam.ClientIntegrationGuiSmoke
java -cp client\target\client-0.1.0-SNAPSHOT-all.jar vn.edu.toeic.client.monitoring.harness.HarnessCli join --input-dir evidence\t2-c4\raw --output client\target\b-review-new-join.json
java -cp client\target\client-0.1.0-SNAPSHOT-all.jar vn.edu.toeic.client.monitoring.trace.TraceCli replay --input client\src\test\resources\monitoring-traces\handwritten-v1.jsonl --output client\target\b-review-new-replay.json
```

Output của CLI phải mới. Sau Docker/DB sẵn sàng mới chạy exam/C1/C2 production smokes tuần tự; manual client smokes yêu cầu TOEIC_SEED_PASSWORD từ môi trường, không fallback. Không dùng TEST trace C4 làm production replay input.
