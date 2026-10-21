# MON-01–MON-09 integrated TEST — report 21/10/2026

**TECHNICALLY_VERIFIED_TEST_SCOPE_WITH_LIMITATIONS.** Không MON DONE, human approval, hardware acceptance hoặc gate DELTA PASS. User authorization attachment100e8713 cho phép implementation/review kỹ thuật/publish/merge commit nếu CI và repository rules đạt; không ký thay A/B/C.

## Bản được kiểm

- Base main `65fa83b73d1c11ffc2257345cb94359cf4521cdf`, đã fetch; worktree/branch `test/monitoring-integrated-20261021`. Các PR EX/UI chưa merge không được nhập.
- Source/build/test cuối `dffa22729434d3d95d67c225ebd035d24d1b3b8d`, dirty=false. Runtime thay đổi lần cuối `51e65dc`; những commit sau chỉ test/diagnostic và cô lập source TEST theo owned handle. Main/merge/publication HEAD thực tế được ghi trong PR, không gán JAR này cho SHA merge mới.
- Windows NT10.0.26200.0, Temurin21.0.10+7, Maven3.9.15, JavaFX21.0.12, Spring Boot4.1.1, PostgreSQL18.3, host PowerShell5.1.26100.9711. Diagnostics provider sau close là UNKNOWN, không suy privilege/runtime từ host shell.
- Cùng reactor classpath/output cho candidate, proctor, candidate2; Spring chạy trong JUnit, không tuyên bố đã launch packaged server/client JAR. Sau đó `mvn verify` tạo bốn JAR mới; **674 file class/resource** bằng byte với output biên dịch, bridge TEST/CIM không vào runtime client/server. [Receipt/hashes](receipt.json); JAR/raw lớn chỉ local ignored, không có link tải GitHub giả.
- [Ma trận MON01–09, owners/methods, production opt-in vs TEST, runbook và thiếu nghiệm thu](../../docs/MONITORING_INTEGRATION.md).

## Actual cùng nguồn

| Lượt trên source cuối | Kết quả | Actual / provenance |
|---|---|---|
| Integrated Mock `usb-f60ec7dfbf3549eb` | 4/4 PASS, 0 skip | 32 typed committed + 1 process event ở first run; 11 cursor pages; 34 drop thật do burst50 SIMULATED; summary chọn 21 inputs (typed cap20 + process1) |
| Integrated Real-USB `usb-ba1001d26a52496c` | 4/4 PASS, 0 skip | 6 scan CIM, last health READY/count0; 27 typed +1 process, 9 pages, 34 deliberate drop; không USB transition vì count0 |
| App controls, nằm trong mỗi lượt trên | PASS | proctor0 collector factories; candidate A Start–Stop–Start → logout → candidate B → Application.stop;3 runs/3 terminations; process exit làm current0, history giữ2 events của hai runs |
| Production opt-in lifecycle, cùng fixture | PASS | SUBMIT, TIMEOUT thật qua business API/deadline; admin token revoke fixture SIMULATED nhưng WS denial/stop thật; ACTIVE-only signals/summary403 sau terminal, không nới quyền |

Process dùng `ProcessHandleSnapshotSource` thật với supplier seam chỉ owned PID **trước** metadata. Workload là cmd tự sở hữu có filename chrome.exe để đi qua policy không đổi, không browser thật. JavaFX Screen báo1 màn; focus/window/display/activity dùng implementation có sẵn, Stage/marker activation programmatic. USB connect/remove ở Mock, CIM chỉ minimal snapshot trong Real; tài khoản/đề đều TEST/MOCK. Burst/delivery faults là SIMULATED, không phải thao tác người dùng. Không gọi cả mode Real là REAL toàn hệ thống.

Một typed context/observer ID trùng lease collector, một sequence/queue/RT chung cho USB/focus/window/display/activity/status/gap; process delivery và current FULL riêng. Có unique event/client-observation sequence, fixed-through page3 oracle, history/raw time fields, timeline sort, server summary/window/cap riêng. Không ép raw32 và summary21 phải bằng nhau. Bộ lọc PROCESS không che cap/gap/unavailable coverage; READY lịch sử không chứng minh nguồn còn khỏe. FULL snapshot ACK xác nhận RAM, process/typed history ACK sau DB COMMIT.

Stop/restart đổi collector và FULL epoch, worker cũ terminated trước run mới; callback cũ không sửa run mới. Reconnect là disconnect/connect ứng dụng loopback, fresh scoped heartbeat ACK/capability trước gửi lại; USB coordinator không tự resume. Candidate khác bị403, assignment revoke ở A xóa dữ liệu chọn và API B vẫn trả1 row. Không gọi localhost là LAN hoặc giả lập mất cáp.

Pending/drop được giữ đúng nghĩa: first run quiesced/drained có0 unconfirmed discard. Restart ở lượt Mock bỏ 3 bản ghi chưa xác nhận khi close; token revoke bỏ3 typed chưa xác nhận ở lượt Mock. Đây là **unconfirmed**, không tự coi mất DB hoặc ACK đủ; chi tiết cả hai mode trong receipt. Không flush bù sau revoke, không chuyển gap qua run/attempt khác.

## Lỗi có bằng chứng và sửa nhỏ

1. **Status owner xung đột:** display đã claim status, USB coordinator tự claim lần hai → `IllegalArgumentException: Fresh matching signal run required` tại start. Regression FAIL ở runtime65fa83b + fixture patch. `ObserverStatusEmitter.usesContext` và overload USB mượn exact context/delivery/generation; close USB không stop owner chung. One-owner guard giữ nguyên, không tạo queue/source mới.
2. **Borrowed owner retire:** regression riêng FAIL vì USB scan chờ không dừng khi emitter mượn bị retire. `laneReady` kiểm owner/generation xuyên vòng đời, async cleanup source/readers/workers; regression sau sửa nằm trong full suite. Default constructor vẫn own emitter; bốn regression standalone cũ kiểm tương thích.
3. **Scope-loading UI guard:** latch giữ initial `/auth/me` chứng minh Start vẫn enabled khi response còn giữ selection cũ. Sửa5 dòng trong `ToeicClientApplication`: khóa Start/chọn ca trong lúc chờ, khôi phục theo fresh outcome. GUI regression FAIL→PASS, không giảm auth/expected hoặc tăng timeout.

**Phần chưa biết:** các lượt `joint-mock-app-root` và `joint-real-final` trên fixture cũ đọc allProcesses rồi lọc PID có failure chờ process event/FULL ACK; lúc đó chưa có diagnostic đầy đủ, nguyên nhân chính xác **chưa chứng minh**. Case riêng hoặc rerun có diagnostic PASS không chứng minh đã tìm nguyên nhân. Fixture cuối dùng owned handle từ đầu đúng yêu cầu TEST, thêm bounded safe status/count/latency diagnostics; không sửa production ProcessHandle enumerator/timeout hoặc tuyên bố đã sửa một lỗi latency host-wide. Giữ lịch sử/limits này để owner xem; kết quả trên không nghiệm thu startup/metadata host-wide production.

Fixture mới cũng giữ log compile lỗi import/Scene lookup; Display count>32 bị parser từ chối, confirm-before-heartbeat bị guard từ chối, enum-vs-string assertion sai. Đã sửa fixture đúng contract; không nới validator, không đổi READY thành ERROR, không skip hoặc retry mù. Receipt liệt kê hashes các FAIL, raw không được sửa/xóa.

## Verification và cleanup

- Baseline MON08 trước sửa65fa83b:1 PASS; source/runs/cleanup trong receipt.
- Ownership focused lúc trước:103 PASS, source patch cũ; sau đó full suite bao gồm ba regression ownership mới. Scope GUI + scope/collector regression tại51e65dc:20 PASS/0 skip. Các số focused/full có overlap, không cộng thành test mới.
- Full `mvn verify` cuối `dffa22729434d3d95d67c225ebd035d24d1b3b8d`: **1305 PASS / 159 SKIP / 0 FAIL/ERROR** (1464 discovered). Không root clean; worktree task ban đầu sạch và không chứa source bị xóa để sinh stale class. Skip không phải PASS. CI clean verify trên checkout riêng được xem riêng trong PR.
- Standalone USB source983249d:4 PASS (9 commits ổn định qua ACK loss SIMULATED, deferred COMMIT fault rollback rồi retry cùng message, revoke, business deadline). Source USB runtime giống cuối; GUI-only thay đổi sau đó không được gán nhãn là đã chạy lại standalone trên SHA mới.
- Sau resource close, JUnit kiểm real collector stop futures, RT executor termination, delivery await, source children0/readers terminated, toeic worker thread identities sau teardown không còn sống. Owned cmd kết thúc bằng stdin, không kill theo tên. Mỗi runner PG schema count0/stop exit0/postmaster PID absent; mọi lượt FAIL có cluster cleanup tương ứng. Không suy cleanup từ window biến mất/sleep.
- Không .env/DB dev/chung, không SQL INSERT user/session/attempt/assignment để vượt nghiệp vụ trong lượt chung; seeder/import/create-session API. SQL revoke/fault fixture được ghi SIMULATED và không cấp quyền.
- Không workflow/POM/migration/protocol/policy edits; không new provider/native dependency/retention/offline scheduler/rule hoặc app EXE/MSI/ZIP. FULL default và cả experiments OFF được giữ; chỉ TEST explicit bật.

## Demo và phần còn lại

Tại checkout chứa PR: `.\scripts\demo-mon08-alerts.ps1 -Integrated`. Tại máy tác giả, script nằm trong worktree task dưới `target/monitoring-integrated-20261021/scripts/`; `-Mode Real -ReadUsbStorage` là opt-in CIM. Prerequisites/cấu hình/stop/output tại [runbook](../../docs/MONITORING_INTEGRATION.md).

- Code/integration: USB provider vẫn TEST-only, không tự gắn production; giữ diagnostics và giới hạn historical startup case trên. Không thêm observer để lấp thiếu nghiệm thu.
- Policy: owner A/B/C, ADR-V2-05/provider choice, MON08 thresholds/false positives, offline60s/retention30ngày, BASE02 và DELTA gate NOT_DECIDED vẫn pending. Review Codex là kỹ thuật, không human signoff.
- Thao tác: USB physical0/12, second-display physical, desktop manual và LAN0/41 NOT_RUN. Application.stop/Button.fire/Stage properties không là OS window-close/human acceptance. Listening interruption projection MOCK không là playback/interruption E2E.
- Đo/báo cáo: campaign/định lượng host-wide, E1/E2 và nghiệm thu còn sau; không mở task mới hoặc sửa tracker/hours/DONE trong lượt này.

PR/CI/review/merge SHA cuối nằm trong mô tả PR và báo cáo trả người dùng, để không sửa số liệu source/build đã đóng trong receipt này. Chỉ merge commit nếu HEAD cuối CI đạt và repository rules cho phép; giữ nhánh/worktree.
