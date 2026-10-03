# Sổ quyết định kỹ thuật

Ghi lại lựa chọn đã chốt để sau này không phải tranh luận lại, và để viết phần "các quyết định thiết kế" trong báo cáo. Các quyết định nền (stack, hợp đồng lưu/nộp, monitoring, Listening) đã có ở `Ke_hoach_LT_Mang_5_chang/02_HOP_DONG.md`; file này chỉ ghi những gì hợp đồng để ngỏ hoặc phát sinh khi làm.

## Mẫu

```markdown
## QD-03 · Cách gửi credential cho WebSocket
- Ngày: 2026-10-04 · Người quyết: A · Duyệt: C
- Bối cảnh: hợp đồng cấm để token trong URL query.
- Lựa chọn: gửi token trong header lúc handshake.
- Đã cân nhắc: message AUTH đầu tiên sau khi mở kết nối (phải giữ kết nối chưa xác thực một lúc).
- Hệ quả: B đặt header khi tạo WebSocket; server từ chối handshake nếu thiếu.
- Liên quan: T1-A2, T1-B2, AT02
```

## Đang chờ quyết

| Mã | Cần quyết | Ai quyết | Hạn | Task |
|---|---|---|---|---|
| QD-01 | Phiên bản JDK, JavaFX, Spring Boot, PostgreSQL | A + B | 03/10 | T1-A1, T1-B1 |
| QD-02 | Cách khởi tạo và reset schema (file SQL + script, hay công cụ migration) | A | 03/10 | T1-A1 |
| QD-03 | Cách gửi credential cho HTTP và WebSocket | A | 04/10 | T1-A2 |
| QD-04 | Định dạng mã lỗi chung (unauthorized, forbidden, invalid, stale, conflict, expired, retryable) | C + A | 04/10 | T1-C1 |
| QD-05 | Tầng truy cập DB (JDBC/JdbcTemplate hay JPA) | A | 03/10 | T1-A1 |
| QD-06 | Cách chạy test trên PostgreSQL thật (DB test cục bộ hay container) | A | 12/10 | T2-A4 |
| QD-07 | Định dạng audio và cách đóng gói tài nguyên | B | 08/10 | T1-B4 |
| QD-08 | Danh sách process bị hạn chế (policy v1) và ứng dụng dùng để demo | C | 04/10 | T1-C1 |
| QD-09 | Quy tắc chấm điểm nội bộ (câu sai, câu trống) | A | 12/10 | T2-A3 |
| QD-10 | Giữ hay cắt delta khỏi bản chính | C, B duyệt | 20/10 | T3-C4 |

Khi chốt, chuyển dòng tương ứng xuống mục dưới theo mẫu.

## Đã chốt

_Chưa có._
