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

**Ghi chú làm dở:** Đã viết collector fixed-delay, policy, snapshot và nút demo cục bộ chỉ cho candidate. 47/47 test toàn dự án PASS, package PASS; có test process Java thật xuất hiện/thoát. Chờ B review và thao tác GUI Edge/proctor/logout. Login chưa cấp attempt nên không tự bật monitoring thật. Bằng chứng: `evidence/stage1/2026-10-03-collector.md`.

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

**Ghi chú làm dở:** —

### T1-C4 · Log đo và ghi chú lựa chọn phương pháp monitoring
`3h` · phụ thuộc: T1-C1, T1-C3 · review: B

**Đầu ra:** schema log dùng cho thực nghiệm về sau; bảng khảo sát phương pháp.

**Cách làm:**
1. Định nghĩa schema log: thời điểm (ghi rõ thuộc đồng hồ nào), loại message, hướng (gửi/nhận), kích thước, `traceId`, `sequence`/`eventId`.
2. Bộ đếm byte hai chiều: đếm số byte UTF-8 của payload đã serialize, theo từng loại message và từng hướng. Đây là byte tầng ứng dụng, không phải băng thông TCP/IP.
3. Bảng khảo sát ít nhất 2 cách thu thập thông tin process (ví dụ polling bằng `ProcessHandle` so với cơ chế thông báo sự kiện của hệ điều hành), dẫn nguồn từ tài liệu chính thức. Ghi rõ cách nào nhóm đã chạy, cách nào chỉ khảo sát trên tài liệu.

**Dễ sai:** điền số đo cho phương pháp chưa chạy. Bảng khảo sát là nguồn cho phần Related Work; mọi nguồn phải trích dẫn được.

**Xong khi:** log của một phiên demo đọc được theo schema; bảng khảo sát có nguồn.

**Ghi chú làm dở:** —

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

**Ghi chú làm dở:** —

### T2-C2 · Heartbeat, event muộn và khoảng trống
`4h` · phụ thuộc: T2-C1 · review: B

**Cách làm:**
1. Kill client: server chuyển UNKNOWN sau timeout, lưu `lastSeenAt` và `timeoutDetectedAt` (MT08). Giám thị thấy "không còn xác nhận được monitoring".
2. Event đến muộn sau reconnect: nếu hợp lệ và thuộc quyền thì thêm vào lịch sử với nhãn đến muộn; trùng thì bỏ. Event muộn không được dùng để sửa state hiện tại.
3. Khoảng trống (do rớt mạng hoặc hàng đợi tràn) lưu thành bản ghi riêng. Snapshot mới sau đó phục hồi state nhưng bản ghi khoảng trống vẫn còn (MT07).

**Dễ sai:** khi client quay lại ONLINE thì xóa dấu vết gián đoạn. Server không biết chính xác monitoring dừng lúc nào, chỉ biết lúc mình phát hiện.

**Xong khi:** MT01, MT07, MT08 có bằng chứng.

**Ghi chú làm dở:** —

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

**Ghi chú làm dở:** —

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

**Ghi chú làm dở:** —

### T2-C5 · Ghi phương pháp baseline và protocol
`2h` · phụ thuộc: T2-C2, T2-C4 · review: B

Viết ra: `policyVersion`, khóa process, phạm vi đo, giới hạn về quyền và PID tái sử dụng; định nghĩa "bỏ sót" và "độ trễ phát hiện"; đầu vào của benchmark. Đưa cho B review trước khi chạy đo. Cập nhật `docs/PROTOCOL.md`.

**Ghi chú làm dở:** —

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

**Ghi chú làm dở:** —

### T3-C3 · Phát lại kiểm tra tính đúng và lỗi
`4h` · phụ thuộc: T3-C2, T2-C3 · review: B

Dùng replayer chạy ba loại trace (ít thay đổi, nhiều thay đổi, có reconnect) với các lỗi: trùng, thiếu, đảo thứ tự, reconnect. Ở mỗi phiên bản so sánh được, full và delta phải cho cùng state chuẩn hóa và cùng tập event. Khi có khoảng trống, so với trạng thái UNSYNCED/UNKNOWN mong đợi.

**Xong khi:** MT02–MT06 có bằng chứng, mỗi loại trace có file lưu lại.

**Ghi chú làm dở:** —

### T3-C4 · Tích hợp delta thật rồi quyết định giữ hay cắt
`4h` · phụ thuộc: T3-C3, T3-A3 · review: B

Chạy thật trên hai desktop: epoch cũ, mất ACK, khởi động lại client, khởi động lại server. Ngày 20/10 chốt QD-10 cùng B và ghi vào `docs/QUYET_DINH.md`.

- **Qua:** delta bật được bằng cấu hình; full vẫn là đường chạy dự phòng.
- **Không qua:** delta tắt mặc định; dùng giờ còn lại để ổn định full snapshot và lưu trace lỗi kèm kết quả mong đợi/thực tế.

**Ghi chú làm dở:** —

### T3-C5 · Bản nháp báo cáo đồng bộ và giới hạn
`2h` · phụ thuộc: T3-C4 · review: B

Viết: state và event tách biệt ra sao, khoảng trống không phục hồi được, full là phương án dự phòng. Nếu gate không qua, ghi rõ delta là thử nghiệm.

**Ghi chú làm dở:** —

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

**Ghi chú làm dở:** —

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
