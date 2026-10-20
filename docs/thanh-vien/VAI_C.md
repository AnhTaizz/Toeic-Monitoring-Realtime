# Vai C — Monitoring, đồng bộ trạng thái, thực nghiệm

Người đảm nhận: _chưa gán_

## Tóm tắt vai

- **Bạn sở hữu:** collector quét process trên máy thí sinh, module monitoring phía server (từ chặng 2: nhận state và event, epoch/sequence, reducer), khung message chung, công cụ ghi và phát lại trace, harness thực nghiệm, định nghĩa phép đo và phân tích.
- **Bạn không sở hữu:** lớp kết nối mạng phía client (của B; collector gửi qua interface của B) và phần lưu trữ, xác thực phía server (của A; A cấp hook cho bạn).
- **B review code của bạn. Bạn review code của A.**
- Tổng: 82 giờ (72 giờ task + 10 giờ review).

Phần của bạn là nơi có đóng góp kỹ thuật (novelty) và thực nghiệm của cả nhóm, chiếm khoảng 30% rubric. Tính đúng đi trước hiệu suất.

Tài liệu phải đọc: `02_HOP_DONG.md` mục 3 (đọc kỹ "Quy tắc reducer state" và "Luồng lịch sử event"), `03_KIEM_THU_VA_THUC_NGHIEM.md` mục 3 và mục 5.

## Cách dùng file này

Mỗi task có phần hướng dẫn và dòng **Ghi chú làm dở**. Khi dừng giữa chừng, ghi vào đó bạn đang ở bước nào và cái gì chưa chạy. Trạng thái chính thức vẫn cập nhật ở `TRACKER.json`. Phần "Cách làm" là gợi ý; nếu chọn cách khác thì ghi vào `docs/QUYET_DINH.md`.

## Hai khái niệm phải phân biệt suốt dự án

| | Trạng thái (state) | Lịch sử (event) |
|---|---|---|
| Trả lời câu hỏi | Ngay lúc này máy thí sinh đang có process nào trong diện theo dõi? | Đã từng quan sát thấy những gì? |
| Truyền bằng | Full snapshot hoặc delta | Message event có `eventId` |
| Khi mất dữ liệu | Gửi full snapshot mới là phục hồi được | Không phục hồi được; phải ghi nhận khoảng trống |
| Chống trùng bằng | `syncEpoch` + `sequence` + `messageId` | `eventId` |

Snapshot mới không được xóa hay viết lại lịch sử event và các khoảng trống đã ghi.

## Contract bạn phải giao và nhận

| Hạn | Bạn giao | Cho ai |
|---|---|---|
| 03/10, **giờ đầu** | Khung message v0: login (cùng A), event, ACK, heartbeat, mã lỗi, traceId | A, B |
| 16/10, khóa 18/10 | Monitoring epoch/baseSequence/sequence, state và event tách biệt, định nghĩa metric | A, B |
| Trước 24/10 | Harness benchmark polling | B |

| Hạn | Bạn nhận | Từ ai |
|---|---|---|
| 04/10 | Interface collector → adapter mạng | B |
| 09/10 | Schema attempt, mã lỗi | A |
| Trước T3-C2 | Hook lưu trữ epoch và kiểm quyền | A |

Ghi mọi message vào `docs/PROTOCOL.md`. A và B chờ khung message của bạn ngay giờ đầu ngày 03/10, nên việc đó làm trước tiên.

---

## Chặng 1 (03–08/10) — 17 giờ

Lịch gợi ý: 03/10 C1 · 04/10 C2 · 05/10 C3 (3h) · 06/10 C3 (1h) + C4 (1h) · 07–08/10 C4.

### T1-C1 · Chốt message v0 và khảo sát ProcessHandle
`4h` · phụ thuộc: không · review: B

**Đầu ra:** mẫu message v0 trong `docs/PROTOCOL.md`; ghi chú về những gì `ProcessHandle` đọc được trên máy đích.

**Cách làm:**
1. **Giờ đầu:** viết mẫu JSON cho login (cùng A), event, ACK, heartbeat và định dạng lỗi. Khung chung theo hợp đồng mục 1: `protocolVersion`, `type`, `messageId`/`requestId`, `attemptId`, correlation ID, payload. Ghi nhãn `MOCK`. Gửi cho A và B rồi mới làm tiếp.
2. Viết một chương trình nhỏ gọi `ProcessHandle.allProcesses()` và in ra với mỗi process: PID, `info().command()`, `info().startInstant()`, `info().user()`.
3. Chạy trên máy Windows sẽ dùng để demo, với tài khoản người dùng thường. Ghi lại: trường nào trống, với loại process nào (process hệ thống, process của người dùng khác).
4. Chọn một ứng dụng đọc được tên rõ ràng để làm ứng dụng demo, và soạn danh sách hạn chế v1 (QD-08).

**Dễ sai:**
- Trên Windows, một số trường (đặc biệt là tham số dòng lệnh, và thông tin của process không thuộc quyền mình) thường trống. Mục đích của task là biết chính xác trống ở đâu trên máy đích.
- Process không đọc được metadata phải được ghi là "không đọc được", không được coi là "sạch".

**Xong khi:** A và B đã nhận mẫu message; bạn nêu được tên một ứng dụng thật sự quan sát được và danh sách trường bị thiếu.

**Ghi chú làm dở:** Đã có contract v0 (message WS ghi rõ MOCK), probe thật và policy v1; chờ B review. Trên 200 process đầu có 133 process thiếu cả command/start/user.

### T1-C2 · Collector polling chạy ngoài luồng giao diện
`4h` · phụ thuộc: T1-C1 · review: B

**Đầu ra:** một lớp collector có `start`/`stop`, quét định kỳ, trả ra tập process thuộc diện theo dõi.

**Cách làm:**
1. Chạy trên executor riêng. Dùng `scheduleWithFixedDelay` để lần quét sau chỉ bắt đầu khi lần trước xong; như vậy các lần quét không chồng lên nhau.
2. Chu kỳ mặc định 1.000 ms, là tham số cấu hình (thực nghiệm E1 sẽ đổi giá trị này).
3. Lọc theo danh sách hạn chế (policy). Policy có `policyVersion`.
4. Khóa định danh một process: `collectorSessionId` + PID + thời điểm khởi động (nếu đọc được). Thiếu thời điểm khởi động thì đánh dấu chất lượng dữ liệu.
5. `collectorSessionId` sinh mới mỗi lần collector khởi động.
6. Collector chỉ được bật trong phiên thí sinh; role giám thị không bao giờ khởi động nó.

**Dễ sai:**
- PID được hệ điều hành tái sử dụng; chỉ dùng PID làm khóa sẽ nhầm hai process khác nhau.
- `stop` phải dừng hẳn executor, nếu không ứng dụng không thoát được.
- Ghi thời điểm quan sát bằng đồng hồ đơn điệu (`System.nanoTime`) cho phép đo; không dùng giờ máy client làm bằng chứng so với máy khác.

**Xong khi:** mở và đóng ứng dụng demo thì tập process thay đổi tương ứng; đăng nhập bằng giám thị thì collector không chạy; phần đầu của MT01.

**Ghi chú làm dở:** 04/10, T1-C2 code-complete trên `feat/t1-c2-process-collector`, base main PR #3 `9f01861`, source `02789a8`. ProcessHandleSnapshotSource chỉ giữ filename/start/user-availability, QD-08 v1 đúng 8 tên, UNREADABLE diagnostics, immutable snapshots và identity(session,pid,start nullable); nanoTime local. Fixed-delay default1s configurable, worker riêng, start/stop/restart UUID mới, stop future chờ worker kết thúc và ngăn overlap xuyên restart. Gate chỉ active candidate; chưa có production monitoring trigger nên không auto-start login. Mvn test/package PASS 175/175 (33 mới), Windows source/owned headless Edge open-close/cleanup PASS; GUI manual/review B NOT RUN, MT01 PARTIAL local. Smoke đầu FAIL trước sửa validation PID0 idle; sau sửa/1 regression test smoke PASS, không dùng lượt lỗi làm PASS. Không eventId/transport.send/queue/ACK/persistence/full/delta, không sửa C3/A3 hoặc tracker. Evidence `evidence/t1-c2/2026-10-04-verification.md`; Git/PR/merge cuối xem report.

### T1-C3 · Event, retry và queue có giới hạn
`4h` · phụ thuộc: T1-C2, T1-B2 (interface), T1-A3 · review: B

**Đầu ra:** collector sinh event và gửi tới server thật qua adapter của B.

**Cách làm:**
1. So sánh tập process của lần quét này với lần trước. Process mới xuất hiện trong diện theo dõi sinh **một** event. Process vẫn còn chạy ở lần quét sau không sinh thêm.
2. `eventId` tạo một lần lúc quan sát và lưu cùng event. Retry gửi lại đúng event đó với đúng `eventId`.
3. Hàng đợi trong RAM có giới hạn (khởi đầu 500). Khi đầy: tăng `droppedCount`, ghi khoảng thời gian bị mất, và gửi thông báo khoảng trống khi kết nối lại được.
4. Retry có giới hạn số lần và backoff. Chỉ bỏ event khỏi hàng đợi khi nhận ACK.
5. Gửi qua interface của B tới server thật của A.

**Dễ sai:**
- Tạo `eventId` mới mỗi lần gửi làm server lưu trùng.
- In ra console không tính là đã tích hợp.
- Process xuất hiện rồi biến mất giữa hai lần quét sẽ không được thấy. Đó là giới hạn của polling và là nội dung của thực nghiệm E1; không che giấu nó.

**Xong khi:** mở ứng dụng demo → đúng một event trong DB và một cảnh báo trên màn giám thị; ngắt mạng rồi nối lại → event được gửi lại không trùng; ép hàng đợi đầy → có `droppedCount`.

**Ghi chú làm dở:** 04/10/2026 T1-C3 code-complete trên feat/t1-c3-monitoring-event-delivery, base74d2704. CandidateMonitoringSession explicit start/stop + fresh server scope; MonitoringMessage immutable, MonitoringDelivery bounded RAM500/in-flight4/ACK5s/max5/backoff1..8s. Strict identity(session,pid,start nullable), scan failure giữ baseline; overflow giữcũ/dropmới + frozen gap và accumulator kế tiếp. B2 adapter hỗ trợ gap/forget correlation/heartbeat expiry; server V3 + transactional MonitoringGapService commit-before-ACK/unique/conflict/scope. Test/package253/253 (53mới), real WindowsC2/B2/Spring/PostgreSQL delivery/reconnect/timeline/overflow/cleanupPASS; ACKlossSIMULATED, overflow snapshotMOCK. HumanBreview/GUI/LAN NOTRUN, MT01PARTIAL tớiB3. Evidence evidence/t1-c3/2026-10-04-verification.md. Git/PR/merge cuối xem report. TRACKER giữ nguyên; không triển khai C4/A4/B3/chặng2.

**Bổ sung handoff A4, 04/10:** CandidateMonitoringSession gắn heartbeat lease vào đúng UUID trả từ collector.start, gỡ khi Stop/logout/switch/mất quyền; RealtimeClient giữ binding qua socket reconnect. Collector C2 và event/gap C3 không đổi contract. Server V4 theo dõi HEARTBEAT_TIMEOUT riêng monitoring_interruptions, không nhận reason đó qua MONITORING_GAP. Human C review phần A4 NOT RUN; T2-C2 event muộn/state/gap khác vẫn chưa làm. Xem PROTOCOL T1-A4 và evidence/t1-a4/2026-10-04-verification.md.

### T1-C4 · Log đo và ghi chú lựa chọn phương pháp monitoring
`3h` · phụ thuộc: T1-C1, T1-C3 · review: B

**Đầu ra:** schema log dùng cho thực nghiệm về sau; bảng khảo sát phương pháp.

**Cách làm:**
1. Định nghĩa schema log: thời điểm (ghi rõ thuộc đồng hồ nào), loại message, hướng (gửi/nhận), kích thước, `traceId`, `sequence`/`eventId`.
2. Bộ đếm byte hai chiều: theo định nghĩa task C4, đếm UTF-8 của toàn bộ JSON đã serialize (envelope + payload), theo type/hướng/outcome. Đây là byte tầng ứng dụng, không phải băng thông TCP/IP.
3. Bảng khảo sát ít nhất 2 cách thu thập thông tin process (ví dụ polling bằng `ProcessHandle` so với cơ chế thông báo sự kiện của hệ điều hành), dẫn nguồn từ tài liệu chính thức. Ghi rõ cách nào nhóm đã chạy, cách nào chỉ khảo sát trên tài liệu.

**Dễ sai:** điền số đo cho phương pháp chưa chạy. Bảng khảo sát là nguồn cho phần Related Work; mọi nguồn phải trích dẫn được.

**Xong khi:** log của một phiên demo đọc được theo schema; bảng khảo sát có nguồn.

**Ghi chú bàn giao 05/10/2026:** C4 có recorder opt-in `monitoring-measurement-v1`, counters thread-safe theo type/TX/RX/outcome, bounded queue/drop-new/flush/failure isolation; client ghép Unicode rồi đo, server đo delegate thật bên trong concurrent send decorator. Summary JSON/CSV validate raw/counter, phát hiện incomplete; ProcessHandle polling/WMI survey có nguồn Oracle/Microsoft. Xem [định nghĩa/cấu hình](../MONITORING_MEASUREMENTS.md), [khảo sát](../PROCESS_MONITORING_SURVEY.md), [evidence](../../evidence/t1-c4/2026-10-05-verification.md). Cross-owner A/B: handler/registry/RealtimeClient; không đổi wire/auth/scope/retry/presence/dashboard. Human B review, GUI/LAN, WMI/ETW và E1/E2 NOT RUN; không triển khai full/delta/B4, TRACKER giữ nguyên. Kết quả và SHA build thật được chốt trong evidence.

---

## Chặng 2 (09–15/10) — 20 giờ

### T2-C1 · Full snapshot baseline v1
`4h` · phụ thuộc: T1-C3, T1-A4 · review: B

**Đầu ra:** chế độ full snapshot chạy thật; module monitoring phía server do bạn viết.

**Cách làm:**
1. Phía server: tạo module monitoring riêng. Tiếp quản phần xử lý event A viết ở T1-A3; A giữ phần lưu trữ và kiểm quyền làm hook.
2. Khi kết nối monitoring mở (hoặc mở lại), server cấp `syncEpoch` mới. Client phải gửi full snapshot đầu tiên (ví dụ `sequence = 1`). Chưa có full hợp lệ thì trạng thái là UNSYNCED.
3. Mỗi lần quét gửi một full snapshot với `sequence` tăng dần, kèm `messageId`.
4. Server kiểm tra quyền, epoch đang hoạt động và `sequence` không lùi, rồi thay state; ACK sau khi state được chấp nhận.
5. Snapshot chỉ chứa process thuộc diện theo dõi và quan sát được. Chế độ delta về sau phải dùng đúng phạm vi này.

**Dễ sai:** để baseline gửi mọi process trong máy còn delta chỉ gửi process đã lọc. Như thế so sánh ở E2 không công bằng. Luồng state không được ghi hay xóa lịch sử event.

**Xong khi:** màn giám thị thấy state hiện tại của thí sinh; message của epoch cũ bị từ chối; MT01 chạy được.

**Ghi chú bàn giao:** 10/10/2026 — PR#18 đã merge bằng merge commit683adfc, nhánh `feat/t2-c1-monitoring-full-snapshot` giữ lại. Code full6acd031, review-fix e07bb7e dùng worker riêng, không bật scheduler toàn server/ExamTimeoutService. Java413 và REAL autonomous STALE/TTL/capacity/shutdown/full Edge/GUI component PASS; C2 chạy lại C1 hồi quy PASS. MT01 PARTIAL; GUI candidate/LAN/human A/B review tổng thể NOT RUN. [Evidence gốc](../../evidence/t2-c1/2026-10-10-verification.md), [bổ sung](../../evidence/t2-c1/scheduling-fix/2026-10-10-verification.md).

### T2-C2 · Heartbeat, event muộn và khoảng trống
`4h` · phụ thuộc: T2-C1 · review: B

**Cách làm:**
1. Kill client: server chuyển UNKNOWN sau timeout, lưu `lastSeenAt` và `timeoutDetectedAt` (MT08). Giám thị thấy "không còn xác nhận được monitoring".
2. Event đến muộn sau reconnect: nếu hợp lệ và thuộc quyền thì thêm vào lịch sử với nhãn đến muộn; trùng thì bỏ. Event muộn không được dùng để sửa state hiện tại.
3. Khoảng trống (do rớt mạng hoặc hàng đợi tràn) lưu thành bản ghi riêng. Snapshot mới sau đó phục hồi state nhưng bản ghi khoảng trống vẫn còn (MT07).

**Dễ sai:** khi client quay lại ONLINE thì xóa dấu vết gián đoạn. Server không biết chính xác monitoring dừng lúc nào, chỉ biết lúc mình phát hiện.

**Xong khi:** MT01, MT07, MT08 có bằng chứng.

**Ghi chú bàn giao 10/10/2026:** Nhánh `feat/t2-c2-monitoring-late-events-and-gaps`, base683adfc, code fdac531. Reuse heartbeat/presence A4, full worker C1 và gap C3; thêm EventOrigin frozen lúc observe, deliveryStatus lần INSERT đầu, V8 additive, HTTP đọc gap/tab giám thị và generation guard gửi event cũ. Java425/package/Python10, C2 REAL Spring/PG/HTTP/WS/collector worker/proctor Stage PASS; readings MOCK/outage/write/ACK loss SIMULATED. A3/A4/C3/C4/B3/C1 mới PASS, gồm hard-kill JVM và ProcessHandle/Edge thật, tự STALE/TTL/shutdown. [Evidence](../../evidence/t2-c2/2026-10-10-verification.md), [demo](../../README.md#heartbeat-event-muộn-và-gap-t2-c2), [contract](../PROTOCOL.md#t2-c2--heartbeat-event-đến-muộn-và-khoảng-trống), QD-13. MT01/MT07/MT08 PARTIAL cấp toàn hệ thống; candidate GUI/LAN/human A/B review NOT RUN. A cần xem V8/transaction/ACK/permission, B xem RealtimeClient/parser/controller/model/view. Event trước ACK đầu/legacy UNSPECIFIED; LIVE chỉ cùng socket, không đo delay; origin không phải chứng cứ gian lận. Không sửa tracker/.env/nguồn kế hoạch/migration cũ, không reset DB; dừng sau C2, không C3–C5/delta/E1/E2.

### T2-C3 · Ghi/phát lại trace và oracle trạng thái
`4h` · phụ thuộc: T2-C1 · review: B

**Đầu ra:** công cụ chạy không cần giao diện, là nền cho mọi kiểm tra tính đúng của delta.

**Cách làm:**
1. Recorder: ghi chuỗi quan sát của collector ra file (mỗi dòng một lần quét), kèm checksum.
2. Replayer: đọc trace, đưa qua bộ mã hóa (full, sau này là delta) rồi qua reducer; không cần mạng hay desktop.
3. Oracle: với cùng trace, tính state mong đợi ở từng bước dưới dạng chuẩn hóa (tập khóa process đã sắp xếp) và tập event mong đợi (theo `eventId` và nội dung, không theo thứ tự đến).
4. Bộ gây lỗi: lặp lại message, bỏ một message, đảo thứ tự, chèn reconnect tại thời điểm định trước, theo seed.
5. Bắt đầu với một trace nhỏ viết tay mà bạn biết trước kết quả.

**Dễ sai:** dùng chính reducer đang cần kiểm tra để sinh kết quả mong đợi. Oracle phải tính độc lập từ chuỗi quan sát. Ghi rõ đây là phát lại, không phải desktop thật.

**Xong khi:** phát lại trace nhỏ cho đúng kết quả đã biết; bật gây lỗi thì kết quả mong đợi là UNSYNCED/UNKNOWN đúng chỗ.

**Ghi chú làm dở:** Code/test/CLI/evidence hoàn tất10/10, commit5844e88. [Cách chạy/schema](../MONITORING_TRACE.md), [evidence](../../evidence/t2-c3/2026-10-10-verification.md). Java461/Python10, real ProcessHandle/owned Edge trace→replay và C1/C2/event/C4/A4 hồi quy PASS. Oracle từ quan sát gốc, fixture expected viết tay, seed tái tạo byte-identical; full lỗi cố ý bị phát hiện. Human B review NOT RUN, A cần xem reducer adapter. UNKNOWN thuộc presence không mô phỏng ở CLI; không gán từ drop full. Candidate GUI/LAN/delta/T2-C4/E1/E2 chưa làm. Dừng sau C3; Git cuối xem báo cáo/PR.

### T2-C4 · Harness phát process thử nghiệm
`4h` · phụ thuộc: T2-C3 · review: B

**Đầu ra:** công cụ khởi chạy process có kiểm soát để làm ground truth cho E1.

**Cách làm:**
1. Viết một chương trình con nhỏ có tên nhận diện được, sống trong thời gian chỉ định rồi thoát; có thể in tín hiệu "ready" khi đã khởi động xong.
2. Harness khởi chạy nó bằng `ProcessBuilder` theo lịch: ba độ dài sống mục tiêu 200 ms, 800 ms, 3.000 ms; ít nhất 10 lần mỗi độ dài trong một lần đo; thời điểm bắt đầu lệch pha so với chu kỳ quét theo seed.
3. Ghi thời điểm khởi chạy và thoát bằng `System.nanoTime`, cùng máy và cùng đồng hồ với collector. Lưu thời gian sống **thực đo**, không giả định bằng đúng giá trị yêu cầu.
4. Ghi cả các process không đọc đủ metadata.

**Dễ sai:** lấy chính collector làm ground truth cho collector. Ground truth phải đến từ harness.

**Xong khi:** một lần chạy thử cho ra file ground truth và file quan sát của collector, ghép được với nhau.

**Ghi chú làm dở:** Code0164297/test/evidence hoàn tất10/10 từ C3 merge PR#20/f6d46b2. Java493/package PASS; Windows30child REAL đủ10 mỗi200/800/3000, poll500/seed1234/TEST:27 OBSERVED/3 NOT_OBSERVED_IN_TRACE/0 INCONCLUSIVE; checksum/rejoin/cleanup PASS. [Cách chạy/clock/identity](../CONTROLLED_PROCESS_HARNESS.md), [evidence](../../evidence/t2-c4/2026-10-10-verification.md). C1-Gui/C3-replay/measurement và event chạy riêng hồi quy PASS; lần event song song FAIL retained/cause chưa chứng minh. Human B review NOT RUN, cần xem policy seam/trace TEST/clock binding/coverage/lifecycle. OS Ctrl+C/hardkill parent, candidate full GUI/LAN/E1/E2 NOT RUN; không latency/miss-rate hoặc tên production mới. Giữ nhánh C4 theo yêu cầu, Git cuối trong báo cáo/PR. Dừng sau C4, chưa C5/delta.

### T2-C5 · Ghi phương pháp baseline và protocol
`2h` · phụ thuộc: T2-C2, T2-C4 · review: B

Viết ra: `policyVersion`, khóa process, phạm vi đo, giới hạn về quyền và PID tái sử dụng; định nghĩa "bỏ sót" và "độ trễ phát hiện"; đầu vào của benchmark. Đưa cho B review trước khi chạy đo. Cập nhật `docs/PROTOCOL.md`.

**Ghi chú bàn giao 11/10/2026:** Tài liệu/đối chiếu hoàn tất, commit phương pháp eb6ba59 từ main82557bf (C4 PR#21 đã merge). [Phương pháp baseline](../MONITORING_BASELINE_METHOD.md), [kiểm tra mới C5](../../evidence/t2-c5/2026-10-11-verification.md). Policy/identity/ACK RAM vs commit/event/state/presence/gap/byte/clock đã chốt theo source; 200ms hold không là đời process200ms. Rate có eligibility và nhóm thực đo đề xuất; latency kernel/CPU/memory NOT MEASURED. Thiếu window/warmup/resource sampler/analyzer E1, delta/oracle/MT02–MT06 trước E2. Human B review NOT RUN, checklist chờ người thật trước đo; không giả đã duyệt. C5 chỉ kiểm tài liệu/source/evidence, không chạy lại Java493/Python10/smoke hoặc benchmark. TRACKER/old artifact/QUYET_DINH giữ nguyên. Git cuối trong PR/final; dừng sau C5, không tự chặng3/delta/E1/E2.

---

## Chặng 3 (16–22/10) — 20 giờ

**Gate 20/10** do bạn chủ trì, B duyệt: MT02–MT06 phải qua. Nếu không qua, tắt delta ở cấu hình mặc định, giữ full snapshot, lưu trace lỗi; không kéo gate sang tuần sau.

### T3-C1 · Bộ mã hóa delta phía collector
`4h` · phụ thuộc: T2-C1, T2-C3 · review: B

**Cách làm:**
1. Đầu mỗi epoch gửi full snapshot và chờ ACK rồi mới gửi delta.
2. Delta gồm phần thêm và phần bớt so với state gốc, kèm `baseSequence` (state gốc) và `sequence` (= gốc + 1).
3. Tính delta từ đúng state gốc mà bạn ghi nhận; nếu không chắc server đang ở đâu (mất ACK lâu, reconnect) thì gửi full.
4. Định kỳ gửi một full snapshot đối soát (khởi đầu 30 giây).
5. Lần quét không có thay đổi có thể không gửi state; heartbeat vẫn gửi như ở baseline.
6. Dùng đúng policy, phạm vi process và quy tắc sinh event của baseline.

**Xong khi:** phát lại trace nhỏ qua bộ mã hóa, áp tuần tự các delta cho ra đúng state của oracle.

**Ghi chú làm dở:** —

**Ghi chú bàn giao11/10/2026:** T3-C1 encoder candidate/replay có từ main12e2838, nhánh feat/t3-c1-monitoring-delta-encoder. [Hướng dẫn](../MONITORING_DELTA_ENCODER.md), [evidence](../../evidence/t3-c1/2026-10-11-verification.md), QD-16. Reuse lifecycle FullSnapshotDelivery/event C3; ACKed canonical base, coalescing1pending snapshot, metadata fallback full, retry frozen, reconnect/fresh scan/full1 và full30s monotonic. Replay full/delta cùng raw oracle, handwritten fixture/negative mutation, event IDs/content giữ nguyên; delta adapter SIMULATED chưa server T3-C2. Production full default/capability false; STALE6s vs full30s cần A/C freshness contract trước bật. Human B/A review, live delta/LAN/gate/E1/E2 NOT RUN; không tự chuyển C2 hoặc thay tracker.

### T3-C2 · Reducer delta phía server
`4h` · phụ thuộc: T3-C1, T3-A3 (hook) · review: B

**Cách làm — cài đúng 6 quy tắc trong hợp đồng mục 3:**
1. Kiểm tra quyền và epoch đang hoạt động trước khi áp dụng.
2. Delta chỉ hợp lệ khi `baseSequence == currentSequence` **và** `sequence == currentSequence + 1`.
3. Message đã áp dụng (cùng ID, sequence, nội dung) thì ACK lại, không áp lần hai. Cùng sequence hoặc ID nhưng khác nội dung là lỗi protocol.
4. Thiếu chuỗi hoặc đảo thứ tự: đánh dấu UNSYNCED, giữ state cuối với nhãn "cũ", yêu cầu full. Không suy đoán phần thiếu.
5. Full snapshot có sequence cao hơn trong epoch hợp lệ thay thế state; sequence không bao giờ lùi.
6. Reconnect: cấp epoch mới, từ chối mọi message của epoch cũ.

Việc chuyển epoch (cấp epoch mới, nhận full mới, vô hiệu epoch cũ) phải được tuần tự hóa theo từng attempt/collector, ví dụ bằng một khóa cho mỗi attempt.

**Dễ sai:** viết reducer dính chặt vào WebSocket khiến không test thuần được. Tách hàm reducer nhận (state hiện tại, message) và trả (state mới, kết quả) để replayer gọi trực tiếp.

**Xong khi:** test thuần cho từng quy tắc qua.

**Bàn giao12/10/2026:** T3-C2 implementation/test/replay/live harness có, branch feat/t3-c2-monitoring-delta-reducer từ main144fe55. Core1c3f982, oracle4042b1c; Java554/Python10/CLI/REAL Spring-PG-WS-resync-restart và full/event/presence/dashboard/measurement regression PASS. Readings delta MOCK, injected future base SIMULATED; worker/network thật. [Guide](../MONITORING_DELTA_REDUCER.md), [evidence](../../evidence/t3-c2/2026-10-12-verification.md), QD17. Full default, capability/flag experimental; freshness6s giữ, empty delta2s/fresh observation và full30s không dùng latest cũ. A/B review NOT RUN, T3-A3 không đánh dấu DONE. Không gate/C3/E1/E2 hoặc sửa tracker/exam/.env/migrations/PR17-23; dừng C2. Git cuối PR/final.

### T3-C3 · Phát lại kiểm tra tính đúng và lỗi
`4h` · phụ thuộc: T3-C2, T2-C3 · review: B

Dùng replayer chạy ba loại trace (ít thay đổi, nhiều thay đổi, có reconnect) với các lỗi: trùng, thiếu, đảo thứ tự, reconnect. Ở mỗi phiên bản so sánh được, full và delta phải cho cùng state chuẩn hóa và cùng tập event. Khi có khoảng trống, so với trạng thái UNSYNCED/UNKNOWN mong đợi.

**Xong khi:** MT02–MT06 có bằng chứng, mỗi loại trace có file lưu lại.

**Bàn giao12/10/2026:** C3 code dc87fcc/runtime d813cb1 từ main6a41031;569tests/72replay cases/4negative/repeat/CLI ProcessHandle-owned Edge PASS. [Guide](../MONITORING_DELTA_FAULT_REPLAY.md), [evidence](../../evidence/t3-c3/2026-10-12-verification.md), QD18. Three MOCK traces/expected manual, production C2 reducer, raw oracle independent, ID/content/checkpoints/recovery/missing events và fault schedules lưu. ACK baseline enumeration, resync recorded/no feedback; NOT MODELED ACK-loss/retry/presence/TTL/DB. Event replay late receiving connection sửa PREVIOUS_CONNECTION, counterexample old/final giữ. Full default, MT02–MT06 PARTIAL toàn gate; B human review/live C4/LAN/GUI/E1/E2 NOT RUN. Không sửa tracker/exam/.env/migration hoặc tự C4/gate; Git cuối PR/final.

### T3-C4 · Tích hợp delta thật rồi quyết định giữ hay cắt
`4h` · phụ thuộc: T3-C3, T3-A3 · review: B

Chạy thật trên hai desktop: epoch cũ, mất ACK, khởi động lại client, khởi động lại server. Ngày 20/10 chốt gate cùng B và ghi vào `docs/QUYET_DINH.md`; QD-19 đang ghi bàn giao chưa chốt, QD-10 đã dùng cho presence nên không ghi đè.

- **Qua:** delta bật được bằng cấu hình; full vẫn là đường chạy dự phòng.
- **Không qua:** delta tắt mặc định; dùng giờ còn lại để ổn định full snapshot và lưu trace lỗi kèm kết quả mong đợi/thực tế.

**Bàn giao13/10/2026:** Sourced9475eb,579Java PASS, candidate factory production DELTA opt-in/capability socket, FULL default. [Guide](../MONITORING_DELTA_LIVE_GATE.md), [evidence](../../evidence/t3-c4/2026-10-13-verification.md). LOCAL preparation trên một Windows host/hai candidate JVM/proctor Application, REAL ProcessHandle/Edge/network/PG; fault seams SIMULATED. Gate20/10 chưa diễn ra, LAN hai máy/human B review NOT RUN; QD19 liên kết QD10 presence đang có. A xem auth/binding/scoped heartbeat rejection/maintenance; B xem factory/callback-generation/mode/UX và LAN. Không tự đánh dấu A3 DONE hoặc C4 nghiệm thu toàn phần; không C5/E1/E2. Git cuối trong PR/final, giữ nhánh.

**Ghi chú làm dở PR47, 18/10/2026:** đã retarget main/fix source `3d93820`, focused148 và clean/package928 PASS/5SKIP; T3-C2 restart/resync PASS. C cần review FullSnapshotDelivery (deadline CLOSE, guard, worker/correlation cleanup) và phối hợp B xem RealtimeClient (cache hủy/ACK-ERROR-RESYNC muộn). T3-C4 lượt mới 42/42 PASS LOCAL; không merge khi thiếu C approval, không thay nghiệm thu LAN hoặc quyết định gate. [Hồ sơ](../../evidence/pr47-stabilize-20261018/REPORT.md).

### T3-C5 · Bản nháp báo cáo đồng bộ và giới hạn
`2h` · phụ thuộc: T3-C4 · review: B

Viết: state và event tách biệt ra sao, khoảng trống không phục hồi được, full là phương án dự phòng. Nếu gate không qua, ghi rõ delta là thử nghiệm.

**Bàn giao 13/10/2026:** [Bản nháp đồng bộ và giới hạn](../MONITORING_SYNC_REPORT_DRAFT.md), đối chiếu main `f97c081`; [kiểm tra tài liệu](../../evidence/t3-c5/2026-10-13-verification.md). Có ví dụ FULL/DELTA/retry/RESYNC, tách state/event/presence/gap và bằng chứng C1–C4 đúng SHA. Không chạy lại runtime, không đổi tracker/kế hoạch/QD hoặc bật delta.

**Ghi chú làm dở:** B review bản nháp NOT RUN; C4 gate NOT_DECIDED/PARTIAL, còn LAN hai máy và review A/B. Merge tài liệu không chốt gate; dừng sau C5, chưa E1/E2/chặng 4.

**Cập nhật 18/10 (agent):** thêm mục 9 vào bản nháp (thay đổi sau 13/10). PR #47 của C chưa vào main; ghép thử lên main `f864034`: 927 PASS/0 FAIL/4 SKIP, live gate 42/42. Việc của C: đổi base PR #47 về main, quyết gate sau LAN + B review. [kiểm tra 18/10](../evidence/t3-c5/2026-10-18-update.md)

---

## Chặng 4 (23–27/10) — 14 giờ

### T4-C1 · Chốt bộ dữ liệu và kiểm tra phép đo
`4h` · phụ thuộc: T2-C4, T3-C4 · review: B

**Cách làm:**
1. Chốt các trace và checksum của chúng; chốt seed, thời gian khởi động (15 giây), thời gian đo (120 giây), lịch full đối soát.
2. Kiểm tra bộ đếm byte bằng một trường hợp nhỏ tính tay được.
3. Chốt công thức CPU: `ΔCPU_time / Δwall_time × 100%` là phần trăm của một lõi; nếu chia thêm số lõi thì báo thành số riêng. Ghi rõ đo bộ nhớ loại nào (heap JVM hay RSS).
4. Chuẩn bị mẫu `run-metadata.json` và các file đầu ra theo `03_KIEM_THU_VA_THUC_NGHIEM.md` mục 6.
5. **Giao harness polling cho B trước 24/10**, kèm hướng dẫn chạy.

**Ghi chú làm dở 16/10/2026:** Review E1 có sẵn PR40, sửa CPU/rejoin/analyzer tại `c2cc312`; 87Java/11Python và Windows smoke/rejoin PASS. [Guide T4-C1](../E1_T4C1_MEASUREMENT.md), [report](../../evidence/t4-c1/2026-10-16-review.md). Chuẩn bị bàn giao, nghiệm thu PARTIAL: raw12run c1601b5 chưa có để phân tích lại resource; B human review/LAN/gate T3-C4 và trace120s E2 còn mở. Không E2/T4-C2/C3, không tracker hoặc merge PR nguồn.

### T4-C2 · Chạy full-vs-delta (E2)
`4h` · phụ thuộc: T4-C1 · review: B

**Nếu gate 20/10 qua:** 2 chế độ × 3 trace × 3 lần = 18 lần chạy. Trước khi đo, phát lại kiểm tra oracle; có sai lệch không giải thích được thì loại delta khỏi kết luận hiệu suất. Đảo thứ tự full/delta giữa các lần lặp. Thêm ít nhất một cặp chạy thật trên desktop.

**Nếu gate không qua:** đo full snapshot với kịch bản reconnect, và lập bảng các lỗi của delta. Không so hiệu suất của một bản delta sai với baseline.

Hai chế độ phải giống nhau ở mọi thứ trừ cách truyền state: cùng lọc, cùng chống trùng event, cùng ACK/heartbeat/retry/resync.

**Ghi chú làm dở:** —

### T4-C3 · Phân tích monitoring và đóng góp
`4h` · phụ thuộc: T4-C2, T4-B2 · review: B

**Cách làm:**
1. Script đọc raw và sinh `summary.csv` cùng biểu đồ. Không sửa số bằng tay.
2. E2: báo riêng byte luồng state, byte điều khiển (ACK, resync) và **tổng byte ứng dụng hai chiều** gồm cả event, heartbeat, retry. Nếu delta chỉ giảm byte state mà tổng gần như không đổi thì viết đúng như vậy.
3. E1: tỷ lệ bỏ sót theo từng độ dài sống, độ trễ phát hiện p50/p95 (chỉ tính trên event phát hiện được; event bỏ sót không có độ trễ), CPU và bộ nhớ theo chu kỳ.
4. Bảng Contribution – Description – Evidence, mỗi dòng trỏ tới test, log hoặc biểu đồ.

**Dễ sai:** 3 lần lặp là thí nghiệm nhỏ; không kết luận tổng quát. Kết quả âm vẫn là kết quả. Mức đủ của novelty do thầy đánh giá.

**Ghi chú làm dở:** —

---

## Chặng 5 (28–31/10) — 11 giờ

### T5-C1 · Tái lập một lần chạy và kiểm tra bằng chứng
`3h` · phụ thuộc: T4-C3 · review: B

Từ lệnh và seed đã lưu, chạy lại một trace. Số hiệu năng không cần trùng tuyệt đối nhưng định nghĩa metric và kết quả tính đúng phải khớp. Khóa file raw. Lần chạy bị loại phải ghi lý do.

### T5-C2 · Rà đóng góp và giới hạn
`3h` · phụ thuộc: T5-C1, T5-A1 · review: B

Mỗi khẳng định trong báo cáo trỏ tới test, log, biểu đồ hoặc SHA. Nếu delta không qua thì không viết là đã tối ưu. Hoàn thiện phụ lục protocol từ `docs/PROTOCOL.md`.

### T5-C3 · Luyện bảo vệ monitoring và luồng chung
`3h` · phụ thuộc: T5-C2, T5-B3 · review: B

Bạn giải thích bỏ sót do polling, state và lịch sử, ACK/epoch/byte; A và B lần lượt giải thích lại. Ghi các câu chưa trả lời được và xử lý trước khi chốt gói.

**Ghi chú làm dở (chặng 5):** —

---

## Task review (T1-CR … T5-CR) — 2 giờ mỗi chặng

Bạn review phần của **A**. Mỗi chặng: 1 giờ đọc code, test, tài liệu của A; 1 giờ cho các buổi tích hợp ngắn. Review theo từng buổi, không dồn cuối chặng.

Khi review code của A, kiểm tra:
- Có nhánh nào trả dữ liệu trước khi kiểm quyền không (đặc biệt nhánh retry, cache chống trùng, bài đã chốt)?
- `writerEpoch` và deadline có được kiểm tra sau khi lấy khóa không?
- Có chỗ nào dùng `now()` thay cho `clock_timestamp()` không?
- ACK có nằm sau commit không?
- Test tranh chấp có ép thứ tự bằng latch/barrier, hay dựa vào `sleep`?
- JSON gửi cho thí sinh có lộ đáp án đúng không?

Ghi kết quả review vào `docs/NHAT_KY.md`.

## Phần báo cáo bạn viết

Thiết kế monitoring và đồng bộ, protocol, Related Work về phương pháp giám sát, Experimental Setup, Results của E1 và E2, Novelty and Contributions, Limitations; phụ lục protocol.

## Câu hỏi bảo vệ bạn phải trả lời được

- Vì sao polling có thể bỏ sót process? Chu kỳ ngắn hơn đổi lấy cái gì?
- State và lịch sử event khác nhau thế nào? Vì sao snapshot mới không phục hồi được lịch sử?
- `syncEpoch`, `sequence`, `baseSequence` dùng để làm gì? Chuyện gì xảy ra khi mất một delta?
- Delta của epoch cũ đến sau khi đã có full của epoch mới thì sao?
- Vì sao so sánh full và delta phải dùng cùng tập process và cùng quy tắc event?
- Đo byte ở tầng nào? Vì sao phải báo cả tổng byte chứ không chỉ byte state?
- Đo độ trễ giữa hai máy như thế nào khi đồng hồ không đồng bộ?
- Client bị tắt đột ngột thì server biết bằng cách nào, và biết được gì?
- Process bị phát hiện có chứng minh thí sinh gian lận không?

Bạn cũng phải vẽ lại được luồng của A (khóa, deadline, commit rồi ACK) và của B (autosave, reconnect phía client).


## Bàn giao tích hợp15/10/2026

Nhánh `integration/reading-listening-20261015`, base8f5ac40, history2ca4121 và Reading65f5123. Guide `docs/INTEGRATION_READING_LISTENING.md`; evidence `evidence/integration/2026-10-15-verification.md`. Phạm vi review: C: collector/full/presence/transport hooks và giới hạn ID v0. Clean test833 PASS (protocol43/client569/server220/spike1), PostgreSQL18.6 thật và 0skip; package833 PASS. GUI smoke8nhóm PASS mới; chưa nâng toàn case human/LAN. FULL default, tracker không đổi.

**Ghi chú làm dở:** human A/B/C/LAN hai máy/installer/E1/E2 NOT RUN; ID ACTIVE đặc biệt còn contract QD29. Không dùng alias đọc status làm quyền monitoring; không merge main khi chưa được người dùng chấp thuận. Kết quả cuối/SHA/log trong evidence, không kế thừa PASS nhánh nguồn.

### Handoff B overnight15/10 — HUMAN C REVIEW PENDING

E1 mở rộng ControlledProcessHarness với window/sampler/CLI;12REALWindowsruns có raw immutable/checksums/analyzer, candidate gateSIMULATED, policyTESTowned không nới production8tên. C review actual lifetime vs targethold, parent-latency cận trên, CPUonecore/parentheap, cùng parentJVM/carryover, eligibility/censoring và reused plan. C1/C2 cóGUI regression PASS mới; không gate delta/E2/LAN/approval thật. [Summary](../../evidence/overnight-20261015/00-summary.md).

E1 quality thực tế:360eligible,287detected,62missed,11INCONCLUSIVE (observation thiếu start; GTstart của11 đọc được). Không đổi oracle để épPASS. Một số bucket không có đủ3miss-rate xác định; review bounds/unknown và heapcarryover trước khi dùng kết luận.


## Handoff bản thống nhất16/10/2026 — HUMAN REVIEW PENDING

Collector/policy/E1 source bằng38; raw84/checksums/14CSV repro giữ nguyên, shortSMOKE mới không E1final. C1 cuối không JFR PASS; review TEST FX20s/null cleanup và các FAIL cleanup ban đầu được giữ; C2+GUI PASS với readings MOCK/fault SIMULATED. FULL default, không bật delta. Checkpointb755646: clean/package mỗi lượt872PASS/1skip, Flow41/GUI11/nativeAudio8/production EXE+PG PASS subset; không nghiệm thu toàncase. [Báo cáo/SHA/count/failure/manual](../../evidence/unified-20261016/verification.md). Không đổi tracker/kế hoạch hoặc tự duyệt A/C.


### Ghi chú làm dở — QA PR39 ngày16/10/2026 (B bàn giao, chưa owner ký)

B gửi QA PR39 source9bfc75e: không sửa collector/reducer/E1 harness/policy, FULL giữ default. C1/C2 cuối PASS local có nhãn MOCK/SIMULATED;84rawE1 hash không đổi, không rerun12/E2 hoặc approve gate. C cần review epoch/gap/UNKNOWN, E1-method và contractQD29/progress; không coi QA của B là human C review. [Báo cáo](../../evidence/b-pr39-acceptance-20261016/FINAL_REPORT.md).


### Ghi chú làm dở — RC17/10/2026

Raw84 đã được tìm thấy/kiểm hash; rejoin12 byte-identical/analyzer17output deterministic, CPU time-weighted mới. C xác nhận kết luận E1 và patch41/43; C1 FAIL lịch sử UNRESOLVED; E2/gate chưa approve. Source TEST c224ba8, không merge nhánh nguồn/main hoặc ký thay owner. [Bàn giao](../../evidence/integration-final-20261017/REPORT.md).

### Ghi chú làm dở — T3-C4 physical LAN, 18/10/2026

B/C nhận [runbook](../T3_C4_PHYSICAL_LAN_ACCEPTANCE.md),41case và ZIP từ sourceTask1 `3044e5b`/runtime `3d93820`. LAN **NOT RUN/0case**, thiếu máyB, human B/C **PENDING**. KitPowerShell29checks PASS (MOCK fixture, không mạng), ZIP/hash/init/tamper PASS; probe/fault/SQL/runtime physical chưa chạy. C cần review script TEST, epoch/late callbacks/CLOSE cleanup của PR47, đối chiếu F no false ACK với state RAM vs history COMMIT, event/gap uniqueness và Reading/Listening giữ contract; giữ case thiếu evidence NOT RUN. Nếu tái hiện bug, corrective PR riêng với regression, không sửa thuật toán tại nhánh chuẩn bị; FULL default, gate NOT DECIDED, tracker/QD approval giữ nguyên. [Hồ sơ](../../evidence/t3-c4-physical-prep-20261018/REPORT.md).

### Bàn giao công bố Task2.1 — 18/10/2026

C/người giữ máy build cung cấp riêng ZIP `target/T3C4-3044e5b.zip`, đối chiếu hash công bố với B trên A/B; không đẩy binary/raw/credential lên Git. Nhánh bàn giao có PR47HEAD3044e5b làm ancestor, Draft PR nhắm nhánh PR47; source/runtime artifact3044/3d93820 giữ nguyên. Review script TEST/evidence và thực hiện F với B; LAN0/41 NOT RUN, human B/C PENDING, FULL default/gate chưa chốt. [Hồ sơ công bố](../../evidence/t3-c4-physical-prep-20261018/PUBLISH.md).

Bộ bàn giao đã ở [Draft PR52](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/52) vào nhánh PR47; không push source branch PR47 hoặc merge. C/B review và physical LAN còn chờ người thật.


## Ghi chú tích hợp19/10/2026
Main8a43a56 có PR47/57 và test56; sourcece38a5f clean/package942PASS/4SKIP, PG68PASS/2SKIPsubset, GUI11/Flow41/MODE8×5PASSlocal. Codex review kỹ thuật theo người dùng giao, không ghi human signatures. PR52/51 đã reuse vào nhánh tích hợp; A5 nhãn envelopev0 được sửa. HarnessC4 localFAIL do TEST backoffnormalpeer được giữ bằng chứng;1072f77 giữ lại productionbackoff, chờ rerun. Còn finalmain/CI/MSI và physicalLAN0/41/hearing/máy2; tracker không đổiDONE, FULL baseline.


19/10/2026 — [Kết quả thực thi cuối](../../evidence/stage3-execution-20261019/REPORT.md), [artifact và thao tác máy thật](../STAGE3_FINAL_HANDOFF.md). Buildsourcecfbba58, haiP1/main/CI/PG/regression/MSIlocal đã kiểm. Reviewer kỹ thuật Codex theo user, không chữ ký vai. Việc dở: cùng B chạy physical41case, chốt FULL/DELTA gate; giữ FULL khi chưa đủ bằng chứng, không nhận measurement INCOMPLETE là E1/E2PASS.


### Ghi chú làm dở — MON-02 standalone USB probe, 19/10/2026

Source `30166b3d50218a570ca523bde452a15a58dfcf0e`, nhánh `spike/v2-c-mon02-usb-storage` từ main1d35b537. [Probe/lệnh](../USB_STORAGE_PROBE.md), [report](../../evidence/v2-c-mon02/REPORT.md). Java21+PowerShell/CIM không dependency mới;35module tests PASS với device MOCK, REAL provider MSFT READY0/Win32 UNKNOWN token STANDARD. Timeout/flood/query close/Ctrl+C direct file/receipt guard có evidence; Tee Ctrl+C cũ INCOMPLETE giữ nguyên. Chưa physical cắm/rút/twoUSB/HID/UASP/no-drive-letter,0/12NOT_RUN; PnP/event subscription NOT_IMPLEMENTED, nativeRegistration NOT_USED. C/B cần review API/identity/polling loss/cleanup/hardware và ADR-V2-05; A cần auth/disclosure/retention/lifetime trước integration cùng MON-07/BASE-04. Không approve ADR, MON-02 DONE/backlog/merge/E1/E2 hoặc đổi FULL. Dừng sau push/Draft PR probe.


### Ghi chú CI73 follow-up — 19/10/2026

Test source1e75924: budget classification10s/diagnostic scalar sau close; CLI5s/parser/resource/lifecycle không đổi, hang150ms vẫn kiểm. Local35PASS và focused1case×3; CI39264645975SUCCESS. Failure39258040529 thiếuproblem nên chưa kết luận nguyên nhân cold startup; experiment delayMOCK chỉ chứng minh budget path. [Report](../../evidence/v2-c-mon02/REPORT.md), [summary/hash](../../evidence/v2-c-mon02/ci73-fix-20261019.json).15runtime hashes giống30166b3; receipt/JAR/provider snapshots mới đã kiểm,29raw/JAR/report cũ giữ nguyên. Physical0/12NOT_RUN, ADR-V2-05 và A/B review pending; PR73 giữDraft, CI HEAD hồ sơ cần xác minh riêng, không merge/DONE/MON07/08/E1/E2.

### Ghi chú làm dở — MON-07 contract proposal, 19/10/2026

**PROPOSED — NOT IMPLEMENTED — OWNER REVIEW PENDING.** Nhánh mới `docs/v2-c-mon07-contract-proposal` từ main `1d35b537`, chỉ [proposal](../MONITORING_MULTISIGNAL_CONTRACT_PROPOSAL.md)/fixture MOCK và hồ sơ. C đề nghị v0 + type/schemaVersion mới, signal queue RAM riêng/cùng socket, bảng mới/dedup/COMMIT-before-ACK, history phân trang. A/C cần chốt ADR-V2-04/BASE-04 (auth/transaction/schema/retention), B cần review consumer/opt-in/lifetime và emitters; Windows API/hardware ADR-V2-05 còn riêng. MON-01 technical verified nhưng physical/B/BASE-02 approval PENDING; PR70/71 chưa merge, không kéo commit verification vào proposal. FULL default/gate NOT_DECIDED, E2 không phép. [Kiểm docs](../mon07-proposal/VALIDATION.md); toàn bộ implementation test NOT_RUN, chưa MON-07 DONE/backlog hoặc human signoff. Dừng sau push/Draft PR, không triển khai MON-02/08 hay merge.


### Ghi chú làm dở — MON-07 review PR72

Đã hoàn thiện [D07-01–05 và kế hoạch slice](../MONITORING_MULTISIGNAL_CONTRACT_PROPOSAL.md#11-năm-quyết-định-để-owner-xác-nhận), **tất cả PROPOSED**; review/comment PR72 rỗng khi kiểm, không có nguồn owner approval. Khuyến nghị MOCK DISPLAY_COUNT_OBSERVED duy nhất, capability một type/no push/no observer production, RAM256/1MiB/2flight, immutable retry/COMMIT-before-ACK, counter+paged history, offline60s/retention30ngày chờ duyệt. Transport write stall và revoke khi chờ lock cần A/B review hook/test. Chỉ cập nhật nhánh proposal PR72; chưa tạo feature branch/DTO/SQL. FULLdefault/gateNOT_DECIDED, MON01physical/B/BASE02pending và MON02physical12NOT_RUN giữ nguyên. [Kiểm tài liệu](../mon07-proposal/VALIDATION.md), không runtime/hardware acceptance hoặc MON07DONE.


### MON07 slice MOCK — ủy quyền thử nghiệm19/10/2026

User cho phép D07-01–04 PR72c9a5829 cho MOCK/REAL WS/PG TEST/commit ACK/history, nhánh riêng feat/v2-c-mon07-signal-pipeline stacked. [Report](../../evidence/v2-c-mon07-signal-pipeline/REPORT.md); A/C review transaction/locks/schema, B review transport/generation/admission. Chưa human approval/D07-05/observer/push/dashboard/DONE hoặc merge. FULLdefault/gate/physicalpending giữ nguyên.

Kết quả slice source0f9d8e7: focused216PASS/fullverify1025PASS+6SKIP,12REAL PG/WS cases,41case mới subset; teardown0schema/stop0, JARreceipt cóhash. Reviewer A/C xem auth/locks/V13/cursor, B xem capability current run/ACK/admission/writecleanup; broader22matrix/hardware chưa PASS. CI exactHEAD trong Draft PR; không tự merge/DONE.

Source cuối c77d8f8/DraftPR74: focused219PASS/fullverify1028PASS+6SKIP,44new cases subset/14REAL PGWS; DBclock sau locks và terminalFAILED cleanup có regression trướcfix. Versionedreceipt/evidence mới, bản0f9d8e7/7cb7478 giữnguyên. Chỉ user-authorized experiment; A/C+B review cònpending, không MON07DONE hoặc merge.


### Ghi chú làm dở — review MON-07 PR74, 20/10/2026

Đã sửa 7 lỗi có regression trước fix tại [review](../../evidence/v2-c-mon07-signal-pipeline/REVIEW-20261020.md), source2d73605/focused227PASS,16PG/WS cases. A/C review token expiry sau assignment wait/transaction/cursor; B review lease identity, queued write guard và callback cleanup đúng request/generation. Flyway V13 vẫn áp khi experiment OFF, được kiểm trên TEST riêng; OFF không undo schema. Fullverify/JAR1036PASS+6SKIP/0FAIL/ERROR, teardown0schema/stop0; CI exact publication HEAD theo report/PR. Bước tiếp theo cho C: cùng owner review các diff này; >5000 typed history/GUI/LAN/hardware còn mở, không tự triển khai hoặc coi MON07DONE.

## Ghi chú làm dở BASE-03 — 19/10/2026

[Thực thi mới A–J](../../evidence/v2-c-base03/REPORT.md): FULL-first/314focused/983regressionPASS+7SKIP/Python22/oracle72/local42 và probe epoch/sequence WS PASS trên PG TEST riêng; production giữ nguyên. Nhánh test/v2-c-base03-delta-gate có commit local, chưa push/PR. Bộ ZIP BASE03/hash/runbook và41case đã chuẩn bị, kit29+29PASS nhưng physicalLAN0/41 NOT RUN, B review PENDING. Việc tiếp: nhận hai máy và B thao tác F Reading/Listening/cable-loss/rejoin/cleanup, C đối chiếu SQL/epoch/history, A xem auth/COMMIT/isolation; giữ raw target cho reviewer. GateNOT_DECIDED, FULL mặc định, E2NO; không tự DONE hoặc chuyển MON-07/MON-02.


## Ghi chú làm dở MON-01 — 19/10/2026

[Report MON-01](../../evidence/v2-c-mon01/REPORT.md): pipeline14chặng/15criteria đã đối chiếu,65focused+C2WS/PG/GUI PASS mới sourcef78b407; REAL ProcessHandle/FULL bằng evidence BASE-03 kế thừa (không lượt mới). BASE-03 đã sao lưu ngoài repo có hash/Gitbundle; giữ source9a97cab sạch. BASE-02 approval PENDING, physical0 và B chưa ký nên TECHNICALLY_VERIFIED_PENDING_PHYSICAL_REVIEW. Cùng B làm [checklist16dòng](../V2_C_MON01_PHYSICAL_HANDOFF.md), actual app end/submit/timeout và cable-loss/rejoin/unique history/workers; A xác nhận BASE-02 và contract decisions trước MON-07. Không triển khai MON-07/USB, không DONE/tracker, FULL default/DELTA gate NOT_DECIDED/E2NO.

## Ghi chú làm dở — review EX-02 / ADR-V2-03, 19/10/2026

C giữ scope review [contract](../EX02_RICH_EXAM_CONTRACT_V1.md)/[ADR](../plan-v2/ADR-V2-03_SESSION_START_DEADLINE.md) về auth/lock/timeout/monitoring. D01–D10 đã TEAM_APPROVED_AS_REPORTED_BY_PROJECT_REPRESENTATIVE theo [nguồn đại diện19/10](../plan-v2/EX02_TEAM_APPROVAL_20261019.md), không chữ ký/GitHub review cá nhân. EX07 cần PG/HTTP/WS barriers cho START vs takeover/invalidate/revoke/lease/restart, session→attempt→readiness locks, auth-before-receipt, no deadline reset/WS trước commit; EX06 GUI gate/heartbeat. Chấp nhận ảnh Part1 prefetch có thể trích xuất, không hứa server thấy pixels hoặc lập tức biết app đóng. Checkers là MOCK, S01–S16/V01–V20 runtime NOT RUN; FULL/WSv0/collector giữ, không tracker DONE.


### 20/10/2026 — Codex đã review kỹ thuật, tiếp tục nghiệm thu thật

[Review C/merge/receipt](../../evidence/c-technical-review-20261020/REPORT.md):70–73đãmergeđúngscope;74base main/source d6bf60a fullverify1071PASS+6SKIP/17PG,5001WS/51pages/concurrent24 vàshared process smokePASS. Đã kiểm phầnA/Cauth/locks/transaction/COMMIT/cursor vàBlease/admission/ACK/callback/cleanup; không còn ghi chưa review kỹ thuật cho các phần này. Human contract/ADR/offline/retention vàphysicalpending không đổi. BướcCtiếp: receipt/kit đúng main tíchhợp, cùngngười thật chạyUSB/LAN/GUIchecklists; khôngtựDONE hoặc lấymerge/CIthayhardware.

### Ghi chú làm dở — kit main tích hợp 20/10/2026

[Report kit](../../evidence/c-integrated-kit-20261020/REPORT.md): maina6fba311,35USB/65consumer/33kit PASS mới, provider thật READY0/UNKNOWN không hotplug. Builder mới pin source/JAR từ HEAD sạch, không relabel JAR cũ; runbook USB đủH01–12/GUI-LAN đủ41classification, TEST-only/FULL/experimentOFF. Giữ USB0/12/LAN0/41/Bpending/gateNOT_DECIDED; chưa DONE/ADR. Bước tiếp không USB: cùng B kiểm GUI start/stop/logout/submit/timeout FULL trên một Windows, dependency A cấp TEST DB/đề/audio/assigned scopes; ghi workers/actual/manual evidence, không nhận LAN hoặc triển khai observer. Chưa chạy bước tiếp này.
### Ghi chú làm dở — FULL lifecycle tự động 20/10/2026

[Report](../../evidence/c-full-lifecycle-20261020/REPORT.md): runtime main5bbeaf2/scenario47caa50,6nhóm A-F/10checkpoints programmatic production Application/JAR+REAL PG/HTTP/WS PASS và215JUnitMOCKPASS/0SKIP. Có process TEST/GUI Scene/API/jcmd, submit COMMIT và timeout8s tự nhiên, worker/executor cleanup0. Sửa test fixture ownership sau FAIL thật, không có production bug chứng minh. Native ComputerUse pipe unavailable, user chọn desktop NOT_RUN; hai JAR handoff và4TESTcluster đã dọn. Không GUI desktop/two-machine/USB/audio hearing/gate/DONE PASS. C tiếp: kiểm OS input/window-close trên hai JAR riêng khi helper/người phối hợp có; dependency chỉ desktop, TEST scope/assets/API đã tự dựng được. Không observer mới, giữ FULL/experimentOFF và hardwarepending.