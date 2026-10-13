# Ghi nhận biên tập bằng chứng (lượt chạy trên `b54a8ad`)

Thư mục này là bản đã biên tập để đưa lên GitHub. Không số liệu, kết quả hay nhận định nào bị đổi.

## Đã che gì

- Ảnh dashboard giám thị có bảng "Process hiện tại" và "Cảnh báo process": che cột tên tiến trình, PID và giờ khởi động của các ứng dụng đang chạy trên máy thử bằng một khối màu có chữ `REDACTED`. Phần còn lại của ảnh giữ nguyên.
- Văn bản: tên ba tiến trình thuộc policy được thay bằng mô tả chung.

- Ảnh `37-…` được đặt tên theo đúng nội dung: dashboard đang báo dữ liệu cũ sau khi server bị dừng có chủ đích, không phải bằng chứng roster ACTIVE-only.

## Ảnh đã che

| Ảnh | SHA-256 bản gốc (local) | SHA-256 bản trong thư mục này |
|---|---|---|
| `screens/11-proctor-process-state.png` | `9d206dd5923225a89565eaf6b4a91a77b526f16b42868807c2f9140038edc6a2` | `3955a661e2cd9a2723cd5a77480f32fa3ca9efda50e490c3e2530fc786dfae39` |
| `screens/12-proctor-warning-tab.png` | `415aca146cacdf5b5e28150f7256493c83786ee8f92528ef543d7b699948b47b` | `85fd3876a7bf65752d1701a19ace978b6c65fbc0d3e77f4cd490a8a9d5ddf024` |

## Không che

Tên tài khoản seed `MOCK`, mã lượt thi, PID của JVM client/server trong log thao tác, cổng loopback và giờ UTC được giữ vì chúng là dữ liệu của lượt thử, không phải dữ liệu cá nhân.

## Bản gốc

Bản gốc chưa che nằm trong nhánh cục bộ `integrate/b-accept-01-20261013` trên máy build. Nhánh đó không dùng để push.
