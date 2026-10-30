# Tạo đề thủ công

Chỉ có một nút **Lưu đề**. Nội dung, âm thanh và ảnh đều được lưu trên server. Chưa đủ 200 câu theo chuẩn thì đề ở **PENDING**, có thể mở lại từ danh sách để nhập tiếp. Đủ nội dung và media bắt buộc thì đề chuyển **READY** và có thể dùng tạo ca thi. Không có luồng lưu/mở nháp trên máy.

## Nội dung và media

- Khung cố định: Part 1 có 6 câu, Part 2 có 25, Part 3 có 39, Part 4 có 30, Part 5 có 30, Part 6 có 16, Part 7 có 54. Mỗi câu có 4 lựa chọn, riêng Part 2 có 3.
- Part 1 cần một ảnh, âm thanh và đáp án; Part 2 cần âm thanh và đáp án. Không bắt nhập lại lời đọc/lựa chọn của hai Part này.
- Part 3–4 dùng chung âm thanh theo nhóm 3 câu; chọn một lần trong bất kỳ câu nào của nhóm. Part 6 dùng chung đoạn văn theo nhóm 4 câu. Các Part đọc cần nội dung, lựa chọn, đáp án; Part 6–7 cần đoạn văn.
- **Chọn âm thanh…** mở file MP3/WAV/M4A từ máy. **Chọn ảnh…** mở PNG/JPEG và hiển thị ảnh xem trước. Part 1 có một ảnh; các câu khác có thể chọn nhiều ảnh nếu cần. Khi mở lại đề từ server, ảnh vẫn được tải lại để xem.
- Tối đa 64 MiB/tệp âm thanh, 8 MiB/ảnh; tổng 256 MiB âm thanh và 64 MiB ảnh mỗi đề. Server kiểm tra định dạng, kích thước, checksum và quyền truy cập. Media tải lên được hoàn tác nếu lưu đề thất bại.
- Đề hoàn thiện được lưu theo định dạng rich hiện có của ứng dụng, gồm ảnh và âm thanh trong bài thi. Không chỉ lưu tên ảnh trong form quản trị.

## Vì sao vẫn có HTTP 404?

Client trên Windows đang gọi server Docker ở VPS. Đổi EXE hay chạy `Start-Server.bat` ở Windows không cập nhật container trên VPS. Cần thay **server.jar trong image đang phục vụ URL đăng nhập**, rồi tạo lại container server. Client kiểm tra hỗ trợ soạn đề có ảnh trước khi mở form để tránh nhập xong mới gặp lỗi phiên bản.

Giữ nguyên database, file cấu hình, khóa PIN và thư mục media đang dùng. Không reset database. Media mounts phải ghi được, không dùng `:ro`. Flyway tự áp dụng các migration còn thiếu, gồm V22 cho PENDING và V23 cho nguồn đề được biên soạn trực tiếp.

Với server Docker hiện có, chép JAR mới vào đường dẫn mà Dockerfile đang `COPY`, rồi chạy từ thư mục compose trên VPS (nếu service có tên `server`):

```sh
docker compose up -d --build --no-deps server
docker compose logs --tail=80 server
```

Nếu đang dùng compose chỉ có PostgreSQL của repository này, có thể thêm `docker-compose.server.yml`. Điền `.env` bằng thông số DB, khóa PIN và đường dẫn media **đang dùng**, sau đó:

```sh
docker compose -f docker-compose.yml -f docker-compose.server.yml up -d --build server
```

Dockerfile nhận `server/target/server-0.1.0-SNAPSHOT.jar`. JAR đã build trên Windows chạy được với Java 21 trong container Linux. Bộ chạy Windows cũng kèm `server.jar`, `Dockerfile` và ghi chú triển khai để người quản trị VPS dùng đúng bản.

Nếu dùng JAR trong bộ chạy thay vì build từ repository, chép `server.jar` và `Dockerfile.server` vào cùng một thư mục trên VPS, rồi build bằng `docker build -f Dockerfile.server -t toeic-authoring:local .`. Cấu hình service server hiện có dùng image này; giữ nguyên environment, network và volumes đang sử dụng. File compose kèm theo là mẫu cho layout repository, không thay trực tiếp cấu hình VPS chưa được kiểm tra.

## Kiểm thử

`scripts/verify-manual-authoring.ps1 -PostgresBin '<PostgreSQL>/bin'` tạo PostgreSQL riêng, chạy HTTP và JavaFX bằng dữ liệu MOCK, rồi dừng cluster. Bao gồm PENDING/READY theo chuẩn, chọn/upload ảnh và âm thanh, xem lại ảnh, ảnh trong chuẩn bị thi của thí sinh, revision conflict, phân quyền, hoàn tác media, một nút lưu và kiểm tra phiên bản server. Các bài cần bộ crawl ngoài được bỏ qua khi thiếu dữ liệu.
