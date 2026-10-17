# Review PR39 — vòng QA mới

Source PR39 `093429ab1abb19f9c6d37be9b86ae8e00f11a284`, main `c0fd9036ba340b0b0a686fb2ce637050b9a3b7b4`. Source sửa lỗi đóng băng `9bfc75ec7c2d91af6f714602727389547c3b3288`, branch `qa/b-pr39-acceptance-20261016`. Đây là review kỹ thuật mới trong cùng phiên Codex, không phải chữ ký reviewer độc lập/human A/B/C. Không kế thừa PASS nguồn.

## Call chain đã đọc và đối chiếu

| Class / điểm vào | Luồng và invariant đã kiểm | Bằng chứng/giới hạn |
|---|---|---|
| `ToeicClientApplication` | Launcher khởi tạo cấu hình trước FX; login async → role server → scene; viewGeneration/monitoringGeneration bỏ callback cũ; release đóng exam/history/dashboard/collector/API/WS. Chỉ Candidate tạo collector. | GUI Application hai role; title runtime đối chiếu smoke; không dùng role UI thay auth. |
| `ExamEntry` | GET status → đề đã validate → takeover cuối cùng cho ACTIVE; final chỉ kết quả. Lần entry mới mới xin writer; status/history không takeover. | Flow/GUI; takeover timeout không được hiểu thành cấp quyền thành công. |
| `ExamSession` | Một owner executor; full-map autosave; request frozen qua unknown retry; ACK cũ theo identity không sửa bản mới; revision không lùi; submit frozen, status reconciliation/timeout do server quyết. | AT11 exact40/42 và PostgreSQL; loss seam SIMULATED. |
| `CandidateExamView` | Sở hữu một ExamSession, truyền epoch sang AudioPreparationView; phase/epoch đổi đóng audio; Listening interrupted chỉ khóa câu Listening và áp đáp án committed; Reading giữ thay đổi. | GUI shared writer/Reading sau restart; subscriptions/timer đóng theo view. |
| `ProctorSessionController` | Một workflow B4; executor2/queue8; read generation; READY monotonic5giây; busy chặn double-click; pending requestId/manifest/reorg giữ qua unknown outcome; start chỉ từ server push. | Controller regression, B4 API/GUI LT04. Backend vẫn kiểm lại READY/deadline/scope. |
| `ProctorSessionView` | FileChooser chọn file, file/HTTP trên worker; start/reorg có confirmation; timeline freshness dừng khi close; form trong ScrollPane. | GUI thao tác nút qua scroll; confirmation tự động có nhãn SIMULATED. |
| `ProctorProgressController` | Owner-thread, một request; lỗi giữ rows/cursor/readAt committed và stale; cursor chỉ tiến sau ACK đọc hợp lệ;401 gọi expired; close bỏ callbacks. | Unit deterministic MOCK và PG progress thật; không snapshot xuyên trang. |
| `ProctorProgressView` | Poll3giây/bounded queued FX callback; render FX; không đọc local answers; close timer/controller, API do dashboard sở hữu. | GUI confirmed, renderer stale503 fixture MOCK; không gọi MOCK là backend PASS. |
| `ProctorDashboardView` | DashboardController/MonitoringApiClient riêng luồng I/O; một tab B4 và một progress; close worker/API/subscription; UNKNOWN nói mất xác nhận liên lạc. | FULL/roster/proctor GUI; ảnh process/PID giữ riêng. |
| `ListeningPlayback` | Validate scope/writer/manifest/checksum; journal CREATE_NEW trước Media; start trùng không replay; reconnect/status chỉ reconcile; interruption khóa và báo server; close generation/worker. | WAV Media/HTTP/WS thật; nghe tự động SIMULATED; marker không bị xóa để nghe lại. |
| `ListeningPlaybackPane` | Media callbacks kiểm player/generation/phase; network loss dispose trên FX; render queued; close MediaPlayer và subscriptions. | EXE/GUI lifecycle; không hứa dừng trước khi hệ thống phát hiện mất mạng. |
| `AudioPreparationView` | Gắn shared writer, không acquire lần hai trong Application; checksum/test loa/READY do coordinator nền; sample player dispose/timeout; trạng thái đóng có guard. | Thiết bị nghe thật chưa nghiệm thu; speaker confirmation SIMULATED. |
| `ExamApiClient` | HTTP async, Bearer header, no redirect; raw ID encode; query-status cho slash/dot; progress validate ordering/row counts/cursor trước publish; API đóng worker/client. | PG raw Unicode/slash/foreign scope và unit malformed; không nới WS ID. |
| `ClientSettings` | Property > env > file4KiB > default; URL origin không credential/query; read file ngoài FX; sửa BOM đầu file, không ghi file người dùng. | Regression BEFORE FAIL/AFTER PASS; localhost chỉ default, có cấu hình LAN ngoài artifact. |
| `RealtimeClient` | Socket/auth/generation mới trên reconnect; heartbeat/ACK correlation; bounded retry/fragment/ACK; old generation bỏ; candidate session FULL mặc định, opt-in DELTA vẫn false. | C1/C2 + unit transport; epoch monitoring khác writerEpoch. |

## Truy vết bổ sung A/C và contract

Đã đối chiếu `CLAUDE.md`, tracker/task B4/B5 và hợp đồng02 §2–4, testplan03, PROTOCOL, QUYET_DINH, docs unified/B4/progress/Windows/LAN/manual cùng conflict-resolution của PR39. Các call chain bổ sung: AudioPreparation → AudioFiles/AudioApiClient; Playback → ListeningApiClient/ListeningMessages/Journal; History → ExamResults/ExamHistoryGateway; DashboardController/MonitoringJson; CandidateMonitoringSession → ProcessCollector/FullSnapshotDelivery; server ExamService/ProctorProgressService/Controller và Listening lifecycle/reorg.

- Save/submit/timeout/takeover dùng khóa hàng attempt; writer kiểm sau khóa; deadline lấy DB `clock_timestamp()` sau khóa; cached submit retry vẫn kiểm chủ lượt trước trả. Commit failure được test với PostgreSQL thật; không đổi A production.
- Progress là SELECT committed/MVCC, WHERE assignment theo proctor, không lấy writer/answers/correct options; correctCount chỉ final. lastSavedAt là decisionAt của autosave đã commit, không phải timestamp COMMIT vật lý. Keyset dùng C/UTF-8 ordering, không hứa snapshot xuyên trang.
- QD29 vẫn **PROPOSED / NEEDS_OWNER_REVIEW**: cấp ACTIVE chỉ ID monitoring-v0, không normalize raw username. Legacy Unicode/slash đọc history/status; không cho ACTIVE không monitoring. A/C phải quyết trước merge; QA không ký thay owner.
- Reorganization cấp lượt/ca mới; old answers/deadline/revision/result được so trước/sau. Không đổi policy Listening, schema/migration, stack, FULL default hoặc E1 collector/harness/policy.
- Không có conflict Git mới trong branch QA: branch từ đúng PR39 HEAD; diff fixes chỉ phần B. Giữ nguyên quyết định nền B4#36/MSI#37, không thêm controller hoặc pipeline sản xuất thứ hai. Private QA harness không đóng gói vào MSI.

## Adversarial và kết luận

[ISSUES](ISSUES.md) ghi ba lỗi đã tái hiện: smoke title, URL BOM, cache TOCTOU/lifecycle. Lỗi cache xuất hiện ở full regression mới, sau đó được tái hiện bằng barrier + close/reopen stress; không tăng timeout để che lỗi. Các nhánh stale ACK/close/generation, request timeout/frozen retry, malformed response,401/403, raw cursor và takeover có unit/HTTP/PG/GUI subset, phân biệt MOCK/SIMULATED.

Kết luận kỹ thuật sau targeted: **FIXED_AND_RETESTED**; gate full regression/MSI theo [FINAL_REPORT](../FINAL_REPORT.md). Human/owner review và LAN vật lý vẫn chưa thực hiện. P0 chưa phát hiện trong phạm vi kiểm; điều này không chứng minh không còn lỗi.
