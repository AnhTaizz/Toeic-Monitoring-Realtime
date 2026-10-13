# Ghi nhận biên tập bằng chứng (lượt chạy trên `6816ff5`)

Thư mục này là bản đã biên tập để đưa lên GitHub. Không số liệu, kết quả hay nhận định nào bị đổi.

## Đã che gì

- Ảnh dashboard giám thị có bảng "Process hiện tại" và "Cảnh báo process": che cột tên tiến trình, PID và giờ khởi động của các ứng dụng đang chạy trên máy thử bằng một khối màu có chữ `REDACTED`. Phần còn lại của ảnh giữ nguyên.
- Văn bản: tên ba tiến trình thuộc policy được thay bằng mô tả chung.

- Hai file log thao tác đổi đuôi thành `.txt` (`ui-actions.txt`, `ws-relay.txt`) vì `.gitignore` của repo bỏ qua `*.log`; nội dung không đổi.
- Thư mục `harness/` của lượt này không đưa vào bản công khai; script dùng chung nằm ở `../b-accept-01-fix-20261013/harness/`.

## Ảnh đã che

| Ảnh | SHA-256 bản gốc (local) | SHA-256 bản trong thư mục này |
|---|---|---|
| `screens/11-proctor-warnings.png` | `4ba78b2e7e59c850cba282a755f5bb2722426cfbf76ae2715f1c02d346a2288a` | `3c47c1ad75759e62f2c73c31c83b6e81464c33b0d2cafd1aad1aa42a1c434577` |
| `screens/12-proctor-warning-tab.png` | `20197cf98e9ea9d2b1c78af4bd7346b9d19c6b01d4e358149224045a4edf1830` | `57c17e8b71598e61bc2895ef9d5f82f6887a977ef002ad782902ca33be3c0e51` |

## Không che

Tên tài khoản seed `MOCK`, mã lượt thi, PID của JVM client/server trong log thao tác, cổng loopback và giờ UTC được giữ vì chúng là dữ liệu của lượt thử, không phải dữ liệu cá nhân.

## Bản gốc

Bản gốc chưa che nằm trong nhánh cục bộ `integrate/b-accept-01-20261013` trên máy build. Nhánh đó không dùng để push.
