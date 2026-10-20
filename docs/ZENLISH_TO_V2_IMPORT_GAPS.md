# Seed Zenlish → V2 import: những gì còn thiếu (ghi chú EX-06, 20/10/2026)

**Trạng thái: NOT IMPORTABLE.** `data/seedingTest/generated/ets2026_test01.seed.json` vẫn `importable:false`. Ghi chú này chỉ liệt kê khoảng trống đọc được từ file seed và contract EX-02. Nó **không** xác nhận dữ liệu nào là VERIFIED và không thay thế việc rà soát EX-01. Trong EX-06, client và E2E chỉ dùng fixture tự tạo `mock-ex02-200` (MOCK, CC0) import qua `POST /api/v2/exams/import`. Không có dữ liệu HTML nào được ép vào DB.

| Hạng mục | Seed hiện có (đọc từ file) | V2 importer cần (contract EX-02 §2/§7/§8) |
|---|---|---|
| Định dạng | `seedType` riêng, các mảng phẳng `questionGroups/questions/options/mediaAssets/contentBlocks` | `RichExamImportRequest` schemaVersion 2: groups/questions lồng blocks, options, `scoring`, sources có `sourceRef`. Cần một bộ chuyển đổi offline (chưa có). |
| Đáp án | `correctOption` có, nhưng `answerKeyStatus: UNVERIFIED_SOURCE_REVIEW_PAGE`; manifest ghi `answerKeys: NOT VERIFIED` | Đáp án phải được người rà soát xác nhận (VERIFIED) mới `selectable` |
| Lựa chọn | `optionTextOrigin: REVIEW_EXPLANATION`. Ví dụ q001 A = "The woman is carrying a tray of food." là **transcript** của Part 1 | Part 1/2 phải để text `null` (chỉ nhãn A/B/C/D), không lộ transcript. Part 3–7 cần text gốc trên đề, không phải bản dịch hay giải thích. |
| Nhóm / passage | 42 group; `groupPassageMapping: NOT VERIFIED` | Mapping nhóm Part 3/4/6/7 phải được kiểm. Part 6/7 cần `documents` có `documentId/order/title` tách theo từng tài liệu, không gộp. Mỗi câu Listening cần đúng một AUDIO block. |
| Audio | 54 asset `storageStatus: REMOTE_REFERENCE` (URL mp3 Zenlish) | File local trong `TOEIC_AUDIO_ROOT/<examId>/`, tên bắt đầu bằng số thứ tự ba chữ số (`001-…`), có SHA-256/size/MIME đo thật, phát thử được. Không tải từ xa lúc chạy. |
| Ảnh | 38 ảnh nội dung, URL từ xa, `widthPx/heightPx: null` | PNG/JPEG local trong `TOEIC_IMAGE_ROOT/<examId>/`, có SHA-256, size, kích thước pixel; Part 1 đủ 6 ảnh |
| Quyền sử dụng | `permissionStatus: USER_CONFIRMED`, `licenseDocument: NOT PROVIDED` | Có căn cứ quyền dùng/công bố. Người cung cấp dữ liệu cho biết sẽ gỡ đề sau khi lấy xong. |
| Trùng nội dung | test04 trùng test05 (`data/seedingTest/README.md`) | Mỗi đề import là một bộ nội dung riêng |

Việc tiếp theo thuộc EX-01/EX-03 (A), không làm trong EX-06:
1. Rà soát đáp án, options và mapping.
2. Tải và kiểm media về root chỉ đọc.
3. Viết bộ chuyển đổi seed → `RichExamImportRequest`, có test chạy trên PostgreSQL.
4. Import qua API; xem catalog báo `selectable` và `blockedReasons` rỗng.
