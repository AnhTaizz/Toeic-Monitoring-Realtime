# Kết quả thực thi Phase3 — 19/10/2026

**CODE_READY_STAGE3_ACCEPTANCE_PENDING**. Hai P1 đã sửa và vào main: [monitoring47](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/47), [audio57](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/57). Handoff52 + report51 merged qua [58](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/58); [59](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/59) sửa race cleanup TEST. Không còn P0/P1 production đã xác nhận chưa xử lý trong phạm vi task.txt; không nhận bằng chứng máy thật khi chưa có.

## Source và kiểm cuối

Build từ clean `origin/main cfbba580886ede7b1b5a4f56875197b10f284514` (merge59), Oracle21.0.8 x64/Maven3.9.11/PostgreSQL18.1 TEST SCRAM loopback/Windows. [CI exact main SUCCESS](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39151021995). Worktree kiểm riêng sạch trước build; checkout main cũ có uncommitted files của người dùng được bảo toàn, không reset/stash/đổi branch thành viên.

| Lượt | Kết quả thật |
|---|---|
| Maven clean test | 942 PASS / 4 SKIP / 0 FAIL |
| Maven package (chạy test lại) | 942 PASS / 4 SKIP / 0 FAIL |
| Installed MSI FullSuite | 943 PASS / 3 SKIP / 0 FAIL |
| PG subset clean/package | 68 PASS / 2 SKIP mỗi lượt; 70 case trong tổng, không cộng thêm |
| MODE stability | 8 case × 5 lượt PASS |
| AudioPreparation cleanup | 10 case × 5 lượt PASS sau TEST fix |
| REAL GUI/HTTP/WS/PG local | ReadingListening11/11, ExamFlow41/41 PASS (sourcece38, production tương đương) |
| C4 local | 42/42 PASS, sourcea96eb24; faults SIMULATED; một máy Windows |
| Kit source + portable | Mỗi bộ29 checks PASS; manifest22files; initialize41 dòng NOT RUN |

[Case results](codefreeze-main-clean-test.json), [package](codefreeze-main-package.json), [installed](installed-full-suite.json), [cleanup](cleanup.json), [CI](ci.json), [repeats](focused-repeats.json). XML gốc có process environment nên chỉ giữ private ignored; công bố tên case/kết quả và SHA256 XML, không công bố properties/system-out. Hai symlink case SKIP do thiếu quyền Windows; clean/package còn hai opt-in installed/image SKIP. Installed skip cụ thể xem JSON, không suy PASS cho case skip.

Lượt main0363995 local clean/package942PASS/4SKIP nhưng CI FAIL do TempDir đua cleanup WAV. Giữ failure CI39150213101; fix59 chỉ TEST đợi sample biến mất bằng await5s sẵn có, không xóa READY/epoch assertions hoặc chặn FX trong production. Không rerun mù để đổi đỏ thành xanh.

C4 supplementary ban đầu FAIL sau27check tại EVENT_DB_COMMIT_FAILURE: FULL thường đã5SEND/5retryableERROR/noACK trước khi peer khác scan/gỡ fault; A retry commit được. Harness1072f77 đưa normal peer về production backoff1s/8s, instrumented peer giữ100/400ms; maxAttempts5/await12s/assertions giữ. [Failure](T3C4-27f43dfc-7106-4a94-8e52-82ed6cafe5e4.json), [corrected42PASS](T3C4-429d40d1-b67b-44a9-bc27-126c07f9a61b.json). Measurement INCOMPLETE12endpoint/140categoryrow do killed endpoint thiếu FINAL; analyzer exit2 không phải E1/E2/performance PASS. Không dùng local42 thay physical41.

## Artifact để chạy máy thật

[Manifest/hash](artifacts.json), [MSI provenance](msi-build-info.json). MSI0.3.11 source`cfbba580886ede7b1b5a4f56875197b10f284514`, dirty=false, FULL default; SHA256 `edd0ceb55d2512d046c972aff2966382d0ad64051e29cc6a14035c643b67a214`. Binary ở `target/stage3-release-20261019/ToeicMonitor-0.3.11.msi`. Kit `target/stage3-release-20261019/T3C4-cfbba58.zip`, ZIP SHA256 `2122b8b3dce57d5befb622603e9c674d9a0786d630b5ed93e75d8c668abf6ab1`. Client JAR trong kit cùng byte MSI; server/test-support từ lượt package cùng source. Không commit binary hoặc giữ hash cũ sau rebuild.

Local MSI thực sự cài Unicode path bằng quyền thường, deny write install dir, launch không JAVA_HOME/JDK trên PATH/TEST agent, kiểm bundled JVM, đóng; FullSuite dùng installed production EXE + TEST agent qua HTTP/WS/PG/MediaPlayer, Reading/Listening/FULL/shared writer/interruption/reconnect; hoàn nguyên ACL và gỡ. Candidate thứ hai READY là MOCK, hearing/socket fault SIMULATED. Đây là một máy, không thay IT01 máy hai hoặc nghe người thật. Kit pin source/hash được cập nhật chính xác; guard/từng file/checklist không nới. ZIP/MSI cũ giữ lịch sử và không đại diện source cuối.

## Đối chiếu 18 task, không tự sửa tracker

| Task | Source/evidence và mức nghiệm thu |
|---|---|
| T3-A1 | AudioRuntimePostgresTest10PASS; manifest/size/SHA/scope/READY/start gate; [gốc](../t3-a1/2026-10-13-verification.md). CODE_READY |
| T3-A2 | ListeningRuntimePostgresTest16PASS/2SKIP clean; start/gián đoạn/new attempt/old deadline; [gốc](../t3-a2/2026-10-13-verification.md). CODE_READY |
| T3-A3 | [hooks](../t3-a3/2026-10-18-verification.md), live42 rollback/restart/re-auth; ACK sau COMMIT. CODE_READY |
| T3-A4 | ExamRuntimePostgresTest38PASS, history bounds4PASS, Flow41; [gốc](../t3-a4/2026-10-18-verification.md). LocalPASS, physicalgate pending |
| T3-A5 | [report](../../docs/T3_A5_NETWORK_CONCURRENCY_REPORT_DRAFT.md) đúng v0/claims/evidence. DRAFT_READY |
| T3-AR | Codex được người dùng giao review B/audio/installer theo source+CI; không ký A/C hoặc ghi giờ |
| T3-B1 | AudioFiles23 + Sharing10 (2symlinkSKIP tổng), Preparation10, real Windows stress1280/1280; [P1](../audio-cache-p1-20261018/REPORT.md). Hearing pending |
| T3-B2 | Playback8PASS, PG/GUI/installed production; startID chống lặp, câu hỏi/audio. LT01 nghe thật pending |
| T3-B3 | PG/installed interruption + no autoplay/replay; [gốc](../t3-b3/2026-10-14-verification.md). Rút LAN/kill vật lý pending |
| T3-B4 | ProctorSessionController10 + API9PASS, GUI11/Flow41; [gốc](../t3-b4/2026-10-15-verification.md). LocalPASS |
| T3-B5 | MSI0.3.11 local lifecycle+FullSuite PASS; máy Windows thứ hai NOT RUN |
| T3-BR | Codex technical review PR47/C5 theo người dùng giao; không ký B/A |
| T3-C1 | Delta delivery32 + payload3PASS, policy/scope/replay; [gốc](../t3-c1/2026-10-11-verification.md). Experimental |
| T3-C2 | Delta reducer7PASS + full5PASS, epoch/base/resync/restart local; [gốc](../t3-c2/2026-10-12-verification.md). Experimental |
| T3-C3 | DeltaReplay9 + ReplayCampaign15PASS, MT02–06 fixtures; [gốc](../t3-c3/2026-10-12-verification.md). Trace/SIMULATED, không physical |
| T3-C4 | Corrected local42/42; physical0/41. C quyết định/B review sau máy thật |
| T3-C5 | [report đã refresh](../../docs/MONITORING_SYNC_REPORT_DRAFT.md), 5000/409, RAM/DB, gaps, fallback, E1/E2 giới hạn; gate pending |
| T3-CR | Codex kiểm A qua PG/concurrency/auth/rollback/restart evidence; không ký C/B |

TRACKER giữ status/actual_hours/evidence theo quy tắc cần xác nhận riêng; bảng này là kết quả thực thi, không phải trackerDONE. Review kỹ thuật user-delegated đã có trên PR47/57/58/59; không đòi thêm một vòng review tổng quát. QD31/32/33/34 được agent review theo giới hạn đã công bố, không gán chữ ký A/B/C.

PR53/54/55 giữ packet lịch sử, không là dependency runtime; test56 đã vào main giữ tác giả qua57, C5 patch đã refresh. PR45 chờ B disposition superseded, không merge patch có regression hoặc tự đóng nhánh thành viên. Không xóa branch lịch sử. [Runtime equivalence](runtime-equivalence.json) và [handoff cụ thể](../../docs/STAGE3_FINAL_HANDOFF.md).

## Việc còn bắt buộc

1. B+C: hai Windows vật lý, 41case với rút LAN thật/UNKNOWN/re-auth/OPEN/FULL/no falseACK/answers giữ/no autoplay; ghi Actual/evidence/reviewer.
2. B: nghe và xác nhận LT01–LT04/audio/câu hỏi/thử loa thật.
3. B: cài/chạy/gỡ MSI mới trên máy hai không IDE/JDK trên PATH; A review evidence.
4. C quyết định gate, B review theo dữ liệu máy thật. Giữ FULL baseline, DELTA chưa production-ready; không cần E2 để hứa DELTA tốt hơn.

Chỉ cập nhật STAGE3_DONE/tracker sau đủ bằng chứng và xác nhận; hiện physical/manual/second-machine NOT RUN.
