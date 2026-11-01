# Slide thuyết trình — TOEIC Monitor

Bộ slide HTML 16:9 (29 slide) cho buổi bảo vệ BTL Lập trình mạng, dựng theo skill [Frontend Slides](https://github.com/zarazhangrui/frontend-slides) và [Design System V1.0 — Claymoon Flow](DESIGN_SYSTEM.md). Không phụ thuộc thư viện JS; font tự host nên chạy được khi không có mạng.

## Mở và trình chiếu

Mở `index.html` bằng Chrome/Edge (bấm đúp là đủ, không cần server).

| Phím | Việc |
|---|---|
| → ↓ Space PageDown Enter | Slide sau |
| ← ↑ PageUp Backspace | Slide trước |
| Home / End | Slide đầu / cuối |
| F | Toàn màn hình |
| E (hoặc rê chuột góc trên trái) | Bật/tắt sửa chữ trực tiếp; Ctrl+S tải về `index.html` đã sửa |

URL giữ số slide (`index.html#18`). Sân khấu luôn là 1920×1080 và được co giãn nguyên khối, có viền đen trên màn khác tỉ lệ.

## Cấu trúc

| Đường dẫn | Nội dung |
|---|---|
| `index.html` | Toàn bộ slide, CSS, JS (một file) |
| `DESIGN_SYSTEM.md` | Token màu/chữ/khoảng cách/chuyển động của Design System V1.0 |
| `assets/fonts/` | Bricolage Grotesque, Be Vietnam Pro, JetBrains Mono (+ Archivo, Space Grotesk cho bản phác) — Google Fonts, SIL OFL 1.1, subset latin + vietnamese |
| `assets/screens/` | Ảnh chụp phần mềm, cắt từ `evidence/` và `docs/` (không chỉnh nội dung) |
| `explorations/` | 3 phương án thiết kế slide bìa và ảnh chụp của chúng |
| `screenshots/` | Ảnh chụp kiểm tra 29 slide ở 1920×1080 |
| `tools/screenshot.js` | Chụp từng slide + kiểm tra tràn chữ/chồng khối |

## Ba phương án đã thử

| Phương án | Hướng | Đánh giá |
|---|---|---|
| A · Swiss Grid (`explorations/style-a-swiss-grid.html`) | Preset Swiss Modern, lưới 12 cột lộ, Archivo | Rõ ràng nhưng khô; bản đầu có tiêu đề đè phụ đề, đã sửa trước khi so |
| B · Neo-Grid Orange (`explorations/style-b-neo-grid.html`) | Bold template neo-grid-bold, đổi vàng chanh → cam | Mạnh, hợp slide số liệu; chữ hoa Space Grotesk nặng và xa phong cách “Claymoon” |
| **C · Claymoon Flow** (`explorations/style-c-claymoon-flow.html`) | Tự thiết kế: nền kem, orb cam “đất sét”, đường cong mềm | **Được chọn**: đúng tinh thần White Orange/Claymoon, cùng màu với app JavaFX nên ảnh chụp phần mềm hòa vào slide |

## Nội dung và nguồn

Mọi chức năng và số liệu lấy từ tài liệu/source trong repo; mỗi slide có dòng nguồn ở chân. Chính:

- Kiến trúc, giao thức, mã lỗi: `docs/ARCHITECTURE.md`, `docs/PROTOCOL.md`, `README.md`.
- Autosave/khóa: `server/.../exam/ExamService.java`; quy tắc delta: `protocol/.../monitoring/MonitoringStateReducer.java`.
- E1 polling: `evidence/qa05-e1-20261021/REPORT.md`. Replay full/delta: `evidence/t3-c3/campaign-final/matrix.md`.
- Benchmark ACCOUNT_PIN: `docs/account-pin/IMPLEMENTATION-REPORT.md`. Hồi quy: `docs/release/RELEASE-REPORT.md`.
- Ảnh: `evidence/ui-ux-20261026/`, `evidence/proctor-feed-20261025/`, `docs/account-pin/screenshots/` — dữ liệu MOCK/TEST, ghi rõ trên slide.

Những gì chưa kiểm (LAN hai máy, nghe audio thật, USB vật lý trên commit tích hợp) được ghi là NOT TESTED trên slide 21 và 28, không trình bày như đã đạt.

## Kiểm tra bố cục

Cần Node và Playwright (môi trường có sẵn Chromium):

```bash
node presentation/tools/screenshot.js presentation/index.html presentation/screenshots-new --audit
```

Lần chạy cuối: 29 slide, 0 lỗi tràn/chồng do script phát hiện; từng ảnh đã được xem lại bằng mắt ở 1920×1080. Đã thử thêm khung 1280×720 và 390×844 (điện thoại): sân khấu giữ 16:9, không cuộn ngang.

## Sửa nhanh

- Màu, font, khoảng cách: khối `:root` đầu `index.html`.
- Chuyển động: lớp `.reveal`, `.reveal-scale`, độ trễ `.d1`–`.d6`.
- Ghi chú người nói: phần tử `<aside class="notes">` (ẩn khi chiếu).
