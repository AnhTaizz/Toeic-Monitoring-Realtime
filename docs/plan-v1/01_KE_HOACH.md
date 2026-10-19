# KẾ HOẠCH TRIỂN KHAI BTL LẬP TRÌNH MẠNG

**Hệ thống thi TOEIC Client–Server có hỗ trợ giám sát hành vi theo thời gian thực**  
Nhóm 3 người • Kế hoạch v1.0 • Lập tối 02/10/2026 • Thực hiện 03–31/10/2026 • Giờ Việt Nam (UTC+7)

> Đây là bộ kế hoạch giao việc. Chưa có source ứng dụng, bộ cài, bài đo hoặc test đã chạy. Mọi task bắt đầu ở TODO. ZIP này không phải bộ nộp cuối kỳ.

## 1. Khả thi, công suất và căn cứ

**Với giả định ban đầu 15 giờ/người/tuần, chưa đủ cơ sở cam kết toàn bộ phạm vi đã chốt.** Từ 03 đến hết 31/10 chỉ có 29 ngày: tổng 186,43 giờ công cho 3 người; giữ 20% dự phòng còn **149,14 giờ có thể giao việc**. Không thể tính thành 5 × 7 ngày hoặc 225 giờ.

Backlog đầy đủ bên dưới ước lượng **246 giờ công**, gồm học, tự test, review, tích hợp, viết tài liệu và thực nghiệm, chưa tính dự phòng. Để giữ tối thiểu 20% dự phòng cần 307,5 giờ gross, tương đương **24,75 giờ/người/tuần**. Lấy mốc lập kế hoạch **25 giờ/người/tuần** (khoảng 3,6 giờ/ngày bình quân), không coi nhóm đã đồng ý tăng giờ. Khi đó có 310,71 giờ gross và 64,71 giờ còn trống, bằng 20,83% công suất.

Đây là **ước lượng có điều kiện, độ tin cậy ban đầu thấp**, vì nhóm chưa rõ kinh nghiệm JavaFX/Security/WS. 25 giờ cũng không bảo đảm hoàn thành; phải đo giờ thực tế ở chặng 1 và dự báo lại ngày 08/10. Không bù thiếu bằng làm thêm không ghi nhận hoặc bỏ test tính đúng.

### Hai phương án công suất

| Phương án | Giờ giao việc cả nhóm | Dự phòng tối thiểu | Ý nghĩa |
|---|---:|---:|---|
| Giữ 15 giờ/người/tuần | Tối đa 149,14 | 37,29 | Chỉ cam kết prototype sớm và quyết định phạm vi lại; thiếu khoảng 96,86 giờ so với backlog đầy đủ |
| Đề xuất 25 giờ/người/tuần | 246,00 | 64,71 còn lại | Kế hoạch 5 chặng dưới đây; cần xác nhận khả năng thực hiện của từng người |

Nếu chỉ giữ 15 giờ: trong chặng 1 giới hạn khoảng 10 giờ/người cho login → process thật → event lưu DB → giám thị → thử package. Demo có thể phải lùi từ 06 sang 08/10 và chưa đủ mọi test lỗi. Không triển khai delta ở bản chính; xin thầy duyệt Reading + monitoring làm sản phẩm cơ sở, chuyển Listening thành spike/future work nếu cần. **Đây là thay đổi phạm vi cần trao đổi, chưa tự coi là đã được duyệt.** Chỉ cắt focus/đồ họa/admin nâng cao không tiết kiệm được 96,86 giờ, vì chúng vốn chưa có trong backlog. Nếu thầy vẫn yêu cầu Listening và nhóm không tăng giờ, lịch hiện tại chưa khả thi; phải thương lượng scope/thời hạn, không lập một cam kết giả.

### Giả định giới hạn để giữ kế hoạch nhỏ

- A/B/C là vai trò tạm; không gán Tài hoặc người khác khi chưa có thông tin. A mạnh backend hơn, B nhận JavaFX, C nhận monitoring; mỗi người vẫn có phần code/test/báo cáo riêng.
- Một OS mục tiêu trước (Windows x64 của nhóm), một ca thi mẫu, một đề Reading/Listening rút gọn khoảng 10–20 câu và audio ngắn. Không hứa đủ 200 câu hoặc chuẩn hóa điểm TOEIC chính thức; chấm số đúng/điểm nội bộ được mô tả rõ.
- Tối thiểu hai máy để kiểm chứng package/LAN; hướng tới 2 thí sinh + 1 giám thị thật. Có thể chạy nhiều instance trên cùng máy để tích hợp nhưng phải ghi topology.
- Java + JavaFX, Spring Boot chia module, PostgreSQL, Maven, HTTP/REST + WebSocket chuẩn/JSON, ProcessHandle polling giữ nguyên. Python chỉ phân tích nếu cần. Khóa phiên bản tương thích sau spike 03/10, không dùng kế hoạch này để khẳng định phiên bản đã kiểm thử.
- Không thêm React, C#, Python agent, broker, microservices, Kubernetes, webcam, USB, khóa OS, streaming audio hay AI chấm bài. Không xây editor đề phức tạp, đăng ký tài khoản công khai hoặc quản trị nhiều ca nâng cao.
- Dữ liệu đề/audio tự tạo hoặc được phép sử dụng. Chỉ thu metadata process cần chính sách; quyền đọc thiếu phải lộ rõ. Process bị quan sát là tín hiệu nghi vấn, không chứng minh người dùng gian lận.

### Nguồn và cách sử dụng

| Nguồn trong `nguon/` | Nội dung có căn cứ | Cách đưa vào kế hoạch |
|---|---|---|
| Instruction.md §§1, 3, 5–9, 13, 16 | Network/concurrency/error handling; novelty có bằng chứng; báo cáo dạng paper; số liệu thật; tỷ trọng đề xuất | Task có contract/test/evidence; báo cáo viết từng chặng. Không suy ra bảo đảm điểm A+ |
| Topics.md §4.6 và §§4.7–8 | Thi qua mạng thuộc ứng dụng Client–Server; chức năng là các ví dụ; trọng tâm vai trò mạng | Không coi mọi dòng ví dụ là bắt buộc; không đưa queue vì nó xuất hiện trong ví dụ auto-grader |
| README_mon_hoc.md | Mẫu cấu trúc mô tả, build/run/test, contribution | Là checklist điền theo sản phẩm thật, không giữ trạng thái Done/Passed mẫu |
| Submission.md §§1–4, trích nguyên bản từ final-project(1).zip | 23:59 ngày 31/10/2026; báo cáo Compilatio; nguồn chạy được; Source/Report bàn giao lớp trưởng qua USB | Lịch kết thúc trước hạn. Instruction còn nói Google Drive; hỏi lại kênh cuối, ưu tiên chuẩn bị bộ cụ thể theo Submission và không bỏ qua thông báo mới |
| Transcript phần I [00:44], [03:17–04:44], [30:28–31:01] | Thu hẹp lời hứa recovery; giám sát hành vi; prototype login/detect tuần sau; nghiên cứu so sánh phương pháp | Demo mạng/monitoring sớm, giới hạn quan sát công khai, note khảo sát từ chặng 1 |
| Transcript phần II (trang 12–17) | Định hướng tổng hợp do người biên soạn trình bày | Chỉ tham khảo, không biến thành nguyên văn hoặc yêu cầu bắt buộc của thầy |
| Yeu_cau_lap_ke_hoach.txt và quyết định đã chốt trong cuộc trao đổi | Stack, 3 hợp đồng, 3 sửa đổi cuối, cách phân công | Là ràng buộc của nhóm; được chuẩn hóa tại 02_HOP_DONG.md |

Rubric được Instruction gọi là **“Đánh giá đề xuất”**: problem/functionality 20%, network/implementation 25%, novelty 15%, experiment 15%, report 10%, demo 15%. Do đó ưu tiên giao tiếp đúng và có số liệu, không đầu tư giao diện phụ để thay đóng góp kỹ thuật.

### Ba thông tin cần nhóm xác nhận trong 03/10

1. Mỗi người thực sự dành được bao nhiêu giờ/tuần, ai đã từng làm JavaFX/Spring/SQL transaction? Dựa vào đó gán tên vào A/B/C và duyệt hoặc giảm backlog 246 giờ.
2. Gặp thầy ngày/giờ nào trong tuần 05–11/10, có ít nhất hai máy Windows để thử và trình diễn không?
3. Thầy chấp nhận phạm vi TOEIC rút gọn, hướng đóng góp protocol/polling và chính sách Listening gián đoạn này chưa; kênh nộp cuối dùng Submission/USB hay bổ sung Drive?

Kế hoạch vẫn có thể dùng ngay trong khi chờ trả lời. Các ô chưa xác nhận giữ trạng thái “chưa xác nhận”, không thay bằng một giả định đã được phê duyệt.

## 2. Lịch 5 tuần/chặng theo ngày thực tế

**“Tuần 1–5” là nhãn chặng, không phải năm tuần trọn vẹn.** P0 = tính đúng/demo cốt lõi; P1 = hoàn thiện phạm vi và bằng chứng; P2 = ứng viên cải tiến có thể loại khỏi release.

| Chặng | Ngày (2026) | Mục tiêu/đầu ra tích hợp | Điều kiện qua | Ưu tiên |
|---|---|---|---|---|
| Tuần 1 | 03–08/10 (6 ngày) | Prototype login hai role, WS xác thực, process thật, event DB, dashboard, heartbeat; package và spike audio | Luồng nhỏ mục tiêu 06/10; ngày 08/10 chạy trên máy khác, replay event không trùng, mất heartbeat hiển thị UNKNOWN | P0 |
| Tuần 2 | 09–15/10 (7 ngày) | Reading rút gọn, import, full snapshot, autosave/submit/timeout và phiên ghi | End-to-end + AT01–AT11; khóa qua deadline, request phiên cũ và revision đảo thứ tự đều đúng | P0 |
| Tuần 3 | 16–22/10 (7 ngày) | Listening chính sách giới hạn; thử delta; installer | Gate delta ngày 20/10; audio lỗi/reconnect không tự replay; full snapshot luôn có đường chạy ổn định | P0 cho correctness; P1 Listening; P2 delta |
| Tuần 4 | 23–27/10 (5 ngày) | Thực nghiệm tái lập, nhiều client, regression, báo cáo Results/Discussion | Có raw/config/SHA và tách giả lập/desktop; claim khớp bằng chứng; 27/10 đóng băng chức năng | P1; sửa lỗi P0 |
| Tuần 5 | 28–31/10 (4 ngày) | Máy sạch, báo cáo cuối/Compilatio, luyện demo, bàn giao | Mục tiêu giao 30/10; ngày 31 buffer; bộ Source/Report chạy được và đã nhận trước 23:59 | P0 |

## 3. Phân công chi tiết: một backlog duy nhất

A sở hữu nghiệp vụ/DB và server network. B sở hữu JavaFX, adapter mạng client, UI giám thị, audio/đóng gói. C sở hữu **cả collector lẫn module đồng bộ monitoring phía server**, replayer và định nghĩa phép đo. Nhờ đó A không phải viết mọi endpoint; B nhận chạy polling, A chạy tải và tranh chấp, C chạy so sánh truyền dữ liệu.

Giờ của task gồm học cần thiết, triển khai, tự test và ghi note ngắn. Giờ reviewer không gộp ẩn vào owner: mỗi người có task `Tn-xR` 2 giờ/chặng. Trong đó 1 giờ review, 1 giờ phân cho contract/checkpoint tích hợp; buổi tích hợp 15 phút với 3 người tiêu tốn 0,75 giờ công. Reviewer luân vòng: C xem A, A xem B, B xem C. Task báo cáo, đo và package có giờ riêng; nếu tự test/review không đủ, dùng dự phòng và cập nhật forecast.

“Contract đầu buổi” nghĩa là producer cung cấp mẫu trước khi hoàn thành implementation; người dùng contract làm với fixture ghi rõ MOCK và phải nối thật trước gate. Không lấy fixture làm bằng chứng đã tích hợp.

### Tuần/chặng 1

| Mã | Công việc / đầu ra | Owner → Review | Giờ owner | Phụ thuộc | Nghiệm thu quan sát được |
|---|---|---|---:|---|---|
| T1-A1 | Khởi tạo server, DB và tài khoản mẫu | A → C | 4 | — | Maven build được; schema khởi tạo lại được; login đúng/sai trả kết quả; ghi phiên bản môi trường. Bao gồm học cấu hình Spring/DB. |
| T1-A2 | Xác thực REST và WebSocket | A → C | 4 | T1-A1; T1-C1 (contract đầu buổi) | Token/phiên gắn người dùng; WS thiếu/sai credential bị từ chối; kiểm tra role và attempt scope cho message; không ghi token vào log. |
| T1-A3 | Lưu event và đẩy cảnh báo giám thị | A → C | 4 | T1-A2; T1-C1 | EventId chống trùng; commit rồi ACK/push; giám thị lấy lại timeline sau reconnect; lỗi DB không báo lưu thành công. |
| T1-A4 | Presence và tích hợp prototype | A → C | 3 | T1-A2; T1-B2 | Heartbeat timeout chuyển UNKNOWN; lưu lastSeen/detectedAt; ngắt một client không làm client khác chết; chốt luồng demo và log server. |
| T1-AR | Review chéo phần B, chốt contract và tích hợp ngắn | A → C | 2 | Các đầu ra T1-B* theo buổi; không chờ hết chặng | A ghi nhận review có bằng chứng cho phần B; 1 giờ đọc PR/test/doc, 1 giờ chia các checkpoint tích hợp/giải thích. Reviewer của task này xác nhận ghi chú. Các phút phối hợp của A đã nằm ở đây. |
| T1-B1 | JavaFX shell, login, hai role | B → A | 4 | T1-A1 (contract login đầu buổi) | Hai tài khoản mở đúng giao diện; lỗi login hiển thị; tác vụ HTTP không chặn UI; bao gồm học JavaFX và callback. |
| T1-B2 | Adapter mạng, heartbeat và khóa UI | B → A | 4 | T1-B1; T1-A2 (contract sớm) | WS xác thực được; reconnect có giới hạn/backoff; khi phát hiện mất kết nối khóa thao tác; đóng app hủy worker; test bằng server thật. |
| T1-B3 | Màn giám thị tối thiểu | B → A | 4 | T1-B2; T1-A3 | Danh sách thí sinh, ONLINE/UNKNOWN, timeline; cảnh báo theo eventId không nhân đôi; trạng thái stale hiển thị riêng; nối collector qua interface. |
| T1-B4 | Spike đóng gói Windows và audio | B → A | 3 | T1-B1; T1-B2 | Ưu tiên tạo app-image có runtime; chạy login/cảnh báo trên máy thứ hai; thử audio mẫu. Ghi codec/đường dẫn lỗi; installer hoàn thiện ở T3-B5. |
| T1-BR | Review chéo phần C, chốt contract và tích hợp ngắn | B → A | 2 | Các đầu ra T1-C* theo buổi; không chờ hết chặng | B ghi nhận review có bằng chứng cho phần C; 1 giờ đọc PR/test/doc, 1 giờ chia các checkpoint tích hợp/giải thích. Reviewer của task này xác nhận ghi chú. Các phút phối hợp của B đã nằm ở đây. |
| T1-C1 | Chốt message v0 và khảo sát ProcessHandle | C → B | 4 | — | Trong giờ đầu phát mẫu login/event/heartbeat; thử process bình thường trên máy đích; ghi trường bị thiếu/quyền hạn. Contract dùng MOCK ghi nhãn rõ. |
| T1-C2 | Collector polling chạy ngoài UI | C → B | 4 | T1-C1 | Quét mặc định 1.000 ms; nhận diện tập process theo policy; thử mở/đóng ứng dụng kiểm soát; không chạy collector trên role giám thị; có start/stop sạch. |
| T1-C3 | Event, retry và queue có giới hạn | C → B | 4 | T1-C2; T1-B2 (interface); T1-A3 | EventId ổn định qua retry; một lần xuất hiện không tạo cảnh báo ở mọi poll; queue tràn được ghi nhận; gửi vào server thật, không chỉ in console. |
| T1-C4 | Log đo và note lựa chọn monitoring | C → B | 3 | T1-C1; T1-C3 | Có schema log, bộ đếm payload hai chiều và traceId; bảng ít nhất 2 cách thu thập từ tài liệu chính thức, phân biệt khảo sát với cách đã chạy; chưa ghi số đo giả. |
| T1-CR | Review chéo phần A, chốt contract và tích hợp ngắn | C → B | 2 | Các đầu ra T1-A* theo buổi; không chờ hết chặng | C ghi nhận review có bằng chứng cho phần A; 1 giờ đọc PR/test/doc, 1 giờ chia các checkpoint tích hợp/giải thích. Reviewer của task này xác nhận ghi chú. Các phút phối hợp của C đã nằm ở đây. |

### Tuần/chặng 2

| Mã | Công việc / đầu ra | Owner → Review | Giờ owner | Phụ thuộc | Nghiệm thu quan sát được |
|---|---|---|---:|---|---|
| T2-A1 | Import JSON và mở ca thi | A → C | 4 | T1-A2 | Kiểm tra ID trùng, đáp án thuộc câu, nhóm Reading/audio manifest; request của giám thị tạo được ca mẫu; không phát đáp án đúng xuống client. |
| T2-A2 | Autosave full answers theo revision | A → C | 4 | T2-A1 | Khóa attempt; kiểm tra quyền/writerEpoch/clock_timestamp sau khóa; cùng revision khác nội dung bị từ chối; ACK sau commit; hoàn tất AT03–AT05. |
| T2-A3 | Submit, timeout và chấm điểm một lần | A → C | 4 | T2-A2 | Submit mang answers cuối; timeout dùng cùng khóa; committed result bất biến; kiểm thử AT06–AT08; câu sai/trống có quy tắc chấm rõ. |
| T2-A4 | Phiên ghi và kiểm thử tranh chấp PostgreSQL | A → C | 4 | T2-A2; T2-A3 | Chuyển writerEpoch dùng cùng khóa; request cũ đang đợi không ghi sau phiên mới; AT01/AT07/AT09/AT10 trên PostgreSQL thật, dùng barrier/latch để tái lập. |
| T2-A5 | Tài liệu giao dịch và bằng chứng quyền | A → C | 2 | T2-A4 | Ghi flow lưu/nộp/hết giờ, mẫu response, log decisionAt/savedRevision; giải thích lock wait và cache chống trùng không bỏ qua authorization. |
| T2-AR | Review chéo phần B, chốt contract và tích hợp ngắn | A → C | 2 | Các đầu ra T2-B* theo buổi; không chờ hết chặng | A ghi nhận review có bằng chứng cho phần B; 1 giờ đọc PR/test/doc, 1 giờ chia các checkpoint tích hợp/giải thích. Reviewer của task này xác nhận ghi chú. Các phút phối hợp của A đã nằm ở đây. |
| T2-B1 | Reading theo nhóm đoạn văn/câu hỏi | B → A | 4 | T2-A1 (schema trước triển khai) | Hiển thị đề mẫu, chọn/đổi/xóa đáp án; một model answers chuẩn; không có correctOption trong DTO client; giao diện xử lý đề sai hợp lý. |
| T2-B2 | Autosave model và trạng thái lưu | B → A | 4 | T2-B1; T2-A2 (contract) | Revision tăng theo thay đổi; debounce có cấu hình; hiển thị đã lưu/chờ/lỗi theo ACK; snapshot bất biến cho retry; UI không đơ khi request chậm. |
| T2-B3 | Nộp và đồng hồ thi phía client | B → A | 4 | T2-B2; T2-A3 | Khóa chỉnh sửa khi nộp; payload cuối cố định; retry nguyên ID/revision/answers; nhận trạng thái đã chốt; hết giờ UI không tự kéo dài deadline server. |
| T2-B4 | Reconnect Reading và mở lại ứng dụng | B → A | 4 | T2-B3; T2-A4 | Cùng writer còn hợp lệ không giảm revision; khác writer bắt đầu từ answers/revision/state server; test trường hợp server 40/client 42; xung đột có thông báo. |
| T2-B5 | Tiến độ trên giám thị và note UX | B → A | 2 | T2-B4; T1-B3 | Màn giám thị hiển thị tiến độ đã được server xác nhận; trạng thái chờ lưu không bị gọi là đã lưu; ảnh/log minh họa luồng thật. |
| T2-BR | Review chéo phần C, chốt contract và tích hợp ngắn | B → A | 2 | Các đầu ra T2-C* theo buổi; không chờ hết chặng | B ghi nhận review có bằng chứng cho phần C; 1 giờ đọc PR/test/doc, 1 giờ chia các checkpoint tích hợp/giải thích. Reviewer của task này xác nhận ghi chú. Các phút phối hợp của B đã nằm ở đây. |
| T2-C1 | Full snapshot baseline v1 | C → B | 4 | T1-C3; T1-A4 | Cài server handler monitoring trong module riêng do C sở hữu; epoch đầu yêu cầu full; phạm vi process giống bản delta; state không viết lại lịch sử event. |
| T2-C2 | Heartbeat, event muộn và khoảng trống | C → B | 4 | T2-C1 | Kill client dẫn tới UNKNOWN; event đến muộn chỉ thêm lịch sử; snapshot mới không xóa gap; queue tràn có counter; test MT01/MT07/MT08. |
| T2-C3 | Recorder/replayer và oracle trạng thái | C → B | 4 | T2-C1 | Replay trace nhỏ biết kết quả; canonical state và event set xác định; hỗ trợ duplicate/reorder/gap; tool chạy headless, ghi rõ không phải desktop thật. |
| T2-C4 | Lịch phát process thử nghiệm và kiểm tra quan sát | C → B | 4 | T2-C3 | Harness ghi launch/exit và mốc đơn điệu; process sống ngắn/dài, lịch lệch pha; thử ground truth; log cả process không đọc đủ metadata, không tính là sạch. |
| T2-C5 | Ghi phương pháp baseline và protocol | C → B | 2 | T2-C2; T2-C4 | Ghi policyVersion, process key, scope đo, giới hạn quyền/PID reuse; đầu vào benchmark và định nghĩa miss/latency sẵn để review. |
| T2-CR | Review chéo phần A, chốt contract và tích hợp ngắn | C → B | 2 | Các đầu ra T2-A* theo buổi; không chờ hết chặng | C ghi nhận review có bằng chứng cho phần A; 1 giờ đọc PR/test/doc, 1 giờ chia các checkpoint tích hợp/giải thích. Reviewer của task này xác nhận ghi chú. Các phút phối hợp của C đã nằm ở đây. |

### Tuần/chặng 3

| Mã | Công việc / đầu ra | Owner → Review | Giờ owner | Phụ thuộc | Nghiệm thu quan sát được |
|---|---|---|---:|---|---|
| T3-A1 | API tài nguyên audio và READY | A → C | 4 | T2-A1; T1-B4 | Manifest kích thước/checksum; resource chỉ cấp cho ca được phép; READY được kiểm tra; ca chưa đủ điều kiện không start; không đưa khóa chấm vào gói tài nguyên. |
| T3-A2 | Start/gián đoạn Listening và lượt thi mới | A → C | 4 | T3-A1; T2-A3 | Server phát lệnh start có ID; không tự start lại khi retry; nhận gián đoạn, giữ dữ liệu lượt cũ; tạo lượt mới có quyền giám thị; deadline cũ không tăng. |
| T3-A3 | Hỗ trợ điểm tích hợp delta và lỗi server | A → C | 4 | T2-A4; T2-C1 | Cung cấp epoch/persistence hook; phối hợp reconnect khi server restart; lỗi DB không ACK thành công; review scope auth của state/event; không xây lại collector của C. |
| T3-A4 | Regression giao dịch và nhiều client | A → C | 4 | T3-A2; T3-A3; T3-C4 | Hai thí sinh + giám thị; gửi chéo attempt bị chặn; đồng thời save/submit/timeout; WS malformed không ảnh hưởng client khác; log tái lập được. |
| T3-A5 | Báo cáo network/concurrency | A → C | 2 | T3-A4 | Bản nháp architecture, communication, authorization và transaction; giải thích rõ framework cung cấp gì, nhóm tự làm gì; gắn evidence thật. |
| T3-AR | Review chéo phần B, chốt contract và tích hợp ngắn | A → C | 2 | Các đầu ra T3-B* theo buổi; không chờ hết chặng | A ghi nhận review có bằng chứng cho phần B; 1 giờ đọc PR/test/doc, 1 giờ chia các checkpoint tích hợp/giải thích. Reviewer của task này xác nhận ghi chú. Các phút phối hợp của A đã nằm ở đây. |
| T3-B1 | Tải audio, checksum và thử loa | B → A | 4 | T1-B4; T3-A1 (contract) | File thiếu/hỏng chặn READY; đường dẫn có dấu/khoảng trắng thử trong package; có audio mẫu trước thi; task mạng/tệp ngoài UI. |
| T3-B2 | Phát Listening từ lệnh server | B → A | 4 | T3-B1; T3-A2 | Start ID trùng không phát lại; ghi local playbackStarted; đoạn nghe và câu hỏi khớp; chỉ mô phỏng phạm vi đã chọn, không hứa đồng bộ audio tuyệt đối. |
| T3-B3 | Gián đoạn và phục hồi UI đúng chính sách | B → A | 4 | T3-B2; T2-B4 | Mất kết nối đã phát hiện/lỗi player: dừng, khóa, ghi interrupted; reconnect/mở lại không tự tiếp tục hoặc nghe lại; không ảnh hưởng chính sách Reading. |
| T3-B4 | UI giám thị điều khiển ca tối thiểu | B → A | 4 | T3-B3; T3-A2 | Import/tạo ca, READY, start, xem kết quả và đánh dấu lượt cần tổ chức lại; các quyền bị kiểm tra server; không thêm quản trị ngân hàng câu hỏi. |
| T3-B5 | Installer và audio trên máy thứ hai | B → A | 2 | T3-B4 | Đóng gói runtime/tài nguyên; thử cài/gỡ/chạy không IDE; ghi build SHA và OS; lỗi đóng gói phải có ticket, không gọi app-image là installer đã xong. |
| T3-BR | Review chéo phần C, chốt contract và tích hợp ngắn | B → A | 2 | Các đầu ra T3-C* theo buổi; không chờ hết chặng | B ghi nhận review có bằng chứng cho phần C; 1 giờ đọc PR/test/doc, 1 giờ chia các checkpoint tích hợp/giải thích. Reviewer của task này xác nhận ghi chú. Các phút phối hợp của B đã nằm ở đây. |
| T3-C1 | Encoder delta phía collector | C → B | 4 | T2-C1; T2-C3 | Sequence/baseSequence, state theo epoch; delta từ đúng base; snapshot đối soát chung lịch; cùng policy/scope với baseline; encoder có test trace nhỏ. |
| T3-C2 | Reducer delta phía server | C → B | 4 | T3-C1; T3-A3 (hook trước) | Áp dụng tuần tự; duplicate không áp dụng lại; thiếu chuỗi UNSYNCED; snapshot mới không bị delta cũ sửa; C tự viết handler/test module monitoring. |
| T3-C3 | Replay độ đúng và faults | C → B | 4 | T3-C2; T2-C3 | MT02–MT06 pass với message trùng/thiếu/đảo thứ tự/reconnect; so state và event set theo version; ít đổi/nhiều đổi/reconnect đều có trace. |
| T3-C4 | Thử tích hợp delta rồi quyết định giữ/cắt | C → B | 4 | T3-C3; T3-A3 | Chạy live hai desktop; stale epoch, mất ACK, client restart; chốt gate ngày 20/10. Nếu fail, phần giờ còn lại ổn định full snapshot và lưu failure trace. |
| T3-C5 | Báo cáo đồng bộ và giới hạn | C → B | 2 | T3-C4 | Ghi state/event tách biệt, gap không phục hồi, baseline fallback; nếu gate fail ghi rõ delta là thử nghiệm, không mô tả production-ready. |
| T3-CR | Review chéo phần A, chốt contract và tích hợp ngắn | C → B | 2 | Các đầu ra T3-A* theo buổi; không chờ hết chặng | C ghi nhận review có bằng chứng cho phần A; 1 giờ đọc PR/test/doc, 1 giờ chia các checkpoint tích hợp/giải thích. Reviewer của task này xác nhận ghi chú. Các phút phối hợp của C đã nằm ở đây. |

### Tuần/chặng 4

| Mã | Công việc / đầu ra | Owner → Review | Giờ owner | Phụ thuộc | Nghiệm thu quan sát được |
|---|---|---|---:|---|---|
| T4-A1 | Chạy bộ tranh chấp và lỗi persistence | A → C | 4 | T3-A4 | Lưu kết quả AT01–AT11 cùng fixture/seed; kiểm tra DB failure và timeout; sửa lỗi trong phạm vi đã có, không mở chức năng. |
| T4-A2 | Đo tải 1/5/10 client giả lập | A → C | 4 | T4-A1; T3-C4 | Ba lần lặp mỗi mức, log thông số máy; concurrency/error/RTT/server CPU-memory; dừng tăng tải khi lỗi, không gọi 10 client giả là 10 desktop. |
| T4-A3 | Phân tích server và viết phần phương pháp | A → C | 4 | T4-A2; T3-A5 | Bảng kết quả thật, cấu hình test, giải thích giao dịch/chờ khóa; rà quyền khi final và retry cache; ghi nguồn thư viện/kế thừa. |
| T4-AR | Review chéo phần B, chốt contract và tích hợp ngắn | A → C | 2 | Các đầu ra T4-B* theo buổi; không chờ hết chặng | A ghi nhận review có bằng chứng cho phần B; 1 giờ đọc PR/test/doc, 1 giờ chia các checkpoint tích hợp/giải thích. Reviewer của task này xác nhận ghi chú. Các phút phối hợp của A đã nằm ở đây. |
| T4-B1 | Regression desktop trên máy khác | B → A | 4 | T3-B5; T3-A4 | Installer, Reading, Listening, restart và ngắt mạng; log UI/audio, OS và build; AT11, LT01–LT04, IT01–IT04. |
| T4-B2 | Chạy polling benchmark với C cung cấp harness | B → A | 4 | T4-B1; T4-C1 | 4 chu kỳ, 3 lần lặp, cùng kịch bản/pha khởi chạy; B chịu trách nhiệm chạy desktop thật và lưu raw; C review phương pháp trong slot review. |
| T4-B3 | Viết Implementation và hướng dẫn chạy | B → A | 4 | T4-B1; T3-B4 | Ảnh màn hình từ bản thật; README cài/run/test và chính sách gián đoạn; phần nhóm câu Reading/audio; video dự phòng có ngày/build rõ. |
| T4-BR | Review chéo phần C, chốt contract và tích hợp ngắn | B → A | 2 | Các đầu ra T4-C* theo buổi; không chờ hết chặng | B ghi nhận review có bằng chứng cho phần C; 1 giờ đọc PR/test/doc, 1 giờ chia các checkpoint tích hợp/giải thích. Reviewer của task này xác nhận ghi chú. Các phút phối hợp của B đã nằm ở đây. |
| T4-C1 | Chốt bộ dữ liệu và kiểm tra phép đo | C → B | 4 | T2-C4; T3-C4 | Kiểm checksum traces, encoder/tổng byte, run metadata, CPU normalization; warmup/seed/lịch snapshot; giao harness polling cho B trước 24/10. |
| T4-C2 | Chạy full-vs-delta hoặc benchmark baseline | C → B | 4 | T4-C1 | Gate pass: 2 mode x 3 trace x 3 lần; correctness trước efficiency; gate fail: full/reconnect và bảng lỗi delta, không so hiệu suất như bản đúng. |
| T4-C3 | Phân tích monitoring và đóng góp | C → B | 4 | T4-C2; T4-B2 | Tách state bytes/tổng bytes, polling miss và chi phí; vẽ biểu đồ từ raw; kết luận cả trường hợp delta không lợi; bảng contribution-evidence không tự bảo đảm novelty. |
| T4-CR | Review chéo phần A, chốt contract và tích hợp ngắn | C → B | 2 | Các đầu ra T4-A* theo buổi; không chờ hết chặng | C ghi nhận review có bằng chứng cho phần A; 1 giờ đọc PR/test/doc, 1 giờ chia các checkpoint tích hợp/giải thích. Reviewer của task này xác nhận ghi chú. Các phút phối hợp của C đã nằm ở đây. |

### Tuần/chặng 5

| Mã | Công việc / đầu ra | Owner → Review | Giờ owner | Phụ thuộc | Nghiệm thu quan sát được |
|---|---|---|---:|---|---|
| T5-A1 | Ghép và rà báo cáo kỹ thuật | A → C | 3 | T4-A3; T4-B3; T4-C3 | Ghép phần A/B/C theo Instruction mục 8; thống nhất các claim với số liệu; Abstract/Conclusion viết sau Results; không tự điền kết quả chưa đo. |
| T5-A2 | Compilatio và rà trích dẫn | A → C | 3 | T5-A1 | Nộp bản báo cáo qua tài khoản nhóm, lưu kết quả; xử lý nguồn/trích dẫn đúng; dùng ngưỡng theo tài liệu môn; không lách kiểm tra. Thời gian chờ hệ thống không tính giờ công. |
| T5-A3 | Chốt gói và bàn giao trước hạn | A → C | 3 | T5-A2; T5-B2; T5-C2 | Tên Source/Report, đúng bản Compilatio, danh sách file và checksum; bàn giao lớp trưởng ngày 30/10 mục tiêu; 31/10 chỉ buffer có xác nhận nhận file. |
| T5-AR | Review chéo phần B, chốt contract và tích hợp ngắn | A → C | 2 | Các đầu ra T5-B* theo buổi; không chờ hết chặng | A ghi nhận review có bằng chứng cho phần B; 1 giờ đọc PR/test/doc, 1 giờ chia các checkpoint tích hợp/giải thích. Reviewer của task này xác nhận ghi chú. Các phút phối hợp của A đã nằm ở đây. |
| T5-B1 | Máy sạch theo README | B → A | 3 | T4-B3; T5-A1 | Người thử không dựa IDE; build server, DB, cài client, login/thi/monitoring; ghi bước lỗi và sửa hướng dẫn; không coi máy cùng tác giả đã cài sẵn là máy sạch. |
| T5-B2 | Chốt release client và tài nguyên | B → A | 3 | T5-B1 | Installer/app runtime, đề/audio hợp lệ, cấu hình mẫu; checksum, phiên bản; bỏ credential thật và đường dẫn cá nhân; chạy smoke sau lần đóng gói cuối. |
| T5-B3 | Luyện demo và bảo vệ client/network | B → A | 3 | T5-B2; T5-A1 | Kịch bản 5–7 phút, thao tác thật, video dự phòng; B giải thích UI threads/audio/revision; A giải thích flow B, C hỏi lỗi phiên để review chéo. |
| T5-BR | Review chéo phần C, chốt contract và tích hợp ngắn | B → A | 2 | Các đầu ra T5-C* theo buổi; không chờ hết chặng | B ghi nhận review có bằng chứng cho phần C; 1 giờ đọc PR/test/doc, 1 giờ chia các checkpoint tích hợp/giải thích. Reviewer của task này xác nhận ghi chú. Các phút phối hợp của B đã nằm ở đây. |
| T5-C1 | Tái lập một run và kiểm tra evidence | C → B | 3 | T4-C3 | Từ command/seed tạo lại một trace/run, đối chiếu định nghĩa metric; không yêu cầu số hiệu năng giống tuyệt đối; khóa raw và đánh dấu excluded run có lý do. |
| T5-C2 | Rà contribution và mục giới hạn | C → B | 3 | T5-C1; T5-A1 | Mỗi claim trỏ test/log/chart/SHA; đối chiếu nguồn trực tiếp với phần tổng hợp; riêng delta fail không tuyên bố đã tối ưu; hoàn thiện phụ lục protocol. |
| T5-C3 | Luyện bảo vệ monitoring và luồng chung | C → B | 3 | T5-C2; T5-B3 | C giải thích bỏ sót polling, state/history, ACK/epoch/bytes; A và B lần lượt giải thích lại; lưu câu chưa trả lời và sửa trước chốt gói. |
| T5-CR | Review chéo phần A, chốt contract và tích hợp ngắn | C → B | 2 | Các đầu ra T5-A* theo buổi; không chờ hết chặng | C ghi nhận review có bằng chứng cho phần A; 1 giờ đọc PR/test/doc, 1 giờ chia các checkpoint tích hợp/giải thích. Reviewer của task này xác nhận ghi chú. Các phút phối hợp của C đã nằm ở đây. |

### Điểm bàn giao contract

| Hạn nội bộ | Producer → Consumer | Bản phải thống nhất | Kiểm tra tích hợp |
|---|---|---|---|
| 03/10, trong giờ đầu | C + mẫu auth A → B/A | Login, role/attempt scope, WS event/ACK/heartbeat v0; lỗi và traceId | Đọc cùng mẫu, không cần chờ server hoàn chỉnh |
| 04/10 trước buổi ghép | B → C; A → B | Interface collector→network; HTTP/WS credential, subscription scope | Event kiểm soát đi qua adapter thật |
| 09/10 đầu buổi | A → B/C | Import đề, answers/revision, requestId, writerEpoch, deadline/state và lỗi | Fixture khớp DTO, không có correct answers trên client |
| 12/10 | A → B | Trả trạng thái đã chốt, takeover writer, reconnect state | Tích hợp Reading rồi inject delayed autosave |
| 16/10 đầu buổi | A + B → C | Audio manifest, READY/start/interrupted, policy Listening | Một audio trong package, lỗi checksum chặn start |
| 16/10; khóa 18/10 | C → A/B | Monitoring epoch/base/sequence, state/event độc lập, metric definition | Replayer dùng chung input và oracle |

Chỉ thay contract khi owner ghi thay đổi + reviewer duyệt + consumer cập nhật fixture trong cùng nhánh tích hợp. Không thêm schema registry hoặc hệ thống phức tạp cho việc này.

## 4. Tổng giờ, dự phòng và phân tải

Công suất một chặng = số ngày × giờ/tuần ÷ 7. Số dưới làm tròn 2 chữ số; tổng tính từ số chưa làm tròn.

| Chặng | Ngày | Trần việc/người ở 15h/tuần, đã trừ 20% | Gross/người ở 25h/tuần | A giao | B giao | C giao | Dư mỗi người ở 25h |
|---|---:|---:|---:|---:|---:|---:|---:|
| T1 | 6 | 10,29 | 21,43 | 17 | 17 | 17 | 4,43 |
| T2 | 7 | 12,00 | 25,00 | 20 | 20 | 20 | 5,00 |
| T3 | 7 | 12,00 | 25,00 | 20 | 20 | 20 | 5,00 |
| T4 | 5 | 8,57 | 17,86 | 14 | 14 | 14 | 3,86 |
| T5 | 4 | 6,86 | 14,29 | 11 | 11 | 11 | 3,29 |
| **Tổng** | **29** | **49,71** | **103,57** | **82** | **82** | **82** | **21,57** |

82 giờ/người gồm **72 giờ task kỹ thuật/học/tự test/tài liệu/đo** và **10 giờ review/checkpoint**. Tổng 246 giờ. Dự phòng 64,71 giờ cả nhóm không phải backlog chức năng mới; dành lỗi package, học thiếu, sửa race, lượt chạy bị hỏng và nộp bài. Thời gian báo cáo đã có T1 note → T2/T3 draft → T4 results → T5 ghép và rà.

Tải số học bằng nhau nhưng rủi ro không bằng nhau: A dễ mắc ở giao dịch, B ở audio/package, C ở state sync. Điều chỉnh đã áp dụng: C tự làm reducer server; B viết UI giám thị và chạy polling; A chạy tải; chia viết báo cáo theo module. Không chuyển mọi “việc lẻ” sang C.

Ngày 08/10 mỗi người cập nhật actual_hours và remaining_estimate. Nếu giờ đã dùng + giờ còn lại vượt 80% gross còn khả dụng, lấy việc tùy chọn ra trước khi tiêu dự phòng. Không tự san nhiệm vụ làm người khác quá tải; đổi owner cụ thể trong tracker và giữ reviewer khác người.

## 5. Chặng đầu theo ngày/buổi và demo với thầy

### 24 giờ đầu: 03/10

| Người | Buổi tập trung 4 giờ | Đầu ra giao ngay |
|---|---|---|
| A | T1-A1: DB/seed, server boot, login mẫu; thống nhất mẫu auth trong giờ đầu với C | Lệnh chạy server, tài khoản test, schema/reset, login response |
| B | T1-B1: shell JavaFX/login/role; 45 phút cuối thử tạo app-image để phát hiện trở ngại sớm | Cửa sổ chạy được, HTTP ngoài UI, log lỗi package nếu có |
| C | T1-C1: mẫu protocol giờ đầu; ProcessHandle probe trên máy đích; note trường bị thiếu | Message v0, tên một ứng dụng thực sự quan sát được, giới hạn quyền |

Slot review/checkpoint đầu tiên 15 phút/người nằm trong T1-xR, ngoài 4 giờ task nói trên. A xác nhận công suất/ngày gặp thầy; B xác nhận máy thứ hai; C tổng hợp câu hỏi phạm vi. Chưa biết IP thật thì để cấu hình, không hard-code localhost làm cách chạy LAN.

### Phân bố giờ task chặng 1

Đây là lịch mục tiêu 25 giờ/tuần. Mỗi task 4 giờ chia được thành hai buổi 2 giờ. Phần T1-xR phân 0,25 giờ ngày 03; 0,5 ngày 04; 0,5 ngày 06; 0,75 ngày 08, đủ 2 giờ/người.

| Ngày | A | B | C | Kết quả tích hợp |
|---|---|---|---|---|
| 03/10 | A1 4h | B1 4h, gồm package spike | C1 4h | Login/schema/message thống nhất; biết process có đọc được |
| 04/10 | A2 4h | B2 3h + B3 1h khung dashboard | C2 4h | Hai role kết nối WS có auth; collector nối interface |
| 05/10 | A3 3h | B2 1h + B3 2h | C3 3h | Process → server lưu → giám thị nhận cảnh báo đầu tiên |
| 06/10 | A3 1h + A4 1h | B3 1h + B4 1h package | C3 1h + C4 1h log | Demo nhỏ; disconnect/heartbeat thử với cấu hình ngắn; mang package sang máy khác nếu spike đã qua |
| 07/10 | A4 1h | B4 1h | C4 1h | Hoàn thiện timeout/lỗi, chạy máy khác, audio mẫu |
| 08/10 | A4 1h | B4 1h | C4 1h | Gate prototype, báo cáo trở ngại và forecast; diễn tập 5–7 phút |
| **Tổng task + review** | **15 + 2 = 17h** | **15 + 2 = 17h** | **15 + 2 = 17h** | Dự phòng khoảng 4,43h/người chưa xếp |

**Demo tối thiểu phải có:** backend + DB chạy; 1 thí sinh và 1 giám thị đăng nhập thật; mở ứng dụng được quan sát; event được DB commit rồi lên dashboard; mất liên lạc chuyển trạng thái; package chạy trên máy khác. Mốc 06/10 là mục tiêu luồng nhỏ, gate 08/10 mới kiểm đủ. Nếu thầy gặp trước khi gate xong, trình bày chính xác phần còn thiếu.

**Nếu còn giờ trong task/dự phòng nhỏ:** thử 2 thí sinh; gửi lại event cùng ID; phát 1 audio mẫu; chụp log ACK và event count. Chưa mở rộng sang UI thi đầy đủ hoặc dashboard đẹp trong chặng 1.

**Nếu bị chặn:** collector không đọc tên process thì chọn một ứng dụng kiểm soát đọc được và công bố giới hạn; fixture chỉ minh họa protocol với nhãn MOCK. Nếu package lỗi, demo từ IDE được nhưng ghi “chưa qua gate package”, kèm lỗi và người xử lý. Nếu mạng buổi gặp lỗi, dùng video quay từ lần chạy thật có ngày/build; không nhận đó là demo live. Không có thành phần chạy thì trình bày thiết kế + log lỗi + spike, không mô phỏng kết quả như đã chạy.

### Kịch bản 6 phút (co giãn 5–7 phút)

| Phút | Người | Nội dung và thao tác |
|---|---|---|
| 0:00–0:45 | A | Bài toán: thi rút gọn, server chốt bài, process là tín hiệu nghi vấn; mục tiêu buổi này |
| 0:45–1:30 | C | Một hình kiến trúc: client thí sinh → server/DB → giám thị; HTTP và WS; chưa có recovery toàn diện |
| 1:30–3:20 | B | Mở package, hai tài khoản; mở ứng dụng thử; chỉ ra event trên giám thị |
| 3:20–4:10 | C | Cho xem eventId trong log/DB; ngắt client và chờ timeout, giải thích UNKNOWN không phải gian lận |
| 4:10–5:00 | A | Đã chạy gì/chưa chạy gì; 2 luồng rủi ro đang kiểm: chốt đáp án và đồng bộ monitoring |
| 5:00–6:00 | A chủ trì, B/C trả lời phần mình | Xin ý kiến phạm vi rút gọn, đóng góp polling/delta, chính sách Listening; chốt việc tuần tới |

Chỉ cần 1 trang kiến trúc, 1 trang tiến độ và app/log thật. Không dành giờ tạo bộ slide dài. Người nói không phải người duy nhất biết phần đó; đổi người giải thích khi tập bảo vệ.

## 6. Tích hợp, kiểm thử và mốc cắt

03/10 ghép contract; 04/10 thử WS; 06 và 08/10 demo. Các chặng sau ghép 10, 12, 15; 17, 19, 20, 22; 24, 27; 29 và 30/10. Mỗi checkpoint 10–15 phút/người nằm trong slot review; thời gian chạy/sửa bằng chứng nằm trong task chức năng hoặc test tương ứng. Mỗi người ghi ngắn: SHA, scenario, thực tế, lỗi, owner sửa.

| Gate | Chủ trì / reviewer | Bằng chứng để qua | Khi không qua |
|---|---|---|---|
| 04/10 nền tảng | B / A | JavaFX build/package spike, WS auth, ProcessHandle probe | Dừng chỉnh UI; dùng dự phòng để sửa đúng blocker, không đổi cả stack |
| 08/10 prototype | A / C | Event commit/ACK, không trùng, heartbeat, package máy khác | Bỏ tính năng phụ vốn chưa giao; tính lại giờ. Nếu vẫn 15h, xin thu hẹp scope ngay |
| 15/10 exam correctness | A / C | AT01–AT11 đủ bằng chứng, Reading đầy đủ một lượt, full state ổn định | Tạm dừng delta, C hỗ trợ harness/race; không gọi save thành công khi chưa commit |
| **20/10 delta correctness** | C / B | MT02–MT06, oracle state/event bằng baseline, fault/reconnect hợp lệ | Tắt delta ở cấu hình mặc định; phần giờ T3-C4/C5 còn lại ghi failure và ổn định full; không kéo gate sang tuần cuối |
| 22/10 Listening/package | B / A | LT01–LT04 và máy thứ hai; full snapshot usable | Nếu không thể sửa bằng buffer, báo phạm vi Listening chưa đạt và xin điều chỉnh; không tự nhận complete |
| 27/10 feature freeze | C / B | Raw/config + bản report Results; không có lỗi mất đáp án/phân quyền chưa xử lý | Chỉ sửa lỗi P0 và tài liệu; không thêm tối ưu hay UI |
| 30/10 release | A / C | Source/Report, Compilatio, smoke máy sạch, người nhận xác nhận | Dùng 31/10 làm buffer; không bỏ test quan trọng để kịp tuyên bố chạy |

Thứ tự cắt: (1) focus; (2) lệnh cảnh báo tùy biến; (3) admin nâng cao/UI trang trí; (4) tối ưu phụ và tăng số client; (5) delta khỏi bản chính khi gate fail hoặc lợi ích không đủ; (6) Listening chỉ thu hẹp sau trao đổi lại vì thuộc scope đã chốt. 1–3 đã nằm ngoài backlog này, nên không được tính thêm “giờ tiết kiệm” khi cắt lần nữa. Không cắt quyền truy cập, revision, deadline, epoch, ACK sau commit hoặc kiểm chứng package.

Full snapshot là lựa chọn vận hành nếu delta sai, không tái lập được hoặc lợi ích không đáng chi phí CPU/độ trễ/độ phức tạp. Delta đúng nhưng không có lợi vẫn là kết quả có thể báo cáo; không ép đổi kết luận. Khi delta fail, giữ bằng chứng polling trade-off và cơ chế giữ state/event đúng qua lỗi. **Mức đủ của Novelty phải được thầy xác nhận; thử nghiệm thất bại tự nó không hoàn thành mục Novelty.**

Checklist kỹ thuật chi tiết ở 03_KIEM_THU_VA_THUC_NGHIEM.md; hợp đồng chuẩn ở 02_HOP_DONG.md. Không chờ toàn bộ test hoàn thành mới ghép: happy path nhỏ từ T1 rồi thêm fault tests quanh luồng đang chạy.

## 7. Checklist bàn giao và trách nhiệm

Các mục dưới là điều kiện cần kiểm, chưa đánh dấu hoàn thành.

| Hạng mục | Owner / reviewer | Điều kiện bàn giao |
|---|---|---|
| Source, dependencies, cấu hình | A / C | Source build được; pin phiên bản; schema/migration/reset; env mẫu; không có bí mật thật |
| Dữ liệu và bộ cài | B / A | JSON/audio có quyền sử dụng, checksum; tài khoản demo; installer Windows và runtime; hướng dẫn LAN/firewall/port |
| README | B / A | Kiến trúc, requirements, install, DB, run server/client/nhiều client, test, đo, limitations; người khác chạy theo được |
| Contract và ảnh/sơ đồ | C / B | Message/errors, auth, save/submit/timeout, epochs; hình kiến trúc và sequence khớp implementation |
| Log, raw, summary, chart | C / B | Mỗi run có config/SHA/máy/seed/thời gian; file raw không chỉnh số; phân biệt simulation và desktop; không có kết quả bịa |
| Báo cáo | A ghép / C rà | A viết server/network/concurrency; B viết client/audio/package; C viết experiment/monitoring; cùng hiểu kết quả |
| Nội dung paper | A / C | Title, Abstract, Keywords, Intro, Related Work, Problem, Approach, Architecture, Network, Implementation, Setup, Results, Discussion, Contributions, Limitations, Future Work, Conclusion, References, Appendix |
| Compilatio | A / C | 1 báo cáo; tên NhomXX_TOEICMonitor_Report.docx; đúng ngưỡng hướng dẫn học phần; giữ bản và kết quả kiểm tra; không tự ghi Passed |
| Source/Report cuối | A / C | NhomXX_TOEICMonitor/Source và /Report; ghi liên hệ bản nguồn NhomXX_TOEICMonitor_Source nếu dùng thư mục riêng; đúng report đã nộp |
| Kênh giao | A / C | Theo Submission: lớp trưởng thu vào USB; xác nhận thông báo mới và Drive nếu được yêu cầu; có biên nhận/mốc bàn giao |
| Luyện bảo vệ | B / A | A giải thích lock/deadline/auth, B giải thích UI/revision/audio, C giải thích monitoring/đo; mỗi người vẽ lại flow của người khác |

Instruction §10 ghi “Similarity + Plagiarism ≤20%”, các mục sau rút gọn thành “Similarity ≤20%”. Giữ nguyên căn cứ môn, đối chiếu chỉ số hệ thống/thông báo giảng viên, không tự diễn giải thành cách vượt kiểm tra. Nộp Compilatio mục tiêu 29/10 để còn xử lý vấn đề trước 30–31/10.

**Tình trạng lúc lập kế hoạch:** chưa xác nhận tên người/ngày gặp thầy/công suất tăng; chưa có code hay bằng chứng test. Việc kế tiếp là A/B/C bắt đầu đúng buổi 03/10 và ghi giờ thực tế, không viết thêm một vòng kiến trúc tổng thể.
