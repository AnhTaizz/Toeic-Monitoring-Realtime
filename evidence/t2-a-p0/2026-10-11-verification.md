# Backend P0 — kiểm chứng ngày 11/10/2026

Agent Vai A chạy trên Windows/WSL, Oracle JDK21.0.8, Maven3.9.11, Spring Boot4.1.1, **PostgreSQL18.1 native Windows**. Đây không phải PostgreSQL18.6 Docker của lượt B cũ. Docker Desktop không chạy; dùng cluster test mới riêng, chỉ listen127.0.0.1:56261, DB `p0_test`, credential TEST sinh lúc chạy. Không đọc/reset DB dev/public của nhóm. Không đưa password/token vào evidence.

## Git và nguồn đã kiểm

- Fetch đầu/cuối: main `12e28386169c42b88883f23a5053baeb8fb0c3c9`, PR17 `2f5fc66d1220f1e860f7c3beced86ef3f294ac46`.
- Nhánh A: `fix/backend-exam-p0-20261011`, worktree `Toeic-Monitoring-Realtime-a-p0`, từ origin/main. Chưa push/tạo PR/merge.
- Commit nhóm quyền `6c2a5d0`; scheduler `6ec3213`; deadline/runtime regressions `f175d63`; assertion và chứng minh hai bên chờ khóa `92027d7`; C1 harness tương thích scheduler `4291a4e`.
- Maven test/package A và package tích hợp chạy production code `92027d7413cb7ca71f683fdfd961b0c6a3edc742`. Sau đó chỉ sửa C1 **manual test harness**, compile bằng javac21 và chạy thật; code production không đổi. Docs/evidence commit sau kiểm thử. [Manifest Java](a-java-source-hashes.json), [metadata](metadata.json) ghi nguồn; không gán log cũ cho HEAD docs mới.
- Worktree tích hợp detached `Toeic-Monitoring-Realtime-ab-p0`, base PR17 `2f5fc66`, cherry-pick4 commit code/tests A không conflict, HEAD khi build `e9337791a706da54b76deec172ce212d445c913c`. Client production giữ nguyên PR17; patch test-only ghi riêng bên dưới. Không sửa branch/main/worktree B4 hoặc file chưa commit của nhóm.

## Root cause, thay đổi và ba GAP

| GAP của B | Nguyên nhân | Thay đổi | Kết quả REAL |
|---|---|---|---|
| 1. GET final403 | Status/exam dùng scope chỉ ACTIVE | AttemptScopeAuthorizer thêm canReadExam default deny; JDBC chỉ cho ACTIVE/SUBMITTED/TIMED_OUT + owner/assigned proctor; AuthorizationService/ExamService dùng gate đọc riêng | PASS owner và assigned proctor đọc final; foreign/unauth/revoked assignment bị chặn |
| 2. Job không chạy | Không có EnableScheduling/annotation processor trong runtime | ExamSchedulingConfiguration bật annotation; ExamTimeoutService dùng examTimeoutScheduler riêng; default1000ms, opt-out rõ ràng | PASS runtime tự TIMED_OUT, không gọi processTimeouts bằng tay; JAR của B smoke cũng tự chốt |
| 3. Chưa có đáp án thì vô hạn | Deadline chỉ được đặt trong autosave | Takeover đầu khi entry đặt deadline bằng DB clock sau row lock, trả deadlineAt đã có; takeover sau giữ nguyên | PASS bài trắng tự hết giờ; hai HTTP caller mô phỏng hai máy nhận cùng deadline; reconnect/takeover/save không reset |

Timeout ghi `submitted_at` là giờ server chốt, chấm `answers_json` đã commit; khóa/kiểm lại ACTIVE/deadline, không chấm lại lượt SUBMITTED/TIMED_OUT. Chưa vào thi thì deadline null; GET không bắt đầu thi. Legacy save-first vẫn tương thích. Không đổi API/DTO/wire/migration. Chi tiết [review A/B](../../docs/REVIEW_A_PR17.md), [PROTOCOL](../../docs/PROTOCOL.md), [giao dịch](../../docs/GIAO_DICH_VA_QUYEN.md).

Framework: annotation cần EnableScheduling và hỗ trợ chọn scheduler theo tên bean; cấu hình áp dụng cho exam, C không có annotation task mới. [Spring Scheduled API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html).

## Tests thực sự chạy

| Kiểm tra | Kết quả | Evidence |
|---|---|---|
| A `mvn -B test`, PG integration bật | **PASS510**, failure/error/skipped0 | [log](mvn-test-final.log) |
| A `mvn -B package`, PG integration bật | **PASS510**, 5modules, package thành công | [log](mvn-package.log), [counts](a-test-counts.json) |
| PR17+A code `mvn -B package`, PG integration bật | **PASS582**: protocol32/client390/server159/spike1, skipped0 | [log](mvn-integrated-package.log), [counts](integrated-test-counts.json) |
| ExamRuntimePostgresTest | **PASS11** trên server production Spring/HTTP/PG; tạo/xóa schema riêng mỗi run | Trong cả ba log Maven cuối |
| Unit gate đọc và scheduler lifecycle | **PASS6**; annotation tự gọi job, explicit disable không tạo worker | Trong Maven logs |
| B ExamFlowPostgresSmoke với JAR thật | **PASS28, GAP0**; GUI và WS NOT RUN trong smoke này | [log](b-exam-flow-smoke-final.log) |
| C1 FullSnapshotSmoke, heartbeatTEST200ms | **PASS**: real Edge/ProcessHandle, full/ACK/RAM, reconnect/epoch/dedup/conflict/history, autonomous STALE/TTL/capacity và shutdown | [log](c1-smoke-final-configured.log) |
| C2 LateEventsAndGapsSmoke | **PASS**: PG/HTTP/WS/collector worker; readings MOCK, outage/write/ACK loss SIMULATED | [log](c2-smoke-args.log) |
| Python analyzer unittest | **PASS10** | [log](python-tests.log) |
| Full candidate→proctor GUI, người thao tác/review, LAN, app-image/audio máy thứ hai | **NOT RUN** | Không suy từ smoke HTTP hoặc component tests |
| E1/E2, Listening, delta | **NOT RUN** | Không thuộc lượt P0 |

Runtime tests kiểm ACTIVE/SUBMITTED/TIMED_OUT, CLOSED/INTERRUPTED fail closed, unknown/foreign/401, assignment bị thu hồi, ACTIVE scope C không mở rộng, exam không lộ correctOption. Concurrent entry cấp epochs2/3 cùng deadline; reopen epoch4 giữ deadline, epoch2 bị STALE. Bài trắng có score0/2, savedRevision0, answers rỗng. Timeout chỉ tính revision đã ACK, không tính request invalid/unsubmitted. Retry submit cùng payload trả cùng kết quả; foreign/role/unauth không được đọc cache, đổi payload CONFLICT, late save/new submit INVALID_STATE, DB chỉ một submit record.

Race PostgreSQL có **hai query FOR UPDATE thật cùng chờ lock**: HTTP submit và job scheduler tự chạy, xác minh bằng pg_stat_activity/Lock; test giữ row qua deadline thật rồi commit. Submit nhận EXPIRED/INVALID_STATE tùy thứ tự, scheduler chốt TIMED_OUT theo answers đã lưu; không có submit record. Không gọi processTimeouts trực tiếp trong integration test. Context close xác minh exam-timeout, presence-timeout và state-maintenance dừng trước drop schema.

## Lỗi exploratory và fixture đã chỉnh

- Maven đầu **FAIL** do3 assertion của test mới dùng introspection `status` trong khi exception có method status(). Đã sửa gọi method trực tiếp; test/package cuối PASS. [Log đầu](mvn-test.log) được giữ, không ghi PASS cho lượt đó.
- C1/C2 lần đầu chưa vào main do classpath dài khi gọi native Java qua WSL. Dùng Java @argfile với path Windows/forward-slash. Các launcher-error logs được giữ riêng.
- C1 lượt chạy heartbeat mặc định2000ms trong khi harness presence1200ms: FAIL RECONNECT rồi FAIL kiểm heartbeat còn sống ở STALE. Chạy lại với `-Dtoeic.realtime.heartbeatMillis=200` PASS. Production heartbeat2s/presence6s giữ nguyên; không sửa bằng cách nới contract production. C1 assertion “không có annotation processor” cũ được đổi thành “chỉ1 annotation job exam, scheduler riêng”; worker C vẫn tự STALE/TTL như kiểm trước.
- B smoke cũ assert deadline null ở entry. Fixture tạm đổi thành deadline đã có, và chờ **confirmedRevision1**: queue Probe còn initial SAVED/revision0, trước kia deadline null vô tình lọc view này. Sau deadline có ngay, thiếu kiểm revision làm smoke kết luận sai score. Không sửa ExamSession/client production.
- B cleanup cũ phụ thuộc docker.exe/toeic-db nên không dọn được schema trên cluster native. Lượt exploratory đã xóa đúng schema sở hữu bằng kết nối cluster test ([log](cleanup-owned-b.log)); fixture tạm dùng JDBC cùng DB + tên schema kiểm regex. Lượt cuối cleanup PASS.
- [Patch fixture PR17](pr17-smoke-fixture.patch): chỉ manual test source trong worktree tích hợp; cần B đối chiếu/nhận patch khi tích hợp, không tự đưa vào branch B.

## Chạy lại PostgreSQL regression và smoke B

Chuẩn bị PostgreSQL **test** đang chạy và user có quyền CREATE/DROP schema trong DB test. Không `docker compose down -v`, không Flyway clean/public reset. Cấp DB_* và TOEIC_SEED_PASSWORD qua môi trường lúc chạy, không ghi mật khẩu vào source/JAR. Cluster của lượt này đã được dừng khi xong; muốn tái dùng cần start đúng cluster test hoặc chọn DB test khác.

```powershell
# Điền host/port/name/user của DB test đang chạy.
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '<test-port>'
$env:DB_NAME = '<test-db>'
$env:DB_USER = '<test-user>'
$dbSecret = Read-Host 'Password DB test' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbSecret).Password
$seedSecret = Read-Host 'Password seed TEST' -AsSecureString
$env:TOEIC_SEED_PASSWORD = [System.Net.NetworkCredential]::new('', $seedSecret).Password
$env:TOEIC_PG_INTEGRATION = 'true'
mvn -B test
mvn -B package
```

ExamRuntimePostgresTest tự tạo schema `t2a_p0_<UUID>` và drop đúng schema đó trong finally/AfterAll. Không đặt `TOEIC_PG_INTEGRATION=true` thì11 PG cases **SKIPPED**, không được báo PG PASS. Đây là cơ chế tránh truy cập DB ngoài ý muốn ở build mặc định.

Trong worktree **tạm** từ PR17, tích hợp commit A rồi áp patch fixture test ở trên, `mvn -B package`. Classpath smoke gồm client test-classes/classes, protocol classes, Gson và PostgreSQL driver (có thể lấy java.class.path từ server surefire XML, thêm client test-classes; dùng @argfile nếu dài). Chạy từ root worktree để smoke tìm đúng server JAR:

```powershell
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp $testClasspath `
  vn.edu.toeic.client.exam.ExamFlowPostgresSmoke
```

Smoke B phải in `28 checks PASS, 0 GAP phía server` và cleanup schema thành công. C1 khi chạy standalone cần heartbeat200ms cho timeoutTEST1200ms:

```powershell
java -Dtoeic.realtime.heartbeatMillis=200 -cp $serverTestClasspath `
  vn.edu.toeic.server.monitoring.FullSnapshotSmoke
java -cp $serverTestClasspath vn.edu.toeic.server.monitoring.LateEventsAndGapsSmoke
python -m unittest discover -s scripts -p 'test_*.py' -v
```

## Kết luận nghiệm thu

**Ba GAP backend PASS trên code local đã tích hợp thử với PR17.** QD16/QD17 phù hợp server, không sửa quyết định B. Monitoring C đã hồi quy kỹ thuật PASS; không gọi E1/E2/human/GUI/LAN PASS.

PR17 remote vẫn chỉ có `2f5fc66`, chưa chứa các fix A, chưa human review/merge. Cần xác nhận push/tạo PR A → review backend → tích hợp thay đổi và fixture vào B → chạy lại GUI candidate/proctor, server-off/reconnect, LAN/app-image/audio máy thứ hai. T2-B3/B4 giữ PARTIAL; chưa kết luận PR17 đủ điều kiện merge hay nghiệm thu toàn hệ thống.
