# Trạng thái kiểm thử

20/10/2026 — [MON-05 display monitoring](../evidence/v2-b-mon05-display-20261020/REPORT.md):
nhánh stacked trên MON-04 `0ae71bf`; 9 case MON-05 trực tiếp kiểm tra baseline/change/dedupe,
polling 1–30 giây, lỗi UNKNOWN/UNSUPPORTED/ERROR, recovery và disposal. Full clean test 1363 total
= 1219PASS/144SKIP/0FAIL/ERROR. Supplier/clock/callback là MOCK; cắm/rút màn hình thứ hai,
REAL WS-HTTP-PG/proctor và review C NOT RUN nên chưa đánh dấu MON-05 DONE.
21/10/2026 — [USB integrated explicit TEST một lệnh](USB_INTEGRATED_TEST_DEMO.md):
baseb68e19a (gồm PR87/88), coordinatorfdc90a1/build-demo e7cd9dc. CIM provider thật →
snapshot/USB adapter/status → shared context/delivery/WS → PG TEST/COMMIT/ACK/history.
REAL MSFT15s STANDARD:10scan READY0,1READY/INITIAL COMPLETE committed+ACK+readback,0USBevent;
children0/readers+3workers stopped/0schema/PGstop0. MOCK4network PASS; baseline194/unit204;
fullverify1220PASS/149SKIP/0lỗi,15JARchecks/CLIhelp0. EarlyStopREAL1scan CANCELLED/metadata130,
không completed pipeline PASS. Script tự dựng DB/schema/password stdin/seeder/API scope,
không .env/DBdev/defaultobserver. Report/CI/merge cuối tại PR/evidence. FULLdefault,
experimentOFF/USB0of12NOT_RUN/MON02+07chưaDONE/ADR/gate/policy/ownerpending giữ nguyên.


20/10/2026 — [MON-04 window state](../evidence/v2-b-mon04-window-20261020/REPORT.md):
nhánh stacked trên MON-03 `d5c568a`; focused 55PASS/0SKIP, gồm 8 case window trực tiếp
và 1 shared-claim case mới. Full clean test 1354 total = 1210PASS/144SKIP/0FAIL/ERROR.
Baseline non-fullscreen INITIAL, minimize/restore, fullscreen transition, same-pulse debounce và
dispose cleanup PASS tự động; không mutation/punishment. Manual Stage/REAL WS-PG/review C
NOT RUN nên chưa đánh dấu MON-04 DONE.

20/10/2026 — [MON-03 focus candidate](../evidence/v2-b-mon03-focus-20261020/REPORT.md):
nhánh `feat/v2-b-mon03-focus-monitoring-20261020`, base d2c8f01; focused 46PASS/0SKIP,
gồm 9 case adapter/JavaFX mới và shared lifecycle regression. Runtime opt-in OFF mặc định,
chỉ candidate attempt/owned JavaFX windows, duration monotonic; không scrape app ngoài.
Full clean test 1345 total = 1201PASS/144SKIP/0FAIL/ERROR; lượt no-clean trước đó
FAIL discovery server do stale `target` thiếu `VisualManifest`, được giữ trong report.
Manual Alt+Tab/PROCTOR/REAL WS-PG và review C NOT RUN, nên chưa đánh dấu MON-03 DONE.

20/10/2026 — [MON-07 status/gap explicit MOCK](../evidence/c-mon07-status-gap-20261020/REPORT.md):
runtime1b84d30, test1c008c8; OBSERVER_STATUS bốn nguồn và SIGNAL_GAP overflow qua shared
context/sequence/queue/socket, REAL WS/HTTP/PG TEST/COMMIT-ACK/history. Baseline246PASS;
focused cuối334PASS/0SKIP (29pipelinePG), fullverify1192PASS/144SKIP/0lỗi,10JARchecks.
V17 append-only, upgrade15/16→17 giữ display/counter/checksum13–16, OFF vẫn applyDDL
nhưng window/status/gap không mở runtime lane. TEST0schema/stop0; raw ignored, CI/PR/merge
cuối tại PR. User authorization không là humanapproval; FULLdefault/experimentOFF,
USB0/12NOT_RUN/MON07chưaDONE/ADR/gate/policy pending. Không productionobserver/E1/E2.


20/10/2026 — MON-02 adapter: baseline e189b61 **154PASS/0SKIP**; aac7528 focused
**312PASS/0SKIP** gồm33protocol/181client/34probe/64server,24 REAL PG pipeline cases.
Input USB là MOCK; REAL HTTP/WS/PG TEST, ACK-loss SIMULATED;6USB events đúng count/ref/
sequence, retry immutable/1row, history, revoke/worker stop và old callback/new attempt.
15 adapter +3 shared context +4 projection unit cases; expected timeline độc lập.
Hai ownership regression2/2FAIL trước sửa được giữ; final single-claim PASS. Fake transport
thiếu interface method ở compile đầu là lỗi fixture đã sửa, không gọi test chưa chạy PASS.
Lượt TEST setup đầu bind socket Windows bị từ chối trước Maven; raw log giữ, chưa chứng
minh cổng đó thuộc excluded range (không ghi lại số); chạy mới ngoài dải đã kiểm đạt.
Các lượt PG thành công còn0schema/cluster stop0; không hotplug/USB hardware acceptance.

20/10/2026 — MON-07 final review: runtime/test **abb282e**, **253 PASS/0SKIP** focused
(33 protocol/163 client/57 server,21 REAL PG/WS cases). Sửa bypass raw payload/details
byte bound bằng whitespace sau test1/1FAIL; giữ log lỗi, bổ sung exact boundary/+1 và
WS reject/no insert. Build/JAR mới và CI exact HEAD tại [report/receipt](../evidence/c-mon07-typed-20261020/REPORT.md).
Các kết quả252/source c71/a0e bên dưới là lượt trước finding, không thay bản cuối.
TEST teardown0schema/stop0; FULL mặc định/experiment OFF/gate/owner/hardware pending.

20/10/2026 — [MON-07 typed experiment](../evidence/c-mon07-typed-20261020/REPORT.md):
baseline ad8c370 **227 PASS/0SKIP**, focused runtime a0e9200/test c71ddde **252 PASS/0SKIP** = 32 protocol,
163 client, 57 server; trong đó 21 case SignalPipelinePostgresTest (5 mới) qua PG/HTTP/WS
thật, mọi observation là MOCK, ACK-loss/deferred fault SIMULATED. Bỏ riêng case5001 rows
đã có evidence cũ; không cộng baseline/focused hay các lượt lặp thành unique tests.
Typed boundary/source/details/quality/canonical/immutability, queue tổng, capability subset,
pre-write shrink, old callbacks, history fixed-through, auth/rollback và upgrade15→16 PASS.
Giữ FAIL trước sửa guard queued type khi capability thu hẹp; legacy fixture giữ nguyên.
Receipt custom selector cũ bỏ sót XML #methods, đã đối chiếu module log/XML bằng bản
reconciled riêng (227/252); không sửa receipt/log gốc hoặc gọi các count thiếu là kết quả cuối.
Mỗi lượt TEST còn0schema/cluster stop0; không .env/DB dev/USB hotplug/Alt-Tab/fullscreen desktop.

20/10/2026 — [E1 derived tooling](../evidence/c-e1-derived-20261020/REPORT.md), [receipt](../evidence/c-e1-derived-20261020/receipt.json):
baseline159954b12PASS và tooling c7f12a7 **27PASS/0SKIP** (12legacy+15mới), không cộngthành39tests.
MOCK fixedcounts/weightedCPU/unknown/censor/diff/invalid/hash/schema/run-clock/trial/output/determinism;
analyzer nguyên bản từ chối thay join bằng RAW_CHECKSUM đúngcontract.1REAL smoke-short source/tool5bbeaf2
(JARreceipt verified):3trials rejoin identical/0diff,7rawhash trước/saukhớp,8outputs của2analysis giốngbyte,
campaignPARTIAL. Chỉ integration rejoin, không JavaBuild/full12/PG/GUI/E2; raw lịch sử NOT_AVAILABLE,
không QA05DONE/E1acceptance hoặc gate change.

20/10/2026 — [Lifecycle FULL mới](../evidence/c-full-lifecycle-20261020/REPORT.md), [receipt](../evidence/c-full-lifecycle-20261020/receipt.json): runtime5bbeaf2/scenario47caa50,
6nhóm A–F/10checkpoints programmatic production Application +REAL PG/HTTP/WS PASS; owned
Edge PID/start/state/history/dashboard khớp; Stop/Logout/Submit/timeout tự nhiên/app-stop
worker0, dump cuối0toeic-threads.215JUnitPASS/0SKIP MOCK bổ sung, không cộng checkpoints vào
JUnit hoặc tính run lặp như test mới. Test ownership49007cf FAIL vì API fixture lazy worker,
47caa50 đóng fixture trước assert rồiPASS; rawFAIL giữ. Native desktop NOT_RUN theo user,
không human/two-machine/USB/audio nghe PASS; cả4run schema0/clusterstop0, owned JVM/listener0.

20/10/2026 — [Lượt kit main a6fba311](../evidence/c-integrated-kit-20261020/REPORT.md): mới tự chạy
35USB +65consumer PASS/0SKIP (hai suite khác nhau),33kit script checks PASS; provider thật
MSFT READY0/Win32 UNKNOWN, children0/readers true, cap4/exit5 và no-overwrite exit3/hash giữ.
Không PG/server/GUI manual/Listening/hotplug/physicalLAN mới. Không dùng lại số lịch sử
làm lượt mới; USB0/12/LAN0/41NOT_RUN, FULLdefault/experimentOFF, gate chưa quyết định.

20/10/2026 — [Tổng hợp C mới](../evidence/c-technical-review-20261020/REPORT.md), [receipt](../evidence/c-technical-review-20261020/receipt.json): source d6bf60a fullverify1077total=1071PASS/6SKIP/0FAIL/ERROR,17REAL PG/HTTP/WS cases. Focused cd00630=228PASS;5001WS COMMIT/ACK/51paged history,24concurrent inserts/cursor/revoke/unassign PASS. Shared process smoke d6bf60a PASS/0schema/stop0. BASE03ff07446:307PASS+realDeltaSmoke; MON0192f9faf:65PASS+GUI/PG smoke; USBaa260ef equivalent5c6886b:35PASS+provider snapshots, không hotplug. Không cộng overlap. Giữ trướcfix NoClassDef/cleanup1schema và fulla5339dc1070PASS/1FAIL/6SKIP; corrective có controlled proof. Không hardware/LAN/GUIoptin/E1/E2 PASS, không gate/DONE.

20/10/2026 — [MON-07 review PR74](../evidence/v2-c-mon07-signal-pipeline/REVIEW-20261020.md): source2d73605 focused227PASS/0FAIL/ERROR/SKIP,16REAL PG/WS cases; 8 case mới trong review. Trước sửa giữ adapter70/4FAIL, stale-write1/1FAIL, stale-denial1/1FAIL và focused226/225PASS/1ERROR (HTTP200 sau expiry, expected401). Fullverify/JAR1042total=1036PASS/6SKIP/0FAIL/ERROR, teardown0schema/stop0; [receipt source/JAR](../evidence/v2-c-mon07-signal-pipeline/review-20261020.json). Không dùng số c77 thay source mới; hardware/owner/gate pending.

19/10/2026 — MON07 sourcecuối c77d8f8: focused219PASS/fullverify1034total=1028PASS/6SKIP/0FAIL/ERROR, **44case mới subset gồm14PG/WS**. [Latest summary/case/hash](../evidence/v2-c-mon07-signal-pipeline/verification-c77d8f8.json), [receipt mới](../evidence/v2-c-mon07-signal-pipeline/build-receipt-c77d8f8.json). Thêm FAIL→fix→PASS cho DB-clock sau session locks và cleanup ở uncorrelated token denial/FAILED; logs/artifact/report0f9d8e7/7cb7478 giữ nguyênignored. Teardown0schema/stop0, không nâng physical/owner/gate/DONE; số0f9d8e7 bên dưới là lịch sử.

19/10/2026 — MON07 user-authorized slice MOCK source0f9d8e7: [report/case/hash](../evidence/v2-c-mon07-signal-pipeline/REPORT.md). Focused216PASS/0SKIP; fullverify1031total=1025PASS/6SKIP/0FAIL/ERROR, gồm12REAL PG/HTTP/WS cases (sensorsMOCK, ACK loss/deferred failureSIMULATED), actual reconnect/cursor/auth/dedup/commit rollback; 0schema/clusterstop0. 41test mới làsubset full; không cộng lượt. Hai regression integer-wrap/old-run capability đãFAIL trước fix, giữraw. Không toànmatrix22PASS/GUI/physical/LAN/E1/E2; FULLdefault/gateNOT_DECIDED, chưa owner signatures/MON07DONE. CI exact-HEAD ghi tại Draft PR, không gán CI cũ.

20/10/2026 — Release candidate `stabilize/v2-rich-main-candidate-20261020` `f9901fd`, cloud Linux, OpenJDK 21.0.12.1, Maven 3.9.11, **PostgreSQL 18.1** (binary chính thức qua zonky 18.1.0, cluster TEST riêng), Xvfb, fixture MOCK. `mvn -fae clean verify` (trên `d13b3ff`) **exit 1**. Với `-Dmaven.test.failure.ignore=true` (exit 0 chỉ do cờ): client 745/3 FAIL `ControlledProcessHarnessTest` (baseline Linux, `main` 3/3 FAIL, Windows CI 8/8 PASS), server 443/1 FAIL LT04 GUI (baseline, FAIL cả trên `main`). PASS: E2E giám thị ↔ thí sinh (5/5 lượt sau fix `56ea3e2`), RichExamImportPostgres 17/0, RichSessionLifecyclePostgres 8/0, RichExamMigrationPostgres 43/0, ExamRuntimePostgres 38/0. CI Windows run 39354563109 success (PG/GUI skip). Flyway 6 kịch bản PG18 ([evidence](../evidence/release-v2-20261020/flyway-scenarios-pg18.txt)). **NOT RUN:** Windows PG18/GUI, LAN, nghe thật. Không nâng AT/MT/LT/IT.

20/10/2026 — Tích hợp EX-06 + Proctor UI + V2, nhánh `integrate/ex06-proctor-20261020` (source `64b7c5f`), cloud Linux, OpenJDK 21.0.12.1, **PostgreSQL 16.15**, Xvfb, fixture MOCK. `mvn -B -fae clean verify` **exit 1**: protocol 45/0, client 744 run/4 FAIL `ControlledProcessHarnessTest` (sẵn có), server SKIPPED. Với `-Dmaven.test.failure.ignore=true` (exit 0 chỉ do cờ): client 744/3 FAIL/5 skip, server 443/**1 FAIL**/3 skip, spike 1/0. FAIL server `proctorControlsLt04RealGui…` (bật bằng `TOEIC_PROCTOR_GUI`) tái hiện y hệt trên `448f58e` và `origin/main` `3bdefe6` nên không do tích hợp. Test mới: `ProctorV2ControllerTest` 14/14, `ProctorV2ApiClientTest` 7/7, `ProctorV2SessionPaneGuiTest` 4/4 (MOCK), `ProctorV2E2eGuiTest` server/PG/WS thật PASS 3/3 trên `182f5f9` ([bằng chứng](../evidence/integrate-ex06-proctor-20261020/README.md)). `ProctorRedesignGuiTest.settingsApply…` FAIL 1 lần trong một lượt suite, chạy riêng PASS 3/3 (flaky, chưa rõ). **NOT RUN:** Windows/MSI/LAN, PG18, nghe thật, token hết hạn thật. Không nâng AT/MT/LT/IT.

20/10/2026 — EX-06 nhánh `feat/ex06-javafx-rich-renderer-20261020` (source `2b003a5`). `mvn clean verify` có PG + GUI + E2E bật: protocol 45/0, **server 443 run/0 fail/4 skip**, client 683 run/**3 FAIL** chỉ ở `ControlledProcessHarnessTest` (lỗi có sẵn khi chạy Linux, không thuộc EX-06), monitoring-spike 1/0. BUILD SUCCESS do chạy với `-Dmaven.test.failure.ignore`. Test EX-06: `RichExamModelTest` 9/9, `RichImageStoreTest` 6/6, `VisualReadyCoordinatorTest` 9/9, `RichExamViewGuiTest` 6/6 (JavaFX Stage thật dưới Xvfb, MOCK), `RichAttemptE2eGuiTest` 1/1 với server + PostgreSQL 16.15 + WS thật (chạy 3/3 lần). Môi trường: cloud Linux, OpenJDK 21.0.12, PostgreSQL 16.15 (không phải bản 18 đã khóa), âm thanh qua ALSA null, fixture MOCK. **NOT RUN:** nghe thật, Windows/MSI, LAN/máy hai, `demo-ex06-v2.ps1` trên Windows. Không nâng AT/MT/LT/IT.

20/10/2026 — EX-07 source nhánh `feat/ex07-rich-session-lifecycle-20261019` (commit `f5df3af`): `RichSessionLifecyclePostgresTest` **8/8 PASS** (race START×2 và START×takeover lặp 3 lượt PASS); `mvn clean verify` protocol 45/0, **server 443 run/0 fail/0 error/4 skip**, client 652 run/**3 FAIL** `ControlledProcessHarnessTest` (có sẵn trên main khi chạy Linux, không thuộc EX-07). Môi trường: cloud Linux, OpenJDK 21.0.12, **PostgreSQL 16.15** (không phải 18 đã khóa), fixture MOCK CC0. ADR S08/S09/S10/S11/S15, V03/V04/V14–V20, WS delivery V2, GUI/LAN/hearing **NOT RUN**. Không nâng AT/MT/LT/IT.

19/10/2026 — EX-03 source9244321: **mvn clean verify1098PASS/6SKIP/0FAIL/0ERROR**,114case mới PASS, Windows11/JDK21.0.8/Maven3.9.11/PostgreSQL18.1 test cluster SCRAM riêng. Fresh1–12,14/upgrade12→14/preservation; rich200Q/775options/76groups/57media,3documents Email/Notice/Reply/54audio ordering; strict JSON/graph/provenance/Unicode/secret query negatives, local bytes/corrupt/missing, RBAC, duplicate barrier, midway/deferred/file-change rollback, v1 import/session/save/submit/scoring PASS. Fixture200Q/media MOCK tự soạn, HTTP/Flyway/PG thật; commit-manager/IO fault unit MOCK ghi riêng. Cleanup0schema/stop0;3JAR receipts. [Report, failures trước sửa và6SKIP cụ thể](EX03_RICH_IMPORTER_VERIFICATION.md), [evidence](../evidence/ex03-rich-importer-20261019/verification.json). Windowsjunction EX03 PASS thật không thay hai symlink SKIP nền. GUI/installed/app-image/hearing/LAN/máy2 và dataset200production NOT RUN; không nâng tracker/AT/MT/LT/IT hoặc gọi EX03–08 DONE.

19/10/2026 — Sửa CI73: [evidence](../evidence/v2-c-mon02/ci73-fix-20261019.json), [report/root-cause limits](../evidence/v2-c-mon02/REPORT.md). Source1e75924: module clean verify35PASS/0FAIL/ERROR/SKIP; focused1case×3PASS, không3case độc lập. CI39264645975 clean verifySUCCESS/monitoring-spike35PASS, đối chiếu fail39258040529/1failure. Test budget10s riêng, hang150ms và runtime default5s giữ; snapshot/JAR thật,15class/resource hashes khớp bảncũ. Physical0/12NOT_RUN, không nâng hardware/ADR/gate; CI cuối docs-only xem PR73 checks.


19/10/2026 — MON-02 [35 isolated module tests PASS](../evidence/v2-c-mon02/unit-tests.json),0FAIL/ERROR/SKIP, source30166b3; thiết bị MOCK, child/process/PowerShell resource thật. [7 runtime run có source/runID](../evidence/v2-c-mon02/runtime-summary.json): MSFT READY0, Win32 UNKNOWN, fault timeout/cap đúng exit, Ctrl+C direct-file có FINISH/children0/readers true; giữ lượt Tee cũ INCOMPLETE. Physical0/12 NOT_RUN, không nâng MT/QA-04 hoặc task DONE; không full reactor/PG/GUI/E1/E2. [Report/giới hạn](../evidence/v2-c-mon02/REPORT.md).

19/10/2026 — MON-07 PR72 review: kiểm tài liệu/fixture Node.js (UTF-8/JSON/schema/correlation/capability/page, links/source, diff/privacy), kết quả tại [biên bản](mon07-proposal/VALIDATION.md). 7 exchange slice MOCK chỉ một type (có 1 unsupported-type negative), 19 catalog cases giữ riêng; chưa gửi qua mạng/DB. Ma trận22 implementation cases vẫn NOT_RUN; CI của HEAD mới theo GitHub, không thay owner/physical acceptance.

19/10/2026 — MON-07 chỉ kiểm [tài liệu/fixture đề xuất](mon07-proposal/VALIDATION.md): JSON MOCK/required fields/ACK/errors/correlation và link/source/diff/privacy; kết quả chi tiết ở biên bản. [Ma trận implementation](MONITORING_MULTISIGNAL_CONTRACT_PROPOSAL.md) 22 hàng đều **NOT_RUN**; không nâng AT/MT/LT/IT, physical, gate hoặc trạng thái task. Không Maven/PG/WS/GUI runtime/E1/E2 trong lượt docs-only.

19/10/2026 — MON-01 technical verification trên nhánh test/v2-c-mon01-process-e2e/base9a97cab, source testf78b407: [report](../evidence/v2-c-mon01/REPORT.md), [15 tiêu chí](../evidence/v2-c-mon01/acceptance-matrix.json), [checklist B](V2_C_MON01_PHYSICAL_HANDOFF.md). Lượt mới65dashboardPASS/0FAIL/0SKIP và C2 REAL WS/PG/proctor GUI PASS; readings/outage/ACK/ended-state fixtures MOCK/SIMULATED. Thêm assertion metadata/time/absence semantics và automatic worker stop SUBMITTED/TIMED_OUT/CLOSED; production không đổi. REAL ProcessHandle/FULL/983regression/42local được kế thừa đúng run/SHA BASE-03, không tính là chạy MON-01 mới. BASE-03 đã sao lưu ngoài repo477raw+27evidence+Gitbundle và kiểm lại hash. BASE-02 approval PENDING, LAN0/B PENDING, MON-01 TECHNICALLY_VERIFIED_PENDING_PHYSICAL_REVIEW; FULL default/DELTA NOT_DECIDED/E2NO. Không tracker/DONE/push/PR/merge hoặc chuyển MON-07/USB.

19/10/2026 — BASE-03 thực thi mới trên worktree/nhánh test/v2-c-base03-delta-gate từ main1d35b53: [báo cáo A–J](../evidence/v2-c-base03/REPORT.md), [evidence/hash](../evidence/v2-c-base03/README.md). FULL163 + DELTA151 focused PASS (314 là subset regression); Maven package với PostgreSQL18.3 TEST riêng983PASS/7SKIP/0FAIL/0ERROR; Python22PASS, oracle72/4negative detected/77file deterministic, local nhiều JVM42/42 và probe WS epoch/sequence bổ sung PASS. Kit22file/29+29checks; LAN0/41 NOT RUN, B PENDING, gateNOT_DECIDED/FULL default/E2NO. Hai lượt thành công0schema/stop0/.env riêng đã xóa; main gốc f98a37f sạch. Chỉ test/tools/docs/evidence, không push/PR/merge hoặc tự DONE/tracker/hours.

19/10/2026 — EX04V11/EX02 review: [report](EX04_V11_VERIFICATION.md), [evidence](../evidence/ex04-v11-20261019/README.md). Reproducer3077f40:2FAIL/0ERROR trên PostgreSQL thật doNULL media_type bypassCHECK/FK; giữ failure. BảnSQL/test975284d thêmV12, không sửaV1–V11: targeted58PASS/0SKIP (43PG/Flyway gồm2HTTP,15unit); full Maven clean verify984PASS/6SKIP/0FAIL/0ERROR, targeted làsubset. Windows11/JDK21.0.8/Maven3.9.11/PG18.1, clusterSCRAM/database test riêng; mỗi lượt0schema còn lại/stop exit0. Seed/GUI/packaged/hearing/LAN NOT RUN; fixture đềMOCK tự tạo không chứng minh đề200production. CI củaPR xemGitHubchecks riêng, không dùngCI thayPGproof. Không nâng AT/MT/LT/IT hoặc tracker.

19/10/2026 — seed/schema V11/API docs: generator chạy lặp cho cùng SHA-256 `8cc6f345...78d2b86`, kiểm đúng 200 câu/775 options/42 group/54 audio/38 ảnh/292 block, phân bố `6/25/39/30/30/16/54`, 0 owner sai, 0 correct option mồ côi và output deterministic PASS. `mvn test` source cuối **933 PASS**,0failure/error,59skip opt-in; package server SUCCESS và JAR chứa V11. Lần Maven sandbox đầu FAIL trước test vì cache bị trỏ `C:\.m2`; chạy lại bằng local repository đúng đã PASS. Audit 25/25 HTTP route,17/17 WS message,0 link hỏng và `git diff --check` PASS. PostgreSQL/Flyway V11 runtime **NOT RUN**: Docker daemon không hoạt động, DB `.env` ở5433 từ chối kết nối; PostgreSQL khác ở5432 từ chối credential dự án. Answer key/group mapping/candidate-facing options/media local/playback/render vẫn NOT VERIFIED/NOT RUN; seed giữ `importable:false`, EX-01/EX-08 chưa DONE.

19/10/2026 — EX-01 input publication: kiểm5/5 SHA256, mỗi file200 article giữ nội dung, số audio/image elements giữ nguyên, không còn script/form/hidden inputs/nonce/inline handlers; PASS trên Windows PowerShell5.1/.NET với input thật người dùng cung cấp. [Phạm vi và manifest](../data/seedingTest/README.md). Phát hiện test04=test05; không phải5 đề riêng. Chỉ data/docs, Maven/PG/GUI/media download/hearing/import NOT RUN; không nâng AT/MT/LT/IT, EX-01/EX-08 hoặc physical acceptance.

18/10/2026 — Task2.1 publication chỉ kiểm ancestry/diff/text-privacy/hash artifact/41caseNOT RUN/link/cú pháp lệnh runbook; [audit công bố](../evidence/t3-c4-physical-prep-20261018/PUBLISH.md). Không chạy lại Maven/PG/fault runtime hoặc LAN local; [Draft PR52](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/52) có CI tự chạy theo workflow. Kit29/29PASS giữ provenance Task2, không nâng kết quả nghiệm thu. LAN0/41 NOT RUN, B review PENDING.

18/10/2026 — T3-C4 physical preparation:29/29kiểm tra kit PowerShell PASS, lặp29/29trên ZIP giải nén;22file hash, trạng thái41caseNOT RUN và tamper rejection PASS. Fixture MOCK origin/map/file, không gọi mạng. LAN **NOT RUN,0case**; protocol probe/fault runtime/SQL/nghe thật/cleanup vật lý NOT RUN, human B PENDING. Không chạy Maven/CI mới vì chỉ script TEST/docs, không nâng MT/LT/IT hoặc dùng local42/42thay LAN. [Báo cáo](../evidence/t3-c4-physical-prep-20261018/REPORT.md).

18/10/2026 — PR47 retarget main: focused client148 PASS (RealtimeClient63), server monitoring/network118 PASS/4SKIP; clean test và package mỗi lượt928 PASS/5SKIP,0failure/error với PostgreSQL TEST thật. T3-C2 restart/resync PASS; T3-C4 lượt mới 42/42 PASS LOCAL. Có regression FAIL trước sửa và hai lượt cấu hình DB FAIL được giữ riêng; không dùng 42/42 lịch sử làm PASS mới. Xem [bàn giao PR47](../evidence/pr47-stabilize-20261018/REPORT.md). Các trạng thái 27 case/LAN/owner review không được nâng bởi lượt local này.

17/10/2026 — T3-C4 gate trên source `2b6c9ce`, base PR #43 `772c6e9`: focused client 77 PASS; local live gate 42/42 PASS trên một Windows host với PostgreSQL TEST schema riêng; full Maven BUILD SUCCESS (protocol45/client610/server219/spike1, 48 PostgreSQL test skip ở lệnh full, 0 fail/error). Lượt local gate đã chạy PostgreSQL thật. MT02–MT06 giữ `PARTIAL` vì ACK/base faults là SIMULATED và chưa có LAN hai máy/human B review. Gate `PARTIAL / NOT DECIDED`; FULL mặc định. [Evidence 17/10](../evidence/t3-c4/2026-10-17-lan-gate.md), [runbook](MONITORING_DELTA_LIVE_GATE.md).

16/10/2026 — Kiểm E1/T4-C1 riêng: 87Java targeted +11Python PASS; fixture CPU trước50% sai/sau10% đúng, Windows smoke và rejoin byte-identical PASS. Đây không nâng MT/IT hoặc gate thành PASS. Raw E1 lịch sử chưa có để tái xác nhận; [báo cáo](../evidence/t4-c1/2026-10-16-review.md), [lệnh](E1_T4C1_MEASUREMENT.md).

Mô tả đầy đủ từng case (cách gây tình huống, kết quả bắt buộc) ở `docs/plan-v1/03_KIEM_THU_VA_THUC_NGHIEM.md`. File này chỉ theo dõi đã chạy chưa và bằng chứng ở đâu.

Trạng thái dùng: `NOT RUN`, `PASS`, `FAIL`, `BLOCKED`; `PARTIAL` khi chỉ một phần case được kiểm (MT01 local C2). Chỉ ghi `PASS` khi đã chạy thật và có bằng chứng.

Mỗi lần chạy ghi đủ: commit SHA, OS/JDK/PostgreSQL, cấu hình, lệnh hoặc bước chạy, kết quả mong đợi, kết quả thật, log hoặc ảnh, người chạy. Lưu bằng chứng vào thư mục `evidence/<testId>/` (tạo khi có lần chạy đầu tiên).

## Answer / Submission

| ID | Tóm tắt | Owner / review | Task | Trạng thái | SHA | Bằng chứng |
|---|---|---|---|---|---|---|
| AT01 | Thí sinh B không đọc/sửa được bài của A, trước và sau khi nộp | A / C | T2-A4, T3-A4 | **PASS** | `60e1ced` | `evidence/t2-a4/2026-10-05-verification.md` |
| AT02 | Chưa login hoặc sai role gọi import/start; WS thiếu token | A / C | T1-A2, T2-A1 | **PASS** | `5fafab2` | `evidence/t2-a1/2026-10-05-verification.md` |
| AT03 | Revision 42 rồi 41: bản cũ không ghi đè | A / C | T2-A2 | **PASS** | `443458d` | `evidence/t2-a2/2026-10-05-verification.md` |
| AT04 | Cùng revision cùng nội dung, khác nội dung, đổi thứ tự key | A / C | T2-A2 | **PASS** | `443458d` | `evidence/t2-a2/2026-10-05-verification.md` |
| AT05 | requestId lặp; rollback không ACK thành công | A / C | T2-A2, T4-A1 | **PASS** | `443458d` | `evidence/t2-a2/2026-10-05-verification.md` |
| AT06 | Autosave cũ đến sau submit không đổi bài đã chốt | A / C | T2-A3 | **PASS** | `f28099b` | `evidence/t2-a3/2026-10-05-verification.md` |
| AT07 | Chờ khóa qua deadline: quyết định theo giờ sau khóa | A / C | T2-A4 | **PASS** | `60e1ced` | `evidence/t2-a4/2026-10-05-verification.md` |
| AT08 | Submit lặp, timeout, save đến gần nhau: một kết quả, chấm một lần | A / C | T2-A3, T4-A1 | **PASS** | `f28099b` | `evidence/t2-a3/2026-10-05-verification.md` |
| AT09 | Request của writer cũ không ghi được sau takeover | A / C | T2-A4 | **PASS** | `60e1ced` | `evidence/t2-a4/2026-10-05-verification.md` |
| AT10 | Không nhận đáp án sau khi chốt hoặc sau deadline | A / C | T2-A4 | **PASS** | `60e1ced` | `evidence/t2-a4/2026-10-05-verification.md` |
| AT11 | Server40/client42, mất ACK, reconnect, app mới | B / A | T2-B4,T4-B1 | PARTIAL — REAL PG/HTTP exact40/42, Flow41/GUI11 PASS subset; physical/LAN/human NOT RUN | c1601b5 | [Overnight](../evidence/overnight-20261015/phase-06/verification.md) |

## Monitoring

| ID | Tóm tắt | Owner / review | Task | Trạng thái | SHA | Bằng chứng |
|---|---|---|---|---|---|---|
| MT01 | Full snapshot hợp lệ; event không lặp mỗi poll; collector không chạy trên giám thị | C / B | T1-C2/C3, T2-C1/C2 | PARTIAL — C1/C2 kỹ thuật + process/proctor component REAL PASS; candidate GUI/LAN chưa đủ | C2 `fdac531`, source/JAR manifest | `evidence/t2-c2/2026-10-10-verification.md`; C1/ProcessHandle/Edge/gate/full separation hồi quy mới PASS |
| MT02 | Replay cùng message sau mất ACK | C / B | T3-C3/C4 | PARTIAL — LOCAL production ACK observer-loss/frozen retry/no duplicate touch/oldACK PASS; fault SIMULATED, LAN/B review NOT RUN | `d9475eb` + C3 `d813cb1` | [C4 evidence](../evidence/t3-c4/2026-10-13-verification.md) |
| MT03 | Thiếu một delta → UNSYNCED, yêu cầu full | C / B | T3-C3/C4 | PARTIAL — LOCAL REAL RESYNC parser/callback/fresh FULL/last-known/GUI recovery PASS; future-base SIMULATED, LAN/B review NOT RUN | `d9475eb` + C3 `d813cb1` | [C4 evidence](../evidence/t3-c4/2026-10-13-verification.md) |
| MT04 | Delta epoch cũ không sửa state epoch mới | C / B | T3-C3/C4 | PARTIAL — LOCAL restart/oldsocket oldepoch rejected PASS + C3 delta oldepoch replay; LAN/B review NOT RUN | `d9475eb` + C3 `d813cb1` | [C4 evidence](../evidence/t3-c4/2026-10-13-verification.md) |
| MT05 | Restart collector hoặc server → cần epoch và full mới | C / B | T3-C3/C4 | PARTIAL — REAL owned candidate kill/server restart/new epoch/full + revoke/capability loss PASS; one host, LAN/B review NOT RUN | `d9475eb` + C3 `d813cb1` | [C4 evidence](../evidence/t3-c4/2026-10-13-verification.md) |
| MT06 | Không lùi state; full và delta cho cùng state và event set | C / B | T3-C3/C4 | PARTIAL — independent root metadata/stateHTTP-push/eventID-content SQL + C3 full/delta oracle PASS; backgroundOS not frozen, LAN/B review NOT RUN | `d9475eb` + C3 `d813cb1` | [C4 evidence](../evidence/t3-c4/2026-10-13-verification.md) |
| MT07 | Event muộn, queue tràn; snapshot mới không xóa gap | C / B | T2-C2 | PARTIAL toàn luồng — late/dedup/conflict/gap/history/full-isolation/proctor component kỹ thuật PASS; candidate GUI/LAN NOT RUN | `fdac531` | `evidence/t2-c2/2026-10-10-verification.md`; REAL PG/HTTP/WS/collector worker, readings MOCK/faults SIMULATED; raw/ảnh riêng |
| MT08 | Kill client → server tự chuyển UNKNOWN | C / B | T2-C2 | PARTIAL toàn luồng — hard-kill A4/B3/proctor component và C2 tự UNKNOWN/reconnect/history mới PASS; candidate GUI/LAN NOT RUN | C2 `fdac531`, nguồn/JAR trong metadata | `evidence/t2-c2/2026-10-10-verification.md`; kill JVM sở hữu REAL riêng, không gọi ngắt socket mô phỏng là kill |

## Listening và tích hợp

Full-only retry/epoch/order/reconnect đã kiểm trong acceptance T2-C1 bên dưới. Không đổi MT02–MT06 của delta thành PASS và không coi full/C4 log là E1/E2.

| ID | Tóm tắt | Owner / review | Task | Trạng thái | SHA | Bằng chứng |
|---|---|---|---|---|---|---|
| LT01 | Thiếu audio hoặc sai checksum → không READY | B / A | T3-B1 | PARTIAL — local bytes + production HTTP/PG + view/Media/app-image Unicode PASS; xác nhận nghe thủ công/review A/máy thứ hai NOT RUN | `89082e3d43a322c0e0e210f393cc3b4bf49bd7da` | [T3-B1](../evidence/t3-b1/2026-10-14-verification.md), [backend A1](../evidence/t3-a1/2026-10-13-verification.md) |
| LT02 | Start lặp không phát lại; lỗi media khóa và ghi gián đoạn | B / A | T3-B2/B3 | PARTIAL — start lặp, Media/câu hỏi/lifecycle và MEDIA_ERROR lock+journal+event PASS tự động; review A/lỗi codec thủ công chưa chạy | Branch T3-B3 | [T3-B3](../evidence/t3-b3/2026-10-14-verification.md), [backend A2](../evidence/t3-a2/2026-10-13-verification.md) |
| LT03 | Mất mạng giữa audio; mở lại không tự phát hay resume | B / A | T3-B3 | PARTIAL — JavaFX MediaPlayer thật dừng khi disconnect, reconnect/status không replay PASS; backend timeout/takeover/restart PASS; rút mạng/kill app thủ công, LAN và review A chưa chạy | Branch T3-B3 | [T3-B3](../evidence/t3-b3/2026-10-14-verification.md), [backend A2](../evidence/t3-a2/2026-10-13-verification.md) |
| LT04 | Tổ chức lại: attempt mới, dữ liệu và deadline cũ giữ nguyên | B / A | T3-B4, T3-A2 | PARTIAL — server và GUI integration B4 local PASS; human/LAN chưa chạy | `06fd215` | [B4](../evidence/t3-b4/2026-10-15-verification.md), [A2](../evidence/t3-a2/2026-10-13-verification.md) |
| IT01 | Chạy từ package trên máy không có IDE, đường dẫn có dấu | B / A | T1-B4, T3-B5, T5-B1 | PARTIAL — MSI local Unicode/quyền thường/runtime đi kèm/UI/audio/đóng/gỡ PASS; máy hai không IDE/LAN/nghe thật/review A NOT RUN | `acac38d` | [B5](../evidence/t3-b5/2026-10-15-verification.md) |
| IT02 | 2 thí sinh + 1 giám thị; một client gửi JSON sai hoặc rớt | A / C | T3-A4, T4-B1 | NOT RUN | | |
| IT03 | Mạng hoặc quét chậm, UI vẫn phản hồi; đóng app dọn worker | B / A | T1-B2, T4-B1 | NOT RUN | | |
| IT04 | DB hoặc server tắt giữa lúc xử lý; khởi động lại | A / C | T3-A3, T4-A1, T4-B1 | NOT RUN | | |

## Thực nghiệm

| Mã | Nội dung | Ai chạy | Task | Trạng thái | Nơi lưu raw |
|---|---|---|---|---|---|
| E1 | Poll250/500/1000/2000ms ×3repeats | B chạy,C review | T4-B2,T4-C3 | PARTIAL — 12 REAL full-config runs COMPLETE/checksums/repro PASS; human C method review pending, giới hạn parent lifetime/heap | [Data](../evidence/overnight-20261015/phase-06/e1/analysis.md) |
| E2 | Full snapshot vs delta, 3 trace × 3 lần × 2 chế độ (18 run) | C | T4-C2 | NOT RUN | |
| Tải | 1/5/10 client giả lập, 3 lần mỗi mức (9 run) | A | T4-A2 | NOT RUN | |

## Kiểm tra acceptance chặng 1 (ngoài 27 case chính thức)

Các kiểm tra dưới đây không thay đổi trạng thái AT/MT/LT/IT ở trên.

| Ngày | Phạm vi | SHA | Môi trường | Kết quả | Bằng chứng |
|---|---|---|---|---|---|
| 03/10/2026 | Maven build + unit test T1-A1/B1/C1 | `864f701` | Windows 11, JDK 21.0.8, Maven 3.9.11 | PASS, 5 module; 7/7 test | `evidence/stage1/2026-10-03-skeleton-smoke.md` |
| 03/10/2026 | Reset PostgreSQL, Flyway V1, login candidate/proctor/sai | code sau đó commit tại `bb48376` | PostgreSQL 18.6 Docker | PASS; 200/200/401 | `evidence/stage1/2026-10-03-skeleton-smoke.md` |
| 03/10/2026 | ProcessHandle probe | `1f012c4` | Windows 11, user thường | PASS có giới hạn; 133/200 process thiếu metadata | `evidence/stage1/2026-10-03-skeleton-smoke.md` |
| 03/10/2026 | Windows app-image local | `864f701` | JDK/jpackage 21.0.8 | PASS local; chưa chạy máy thứ hai | `evidence/stage1/2026-10-03-skeleton-smoke.md` |

## Review-fix PR #1 — 03/10/2026

Code được kiểm: `961feca789fa8d9e3ded554d0ecb21fccbe43d47`. `mvn test` chạy trước commit trên cùng code; `mvn package` chạy sau commit. Môi trường: Windows 11 x64, Oracle JDK 21.0.8, Maven 3.9.11, qua terminal WSL. Người chạy: Codex Agent. Không thay đổi trạng thái 27 case chính thức ở trên.

| Kiểm tra | Kết quả | Bằng chứng |
|---|---|---|
| `mvn test` | PASS — 5/5 module, 42/42 test; 0 failure/error/skipped | `evidence/stage1/2026-10-03-review-build.txt` |
| `mvn package` | PASS — 5/5 module, 42/42 test; fat JAR client được tạo | Cùng file trích log build |
| Login HTTP 200: malformed JSON, thiếu user, role ADMIN, body rỗng | PASS — future lỗi InvalidServerResponseException; không trả DTO; thông báo UI chung được kiểm ở mức hàm | LoginApiClientTest và trích log build |
| Các trường bắt buộc thiếu/null/blank/sai kiểu; JSON null/array/user sai kiểu/trailing data | PASS — response bị từ chối an toàn | LoginApiClientTest (35 lượt test mới tổng cộng) |
| LAN URL, thiếu scheme, HTTP chậm bằng latch, map candidate/proctor; HTTP proctor hợp lệ và HTTP 401 JSON/non-JSON | PASS — unit/HTTP local; không phải LAN máy thứ hai | LoginApiClientTest |
| Candidate/proctor/sai mật khẩu/server tắt trên GUI thật | NOT RUN — không có công cụ thao tác GUI trong phiên Agent | Phần review-fix trong smoke evidence |
| LAN second-machine test | NOT RUN — chưa có máy Windows thứ hai thật | Cùng smoke evidence |

Reset/migration PostgreSQL, HTTP login thật vào server, app-image và probe ProcessHandle không chạy lại trong phiên review; kết quả lịch sử ở các dòng acceptance phía trên vẫn thuộc phiên trước. Kiểm tra source xác nhận sendAsync/Platform.runLater/stop() vẫn giữ; chưa thay cho test tương tác GUI.

## Verification cuối trước merge PR #1 — 03/10/2026

HEAD đối chiếu: `ceb8131a558660057cf62c46f2466db8515b9b4e`. Chỉ cập nhật docs/evidence, không chạy lại build/test. Source không đổi so với code đã kiểm tại `961feca`; 42/42 PASS và package PASS tiếp tục trỏ tới log lượt chạy thật trước.

| Kiểm tra | Trạng thái | Lý do / bằng chứng |
|---|---|---|
| PR description phản ánh 42/42 test, package và review-fix | PASS | GitHub update PR #1 thành công; không còn dòng 7/7 tests cũ |
| Candidate GUI | NOT RUN | Không có công cụ thao tác GUI thật |
| Proctor GUI | NOT RUN | Cùng lý do |
| Wrong-password GUI | NOT RUN | Cùng lý do |
| Server-off / bad URL GUI | NOT RUN | Cùng lý do |
| Candidate/proctor/wrong password qua LAN máy thứ hai | NOT RUN | Không có máy Windows thứ hai thật được cung cấp |
| A reviewed B | NOT RUN | Chưa đủ xác nhận review thực tế; GitHub chưa có review/comment |
| B reviewed C | NOT RUN | Cùng lý do |
| C reviewed A | NOT RUN | Cùng lý do |

Bằng chứng và giới hạn xác nhận: `evidence/stage1/2026-10-03-merge-readiness.md`. Không thay đổi trạng thái 27 test chính thức hay TRACKER.json. Ready to merge: NO.

## Acceptance T2-C2 — 10/10/2026

Code `fdac531`, base main683adfc sau PR#18. Java425/package PASS (protocol32/client250/server142/spike1), Python10 PASS chạy mới. C2 Spring/PostgreSQL18.6/HTTP/WS/collector worker/proctor Stage thật PASS: tự UNKNOWN không gọi scan bằng tay, B vẫn ONLINE/event ACK, reconnect ONLINE và interruption/recoveredAt giữ nguyên; event từ socket cũ/offline có nhãn đúng, retry DB1/warning1, đổi payload CONFLICT, LIVE retry giữ metadata lần đầu, client date2099 không suy ra delay. Late event không thay full mới; full giữ lịch sử event/gap/interruption. Queue1/drop2/gap retry một dòng, HTTP/GUI đọc đúng; deferred event COMMIT rollback không ACK/warning giả, retry thành công; legacy/V7→V8, role/foreign/CLOSED/SUBMITTED/TIMED_OUT/assignment/token revoke và cleanup PASS.

Inputs process MOCK, outage/write/ACK loss SIMULATED có nhãn; transport/DB/timeout worker REAL. A3/A4/C3/C4/B3-Gui/C1-Gui hồi quy mới PASS, gồm owned JVM hard-kill REAL, ProcessHandle/owned Edge REAL, socket+heartbeat sống không full/CLOSE/HTTP read/manual maintain → tự STALE/TTL4000ms/capacity1 và shutdown dọn RAM/worker. Không bật scheduling toàn app. Raw cuối6endpoint/63category COMPLETE/drop-unwritten-pending0, metadata clean fdac531, JSON/CSV regenerated byte-identical. [Evidence mới](../evidence/t2-c2/2026-10-10-verification.md) có negative test trên main cũ và các lỗi exploratory đã sửa, ảnh, transcripts, configs, checksum.

MT01/MT07/MT08 giữ PARTIAL toàn luồng do candidate GUI/LAN chưa chạy; human A/B review NOT RUN. Không làm C3–C5/delta/E1/E2 hoặc cập nhật tracker. Contract mới QD-13 dùng nguồn kết nối, không wall-clock delay; legacy/trước heartbeat ACK đầu UNSPECIFIED; LIVE chỉ cùng socket. Gap mới đọc bằng refresh, không push riêng.

## Acceptance T2-C1 — 10/10/2026

Base `8eba405`, code `6acd031`; Windows11/Temurin21.0.10/PostgreSQL18.6/Maven3.9.15/Python3.15.0b3. Agent chạy. Baseline trước sửa Java376/A2 PASS; sau sửa Java408/package/Python10 PASS. A2/A3/A4/C2/C3/C4/B3 và B2 riêng trên owned TEST DB PASS. B2 wrapper DB dev có lỗi tiền điều kiện scope rỗng; không xóa fixture để làm test qua. A4/B3 harness hỗ trợ migration mới và B3 tab mới; C3 ERROR phải correlate request gap.

T2-C1 REAL HTTP/WS/PG/ProcessHandle/owned Edge/proctor JavaFX component PASS: full1, Edge xuất hiện/biến mất giữ event, reconnect/new epoch/old epoch STALE, retry/conflict, full rỗng, role/scope, CLOSE/STale, history gap/interruption còn và dọn workers. Fault process sets MOCK; queue/scan failure/order/TTL/late callback/canceled socket write bằng MOCK unit. 4file measurement FINAL, drop/unwritten/pending0, summary COMPLETE. Chi tiết, lỗi ban đầu đã sửa, configs thực và ảnh: [evidence](../evidence/t2-c1/2026-10-10-verification.md).

Bổ sung review-fix cùng ngày: tại30fa6d9, maintain Scheduled không được Spring gọi; các test PASS cũ không chứng minh autonomous STALE/TTL. Harness mới trên implementation cũ FAIL phase AUTO_STALE_WITHOUT_HTTP_OR_CLOSE. Code e07bb7e dùng worker riêng; Java413/package PASS (protocol29/client242/server141/spike1). REAL Spring/PG/WS giữ socket và scoped heartbeat, không full/CLOSE/HTTP state read/manual maintain: tự push STALE, TTL4s tombstone, capacity1 từ OPEN B bị từ chối đến ACK B và giữ history PASS. Spring close xóa RAM/dừng worker PASS. A4/C3/C4/B3 hồi quy chạy lại PASS. [Evidence bổ sung](../evidence/t2-c1/scheduling-fix/2026-10-10-verification.md); Python10 thuộc lượt trước, không chạy lại unittest Python vì script phân tích không đổi (summary mới vẫn chạy thật).

MT01 vẫn PARTIAL; GUI candidate toàn app, LAN máy thứ hai, human A/B review, full server restart network và lỗi scan OS thực NOT RUN. Không triển khai task C2–C5 chặng2/delta/E1/E2. Không reset DB/migration/tracker/kế hoạch.

## T1-B2 — kiểm 03/10, ghi nhận 04/10/2026

Code SHA: `75d4b9fc794c4a1939b47bcdd990ffc0def52164`, base main `34a7835`. Windows 11 x64 / Oracle JDK 21.0.8 / Maven 3.9.11, gọi từ WSL; người chạy Codex Agent. Không dùng PostgreSQL/server thật trong phiên này. Evidence tổng: `evidence/t1-b2/2026-10-04-verification.md`.

| Kiểm tra | Kết quả | Bằng chứng / giới hạn |
|---|---|---|
| mvn test | PASS — 5/5 module, 70/70 test, 0 failure/error/skipped | `evidence/t1-b2/2026-10-03-mvn-test.txt`; realtime 28/28 MOCK |
| mvn package | PASS — 5/5 module, 70/70 test, fat JAR client tạo được | `evidence/t1-b2/2026-10-03-mvn-package.txt`; shade cảnh báo module-info/MANIFEST trùng |
| Fragment >=2 phần, parse chỉ last=true; request(1) mỗi fragment; message quá lớn | PASS — MOCK | RealtimeClientTest; không phải socket server thật |
| Heartbeat 2s khi CONNECTED; không gửi sau disconnect/close, không tích lũy khi write kẹt | PASS — MOCK, thời gian ảo | RealtimeClientTest + MockScheduler |
| Mất connection → RECONNECTING; delay tăng/cap; hết 4 retry → FAILED | PASS — MOCK | Test budget 1+4 lần mở, không sleep dài |
| Reconnect dùng socket mới; bỏ fragment/ACK/callback cũ; lỗi auth không retry | PASS — MOCK | Hook opener được gọi lại; chưa chứng minh re-auth thật |
| close hủy heartbeat/retry/handshake, abort socket, close opener, shutdown executor | PASS — MOCK + executor thật | Test worker thật dùng awaitTermination; không phải đóng GUI thật |
| ConnectionState → UI model khóa/mở | PASS — model | Container JavaFX cập nhật qua Platform.runLater; thao tác GUI NOT RUN |
| JSON malformed/type lạ/thiếu trường/sai scope/ACK lệch không crash listener | PASS — MOCK | Message bị từ chối bằng thông báo cố định, message hợp lệ tiếp theo vẫn nhận |
| WS real authenticated, heartbeat server receipt, server-off/reconnect/re-auth | BLOCKED BY T1-A2 | QD-03/endpoint/auth chưa có; không tự bịa contract |
| Login server thật trong phiên B2 | NOT RUN | Không chạy lại smoke T1-A1; kết quả lịch sử giữ nguyên |
| GUI manual / LAN máy thứ hai / shutdown app GUI | NOT RUN | Không có công cụ điều khiển GUI hay máy thứ hai thật |

Không đổi trạng thái IT03/AT02 hoặc 27 case chính thức bằng test MOCK; TRACKER.json giữ nguyên. T1-B2 **PARTIAL / BLOCKED BY T1-A2**.

## T1-A2 — 04/10/2026

Code: `7ac62c4d0bc5120ba10f887efbe5478a693d6385`, base main `34a7835`. Windows 11 x64 / Oracle JDK 21.0.8 / Maven 3.9.11; PostgreSQL 18 Docker. Người chạy: Codex Agent. Evidence: `evidence/t1-a2/2026-10-04-verification.md`.

| Kiểm tra | Trạng thái | Evidence/giới hạn |
|---|---|---|
| mvn test | PASS — 91/91, 5/5 module, 0 failure/error/skipped | `2026-10-04-mvn-test.txt`; 49 tests mới ngoài baseline 42 |
| mvn package | PASS — 91/91, 5/5 module, JAR production | `2026-10-04-mvn-package.txt`; Mockito dynamic-agent/CDS và shade warnings |
| Valid/invalid/expired/exact expiry/revoked/disabled session | PASS | SessionAuthenticationTest; REST/handshake/message network cases; expired/revoked còn kiểm JDBC thật |
| Login public, protected REST thiếu/sai bearer 401, principal đúng user | PASS — real HTTP | AuthenticatedNetworkTest dùng session store MOCK; PostgreSQL smoke /auth/me thật |
| Role đúng/sai 403; own/foreign/proctor scope, check-before-business | PASS — unit + real HTTP/WS | Scope provider MOCK ghi rõ; production deny unknown/all unassigned attempts, không invent data |
| WS missing/invalid/malformed/expired/revoked/disabled header reject | PASS — real java.net.http.WebSocket/Spring | AuthenticatedNetworkTest; thiếu/sai/expired/revoked còn kiểm PostgreSQL smoke |
| Header handshake, heartbeat/ACK correlation, fragmented heartbeat | PASS — real network | Java21 raw WS + Spring/Tomcat; không chứng minh event commit/presence |
| JSON sai/type lạ/foreign scope không crash; client khác còn hoạt động | PASS — real network | Controlled ERROR; client tiếp tục nhận heartbeat ACK |
| Revalidate WS mỗi message, revoke/expiry/disabled → ERROR + close1008 | PASS — real network | MOCK store; PostgreSQL smoke kiểm revoke trên active socket |
| Concurrent send protection | PASS — MOCK raw session, worker thật/latch | ConcurrentRealtimeSendTest kiểm max 1 raw writer với hai caller trùng thời điểm |
| PostgreSQL production smoke | PASS | Script dùng app production, JDBC/Flyway/session thật; không H2, không load test MOCK configuration |
| Token/password không log hoặc reflected trong ERROR | PASS | OutputCapture network test + diff/evidence secret scan; không in credential trong assertion output |
| GUI/LAN, B2 real integration, event DB/presence | NOT RUN | Ngoài task A2; nhánh B2 chưa đổi/merge, A3/C3/A4 chưa cài |

AT02 mới kiểm các thành phần auth/role/scope/WS của A2; endpoint import/start thật chưa có, nên không đánh PASS toàn bộ case AT02 hoặc 27 cases bằng test fixture. TRACKER.json giữ nguyên. Optional clean FAIL vì app-image EXE bị Windows khóa; không ảnh hưởng test/package PASS và không kill process người dùng.

## T1-B2 — real integration 04/10/2026

Source `6d5c7f4335743b42e74b7232b2e219a89be2e9ca`, merge main A2 `f62c732` bằng `57f2a9c`. Windows 11/JDK21.0.8/Maven3.9.11/PostgreSQL18.6; người chạy Codex Agent. Evidence `evidence/t1-b2/2026-10-04-real-integration.md`.

| Kiểm tra | Trạng thái | Bằng chứng/giới hạn |
|---|---|---|
| Baseline sau merge main | PASS 119/119, 5/5 module | Log merged-baseline-test |
| Final mvn test/package | PASS 142/142 mỗi lượt, 5/5 module; 0 failure/error/skipped | Logs real-mvn-test/package; 23 lượt mới phiên này |
| URI HTTP→WS/HTTPS→WSS, no query, header mỗi open | PASS | Opener unit + real HTTP fixture 401/503; production WS smoke |
| Fragment/JSON/bounded writes/ACK correlation/shutdown | PASS | 36 RealtimeClient MOCK test lượt; worker thật được await |
| Unscoped heartbeat/optional collector/ACK, direct ERROR/1008 | PASS | Unit/mock parser; real heartbeat ACK/ERROR; close1008 riêng là MOCK test |
| Candidate/proctor real login → chính B adapter → ACK | PASS | Production Spring/JDBC/Flyway/PostgreSQL; không H2/provider MOCK |
| Invalid/expired/revoked → auth FAILED, no old-token retry | PASS | PostgreSQL real smoke + controlled unit tests |
| Owned server stop/restart → lock model/reconnect/fresh auth | PASS | Real process/network; không phải GUI manual |
| Retry budget exhausted + owned worker/server cleanup | PASS | Real smoke và unit shutdown |
| Token/password/private path diff/evidence scan | PASS | Security scan log, fixed smoke stdout, empty stderr |
| GUI manual/LAN/cross-review A | NOT RUN | Chưa có công cụ desktop/máy/reviewer thật |

Không đổi trạng thái 27 case chính thức: IT03 mới kiểm headless model/worker, chưa thao tác GUI; event/presence/queue/dashboard chờ A3/A4/C3/B3. TRACKER giữ nguyên.

## T1-C2 — 04/10/2026

Source `02789a86c1c9785bc54f31b4584751eae3340f10`, base main `9f01861`. Windows11/JDK21.0.8/Maven3.9.11, người chạy Codex Agent. Evidence `evidence/t1-c2/2026-10-04-verification.md`.

| Kiểm tra | Trạng thái | Evidence/giới hạn |
|---|---|---|
| Final mvn test/package | PASS 175/175 mỗi lượt, 5/5 module, 0 failure/error/skipped | Server51/client123/monitoring1; C2 thêm33 lượt |
| QD-08 v1 filenames/case-insensitive/immutable list | PASS | ProcessPolicyAndSourceTest, không mở rộng policy |
| Immutable restricted-only snapshot + unreadable counts | PASS | Unknown command không phải sạch; lỗi poll clear latest thay vì publish clean snapshot |
| Session/PID/start identity, PID reuse/start null | PASS | Start khác → identity khác; missing start UNREADABLE; restart UUID mới |
| Fixed-delay/no-overlap/thread off caller | PASS | Slow fake source/latch, second poll không chạy khi first giữ latch; interval sau first finish |
| start/stop/restart/no later poll/worker termination | PASS | Interrupt source và await worker; ngăn restart khi old source chưa kết thúc |
| Source/listener/diagnostic listener failures | PASS | Controlled problem enum; poll sau tiếp tục |
| Active/inactive candidate, active/inactive proctor gate | PASS | Lifecycle side effects với MOCK contexts, không chỉ kiểm enum |
| Real ProcessHandle Windows source | PASS | Smoke:378 scanned,162 unreadable; discord.exe/msedge.exe/zalo.exe observed |
| Owned headless Edge open/close process-set transition | PASS | Profile tạm riêng, chỉ close owned tree; không kill process user |
| Real collector stop/restart/cleanup | PASS | Source production; active context MOCK, không giả assignment |
| GUI manual/LAN/review B | NOT RUN | Headless/role-gate unit không phải GUI PASS |

MT01 PARTIAL: local polling/gate/lifecycle đã kiểm; network full/state/event non-dup/persistence còn C3/T2-C1/C2. E1 không chạy, không sửa TRACKER. Smoke đầu FAIL trước sửa PID0 idle validation; after-fix smoke PASS trên code sau đó commit02789a8. Mockito/ByteBuddy + shade warnings không làm fail build.

## T1-A3 — 04/10/2026

Source/test/script `88ef95a2d96db202edf39e3ed5c72f026bfdb7fc`, base main `6b9572e`. Windows11 amd64/Temurin21.0.10/Maven3.9.15/PostgreSQL18.6; người chạy Codex Agent. Evidence `evidence/t1-a3/2026-10-04-verification.md` và sanitized logs.

| Kiểm tra | Trạng thái | Bằng chứng/giới hạn |
|---|---|---|
| Final mvn test/package | PASS 200/200 mỗi lượt; 5/5 module; 0 failure/error/skipped | Server76/client123/spike1, A3 mới25; package không đóng Test/Smoke classes |
| Payload validation và privacy | PASS | ProcessEventTest24 + real WS invalid JSON/field/path |
| Schema clean V1+V2 và upgrade V1→V2 | PASS — PostgreSQL thật | Hai schema TEST riêng; FK/unique SQLSTATE23503/23505 |
| Production assignment scope, login/auth-me | PASS — DB/HTTP/WS thật | Own/assigned ACTIVE allowed; foreign/unknown/CLOSED denied |
| Event commit→ACK→push assigned | PASS — mạng/DB thật | Không broadcast proctor khác; candidate chỉ ACK |
| Duplicate same/changed, concurrent retry | PASS — mạng/DB thật | ACK2/row1/warning1; CONFLICT không overwrite; CyclicBarrier hai workers |
| Deferred COMMIT fail rollback/no success ACK/no warning | PASS — PostgreSQL thật | Trigger TEST chạy tại COMMIT; retryable error/row0 |
| Timeline auth/order/offline recovery | PASS — HTTP/DB thật | Bearer+PROCTOR+scope; received_at,id order; offline event phục hồi |
| Revalidate assignment/session trước push/message | PASS — mạng/DB thật | Xóa assignment→no push/403; revoked proctor/candidate→close1008 |
| Concurrent ACK/ACK và ACK/warning send | PASS — MOCK raw session + latch/worker thật | Max1 raw writer; không dùng sleep để điều khiển overlap |
| Secret/path scan | PASS | security-scan.txt; TEST-only password và synthetic rejected path không credential thật |
| DB-off smoke | NOT RUN | DB dùng chung; dùng COMMIT-failure test an toàn, không gọi là DB-off PASS |
| C human review, GUI/LAN | NOT RUN | Không giả review hoặc desktop demo từ headless network harness |

Không đánh PASS 27 case chính thức bằng test thành phần A3: C3 queue/collector integration, B3 dashboard, A4 presence/full/delta/exam flow chưa làm. TRACKER giữ nguyên. Không thêm QD implementation-detail; QD-05 vẫn áp dụng. Lỗi JDBC Timestamp/test fixtures đã sửa trước lượt PASS, xem evidence.

## T1-C3 — 04/10/2026

Base main `74d2704`. Windows11/Temurin21.0.10/Maven3.9.15/PostgreSQL18.6. Baseline chạy lại PASS200; final mvn test19:37:13 và package19:38:26 UTC+7 PASS253/253 mỗi lượt, client161/server91/spike1; 53 mới (delivery30, scope7, adapter1, gap15). Số lấy từ summary Maven, không cộng XML surefire cũ trong target. Evidence `evidence/t1-c3/2026-10-04-verification.md`.

| Kiểm tra | Kết quả | Phân loại / giới hạn |
|---|---|---|
| First snapshot, 10 poll không trùng, disappearance/reappearance, PID/start reuse | PASS | MOCK snapshots, fake wall/monotonic clock |
| Missing start/quality, source failure, bounded baseline | PASS | MOCK; null→known coi identity mới; không hứa PID reuse hoàn hảo |
| Frozen ID/payload/time, write≠ACK, early/wrong/duplicate ACK | PASS | MOCK transport; deep copy, sáu correlation fields |
| Timeout/backoff/budget/manual retry và permanent/permission errors | PASS | MOCK virtual time; reconnect không reset budget |
| Overflow/frozen gap/next accumulator/heartbeat slot | PASS | MOCK source/transport unit; real delivery bên dưới |
| Late old send completion/ACK, asyncStop, restart chờ blocked scan | PASS | MOCK + latch, không long sleep làm chứng cứ |
| Fresh auth-me, empty candidate scope/proctor gate | PASS | MOCK HTTP unit và REAL TEST login/me; không scan/queue scope rỗng |
| Windows ProcessHandle + owned Edge open/close + C2→C3→B2→DB/ACK | PASS REAL | Profile/process tree riêng; warning kiểm qua WS test, không GUI |
| Lost ACK retry row1/warning1, many real polls | PASS | ACK loss SIMULATED tại observer; HTTP/WS/DB/C2/C3 REAL |
| Offline assigned proctor đọc lại timeline | PASS REAL | Endpoint A3 giữ nguyên; không B3 dashboard |
| B2 reconnect sau owned Spring server stop/restart | PASS | Server/network REAL; queued snapshot MOCK; PostgreSQL không tắt |
| Capacity1 overflow→persist/commitACK gap, dedup/next accumulator | PASS | Snapshots MOCK; WS/PG/ACK REAL; đầu gap ACK loss SIMULATED |
| Deferred gap COMMIT failure→rollback/error→retry | PASS REAL | Trigger TEST riêng, không sửa production handler để bỏ ACK |
| Gap conflict/foreign/CLOSED/unknown/proctor forbidden | PASS REAL | Row không đổi; event state/warning contract giữ nguyên |
| Collector/delivery/B2 workers/subscription cleanup | PASS | Harness đợi worker sở hữu kết thúc; stop unit idempotent |
| A3 regression smoke trên V3 mới | PASS REAL | Scope/revocation/concurrent duplicate/commit failure/timeline PASS |
| DEV fixture Create/duplicate rejection/idempotent Cleanup | PASS REAL | Schema c3_demo_test_UUID riêng, user2 giữ nguyên, public không chạm |
| JAR/security/diff check | PASS | Evidence scan; test/harness/client test dependency không lọt serverJAR |
| Human B review, GUI manual, LAN máy2 | NOT RUN | Không giả Agent/headless review thành review thành viên |

MT01 vẫn PARTIAL tới B3; A4 presence chưa làm. Không thực nghiệmE1, không full/delta/reducer/đề thi, không đổi TRACKER hoặc nguồn01–03. Lượt unit đầu có một assertion gap retry sai giả định thứ tự slot; đã sửa test theo việc event dùng được slot lúc gap backoff, final PASS ở trên. Không dùng lượt lỗi làm chứng cứ PASS.

## T1-A4 — 04/10/2026

Base main99b4f39. Windows11/Temurin21.0.10/Maven3.9.15/PostgreSQL18.6; người chạy Codex Agent. Baseline chạy lại20:48:02 UTC+7 PASS253. Final mvn test21:26:27 và package21:28:28 PASS277/277, client166/server110/spike1, 24 lượt mới; test/package ở checkout build riêng vì JAR dev đang được server người dùng giữ. Source checksum đối chiếu ở security-scan, code không thay bởi checkout. Test và package cùng code hành vi; package bổ sung import IOException tương đương thay FQN trong harness cleanup. Evidence `evidence/t1-a4/2026-10-04-verification.md`.

| Kiểm tra | Kết quả thật | Giới hạn |
|---|---|---|
| Scoped lease start/stop/reconnect/old callback, unscoped/proctor/malformed | PASS | Unit MOCK và HTTP/WS thật; không thao tác GUI |
| Deadline6s fake ticker, wall-clock jump, CAS/race có latch, association bounded/cleanup | PASS | Service MOCK store/clock; không đo LAN |
| PostgreSQL V3→V4, roster role/ACTIVE scope, history permission | PASS REAL | Schema TEST UUID; public vẫn V3 vì giữ server đang chạy |
| Hai candidate B2/C3, hard-kill JVM A riêng, B ONLINE/event/ACK | PASS REAL | Process snapshot MOCK; hard-kill thật, không gọi graceful close là kill |
| Assigned/foreign MONITOR_PRESENCE, old socket, recoveredAt + stable gapId | PASS REAL | WS test giám thị, không GUI dashboard |
| Deferred COMMIT failure heartbeat/timeout, rollback/no fake ACK/push/gap, retry | PASS REAL | TEST constraint trigger; không tắt/reset DB dùng chung |
| Token revoke/CLOSED cleanup, Stop→timeout, offline roster/history, server restart | PASS REAL | Restart server do test sở hữu, không server dev |
| Presence/collector/delivery/B2/child-reader cleanup | PASS REAL | Final run hết workers; thư mục Edge từ lượt hồi quy lỗi còn riêng bên dưới |
| A3 regression | PASS REAL | Event/duplicate/conflict/concurrency/deferred COMMIT/live scope/timeline giữ contract |
| C3 regression | PASS REAL | Windows ProcessHandle/owned Edge thật; ACK loss SIMULATED, overflow source MOCK → gap REAL |
| GUI/LAN/package máy khác/human C review | NOT RUN | B3 còn dashboard/parser/HTTP recovery; chưa nghiệm thu toàn prototype |

Lượt đầu decorator test phát hiện Gson không serialize Instant; đã thêm adapter ISO UTC và chạy lại PASS. Hồi quy đầu A3 dùng scoped ping không collector, chỉnh sang unscoped đúng mục đích transport. Hồi quy đầu C3 lỗi khóa profile Edge sau exit, thêm bounded cleanup retry; lượt final PASS. Lệnh dọn riêng profile sót của lượt lỗi bị kiểm duyệt tự động từ chối (`blocked by policy`), profile còn nhưng kiểm tra không process Edge nào dùng nó; không kill process người dùng. Không lấy lượt lỗi làm evidence PASS. MT08 PARTIAL ở mức case toàn hệ thống, thành phần server A4 PASS; IT02/IT03/GUI chưa đánh PASS toàn bộ. TRACKER/nguon/01–03 và V1–V3 giữ nguyên.

## T1-C4 — 05/10/2026

Base main PR#8 a04aaa2; final source c70c93e sạch trong checkout build riêng. Baseline331 PASS20:26:45 UTC+7; final test366 PASS20:56:53/package366 PASS20:57:53 (+35: protocol23/client7/server5). Tổng protocol23/client227/server115/spike1. Python9 PASS riêng; 3 concurrent cases chỉ bổ sung assertion không tăng test count. Source/script sau build không thay đổi, docs/evidence bổ sung riêng. [Evidence C4](../evidence/t1-c4/2026-10-05-verification.md) và transcripts/hash/raw/summary cùng thư mục.

| Kiểm tra | Kết quả thật | Giới hạn |
|---|---|---|
| Toàn JSON UTF-8 ASCII/Vietnamese/emoji, TX success/failure, retry cùng event | PASS MOCK | Đúng chuỗi serialize/send, ticket terminal1, ACK nghiệp vụ riêng không thêm byte |
| RX fragmented/surrogate/malformed/binary/oversized | PASS MOCK | Full text1; INVALID lọc ID/cause, UNMEASURED nullablebyte; demand/rejection giữ |
| Server ACK/ERROR/warning/presence, decorator enqueue/delegate/race | PASS MOCK + REAL | Byte exact socket; latch enqueue2 chưa write2, release đo đúng2 |
| Counters concurrent, bounded queue/drop, file failure/timeout/flush/disabled | PASS MOCK | Counter độc lập raw; logger hỏng vẫn event/ACK; daemon + bounded close không giữ JVM |
| Summary known trace/hand calc/reproducibility/schema/drop/truncation | PASS MOCK | 220byte =2×100+20, không TX/RX/outcome đếm đôi; 9 Python tests |
| PG18.6/Spring/HTTP/WS/B2 candidate+proctor/dashboard model | PASS REAL | TEST schema/port, cả7types log; nguồn event/overflow MOCK, không GUI |
| ProcessHandle collector production scan/stop | PASS REAL | Windows đọc processesScanned>0 riêng; không dùng MOCK làm bằng chứng process thật |
| Retry + gap overflow | PASS MIXED | ACK observer C3 suppression SIMULATED sau B2 accepted; DB1/dashboard1 trước overflow, capacity1/drop2/gapACK REAL |
| Raw3file/FINAL khỏe/26category/summary regenerated | PASS REAL | TX37message12568byte mỗi outcome; JSON/CSV hash tái tạo byte-identical, raw untouched |
| B3 hồi quy | PASS REAL | C3/event/presence/dashboard, owned hard-kill, stale/reconnect/history, 403/401/cleanup; source MOCK; GUI NOT RUN |
| Worker/TEST schema/security/JAR/protected/source checks | PASS | TEST schemas0, public vẫn V3; no live secret/private path/payload, JAR không test/harness; TRACKER/01–03/nguon/migrations không đổi |
| Human B review/GUI toàn luồng/LAN/WMI/ETW/E1/E2/full-delta | NOT RUN | C4 chuẩn bị công cụ đo, chưa nghiệm thu toàn MT01/MT08 hay thử hiệu năng |

Lượt exploratory phát hiện assertion/control-record và compile signature/constant trong test, đã sửa trước final PASS. Final sạch trên commit c70c93e; không lấy exploratory làm evidence. Automatic review chặn dọn bản sao target tạo thừa; giữ ignored, không retry xóa. Dừng C4; không B4/chặng2, không claim novelty/delta tốt hơn.

## T2-C3 — 10/10/2026

[Evidence mới](../evidence/t2-c3/2026-10-10-verification.md), [lệnh và schema](MONITORING_TRACE.md). Baseline425 chạy mới; final461 PASS (+36, protocol32/client286/server142/spike1), Python10 PASS. Không đổi trạng thái MT02–MT06 vì chưa delta; MT01/MT07/MT08 giữ PARTIAL toàn hệ thống.

| Ca C3 | Kết quả | Mức |
|---|---|---|
| Handwritten empty→Edge→Edge→empty: state0/1/1/0, event1 | PASS | MOCK + production REPLAY |
| Roundtrip/empty versus error/PID reuse/missing metadata/nanosecond identity | PASS | MOCK |
| Reader checksum/missing/corrupt/UTF8/field/version/size/order | Reject đúng | MOCK |
| Writer error/full queue/close/tap failure isolation | PASS, không COMPLETE giả | MOCK latch/failure |
| Duplicate/drop/reorder/reconnect/old epoch/history separation | PASS | REPLAY + SIMULATED |
| Same seed/config/input | Byte-identical report PASS | CLI thật |
| Intentional full mutation/corrupt checksum | FAIL exit1/reject exit2 đúng | CLI thật, nguồn MOCK |
| ProcessHandle capture + owned Edge appear/disappear/cleanup | COMPLETE→replay PASS | Nguồn REAL, gate/fault SIMULATED |
| C1-Gui/C2-Gui/event-C3/C4/A4 | PASS mới | REAL localhost/PG/component, source/fault từng ca xem evidence |
| B human review/A adapter review/candidate full GUI/LAN/T2-C4/delta/E1/E2 | NOT RUN | Không lấy replay thay bằng chứng này |

## T2-C4 — harness và dữ liệu chuẩn độc lập (10/10/2026)

Java493/package PASS; 32 ca mới có expected viết tay và child thật. [Evidence/logs/raw/example/checksums](../evidence/t2-c4/2026-10-10-verification.md); [lệnh](CONTROLLED_PROCESS_HARNESS.md). Không đổi MT02–MT06 delta hoặc E1/E2 thành PASS.

| Kiểm tra mới | Kết quả | Mức bằng chứng |
|---|---|---|
| Same seed/manual seed7/ba loại/giới hạn | PASS | Unit schedule/expected viết tay |
| GT độc lập khi collector luôn empty | PASS | Child REAL, collector readings MOCK |
| Clock offset/thứ tự/không ép target bằng thực đo | PASS | Fixture viết tay + audit raw REAL |
| PID/start/collector, reuse, thiếu start/tên/coverage | PASS | Fixture MOCK riêng |
| Trace lỗi/truncated/hash/mutation/existing output | PASS reject/inconclusive | Unit + CLI bản sao lỗi |
| Child lỗi/noREADY/hang/launchfail/APIcancel/known tree | PASS cleanup | Child REAL + source MOCK |
| Windows30child (10 mỗi200/800/3000), poll500/seed1234 | COMPLETE 27 observed/3 notObserved/0 inconclusive | Child+source REAL, TEST policy; gate SIMULATED |
| Checksum/manifest/rejoin/cleanup/no overwrite | PASS | Raw và CLI thật |
| C3 replay/REAL owned Edge trace, C1-Gui, measurement | PASS | Hồi quy riêng; faults/gate MOCK/SIMULATED theo evidence |
| Event lostACK retry/network | Initial concurrent FAIL; isolated PASS | Hai log retained, cause chưa chứng minh |
| Human B review/OS Ctrl+C/hardkill/candidate full GUI/LAN/E1/E2 | NOT RUN | Không giả bằng unit/replay/smoke |

C2-Gui/A4 không chạy lại trong C4; server production không đổi. Python analyzer không đổi, không chạy lại bộ Python10 trong C4. B review policy/TEST DTO/clock/identity/coverage/cleanup.

## T2-C5 — kiểm tra phương pháp và protocol (11/10/2026)

C5 là thay đổi tài liệu. [Phương pháp](MONITORING_BASELINE_METHOD.md), [kiểm tra mới](../evidence/t2-c5/2026-10-11-verification.md). Link/anchor/source method/schema/default và C3/C4 artifact hashes đối chiếu mới; số chi tiết trong doc-checks.json. Không coi các kiểm tra file này là chạy lại test Java493/Python10 hoặc component smoke.

| Kiểm tra/điều kiện | Trạng thái | Phạm vi |
|---|---|---|
| Source policy8tên/TEST/identity/ACK/RAM/default | Đối chiếu PASS | Không đổi code/wire |
| Markdown local link/anchor, class/method/schema/CLI | PASS | Tài liệu thay đổi |
| C3/C4 hashes, FINAL/manifest, gzip phục hồi | PASS | Đọc artifact cũ, không replay lại |
| C3 PASS có missingBusinessEvents/C4 số27/3 và proxy example | Đối chiếu PASS | Kết quả cũ, không E1 |
| Java/Python analyzer unit/desktop smoke | NOT RUN trong C5 | Kết quả cũ giữ riêng |
| B human review/window/sampler/analyzer E1/delta correctness/E2 | NOT RUN hoặc còn thiếu | Không đánh dấu đủ công cụ/nghiệm thu |

MT01/MT07/MT08 giữ PARTIAL toàn hệ thống; MT02–MT06 delta và E1/E2 không nâng PASS. Merge tài liệu không thay review của B hoặc nghiệm thu GUI/LAN.

## Backend P0 unblock PR17 — 11/10/2026 (local, chưa merge)

Nhánh A từ main12e2838: Maven test/package510 PASS, tích hợp tạm PR17 package582 PASS, đều bật11 regression PG thực và không skip. Native PostgreSQL18.1 cluster test riêng; không reset DB dev. Ba GAP B: owner/assigned proctor đọc SUBMITTED/TIMED_OUT, scheduler tự chốt và deadline bắt đầu ngay takeover entry **PASS kỹ thuật**. B smoke JAR/PG/HTTP28 PASS,0GAP; C1 full/reconnect/autonomous STALE/TTL và C2 late/gap/history/permission hồi quy PASS; Python10 PASS.

[Evidence P0 mới](../evidence/t2-a-p0/2026-10-11-verification.md) ghi source/hash, logs, fixture patch và lỗi exploratory. Không sửa các evidence lịch sử B/C hoặc TRACKER. T2-B3/B4 vẫn PARTIAL; human review, full GUI candidate/proctor, server-off/reconnect, LAN/app-image/audio máy thứ hai NOT RUN. E1/E2 không thay trạng thái. Remote PR17 chưa có commit A cho đến khi được phép push và tích hợp.

## T3-C1 — encoder delta candidate (11/10/2026)

[Guide](MONITORING_DELTA_ENCODER.md), [evidence](../evidence/t3-c1/2026-10-11-verification.md). Baseline mới493 PASS; final531 Java PASS (protocol35/client353/server142/spike1), 38 mới. Test snapshot/clock/transport MOCK, scheduled worker REAL; CLI thực thi thật nhưng network/ACK/epoch/adapter SIMULATED. Production vẫn full.

| Kiểm tra | Kết quả | Phạm vi |
|---|---|---|
| First full ACK / ACK sai8trường / base chỉ ACK | PASS | DeltaSnapshotDeliveryTest |
| Thêm/bớt/multiple/empty/metadata full/PID reuse/null start/reappear | PASS | Delivery + handwritten oracle/replay |
| Unchanged không event/poll, coalescing1.000→queued1 | PASS | Event thật trong test, readings MOCK |
| SOURCE_FAILURE khác empty, retry nguyên message/ACK loss/write error | PASS | Clock/transport MOCK, lỗi SIMULATED |
| Retry bounded/error-STale/fresh scan/OPEN/full1/reconnect/oldcallback | PASS | Client lifecycle; không server delta/resync proof |
| Reconciliation30s/scan failure/pending/cleanup/auth revoke | PASS | Controlled clock, worker scheduled/shutdown thật |
| Replay full/delta/canonical metadata/event ID-content/reconnect/duplicate | PASS | Fixture MOCK + trace ProcessHandle REAL; tất cả ACK/network SIMULATED |
| Delta/full cố ý sai | FAIL đúng exit1 | Oracle độc lập bắt sai từ raw; giữ report |
| TEST C4/future delta drop options | Refused đúng exit2 | Không đổi schema hoặc giả gate support |
| MT02–MT06 gate/live delta/E1/E2/human A/B review | NOT RUN | Không nâng status27case/tracker từ component tests |

Lượt package đầu mới thêm test ERROR STALE FAIL vì còn deadline ACK cũ; sửa deadline rejection candidate về now, bản sửa529 PASS; bổ sung2ca pending-ACK, final531 PASS. Không lấy lượt lỗi làm PASS. Hồi quy DB đầu dừng MIGRATION do Docker chưa chạy; bật Docker/compose volume cũ rồi chạy lại PASS. Kết quả hồi quy mạng/DB mới ghi trong evidence, không suy từ529unit.

## Backend A sync main T3-C1 — 12/10/2026 (local, chờ push)

Merge A6cfe7c4 + main144fe55 thành cadd98d, giữ P0 và encoder/transport/regression C. Maven test/package **548PASS**, checkout detached PR17+A/main package **620PASS**, đều0fail/error/skip và gồm11PG runtime +38test C mới. Smoke B **28PASS/0GAP**, C1/C2/CLI regression PASS, Python10PASS. Scheduler exam riêng, C tự STALE/TTL/presence và shutdown PASS; bài trắng tự TIMED_OUT0/2. [Evidence mới](../evidence/t2-a-p0-sync/2026-10-12-verification.md) ghi source/code/fixture hashes, logs và giới hạn.

PR23 remote vẫn6cfe7c4, PR17 vẫn2f5fc66; chưa push local sync/chưa merge/human review. B3/B4 vẫnPARTIAL; full GUI/server-off/LAN/app-image/audio máy thứ hai/live delta/gate/E1/E2 NOT RUN. Không thay tracker hoặc evidence lịch sử.

## T3-C2 — reducer/resync/freshness (12/10/2026)

[Guide](MONITORING_DELTA_REDUCER.md), [evidence](../evidence/t3-c2/2026-10-12-verification.md). Baseline531 mới PASS; final554 Java/package PASS (protocol43/client361/server149/spike1), Python10 PASS.23test mới = core6/measurement2/client freshness-resync5/parser2/oracle1/server7. Không nâng gate MT02–MT06 hoặc tracker.

| Kiểm tra | Kết quả | Mức |
|---|---|---|
| Full đầu/delta/base gap/dup/conflict/common cache64/atomic payload/full recovery/old binding | PASS | Pure core + store/mock scope/clock |
| Quyền trước duplicate, TTL không gia hạn bởi duplicate/gap | PASS | Unit + REAL JDBC/WS scope CLOSED/revoked token |
| OPEN/full/CLOSE tranh chấp delta/maintenance | PASS | Latch/order có kiểm soát, không sleep quyết định thứ tự |
| Correlated resync/parser/client fresh full cùng epoch, seq0 newOPEN | PASS | Unit + REAL Spring/PG/HTTP/WS |
| Successful unchanged scan/noop, scan hung/failure HB ONLINE → STALE/TTL | PASS | Worker thật, readings MOCK; không maintain/HTTP ở phase auto |
| Spring server restart → instance/epoch mới/full, DB history retained | PASS | REAL restart same test port, persisted auth |
| Oracle missing-first-full/missing-delta/recovery/old, manual fixture/negative CLI/REAL trace | PASS hoặc FAIL/refusal đúng ca âm | Shared production core, independent raw expected; ACK/network CLI SIMULATED |
| Full-Gui/C2-Gui/event/presence/measurement | PASS mới | REAL components, fault/readings đúng nhãn trong evidence |
| DELTA/RESYNC measurement/analyzer/byte summary | PASS | Known types, counters cùng transport hooks; không E2 |
| Human A/B review/whole gate/C3 campaign/fullcandidateGUI/LAN/E1/E2 | NOT RUN | Không giả approval hoặc prototype acceptance toàn case |

Các lượt lỗi fixture/constructor oracle/manifest/DB chưa chạy được giữ trong evidence và sửa trước final. Không lấy kết quả dương cũ thay lượt mới.

## T3-C3 · Kiểm tra mới12/10/2026

- Baseline554 PASS22:16:59; final569/package PASS22:37:27 UTC+7 (protocol43/client376/server149/spike1),15new ReplayCampaignTest invocations.38focused replay tests PASS sau2lượt lỗi fixture label/comparison; không bỏ lỗi production bằng sửa oracle.
- CLI campaign72cases=3MOCKworkloads ×12scenarios ×2modes và4ca âm detected; Java repeat nội bộ + full CLI lần2 byte-identical. Raw/code/JAR/sourceSha đúng; summary/matrix/checksum sinh từ file thực. UNKNOWN/presence/TTL/ACK-loss/retry/feedback/DB trong replay NOT MODELED.
- Ca gap giữ last-known/UNSYNCED rồi full định kỳ hoặc newOPEN/full1 phục hồi; duplicate sau RESYNC không tự SYNCED. Event drop vẫn missingBusinessEvents=1, full không sửa history. Intentional delta/full/event mutations oracle FAIL/CLIexit1, bad config/checksum/TEST/nooverwrite exit2.
- C3 script -RealScan PASS; real ProcessHandle trace47business/40delivered/7missing trong seeded cảmode. C1 CLI regression -RealScan PASS; T2-C3 -RealEdge PASS/owned cleanup, source REAL/replay network SIMULATED,45business/40delivered/5missing trong owned-edge seeded. Không so workloads hai lượt/performance.
- Counterexample compiled against old C2 JAR expected PREVIOUS_CONNECTION actual LIVE FAIL; final actual PREVIOUS_CONNECTION PASS. Chỉ fix replayer receiving connection, không production server/transport/reducer/EventOrigin. First handwritten expected ghi LATE nhầm tên nhãn, sửa theo EventOrigin/PROTOCOL; log nguồn giữ. Seeded reconnect0common checkpoint là fault divergence đúng model, công khai NO_COMMON_SYNC_CHECKPOINT; oracle từng mode vẫn nguyên kiểm.
- Không chạy lại live Spring/PG/WS smokes hoặc Python analyzer vì code liên quan không đổi; kết quả C2 là evidence cũ ghi riêng, không PASS mới. Human B/ChatGPT review NOT RUN theo opt-out; MT02–MT06 toàn gate giữ PARTIAL; T3-C4/GUI/LAN/E1/E2 NOT RUN. [Evidence](../evidence/t3-c3/2026-10-12-verification.md).

- Kiểm cuối exact Git fixture bytes: auto text normalization đã làm manifest/expected blob LF khác workspace CRLF. Commit d813cb1 giữ exact byte; verified package569 PASS22:51:24 và campaign runb46f6a5e-e652-4ec4-8a8b-0201ba5b991a72PASS/4negative/77repeat identical,76case reports byte-identical với earlier d337 run, chỉ summary sourceSHA mới. Final REAL capture46business/40delivered/6missing; earlier47/40/7 giữ riêng, không so performance. JAR không đổi FB05E7D3;243source/blob hashes và222artifact/167gzip PASS. Runtime/doc workingtree ghi đúng dirty filenames; live/Python regressions không chạy thêm.

## T3-C4 local preparation13/10/2026

[Guide](MONITORING_DELTA_LIVE_GATE.md), [evidence](../evidence/t3-c4/2026-10-13-verification.md). Source d9475eb,579Java/package PASS,42local assertions +7affected smoke commands latestPASS; previousfailures retained. OneWindows host/default actual candidate/proctor Applications/two childJVMs/REALProcessHandle-ownedEdge/PG/HTTP/WS. Fault observer/base/source/DB seams SIMULATED, nophysicalpacketloss measurement. Fulldefault, gate20/10notdecided, humanA/B/LANNOTRUN.

MT01/MT07/MT08 thêm LOCAL actualcandidate/proctor Start/FULLfallback/DELTA/history/push/ownedkill/isolation/cleanup PASS, nhưng toàncase vẫn PARTIAL choLAN/human/fullflow. IT02 LOCAL monitoring subset2candidate+1proctor/scopeforgery/kill/isolation PASS; IT03 sourcepark+heartbeat/UI IPC/worker0 subsetPASS. Không nghiệm thu wholeexam/Listening/packageIT01 hoặc IT04; bảng NOT RUN của toànIT giữ nguyên. Replay oracleC3 vàlocalC4 táchriêng; không nângMT02–MT06 gate/E1/E2 thànhPASS.

## Backend A — PR #23 đồng bộ main T3-C3 và review lại P0, 13/10/2026 (local, chờ push)

Commit `9713658` trên nhánh cục bộ `sync/pr23-main-20261013` = head PR #23 `4753149` + main `ee714fc`. Chi tiết: `evidence/t2-a-p0-sync-20261013/2026-10-13-verification.md`.

| Kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| `mvn package` với `TOEIC_PG_INTEGRATION=true` | PASS 586, 0 skip | protocol 43, client 376, server 166, spike 1; gồm 11 test `ExamRuntimePostgresTest` trên PostgreSQL thật |
| Smoke C1 full snapshot, smoke C2 late event/gap | PASS | REAL HTTP/WS/PG trên schema riêng; nhãn MOCK/SIMULATED như script ghi |
| Python analyzer | PASS 10 | Python 3.14; lượt đầu bằng Python 3.9 trên PATH lỗi do phiên bản, không tính |
| Review lại ba sửa P0 (đọc sau khi chốt, scheduler hết giờ, deadline khi vào thi) | Đạt, 4 ghi nhận không chặn | Agent review; human review NOT RUN |
| `ExamFlowPostgresSmoke`, GUI, LAN | NOT RUN trên nhánh này | chạy ở nhánh client của B |

Không đổi trạng thái AT/MT/LT/IT trong bảng chính; `TRACKER.json` giữ nguyên.

### Backend A — PR #23 đồng bộ lần hai với main T3-C4, 13/10/2026 (local, chờ push)

Main tiến lên `cb30f3d` (PR #27) trong lúc làm, nên nhánh `sync/pr23-main-20261013` được merge thêm một lần: commit `c64503f`. `mvn package` với `TOEIC_PG_INTEGRATION=true`: **PASS 596**, 0 skip (protocol 43, client 386, server 166, spike 1; 11 test PostgreSQL thật). Smoke C1, C2 PASS; Python 10 PASS. Smoke T3-C4, GUI, LAN, human review: NOT RUN. Bảng 586 ở mục "PR #23 đồng bộ main T3-C3" là của commit `9713658` trước đó. Chi tiết: `evidence/t2-a-p0-sync-20261013/2026-10-13-verification.md`.

## T3-C5 · Kiểm tra bản nháp tài liệu · 13/10/2026

Đối chiếu source main `f97c081`, class/method/defaults/quyền monitoring, liên kết và anchor, bảng evidence C1–C4, phạm vi diff. Kết quả mới xem [báo cáo kiểm tra](../evidence/t3-c5/2026-10-13-verification.md); không chạy lại Java/Python runtime/smoke/GUI/LAN/E1/E2. Các số PASS trước đó giữ task/SHA/môi trường gốc, không gán cho main mới. FULL mặc định, gate NOT_DECIDED/PARTIAL; B review NOT RUN. Không nâng trạng thái MT toàn hệ thống.

## T3-B4 · Điều khiển ca và LT04 · 15/10/2026

[Report B4](../evidence/t3-b4/2026-10-15-verification.md) phân biệt test thuần/MOCK gateway, HTTP loopback và JavaFX/Spring/HTTP/WS/PostgreSQL thật. LT04 kiểm mới khác ID, answers/revision/writer/deadline và hai dòng history MOCK đã lưu giữ nguyên; kiểm trước và sau job timeout thực, không gọi timeout bằng tay. Import GUI tự động đi cùng controller sau chọn path, chưa thao tác native FileChooser. Xác nhận nghe và fixture/audio/history là MOCK/SIMULATED đúng report. Không nâng LT/IT toàn hệ thống thành human/LAN PASS; review A, LAN, PR35 Reading+Listening và E1/E2 còn riêng. Tracker và các evidence cũ giữ nguyên.

## B-ACCEPT-01 phía A — API lịch sử lượt thi (12/10/2026)

SHA `33a2932` trên nhánh cục bộ `fix/backend-candidate-attempt-history-20261012` (base `main` `ee714fc` + 5 commit P0), Windows 11, JDK 21.0.8, PostgreSQL 18.6. Chi tiết: `evidence/t2-a-history/2026-10-12-verification.md`.

| Kiểm tra | Kết quả | Ghi chú |
|---|---|---|
| `mvn test`, `mvn package` với `TOEIC_PG_INTEGRATION=true` | PASS 592/592 | protocol 43, client 376, server 172, spike 1; bản Codex để lại (`e81961e`) là 590/590 |
| Endpoint lịch sử: chủ sở hữu, thí sinh khác, role, phiên thu hồi, phân trang, whitelist trường | PASS REAL | Spring runtime + HTTP + PostgreSQL thật, schema riêng tự xóa; 4 test |
| Route API không tồn tại | PASS | 404 `INVALID_INPUT`, không còn 500 |
| Client B `770c896` gọi server nhánh này | PASS REAL 13/13 | HTTP + PostgreSQL thật, không GUI; `SUBMITTED` và `TIMED_OUT` thật |
| GUI production của B, review C→A, LAN | NOT RUN | B-ACCEPT-01 chưa đóng cho tới khi B chạy lại GUI |

Không đổi trạng thái AT01–AT11 hay IT01–IT04; `TRACKER.json` giữ nguyên.

### API lịch sử xếp lại trên PR #23 đã đồng bộ — 13/10/2026

Nhánh cục bộ `feat/backend-candidate-attempt-history-20261013` tại `19f669e`: ba commit lịch sử đặt trên `sync/pr23-main-20261013`, không lặp commit P0. `server/src/main` và `protocol/` trùng từng byte với `7bdac52`. `mvn package` với `TOEIC_PG_INTEGRATION=true`: **PASS 602**, 0 skip (protocol 43, client 386, server 172, spike 1). Quyết định lịch sử mang số QD-23 vì main đã dùng QD-19. Chi tiết: `evidence/t2-a-history/2026-10-13-restack.md`. GUI, LAN, review C→A: NOT RUN.

## PR #28 — fix P1 cursor sau tích hợp main T3-A1/T3-A2, 13/10/2026

SHA code/test `00b1df18d89f3b01a5864dc4aa689c22186943fa`, main `2e784fa`. Maven test và package:638 PASS mỗi lượt,0failure/error/skip; protocol43/client386/server208/spike1. PostgreSQL runtime37 (Exam19/Audio8/Listening10), là subset của638. Thêm3regression thật: mixed-ID `A bad`/Unicode/NFC/NFD/emoji/ký tự URL/SQL-looking, limit1/2/3 không trùng/mất lượt; biên128 PostgreSQL ký tự và129 bị400; rỗng/NUL bị400, cursor khác bind literal, 401/403 giữ nguyên. Probe nguyên bản từ lượt BLOCK10/1 nay11/11 PASS, exit0; HTTP/Spring/PG REAL, audio attestation MOCK, không GUI.

Chẩn đoán riêng: sessionId128+username13 gây attemptId142, POST tạo ca500 và rollback; không đánh dấu chức năng này đã sửa/PASS. Client B còn regex ASCII/không encode; GUI B/LAN/human review NOT RUN. C1/C2/ExamConcurrency smokes ngoài37 runtime chưa chạy lại trong fix này; không kế thừa PASS từ SHA4362d89. Evidence local `target/pr28-cursor-20261013/REPORT.md` và các log/counts/XML trong cùng thư mục. Tracker/bảng nghiệm thu gốc giữ nguyên.


## Tích hợp Reading/Listening/history — 15/10/2026

Nhánh `integration/reading-listening-20261015`, base8f5ac40; source runtime9115776, GUI harness3f4d5d6 (precondition seed đã sửa ở working tree khi chạy lại). `mvn -B -ntp -o clean test`: **833 PASS,0skip**, SHA9115776; package trên3f4d5d6: **833 PASS,0skip** (43protocol/569client/220server/1spike). PostgreSQL18.6/Spring/HTTP/WS thật ở49 runtime tests, là subset, không cộng vào833. Targeted trước đó142 PASS; không kế thừa776/669 của hai nhánh nguồn.

Các kiểm tra mới gồm shared writer không takeover audio, callback cũ sau close, Listening server-interrupted giữ Reading local với revision tăng, raw UTF8 query status/owner/repeated/unknown query; scope HTTP raw không làm nới ASCII heartbeat v0. Lượt targeted đầu FAIL5 do assertions path cũ; cập nhật kiểm exact query round-trip/fixed origin và chạy lại PASS. GUI smoke đầu FAIL ở precondition seed; lượt sau PASS8 rồi visual QA bổ sung lock/proctor assertion. Lượt final d809a00 PASS8, exact HTTP SYNCED và nhãn quan sát UI đã render; preparation bị khóa khi playback, Reading còn lưu được sau Listening interruption. Raw giữ ngoài Maven target bị clean; derived/public evidence ở `evidence/integration/`.

Giữ nguyên bảng27case và tracker: AT11/MT/LT/IT chỉ có các subset kỹ thuật theo bằng chứng mới, chưa tự nâng cả case PASS. Human/physical cable-pull/client kill/LAN/installer/codec MP3-M4A/gate delta/E1/E2/load NOT RUN. FULL default.


### Kết quả cuối sau visual QA

Runtime387ed99: package833 PASS,0skip sau sửa playback→preparation lock. Harnessd809a00: GUI production **8 nhóm PASS**, real Application/Media/WAV/HTTP/WS/JAR/PostgreSQL/restart; TEST đề/audio, xác nhận nghe SIMULATED. Proctor chọn row và nhãn quan sát đã đồng bộ thật; exact API status=SYNCED. C1 full/epoch/reconnect/autoSTALE-TTL và C2 late/gap/UNKNOWN/permission hồi quy PASS với heartbeat200ms theo script C; readings/faults MOCK/SIMULATED giữ nhãn. Lần C1 driver thiếu heartbeatflag FAIL retained; GUI literal SYNCED-label assertion FAIL retained rồi sửa theo nhãn quan sát đang dùng, không đổi UI để né kiểm tra. Ảnh/log/metadata/hash tại evidence/integration. Không đổi trạng thái27case/tracker/E1/E2.


Chốt thêm d809a00/final JAR: ExamConcurrencyPostgresSmoke AT01/07/09/10 PASS; ExamFlowPostgresSmoke28PASS/0GAP (mất response/network wrapper SIMULATED; deviceA/B là2model cùngJVM). Evidence/log/source hashes ở evidence/integration; không đổi bảng27case.

### B overnight phase 01 — 15/10/2026

Targeted controller/PG exam/listening49 PASS, 0skip; baseline trước DB FAIL connection refused. Đây là technical subset, không nâng case AT/LT/IT toàn hệ thống. Boundary protocol/reorg guard thêm sau targeted chờ full gate. [Evidence](../evidence/overnight-20261015/phase-01/verification.md).

B overnight phase02: targeted22/Python3/dry/SMOKE/negative checksum PASS; một failure INCOMPLETE cũ retained. Chưa E1 final/C1-C2 mới/gate/LAN. [Evidence](../evidence/overnight-20261015/phase-02/verification.md).

B overnight phase03: clean/package839,flow41/0GAP,GUI8groups,C1/C2 stagingretest PASS thật. Không nâng AT11/LT/MT/IT toàncase. [Evidence/matrix](../evidence/overnight-20261015/phase-03/verification.md).

Overnight phase04: GUI production11groups PASS (controls visible, PG assertions); targeted35client+33PG PASS, full846 trước test bổ sung. AT11/LT/IT/MT toàncase vẫn PARTIAL/NOT RUN theo matrix. [Evidence](../evidence/overnight-20261015/phase-04/verification.md).

Overnight phase05: parser3/negative5 PASS, ASCII nativeMedia8 PASS subset; runtime21.0.8, API/WS MOCK. Unicodeparent SDKgate/installer BLOCKED; IT01 whole NOT RUN. [Evidence](../evidence/overnight-20261015/phase-05/verification.md).

## B overnight — gate cuối 15/10/2026

Java sourcec1601b5: clean/package848 PASS mỗi lượt,0skip, PG18.6 thật; Flow41/0GAP, GUI11, C1/C2 có GUI PASS. Analyzer Python4 PASS. E1 collection12run/360trials COMPLETE và checksum/repro/cleanup PASS; nghiệm thu toàncase PARTIAL/human C review pending. [Báo cáo tổng](../evidence/overnight-20261015/00-summary.md). E2/load/LAN/installer/manual hearing NOT RUN, FULL default.

Phiên hòa PR #35/#38 trên main B4/B5 đã có kiểm mới16/10: xem bảng dưới. LT04/IT01 và E1 lịch sử vẫn thuộc source ghi kèm, không kế thừa.


## Bản thống nhất16/10/2026 — checkpoint b755646

Clean test/package mỗi lượt872PASS/1skip/0failure/error (protocol44/client600/server227+1skip/spike1), PG18.6 thật, GUI B4 LT04 và production app-image bật. Flow41/0GAP gồm AT11 exact40/42; concurrency AT01/07/09/10; GUI toàn Application11nhóm/progress/shared writer/FULL; native Audio8 và EXE/PG PASS subset. C2+GUI PASS với readings MOCK/fault SIMULATED. C1 cuối không JFR PASS; các lượt FAIL cleanup vẫn giữ, nguyên nhân ban đầu chưa chứng minh; MSI/Unicode parent BLOCKED thiếu SDK/WiX, installed test skip. E1 short mới COMPLETE/3trial, không E1 final;84raw lịch sử/14CSV repro không đổi. Python4 PASS. [SHA/log/count/limitations](../evidence/unified-20261016/verification.md), [manual](ACCEPTANCE_B_MANUAL_MATRIX.md). Không thay trạng thái nghiệm thu27case/tracker hoặc kế thừa848/687/MSI nguồn.


## QA PR39 — 16/10/2026, source9bfc75e

Clean test875PASS/1SKIP và package875PASS/1SKIP; installed FullSuite876PASS/0SKIP,0failure/error. Skip trước install là installedProductionClientUsesBundledRuntimeAndRealAudioLifecycle do thiếu flag; đã chạy trong FullSuite. Flow/AT11 exact40/42 41checks/0GAP, PG concurrency, GUI11/LT04/progress/shared writer, C1/C2 và native MSI037 + History/reorg extra PASS local subset. Unit/MOCK, faults/hearing/dialogs SIMULATED phân biệt rõ; ma trận AT11/LT01–04/IT01–04/MT01/07/08 vẫn PARTIAL toàncase, physicalLAN/manual/human review NOTRUN. Cache race BEFORE FAIL có fix+regression; counts loại XML cũ khi reactor dừng. [Counts/source/failures/matrix/artifacts](../evidence/b-pr39-acceptance-20261016/FINAL_REPORT.md). Không đổi trạng thái27case/tracker hoặc kế thừa PASS nguồn.

## F01 — kết quả Listening gián đoạn, 16/10/2026

**F01 FIXED local**, code `d19c63902725662aeb6f43c6f04ca80ea6caff42`, base PR40 `3fc5bb4`. Before6ERROR do Result thiếu cờ → mapping6PASS; targeted104PASS gồm submit/status/history và GUI4. Test PG mới4 tổ hợp SUBMITTED/TIMED_OUT × true/false qua HTTP thật, điểm/revision/thời điểm giữ nguyên, history không đổi row/writerEpoch. GUI4 dùng **MOCK result** trong Stage thật, không gọi là tích hợp server hoặc nghe thật.

| Lượt mới | Kết quả thật |
|---|---|
| Maven clean test | 898 ca:896PASS,2SKIP,0failure/error |
| Maven package | 898 ca:896PASS,2SKIP,0failure/error |
| MSI0.3.8 installed FullSuite | 898PASS,0SKIP,0failure/error; hai opt-in app-image/installed đã chạy bằng artifact mới |
| ExamFlow/AT11, Application GUI | 41PASS/0GAP và11nhóm PASS; REAL HTTP/PG/JavaFX, loss/hearing seam SIMULATED |
| PG concurrency, B4/progress | AT01/07/09/10 và LT04/progress/shared writer PASS local trong suites/harness |
| C1/C2 monitoring + GUI | C1 đầu FAIL cleanup Edge, giữ log; đổi temp TEST Windows, không sửa source, C1 cuối PASS; C2 PASS. Process fixtures MOCK/faults SIMULATED theo harness |
| MSI0.3.8 lifecycle local | PASS quyền thường/Unicode/bundled JVM/no JAVA_HOME/deny-write/close/uninstall;5439class packaged/final giống byte |

PostgreSQL native18.1 TEST riêng,0schema còn lại và cluster đã dừng; không thay service dùng chung.84rawE1 nguyên checksum, không E1/E2 rerun. Counts từ XML mới đã archive riêng, không kế thừa876 PASS nguồn cũ. [Report và raw index/counts/ảnh](../evidence/f01-20261016/REPORT.md). Nguyên nhân chính xác C1 FAIL đầu chưa chứng minh; C review pending. Không nâng trạng thái toàn bộ27case hoặc tracker: human A/C/LAN/máy thứ2/nghe thật vẫn NOT RUN.


## Kiểm chứng RC tích hợp — 17/10/2026

Source c224ba8, test/build tree trong evidence: [báo cáo và XML counts](../evidence/integration-final-20261017/REPORT.md). Clean test/package904PASS/2SKIP mỗi lượt, installed FullSuite906PASS/0SKIP. Flow41/0GAP,GUI11,PGconcurrency AT01/07/09/10,C1FULL/C2 PASS mới; Python12PASS. REAL PG/HTTP/WS/GUI/installed và MOCK/SIMULATED theo bảng evidence. PR43 history bounds2 test là subset của tổng. E1 chỉ rejoin/reanalysis raw lịch sử; physical LAN/hearing/soak/load/E2 NOT RUN. Không tự nâng AT/MT/LT/IT toàn case hoặc tracker thành hoàn tất; cần owner nghiệm thu.

## Ổn định hóa 17/10/2026 — source `3702b6d`

Lượt cuối `final-3702b6d-r3` trên cluster PostgreSQL 18 riêng, loopback; đếm từ XML. Không cộng test tập trung vào tổng (đã nằm trong tổng).

| Suite | Kết quả | Ghi chú |
|---|---|---|
| Maven clean test / package | mỗi lượt 924 PASS, 0 FAIL, 4 SKIP (82 suite) | skip: symlink thiếu quyền, 8.3 tắt trên E:, 2 test cần app-image/installed |
| MSI 0.3.10 installed FullSuite | 926 PASS, 0 FAIL, 2 SKIP | cài/gỡ TEST trên máy build, không phải máy thứ hai |
| ExamFlow AT11 / concurrency AT01·07·09·10 / GUI 11 nhóm / C1 / C2 | PASS | C1/C2 cleanup PASS từng thành phần |
| QD-29, lịch sử 0/1/5000/5001 cả 3 endpoint, cache audio, takeover replay | PASS | nằm trong tổng trên |
| GitHub Actions windows-2025 | SUCCESS run 39017363076 | Maven mặc định, PG/GUI opt-in skip |
| E1 analyzer + 84 raw | 12/12 PASS, 84/84 hash | kết quả 287/62/11 không đổi |

FAIL trung gian giữ lại: CI 39012744164 (assert thông báo cũ, sửa `7c0491e`); `final-4fbc538` (classpath smoke, lỗi pipeline agent); `final-3702b6d`, `-r2` (môi trường); C1 với hạn 8 s: 11/24 FAIL (10 khóa profile Edge, 1 Edge không lên dashboard), hạn 45 s: 5/5 PASS. Vẫn NOT RUN: LAN hai máy, nghe thật, máy sạch, tải 1/5/10, E2. Chi tiết: [báo cáo](../evidence/stabilize-20261017/REPORT.md).

## 18/10/2026 — main `f864034` + PR #47 ghép cục bộ (`eca6ad3`)

| Suite | Kết quả | Ghi chú |
|---|---|---|
| `mvn clean test`, PG 18.1 riêng + proctor GUI | 931 test: 927 PASS, 0 FAIL, 4 SKIP | đếm từ XML; skip đã biết (symlink, 8.3, app-image, installed) |
| `scripts/smoke-t3c4.ps1` | 42/42 PASS, run `T3C4-37bd5294…` | một máy, loopback; fault SIMULATED; LAN/B review NOT RUN |

Không phải PASS của main một mình: bản ghép chứa sửa `2b6c9ce` chưa merge. [kiểm tra 18/10](../evidence/t3-c5/2026-10-18-update.md)


## Corrective S3-P1-01 · 18/10/2026
Source audio56a27e9, [báo cáo mới](../evidence/audio-cache-p1-20261018/REPORT.md): exact-main stress4batch FAIL747/1280; source sửa4batch PASS1280/1280,20/20hash,0partial. Targeted AudioFiles/Sharing/Preparation/ApiClient/Playback:54PASS/2SKIP/0FAIL/ERROR. REAL Windows filesystem/multi-JVM/reader/cancellation; payload và API audio MOCK. LT01 phần kiểm file/READY có regression; không đổi LT01–LT04 hoặc IT01 thành nghiệm thu đầy đủ, nghe thủ công/LAN/máy2 NOT RUN. Diagnostic wrapper full-run không tính PASS. CI mới NOT RUN tại snapshot; sau lần push bị automatic approval review từ chối, ngày19/10 người dùng đã cho phép push hai nhánh và mở PR/CI.


## Tích hợp19/10/2026
PR47 vàPR57 đã vào main8a43a56. Sourcece38a5f clean test/package mỗi lượt946total=942PASS/4SKIP,0FAIL/ERROR; PG68PASS/2SKIPsubset. MODE8case lặp5lượtPASS; GUI11/11, ExamFlow41/41PASSlocal. C4 supplementaryFAIL ở fixture retrybudget (raw giữ); harness1072f77 sửa configurationnormalpeer, chưa nhận lượt rerunPASS trước khi chạy. LAN0/41 và hearing/MSI-máy2 NOT RUN; không nâng MT/LT/IT toàn hệ thống thànhPASS.


19/10/2026 — CI exact-main0363995 run39150213101 FAIL: AudioPreparationTest JUnit TempDir xóa next-speaker WAV đồng thời với cleanup async của production close(). Sửa TEST chờ Files.notExists(sample) bằng await5s hiện có, cho cả AfterEach và view kế tiếp; giữ mọi assertion READY/epoch, không đổi production close/FX. Focused10case×5lượt PASS trên Windows (fixture API/media MOCK), không gọi50case duy nhất. Clean main0363995 test/package mỗi lượt942PASS/4SKIP nhưng CI này vẫn FAIL; không gán CI củaPR58 cho main. Full/PG/CI sau corrective sẽ ghi tiếp; LAN0/41, hearing/máy2 NOT RUN.


## 19/10/2026 · Codex · main ổn định và artifact cuối

19/10/2026 — CODE_READY_STAGE3_ACCEPTANCE_PENDING: main buildsourcecfbba58 có47/57/58/59, CI39151021995SUCCESS; clean test/package mỗi lượt942PASS/4SKIP, installed MSI0.3.11 FullSuite943PASS/3SKIP trên Windows/PG thật. HaiP1 FIXED/MERGED; C4 local42/42, kit source/portable29checks mỗi bộ, physicalLAN0/41, hearing/máy2 NOT RUN. [Kết quả và hash](../evidence/stage3-execution-20261019/REPORT.md), [bàn giao](STAGE3_FINAL_HANDOFF.md). FULL baseline/DELTA experimental; technical reviewer Codex được người dùng giao, không ký A/B/C; tracker/hours giữ nguyên.

Raw local được giữ private/ignored, XML chỉ công bố case+SHA không environment. PG TEST ba lượt đều0schema và dừng; MSI đã gỡ, kit không chạy network. Đã giữ failure CI0363995/C4 trước corrective, không đổi kết quả lịch sử. Release followup chỉ pin kit/source/template/runbook và hồ sơ, không Java/POM/protocol; buildsource giữ SHA trên, kiểm equivalence lại khi merge.

## 19/10/2026 · EX-02 contract candidate — kiểm thiết kế MOCK

[Fixture checker](plan-v2/fixtures/ex02/verify-design.ps1) chạy thật trên Windows PowerShell5.1: positive MOCK200/76group/55media (54audio),19 negative mutations +10 access-policy scenarios PASS. [Kết quả](plan-v2/fixtures/ex02/verification.mock.json), [report](../evidence/ex02-contract-20261019/REPORT.md). Các case RBAC/state là oracle thiết kế, không HTTP test. Missing reference/checksum kiểm file thật; media decoder/junction chưa kiểm. Chữ ký records/JSON field consistency/Markdown links/Git diff và CI ghi riêng trong report. Runtime mới API/START/PG/JavaFX/hearing/LAN/máy2 NOT RUN; ADR S01–S16 là acceptance tương lai. Không kế thừa984PASS/6SKIP của EX04 thành kết quả task này, không đổi trạng thái nghiệm thu Phase3.

## 19/10/2026 · PR67 Option A — thiết kế và acceptance visual

Thiết kế D01–D10/ADR TEAM_APPROVED_AS_REPORTED_BY_PROJECT_REPRESENTATIVE theo [nguồn đại diện](plan-v2/EX02_TEAM_APPROVAL_20261019.md); không GitHub Approving Review hoặc runtime PASS. [Checker](plan-v2/fixtures/ex02/verify-design.ps1) giữ29cases cũ (non-Part1 prestart image403 theo D10), thêm9positive+20negative visual; lượt đầu58design cases PASS, actual PNG size/hash/.NET decode và corrupt rejection. [Report kiểm cuối](../evidence/ex02-contract-20261019/REPORT.md), [results](plan-v2/fixtures/ex02/verification.mock.json). Kiểm signatures Java21, canonical manifest hash độc lập bằng Java, exact JSON fields/links/diff và CI ghi số thực trong report; không cố giữ21records.

ADR giữ S01–S16, cập nhật image expectations và thêm V01–V20. Target backend HTTP/RBAC/PG/WS, JavaFX Stage/layout/two-pulse/widget snapshot-before-play, heartbeat/race/restart, hearing/LAN/máy2 đều NOT IMPLEMENTED/NOT RUN ở lượt docs. Modeled PLAY trace không chứng minh MediaPlayer/pixels thật. P09 chỉ kiểm đoạn QD21 không đổi; không rerun deadline service. Phê duyệt thiết kế không đánh EX03/05/06/07/08 DONE hoặc nghiệm thu Phase3 physical.

## EX05 — checkpoint triển khai, 19/10/2026

19/10/2026 — EX05 đang triển khai trên stack EX03: resolver verified bytes, Part1 canonical manifest và image route fail-closed khi thiếu EX07 authority. [Checkpoint](EX05_AUTHENTICATED_IMAGES.md). Main/PR75 chưa merge; V13/V14 MERGE BLOCKED. Targeted PG/HTTP đang chạy; không EX05/FE DONE, không sửa tracker/hours.

Unit focused ghi25case RichImageTest PASS; lượt PG mới/chính xác source đang chạy, không kế thừa PASS từ EX03. GUI/LAN/hearing NOT RUN.

## EX05 — clean verify20/10/2026

Source05cc694 clean verify1127PASS/6SKIP/0FAIL/0ERROR, PG18.1/HTTP thật, đề MOCK tự soạn;0schema/clusterstop0. [Evidence](EX05_AUTHENTICATED_IMAGES.md).29caseEX05 mới PASS; V13 sau14 bịFlywaydefaultchặn thật. AuthorityEX07 còn thiếu, image route fail-closed; GUI_PHYSICAL_NOT_RUN/hearing/LAN NOT RUN; MERGE BLOCKED/PR75/V13coordination. Không tracker/hours/human signoff.
