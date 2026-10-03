# BỘ KẾ HOẠCH BTL LẬP TRÌNH MẠNG — NHÓM 3 NGƯỜI

Giải nén ZIP rồi mở **START_HERE.html** bằng Edge/Chrome để đọc bản trình bày đầy đủ. Tệp hoạt động offline, có mục lục và nút in. Các tệp Markdown giữ nội dung để sửa hoặc giao cho agent. Không cần cài công cụ lập trình để đọc.

## Có gì trong gói?

| Tệp | Cách dùng |
|---|---|
| START_HERE.html | Bản đọc đẹp, gộp kế hoạch và hai phụ lục; có thể in từ trình duyệt |
| 01_KE_HOACH.md | 5 chặng 03–31/10, 75 task, A/B/C và review, tổng giờ, từng buổi chặng 1, demo, mốc cắt, checklist nộp |
| 02_HOP_DONG.md | Chuẩn chung quyền truy cập, lưu/nộp/deadline/writer, monitoring epoch/state/history và Listening |
| 03_KIEM_THU_VA_THUC_NGHIEM.md | 27 case kiểm thử NOT RUN, hai thí nghiệm độc lập, phép đo và cách bảo vệ đóng góp |
| TRACKER.json | Cùng 75 task dạng cấu trúc; owner/reviewer/giờ/phụ thuộc/nghiệm thu, trạng thái TODO; actual/evidence để trống |
| nguon/ | Yêu cầu gốc, README/Topics/Instruction, transcript PDF và Submission trích nguyên bản từ ZIP đã cung cấp |
| SHA256SUMS.txt | Mã kiểm tra toàn vẹn từng tệp trong gói |

## Ba điểm cần đọc trước khi giao việc

1. Đây là **kế hoạch**, chưa có source app hoặc bộ cài. Không dùng ZIP này làm gói nộp môn học; không có kết quả thực nghiệm đã đo.
2. 5 chặng không phải 5 tuần đầy đủ. Deadline nguồn là **23:59 ngày 31/10/2026**, lịch làm bắt đầu **03/10**.
3. 15 giờ/người/tuần chỉ đủ khoảng 149,14 giờ giao việc sau dự phòng. Backlog đầy đủ cần 246 giờ, nên tài liệu đề xuất khoảng **25 giờ/người/tuần**, chưa coi đã được nhóm xác nhận. Có phương án thu hẹp nếu chỉ giữ 15 giờ; không âm thầm đổi phạm vi.

Mở mục 5 của kế hoạch để biết A/B/C làm gì trong 24 giờ đầu. Mỗi người tự test/tài liệu module mình; các phiên review đã có giờ riêng. Sau ngày 08/10 cập nhật ước lượng từ giờ thực tế.

## Ghi nhận nguồn và trạng thái

Nội dung được lập từ tệp yêu cầu kèm theo và các quyết định cuối trong cuộc trao đổi: giữ stack, kiểm quyền trước trả dữ liệu, dùng clock_timestamp sau khóa, giữ revision và kiểm writer tại điểm ghi, state/history riêng, timeout do server phát hiện, benchmark tính tổng byte hai chiều. Phần I transcript là lời trao đổi được ghi lại; phần II là định hướng tổng hợp, đã phân biệt trong kế hoạch.

Tài liệu môn được chép nguyên bản vào nguon; không đổi nguồn thành yêu cầu mới. Các tham số thử, giờ công, ngày gate, 75 task và phương án tăng giờ là **đề xuất lập kế hoạch**, không phải yêu cầu nguyên văn của thầy.
