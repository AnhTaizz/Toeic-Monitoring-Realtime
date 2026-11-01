# Sửa phục hồi READY và bảo vệ START — 01/11/2026

## Thay đổi

- Server mặc định giữ visual READY 30 giây (`VISUAL_LEASE_MS`, trước đây 6 giây); client vẫn gửi ATTEST mới khoảng 2 giây/lần, sau khi nhịp trước hoàn tất.
- Khi lease cũ hết hạn, một ATTEST **mới** được kiểm tra đầy đủ claims ảnh, audio READY, graph/media thực tế, writer/epoch và token trước khi cấp lease mới. Không coi bản READY hết hạn là còn hiệu lực. Retry requestId cũ đã hết hạn vẫn bị từ chối; requestId xung đột, takeover, manifest sai, token hết hiệu lực vẫn bị chặn.
- Client giữ ảnh đã kiểm tra cho cùng scope khi gặp `INVALID_STATE`, lấy lại preparation và gửi ATTEST mới. Khi epoch/manifest đổi, client vẫn tải và chuẩn bị lại theo scope mới. Mất mạng hiển thị chưa xác minh; không suy READY từ lỗi.
- Phòng chờ hiển thị "Đang xác nhận với máy chủ…" khi đang gửi yêu cầu; không nhấp nháy thành "Chưa sẵn sàng" chỉ vì đang báo lại. Thông báo Listening bị khóa diễn đạt là thiếu READY hợp lệ lúc START, không tự kết luận người dùng vào phòng muộn.
- Client giám thị mặc định gửi `requireAllReady=true` trong START. Nếu READY mất hiệu lực hoặc có người mới chưa sẵn sàng, server trả 409 **trước** khi ghi START/deadline/cờ gián đoạn; giám thị nhận thông báo và đọc lại readiness.
- Chỉ sau khi giám thị xác nhận cảnh báo có người chưa sẵn sàng, màn Phòng thi mới gửi `requireAllReady=false`. Ý định này được giữ từ hộp thoại, không suy lại từ snapshot có thể đổi trong khi hộp thoại mở. Retry START chưa rõ kết quả giữ nguyên requestId/manifest/flag; ca đã bắt đầu trả lại startId cũ.
- Readiness hiển thị cho phòng ACCOUNT_PIN kiểm tra cùng ràng buộc token admission của attempt như START.

## Giới hạn và tính nhất quán

Không migration, không reset DB, không đổi đáp án/điểm hay deadline của ca đã bắt đầu. Không bỏ kiểm tra hash/media để tăng tốc. Phép đo chẩn đoán cục bộ trước đó không chứng minh media vượt 6 giây trên VPS; thay đổi này sửa lỗi phục hồi đã tái hiện, không gán nguyên nhân chưa đo cho phiên thật.

`ListeningStartRequest` thêm field tùy chọn Boolean `requireAllReady`. Null/không gửi giữ hành vi cũ cho client cũ. Vì vậy **phải cập nhật server và client giám thị** để có bảo vệ mới; cập nhật client thí sinh để có luồng/phản hồi UI mới. Không tự triển khai lên VPS trong phiên này.

## Cập nhật bản chạy

1. Dừng server cũ bình thường, dùng `server.jar` mới tại nơi thực sự phục vụ URL đăng nhập. Nếu là VPS/Docker, cập nhật JAR/image trên VPS; mở EXE hoặc BAT ở Windows không thay thế container từ xa.
2. Giữ DB, media, PIN key và cấu hình hiện có. Mặc định mới là 30000 ms; nếu đang đặt riêng `VISUAL_LEASE_MS=6000` hoặc `--toeic.visual.lease-ms=6000`, đổi thành 30000 hoặc bỏ override để dùng mặc định.
3. Mở `ToeicMonitor.exe` trong bộ mới cho cả giám thị và thí sinh. Bộ cũ `TOEIC-Admin-c170798` vẫn chạy code cũ.
4. Kiểm tra bằng phòng mới: hoàn tất chuẩn bị, đợi Sẵn sàng rồi START. Lượt đã mang `NOT_READY_AT_START`/đã gián đoạn không được tự phát lại Listening; cần ca/lượt mới nếu tổ chức thi lại.

## Kiểm chứng

Các regression server chạy PostgreSQL/Flyway/HTTP thật với dữ liệu đề MOCK. Lease hết hạn được tạo bằng SQL tuân thủ constraint để kiểm tra xác định, không chờ bằng sleep. Bao gồm: receipt cũ không gia hạn, ATTEST mới phục hồi, strict START rollback hoàn toàn khi READY đổi, START sau phục hồi cấp Listening, retry không START hai lần, takeover/revoke/manifest/media guards và việc giám thị chủ động cho bắt đầu khi chưa đủ READY. Client kiểm tra không dựng lại ảnh cùng scope, phục hồi sau lỗi tạm thời, giữ flag START qua retry/hộp thoại và UI phòng chờ.

Kết quả cuối cùng và receipt được ghi sau lượt kiểm chứng chốt trong `ready-recovery-verification.json`.
