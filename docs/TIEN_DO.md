# Tiến độ hiện tại

19/10/2026 — CODE_READY_STAGE3_ACCEPTANCE_PENDING: main buildsourcecfbba58 có47/57/58/59, CI39151021995SUCCESS; clean test/package mỗi lượt942PASS/4SKIP, installed MSI0.3.11 FullSuite943PASS/3SKIP trên Windows/PG thật. HaiP1 FIXED/MERGED; C4 local42/42, kit source/portable29checks mỗi bộ, physicalLAN0/41, hearing/máy2 NOT RUN. [Kết quả và hash](../evidence/stage3-execution-20261019/REPORT.md), [bàn giao](STAGE3_FINAL_HANDOFF.md). FULL baseline/DELTA experimental; technical reviewer Codex được người dùng giao, không ký A/B/C; tracker/hours giữ nguyên.

19/10/2026 — CI exact-main0363995 run39150213101 FAIL: AudioPreparationTest JUnit TempDir xóa next-speaker WAV đồng thời với cleanup async của production close(). Sửa TEST chờ Files.notExists(sample) bằng await5s hiện có, cho cả AfterEach và view kế tiếp; giữ mọi assertion READY/epoch, không đổi production close/FX. Focused10case×5lượt PASS trên Windows (fixture API/media MOCK), không gọi50case duy nhất. Clean main0363995 test/package mỗi lượt942PASS/4SKIP nhưng CI này vẫn FAIL; không gán CI củaPR58 cho main. Full/PG/CI sau corrective sẽ ghi tiếp; LAN0/41, hearing/máy2 NOT RUN.

19/10/2026 — Main `8a43a56` chứa PR47+PR57 (hai P1 và test56). Review kỹ thuật Codex theo ủy quyền người dùng, CI đúng HEAD từng PR SUCCESS; chưa nhận CI main khi workflow chưa vào main. Source tích hợpce38a5f clean/package942PASS/4SKIP mỗi lượt (PG68PASS/2SKIPsubset), GUI11/ExamFlow41PASSlocal, MODE8case×5PASS. C4 supplementaryFAIL ở fixture backoff, corrective harness1072f77 chờ rerun. Đang ghép workflow exact-main/PR52+51 và chuẩn bị finalMSI; physicalLAN0/41/hearing/máy2 NOT RUN. FULL baseline, tracker không tự DONE.

18/10/2026 — S3-P1-01 có bản sửa production local `56a27e9` trên `fix/stage3-audio-publication-20261018`: stress exact-main747/1280FAIL → corrected1280/1280PASS, hash20/20 và partial0; targeted54PASS/2SKIP. [Báo cáo](../evidence/audio-cache-p1-20261018/REPORT.md). Main vẫn `f864034`, chưa chứa fix; lần push đầu bị automatic approval review chặn; ngày19/10 người dùng đã cho phép push hai nhánh, mở PR/CI. Nhánh TEST riêng ghép PR47+test56 để kiểm tích hợp. Người dùng ủy quyền Codex review kỹ thuật, không ghi chữ ký A/B/C; LAN0/41 và nghe thật NOT RUN, FULL mặc định. Tracker không đổi DONE.

18/10/2026 — Task2.1 công bố bộ bàn giao: đã xác minh ancestry PR47HEAD `3044e5b` → local `e4c86aa`; bổ sung nhận ZIP riêng và kiểm SHA256 trên A/B. Đã push nhánh chuẩn bị và mở [Draft PR52](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/52) vào nhánh PR47; không sửa production hoặc merge. LAN **0/41 NOT RUN**, human B **PENDING**. [Hồ sơ công bố](../evidence/t3-c4-physical-prep-20261018/PUBLISH.md).

18/10/2026 — T3-C4 physical LAN: người dùng chưa có máy thứ hai, đã chuẩn bị [bộ bàn giao B/C](T3_C4_PHYSICAL_LAN_ACCEPTANCE.md) trên sourceTask1 `3044e5b`/runtime `3d93820`,41case và ZIP có hash. Kit29checks PASS trên Windows và bản ZIP; không truy cập mạng. LAN **NOT RUN/0case**, B/C review **PENDING**; cần máyB và lượt F vật lý. FULL default, không quyết định gate hoặc cập nhật tracker. [Evidence chuẩn bị](../evidence/t3-c4-physical-prep-20261018/REPORT.md).

18/10/2026 — Đã merge `origin/main f864034` vào nhánh PR #47 bằng merge commit `167caf3`, source sửa `3d93820`, base GitHub là `main`. CLOSE write treo đã có deadline/guard; phản hồi muộn lặp và race ACK trước cleanup có regression. Focused148 PASS, server-focused118 PASS/4SKIP; clean test và package mỗi lượt928 PASS/5SKIP trên PostgreSQL18.1 TEST riêng; T3-C2 restart/resync PASS. T3-C4 lượt mới 42/42 PASS LOCAL; READY_FOR_C_REVIEW; C approval còn thiếu, không merge. FULL default; tracker/QD owner approval giữ nguyên. [bàn giao PR47](../evidence/pr47-stabilize-20261018/REPORT.md).

17/10/2026 — T3-C4 gate LAN: hoàn tất phần chạy được trên một máy, gồm sửa isolation ACK CLOSE muộn, focused 77 PASS, local live gate 42/42 PASS, full Maven BUILD SUCCESS và app-image/ZIP từ source `2b6c9ce`. Runbook hai máy, checksum artifact và checklist human B đã sẵn sàng; artifact/raw lớn nằm trong `target/` ignored. LAN hai máy vật lý và review thật của B còn thiếu, nên trạng thái `PARTIAL / NOT DECIDED`; FULL vẫn mặc định, DELTA thử nghiệm. Không tạo quyết định gate, không sửa TRACKER, không chạy E1/E2/load. [Evidence](../evidence/t3-c4/2026-10-17-lan-gate.md).

18/10/2026 — Chặng 3: A xong T3-A3/A4/A5 (PR #48–#50 trên main `f864034`). T3-C4 gate vẫn NOT_DECIDED/PARTIAL: sửa ACK CLOSE muộn của C ở Draft PR #47 chưa vào main; ghép thử lên main 927 PASS/0 FAIL/4 SKIP và live gate 42/42 PASS cục bộ. T3-C5 có mục cập nhật 18/10. Còn LAN hai máy, B review, T3-B5 máy thứ hai. FULL mặc định. [kiểm tra 18/10](../evidence/t3-c5/2026-10-18-update.md)

17/10/2026 (chiều) — Nhánh ổn định hóa `stabilize/final-rc-20261017`, source `3702b6d` chứa PR39–43 + main `07c4024` + sửa QD-29/lịch sử/cache audio/C1. Clean test/package 924 PASS/4 SKIP, MSI 0.3.10 (SHA256 `d69a6f59…2355881`) installed FullSuite 926 PASS/2 SKIP, CI Windows SUCCESS. QD-31..34 IMPLEMENTED, chờ owner; PR45 đề xuất đóng (superseded). LAN/nghe thật/máy sạch/tải/E2 NOT RUN. [báo cáo](../evidence/stabilize-20261017/REPORT.md)

17/10/2026 — RC TEST mới `test/unified-final-20261017`, source c224ba8/tree22858b4 chứa PR39/40/41/42/43 + main07c4024; PR45 ngoài RC do conflict/race (18/128 lỗi, RC0/128). Clean test/package mỗi lượt904PASS/2SKIP; MSI0.3.9 installed FullSuite906PASS/0SKIP; Flow41/GUI11/concurrency/C1/C2 PASS mới. E1 raw84 giữ hash, rejoin12 và analyzer17output deterministic; CPU time-weighted mới, C confirmation pending. QD29 PROPOSED_FOR_A_C_APPROVAL; không owner approval/merge thật/main, FULL default. [Bàn giao RC](../evidence/integration-final-20261017/REPORT.md). Manual/LAN/hearing/load/E2 NOT RUN; release NOT_READY.

16/10/2026 — **F01 FIXED** trên nhánh local cô lập `qa/f01-listening-result-20261016` từ HEAD PR40 `3fc5bb4`. Code `d19c639` giữ `listeningInterrupted` qua submit/status/history và cảnh báo kết quả SUBMITTED/TIMED_OUT. Regression trước sửa6ERROR → mapping6PASS; clean test/package mới mỗi lượt896PASS/2SKIP; installed MSI0.3.8 FullSuite898PASS/0SKIP. AT11 41/0GAP, Application GUI11, PG concurrency, C1/C2 PASS local; giữ C1 FAIL cleanup đầu, nguyên nhân chưa chứng minh. MSI mới/5439class byte-equivalent,84rawE1 giữ checksum; TEST cluster riêng đã dừng/0schema. A review B và test trong phần A; nghe thật/LAN/máy2/human A-C NOT RUN. Không push/merge/approve/đổi tracker hoặc PR39/40. [Báo cáo F01](../evidence/f01-20261016/REPORT.md).

16/10/2026 — C review E1 trên nền PR40 `3fc5bb4`, nhánh `review/c-e1-methodology-t4c1`. Sửa CPU weighted interval/rejoin E1 và kiểm chất lượng analyzer; 87Java/11Python PASS, smoke Windows/rejoin/reanalysis PASS. [Guide T4-C1](E1_T4C1_MEASUREMENT.md), [review](../evidence/t4-c1/2026-10-16-review.md). Chuẩn bị có thể review, nghiệm thu PARTIAL: raw12run lịch sử chưa tìm thấy để reanalysis, human B/LAN/gate còn mở. FULL default, không E2/merge/tracker.

16/10/2026 — vòng QA PR #39 — [Draft QA #40](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/40) trên nhánh riêng `qa/b-pr39-acceptance-20261016`, source9bfc75e: sửa3lỗi (title smoke/BOM/cache race), clean test và package875PASS/1SKIP mỗi lượt; installed FullSuite876PASS/0SKIP; MSI037 + native History/reorg local PASS. Human/LAN2máy NOT RUN theo xác nhận chỉ một máy; A/C review và cleanup QA DB trong shared container đang dừng còn chờ owner. Không merge PR39/main hoặc cập nhật tracker. [Báo cáo QA mới](../evidence/b-pr39-acceptance-20261016/FINAL_REPORT.md). Các kết quả dưới đây là lịch sử theo SHA riêng.

16/10/2026 — đã hòa PR #35/#38 trên main `c0fd903`, production merge `24f70bd`, checkpoint test `b755646`. Clean test/package mới mỗi lượt872PASS/1skip, PG thật; Flow41/0GAP, GUI11/progress/B4/shared writer/FULL và app-image Audio8 PASS subset. C1 cuối PASS (giữ FAIL ban đầu), MSI/Unicode SDK/WiX BLOCKED; manual/LAN/A-C review pending. [Draft PR #39](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/39) chờ review, chưa nghiệm thu toàn hệ thống. [Báo cáo và giới hạn](../evidence/unified-20261016/verification.md). Các số lịch sử bên dưới giữ theo SHA nguồn.

Cập nhật T3-B5: 15/10/2026 — nhánh `feat/t3-b5-windows-installer` từ main B4 `0b9c5fa`. [MSI per-user và hướng dẫn máy hai](WINDOWS_INSTALLER.md) đã triển khai, artifact nguồn sạch `acac38d`, Java21/JavaFX đi kèm, FULL mặc định. Lượt mới **687 PASS, 0 skip** và MSI cài → login không agent → UI B4/FULL/audio/READY/start/gián đoạn → đóng/gỡ PASS bằng quyền thường, install Unicode bị chặn ghi. [Report](../evidence/t3-b5/2026-10-15-verification.md) giữ hash/artifact/raw path/giới hạn. Phần đóng gói local hoàn tất; **IT01 PARTIAL**, máy Windows thứ hai không IDE/LAN/nghe thủ công/human A review NOT RUN. Không ghép PR35 draft hoặc làm T4/E1/E2; tracker/kế hoạch/.env giữ nguyên.

Cập nhật T3-B4: 15/10/2026 — nhánh `feat/t3-b4-proctor-session-controls`, base main `8f5ac40`. [Tab điều khiển ca/kết quả](PROCTOR_SESSION_CONTROLS.md) dùng backend A1/A2, không đổi server production. Clean package source `06fd215`: **686 PASS, 0 skip**, GUI LT04 local, media rerun 8 checks và FULL dashboard smoke PASS. Lượt media đầu file lock giữ FAIL, nguyên nhân chưa chứng minh; xem [evidence](../evidence/t3-b4/2026-10-15-verification.md); human A review/nghe thủ công/LAN NOT RUN, PR35 draft giữ riêng. FULL mặc định, không B5/E1/E2.

Lịch sử nhánh nguồn #38: Cập nhật B overnight15/10 (main mới0b9c5fa có PR36 B4; cần đối chiếu B4 trước merge): code đề xuất trên nhánh riêng; finalJava848/Flow41/GUI11/C1-C2 local PASS, E1 đủ12run REAL (human C review pending). Unicode/installer thiếuSDK/WiX, physical/LAN/human review chưa nghiệm thu; tracker không đổi. [Bàn giao](../evidence/overnight-20261015/00-summary.md).

15/10/2026 — tích hợp Reading/Listening/history trên `integration/reading-listening-20261015`, base main8f5ac40, history2ca4121 và Reading65f5123. Code9115776: một ExamSession/writer cho hai phần; query status an toàn cho raw ID; scope HTTP không nới monitoring v0. Clean test833 và package833 PASS với PostgreSQL18.6 thật, 0skip. GUI production smoke8nhóm PASS mới trên d809a00, proctor roster/quan sát đã render; C1/C2 hồi quy PASS; [guide](INTEGRATION_READING_LISTENING.md), [evidence mới](../evidence/integration/2026-10-15-verification.md). Nhánh tích hợp đã push qua Git Windows; [Draft PR #35](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/35) đã tạo, chưa merge; human A/B/C/LAN/installer/E1/E2 NOT RUN, ID ACTIVE đặc biệt chờ contract QD29. Những cập nhật cũ bên dưới giữ ngày/SHA lịch sử.

Cập nhật T3-B3: 14/10/2026 — branch `feat/t3-b3-listening-interruption-recovery` kế thừa T3-B2; Media/mạng lỗi dừng player, ghi marker và gửi INTERRUPTED, reconnect/mở lại GET status rồi giữ khóa, không replay/resume. Java669/669 PASS với PostgreSQL thật; app-image Unicode PASS8, có MediaPlayer thật dừng giữa audio và reconnect không phát lại. [Hướng dẫn](LISTENING_INTERRUPTION_RECOVERY.md), [evidence](../evidence/t3-b3/2026-10-14-verification.md). Code complete; review A, rút mạng/kill app thủ công, LAN/máy thứ hai và nghe thủ công chưa chạy. T2-B4 chưa có trên nhánh nên policy Listening tách riêng và gate ghép Reading còn chờ; tracker IN_PROGRESS, giờ null.

Cập nhật T3-B2: 14/10/2026 — branch `feat/t3-b2-listening-playback` kế thừa T3-B1; nhận `LISTENING_START`, kiểm writer/manifest/SHA-256, journal chống replay, playlist JavaFX và câu hỏi theo `audioFile`, báo STARTED/heartbeat/COMPLETED đã triển khai. Java666/666 PASS với PostgreSQL thật; app-image Unicode PASS7. [Hướng dẫn](LISTENING_CLIENT_PLAYBACK.md), [evidence](../evidence/t3-b2/2026-10-14-verification.md). Source `987d9b27f400c339a50914de11de6d094649a271`; code complete, review A/LAN/máy thứ hai/nghe thủ công chưa chạy. B3 còn event gián đoạn và reconnect policy; tracker IN_PROGRESS, giờ null.

Cập nhật T3-B1: 14/10/2026 — branch `feat/t3-b1-audio-download-readiness` từ main `2e784fa`; tải/checksum nền, WAV thử loa riêng, xác nhận nghe và READY có ACK đã triển khai. Java 657/657 PASS (PostgreSQL thật, 0 skip); 6 kiểm tra view/media trong app-image Unicode PASS. [Hướng dẫn](AUDIO_CLIENT_PREPARATION.md), [evidence](../evidence/t3-b1/2026-10-14-verification.md). Source `89082e3d43a322c0e0e210f393cc3b4bf49bd7da`; code complete, review A/thử nghe thủ công/LAN/máy thứ hai chưa chạy; tracker IN_PROGRESS, không ghi giờ chưa đo.

Fix P1 cursor PR #28 — 13/10/2026 (local): code/test `00b1df1` tích hợp main `2e784fa`; validator theo128 ký tự PostgreSQL, không trim/normalize; Maven test/package638 PASS, PG37 PASS, probe history+Listening11/11. Diff giữ21file history gốc; chưa push/retarget/bỏ Draft/merge. B còn validator ASCII/URL encoding cần sửa riêng; nguy cơ sessionId+username vượt128 (500+rollback) chưa sửa theo phạm vi được cho phép. Evidence local `target/pr28-cursor-20261013/REPORT.md`; GUI/human review NOT RUN.

Cập nhật T3-C5: 13/10/2026 — [bản nháp đồng bộ và giới hạn](MONITORING_SYNC_REPORT_DRAFT.md) đối chiếu main `f97c081`. Chỉ tài liệu; [kiểm tra mới](../evidence/t3-c5/2026-10-13-verification.md) không chạy lại runtime. C4 gate NOT_DECIDED/PARTIAL, FULL mặc định; LAN hai máy và B review còn thiếu.

Cập nhật T3-A3:18/10/2026 — A review và khóa contract hook auth/persistence/epoch trên nhánh `feat/t3-a3-delta-server-hooks`, không sửa collector/reducer C. Thêm regression restart store; targeted31 PASS, full package912 PASS/58 opt-in skip. REAL PG/HTTP/WS chứng minh deferred COMMIT failure không success ACK/warning; REAL Spring restart cùng port giữ login/history nhưng đổi instance/epoch, client reconnect và full resync. [Contract](MONITORING_SERVER_HOOKS.md), [evidence](../evidence/t3-a3/2026-10-18-verification.md). Human C review/LAN hai máy chưa chạy; tracker gốc giữ nguyên.

Cập nhật T3-A4:18/10/2026 — nhánh `test/t3-a4-transaction-multiclient-regression` thêm regression save+submit+timeout tranh chấp cùng row PostgreSQL qua deadline và runner tái lập AT01/IT02. Targeted74 PASS; AT01/07/09/10 PASS; REAL PostgreSQL/HTTP/WS hai candidate + proctor, malformed-client survival và four-client isolation PASS; full package912 PASS/58 opt-in skip. [Evidence](../evidence/t3-a4/2026-10-18-verification.md). LAN hai máy/GUI/human C review NOT RUN; tracker gốc giữ nguyên.

Cập nhật T3-A5:18/10/2026 — nhánh `docs/t3-a5-network-concurrency-report` có [bản nháp báo cáo](T3_A5_NETWORK_CONCURRENCY_REPORT_DRAFT.md) kiến trúc/giao tiếp/phân quyền/giao dịch–đồng thời, bảng framework so với phần nhóm thiết kế và ma trận 8 claim → source/evidence. 36 link + audit 6 source owner PASS; docs-only nên không chạy lại runtime, reuse T3-A3/T3-A4. [Evidence](../evidence/t3-a5/2026-10-18-verification.md). Load/LAN/GUI/human C review NOT RUN; vẫn DRAFT.

Cập nhật T3-A2:13/10/2026 — backend start WS sau COMMIT, playback/heartbeat/gián đoạn, khóa đáp án Listening và tổ chức lại đã triển khai trên nhánh `feat/t3-a2-listening-lifecycle`. Full package với PostgreSQL bật PASS629, 0 skip; REAL HTTP/WS/PG/Flyway subset PASS29 (10 Listening +8 audio +11 exam), gồm restart Spring thật và nâng cấp V9→V10. [Contract](LISTENING_LIFECYCLE.md), [evidence](../evidence/t3-a2/2026-10-13-verification.md). B UI/audio/LAN và human C review chưa chạy; tracker/kế hoạch gốc giữ nguyên.

Mốc T3-A1 trước T3-A2: triển khai audio/manifest/READY/gate từ main `80ee1ba`, bàn giao `feat/t3-a1-audio-manifest-ready`, code/test `9ed067a`. Package PASS600 test thường,16 opt-in skip; lượt riêng REAL HTTP/PG/Flyway PASS19. [Contract audio](AUDIO_RESOURCES.md), [evidence lịch sử](../evidence/t3-a1/2026-10-13-verification.md). T3-A1 đã merge PR #29 trên main `8894452`; T3-B1 vẫn đã hoàn tác theo yêu cầu.

Cập nhật T3-C4:13/10/2026 — candidate Application chọn DELTA opt-in + capability hiện tại, FULL mặc định. Code/test sourced9475eb; Java579 PASS. [Guide](MONITORING_DELTA_LIVE_GATE.md), [evidence](../evidence/t3-c4/2026-10-13-verification.md). LOCAL preparation riêng với replay; gate20/10/LAN hai máy/human B review chưa chốt, T3-A3 không tự đánh dấu DONE, E1/E2 NOT RUN.

Cập nhật backend A: 13/10/2026 — nhánh cục bộ `sync/pr23-main-20261013` tại `c64503f` đưa ba sửa P0 của PR #23 lên trên main `cb30f3d` (đã gồm T3-C4). Maven package 596 PASS, 0 skip, có PostgreSQL thật; smoke C1/C2 PASS; Python 10 PASS. Review lại ba sửa P0: đạt, có 4 ghi nhận không chặn. Agent-driven; human review NOT RUN; chưa push/merge. [Evidence](../evidence/t2-a-p0-sync-20261013/2026-10-13-verification.md).

Cập nhật B-ACCEPT-01 phía A: 12/10/2026 — `GET /api/v1/candidate/attempts` (lịch sử lượt thi của thí sinh) đã cài theo đúng đề xuất của B, kèm sửa route không tồn tại trả 404 thay cho 500. Nhánh cục bộ `fix/backend-candidate-attempt-history-20261012` tại `33a2932`, base `main` `ee714fc` + 5 commit P0; **chưa push, chưa merge**. `mvn package` 592/592 với PostgreSQL thật; client B `770c896` gọi server này PASS 13/13. GUI production của B và review C→A NOT RUN. [Evidence](../evidence/t2-a-history/2026-10-12-verification.md).

Cập nhật mới T3-C2: 12/10/2026 — reducer production/resync/capability/scan freshness thử nghiệm có; full vẫn default. Java554/Python10 và REAL Spring/PG/WS/restart, CLI/hồi quy PASS. Readings MOCK trong delta smoke; gate/LAN/human A/B/E1/E2 NOT RUN. [Guide](MONITORING_DELTA_REDUCER.md), [evidence](../evidence/t3-c2/2026-10-12-verification.md).

Mốc C5 trước đó: 11/10/2026 — T2-C5 tài liệu phương pháp và đối chiếu protocol hoàn tất từ main82557bf. Không đổi runtime; không chạy lại Java493/smoke/E1/E2. Kiểm tra mới là source/link/schema/default/evidence integrity. B human review trước phép đo NOT RUN; marker cửa sổ E1, resource sampler/analyzer và delta/E2 còn thiếu. Giữ các kết quả C1–C4 theo evidence gốc.

File này chỉ mô tả **hiện tại**. Lịch sử nằm ở [NHAT_KY.md](NHAT_KY.md); trạng thái từng task nằm ở `Ke_hoach_LT_Mang_5_chang/TRACKER.json`.

10/10/2026: thêm [mục lục log và bằng chứng](../evidence/README.md) theo task để đọc trên GitHub và chuẩn bị prompt cho chat web. Kết quả kiểm thử và trạng thái nghiệm thu giữ theo báo cáo của từng lượt chạy.

## Đang ở đâu

- Chặng: **2, gate tích hợp ngày15–16/10**; backend, Listening B1–B3 và monitoring chặng3 đã có source, đang nghiệm thu bản chung Reading/Listening.
- Gate hiện tại: **15–16/10 lưu/nộp/reconnect + Reading/Listening chung writer**; LAN/human và gate delta20/10 vẫn riêng.

## Mỗi người

| Vai | Tên | Đang làm | Xong gần nhất | Bước tiếp theo | Nhánh git |
|---|---|---|---|---|---|
| A | _chưa gán_ | Backend thi/Listening/history đã hòa với validation và progress; chờ review A | Clean/package872PASS+1skip, PostgreSQL thật | Review quyền, committed data và QD-29 | `integrate/unified-reading-listening-b4-b5-20261015` |
| B | _chưa gán_ | Đã hòa PR #35/#38; Draft #39 chờ review | Giữ B4/MSI nền; GUI11/Flow41/nativeAudio8 và EXE+PG PASS subset | Human review/manual/LAN; installer thiếu SDK/WiX BLOCKED | `integrate/unified-reading-listening-b4-b5-20261015` |
| C | _chưa gán_ | T3-C5 bản nháp tài liệu đã viết; chờ B review | Báo cáo đồng bộ/giới hạn đối chiếu main `f97c081`; kiểm tài liệu riêng | LAN hai máy, human A/B review và gate 20/10 NOT_DECIDED/PARTIAL; FULL default | `docs/t3-c5-monitoring-sync-report` |

## Đang bị chặn

- [13/10] Nhánh để mở PR cho API lịch sử là `feat/backend-candidate-attempt-history-20261013` (`19f669e`, xếp trên PR #23 đã đồng bộ, 602 test PASS); nhánh `…-20261012` bên dưới là bản cũ có lặp commit P0, không dùng để push.
- [12/10] B-ACCEPT-01: backend đã sẵn ở nhánh cục bộ `fix/backend-candidate-attempt-history-20261012` (`33a2932`) nhưng chưa push/merge — cần người dùng quyết định đưa nhánh lên và ghép với nhánh tích hợp của B (`770c896`), rồi B chạy lại GUI production theo mục 5 của evidence — hạn gate 15/10.
- [12/10] Gọi sai phương thức HTTP (ví dụ `POST` vào route chỉ có `GET`) vẫn trả 500 thay vì 405 — A xem có sửa cùng handler lỗi không; không chặn B-ACCEPT-01.
- T2-C2 human A/B review: **NOT RUN**, không ngăn code/test/PR/merge đã được người dùng ủy quyền nếu GitHub không có yêu cầu bắt buộc thiếu. [Evidence C2](../evidence/t2-c2/2026-10-10-verification.md). MT01/MT07/MT08 giữ PARTIAL cấp toàn hệ thống do candidate GUI/LAN chưa chạy; các kiểm tra kỹ thuật mới PASS có nhãn REAL/MOCK/SIMULATED. Origin legacy/trước heartbeat ACK đầu UNSPECIFIED; queue RAM/full RAM một JVM, gap mới cần HTTP refresh.
- C1 lỗi annotation Scheduled đã sửa bằng worker riêng và merge PR#18 tại683adfc. Nhánh C1 giữ local/remote. Không bật scheduling toàn app hoặc sửa ExamTimeoutService; issue scheduler bài thi chuyển A xem riêng. [Evidence sửa C1](../evidence/t2-c1/scheduling-fix/2026-10-10-verification.md). C2 đã chạy lại hồi quy tự STALE/TTL/capacity/shutdown PASS.

- C4 human B review: **NOT RUN**; A/B cần xem RealtimeClient và handler/registry hooks. Đã kiểm BYTE/fragment/retry/concurrent-decorator/failure/cleanup và B3 regression. Log COMPLETE chỉ cho file đã cung cấp; chưa đo CPU/memory/latency/miss-rate hoặc lợi ích delta. ProcessHandle scan REAL riêng, event/overflow MOCK; WMI/ETW chỉ khảo sát. Không tự làm B4/chặng2/E1/E2.

- GUI manual end-to-end: **NOT RUN** — người dùng/B chưa nghiệm thu thủ công. T3-C4 đã tự động thao tác candidate/proctor Application thật và ProcessHandle/Edge trên một máy, bổ sung cho B3 component trước đó; [evidence C4](../evidence/t3-c4/2026-10-13-verification.md). Local automation không thay LAN hai máy hoặc toàn MT01/MT08.
- LAN second-machine test: **NOT RUN** — chưa có máy Windows thứ hai thật được cung cấp cho phiên này; không thay bằng localhost.
- Review C→A2 và A→B2: **NOT RUN trong phiên Agent**; không ghi duyệt thay thành viên. Task.txt C2 cho phép merge khi code acceptance/PR mergeability PASS; review B→C2 cũng NOT RUN. PR #2/#3 đã merge, Git C2 cuối xem final report.
- PR #1 đã merge trên main `34a7835`; các mục GUI/LAN thiếu chứng cứ lịch sử vẫn NOT RUN.
- A3 đã thay deny-all A2 bằng assignment scope JDBC; candidate sở hữu/proctor được phân công vào attempt ACTIVE. Không seed assignment production; khi chưa tạo lượt thi, scope vẫn rỗng. Schema tối thiểu chưa phải hệ thống đề/ca thi.
- B2 đã có real integration riêng: production opener, heartbeat/ACK attemptId null, ERROR, server-off/re-auth. Headless model lock PASS; không coi là GUI manual hoặc event/presence đã xong. Evidence mới: `evidence/t1-b2/2026-10-04-real-integration.md`.
- C3 đã nối production C2/B2/A3, fresh scope + explicit candidate start/stop; queue500/in-flight4/retry bounded và MONITORING_GAP persistence/ACKV3. B3 không đổi collector/delivery/heartbeat lease, C3/A4 real regression đã chạy lại PASS. MT01/MT08 còn PARTIAL toàn case: B3 dashboard component đã kiểm, full/delta chặng2 và GUI candidate/LAN chưa nghiệm thu. Evidence B3 `evidence/t1-b3/2026-10-04-verification.md`; human A review NOT RUN.
- DB-off smoke A3: NOT RUN vì PostgreSQL đang dùng chung; deferred constraint trigger TEST trong schema riêng đã kiểm lỗi COMMIT thật, rollback, RETRYABLE_SERVER_ERROR, không ACK/warning. Review C→A3 NOT RUN.
- Bằng chứng A2: `evidence/t1-a2/2026-10-04-verification.md`. TRACKER.json giữ nguyên.

Ghi theo mẫu: `[ngày] Ai bị chặn — bởi cái gì — cần ai làm gì — hạn`.

## Contract đang chờ

| Hạn | Ai cung cấp → ai dùng | Nội dung | Trạng thái |
|---|---|---|---|
| 03/10, giờ đầu | C + mẫu auth của A → A, B | Login, role/attempt scope, WS event/ACK/heartbeat v0 | A3/C3 collector→event/ACK/push/timeline thật; overflow gap riêng; state chưa có |
| 04/10 | B → C | Interface collector → network | MonitoringTransport B2 send=write, ACK/ERROR riêng; C3 dùng event contract A3, state chưa có |
| 04/10 | A → B | Cách gửi credential HTTP/WS, phạm vi subscription | Bearer header; B3 đã consume warning/timeline, HTTP scope refresh và dedupe attempt/event |
| 04/10 | A → B, C | Scoped heartbeat, roster/presence/history | A4 V4; B3 consume roster/history/push presence, revision, stale và HTTP recovery; REAL integration PASS; schema không đổi |
| 09/10 | A → B, C | Import đề, answers/revision, requestId, writerEpoch, deadline/state, mã lỗi | Xem contract/source mới và bằng chứng theo task; hàng lịch sử chưa được nghiệm thu toàn luồng |
| 12/10 | A → B | Trạng thái đã chốt, takeover writer, state khi reconnect | Xem contract/source mới và bằng chứng theo task; hàng lịch sử chưa được nghiệm thu toàn luồng |
| 16/10 | A + B → C | Audio manifest, READY/start/interrupted | A1/A2 backend đã có; B1 tải/checksum/thử loa/READY đã có trên branch riêng; B2/B3 playback/gián đoạn phía client chưa có |
| 16/10, khóa 18/10 | C → A, B | Monitoring epoch/base/sequence, định nghĩa metric | C1 OPEN/full/CLOSE/epoch/sequence/state đã merge; C2 bổ sung EventOrigin/deliveryStatus và HTTP gap theo QD-13, server trước client mới; T3-C2 encoder/reducer/base/resync/freshness có qua capability/flag thử nghiệm; gate và metric E1-E2 chưa có; human A/B review NOT RUN |

## Câu hỏi mở cần nhóm hoặc thầy trả lời

1. Mỗi người thật sự có bao nhiêu giờ mỗi tuần? (kế hoạch cần khoảng 25; nếu chỉ 15 phải xin thu hẹp phạm vi)
2. Gán tên thật vào A/B/C: ai từng làm JavaFX, Spring, SQL transaction?
3. Ngày gặp thầy trong tuần 05–11/10? Có đủ hai máy Windows không?
4. Thầy có chấp nhận phạm vi TOEIC rút gọn, hướng đóng góp polling/delta và chính sách Listening gián đoạn không?
5. Kênh nộp cuối: USB qua lớp trưởng hay Google Drive?

## Dự báo giờ (cập nhật ngày 08/10)

| Vai | Giờ đã dùng | Giờ còn lại ước tính | Ghi chú |
|---|---:|---:|---|
| A | — | — | |
| B | — | — | |
| C | — | — | T3-C1 candidate encoder/test/replay có; B review/live delta/gate và E1/E2 NOT RUN. Không điền giờ chưa đo. |

## Bàn giao T2-C3 ngày10/10/2026

C2 đã merge PR#19 bằng merge commit b2926ea, giữ C1/C2 local/remote. C3 tạo từ main này, không tiếp tục nhánh cũ. Trace tùy chọn mặc định tắt, full/event encoder và lõi reducer C1 production, oracle độc lập/fixture viết tay/seed faults có bằng chứng; [cách chạy](MONITORING_TRACE.md), [evidence](../evidence/t2-c3/2026-10-10-verification.md). Human B review/A adapter review NOT RUN, không giả approval; người dùng cho phép merge commit khi không blocker GitHub và giữ C3. Trạng thái Git cuối xem báo cáo/PR. Không suy UNKNOWN từ full mất; CLI không presence/TTL/auth/DB, không MT02–MT06 delta PASS hoặc E1/E2. Dừng sau C3.

## Bàn giao T2-C4 ngày10/10/2026

C4 từ main f6d46b2, code0164297. [Hướng dẫn](CONTROLLED_PROCESS_HARNESS.md), [evidence](../evidence/t2-c4/2026-10-10-verification.md). Ground truth độc lập từ parent, clock mapping cùng JVM, TEST owned identity giữ đến collector stop; production8 tên và network contract không đổi. Java493 PASS, full30 REAL child:27/3/0, nguồn scan REAL/gate SIMULATED, checksum/rejoin/cleanup PASS. C1-Gui/C3-replay/measurement/event riêng PASS; event chạy song song lần đầu FAIL retained, cause chưa chứng minh. Human B review NOT RUN; B cần xem policy/trace/clock/identity/lifecycle. Chưa C5/delta/E1/E2; dừng sau C4. Git merge cuối xem báo cáo/PR, không giả review.

## Bàn giao T2-C5 ngày11/10/2026

[Phương pháp baseline](MONITORING_BASELINE_METHOD.md), [kiểm tra tài liệu](../evidence/t2-c5/2026-10-11-verification.md). Base82557bf có mục lục evidence sau C4 merge PR#21. Tài liệu không đổi collector/harness/reducer/wire; sửa mô tả byte type/sequence cũ, phân biệt hold200ms với parent-observed lifetime và policy TEST/production. Rate có eligibility chung, INCONCLUSIVE riêng, latency chính xác NOT MEASURED; E2 chưa delta. Human B review/GUI/LAN/E1/E2 NOT RUN. Không sửa tracker/kế hoạch/old artifacts; QUYET_DINH giữ nguyên vì không quyết định kiến trúc mới. Git cuối xem PR/final; dừng sau C5.

## Bàn giao T3-C1 ngày11/10/2026

[Encoder candidate](MONITORING_DELTA_ENCODER.md), [evidence](../evidence/t3-c1/2026-10-11-verification.md). Main remote12e2838 sau C5PR#22; chưa branch/PR delta hoặc A3 hook delta. Production factory/transport/server giữ full; baseSequence encoder/replay đã có, server delta/resync/freshness/measurement classification và gate chưa làm. STALE6s/full30s bàn giao A/C, không âm thầm thay timeout/heartbeat. Human B review/ChatGPT planning-review NOT RUN theo opt-out; không thay tracker/task completion hoặc MT02–MT06 toàn hệ thống. Người dùng ủy quyền merge commit khi không blocker GitHub và giữ nhánh; Git cuối xem evidence/PR/final. Dừng T3-C1, không T3-C2/E1/E2.

## Bàn giao T3-C2 ngày12/10/2026

Dùng lại AuthorizationService/SessionAuthenticationService/MonitoringStateService C1 và registry/maintenance. Thêm lõi MonitoringStateReducer, resync DTO/handler/parser/client hook, optional heartbeat capability và empty delta từ scan mới; candidate factory full không đổi. T3-A3 chưa tự đánh dấu DONE; A/B cần review auth/binding/store/global lock/ACK/push/parser/capability/clock/lifecycle. [Evidence](../evidence/t3-c2/2026-10-12-verification.md), QD17. Gate MT02–MT06/E1/E2/full candidate GUI/LAN/human review NOT RUN; không thay tracker. Dừng T3-C2, chưa C3/C4. Git cuối trong PR/final.

## Bàn giao T3-C3 ngày12/10/2026

- [Guide](MONITORING_DELTA_FAULT_REPLAY.md), [evidence](../evidence/t3-c3/2026-10-12-verification.md):3MOCKproduction-schema traces/handwritten expected,72positive acceptance replay cases/4detected negatives, summary sinh Java, repeat report byte-identical. Java569/PASS (+15), CLI/ProcessHandle/owned Edge regressions PASS; không source/network benchmark.
- Fix replayer receiving connection dùng PREVIOUS_CONNECTION đúng contract; không sửa encoder/reducer/server/transport production. ACK baseline enumeration, resync recorded/no feedback; retry/ACK-loss/presence/TTL/network/DB trong replay NOT MODELED. Seeded reconnect có0common synced checkpoints ghi riêng, không claim state equivalence ở đó.
- MT02–MT06 PARTIAL cấp toàn gate với evidence REPLAY; T3-C4/human B/GUI/LAN/hai desktop/E1/E2 NOT RUN. Không tự quyết QD10/bật delta hoặc thay tracker/.env/kế hoạch gốc/migrations/exam/PR A-B. Git cuối trong PR/final; giữ nhánh C3 và sync main an toàn theo ủy quyền.

## Bàn giao T3-C4 ngày13/10/2026

Baseee714fc (C3PR26). Opt-in/current capability/fixed mode, reconnect fresh OPEN/full, scope heartbeat callback, actual candidate/proctor Application LOCAL GUI; code2d867c2 + fix stdoutd9475eb. [Guide](MONITORING_DELTA_LIVE_GATE.md), [evidence](../evidence/t3-c4/2026-10-13-verification.md), QD19 liên kết QD10 presence. Source/JAR/raw/failed attempts giữ riêng, không sửa `.env`/tracker/kế hoạch/migrations/exam/PR A-B. Gate preparation/PARTIAL, cần LAN hai máy và B review; không giả approve hoặc gate20/10 đã diễn ra. Git cuối trong PR/final; giữ nhánh và sync main khi an toàn. Dừng sau C4, không C5/E1/E2.

## B overnight 15/10/2026 — đang thực hiện

Branch riêng `agent/b-overnight-followups-20261015`, base PR35 11c5da9. Phase01 backend ID/progress đề xuất có targeted49 PASS PostgreSQL; human A/C review và QD29 approval pending. [Evidence](../evidence/overnight-20261015/phase-01/verification.md). Phases02–06 chưa xong, tracker không đổi.

Overnight phase02: E1 harness có dry/SMOKE + analyzer thực, C human review pending; chưa E1 final. Phase03 tiếp theo exact AT11/GUI regression.

Overnight phase03 localgate PASS (839/flow41/GUI8/C1-C2); phase04 bắt đầu proctorcontrols/progress. Humanreview/LAN/E1final pending.

Overnight phase04: proctor session/progress local gate PASS, cần A/C review API và B review UI; đang sang package Windows. [Guide](PROCTOR_SESSION_PROGRESS.md).

Overnight phase05 PARTIAL: ASCII app-imageMedia8 PASS, wrapperUnicode/installer BLOCKED thiếu mt.exe/WiX; không cài/gỡ. Phase06 E1/manual chuẩn bị tiếp. [Guide](WINDOWS_PACKAGING.md).

### Công bố overnight 15/10

[Draft PR overnight](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/38) dùng nhánh public riêng, base integration `11c5da9`. Main mới `c0fd903` có PR #36 B4 và PR #37 B5: owner cần đối chiếu cả UI/controller lẫn pipeline Windows trước merge; chưa kiểm code ghép. Java đã kiểm `c1601b5` không đổi; E1 có 11 inconclusive nên PARTIAL. Evidence MSI upstream không được dùng thay kết quả package overnight. [Bàn giao](../evidence/overnight-20261015/00-summary.md).
