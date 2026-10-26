# Màn giám thị "Phòng thi" — ảnh review

- `p1`–`p7`: dữ liệu **MOCK** (`ProctorRoomViewGuiTest.reviewScreenshots`, chạy với `TOEIC_PROCTOR_GUI=true TOEIC_UI_SCREENSHOTS=true`). Không phải bằng chứng tích hợp.
  - `p1` chưa chọn phòng · `p2` phòng chờ bắt đầu (READY 2/3) · `p3` phòng chưa có ai · `p4` phòng đang thi (đã nộp/hết giờ/chỉ làm Reading) · `p5` panel chi tiết thí sinh · `p6` tạo phòng · `p7` phòng vừa tạo có PIN.
- `real-*`: luồng ACCOUNT_PIN thật (Server + PostgreSQL + 2 app thí sinh), `AccountPinFlowPostgresGuiTest` với `TOEIC_UI_SCREENSHOTS=true`, trường hợp 1/2 thí sinh sẵn sàng.
  - `real-p1` phòng vừa tạo · `real-p2` hai thí sinh đã vào, 1 sẵn sàng · `real-p4` cả hai đã nộp, người vào muộn "Chỉ làm Reading".
