# Tài liệu làm việc của nhóm

Thư mục này là nơi ghi lại việc đang làm, để bất kỳ ai (hoặc Claude) mở project lên đều biết đang ở đâu và làm tiếp cái gì. Bộ kế hoạch gốc nằm ở `Ke_hoach_LT_Mang_5_chang/` và không thay đổi hằng ngày; các file ở đây thì có.

## File nào dùng để làm gì

| File | Nội dung | Ai cập nhật | Khi nào |
|---|---|---|---|
| [TIEN_DO.md](TIEN_DO.md) | Đang ở chặng nào, mỗi người đang làm gì, bước tiếp theo, đang bị chặn bởi gì | Cả ba | Cuối mỗi buổi làm |
| [NHAT_KY.md](NHAT_KY.md) | Nhật ký từng buổi: đã làm gì, commit nào, còn dở gì | Người vừa làm | Cuối mỗi buổi làm |
| [QUYET_DINH.md](QUYET_DINH.md) | Các quyết định kỹ thuật đã chốt và đang chờ chốt | Người ra quyết định, reviewer duyệt | Khi chốt một lựa chọn |
| [PROTOCOL.md](PROTOCOL.md) | Danh mục endpoint và message đang dùng thật | C (monitoring), A (auth, thi) | Khi thêm hoặc đổi message |
| [MONITORING_MEASUREMENTS.md](MONITORING_MEASUREMENTS.md) | Schema JSONL, byte/outcome, cấu hình và tái tạo summary C4 | C | Khi đổi cách đo |
| [PROCESS_MONITORING_SURVEY.md](PROCESS_MONITORING_SURVEY.md) | ProcessHandle/WMI, nguồn chính thức và giới hạn quan sát | C | Khi khảo sát phương pháp |
| [KIEM_THU.md](KIEM_THU.md) | Trạng thái 27 test case và nơi lưu bằng chứng | Owner của test | Khi chạy test |
| [thanh-vien/VAI_A.md](thanh-vien/VAI_A.md) | Task và hướng dẫn cho vai A (server, DB, giao dịch) | A | Ghi chú làm dở |
| [thanh-vien/VAI_B.md](thanh-vien/VAI_B.md) | Task và hướng dẫn cho vai B (JavaFX, audio, đóng gói) | B | Ghi chú làm dở |
| [thanh-vien/VAI_C.md](thanh-vien/VAI_C.md) | Task và hướng dẫn cho vai C (monitoring, thực nghiệm) | C | Ghi chú làm dở |

Trạng thái chính thức của từng task (TODO/đang làm/xong, giờ thực tế, bằng chứng) vẫn nằm ở `Ke_hoach_LT_Mang_5_chang/TRACKER.json`. Các file ở đây không lặp lại trạng thái đó.

Bàn giao B3 ngày04/10: [evidence dashboard](../evidence/t1-b3/2026-10-04-verification.md) ghi 331 tests, REAL integration và Stage proctor; [README chạy dashboard](../README.md#dashboard-giám-thị-t1-b3). TRACKER giữ nguyên theo yêu cầu task; trạng thái code/component và nghiệm thu toàn prototype được ghi riêng.

## Bắt đầu một buổi làm

1. Đọc [TIEN_DO.md](TIEN_DO.md): xem dòng của mình và mục "Đang bị chặn".
2. Đọc mục cuối cùng của mình trong [NHAT_KY.md](NHAT_KY.md) để biết lần trước dừng ở đâu.
3. Mở file vai của mình, tìm task đang làm, đọc "Ghi chú làm dở".
4. Nếu task phụ thuộc contract của người khác, xem [PROTOCOL.md](PROTOCOL.md) đã có mẫu chưa.

## Kết thúc một buổi làm

1. Commit code (kể cả đang dở, trên nhánh riêng).
2. Thêm một mục vào [NHAT_KY.md](NHAT_KY.md).
3. Sửa dòng của mình trong [TIEN_DO.md](TIEN_DO.md).
4. Nếu task chưa xong, ghi vào "Ghi chú làm dở" của task đó trong file vai: đang dừng ở bước nào, cái gì chưa chạy.
5. Nếu task xong và có bằng chứng thật, cập nhật `TRACKER.json` (`status`, `actual_hours`, `evidence`).
6. Nếu có chốt lựa chọn kỹ thuật, ghi vào [QUYET_DINH.md](QUYET_DINH.md). Nếu có đổi message, sửa [PROTOCOL.md](PROTOCOL.md) và báo người dùng message đó.

## Quy ước

- Bằng chứng là thứ kiểm tra lại được: commit SHA, tên test đã chạy, đường dẫn file log. "Đã thử thấy ổn" không phải bằng chứng.
- Dữ liệu giả ghi nhãn `MOCK`.
- Không ghi kết quả chưa đo, không ghi Passed cho test chưa chạy.

T2-C3: [trace/schema/lệnh CLI](MONITORING_TRACE.md), [evidence/checksums](../evidence/t2-c3/2026-10-10-verification.md). Reuse full/event encoder và shared C1 reducer, oracle độc lập, recorder mặc định tắt. REAL source khác REPLAY và SIMULATED faults; Human B review NOT RUN; chưa delta/T2-C4/E1/E2.
