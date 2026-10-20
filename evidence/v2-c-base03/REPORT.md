# V2-C-BASE-03 EXECUTION REPORT

Ngày 19/10/2026 · Owner task C · Reviewer B: **PENDING** · Deadline gate 20/10/2026.

**Kết quả: DELTA_CORRECTNESS_GATE = NOT_DECIDED.** Kiểm chứng kỹ thuật mới đã PASS, nhưng LAN hai máy vật lý và chữ ký B còn thiếu. FULL tiếp tục là mặc định; E2 không được phép chạy. Không cộng kết quả audit 307+12 trước đó vào lượt này.

## A. Git

| Mục | Giá trị |
|---|---|
| Main local ban đầu và cuối phiên | `f98a37f73d8035e9386e930412a02e9e92933015`, sạch |
| Remote main sau fetch và kiểm lại cuối phiên | `1d35b53729abade7b460d35ab07d042b6f9f3571` |
| Branch | `test/v2-c-base03-delta-gate` |
| Worktree | `D:\Toeic-Monitoring-Realtime\target\v2-c-base03-delta-gate` |
| Source lượt FULL-first R1 | `834f683e50b22e214ab63bbf7d046a7d418b8242` |
| Source bổ sung R2 | `463cbadc50465c6ca2f2d4c0a0701dcb9c38e034` |
| Source đóng gói kit | `2ac13d2d1a95bf655c5869542ae8c25d27ea7600` |
| HEAD hồ sơ cuối | Commit local chứa báo cáo này; xem `git log -1` |
| Push / PR / merge | Không thực hiện |

Bốn commit remote mới so với f98a37f: 3077f40 thêm test PG rich migrations; 975284d thêm V12 chặn NULL media type; 6378dc8 ghi evidence/EX02 proposal; 1d35b53 merge PR66. Không có thay đổi monitoring production trong bốn commit này. Chỉ fetch refs và tạo worktree, không pull vào main.

Các commit local kỹ thuật: fc16524 (runner), 834f683 (sửa inherited pipes Windows), 463cbad (probe MT04/MT06 + supplement guard), 2ac13d2 (kit pin/runbook). Commit hồ sơ cuối bổ sung evidence/tài liệu. Diff từ main1d35b53 không đổi client/server/protocol production, migration, POM hoặc cấu hình mặc định.

## B. Baseline FULL

R1: `BASE03-20261019T095202Z-66b7cc19`. [Metadata và lệnh](BASE03-20261019T095202Z-66b7cc19/run.json), [163 test FULL](BASE03-20261019T095202Z-66b7cc19/full-focused.json), [pipeline assertions](BASE03-20261019T095202Z-66b7cc19/full-pipeline-checks.json), [history assertions](BASE03-20261019T095202Z-66b7cc19/history-checks.json).

Đã chạy theo thứ tự FULL focused → package → smoke-t2c1 -Gui → smoke-a3, rồi mới DELTA. Pipeline REAL: HTTP login/attempt TEST hợp lệ → production ProcessHandle quét Edge do harness sở hữu → collector CANDIDATE → FULL qua WS → state RAM đúng attempt → proctor component JavaFX/HTTP/push → event history PostgreSQL. Đóng Edge xóa PID khỏi current state nhưng giữ history; reconnect/epoch, duplicate/conflict, empty FULL và scope đều PASS. Một process tồn tại qua nhiều poll không tạo history lặp. Proctor không chạy collector; stop/close dọn worker và schema.

Heartbeat/presence và scan freshness được kiểm tách biệt: socket/heartbeat còn sống vẫn tự STALE khi nguồn scan dừng; TTL tạo tombstone và giải phóng capacity, không xóa event/gap/interruption history. Đây là test với timeout rút ngắn, fault process sets MOCK. MT01/MT07/MT08 được đối chiếu qua các nhóm này; chưa nâng trạng thái nghiệm thu vật lý trong tracker.

FULL smoke dùng proctor component thật; candidate Application đầy đủ được kiểm ở C4 sau đó. Wrapper nạp thêm SmokeCleanup test helper vào isolated loader directory vì script FULL cũ chỉ copy FullSnapshotSmoke*.class. Không sửa baseline production để hỗ trợ DELTA.

## C. MT02–MT06

R2: `BASE03-20261019T095950Z-e51076ac`. [Lệnh/cleanup R2](BASE03-20261019T095950Z-e51076ac/run.json), [probe assertions R2](BASE03-20261019T095950Z-e51076ac/delta-network-checks.json). R2 chỉ bổ sung test; guard kiểm R1 đã FULL PASS, ancestor hợp lệ và production/POM không đổi trước khi tái sử dụng baseline.

| ID | Test method/harness và command stage | Input / expected | Actual mới | Status | Evidence / run |
|---|---|---|---|---|---|
| MT02 | DeltaSnapshotDeliveryTest.ackLossOrWriteFailureRetriesEntireFrozenDelta; MonitoringDeltaStateTest.duplicateNoopDoesNotRenewLeaseAndRevokedScopeIsCheckedFirst; smoke-a3; smoke-t3c2; smoke-t3c4 | Cùng ID/envelope retry; khác payload CONFLICT; auth trước cache; revision/history không tăng trùng; ACK DB sau COMMIT | Frozen retry, ACK loss không apply lần hai; SQL event một row; commit rollback không success ACK; revoke không đi qua duplicate cache | PASS technical | R1 delta-focused, history-checks, delta-network-checks, local-live-report |
| MT03 | DeltaReplayTest.independentOracleAndProductionCoreAgreeOnMissingDeltaFullRecoveryAndLateDelta; smoke-t3c2; C4 RESYNC_LAST_KNOWN | Drop/reorder/future base → UNSYNCED/RESYNC; giữ canonical last-known; scan/FULL mới phục hồi | Replay và WS đúng expected; client parser/callback tạo FULL mới, không đoán state thiếu; heartbeat vẫn có thể ONLINE | PASS technical | R1 oracle-summary, delta-network-checks, local-live-report |
| MT04 | ReplayCampaignTest.epochAndLowVersionLateMessagesCannotOverwriteRecoveredState; DeltaStateSmoke BASE03_RETIRED_DELTA | OPEN epoch mới + FULL1; DELTA epoch cũ với cả ID đã nhận và ID mới phải STALE; revision/set/history không đổi | Hai probe DELTA trả STALE qua WS; epoch/sequence/revision/canonical processes và SQL history giữ nguyên | PASS technical | R1 delta-focused; R2 delta-network-checks |
| MT05 | RealtimeClientTest.reconnectOpensFreshSocketAndIgnoresStaleCallbacks; CandidateModeSessionTest.reconnectWaitsForCurrentSocketCapabilityThenStartsFullOne; C2/C4 | Restart collector/candidate/server; bỏ socket callback cũ; OPEN/epoch/FULL mới; DB history giữ | Restart child JVM/server thật, serverInstanceId/epoch mới; scope/token revoke dọn worker; không hồi sinh base cũ | PASS technical | R1 delta-focused, delta-network-checks, local-live-report; R2 C2 |
| MT06 | DeltaSnapshotDeliveryTest.periodicFullUsesMonotonicDeadlineAndDoesNotRefreshAfterScanFailure; ReplayCampaign; DeltaStateSmoke BASE03_FULL_SEQUENCE | FULL reconciliation; lower seq STALE; same seq changed content CONFLICT; canonical identity/content và history đúng | C4 qua mốc FULL30s; R2 FULL4 sau FULL5 STALE, FULL5 đổi set CONFLICT, state/revision/history bất biến; oracle bên dưới | PASS technical | R1 delta-focused, oracle-summary, local-live-report; R2 delta-network-checks |

Command thực thi đầy đủ và stage Maven class list nằm trong run.json từng lượt; [README](README.md) có lệnh tái lập từ đầu. [151 test DELTA](BASE03-20261019T095202Z-66b7cc19/delta-focused.json) ghi từng method/status, không gán PASS cho case chưa chạy. Các đoạn assertion công bố được chọn từ stdout; raw gốc và checksum nằm trong [raw-manifest](raw-manifest.json).

## D. FULL/DELTA Oracle

Harness T3C3 run `65da0b98-e732-4b0f-adb2-e7d85d6a9b50`, seed20261012. Ba input MOCK: low-change.jsonl, high-change.jsonl, reconnect.jsonl trong client/src/test/resources/monitoring-traces/t3-c3. Có thêm handwritten expected-table tests, thay đổi PID/start/name/metadata và event appearance. [Summary gốc](BASE03-20261019T095202Z-66b7cc19/oracle-summary.json), [reproducibility/hash](BASE03-20261019T095202Z-66b7cc19/oracle-verification.json).

72 ca = 3 workload × 12 scenario × 2 mode, PASS. So tập Process canonical đầy đủ và event map theo eventId/nội dung, không chỉ process count. ObservationOracle đọc observation gốc, không gọi encoder/reducer/ReplayServer để tạo expected. Mỗi scheduled frame có outcome/state/event comparison riêng.

35/36 cặp có common synchronized observations. Một cặp seeded không có checkpoint chung: giữ NO_COMMON_SYNC_CHECKPOINT, không ép state tương đương trong lúc missing-base gây divergence; từng mode vẫn được kiểm bằng oracle độc lập. Business events và delivered event maps bằng nhau giữa FULL/DELTA ở cả36 cặp. Scenario event-drop cố ý thiếu historical event: FULL state recovery không được coi là sửa history đã mất.

4 negative controls cố tình làm sai FULL/DELTA/event đều trả FAIL như expected và được phát hiện; đây là PASS cho khả năng bắt mismatch, không phải bug correctness mới. 77 report/summary files của hai campaign có byte/hash giống nhau. Các CLI invalid config/checksum/test-schema/no-overwrite trả exit2; mutation trả exit1 đúng expected. Raw failure traces không sửa.

-RealScan ghi 6 ProcessHandle scans thật; replay FULL và DELTA đều PASS. Transport/fault của lượt này vẫn REPLAY/SIMULATED, không phải live ACK-loss/network/DB hoặc LAN.

## E. Reliability

C4 run `T3C4-1565c854-d06b-4a3b-a1d8-bf4ad3c3f2c8`: [42/42 assertions](BASE03-20261019T095202Z-66b7cc19/local-live-report.json). Một Windows host, hai candidate child JVM khác tài khoản/attempt, proctor Application, Spring và PostgreSQL thật, ProcessHandle/Edge thật. Một child bị hard-kill có chủ đích, restart sang instrumented child cho fault subset.

Đã kiểm retry nguyên ID/envelope, ACK mất không reapply; DB rollback không ACK thành công; event identity/content/origin chỉ COMMIT một lần; revoke auth/scope trước cache; RESYNC giữ last-known rồi fresh FULL; exhaustion mở epoch mới; old callbacks không cập nhật run mới; hung source/TTL tách presence; server/client restart giữ history; FULL30s reconciliation. ACK drop, future base, source pause/failure và TEST DB trigger đều **SIMULATED**.

Fallback: client không opt-in luôn FULL kể cả server hỗ trợ; capability UNKNOWN không tự bật DELTA; unsupported/disabled server trả FULL; reconnect xác minh capability mới; mất capability dừng DELTA, FULL được chọn khi explicit stop/start phù hợp. Default app factory và server defaults không đổi. Không có P0 mới được phát hiện trong phạm vi đã chạy.

C4 measurement summary trả exit2 INCOMPLETE vì endpoint bị hard-kill không có END. Đây là giới hạn measurement được giữ nguyên; correctness smoke exit0. Không suy ra throughput/bandwidth/CPU/E1/E2 từ lượt này. Trường gate trong raw local-report còn chuỗi template lịch sử “2026-10-17 preparation”; dateUtc/sourceSha và metadata xác định lượt mới, không dùng chuỗi đó làm ngày chạy.

## F. Physical LAN

**NOT_RUN, 0/41.** Chưa có máy Windows vật lý thứ hai/người B phối hợp; không rút mạng, đổi adapter/firewall hoặc tạo screenshot/signoff vật lý. Ảnh local smoke nằm private target, không gọi chúng là LAN proof.

Đã chuẩn bị [runbook hiện hành](../../docs/T3_C4_PHYSICAL_LAN_ACCEPTANCE.md), [41 case](physical-pending/cases.csv), [topology](physical-pending/topology.json), [review trống](physical-pending/review.json), [run pending](physical-pending/run.json) và [ZIP/hash](artifacts.json). 22 file được pin hash; kit source29/29 và giải nén29/29 PASS. Initialize sau giải nén xác minh manifest và vẫn ghi casesExecuted0/B PENDING. Fixture kit MOCK, không gọi mạng. Các trường máy A/B phải do người thật điền sau preflight.

B cần cùng C làm: A chạy TEST DB/server/proctor/candidate_A; B chạy candidate_B khác attempt qua IPv4 LAN thật; ghi baseline; rút cáp B thật có chứng kiến; A tiếp tục hoạt động; B UNKNOWN/STALE; nối lại → auth/OPEN/epoch/fresh FULL; đối chiếu SQL history unique/COMMIT; lặp Reading và Listening trên attempt mới, nghe thật và chứng minh không autoplay/resume; outage dài vượt retry budget; stop/logout dọn observer. Fault console/probe dùng attempt riêng và nhãn SIMULATED, không thay F.

B chưa nhận ZIP. Không có MSI build/install hoặc clean-machine acceptance trong task này. Bộ cfbba58/MSI0.3.11 lịch sử không được dùng thay hash BASE-03.

## G. Test Summary

| Nhóm mới | PASS | FAIL/ERROR | SKIP / NOT_RUN |
|---|---:|---:|---|
| FULL focused Java | 163 | 0 | 0 |
| DELTA focused Java | 151 | 0 | 0 |
| Maven package regression, PG enabled | 983 | 0 | 7 SKIP |
| Python E1 analyzer unit | 12 | 0 | 0 |
| Python measurement analyzer unit | 10 | 0 | 0 |
| Oracle CLI campaign | 72 | 0 unexpected | 4 intentional failures detected |
| Local live smoke | 42 | 0 | LAN not included |
| Kit source / unpacked | 29 / 29 | 0 | No network |
| Physical LAN | 0 | 0 observed | 41 NOT_RUN; B PENDING |

314 focused cases là subset của regression983, không cộng thành1297 unique tests. Python22 là unit analyzer, không phải chạy chiến dịch E1. Không cộng audit cũ, smoke assertion và JUnit thành một tổng “tests” không cùng đơn vị. [Summary máy đọc](summary.json), [JUnit case metadata](BASE03-20261019T095202Z-66b7cc19/regression-package.json).

7 SKIP: 3 filesystem cases cần symlink/8.3 capability của host; 2 method yêu cầu TOEIC_PROCTOR_GUI; 1 installed MSI; 1 packaged app-image. Không gọi chúng PASS. GUI proctor/production Application trong monitoring smokes có scope riêng, không thay các method skipped.

Môi trường: Windows NT10.0.26200; Temurin21.0.10+7; Maven3.9.15; PostgreSQL18.3 native SCRAM; Python3.15.0b3. Mỗi lượt có cluster/database riêng, random loopback port; DB/seed credentials TEST ngẫu nhiên chỉ nằm private .env của worktree và được xóa. Hai lượt thành công đều0 schema còn lại, pg-stop exit0; password file/private .env đã xóa.

Các lỗi môi trường giữ lại: Maven sandbox đầu không đọc được cached dependency, trước test; R0 fc16524 bị pg_ctl inherited pipes, phải dừng đúng owned cluster exit0 rồi runner ghi createdb/second-stop FAIL. Sửa wrapper ở834f683, R1/R2 PASS; không đổi assertion/production. New-LanBundle gọi không truyền RepositoryRoot gặp lỗi param mặc định PowerShell5.1; chạy lại với root tường minh PASS. Dot-source trực tiếp bị execution policy local chặn; chạy child powershell -ExecutionPolicy Bypass theo scope process PASS, không đổi policy máy. Các lỗi này không được đổi thành correctness FAIL hoặc xóa khỏi hồ sơ.

## H. Gate Decision

| Hạng mục | Technical | Real WS/PG | Physical LAN | B Review | Kết luận |
|---|---|---|---|---|---|
| MT02 | PASS | PASS, retry/SQL/commit | NOT_RUN | PENDING | NOT_DECIDED |
| MT03 | PASS | PASS; missing/order faults SIMULATED | NOT_RUN | PENDING | NOT_DECIDED |
| MT04 | PASS | PASS, old DELTA qua WS R2 | NOT_RUN | PENDING | NOT_DECIDED |
| MT05 | PASS | PASS, owned JVM/Spring restart | NOT_RUN | PENDING | NOT_DECIDED |
| MT06 | PASS trong scope oracle | PASS reconciliation/sequence; oracle REPLAY | NOT_RUN | PENDING | NOT_DECIDED |
| FULL fallback | PASS | PASS, disabled server/current capability | NOT_RUN | PENDING | NOT_DECIDED |
| Reconnect | PASS | PASS loopback | NOT_RUN | PENDING | NOT_DECIDED |

```text
DELTA_CORRECTNESS_GATE = NOT_DECIDED
FULL_DEFAULT_PRESERVED = YES
MT02_MT06_COVERAGE = TECHNICAL_PASS / REAL_LOOPBACK_WS_PG / PHYSICAL_NOT_RUN
ORACLE_VERIFIED = YES (declared checkpoints; 35/36 pairs comparable)
PHYSICAL_LAN_VERIFIED = NO
B_REVIEW_SIGNED = NO
E2_ALLOWED = NO
```

Không có chữ ký thay B, không đổi QD hoặc backlog/tracker DONE. BASE-01/02 human acceptance dependencies vẫn cần owner xác nhận; CODE_READY không đồng nghĩa nghiệm thu.

## I. Bàn giao

C đã hoàn tất phần kỹ thuật chạy được: FULL-first, 314 focused, real WS/PG/history, 983 regression, oracle,42 local live checks, probe MT04/MT06, wrapper cô lập và kit/checklist có hash; không sửa production. Giữ worktree và raw cho người review, không xóa target.

B review [matrix](test-matrix.json), case metadata và raw/checksum; nhận ZIP qua kênh riêng, đối chiếu hash độc lập trên cả A/B; chạy41case, nghe thật, lưu evidence và ký review.json/cases.csv bằng danh tính của mình. C hỗ trợ đối chiếu epoch/sequence/SQL/workers. A review auth/attempt/assignment trước duplicate cache, transaction COMMIT/rollback và TEST DB isolation; không cần đổi auth/server/DB để làm các test hiện tại PASS.

Còn thiếu để gate PASS: physical topology/cable-loss/rejoin/Listening/cleanup evidence, B signoff, closure dependencies theo owner; raw E1 lịch sử vẫn chưa đủ, đây còn là prerequisite đo lường riêng cho E2. Automated PASS không tự cấp phép E2.

Không thấy P0 mới cần ưu tiên sửa trước. Có thể tiếp tục verification MON-01 trong phạm vi/dependency được giao; MON-07 còn cần BASE-04/MON-01 và review contract, chỉ chuẩn bị proposal khi được giao. Phiên này dừng BASE-03 để người dùng review, không triển khai MON-07/MON-02/E2.

Mâu thuẫn tài liệu được ghi nhận: V2 risk/runbook còn mô tả USB bị cấm tuyệt đối dù CLAUDE đã có scope exception; QD-10 lịch sử không thay gate BASE-03/QD-19; TIEN_DO có placeholder ${base}; source/kit cfbba58 cũ khác main1d35b53. Chỉ kit/runbook của nhánh này được pin lại đúng artifacts đã kiểm; không sửa hợp đồng để suy ra quyền đổi wire.

## J. Giải thích cho sinh viên

FULL gửi toàn bộ danh sách process sau một lần scan. DELTA chỉ gửi phần thêm/bớt so với state mà server đã ACK, nên tiết kiệm dữ liệu trong một số tình huống nhưng cần đúng “nền”. Nếu một DELTA bị mất, bản tiếp theo có thể dựa trên danh sách server chưa từng nhận; áp dụng tiếp sẽ sai.

Epoch nhận diện một vòng đồng bộ mới sau start/reconnect/restart; sequence đánh số các phiên bản trong vòng đó. Server từ chối epoch cũ và phát hiện thiếu base/sequence. Khi thiếu nền, server giữ last-known với nhãn UNSYNCED và gửi RESYNC. Client bỏ pending/base không còn đáng tin, scan lại rồi gửi FULL mới; không suy đoán những process chưa quan sát được.

ACK FULL/DELTA xác nhận state RAM của server, không bảo đảm state đó sống qua server restart. ACK PROCESS_OBSERVED/MONITORING_GAP xác nhận transaction history đã COMMIT PostgreSQL. Vì vậy snapshot mới không được xóa lịch sử và ACK state không được gọi là ACK DB.

Trong lượt local này, candidate quét Edge thật và gửi FULL/DELTA qua WS; server kiểm token/attempt rồi cập nhật RAM và ghi event riêng; proctor nhận push và đọc HTTP cùng state. Khi harness giả lập mất ACK, candidate gửi lại nguyên message, server không áp dụng hai lần. Khi restart, epoch mới và FULL mới khôi phục quan sát còn history vẫn ở DB.

Các test tự động kiểm được logic và nhiều lỗi có chủ đích. Hai JVM cùng máy chưa chứng minh cáp LAN, đường đi dự phòng, máy thứ hai, nghe Listening hay thao tác người dùng thật. B cần nhìn và ký các bằng chứng đó trước khi nhóm quyết định gate; tới lúc ấy FULL vẫn là lựa chọn mặc định.
