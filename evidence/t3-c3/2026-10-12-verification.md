# T3-C3 · Replay acceptance campaign ·12/10/2026

## Source, Git và phạm vi

- Fetch/base `6a4103161ad5bebde5277bc6a415b6c21a0cce6b`, C2PR25 đã merge; checkout ban đầu sạch, không nhánh/PR C3. Branch `feat/t3-c3-monitoring-delta-fault-replay`. PR17/23 vẫn mở riêng, không sửa/merge.
- Code/tests/fixtures/CLI/guide `dc87fcc`; package569 trên source đó. Runtime HEAD `d337fea45ca2a2e621cb2a6ce5d36e29d2e606d4` chỉ thêm `.gitattributes` giữ exact fixture/evidence bytes. Diff giữa hai SHA chỉ thuộc attributes, code/JAR không thay;243source manifest hashes và client JAR khớp. Evidence/docs commit và PR/merge cuối xem PR/final, không đổi raw sourceSHA thành docs commit.
- Earlier clean CLI run `2bd621c6-7577-499e-b300-b69fc226a45e`; C1 regression `20c044de-22cf-412a-aa1e-851c97fae7b6`; T2-C3 ownedEdge regression `421db06a-2832-48c1-a0d6-2da54314c989`, tuần tự, runtime d337fea. Metadata ghi JDK21.0.10+7/Maven3.9.15/Windows11 build26200/command/seed20261012/JAR/source hashes; không credentials/private path/machine account.
- Không server/transport/encoder/reducer production/EventOrigin/network schema/dependency/migration/exam changes. Không `.env`/tracker/kế hoạch gốc/old evidence hoặc PR A/B changes. Full default, delta experimental chưa tự bật. Không DB cần reset/kill process người dùng; owned Edge/profile/worker được smoke tạo và dọn riêng.

- Verified final source/build/runtime `d813cb15b83ec640f3894486ff4c2defef268398`, run `b46f6a5e-e652-4ec4-8a8b-0201ba5b991a`. Initial -text attribute chưa tự sửa Git index cho manifest/expected JSON: workspace CRLF vs prior blob LF. d813 giữ exact2file bytes; source blobs/243manifest và JAR verified. Repackage56922:51:24 + CLI72/4negative/77repeat identical PASS.76case files byte-identical earlier d337, summaries/metadata sourceSHA riêng. Final tree docs/evidence dirty ghi đúng trong metadata, không gọi wholecheckout clean. Client JAR unchanged FB05E7D3; prior C1/T2-C3 regression source bytes/JAR khớp.

## Code được kiểm và fault model

MonitoringReplayDriver đưa raw qua FullSnapshotDelivery/MonitoringDelivery production, ReplayServer dùng parser/MonitoringStateReducer C2. ObservationOracle tính từ raw business observations/identity/event rule và lịch nhận; không gọi encoder/reducer để tạo expected. Clean checkpoint coverage kể cả không phát wire, fault outcome/canonical metadata và event ID/nội dung/map phải khớp sau từng action. ReplayCampaign/TraceCli thêm manifest validation,72cases,4negative, repeat và summary JSON. FaultSchedule thêm targeted intents và mapped seeded events theo eventId/channel riêng, states theo mode ordinal; schedule payload/ID/source/generation/expected seq/atRecord lưu riêng mỗi mode.

Ba72-record MOCK production-schema workloads/70scan attempts/71giây ảo: [manifest](../../client/src/test/resources/monitoring-traces/t3-c3/manifest.json), [handwritten table](../../client/src/test/resources/monitoring-traces/t3-c3/handwritten-expected.json). Low có scan không đổi/empty delta2s/full30s; high metadata/PIDreuse/startnull/SOURCE_FAILURE; reconnect scan6/event history xuyên epoch. TEST harness trace schema vẫn từ chối, raw không sửa.

ACK chỉ dùng baseline enumeration trước fault. DROP = server chưa nhận, không ACK-loss. HOLD chưa nhận đến RELEASE_LATE; duplicate nguyên ID/content. OPEN/CLOSE reliable. RESYNC/ACK_WITH_RESYNC recorded/not sent back to encoder; recovery từ periodic fresh full hoặc reconnect OPEN/full1. Không retry/ACK-loss/automatic feedback/auth/presence/TTL/DB/control fault/network trong model. C2 có real callback evidence riêng, không gọi là đã chạy lại trong C3. UNKNOWN NOT MODELED, STALE chỉ do CLOSE/rejection, không suy timeout từ drop. Full/delta state schedule khác dù cùng seed; event schedule keyed same eventId và pair events được đối chiếu.

## Kết quả mới đã chạy

| Kiểm tra | Kết quả | Mức bằng chứng |
|---|---|---|
| Baseline Maven test/main6a41031 |554PASS,22:16:59 UTC+7| Chạy mới |
| Verified Maven package/d813cb1 |**569PASS**,0failure/error/skip,22:51:24| protocol43/client376/server149/spike1;15new ReplayCampaignTest invocations |
| Focused replay tests |38PASS lúc22:33:44| Sau2lượt FAIL giữ logs; shared encoder/reducer thật, input MOCK |
| Three workloads ×12scenarios ×full/delta |**72PASS**,4intentional negatives detected| REAL CLI; source MOCK, data faults/ACK/control REPLAY/SIMULATED |
| Java per-case repeat + second full CLI campaign |77report/summary files byte-identical| Seed20261012/config/input không đổi; [summary](campaign-final/summary.json), [matrix generated](campaign-final/matrix.md), [CSV](campaign-final/matrix.csv) |
| Full/delta shared observations/business/delivered maps |36pairs event ID/content equal| Compare canonical metadata tại shared current-observation checkpoints; không ép sequence bằng nhau |
| Seeded reconnect comparison |0common synchronized checkpoints,69divergences| NO_COMMON_SYNC_CHECKPOINT; oracle từng mode vẫn khớp, không claim state equivalence/recovery ở chỗ này |
| Delta missing base/reorder/retry while UNSYNCED |PASS| Last-known set/seq giữ, RESYNC requests logged, duplicate không restore, full/new epoch phục hồi |
| Conflict ID + alias sequence/full/delta/event |PASS| Probes đổi content sau original, reject CONFLICT/no state/event modification |
| Drop event then full recovery |PASS,missing1 mỗi mode/workload| Missing replay-event-1 được liệt kê; state cuối SYNCED, không khôi phục history |
| Wrong full/delta/event mutation |FAIL đúng + CLIexit1| Oracle raw bắt mismatch; [wrong delta](campaign-final/negative-wrong-delta.json), other negatives.gz |
| Bad config/checksum/TEST/no-overwrite |Refused đúng CLIexit2| Bản corrupt riêng, trace gốc không sửa |
| REAL ProcessHandle6scans100ms verified C3 |COMPLETE/PASS both modes,46business/40delivered/6missing| Source REAL; delivery/control REPLAY; không live desktop/network |
| C1 CLI -RealScan regression |PASS| Clean/reconnect/negative/legacy faults + new delta faults/TEST refusal/REAL capture |
| T2-C3 -RealEdge regression |PASS/cleanupPASS| Owned Edge + existing ProcessHandle/collector/recorder REAL; seeded45business/40delivered/5missing, transport SIMULATED |
| Counterexample old C2 JAR → final C3 JAR |Old expected PREVIOUS_CONNECTION/actualLIVE FAIL; final đúng PASS| Chỉ lỗi mô hình replayer, không production server bug |
| Artifact/JAR/source/repeat/raw restore audit |**222artifact/167gzip/243source/77repeat PASS**| [Checksums](checksums.json), [artifact audit](artifact-audit.json), [final scope/privacy/link audit](final-audit.json) |

PASS nghĩa outcome/state/event đúng fault model, **không có nghĩa mọi business event đã tới**, mọi run synchronized hoặc delta nhanh hơn. Seeded late data có thể tới sau reliable CLOSE nên lastDataState STALE; targeted cases final SYNCED. Matrices không ép full/delta set bằng nhau khi delta mất base. Report gồm business expected, delivered expected/actual/missing IDs, mutation config, recovery/resync/source schedule, comparison status; summary sinh từ tool, không điền số bằng tay.

Ví dụ [low drop-state delta](campaign-final/low-change-drop-state-delta.json): full1 Edge+Chrome; delta2 Chrome+Zalo bị DROP; delta3 Chrome đòi state2 → UNSYNCED giữ Edge+Chrome/sequence1, RESYNC current1; subsequent delta giữ UNSYNCED; full định kỳ tại scan31 thay toàn tập bằng Edge và phục hồi SYNCED. [Retry after resync](campaign-final/low-change-retry-resync-delta.json) giữ state3, future state4 gây RESYNC rồi replay accepted full1/delta2 vẫn DUPLICATE/UNSYNCED. ACK/resync feedback không được thực hiện trong replay model này.

## Counterexample và failure giữ lại

- [Counterexample helper](counterexample/LateEventCounterexample.java) compile against old C2 client JAR SHA `de170932f2444f4610ee37d34a2731f74416dfaff0d922d7739698d093e21361`: event origin connection1 tới sau OPEN/full của connection2. Replayer cũ dùng origin connection làm receiving connection nên LIVE. Handwritten contract expected PREVIOUS_CONNECTION. Final client JAR `fb05e7d30b9be60c2eb06db21eaa7c37dc3efbbb98a7b4f2939e1e8858d19f3b` đúng. Test lateEventUsesCurrentDeliveryConnectionNotOriginConnection giữ regression.
- Initial helper/first test ghi “LATE” nhầm tên nhãn, có log riêng; đọc EventOrigin và PROTOCOL xác minh tên thật PREVIOUS_CONNECTION. Sửa reference/test đúng contract, giữ corrected-before FAIL và final PASS; không âm thầm sửa artifact cũ.
- First focused38 có2FAIL: oracle/test nhãn LATE và seeded compare; sau sửa nhãn còn1FAIL vì campaign ép có common synced checkpoint trong seeded lịch làm mất base. Diagnostic [summary trước](diagnostic-before/summary.json) cho thấy per-mode oracle PASS/event maps bằng nhau nhưng0common. Sửa comparison policy theo yêu cầu: công khai NO_COMMON_SYNC_CHECKPOINT thay vì ép bằng nhau; targeted recovery vẫn bắt có checkpoint chung/finalSYNCED. Không sửa oracle từng mode hoặc raw để PASS.
- Third focused38/first package569/72CLI PASS; exact manifest Git bytes fix d813 sau đó được verified package569/CLI72 chạy lại, Java/runtime source không đổi. Copy evidence lầnđầu NameError do khối Python indentation trước khi tạo transcript; sửa copier rồi audit, không runtime/raw change. Không bịa nguyên nhân lỗi test chạy song song; smokes chạy tuần tự.
- Final audit BOM-aware decode giữ original regression artifact t2c3-regression/cli.txt UTF16 BOM nguyên byte; decoded text không NUL/U+FFFD, đây không phải decode hỏng.
- [Transcripts](transcripts/) gồm baseline/package/focusedFAILs/diagnostic/old-final counterexample/CLI và regressions. BOM-aware decode UTF16→UTF8/LF, trim whitespace/private prefix aliases; originalSHA và derivedSHA riêng, không NUL/U+FFFD. Raw trace/report/metadata exact bytes, gzip lossless mtime0 với originalSHA; duplicate repeat không copy lần2 vì đã verifybyte-identical. Old evidence không thay.

## Tự chạy, handoff và giới hạn

`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t3c3.ps1 -JavaHome <JDK21> -RealScan` sau `mvn package`. Output directory mới và checksum/config/source trong metadata; [guide](../../docs/MONITORING_DELTA_FAULT_REPLAY.md) có command replay/campaign, model, MTmapping và cách đọc report. Không dùng duration replay/message count/byte minh họa làm E1/E2.

MT02 duplicate/conflict replay có; fault-network ACK-loss/retry NOT MODELED. MT03 gap/reorder/last-known/full recovery REPLAY PASS. MT04 epoch cũ sau full mới REPLAY PASS. MT05 chỉ reconnect giả lập, không restart collector/server/auth revoke thật trong C3. MT06 three workloads/full high/late version/conflicts/canonical state-event comparison REPLAY PASS, zero-common case công khai. **MT02–MT06 PARTIAL cấp toàn gate**, không fake human approval hoặc live nghiệm thu.

B cần review model/coverage/independent oracle/raw metadata-event mapping/bounds/exit/repeat/resync limits. Human B/A và ChatGPT planning-review NOT RUN theo opt-out. Python analyzer/live Spring-PG-WS smokes không chạy lại vì code tương ứng không đổi; previous C2 evidence không phải PASS mới. GUI/LAN/two-desktop T3-C4/control/ACK-loss/restart/revoke/live gate/E1/E2 NOT RUN. Full vẫn mặc định, không tự quyết QD10.

User ủy quyền commit/push/PR/merge commit nếu không blocker C3/GitHub requirements đáp ứng, giữ local/remote C3 và sync main an toàn. Git cuối/checks/PR/merge SHA trong PR/final. **Dừng sau T3-C3**, chưa C4/live campaign/E1/E2; không merge/sửa PR A/B.

- Staged whitespace audit phát hiện CSV derived export có CRCRLF do CSV writer + Windows text translation. Regenerate đúng2matrix CSV từ untouched summary bằng explicit UTF8/LF bytes, kiểm72rows và ledgerhash mới/previousDerivedSHA; không sửa raw/report/runtime hoặc chạy lại tests vì chỉ format derived view. Git staged diff check chạy lại trước commit.
