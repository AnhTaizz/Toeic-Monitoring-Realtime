# V2-C-MON-01 EXECUTION REPORT

19/10/2026 · Task MON-01 · Owner C · Reviewer B: **PENDING**.

**MON_01_STATUS = TECHNICALLY_VERIFIED_PENDING_PHYSICAL_REVIEW.** Kỹ thuật local đã được kiểm bằng evidence BASE-03 bảo toàn và lượt MON-01 mới; còn BASE-02 approval, LAN hai Windows thật và B review. FULL giữ mặc định. Không tự DONE/tracker, không triển khai MON-07/USB/E2.

## Git

| Mục | Giá trị |
|---|---|
| Main local gốc | `f98a37f73d8035e9386e930412a02e9e92933015`, giữ nguyên |
| Remote main kiểm lúc bắt đầu | `1d35b53729abade7b460d35ab07d042b6f9f3571` |
| Base MON-01 / BASE-03 xác minh | `9a97cab646af40e45871cb76ffbfcfcb861f2954` |
| Branch | `test/v2-c-mon01-process-e2e` |
| Worktree riêng | `D:\Toeic-Monitoring-Realtime\target\v2-c-mon01-process-e2e` |
| Source phép thử mới | `f78b40776d0e414e2a631f9e0eb0725b2f1033aa` |
| Commit test | f78b407 — observation semantics và ended-attempt worker cleanup |
| Commit hồ sơ / HEAD | Commit local chứa báo cáo này; `git log -1` cho SHA cuối |
| Push / PR / merge / xóa branch | Không thực hiện |

Chọn9a97cab làm base vì có toàn bộ tools/evidence BASE-03 cần tái sử dụng; production/POM giống remote main1d35b53. Không fetch/pull/merge vào main hoặc reset/rebase/stash/clean. Worktree BASE-03 giữ đúng HEAD9a97cab, sạch; source/evidence/scripts BASE-03 không đổi.

### Sao lưu bền vững BASE-03

Đã sao lưu **trước khi tạo worktree MON-01**, ngoài repository/worktree/build output, không ghi đè:

`D:\Toeic-Evidence-Archive\BASE03-before-MON01-20261019T102703Z-51d0baf1`

477 raw artifacts +27 file evidence + Git bundle5commit local + script/README;507 file trong archive manifest, source artifact bytes205394563. Manifest SHA256 `f857e314a219e17fd113cf08a0c09aa2ab685cbd0e49a93c9bd58d5d988b0bd8`. Bundle verify PASS, prerequisite main1d35b53. Thư mục mới chỉ tài khoản người sao lưu có quyền; không đổi ACL nguồn.

[Receipt](base03-backup-receipt.json) ghi hash/path. Sau chạy MON-01 đã kiểm lại toàn bộ477 file gốc và bản copy: khớp SHA256; source vẫn sạch. Raw/XML private không đưa Git. Việc sandbox reader không vào được archive account-only được giải quyết bằng tiến trình đọc được ủy quyền; ACL không bị nới.

## Existing Implementation

[Pipeline audit máy đọc](pipeline-audit.json) có **14 chặng**, file SHA256, methods, input/output, attempt binding, auth/scope, ACK/retry, persistence, freshness/lifecycle và evidence cho từng chặng.

| Chặng | Class / method chính | Input → output / contract |
|---|---|---|
| App/scope | ToeicClientApplication.candidateMonitoringControls, startOrReuseMonitoring, releaseConnectionView | Fresh HTTP role/scope + attempt đã chọn → start hoặc từ chối; PROCTOR đi nhánh dashboard. Client gate không thay server authorization. |
| Coordinator | CandidateMonitoringSession.start/stop/close | CANDIDATE + attempt trong scope → collectorId, event/FULL delivery và heartbeat lease; cùng snapshot cấp cho hai luồng độc lập. |
| Windows source | ProcessHandleSnapshotSource.scan/read/safe/filename | ProcessHandle.allProcesses → PID/basename/startInstant?/userAvailable; không đọc commandLine/arguments. |
| Policy/collector | ProcessPolicy.includes; ProcessCollector.start/poll/snapshot/stop | Lọc process-policy-v1 → immutable ProcessSnapshot + diagnostics; worker fixed-delay không overlap. |
| History encoder | MonitoringDelivery.observe/tick/connectionChanged/accessRejected | Identity mới → PROCESS_OBSERVED một lần/appearance; bounded queue, frozen eventId/payload/origin/time khi retry; overflow tạo gap. |
| FULL encoder | FullSnapshotDelivery.bind/observe/tick/close | Snapshot → OPEN/epoch/FULL1+; giữ riêng current state và event history. |
| Client transport | RealtimeClient.send/sendForGeneration và callbacks | v0 JSON/attempt + bearer socket → ACK/push được correlation; generation cũ bị bỏ. |
| Server WS | RealtimeWebSocketHandler.handleTextMessage | Revalidate token trước business → gọi service state/event/gap riêng; ACK đúng loại. |
| PG history | MonitoringEventService.store/timeline/lockAndAuthorize/read | Unique(attempt,eventId), FOR SHARE + ACTIVE ownership/assignment → durable event; changed payload CONFLICT. |
| RAM state | MonitoringStateService.open/full/read/maintain | Binding attempt/collector/socket/epoch → canonical state/revision; STALE/TTL tự động; server restart mất current RAM. |
| Presence | MonitoringPresenceService.heartbeat/scan; JdbcPresenceStore | Scoped heartbeat → ONLINE/UNKNOWN + durable interruption; không chứng minh scan mới. |
| HTTP read | MonitoringStateController.read; MonitoringTimelineController.timeline/gaps | Assigned PROCTOR đọc state/events/gaps đúng route attempt; sai quyền403/thiếu token401. |
| Dashboard model | MonitoringApiClient/MonitoringJson; DashboardController/Model | HTTP/push → authorized snapshot; attempt/generation cũ bị bỏ, event merge theo attempt+eventId. |
| JavaFX | ProctorDashboardView.render/column factories/close | Snapshot trên FX thread → process hiện tại/history/metadata/times/presence hiển thị tách biệt. |

Policy hiện tại gồm chrome/msedge/firefox/zalo/teams/discord/anydesk/teamviewer.exe. Identity local là (collectorSessionId,pid,startInstant); tên không phải khóa. Thiếu startInstant làm yếu khả năng phân biệt PID reuse. Thiếu command không thể match allowlist, được đếm trong diagnostics; không được suy ra không có process liên quan.

observedAt lấy UTC Clock ở client lúc encoder xử lý appearance; observationNanos chỉ thời gian monotonic local, không đổi thành Unix time. receivedAt do PostgreSQL/server ghi riêng. deliveryStatus dựa origin/socket, không đo độ trễ bằng cách trừ đồng hồ hai máy.

ACK FULL/DELTA là chấp nhận **RAM** server. ACK PROCESS_OBSERVED/MONITORING_GAP chỉ gửi sau khi Spring transactional store đã COMMIT. FULL mới không ghi/xóa event/gap/interruption. Queue chưa COMMIT chỉ ở RAM client; kill/logout không giả phục hồi nó.

Code production tái sử dụng nguyên trạng. Chỉ bổ sung assertion vào LateEventsAndGapsSmoke và wrapper verify-v2-c-mon01.ps1; không thay ProcessCollector, delivery, JavaFX production, auth, protocol, public DTO hoặc Flyway.

## MON-01 Acceptance Matrix

[Ma trận15 tiêu chí](acceptance-matrix.json) ghi run/source/classification và phần còn thiếu; [đối chiếu BASE-03](reuse-assessment.json) phân biệt REUSED/NEW.

| Tiêu chí | Evidence kỹ thuật | Trạng thái local / còn thiếu |
|---|---|---|
| CANDIDATE-only; PROCTOR không collector | BASE-03 ExamMonitoringIntegration7, CandidateModeSession, C4 PROCTOR_NO_COLLECTOR | PASS kế thừa; B/LAN chưa ký |
| Attempt được cấp hợp lệ/fresh scope | BASE-03 A3 auth/me/scope, app fresh-scope tests; MON-01 ended denial | PASS; BASE-02 pending |
| Scan process Windows thật đúng policy | BASE-03 FULL ProcessHandle/owned Edge; C4 identity/PID/start/metadata | PASS **kế thừa**, không gọi lượt MON-01 scan mới |
| Mở/đóng → current state/history đúng | BASE-03 FULL/proctor component/SQL và C4 | PASS kế thừa |
| Không duplicate mỗi poll/retry | BASE-03 delivery/A3/C4; MON-01 C2 late retry/SQL/warning once | PASS |
| Assigned proctor, candidate/foreign attempt không lộ dữ liệu | BASE-03 A3/C4; MON-01 real HTTP403/revoked assignment/token,65 dashboard cases | PASS |
| Metadata thiếu không gây lỗi/kết luận gian lận | BASE-03 source unreadable tests; MON-01 GUI cell UNREADABLE/thiếu thông tin | PASS, thiếu metadata test là MOCK |
| Absence không thành “chắc chắn không chạy” | Source diagnostics/failure tests; GUI empty labels chỉ nói snapshot/cảnh báo đang quan sát | PASS technical; B review wording còn thiếu |
| observedAt/receivedAt riêng | MON-01 event2099 + real PG/HTTP/FX hai cột khác nhau | PASS; không tính cross-clock latency |
| Heartbeat loss → UNKNOWN | BASE-03 C4 hard-kill; MON-01 disconnect không final message, server timeout/history, B unaffected | PASS local; cable loss thật NOT_RUN |
| Process stale → STALE | BASE-03 autonomous stale khi heartbeat còn sống; MON-01 FullStateDashboardTest | PASS; không gộp với presence |
| Reconnect không mất history/gap | MON-01 delayed/offline origin, dedup, full-history isolation, queue overflow | PASS REAL WS/PG với nguồn/fault MOCK/SIMULATED |
| Stop/logout/attempt end dọn workers | BASE-03 app/stop/logout; MON-01 ba ended-state probes tự dừng trước explicit close | PASS local; actual HTTP submit/timeout GUI flow dành B |

## Verification

**Lượt mới** `MON01-20261019T103217Z-25e62fdd`, sourcef78b407, harness `T2C2-4a57ef56-cc9e-449a-87f6-b2d8fab2c618`.
[Lệnh/environment/cleanup](MON01-20261019T103217Z-25e62fdd/run.json) · [JUnit65](MON01-20261019T103217Z-25e62fdd/mon01-dashboard-focused.json) · [assertions stdout](MON01-20261019T103217Z-25e62fdd/smoke-checks.json) · [raw hashes](raw-manifest.json).

| Phần | Expected / actual mới | Result / classification |
|---|---|---|
| Focused consumer | DashboardModel9 + Controller16 + FullState4 + LateEvents3 + MonitoringApi13 + MonitoringJson20: parse/scope/stale/late callbacks/dedup đúng | **65 PASS,0 FAIL/ERROR,0 SKIP**; model/HTTP fixture MOCK, local HTTP transport thật |
| Build | Package dependencies và compile test harness mới, không chạy full suite | package -DskipTests SUCCESS |
| MT01 | Existing FULL pipeline/real ProcessHandle/proctor correctness từ BASE-03 được giữ; GUI semantics bổ sung | PASS local tổng hợp; physical NOT_RUN |
| MT07 | Held online event + offline observation được gửi sau reconnect, PREVIOUS_CONNECTION/BUFFERED_OFFLINE; duplicate row/warning once; FULL không đổi history/gap | PASS REAL WS/PG; readings/withheld-write/ACK loss SIMULATED/MOCK |
| Overflow/commit | Capacity1/drop2 tạo gap; ACK loss retry giữ ID; deferred event COMMIT failure rollback không ACK/warning, retry COMMIT đúng | PASS; TEST fault/observations SIMULATED/MOCK, PG transaction/HTTP/WS REAL |
| MT08 | Client disconnect không thông báo cuối → UNKNOWN tự động; lastSeen/timeoutDetected/history/recovery giữ, candidate B vẫn ONLINE | PASS REAL timeout/server/transport; outage trigger SIMULATED, không phải rút cáp |
| Metadata/time/UX | Event MOCK UNREADABLE/observedAt2099 đi qua PG/HTTP; FX cell nói thiếu thông tin; hai cột client/server khác nhau; empty snapshot/history không khẳng định absence; UNKNOWN không là gian lận | PASS REAL proctor JavaFX component, data MOCK |
| Attempt end | SUBMITTED/TIMED_OUT/CLOSED riêng → WS scope denial → collector/event/FULL workers tự kết thúc trước explicit stop/close | PASS; chuyển DB TEST **SIMULATED**, không nhận HTTP submit/timeout toàn luồng PASS |
| Role/scope | Foreign proctor/attempt, ended scope, assignment/token revoke bị từ chối kể cả duplicate | PASS REAL auth/HTTP/WS/PG |
| Lifecycle | Stop/remove heartbeat/listeners, GUI/context/schema/owned workers dọn sạch | PASS REAL; .env/password file riêng đã xóa;0schema còn lại, pg-stop0 |
| Physical LAN | Hai Windows/IP LAN/cáp thật/người B | **NOT_RUN**, không có người thao tác/máy thứ hai |

Environment mới: Windows NT10.0.26200, Temurin21.0.10+7, Maven3.9.15, native PostgreSQL18.3 SCRAM, Python3.15.0b3. Cluster/database toeic_mon01_test/cổng loopback ngẫu nhiên thuộc riêng lượt chạy; không đọc credential DB dev. Smoke cũ đọc .env nên wrapper tạo/xóa .env chỉ trong worktree MON-01. SmokeCleanup helper được copy vào loader TEST riêng, không sửa script BASE-03.

19 dòng PASS/cleanup/summary được trích nguyên từ stdout không phải19 JUnit tests độc lập. Measurement summary COMPLETE9endpoints/96categories chỉ xác minh raw đầy đủ, không là E1/E2 hoặc kết luận hiệu suất. Ảnh là proctor component local do harness chụp, giữ private, không là screenshot LAN hoặc human review.

### BASE-03 được tái sử dụng, không cộng như lượt mới

| Evidence | Source / run | Trực tiếp chứng minh MON-01 | Giới hạn |
|---|---|---|---|
| FULL focused163 + FULL/A3 smoke | 834f683 / BASE03-20261019T095202Z-66b7cc19 | Policy/metadata/collector/event retry; REAL Edge → WS → RAM/PG → proctor; auth/COMMIT | Fault sets MOCK; local một máy |
| Local C4 42/42 | Cùng source/run trên; harness T3C4-1565c854-d06b-4a3b-a1d8-bf4ad3c3f2c8 | Production candidate/proctor Application, actual ProcessHandle, cross-candidate/auth, hard-kill/restart/cleanup | Có DELTA/SIMULATED subset; chỉ tái sử dụng phần liên quan MON-01 |
| Full regression983/7SKIP | Cùng source/run834f683 | Regression đã có, includes dashboard classes và worker tests | **Không chạy lại**, không cộng65 thành1048 unique cases |
| Supplement C2 | 463cbad / BASE03-20261019T095950Z-e51076ac | Cơ sở epoch/sequence được giữ nguyên | Không cần chạy lại DELTA để chứng minh MON-01 FULL |
| Oracle72 / Python22 | BASE-03 | Bối cảnh gate/measurement đã lưu | Không là lượt MON-01 mới; không dùng thay process/LAN acceptance |

Môi trường BASE-03: WindowsNT10.0.26200/Temurin21.0.10+7/Maven3.9.15/PG18.3/Python3.15.0b3, đúng [metadata cũ](../v2-c-base03/summary.json). Reuse hợp lệ do production/POM không đổi và477 raw hashes đã kiểm lại ở gốc + archive. Chỉ thêm tests khi thiếu coverage: metadata/time/absence labels và automatic end-state worker shutdown; chạy C2 cho MT07/08 nhắm đích. Không rerun full983/oracle72/C4 chỉ để tăng PASS.

## Physical LAN

**Physical cases executed =0; B review PENDING.** [Handoff riêng](../../docs/V2_C_MON01_PHYSICAL_HANDOFF.md), [CSV16 dòng pending](physical-pending/cases.csv), [review trống](physical-pending/review.json). Các dòng gồm physical và human review; không gọi tất cả là physical test đã chạy.

Dẫn đúng [runbook hiện hành BASE-03](../../docs/T3_C4_PHYSICAL_LAN_ACCEPTANCE.md), source/hash pin mới, dùng -Mode Full/server FULL. Giữ bộ ZIP đã kiểm và checksum trong archive; chưa giao B. STAGE3_FINAL_HANDOFF còn artifact cfbba58/MSI lịch sử, không trộn hash với bundle463cbad/runtime834f683.

Cần B/C thật làm: A server/PG TEST+proctor+candidateA; B candidateB Windows khác, hai account/attempt; mở/đóng process policy, current/history đúng và unique; physical cable-loss → UNKNOWN/STALE, A tiếp tục; re-auth/OPEN/epoch/fresh FULL; persisted history và no duplicate/false ACK; stop/logout và end attempt thật dọn observer; kiểm assigned/foreign attempt permissions. B ký Actual/evidence, không agent ký thay. Không ngắt adapter/firewall hoặc network người dùng trong phiên này.

## Dependencies

**BASE-02 = APPROVAL_PENDING.** Task card yêu cầu human-reviewed Phase3 integration matrix và unresolved issues; docs adoption/STAGE3 ghi CODE_READY_STAGE3_ACCEPTANCE_PENDING, LAN/hearing/MSI máy hai NOT_RUN. Không tìm thấy biên bản approval BASE-02 cụ thể trong docs/evidence hiện có. Team scope approval của PlanV2 không thay approval integration/ADR; backlog TO_AUDIT không là evidence nghiệm thu. Theo yêu cầu, các kiểm chứng độc lập vẫn được phép thực hiện.

**BASE-03 = NOT_DECIDED**, FULL default/E2NO giữ nguyên; không cần gate DELTA PASS để kiểm MON-01 FULL, nhưng không vì MON-01 technical PASS mà tự chốt gate.

**MON-07 chưa được phép tích hợp.** Infrastructure eventId/scope/retry/ACK-after-commit đã có và được MON-01 đối chiếu; readiness chỉ mức tái sử dụng/phác thảo contract. BASE-04/ADR-V2-04 và MON-01 physical/review còn pending; chưa có approval cụ thể A/C cho wire/schema/retention/observer lifetime.

Không phát hiện P0 production mới trong scope đã kiểm; không có reproducer yêu cầu sửa production. Không đổi default, auth/ACK/scope, migration/public DTO, JavaFX production hoặc tracker.

## Final

```text
PROCESS_PIPELINE_VERIFIED = YES_LOCAL (BASE03 REAL ProcessHandle evidence reused + NEW MON01 WS/PG/GUI checks)
EVENT_HISTORY_VERIFIED = YES
ROLE_SCOPE_VERIFIED = YES
FULL_DEFAULT_PRESERVED = YES
PHYSICAL_LAN_VERIFIED = NO
B_REVIEW_SIGNED = NO
BASE_02_APPROVAL = PENDING
BASE_03_GATE = NOT_DECIDED
MON_01_STATUS = TECHNICALLY_VERIFIED_PENDING_PHYSICAL_REVIEW
E2_ALLOWED = NO
```

[Summary JSON](summary.json) là bản máy đọc. Commit local để review; không push/PR/merge, không DONE. Giữ worktree/raw; file raw/artifact51 trong [manifest](raw-manifest.json) có thể được phục hồi từ archive MON-01 riêng theo README/receipt, không chỉ phụ thuộc target.

## Next Task Recommendation

1. **Để MON-01 DONE:** B/C thực hiện checklist vật lý với evidence source/hash/topology đúng, actual app end/submit/timeout/lifecycle, review semantics/scope/history; B ký thật; owner đóng approval BASE-02. Sau đó người dùng/owner mới xác nhận tracker.
2. **Chuẩn bị MON-07 đến đâu:** có thể so reuse event envelope/queue/transaction, làm field/error/acceptance matrix và sample JSON MOCK trong proposal riêng khi được giao. Không tái cấu trúc process pipeline hoặc gắn USB/focus/display vào PROCESS_OBSERVED hiện có. Phiên này chỉ khuyến nghị, không viết/triển khai contract MON-07.
3. **Quyết định cụ thể chờ A cùng C:** type/envelope và schema version mới; eventType taxonomy và field required/optional; attempt ACTIVE/START eligibility và scope trước duplicate; dedup key/CONFLICT và ACK-after-COMMIT; bảng append-only/Flyway version mới; metadata/body/queue/retry bounds; nguồn observedAt/receivedAt/origin; retention/privacy; error/unsupported/unknown semantics và B consumer compatibility. Cần record approval ADR-V2-04/BASE-04 trước thay public wire/SQL. Không tự chọn lại QD-21 START/deadline.
4. **MON-02 USB spike độc lập:** khi được giao, có thể probe Java local trên Windows so PnP với bounded WMI/CIM, hardware presence/connect/remove/debounce và quyền thường; không gửi server hoặc thay DTO/schema. Chỉ USB mass storage, không mouse/keyboard tổng quát, không đọc file/serial, không block thiết bị. Thiếu USB thật/no-admin/package evidence thì NOT_RUN; native dependency phải khai báo/kiểm đóng gói. Integration vẫn đợi MON-07 + BASE-04/ADR API review với A/C/B.

Dừng ở MON-01 để người dùng review, không tự chuyển task.
