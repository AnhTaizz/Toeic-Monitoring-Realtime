# Standalone monitoring probes

`ProcessHandleProbe` và Maven exec entry point cũ giữ nguyên. MON-02 thêm CLI riêng `vn.edu.toeic.monitoring.usb.UsbStorageProbe`, không được gọi từ client/server production.

USB probe dùng **Java 21 standard library + Windows PowerShell 5.1/CimCmdlets**; không thêm dependency JNA/native, C#, Python agent hoặc service. Chỉ resource PowerShell của module spike được đóng vào JAR spike; bộ cài JavaFX production chưa được thay/kiểm với probe.

Từ repo root, source phải commit sạch:

```powershell
.\scripts\build-usb-probe.ps1
.\scripts\probe-usb-storage.ps1 -Method msft-disk -MachineAlias WIN-A
.\scripts\probe-usb-storage.ps1 -Method msft-disk -DurationSeconds 120 -IntervalMillis 1000 -MachineAlias WIN-A
```

Build chỉ chạy `clean verify` module `monitoring-spike`, không chạy server/PG hoặc full reactor. Nếu môi trường Maven trỏ nhầm cache, truyền `-MavenRepository <thu-muc-cache-hien-co>`; không sửa cấu hình toàn máy. Build receipt local ignored gắn source SHA với hash source/class/resource; wrapper từ chối source/runtime đã đổi. Rebuild sau checkout/clean/cập nhật code. Đổi riêng tài liệu không làm giả source SHA: output vẫn ghi SHA source đã build trong receipt.

Fallback khảo sát đã code: `-Method win32-diskdrive`; SCSI/missing interface giữ UNKNOWN, không đoán là không có USB. Chi tiết API, exit codes, độ bao phủ, giới hạn và physical checklist ở [runbook](../docs/USB_STORAGE_PROBE.md). Mọi JSONL là **local probe schema**, chưa phải contract MON-07 đã duyệt.
