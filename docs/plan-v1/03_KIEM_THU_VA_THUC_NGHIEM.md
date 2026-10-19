# KIỂM THỬ VÀ THỰC NGHIỆM

**Tất cả case dưới đây đang NOT RUN.** Đây là thiết kế kiểm thử và mẫu thu bằng chứng. Không có bảng số liệu giả hoặc nhãn Passed trong gói kế hoạch.

## 1. Bằng chứng tối thiểu của một test

Ghi testId, runId, git SHA, OS/JDK/PostgreSQL/library version, cấu hình, bước chạy hoặc command, expected, actual, status, log/ảnh/query kiểm tra và người chạy. Với test tranh chấp dùng latch/barrier hoặc khóa DB chủ động để tái lập thứ tự; không chỉ “bấm nhiều lần thấy ổn”. Dùng PostgreSQL thật cho semantics khóa/thời gian; test thuần reducer có thể chạy độc lập.

Test đơn vị + tích hợp phải bám rủi ro. Ghép happy path trước rồi thêm lỗi; không đợi xây toàn bộ harness mới chạy hệ thống. Người thực hiện và reviewer theo task trong 01_KE_HOACH.md; test không có giờ công độc lập ngoài backlog đó.

## 2. Bộ test Answer/Submission

| ID | Cách gây tình huống | Kết quả bắt buộc | Owner / review; task |
|---|---|---|---|
| AT01 | Thí sinh B gọi save/submit/result/retry cache của A, trước và sau khi A nộp | Không đọc/sửa dữ liệu A; trạng thái chốt không mở lỗ hổng; WS scope cũng kiểm | A/C; T2-A4, T3-A4 |
| AT02 | Chưa login hoặc role candidate gọi import/start/retest; thiếu token WS | Bị từ chối; không có dữ liệu ca ngoài quyền | A/C; T1-A2, T2-A1 |
| AT03 | Gửi revision 42 rồi 41; sửa/clear lựa chọn trong toàn bộ map | 41 không ghi đè; full map được lưu đúng khi revision hợp lệ | A/C; T2-A2 |
| AT04 | Cùng revision/nội dung hai lần; cùng revision khác nội dung; cùng map đổi thứ tự key | Retry đúng là idempotent; payload khác conflict; đổi key order không gây conflict giả | A/C; T2-A2 |
| AT05 | RequestId lặp cùng/khác payload; cố làm DB rollback | Payload khác bị từ chối; rollback không ACK thành công; retry sau commit không ghi lần hai | A/C; T2-A2, T4-A1 |
| AT06 | Giữ autosave cũ, gửi submit mang answers cuối rồi thả save | Result phản ánh answers submit hợp lệ; save muộn không thay đổi bài đã chốt | A/C; T2-A3 |
| AT07 | Transaction khác giữ khóa tới sau deadline; request bắt đầu trước deadline chờ khóa | decisionAt sau khóa >= deadline; không nhận answers mới; log có mốc quyết định | A/C; T2-A4 |
| AT08 | Submit lặp, timeout và save đến gần nhau; thêm case kiểm tra trước hạn nhưng commit sau hạn | Một kết quả bất biến theo thứ tự khóa/decisionAt; không chấm hai lần; chốt timeout từ bản đã lưu | A/C; T2-A3, T4-A1 |
| AT09 | Request writer cũ đã vào handler nhưng dừng trước lấy khóa; kích hoạt writer mới và commit; thả request cũ | Epoch được kiểm lại sau khóa; writer cũ không ghi. Thử thêm thứ tự ngược để xác nhận ghi trước takeover là hợp lệ | A/C; T2-A4 |
| AT10 | Save/submit cố sửa answers sau final hoặc sau deadline khi job timeout chưa chạy | Không nhận answers mới; query chỉ trả dữ liệu trong quyền; timeout trễ không làm trễ deadline logic | A/C; T2-A4 |
| AT11 | Server 40/local 42; mất ACK; reconnect cùng app; sau đó mở app mới hoặc takeover | Cùng writer giữ payload/revision; writer mới lấy cả answers/revision/state; không tự replay cache cũ | B/A; T2-B4, T4-B1 |

Mỗi test deadline đặt thời gian kiểm soát đủ lớn để không phụ thuộc thao tác thủ công; kết luận dựa trên `decisionAt`/DB state. Không dùng chỉnh giờ máy client để giả định đã kiểm giờ server.

## 3. Bộ test Monitoring Synchronization

| ID | Cách gây tình huống | Kết quả bắt buộc | Owner / review; task |
|---|---|---|---|
| MT01 | Join trước start, full snapshot hợp lệ; mở/đóng process kiểm soát | State và lịch sử rõ; process role proctor không bị collector thu; event không lặp mỗi poll | C/B; T1-C2/C3, T2-C1/C2 |
| MT02 | Replay cùng event/state message sau khi mất ACK | EventId duy nhất; state không áp hai lần; cùng ID khác payload báo protocol conflict | C/B; T3-C3 |
| MT03 | Bỏ một delta hoặc đưa delta cần base cao tới trước | UNSYNCED/stale; yêu cầu full; không đoán phần thiếu | C/B; T3-C3 |
| MT04 | Full S20 thuộc epoch mới rồi phát lại delta D11 epoch cũ | State không thay đổi bởi D11; full epoch mới là gốc | C/B; T3-C3/C4 |
| MT05 | Khởi động lại collector/client hoặc server; gửi sequence/collector cũ | Cần epoch/full mới; collector không còn quyền không sửa state hiện tại | C/B; T3-C3/C4 |
| MT06 | Full đối soát số cao, snapshot số thấp, cùng seq khác payload; replay 3 loại trace | Không lùi state; lỗi rõ; tại version so sánh full và delta cho cùng canonical state/event set | C/B; T3-C3 |
| MT07 | Event muộn sau reconnect và queue tràn; rồi gửi snapshot mới | Event hợp lệ chỉ thêm history; duplicate không thêm; dropped/gap không bị snapshot xóa | C/B; T2-C2 |
| MT08 | Kill ứng dụng/đứt mạng, không cho client gửi thông báo cuối | Server timeout sang UNKNOWN, lưu lastSeen/timeoutDetected; không khẳng định thời điểm monitoring dừng hoặc mọi hành vi đã biết | C/B; T2-C2 |

Oracle độ đúng: với cùng chuỗi quan sát, full và delta khi đã đồng bộ ở cùng version phải có cùng tập process chuẩn hóa. Lịch sử event so sánh theo eventId/nội dung của quy tắc chung, không theo thứ tự đến mạng. Khi có gap, so sánh trạng thái UNKNOWN/UNSYNCED được mong đợi, không so state stale như thể state hiện tại là thật.

## 4. Listening và tích hợp/package

| ID | Tình huống | Kết quả bắt buộc | Owner / review; task |
|---|---|---|---|
| LT01 | Thiếu audio, tải chưa xong, checksum hỏng | Không READY/start; lỗi rõ; tải lại chỉ trong chuẩn bị | B/A; T3-B1 |
| LT02 | Start message lặp hoặc lỗi media/thiết bị | Không phát từ đầu hai lần; playback error khóa và ghi gián đoạn | B/A; T3-B2/B3 |
| LT03 | Mất mạng giữa audio; kill/mở lại; reconnect | Dừng khi phát hiện gián đoạn; không tự replay/resume; trạng thái server tương ứng | B/A; T3-B3 |
| LT04 | Giám thị tổ chức lại và lượt cũ hết giờ | Attempt mới có ID mới; dữ liệu/deadline cũ giữ; không sửa âm thầm | B/A; T3-B4, T3-A2 |
| IT01 | Chạy từ package trên máy không có IDE, đường dẫn có dấu/khoảng trắng | Login, audio, runtime và config hoạt động; ghi OS và dependencies cần thật | B/A; T1-B4, T3-B5, T5-B1 |
| IT02 | 2 thí sinh + 1 giám thị, một client gửi JSON sai hoặc disconnect | Client khác còn hoạt động; scope/role không lẫn; cảnh báo theo đúng attempt | A/C; T3-A4; B/A T4-B1 |
| IT03 | Làm network/scan chậm khi đổi câu/resize; đóng app | UI vẫn phản hồi; không gọi scan/network trên UI thread; worker được dọn | B/A; T1-B2, T4-B1 |
| IT04 | DB/server tắt ở lúc event/save đang xử lý; khởi động lại | Không ACK giả; retry/reload theo state authoritative; dashboard lấy lại history; server mới resync monitoring | A/C; T3-A3, T4-A1; B/A T4-B1 |

## 5. Hai thực nghiệm độc lập

### E1. Trade-off chu kỳ polling

**Câu hỏi:** trong dữ liệu thử, giảm chu kỳ quét làm detection latency/miss rate/CPU thay đổi ra sao? Đây là so sánh tham số của ProcessHandle polling, không được gọi là đã benchmark mọi phương pháp monitoring.

- 4 chu kỳ: 250/500/1.000/2.000 ms. Giữ nguyên máy, JVM, policy, logic event và chế độ full snapshot.
- Mỗi run có 15 giây warmup, 120 giây đo; 3 lần lặp mỗi chu kỳ, tổng 12 run, khoảng 27 phút chạy thuần chưa tính setup/phân tích. Ngân sách chạy/phân tích nằm ở T2-C4, T4-B2, T4-C1/C3.
- Harness khởi chạy process kiểm soát với 3 độ dài mục tiêu: 200 ms, 800 ms, 3.000 ms; tối thiểu 10 lần mỗi độ dài/run, pha bắt đầu so với polling lệch nhau theo seed. Lưu thời gian sống thực đo, không giả định đúng tuyệt đối theo sleep yêu cầu.
- Ghi ground truth từ harness launch/exit trên cùng máy, cùng nguồn monotonic clock; startup điều khiển được có thể dùng tín hiệu “ready” của process thử để biết cửa sổ quan sát. Không lấy chính collector làm ground truth của nó.
- Chỉ đưa sự kiện nằm trọn cửa sổ đo vào mẫu; ghi rõ mẫu bị loại. Công bố các trường metadata không đọc được và cách xử lý, không âm thầm loại để tăng tỷ lệ phát hiện.
- Metrics: số event ground-truth/đã phát hiện/missed, miss rate theo độ dài, detection latency p50/p95 trên các event phát hiện được, CPU process/collector và memory. Missed event không được gán latency=0. Ghi thêm run không đo được thay vì tự điền.
- CPU định nghĩa rõ: `ΔCPU_time / Δwall_time ×100%` là phần trăm một core; nếu chia thêm số core thì báo riêng là chuẩn hóa toàn máy. Peak RSS hoặc JVM heap phải ghi rõ loại, không đổi lẫn hai khái niệm.

Nhận định dự kiến chỉ là giả thuyết: poll nhanh có thể giảm bỏ sót process ngắn, đổi lấy nhiều CPU. Không có mục tiêu giảm tài nguyên hay tỷ lệ phát hiện cam kết trước đo. C có bảng khảo sát OS event API/cách liệt kê process khác bằng tài liệu; nếu chưa triển khai, gọi đúng là khảo sát, không điền số đo cho chúng.

### E2. Full snapshot so với snapshot + delta

Chỉ chạy so sánh hiệu suất của delta sau gate correctness. Baseline không bị cố ý làm yếu: hai mode cùng lọc process, cùng event dedup, cùng auth/ACK/heartbeat/retry/resync, cùng compression setting và lịch heartbeat.

| Yếu tố | Quy định |
|---|---|
| Input | Cùng trace snapshot chuẩn hóa, checksum và seed |
| Polling | Cố định 1.000 ms cho phép so sánh này, hoặc một giá trị được chọn và khóa trước run |
| Snapshot | Full mỗi tick ở baseline; delta mode full đầu epoch + delta + full đối soát định kỳ 30 giây thử nghiệm |
| Tick không thay đổi | Delta không cần gửi state payload mới nếu contract cho phép; heartbeat vẫn như baseline; nêu rõ đây là phần khác biệt trong truyền state |
| Quy tắc event | Cùng quan sát đầu vào và logic; không gom cảnh báo chỉ ở delta |
| Độ tin cậy | Cùng ACK, bounded retry, reconnect và thông báo gap; tính mọi resync/retry |
| Mạng/process | Cùng topology, ứng dụng nền và giới hạn client; metadata máy được ghi |
| Input có lỗi | Duplicate/drop/reorder/reconnect được dựng bằng harness; không gọi đó là tỷ lệ packet loss đo từ mạng thật |

Tối thiểu 3 trace, mỗi trace 120 giây, 3 lần lặp/mode: **18 run**, cộng warmup 15 giây/run khoảng 40,5 phút chạy thuần. Trace (1) ít thay đổi, (2) nhiều process thay đổi, (3) reconnect ở lịch định trước, chẳng hạn giây 40–45 và 80–85. Cấu hình chính xác lưu kèm. Đảo thứ tự full/delta giữa các lần lặp để giảm ảnh hưởng JVM/cache/nhiệt máy.

Trước run đo, replay kiểm oracle tại mọi version so sánh; có bất kỳ mismatch không giải thích được thì loại delta khỏi kết luận hiệu suất vận hành, giữ failure trace. Sau replay có ít nhất một cặp run live trên desktop thật để kiểm đường collector→network→server→giám thị.

**Các số phải báo cáo:**

1. State data bytes (snapshot/delta outbound), state-control bytes (ACK/resync của luồng này) và tổng state stream hai chiều. Quy định rõ byte UTF-8 đã serialize, không đếm ký tự Java hoặc dung lượng object.
2. **Tổng byte ứng dụng hai chiều cho cùng cửa sổ và cùng phạm vi endpoint**, gồm state, history event, ACK, heartbeat, retry, auth/handshake payload và đồng bộ lại. Ghi cả direction/category để không đếm cùng frame hai lần. Log bộ đếm application payload là phạm vi chính; không gọi là TCP/IP bandwidth.
3. Nếu session không chứa download audio/HTTP làm bài thì ghi “monitoring session only”; đó không phải tổng chi phí một kỳ thi. Run hỗn hợp chỉ gọi tổng hệ thống khi đã đo cả HTTP lẫn WS của các thành phần liên quan.
4. CPU client/server, memory, throughput sự kiện hợp lệ và latency rõ định nghĩa. Báo số theo từng run cùng thống kê tóm tắt; 3 lần lặp là thí nghiệm sinh viên nhỏ, không khẳng định tổng quát rộng.

Nếu capture wire bytes bổ sung, ghi rõ overhead WebSocket/TLS/TCP/IP và cách lọc flow; không bắt buộc làm capture để qua MVP. Tải audio ban đầu báo riêng, không trộn vào cửa sổ truyền monitoring để làm tỷ lệ đẹp.

### Đo thời gian không trừ hai đồng hồ chưa đồng bộ

- Polling latency: harness và collector cùng máy/clock monotonic.
- RTT nhận ACK: client lấy chênh lệch monotonic trước send và khi nhận ACK cùng requestId; không gọi RTT này là one-way network latency.
- Thời gian server xử lý/commit: lấy span monotonic cùng tiến trình server, tách lock wait nếu có log.
- Độ trễ tới màn hình giám thị: có thể chạy hai client trên cùng máy dùng nguồn thời gian chung được kiểm chứng hoặc harness điều phối có ACK từ giám thị; ghi nó là round-trip/upper-bound tùy cách đo. Với hai máy chưa đồng bộ, không trừ observedAt phía candidate với renderedAt phía proctor.
- Nếu không đủ instrumentation cho một chỉ số, bỏ claim chỉ số đó, giữ RTT và span nội bộ đo được.

### Tải đồng thời tối thiểu bổ sung

A chạy headless clients 1/5/10 kết nối, mỗi mức 3 run 120 giây + warmup 15 giây (9 run, khoảng 20,25 phút thuần). Thử save có tốc độ được khóa theo kịch bản và monitoring; ghi tỷ lệ thành công/lỗi, response RTT, CPU/memory. B chạy 2 candidate desktop + 1 proctor riêng để xác nhận UX; không suy ra 10 client giả lập = 10 ứng dụng JavaFX thật. Không đặt mục tiêu 200 client.

Giới hạn dừng do tài nguyên/lỗi phải được ghi; nếu mức 10 không chạy nổi, báo mức đo cuối và nguyên nhân, không xóa run thất bại. Thời gian chuẩn bị/harness/cleaning mới là phần lớn công sức, đã có task riêng; số phút chạy thuần không phải ước lượng toàn bộ thí nghiệm.

## 6. Đầu ra thực nghiệm cần tạo khi triển khai

Chưa có các file kết quả này trong ZIP; danh sách dưới là mẫu cấu trúc bàn giao sau khi thực hiện:

- `run-metadata.json`: runId, SHA, máy/OS/runtime, mode, policyVersion, pollMs, seed, trace checksum, thời gian và phạm vi byte.
- `events.csv`: observed/received/ack correlation, revision/sequence, trạng thái, loại message, size; timestamp có tên miền đồng hồ.
- `resources.csv`: thời gian lấy mẫu, CPU-time, wall-time, heap/RSS đúng loại, PID/process role.
- `summary.csv`: một dòng/run, số mẫu, miss, latency, bytes theo loại, CPU/memory và run status.
- `reproduce.md`: command thực đã chạy, fixtures, điều kiện môi trường; `failures/`: trace lỗi và expected/actual.

Không đưa sẵn dòng số ngẫu nhiên vào các bảng như thể là kết quả. Tách raw bất biến khỏi summary do script tính. C review định nghĩa số đo, B review khả năng tái lập, A đối chiếu số request/server state.

## 7. Đóng góp và bằng chứng để bảo vệ

| Claim ứng viên | Bằng chứng cần có | Giới hạn kết luận |
|---|---|---|
| Đồng bộ delta đúng trong điều kiện đã thử | Reducer, replay oracle, MT02–MT06 và trace fault | Không khẳng định phục hồi đủ lịch sử hoặc chống client bị sửa |
| Delta giảm payload | State stream + application total cùng điều kiện, CPU/latency đối chiếu | Nếu chỉ giảm state bytes nhưng tổng gần như không đổi, nói đúng như vậy |
| Lựa chọn polling có cơ sở | E1, ground truth, miss theo độ dài, CPU/latency | Chọn cho môi trường thử, không gọi là thuật toán tối ưu toàn cục |
| Lưu/nộp có quy tắc nhất quán | AT06–AT10 trên DB thật, writer takeover, auth tests | Engineering contribution cần thầy đánh giá, không tự coi dùng transaction là novelty đủ |

Nếu delta sai: release full snapshot; báo nguyên nhân và failure trace. Vẫn còn bằng chứng lựa chọn polling và xử lý mạng/lượt thi, nhưng hỏi thầy xem đóng góp đó đủ chưa. Không biến một thử nghiệm thất bại thành lời bảo đảm điểm. Nếu delta đúng nhưng không có lợi: báo kết quả âm, giữ baseline phù hợp vận hành.
