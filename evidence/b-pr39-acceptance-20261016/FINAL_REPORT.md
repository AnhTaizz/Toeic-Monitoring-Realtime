# QA PR39 — 16/10/2026

## 1. Kết luận điều hành

**CONDITIONALLY_READY**. Ba lỗi đã tái hiện và sửa (2P1,1P2; P0 chưa phát hiện trong phạm vi kiểm). Source cuối `9bfc75ec7c2d91af6f714602727389547c3b3288`: clean test/package mỗi lượt **875PASS/1SKIP**, installer FullSuite **876PASS/0SKIP**, đều0failure/error. MSI037 build mới/cài/runtime/gỡ local PASS; kiểm bổ sung History/B4 trong EXE cũng PASS. Không kế thừa848/687/872 hoặc MSI nguồn. Phase1 **COMPLETE kỹ thuật / FIXED_AND_RETESTED**; Phase2 **PARTIAL nghiệm thu toàn phần, INSTALLED_LOCAL_PASS**; Phase3 **PARTIAL chuẩn bị / PHYSICAL_LAN_NOT_RUN**, đúng xác nhận chỉ có một máy.

Human A/B/C review, nghe thật và physical LAN vẫn chờ. Database QA đầu phiên chưa drop vì shared container bị dừng trong khoảng nghỉ; agent giữ nguyên shared service, dọn xong container TEST mới riêng. Không tự merge hoặc nghiệm thu toàn hệ thống.

## 2. Repository / SHA / môi trường

PR39 vẫn OPEN/Draft/mergeable, HEAD `093429ab1abb19f9c6d37be9b86ae8e00f11a284`; main và merge-base `c0fd9036ba340b0b0a686fb2ce637050b9a3b7b4`, fetch/metadata đã kiểm lại sau resume. Branch mới `qa/b-pr39-acceptance-20261016` từ đúng HEAD39. Không reset/clean/rebase/force-push/sửa nhánh nguồn. PR35/38 còn Draft; không close/Ready/merge.

Windows11 x64 build26200, Oracle21.0.8, JavaFX21.0.12, Maven3.9.11, PG18.6, Docker29.4.3; WiX3.14.1 và SDK mt10.0.26100.6584 portable đã kiểm. [Readiness](ENVIRONMENT_READINESS.md).22worktree metadata,20 audit được bằng native/Linux; root5untracked giữ nguyên,2detached record lỗi giữ nguyên (missing/timeout), không prune/repair. Những SHA test trong report là source chạy thực; commit bàn giao tài liệu sau đó phải giữ runtime/test/build tree bằng source đóng băng.

## 3. Findings

[Chi tiết Severity/File:Line/Scenario/Root cause/Impact/Repro/Fix](review/ISSUES.md): QA39-01 P1 smoke title cũ; QA39-02 P2 URL BOM; QA39-03 P1 race cache khi close/reopen/multiple reader. Không sửa cosmetic hoặc backend A/monitoring C. [Toàn call chain15class và backend/contract review](review/PR39_CODE_REVIEW.md).

## 4. Fix và regression mới

Commit0a00d0f sửa BOM; dcbdf85 sửa title smoke;9bfc75e sửa cache idempotent vẫn kiểm realpath/directory và guard sau manifest. BOM BEFORE1FAIL/5 → AFTER5PASS. Race: full clean test dcb bị1FAIL client; diagnostic65case có10FAIL+1ERROR →65PASS cùng stress sau sửa; targeted CI26PASS.40lặp stress chỉ diagnostic, không cộng vào tổng CI. Source uncommitted targeted patches được đóng băng ở commit fix tương ứng; final full runs đều ở9bfc75e.

| Lượt mới | Source | Thực tế |
|---|---|---|
| Baseline clean test |093429a|871PASS/2SKIP, PG thật; hai package opt-in chưa bật |
| Failed clean test |dcbdf85|protocol44/client601 thực thi, client1FAIL; server/spike NOT RUN; corrected XML archive loại dữ liệu cũ |
| final-clean-test-02 |9bfc75e|875PASS/1SKIP/0failure/error,876cases |
| final-package-02 |9bfc75e|875PASS/1SKIP/0failure/error,876cases |
| final-msi-install-02 FullSuite |9bfc75e|876PASS/0SKIP/0failure/error |
| Flow/concurrency/GUI/C1/C2 cuối |9bfc75e|Flow41PASS/0GAP, concurrency AT01/07/09/10 PASS; GUI11groups; C1/C2 PASS subset |
| Extended native installed |9bfc75e + private harness hashes|History/submit/reorg/old immutable/new fresh PASS; một standalone scenario riêng, không JUnit count |

XML được archive trước lượt clean tiếp theo; counts chỉ lấy XML mtime trong START/END của đúng immutable log. [Clean counts](final-clean-test-02-counts.json), [package counts](final-package-02-counts.json), [installed counts](final-msi-install-02-counts.json). Skip duy nhất trước install là `ListeningRuntimePostgresTest.installedProductionClientUsesBundledRuntimeAndRealAudioLifecycle` vì thiếu TOEIC_INSTALLED_CLIENT; trong installed FullSuite đã chạy,0skip. App-image opt-in bật bằng artifact037 thật. [Raw log hash/source/time/exit index](RAW_LOG_INDEX.json); raw FAIL giữ local. Bản đếm đầu failed reactor từng trộn XML cũ, corrected-v2 mới được dùng; không che lỗi bằng rerun đơn thuần.

## 5. Visual QA

Application thật, ảnh mới source9bfc75e runfb4a7d409c8d; agent đã mở xem Reading/History/Listening/confirmed progress/B4 start/reorg. Login và stale503 renderer thật có rows MOCK, không gọi là PG integration. Thêm ảnh History/B4 trong chính EXE đã cài. [Visual QA/ảnh/provenance](review/VISUAL_QA.md). Form B4 có scroll; progress nhiều cột có scrollbar; quick-fill có ellipsis. Chưa sweep DPI/đề dài/thiết bị; không sửa cosmetic. Không publish ảnh process/PID06.

## 6. MSI build/cài/runtime

**MSI_BUILT + INSTALLED_LOCAL_PASS**,0.3.7, source sạch9bfc75e, không AllowDirty. [Build](release/WINDOWS_BUILD_REPORT.md), [installation](release/INSTALLATION_TEST_REPORT.md), [manifest](release/ARTIFACT_MANIFEST.json). Ordinary login không agent, JAVA_HOME removed/PATH Windows-only, bundled jvm.dll, Unicode install, deny-write, file hashes nguyên vẹn, close/uninstall PASS. TEST agent ngoài package; native READY/hearing/dialog/socket fault có nhãn MOCK/SIMULATED. 5439class entries của JAR trong image/MSI và JAR cuối bằng byte; [class equivalence](release/CLASS_EQUIVALENCE.json).

## 7. LAN preflight và actual

Người dùng xác nhận chỉ có máy hiện tại, yêu cầu runbook. S0–S7 physical LAN **NOT RUN**; không dùng nhiều JVM/loopback thay hai host. [Topology](lan/TOPOLOGY.md), [preflight](lan/PREFLIGHT.md), [checklist](lan/EXECUTION_CHECKLIST.md), [actual results](lan/TEST_RESULTS.md), [lệnh từng máy/chuyển MSI/hash/test data](lan/MANUAL_STEPS_FOR_OWNER.md). Generator JSON/WAV TEST120s đã chạy thật vào output riêng; local bundle `jpackage-out/pr39-qa-20261016/LAN_HANDOVER` chứa MSI/hash/build-info/server JAR/manifest. Chưa chuyển hoặc chạy trên máy B; HTTP401 liên máy chưa kiểm, không tự đổi firewall.

## 8. Ma trận nghiệm thu

| Mã | Technical evidence mới | Nhãn / toàn case |
|---|---|---|
| AT11 | Flow41/0GAP, exact server40/local42, lost ACK, new entry/old writer409; GUI shared writer/history; PG concurrency | REAL HTTP/PG/2model cùnghost; loss SIMULATED; toàncase PARTIAL, chưa2máy |
| LT01 | Audio checksum/download/READY/sample + native installed WAV | REAL native/HTTP/PG; nghe SIMULATED; human NOT RUN, PARTIAL |
| LT02 | B4 READY/start confirmation/dedupe và native playback | REAL source/runtime; TEST audio/dialog automation; PARTIAL human |
| LT03 | Owned Spring restart thật và socket close seam, Reading sau reconnect/Listening khóa | REAL local restart, SIMULATED socket fault; physical loss NOT RUN, PARTIAL |
| LT04 | GUI proctor trước/sau auto timeout, native installed reorg+old immutable/new fresh, foreign403 API | REAL local GUI/PG; confirmation SIMULATED; manual/LAN PARTIAL |
| IT01 | MSI037 normal user/Unicode/bundled JVM/write-denied/2role/History/B4/Reading/Listening/FULL/close/uninstall | INSTALLED_LOCAL_PASS; secondWindows/human NOT RUN, PARTIAL |
| IT02 | Auth/scope/malformed API/WS và2candidate fixture realbackend | REAL local; second candidate READY fixture MOCK khi native smoke; physical2host NOT RUN, PARTIAL |
| IT03 | Async I/O, late callback/cancellation unit + native worker/media/process close; cache race regression | MOCK controlled concurrency + REAL lifecycle; manual responsiveness/DPI NOT RUN, PARTIAL |
| IT04 | Real owned server restart/deferred PG COMMIT rollback; no false ACK, immutable finals | REAL local; DB/shared-service crash NOT RUN, PARTIAL |
| MT01/07/08 | C1 FULL/C2 event/gap/UNKNOWN/epoch/role + Application FULL | REAL Spring/PG/WS; process readings MOCK và faults SIMULATED theo harness; human C/LAN PARTIAL |

Không thay status27case/tracker hoặc ký duyệt toàn case từ subset.

## 9. Automated / manual / simulated

REAL: native JavaFX/Media/productionEXE, Spring/HTTP/WS/PG transaction, source/hash/JVM/ACL/install/uninstall, owned server restart. TEST: đề/PCM/WAV/seed accounts/schema/DB. MOCK: unit gateway/pages/process sets và second-candidate READY trong installed fixture; backend thật vẫn kiểm. SIMULATED: lost request/ACK/socket seam, auto dialogs/hearing confirmation. Manual/human hearing/physical disconnect/2host/owner review: NOT RUN. Các nhãn này không đổi vì876test PASS.

E1/E2 không chạy lại. [84raw lịch sử](HISTORICAL_E1_INTEGRITY.json) được hash lại và giữ nguyên; collector/harness/policy E1 không đổi. RunId C1/C2 mới riêng, không dùng làm số đo E1. Không sửa số liệu lịch sử hoặc inherit gate delta.

## 10. Blockers và owner

[Danh sách cụ thể](release/BLOCKERS.md): B nghiệm thu nghe/DPI/máy hai/LAN bằng runbook; A review B fixes và backend/QD29/progress/transaction; C review FULL/epoch/gap/E1-method; human approvals chưa có. QD29 vẫn PROPOSED, không approve thay owner. Shared container sau resume exited nên QA database đầu phiên được giữ; owner cleanup đúng tên private khi service được phép hoạt động. Container TEST bổ sung đã remove;0owned connection/schema/driver/client trước cleanup; [counters](release/CLEANUP_SUMMARY.json). Không restart/shared volume reset.

## 11. Artifacts và SHA256

- MSI: `jpackage-out/pr39-qa-20261016/release-0.3.7/ToeicMonitor-0.3.7.msi` — `0068d05946eb26cfd632b6be1b9b26ca61f0729c58a44a63646d92c554f2b698`.
- Client JAR trong MSI/image: `e6efeaeb42990a73794ad42ba55b272bebc749fceae2967b51f283928091dfed`.
- Server JAR cuối: `7d53bdccc61275e005d6733c55cd845424eb097a9a86a5db5109251818586046`.
- Metadata/toolchain/harness hashes: [manifest](release/ARTIFACT_MANIFEST.json), [harness provenance](release/HARNESS_PROVENANCE.json), raw log index và00-FILE-SHA256.json.

Binary/raw giữ ignored local; public chỉ curated fixture screenshots/derived evidence và Java QA harness không credential. Bản035/036 và mọi FAIL giữ làm lịch sử, chỉ037 là ứng viên cuối.

## 12. Khuyến nghị merge / Git handover

**CONDITIONALLY_READY** cho review. Source/tests/MSI cuối9bfc75e có bằng chứng mới, chưa đủ human A/C/LAN để tự merge main. Draft PR QA nhắm nhánh PR39 khi bàn giao, xem PUBLICATION.md để lấy URL/HEAD mới nhất; PR39/main/35/38 giữ nguyên. Commit hồ sơ sau source đóng băng không được gọi là một lượt test mới; đối chiếu runtime/test/build diff và byte hashes trước push. Không mark Ready/approve/close/merge thay owner.
