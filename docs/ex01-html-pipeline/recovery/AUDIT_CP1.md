# CP1 — Audit trực tiếp 25 câu Test 01, 20/10/2026

Nguồn: `data/seedingTest/ets2026_test01.html`, SHA-256 `7b9c38f3772f2f3d02d14896f0debe120eb7e17f483962dc7c8489df7ba003fe`.

Đã đọc question block, answer details, explanation, attributes và passage/group cho các câu: Part3 **32,33,34,50,68**; Part4 **71,72,80,93,100**; Part5 **101,102,108,115,130**; Part6 **131,132,135,141,146**; Part7 **147,150,163,181,196**. Raw DOM/evidence và kết luận từng câu ở [audit25.json](audit25.json), sinh bằng jsoup hiện có trước khi đổi parser recovery.

- 20 câu Part3/4/6/7 có A-D tiếng Việt trong lời giải; trường “Đáp án đúng” còn đúng một text tiếng Anh có nhãn. Không thể lấy transcript hoặc câu giáo viên giải thích để lấp ba distractor thiếu.
- Câu101 có A-D gắn nhãn rõ: across/after/inside/among, kèm gloss tiếng Việt sau dấu hai chấm. Phép tách prefix cần EXTRACTED_NEEDS_REVIEW; chưa HUMAN_VERIFIED.
- Câu102/108/115/130 có từ tiếng Anh trong danh sách gạch đầu dòng **không nhãn**, xen thêm vocabulary/cấu trúc. Không suy A-D từ vị trí. Ví dụ108 “to line / lining / lined / line” không chứng minh đó là thứ tự A-D nguyên bản.
- Cả25 answer details giữ text tiếng Anh của lựa chọn đúng. Đây là25/100 text có vị trí nhãn chính xác trong mẫu, không phải25 đáp án đã được kiểm độc lập. Câu101 thêm3 prefix distractor có thể tách để review; không tính là SOURCE_EXACT cả bộ.
- Trong mẫu: 20 prompt tiếng Anh; năm câu Part6 chỉ có số câu. Passage Part6/7 chủ yếu là ảnh, chưa có bằng chứng boundary document/text gốc.
- DOM không còn script; không có data-choices/choice_answer/correct_answer/sub_questions hoặc JSON question payload. Tìm raw marker không thấy các trường này; link WordPress JSON/oEmbed không phải dữ liệu options. `data-answer-state` chỉ là kết quả trả lời của người học.

Chưa có câu nào trong25 được xác minh đủ4 options ETS gốc. Cần parser tách evidence/English/translation/teacher content theo nhãn rõ, giữ riêng danh sách không nhãn; sau đó đo toàn bộ169 câu Part3–7. Không dịch ngược, không nâng VERIFIED hoặc sửa importer.
