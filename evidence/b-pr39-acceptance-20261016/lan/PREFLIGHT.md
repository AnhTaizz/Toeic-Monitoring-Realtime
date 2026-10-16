# Preflight LAN

| Hạng mục | Phiên này | Cần owner xác nhận |
|---|---|---|
| Host A Windows/JDK21/PostgreSQL18.6 | AVAILABLE đầu phiên; shared DB hiện exited; cần DB TEST riêng sẵn sàng | Host A dành riêng, IP/interface/port18080 được phép |
| MSI0.3.7/hash/runtime | Xem report release mới | Hash máy A và B giống nhau |
| Windows host B / quyền thao tác | MISSING | Cung cấp máy B/operator, không coi WSL là máy B |
| HTTP/WS qua IP LAN | NOT RUN | Test-NetConnection và preflight HTTP401 trên từng host |
| Audio/checksum/test data | Script tạo fixture được cung cấp | Import đề UI, đặt WAV vào audio root TEST trước READY |
| Firewall / quyền cài/gỡ | Không đổi | Nếu cần rule mới, owner/admin xử lý; không tự sửa |
| Nghe thật/rút mạng thật | NOT RUN | Ghi tên operator, thiết bị, UTC, ảnh/log thực |
| Clock synchronization | NOT VERIFIED | Ghi trạng thái; không trừ clock khác host để suy latency |

File `New-LanTestData.ps1` chỉ tạo thư mục TEST mới, JSON/WAV tổng hợp120giây; không gọi API/DB hoặc ghi đè thư mục hiện có. Đây là fixture, không phải kết quả E2E.
