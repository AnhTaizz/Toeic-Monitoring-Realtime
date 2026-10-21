# MON-09 C+B — báo cáo slice tích hợp timeline (21/10/2026)

Base `dcb124d2c3098beac861150889f1dfbb81a89d69`; nhánh `feat/cb-mon09-timeline-20261021`. Runtime cuối `506634750011bdb9d8b1de0f28f14a926b7c9202`, build/full verify `1c1523b741954fccd80ed59f989fc0c4d00a7246`; commit sau chỉ bổ sung test/tài liệu/evidence, không thay runtime. Source test cuối trước publication `05e154ab2b90c820b7d0cd22fcbedb45bbbc5064`. [Receipt + hash](receipt.json), [runbook một lệnh](../../docs/MON09_MULTI_SIGNAL_TIMELINE.md). PR/CI/merge final được cập nhật tại PR; report này không tự ghi CI pending thành PASS.

## Hiện trạng và thay đổi

Main gồm PR87 focus/88 window/89–90 USB TEST/91 display/92 activity. Đã đọc source: real FX observers đã nối opt-in candidate; dashboard hiện có RoomFeed/RoomModel/process/history/presence/gap nhưng chưa có typed-history consumer. USB thật chỉ TEST bridge; không có production binding. Kiểm open PR không thấy MON-09 trùng, không merge các PR EX/UI khác.

Thêm parser đọc API typed hiện hữu, controller polling hữu hạn và trang Timeline trong dashboard. Process snapshot tách event history; Listening status dùng read endpoint/phân quyền hiện có, conditional interruption projection, không sửa business/wire. Không POM/dependency/server-main/migration/protocol/workflow/default changes. API token chỉ header. Ownership/generation/cancel/scope/ACTIVE-only và semantics tại runbook.

**Bug tích hợp có bằng chứng:** `CandidateExamSignalMonitoring` tạo observerSessionId ngẫu nhiên khác collector lease. `RealtimeClient.sendGuarded` từ chối trước socket; capability/heartbeat READY nhưng không có COMMIT ACK. Run `mon09-identity-before` giữ **1 FAIL**, WIP test; receipt chứng minh producer bytes bằng base dcb, không gọi đây là clean-base JUnit run. Sửa tối thiểu: context dùng collectorSessionId, không nới guard/capability. Source `81d8410` sau sửa nhận **9 ACK**, history đúng identity; real existing FX types display/activity/focus/window/status, dropped/discarded 0, delivery worker dừng. Kích hoạt Stage API, không OS mouse/keyboard.

UI trước sửa text UNSYNCED/receivedAt=null có thể hiểu là 0 process; source506 ghi rõ chưa snapshot, REAL GUI assertion PASS. Review cuối nghi nhầm client/server sequence; oracle hai collector runs với client seq900→1, server seq100→101 **PASS ngay**, code đã dùng entry.sequence(); chỉ thêm regression, không tuyên bố bug/fix ở đây.

## Test đã chạy (không cộng các lượt lặp thành test độc lập)

| Kiểm | Source/nhãn raw | Kết quả |
|---|---|---|
| Baseline tập trung trước sửa | dcb124d sạch; `mon09-check/baseline` | 227 PASS, 0 SKIP |
| Hồi quy consumer/shared/FX/USB | WIP; `consumer-regression` | 225 PASS |
| Listening parser/controller + API cũ | WIP; `listening-unit-fixed` | 36 PASS |
| Candidate real FX → real WS/PG/ACK/history | 81d8410; `mon09-identity-81d8410` | 1 PASS; 9 ACK, 5 committed types |
| Mixed MOCK → real WS/HTTP/PG/dashboard | 71c49ff; `mon09-mock-complete` | 1 PASS; attempt A19/B1 rows; actual queue3drops, ACK-loss SIMULATED |
| Real CIM → real WS/PG/status/dashboard | 5066347; `mon09-real-complete` | 1 PASS; 8 scans READY/count0; ACK1/UI1; 0 USB event |
| Dashboard JavaFX cũ | 1c1523b; `legacy-dashboard-gui` | 12 PASS; controls/Scene MOCK API |
| Toàn reactor `mvn verify` (không clean) | 1c1523b; `full-verify-final` | **1263 PASS /154 SKIP /0 FAIL/ERROR** (1417 total) |
| Parser/model + cross-run sort oracle | 05e154a; `server-sequence-confirmed` | 8 PASS; runtime không đổi |
| JAR/source/dependency checks | build1c/runtime506 | **24/24**; hash4JAR trong receipt |
| Portable EXE startup | build1c/runtime506 | Login window đúng, bundled JVM, đóng bình thường; không login/network acceptance |

Full verify module total/skip: protocol86/0, spike35/0, client862/27, server434/127. Opt-in PG/CIM/GUI/packaged/installed không bật trong full verify; 154 SKIP không là PASS. Các PG/GUI runs opt-in tách phía trên. JAR kiểm compiled classes/resources/nativeFX, không CIM/test bridge trong client; server không client/monitoring-spike runtime dep, nested protocol đúng hash. EXE app-image ASCII path, không MSI/Unicode/installer acceptance. Artifact/log không upload GitHub; receipt chỉ ghi hash/provenance, không giả link tải.

Giữ các thất bại phát triển ở raw: `first-compile` helper Ui không tồn tại; `parser-unit` nullable last cursor; `parser-unit-fixed` query oracle thiếu querystring; `lifecycle-unit` expected vượt3page budget; `listening-unit` Gson fixture Instant reflection. Đã sửa đúng implementation/fixture theo contract, không skip/swallow/retry mù/tăng production timeout. `server-sequence-before` thực tế PASS, dù nhãn chứa before, không ghi FAIL giả. Full verify cũ820703c:1259PASS/154SKIP; Mock/Real cũ820 giữ riêng, không thay source bằng SHA mới.

## Bằng chứng nhìn thấy

Ảnh đã rà: chỉ MOCK seed display names/TEST UUID, không token/password/IP/answers/serial/CIM object. Đây là **JavaFX Scene snapshots**, không OS screenshot/human acceptance. Source ảnh chính xác trong receipt; ảnh MOCK71 không được gán source506/1c. Các thay đổi sau71 chỉ no-snapshot text branch/test, không thay nguồn MIXED.

- [Tổng hợp MOCK, 19 dòng](screens/overview-mock.png): process fixture/FULL, typed events, Listening NOT_STARTED, real transport/storage.
- [USB MOCK và UNKNOWN](screens/usb-status-mock.png): weak identity/PARTIAL; filter vẫn giữ gap/unavailable banner.
- [Queue gap MOCK input, actual3drops](screens/gap-mock.png): số bỏ thực qua queue chung.
- [CIM thật READY/count0](screens/cim-real.png):1status, không bịa USB event, chưa process snapshot rõ ràng.

Mixed TEST kiểm hai attempts độc lập, assigned PROCTOR, candidate/wrong candidate/unassigned proctor403; revoke token TEST thật401 làm expired/close/clear; reconnect thật +fresh scope/catch-up,19identity không trùng. Session/assignment tạo bằng API/seeder; directSQL chỉ denial fixture revoke login, không cấp scope/nhét event. ACK-loss là simulation, queue overflow thực. Listening thật chỉ NOT_STARTED; interruption projection/heartbeat recovery/process gap có oracle MOCK, chưa playback/interruption E2E hay heartbeat-loss campaign mới.

## Cleanup, publication và giới hạn

Ba clean-source opt-in runs trên đều schemaRemaining0, PG stop0, postmasterPID absent. REAL CIM children0/readersTerminated/scan+control worker stopped; candidate delivery worker stopped; view/API/listener/requests/timer đóng theo ownership, shared transport chỉ fixture chủ sở hữu đóng. Không đọc/sửa `.env`, DB dev, kill theo tên hoặc xóa volume. Raw lớn/PGdata/JAR/ảnh chưa rà ở worktree target ignored; `localOnlyManifest` là hash kiểm local, **không** evidence có thể tải trên GitHub. Public là source/test/report/receipt và4ảnh đã rà. Không công bố credential hay full exceptions/CIM objects. Runtime source/JAR không đổi sau build1c; source test publication mới không làm artifact giả mang SHA merge.

FULL default và experiment OFF giữ nguyên. USB physical **0/12 NOT_RUN**, second-display physical, desktop mouse/keyboard/manual, LAN hai máy NOT_RUN. READY0/CI PASS không là hotplug. MON-09 chỉ technical slice, chưa DONE; A/B/C human approval, policy/ADR/hardware/gates còn pending; offline60s/retention30ngày chưa được phê duyệt. Không sửa backlog/giờ công hoặc chuyển E1/E2/MON-08. User ủy quyền implementation/review kỹ thuật/merge theo điều kiện, không ký thay thành viên B.
