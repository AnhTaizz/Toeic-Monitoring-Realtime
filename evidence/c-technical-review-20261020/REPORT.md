# Review kỹ thuật C — 20/10/2026

**Codex technical review theo ủy quyền user**, không GitHub APPROVE/chữ ký A/B/C hoặc quyết định nhóm. [Receipt nguồn/run/hash](receipt.json). Báo cáo chốt trước publication/merge PR74; CI đúng publication HEAD và merge SHA cuối ghi tại [PR74](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/74), không lấy CI cũ thay kết quả mới.

## PR, dependency và merge

| PR | HEAD review / base lúc merge | Phạm vi | Merge SHA đã xác minh |
|---|---|---|---|
| [70 BASE03](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/70) | ff07446 / main3bdefe6 | verification/oracle/kit | e0ef252d50e6a6f3d5d991b2b0894f62cd0e8a9b |
| [71 MON01](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/71) | 92f9faf / maine0ef252 | 70→71 resolved; lifecycle/metadata/harness | 2595106ce7a95d1bd84ef733ff8f48f7c06f0601 |
| [72 proposal](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/72) | e1326e9 / main2595106 | docs/fixture vẫn PROPOSED | a636e2a2b7d3e6549f179b320676dd9f778aa9bb |
| [73 USB probe](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/73) | 5c6886b / maina636e2a | standalone SPIKE | 46869ebefb5258d2934e7805a95b67633d4c8a63 |
| [74 experiment](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/74) | runtime/test/runner d6bf60a / main46869eb | 72→74 resolved; MOCK lane OFF | publication/merge cuối xem PR74 |

70–73 merge commit, tree main ngay sau merge khớp exact HEAD đã kiểm. Conflict chỉ docs, giữ cả cập nhật task/main; không reset/rebase/force-push/squash/xóa branch. Root main local vẫn f98a37f, không đồng bộ/ghi đè. Đã liệt kê open PR: EX75–79 không sửa; hồ sơ release/sign-off legacy53–56 không mở lại audit T1–T4 chỉ vì NOT_RUN, không tự ký/đóng.

## Findings có bằng chứng

- **C71-H01 / P2 harness:** scripts/smoke-t2c2.ps1 chỉ copy LateEventsAndGapsSmoke vào loader, thiếu SmokeCleanup; fresh run9a925d7 đi qua assertions nhưng FAIL NoClassDefFoundError ở cleanup, còn1schema trong cluster TEST đã dừng. Giữ raw. **92f9faf** copy helper vào loader; sau sửa65dashboardPASS và REAL GUI/HTTP/WS/PG smoke PASS,0schema/stop0. Lượt đầu loader riêng cũng thiếu helper; không nhận kết quả đó là smoke PASS.
- **C74-H02 / P2 fixture isolation:** scripts/verify-mon07-postgres.ps1 đặt JAVA_TOOL_OPTIONS cho UTC; child JVM probe ghi launcher banner stderr và bị strict source từ chối. Full a5339dc có1070PASS/1FAIL/6SKIP, case realChildCompletesAndAllReadersTerminate. Cùng child có biến→ERROR/CHILD_DIAGNOSTIC, bỏ biến→READY/NONE, cả2child0/readers true. **d6bf60a** lưu/cô lập/restore JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS/_JAVA_OPTIONS, truyền UTC trực tiếp cho Maven. Không sửa probe/parser/expected/timeout để che lỗi.
- **7 finding MON07 lượt trước** (lease, heartbeat/run, queued IO, stale callback/correlation, expiry sau assignment lock) đã sửa tại79ac620/2d73605: [biên bản](../v2-c-mon07-signal-pipeline/REVIEW-20261020.md). Lượt này đọc lại source và chạy lại regression; không dựa báo cáo hoặc CI xanh để suy approval.

Sau corrective không còn blocker đã xác minh trong phạm vi5PR. Chưa chứng minh mọi interleaving/OS/runtime; giới hạn dưới đây giữ nguyên.

## Test tự chạy mới

| Source / lượt | Kết quả |
|---|---|
| ff07446 BASE03 |307PASS/0SKIP; campaign72cases/4negative-controls là subset unit. REAL DeltaStateSmoke PASS: FULL STALE/CONFLICT, retired epoch, resync, scan-failure→STALE/TTL, restart/history/revoke. ProcessMOCK, faultsSIMULATED |
| 9a925d7 MON01 trước fix |204unitPASS/0SKIP nhưng smokeFAIL/cleanup1schema; không cộng overlap |
| 92f9faf MON01 sau fix |65PASS/0SKIP; REAL JavaFX component+HTTP/WS/PG PASS. Metadata/time tách biệt; worker tự dừng ở3terminal states qua TEST DB transitionSIMULATED; không full candidate application acceptance |
| e1326e9 proposal |7exchange/43relative targets PASS; fixture/schema không đổi từc9a5829 |
| aa260ef USB, final5c6886b source equivalent |35PASS/0SKIP/JAR; WindowsSTANDARD MSFT READY0, Win32UNKNOWN/nullcount;child0/readers true, không hotplug |
| cd00630 MON07 focused |228PASS/0SKIP,17REAL PG/HTTP/WS cases; inputMOCK, faultsSIMULATED;cleanup0schema/stop0 |
| a5339dc full trước fixture fix |1077total=1070PASS/1FAIL/6SKIP;17PG PASS; không gọi BUILD SUCCESS |
| d6bf60a full cuối |1077total=1071PASS/6SKIP/0FAIL/ERROR, verify/JAR SUCCESS;17REAL PG cases,0schema/stop0 |
| d6bf60a smoke process với RealtimeClient tích hợp |REAL HTTP/WS/PG LateEventsAndGapsSmoke PASS/0schema/stop0; package -DskipTests, không tính thêm unit tests hoặc GUI campaign |

Không cộng các lượt overlapping thành test duy nhất. Full6skip (symlink/sidecar, FX result-dialog, installed/app-image/proctor GUI opt-ins) có nguyên tên trong receipt. Cluster SCRAM/loopback/database TEST riêng; không .env/devDB/Flyway clean/root clean. Raw/log/XML/JAR chỉ local ignored; hash trong receipt không là GitHub download link. Không sao chép bundle/JAR lớn.

### Bằng chứng 5.001 signal

Method fiveThousandOneCommittedSignalsPageWithoutLossAndKeepThroughDuringConcurrentWrites: **5.001 MOCK events đều qua REAL WS/auth/transaction/COMMIT/ACK**, counter1..5001. Đọc đủ51trang mỗi trang≤100 và≤512KiB, ID unique/sequence liên tục/đúng thứ tự. Fixed-through5001 không kéo thêm24insert WS đồng thời; catch-up trả đúng24record tới5025. Cursor sai400; revoke token giữa trang401/mất assignment403, không lộ items/counter. Đây là quy mô test, không thêm cap5000 cho API. Small-fixture lock/rollback/COMMIT-fault regressions vẫn giữ; không bulk SQL bypass store.

## Phần A/B liên quan đã được Codex kiểm kỹ thuật

Protocol strict fields/duplicate/integer/Unicode/depth/UTF8-byte bounds ở client/server; immutable retry và canonical dedup/conflict. Queue/count/bytes/flight/write/retry/drop hữu hạn; ACK scope/type/run/generation, lease identity/heartbeat mapping, guard sát socket IO và cleanup correlation sở hữu. Stop/revoke/FAILED có regression; write completion không phải commit ACK.

MON07 candidate owner ACTIVE/assigned proctor ACTIVE, recheck auth sau counter/session/assignment waits, clock DB sau lock, counter/event atomic/rollback, proxyCOMMIT trước ACK và không DB lock chờ socket. History auth trước counter/cursor, fixed-through/no-store/body cap/safe errors. Process FULL default/DELTA opt-in, oracle độc lập/checkpoint/coverage, history tách state; heartbeatONLINE không chứng minh collector còn scan. USB projection/UNKNOWN/no-false-REMOVED/polling-loss/index-reuse/timeout/output/process/readers/close và TEST10s so CLI5s đã đối chiếu.

Các phần trên không còn là chưa review kỹ thuật; **human owner review chưa thực hiện**. CI73 gốc39258040529 thiếu diagnostic nên root cause vẫn UNDETERMINED, không nhầm với lỗi launcher banner đã chứng minh lần này.

## Migration, giới hạn và bước tiếp

Flyway mặc định **vẫn áp V13 khi experiment OFF**; flag chỉ tắt runtime service/routes/dispatch, không undo/drop schema. REAL OFF test: migration13SUCCESS/bảng rỗng/khôngbeans-routes. Không sửa applied migration hoặc áp DB dev. Main trước74 làV12; open EX03 dùngV14/EX07 dùngV15 tại audit, không collisionV13.

Giữ experiment OFF/FULL default; BASE03 NOT_DECIDED; MON01 TECHNICALLY_VERIFIED_PENDING_PHYSICAL_REVIEW/BASE02pending; LAN0/41; USBphysical0/12NOT_RUN. D07/ADR owner decisions, offline60s/retention30ngày vẫn PROPOSED/PENDING. Không observer thật/USB integration/push/dashboard/signal mới/retention job/E1/E2/trackerDONE.

C tiếp theo: chuẩn bị receipt/kit đúng main đã tích hợp và phối hợp người thao tác chạy checklist USB/LAN/GUI còn thiếu; ghi expected/actual và hạn chế thật, không lấy CI/READY0/MOCK thay nghiệm thu.
