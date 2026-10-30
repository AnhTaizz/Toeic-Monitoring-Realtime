# Design System V1.0 — Claymoon Flow (slide TOEIC Monitor)

Hệ thống thiết kế dùng cho bộ slide trong `presentation/`. Cảm hứng: phong cách *White Orange Pitch Deck — Claymoon Flow* (nền trắng kem, khối "đất sét" cam bo tròn, đường cong mềm chạy qua slide). Màu gốc lấy từ chính giao diện JavaFX của dự án (`client/src/main/resources/proctor-redesign/proctor-redesign.css`, `login/home.css`) để slide và phần mềm demo nhìn như một hệ thống.

> Ghi chú nguồn: trong repo và tài khoản không có file "Design System V1.0" riêng. Token dưới đây được dựng từ màu thật của ứng dụng + mô tả phong cách Claymoon Flow. Nếu nhóm có file token chính thức, chỉ cần thay giá trị trong khối `:root` của `index.html`.

## 1. Màu

| Token | Giá trị | Nguồn / vai trò |
|---|---|---|
| `--orange` | `#F26B1D` | Màu nhấn của app (`-fx-accent`). Khối nhấn, orb, thanh dữ liệu |
| `--orange-deep` | `#C2410C` | Cam đậm của app; chữ cam trên nền sáng (đạt tương phản chữ) |
| `--orange-soft` | `#FFF1E8` | Nền chọn của app; nền thẻ nhấn nhẹ |
| `--peach` | `#FFD9BF` | Quầng sáng quanh chấm tín hiệu |
| `--cream` | `#FFFAF4` | Nền slide (gần `#FCF8F2` của màn thí sinh) |
| `--sand` | `#F6ECE1` | Nền phụ, pebble |
| `--navy` | `#13254A` | Chữ chính của app |
| `--ink-2` | `#3B4A66` | Chữ thân |
| `--muted` | `#6B7A90` | Chú thích, nhãn |
| `--line` | `#E3E8EF` | Đường kẻ mảnh |
| `--good` / `--warn` / `--bad` | `#16A34A` / `#D97706` / `#DC2626` | Trạng thái (giống badge của app); luôn đi kèm chữ, không dùng màu đơn lẻ |

Quy tắc: một màu nhấn (cam) trên nền kem; navy cho chữ; trạng thái chỉ dùng khi nói về PASS/FAIL/NOT TESTED.

## 2. Chữ (đều hỗ trợ tiếng Việt, tự host trong `assets/fonts/`)

| Vai trò | Font | Cỡ trên stage 1920×1080 |
|---|---|---|
| Display / tiêu đề | Bricolage Grotesque 800, tracking −0.03em | 196 (bìa) · 120 (section) · 76 (tiêu đề slide) |
| Thân | Be Vietnam Pro 400–700 | 30–34 (lead) · 24–28 (thẻ) · 20 (chú thích) |
| Mã, số đo, nhãn | JetBrains Mono 400/600 | 20–26 |

Không dùng chữ dưới 20px trên stage.

## 3. Khoảng cách và lưới

- Lề slide 120px trái/phải, 96px trên, 88px dưới; nội dung trong vùng 1680×896.
- Bước khoảng cách: 8 · 16 · 24 · 32 · 48 · 64 · 96.
- Bo góc: thẻ 32px, pill 999px, khung ảnh 24px.

## 4. Thành phần

- **Clay card**: nền trắng, bo 32px, bóng mềm hai lớp `0 18px 40px rgba(120,70,30,.10)` + viền sáng trong `inset 0 -4px 0 rgba(0,0,0,.03)`.
- **Clay orb / pebble**: hình tròn với 3 lớp gradient (điểm sáng 34%/30%, thân cam, cạnh tối) + bóng đổ cam — chỉ để trang trí, mỗi slide tối đa một orb lớn.
- **Flow ribbon**: 1–2 đường cong SVG cam (gradient alpha), vẽ dần khi vào slide.
- **Pill / kicker**: nhãn phần, nhãn vai trò, nhãn trạng thái.
- **Code card**: nền navy, JetBrains Mono 22–24px, chú thích dòng bằng màu peach.
- **Screen frame**: ảnh chụp phần mềm thật trong khung trắng bo 24px; luôn ghi nguồn (test nào chụp, dữ liệu MOCK/TEST).
- **Chart**: một chuỗi một màu (cam hoặc navy), thanh ≤ 32px, đầu bo 4px, nhãn giá trị bằng chữ navy cạnh thanh, lưới mảnh `--line`. Không dùng hai trục.

## 5. Chuyển động

- Vào slide: `reveal` (mờ → rõ, trượt 28px, 0.8s `cubic-bezier(.16,1,.3,1)`), so le 0.12s.
- Orb: phóng nhẹ kiểu lò xo `cubic-bezier(.34,1.56,.64,1)`.
- Ribbon: vẽ nét 2.2s. Thanh biểu đồ: mọc từ đường gốc.
- `prefers-reduced-motion`: tắt chuyển động (có sẵn trong `viewport-base.css`).

## 6. Thứ bậc thị giác

1 ý chính / slide → tiêu đề nói kết luận, không nói chủ đề ("ACK chỉ gửi sau COMMIT" thay vì "Giao thức"). Số đo lớn nhất của slide dùng Display; nguồn số đo luôn có ở chân slide.
