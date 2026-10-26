# Ảnh duyệt UI — chặng Thí sinh

Nhánh `feat/ui-ux-simplify-20261026`. Dữ liệu `MOCK` (đề EX-02 200 câu, tài khoản tạo bởi test), không phải dữ liệu thật hay máy của người dùng.

- `01`–`13`: luồng ACCOUNT_PIN thật (Server + PostgreSQL + 2 app thí sinh trong cùng JVM), chụp bởi `AccountPinFlowPostgresGuiTest` khi đặt `TOEIC_UI_SCREENSHOTS=true`; mỗi trạng thái chụp ở 1920×1080 và/hoặc 1366×768, hộp thoại chụp riêng (`-dialog-0`).
- `part1`–`part7`: từng Part ở 1440 px, chụp bởi `RichExamViewGuiTest`.
- `result-timeout-interrupted`: hộp kết quả khi hết giờ và Listening bị gián đoạn, chụp bởi `ScoreResultDialogTest`.

Lệnh tái tạo: `scripts\verify-room-ux.ps1 -Mode Gui -SelectedTests AccountPinFlowPostgresGuiTest` với `TOEIC_UI_SCREENSHOTS=true`.
