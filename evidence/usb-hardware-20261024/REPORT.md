# Kiểm tra USB thật — 24/10/2026

**Kết quả: phát hiện được một USB mass-storage sau khi người dùng rút rồi cắm lại.**
Luồng TEST thật từ CIM → adapter → WebSocket → PostgreSQL → ACK → HTTP history PASS.
Không dùng codex-with-chatgpt theo yêu cầu người dùng; không thay đổi code.

- Source `acc1f82ed64410ae3759c572813cb80dcc6de8f4`; trước test chỉ có `AGENTS.md` untracked,
  nên runner ghi `sourceDirty=true`. Máy `USB-TEST`, Windows 11 x64, Java 21.0.10,
  Windows PowerShell 5.1.26100.9711, PostgreSQL TEST 18.3; token Windows `STANDARD`.
- Run `usb-92ad0baf1e9d4dc4`, 20:52:25–20:54:58 ngày 24/10/2026 (UTC+7).
  Chạy `scripts/demo-usb-integrated.ps1 -Mode Real -ReadUsbStorage -DurationSeconds 120
  -CadenceMillis 1000 -QueryTimeoutMillis 5000`, provider `MSFT_DISK`.
- 86 scan READY: scan 1–41 thấy 0 USB, scan 42–86 thấy 1 USB. History có đúng một
  `USB_STORAGE_CONNECTED` lúc **20:53:54.556761 UTC+7**, `storageCount=1`,
  `metadataQuality=PARTIAL`; không lặp ở 44 scan ổn định sau đó.
- Tổng hai bản tin: một `OBSERVER_STATUS READY/INITIAL` và một USB event;
  cả hai COMMIT/ACK/readback, canonical equality đúng. Drop, pending, unreported,
  unconfirmed gap đều 0. JUnit `UsbIntegratedDemoPostgresTest#realCimReadinessCommitAckAndHistory`:
  **1 test, 0 failure/error/skip**; Maven BUILD SUCCESS.
- Cleanup: 0 schema còn lại, PostgreSQL stop exit 0, không còn postmaster PID;
  child 0, pipe readers và scan/control/delivery workers đã dừng.

## Giới hạn kết luận

Ban đầu người dùng xác nhận đang cắm USB, nhưng MSFT chỉ thấy một ổ bus 17 và không có
USB; Win32 trả UNKNOWN. Sau thao tác rút/cắm được người dùng xác nhận, MSFT thấy bus
17 và 7, USB count 1, phù hợp event thật. Chưa xác định nguyên nhân không thấy ở đầu lượt.
Không có mốc thao tác thủ công chính xác để đo latency. Chỉ quan sát được chuyển đổi
**0 → 1**; chưa kiểm chứng `REMOVED` hoặc `PRESENT_AT_START`, không nghiệm thu trọn H01–H12.

USB hiện chỉ có probe/pipeline TEST; app thí sinh mặc định chưa gắn nguồn USB.
Không kiểm GUI/timeline trong lượt này, không đổi observer, API/schema, cấu hình mặc định,
tracker hoặc đánh dấu MON-02/QA-04 DONE. Trường `hardware="0/12 NOT_RUN"` trong runner
là nhãn cố định từ harness cũ; giữ raw nguyên trạng, không dùng nhãn đó phủ nhận lần
quan sát USB vật lý này hoặc suy ra bộ 12 ca đã được nghiệm thu.

## Evidence

[Receipt dẫn xuất và SHA-256](receipt.json) được sinh từ summary, history và JUnit XML.
Raw local ignored: `target/usb-integrated-usb-92ad0baf1e9d4dc4/`.
Không sửa raw, không đọc file, serial hoặc nội dung USB.

Lượt đầu `usb-ac48d52caf264843` thất bại trước Maven vì sandbox chặn kết nối DB local
(`10013`); standalone CIM trong sandbox trả `QUERY_FAILED`. Lượt lỗi được giữ tại
`target/usb-integrated-usb-ac48d52caf264843/`, cluster đã stop/không còn PID.
Lượt thành công chạy ngoài sandbox, không nâng token Windows lên Administrator.
