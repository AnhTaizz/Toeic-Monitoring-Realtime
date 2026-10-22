# Hồ sơ dữ liệu HTML nguồn cho EX-01

> **Tình trạng từ 22/10/2026:** Năm file nguồn `ets2026_test01.html` đến `ets2026_test05.html` đã được gỡ khỏi cây file Git để tinh gọn bản demo. Bảng số liệu bên dưới và `manifest.json` là **hồ sơ lịch sử**, không phải danh sách HTML hiện có. `generated/ets2026_test01.seed.json` vẫn được giữ để đối chiếu nhưng có `importable: false` và **không phải dữ liệu import sẵn dùng**.
>
> File ZIP chứa dữ liệu nguồn sẽ được quản lý/nạp riêng. Nếu cần tái tạo seed từ HTML, giải nén HTML về đúng các đường dẫn trong `manifest.json` rồi chạy `scripts/build-exam-seed.ps1`. Các file `.html` ở thư mục này được `.gitignore` bỏ qua để tránh commit nhầm lần nữa.

Năm bản HTML do người dùng cung cấp, lấy từ **Zenlish TOEIC** (zenlishtoeic.vn), có footer “Copyright 2026 c Zenlish”. Ngày 19/10/2026, người dùng xác nhận: **“Có quyền công khai — tôi sẽ nêu căn cứ”** khi được hỏi về công bố trong repository public này. Đây là xác nhận của người cung cấp dữ liệu; chưa kèm văn bản cấp phép. Không gán giấy phép mã nguồn của repository cho nội dung bên thứ ba.

## Nội dung và giới hạn

| File | Số câu | Audio elements | Image elements | Ghi chú |
|---|---:|---:|---:|---|
| ets2026_test01.html | 200 | 54 | 48 | Nội dung riêng |
| ets2026_test02.html | 200 | 54 | 43 | Nội dung riêng |
| ets2026_test03.html | 200 | 54 | 48 | Nội dung riêng |
| ets2026_test04.html | 200 | 54 | 43 | Trùng test05 |
| ets2026_test05.html | 200 | 54 | 43 | Trùng test04, giữ tên người dùng cung cấp |

Mỗi file có 200 câu đánh số 1–200 và phân bố Part 1–7 là **6/25/39/30/30/16/54**. Năm file chỉ có **bốn nội dung khác nhau**. Số element HTML không phải số asset duy nhất hoặc bằng chứng mapping media đúng.

Các audio/hình trong HTML là **tham chiếu URL từ xa**. Chưa tải media vào repository, chưa xác minh khả năng truy cập/phát/hiển thị, chưa kiểm đầy đủ đáp án đúng, options, passages và group mapping. Không dùng các file này để kết luận EX-01 hoặc EX-08 PASS.

## Data seed mẫu

[generated/ets2026_test01.seed.json](generated/ets2026_test01.seed.json) được sinh trực tiếp từ `ets2026_test01.html`, không phải dữ liệu viết tay. Chạy lại bằng:

```powershell
./scripts/build-exam-seed.ps1
```

Script bắt buộc SHA-256 của HTML khớp manifest, sau đó fail-closed nếu thiếu câu, option, đáp án, group hoặc media. Seed mẫu gồm 200 câu, 775 options, 42 group, 54 audio và 38 ảnh nội dung. Test 01 có 48 thẻ ảnh tổng cộng; 10 ảnh giao diện trang nằm ngoài question/group nên bị loại khỏi seed.

Seed cố ý có `importable: false`. Khóa đáp án và mapping group trong manifest vẫn là `NOT VERIFIED`; media vẫn là `REMOTE_REFERENCE` và chưa được kiểm playback. Đây là HTML trang review: option được parser lấy từ phần giải thích, có thể là bản dịch, nên chưa được coi là nội dung candidate-facing nguyên bản. Trường `importBlockers` ghi rõ các điều kiện này để seed không bị hiểu nhầm là đề sẵn sàng vận hành.

## Bản công bố

- Loại bỏ toàn bộ script, form trang nguồn, hidden inputs, HTML comments, thuộc tính xử lý sự kiện và liên kết javascript.
- Mỗi file bỏ 65 script và 5 form. Nguồn gốc có 11 hidden inputs/file; các trường nonce/form không được công bố.
- Đối chiếu đủ 200 article câu hỏi/file trước và sau; nội dung giữ nguyên ngoài script/comment/thuộc tính sự kiện. Số audio/image elements giữ nguyên.
- Chuẩn hóa UTF-8 không BOM, xuống dòng LF; Git giữ nguyên byte để [manifest.json](manifest.json) có checksum ổn định trên Windows/WSL.
- Bản gốc được giữ riêng trong backup cục bộ, không đưa nonce/script vào lịch sử Git.
- Các file HTML là dữ liệu đầu vào, **không phải trang web chạy độc lập** hoặc candidate DTO. File JSON trong `generated/` là seed candidate normalized để đối chiếu schema; chưa phải payload cho API import v1 và chưa được phép đưa vào ca thi.

## Kiểm tra đã chạy

Kiểm SHA256 cả năm file, bảo toàn 200 article/file, bảo toàn số tham chiếu media và kiểm không còn script/form/hidden input/nonce/thuộc tính sự kiện: **PASS**. Manifest ghi hash gốc và hash bản công bố để đối chiếu.

Maven/PG/GUI/LAN/hearing và import đề: **NOT RUN** trong lượt thêm dữ liệu này. Không đổi Java/POM/SQL/API, tracker, điểm hoặc trạng thái nghiệm thu Phase 3.

**Owner EX-01:** A; **review nội dung:** B. Codex thực hiện kiểm kỹ thuật theo ủy quyền người dùng; không tạo chữ ký A/B. Việc còn lại: kiểm đáp án/options/groups/passages, lưu căn cứ quyền sử dụng, thu thập media được phép dùng với MIME/size/SHA256 và kiểm packaged client.
