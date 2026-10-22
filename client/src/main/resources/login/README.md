# Ảnh minh họa màn đăng nhập

`login-reference.png` là mockup đăng nhập TOEIC Room do người dùng cung cấp ngày 20/10/2026.
Giữ nguyên bitmap gốc. `LoginPane` dùng `ImageView.viewport` để hiển thị riêng vùng minh họa bên trái
và linh vật ở phần đầu; toàn bộ form, thông báo, nút và trường nhập là control JavaFX thật.
Ảnh được nạp nền. Không thêm thư viện, không dùng hình của form làm nút giả.
Vai trò đăng nhập vẫn do server quyết định; ảnh tham chiếu không phải hợp đồng phân quyền.

SHA-256: `2eb3a85d990474b3db312572433fdfd127c4bed72d7efc24c2d53339fefe8c40`.

`home-reference.png` là mockup trang chủ người dùng cung cấp; chỉ hiển thị vùng minh họa
bên trái bằng viewport giữ tỷ lệ. Tên/ID/thời lượng trên giao diện thật lấy từ server.
