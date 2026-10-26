# USB thật trên ứng dụng — 24/10/2026

**Đã nối vào app và người dùng xác nhận thấy cả cắm/rút USB trên Timeline giám thị.**
Nhánh `codex/usb-app-integration-20261024`, base `acc1f82`; working tree chưa commit.
Không dùng codex-with-chatgpt theo opt-out. Không đổi wire/schema/deadline hay tracker.

## Thay đổi

- Tách provider CIM/model/resource sang module nội bộ `windows-observer`, client và
  probe dùng chung. Giữ classification/parser; không thêm thư viện ngoài/native.
  Provider giải phóng handle đã chết thay vì giữ metadata qua cả bài thi.
- Candidate hook dùng cùng collector lease/context/sequence/status emitter/queue;
  chỉ scan sau capability/heartbeat hiện tại. Pause khi mất kết nối, owner xác thực
  lại rồi resume, chặn callback generation cũ. Cleanup ngoài FX; control worker giữ
  JVM đến khi cleanup xong. Proctor không khởi tạo collector/USB source.
- Gate USB mặc định OFF, cadence/query timeout cấu hình được. Thí sinh thấy thông báo
  trước khi vào bài. Giám thị dùng bảng/lọc nguồn hiện có, không thêm luồng UI riêng.
- Demo hỗ trợ source chưa commit bằng inventory/hash trước/sau build. Mật khẩu không
  ghi vào file hướng dẫn; chỉ dùng account MOCK phát triển trong DB loopback riêng.
  Server artifact có tên cấu hình, mặc định không đổi; demo tạo tên riêng để không
  đụng JAR của server cũ. Thay đổi chạm C/B và cấu hình đóng gói thuộc A.

## Kiểm chứng đã chạy

| Lượt | Kết quả thật |
| --- | --- |
| Focused trước các fix cleanup cuối | 17 protocol + 20 provider + 150 client = 187 PASS, không skip |
| Test phase của `mvn verify` sau fix | 1342 PASS, 169 SKIP, 0 FAIL/ERROR trong protocol/provider/spike/client/server; 143 XML được lưu/hash |
| REAL production hook `usb-300be357ef1749f4` | 1 PASS: CIM READY/count0 → candidate hook → WS/PG/ACK/history/proctor UI; reconnect/cleanup PASS; không USB transition vật lý ở lượt tự động này |
| Desktop `usb-c26aa31b588a43f2` | Maven package PASS, hai JVM chạy JAR mới; USB thật CONNECTED → REMOVED → CONNECTED, người dùng nhìn thấy trong app |

`mvn verify` dừng ở **repackage**, không được gọi toàn lệnh PASS: JVM server cũ PID56484
đã chạy từ 20:42 giữ default JAR; package retry cùng tên vẫn lỗi. Không dừng JVM cũ.
Sau khi thêm tên artifact riêng, desktop package toàn reactor PASS (skipTests).
Module test STUDY4 chưa được chạy trong verify bị ngắt; không cộng vào kết quả này.
Log lỗi được giữ cùng log thành công, không xóa raw để đạt PASS.

Desktop thực: các giờ dưới đây là UTC+7, ngày 24/10/2026:

| Giờ client quan sát | Event server đã ghi nhận | USB count |
| --- | --- | --- |
| 21:35:58.056866 | USB_STORAGE_CONNECTED | 1 |
| 21:36:09.392931 | USB_STORAGE_REMOVED | 0 |
| 21:36:19.243767 | USB_STORAGE_CONNECTED | 1 |

REMOVED tham chiếu đúng ref của CONNECTED đầu, CONNECTED sau có ref mới. History
thuộc lượt TEST đang SYNCED. Người dùng xác nhận: “Đã thấy cả cắm và rút USB trên ứng dụng”.
Không có timestamp thao tác tay chính xác để đo latency vật lý. Không đọc nội dung/serial.

## Evidence và giới hạn

[Receipt sinh từ raw/hash](receipt.json); raw ignored tại
`target/usb-app-verification-20261024`, `target/usb-integrated-usb-300be357ef1749f4` và
`target/usb-integrated-usb-c26aa31b588a43f2`. Snapshot desktop lúc xác nhận được đóng
băng riêng trước khi hash, không hash nhầm file polling vẫn thay đổi.

Ứng dụng còn mở tại thời điểm xác nhận; cleanup desktop phải đọc receipt sau khi hai
app đóng hoặc hết ngân sách 60 phút. Không tuyên bố cleanup của lượt live đã PASS.
Sau build live chỉ sửa launcher để cả hai cửa sổ hỗ trợ flag USB, tránh phụ thuộc thứ
tự đăng nhập; chỉ flow CANDIDATE xác thực mới tạo source. Java/JAR đã xem không thay đổi.

Chưa nghiệm thu đầy đủ H01–H12, PnP comparison/ADR/retention/LAN/human owner review.
Không tự đánh MON-02/QA-04 DONE hoặc ký thay owner. Các gate vẫn mặc định OFF ngoài
lượt TEST được ủy quyền; FULL mặc định. [Runbook](../../docs/USB_APP_INTEGRATION.md).
