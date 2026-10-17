# Sửa F01 — cờ Listening gián đoạn trong kết quả thi

**F01 FIXED.** Commit sửa riêng: `d19c63902725662aeb6f43c6f04ca80ea6caff42`. Chưa push/merge/approve. Bản sửa giữ nguyên cờ server và cảnh báo đúng cả SUBMITTED/TIMED_OUT; phần nghe thật/LAN/human review chưa nghiệm thu.

| Lượt mới | Kết quả thật |
|---|---|
| Regression trước sửa | 6 ca thực thi, 0 PASS / 6 ERROR vì Result thiếu cờ |
| Targeted sau sửa | 104 PASS / 0 SKIP; trong đó mapping6, session51, history43, GUI4 |
| Maven clean test | 896 PASS / 2 SKIP / 0 failure/error (898 ca) |
| Maven package | 896 PASS / 2 SKIP / 0 failure/error (898 ca) |
| MSI0.3.8 installed FullSuite clean package | 898 PASS / 0 SKIP / 0 failure / 0 error; 898 ca |
| ExamFlow/AT11 | 41 PASS / 0 GAP, REAL HTTP/PG; loss seam SIMULATED |
| Reading/Listening/Application/history/B4/progress/FULL GUI | 11 nhóm PASS, hearing click SIMULATED |
| PostgreSQL concurrency | AT01/AT07/AT09/AT10 PASS |
| Monitoring C1 FULL + GUI | Lượt đầu FAIL cleanup; lượt Windows Temp mới PASS, giữ nguyên FAIL |
| Monitoring C2 event/gap/UNKNOWN + GUI | PASS; process readings MOCK, faults SIMULATED |

Hai SKIP đầu là opt-in app-image/installed chưa có artifact mới. Installed FullSuite đã bật cả hai từ 0.3.8, đếm XML mới sau clean; không kế thừa 876 PASS nguồn cũ. XML archive/counts nằm trong `jpackage-out/f01/<run>-reports` và các file `*-counts.json` được công bố.

MSI cài/gỡ local **PASS**: quyền thường, Unicode/spaces, launcher không agent/JAVA_HOME, PATH chỉ Windows, bundled JVM, install directory chặn ghi, runtime audio/UI, close và uninstall. Test dùng agent ngoài MSI cho các bước tự động; không gọi là nghe thủ công hoặc máy thứ hai. Script lifecycle chạy đúng product TEST và khôi phục ACL. 5439 class entries trong JAR packaged và JAR final giống byte; xem `class-equivalence.json`.


Ngày 16/10/2026. Nhánh cô lập `qa/f01-listening-result-20261016`, base HEAD PR #40 `3fc5bb4e8ef93b60bf479fc52fe725e4b8f072af`. PR #39 HEAD `093429ab1abb19f9c6d37be9b86ae8e00f11a284`; cả hai OPEN/Draft khi kiểm tra qua GitHub và fetch. Worktree mới `target/f01-worktree` trong workspace chính. Không thay nhánh/worktree/file chưa commit cũ.

## Nguyên nhân và sửa lỗi

Server DTO/API đã có cờ. Record `ExamSession.Result` chỉ lưu state/điểm/revision/thời điểm nên bỏ `listeningInterrupted` trong cả `of(SubmitExamResponse)` và `of(CandidateAttemptStatusResponse)`. `ExamResults.open()` gọi mapping status này, vì thế history cũng mất cờ. `CandidateExamView` và callback history chuyển cùng Result vào `ScoreResultDialog`, vốn chưa có cảnh báo.

Thêm boolean vào Result, chép trực tiếp cờ từ hai DTO. Dialog có cảnh báo tiếng Việt và nhãn Listening gián đoạn khi true, vẫn hiển thị trạng thái, thời điểm chốt và số câu đúng server trả. Khi false giữ giao diện bình thường. Không suy từ TIMED_OUT/score và không tự hủy điểm. Không cần thay ExamResults hoặc hai view vì đã đi qua mapping chung.

## File code/test thay đổi

- `client/src/main/java/vn/edu/toeic/client/exam/ExamSession.java`
- `client/src/main/java/vn/edu/toeic/client/exam/ScoreResultDialog.java`
- `client/src/test/java/vn/edu/toeic/client/exam/ExamResultMappingTest.java`
- `client/src/test/java/vn/edu/toeic/client/exam/ExamSessionTest.java`
- `client/src/test/java/vn/edu/toeic/client/exam/ExamHistoryReadTest.java`
- `client/src/test/java/vn/edu/toeic/client/exam/ScoreResultDialogTest.java`
- `server/src/test/java/vn/edu/toeic/server/audio/ListeningRuntimePostgresTest.java`

Phần B là production; chỉ bổ sung test trong phần A, cần A review. Không đổi backend production, DTO wire, giao dịch, QD-29, monitoring policy/FULL, dependency hoặc tracker. Không ký duyệt thay A/C.

## Regression trước/sau

Trước sửa, chỉ thêm `ExamResultMappingTest` vào source PR40: biên dịch được, thực thi 6 ca, **0 PASS / 6 ERROR**, đúng lỗi không tìm được thuộc tính `listeningInterrupted` trên Result. XML và log gốc lưu ngoài target trước clean. Hai lượt khởi chạy đầu BLOCKED do môi trường Windows/WSL (PATHEXT thiếu `.EXE`, Surefire không tìm được java); giữ log riêng, không gọi đó là tái hiện F01. Lượt tái hiện thật đã chuẩn hóa PATHEXT và temp TEST.

Sau sửa: 6 mapping PASS, nhóm targeted 104 PASS/0 SKIP. Mapping dùng cùng điểm 735 và số đúng 7/10 ở cả true/false để xác nhận không suy cờ hoặc sửa điểm. Session kiểm submit ACK và mất ACK rồi status, history HTTP fixture kiểm chỉ một GET/status, không takeover/paper/collector. GUI JavaFX thật kiểm 4 tổ hợp trạng thái/cờ, cảnh báo/nhãn/điểm/nút thoát; ảnh MOCK result được kiểm trực quan.

Test PostgreSQL mới kiểm 4 tổ hợp SUBMITTED/TIMED_OUT × true/false: Listening event thật qua HTTP, client ExamSession submit thật hoặc timeout service thật, đọc status/history thật, đối chiếu toàn bộ điểm/revision/thời điểm; `row_to_json(attempt)` trước/sau history giống hệt, writerEpoch vẫn 1.

## Môi trường và nhãn

Windows x64 native, Oracle JDK21.0.8, Maven3.9.11, JavaFX21.0.12. Docker daemon đang tắt, không khởi động service dùng chung. Dùng binary PostgreSQL **18.1** đã có để tạo cluster TEST mới trong thư mục ignored, listen riêng loopback/cổng tự chọn, schema theo UUID từng harness. Không kế thừa kết quả PG18.6/876 PASS của bản cũ.

REAL: JavaFX controls/Stage, HTTP/WS, Spring/Flyway/PG, native runtime/installer nếu lượt thực chạy thành công. MOCK: DTO/gateway/unit/history HTTP fixture và dữ liệu process fault theo harness. SIMULATED: mất ACK, click xác nhận/human hearing/socket seam tự động trong harness cũ. Không dùng chúng để kết luận production/LAN/nghe thật đã PASS.

## Cách đưa patch vào PR

Ưu tiên maintainer cherry-pick commit sửa F01 và commit hồ sơ bàn giao vào nhánh PR #40; sau đó đưa PR40 vào PR39 một lần. Không cherry-pick cùng patch riêng vào cả hai nhánh rồi lại nhập lại. Nếu chọn áp patch trực tiếp PR39 thì phải thống nhất với owner PR40 trước để chỉ đưa F01 một lần. Agent chưa push/tạo PR/merge/approve/đóng PR nào.

Lệnh đưa riêng bản sửa vào nhánh PR40, do maintainer thực hiện khi được phép:

```bash
git cherry-pick d19c63902725662aeb6f43c6f04ca80ea6caff42
```

## Source, bộ cài và giới hạn

Commit code/test: `d19c63902725662aeb6f43c6f04ca80ea6caff42`. Các lượt clean test trước commit dùng đúng patch này; package, harness và MSI dùng commit này. Commit bàn giao sau chỉ chứa hồ sơ/evidence, không được tính là một lượt test mới.

MSI **0.3.8** build mới bằng `scripts/package-windows.ps1 -AppVersion 0.3.8 -Offline`, không AllowDirty. Source sạch `d19c639...`; WiX3.14.1.8722, SDK mt10.0.26100.6584; Java21.0.8/FULL mặc định. Artifact local `jpackage-out/f01/release-0.3.8/ToeicMonitor-0.3.8.msi`. SHA256 `df17a3fa168579ccd222cfc1429599277b4e05ec7e8f71b017ef6bd91faefc49`. Client JAR gốc trong MSI/image: `da277c9048eb983776c2a8fd2058bd898293713a3baca394a924ff99ef630c2b`. Không dùng MSI0.3.7 làm bản cuối.

C1 FULL lượt đầu FAIL ở `EDGE_PROFILE_CLEANUP / FileSystemException`, schema/server đã dọn; kiểm sau đó không còn Edge mang đường dẫn profile TEST và không tìm thấy file còn khóa. Giữ nguyên stdout/stderr/metadata lượt đầu. Chỉ đổi thư mục `java.io.tmpdir` của lần chạy TEST sang thư mục UUID trong Windows Temp, không sửa collector/harness/policy; lượt mới PASS đầy đủ Edge thật, reconnect/epoch/duplicate/empty/scope, GUI và autonomous STALE/TTL. **Nguyên nhân chính xác của FAIL cleanup đầu chưa được chứng minh**, không xóa FAIL hoặc xem việc chạy lại là đã sửa một lỗi monitoring.

84 file raw E1 lịch sử được đối chiếu lại SHA256, giữ nguyên; không chạy lại E1/E2, không sửa analyzer/metric hoặc bằng chứng lịch sử. Xem `e1-integrity.json` mới và manifest lịch sử nguyên trạng.

Rủi ro còn lại: C cần xem độ ổn định cleanup C1; A review B mapping/UI và test đặt trong server. Nghe thủ công, DPI rộng, physical LAN/máy Windows thứ hai và human A/C review **NOT RUN**. Lượt PostgreSQL mới dùng18.1, không gọi là18.6. Những giới hạn này không thay đổi cờ/điểm/kết quả server hoặc biến test MOCK/SIMULATED thành nghiệm thu production.

## Chạy lại

Cấp `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD/TOEIC_SEED_PASSWORD` qua environment của **PostgreSQL TEST cô lập**, không ghi vào source/log. Dùng JDK21, Maven3.9.11 và dependency cache phù hợp. Trên WSL gọi Windows, chuẩn hóa PATHEXT Windows để Surefire tìm được `java.exe`; ghi raw ra `jpackage-out/f01` trước mỗi clean.

- `TOEIC_PG_INTEGRATION=true`, `TOEIC_PROCTOR_GUI=true`: `mvn -B -o "-Dmaven.repo.local=$env:USERPROFILE/.m2/repository" clean test`, rồi `package`.
- Targeted: thêm `-pl protocol,client -Dtest=ExamResultMappingTest,ExamHistoryReadTest,ExamSessionTest,ScoreResultDialogTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- AT11: chạy `vn.edu.toeic.client.exam.ExamFlowPostgresSmoke` với classpath `client/target/test-classes;client/target/client-0.1.0-SNAPSHOT-all.jar;<PostgreSQL JDBC jar>`.
- GUI: cùng classpath chạy `vn.edu.toeic.client.integration.ReadingListeningPostgresGuiSmoke`, `-Dintegration.output=<output mới>`.
- Concurrency/C1/C2: các main lần lượt `vn.edu.toeic.server.exam.ExamConcurrencyPostgresSmoke`, `vn.edu.toeic.server.monitoring.FullSnapshotSmoke`, `vn.edu.toeic.server.monitoring.LateEventsAndGapsSmoke`, dùng Boot `PropertiesLauncher`, `loader.main` tương ứng, `loader.path` chứa đúng các smoke class/inner class đã copy và client shaded JAR cho C1/C2; C1/C2 thêm `--gui`. C1 dùng thư mục Windows Temp TEST riêng, heartbeat200ms; hai luồng đo dùng runId/output riêng. Xem scripts smoke hiện hữu để đối chiếu cấu hình; không cần sửa chúng.
- Build MSI: `scripts/package-windows.ps1 -JavaHome <JDK21> -WixHome <WiX3> -ManifestTool <mt.exe> -AppVersion 0.3.8 -Offline`.
- Installed FullSuite: đặt `TOEIC_PACKAGED_CLIENT=<image 0.3.8 mới>` rồi chạy `scripts/smoke-windows-installer.ps1 -Installer <MSI mới> -JavaHome <JDK21> -FullSuite`. Script từ chối thay app đã cài của người dùng và tự khôi phục ACL/gỡ đúng product TEST.

Raw logs/XML/smoke metadata giữ ignored local trong `jpackage-out/f01`; logs installer/pipeline trong `target/t3-b5`. `RAW_LOG_INDEX.json` ghi hash và vị trí thật. Counts lấy từ XML của từng lượt đã archive riêng, không lấy con số từ báo cáo source cũ. Ảnh dialog trong `images/` là GUI thật với **MOCK result**, không chứng nhận nghe thật.
