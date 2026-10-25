# USB thật trong ứng dụng JavaFX

## Khi app8080 không hiện USB

[Lượt debug24/10](../evidence/usb-8080-fix-20261024/REPORT.md) xác nhận: cần bật cả hai
đầu, thí sinh thực sự ở màn làm bài và giám thị chọn đúng attempt. `DEMO-C3-A` là fixture
process cũ, không gắn ca/đề nên không dùng để mở bài/quan sát USB. Lượt TEST hợp lệ hiện
có là `USB-8080-20261024-511c18-candidate1`; đăng nhập lại sau khi được phân công mới
để danh sách scope ở cả hai app được cập nhật. Bản client cập nhật đã sửa preview
ACTIVE0 và projection Listening rỗng của lượt legacy, không đổi schema hay playback.

Nhánh `codex/usb-app-integration-20261024` nối CIM vào `CandidateExamSignalMonitoring`.
Giám thị đọc qua **Timeline đa tín hiệu** hiện có. Module nội bộ `windows-observer`
giữ nguyên provider/parser/classification dùng chung với probe, không thêm thư viện
ngoài, endpoint hay schema; không đưa CLI/harness vào app.

## Mở hai ứng dụng để nhìn trực tiếp

```powershell
.\scripts\demo-usb-integrated.ps1 -Desktop -ReadUsbStorage
```

Runner dựng PostgreSQL TEST riêng và hai JVM cùng JAR app; chỉ flow thí sinh được
server xác thực mới khởi động USB, dù người dùng đăng nhập hai cửa sổ theo thứ tự nào.
Notepad hướng dẫn không chứa mật khẩu; dùng tài khoản phát triển MOCK đã công bố
trong AGENTS.md, chỉ trên cluster loopback riêng. Không đọc `.env`/DB phát triển chung.
Màn trang chủ thí sinh có thông báo USB trước khi vào phòng thi.

1. Thí sinh đăng nhập → chọn lượt thi → **Bắt đầu giám sát** → **Vào phòng thi & làm bài**.
2. Giám thị → **Timeline đa tín hiệu** → chọn cùng lượt thi → nguồn
   `WINDOWS_USB_STORAGE` (hoặc Tất cả nguồn) → bảng lịch sử dưới nhóm cảnh báo.
3. USB cắm sẵn: `USB_STORAGE_PRESENT_AT_START`. Rút USB, giữ khoảng 10 giây:
   `USB_STORAGE_REMOVED`. Cắm lại, giữ khoảng 10 giây: `USB_STORAGE_CONNECTED`.
4. Thí sinh thoát màn thi/đăng xuất; giám thị đăng xuất; đóng cả hai app để runner
   dọn DB/server. Kiểm receipt cleanup trước khi gọi hoàn tất.

Polling có thể bỏ sót thao tác giữa hai scan. READY là lịch sử nguồn, không bảo đảm
nguồn vẫn khỏe. UNKNOWN/ERROR không thành count0/removal. Slot là định danh yếu,
metadata PARTIAL; không đọc file/serial và không kết luận gian lận.

## Cấu hình và vòng đời

Nếu dùng các script chạy thường với DB phát triển TEST hiện có, cần bật ở **cả server
và client** (mặc định vẫn OFF):

```powershell
.\scripts\start-monitoring-server.ps1 -SignalExperiment
.\scripts\start-monitoring-client.ps1 -ServerUrl http://localhost:8080 -ReadUsbStorage
```

Mở hai client bằng lệnh client trên; đăng nhập hai role theo server. `-ReadUsbStorage`
tự bật gate signal phía client, kiểm JAR chứa provider/resource mới trước launch.
Cả hai script có `-ValidateOnly` để kiểm cấu hình mà không khởi động JVM; server không
đọc `.env` ở chế độ này. Nếu app đã chạy, cần đóng/khởi động lại để nhận cờ mới.
Server cũng phải hỗ trợ typed signal; chỉ bật client chưa đủ. Thí sinh cần vào màn thi
sau khi bắt đầu giám sát. App-image/exe mở trực tiếp không tự nhận các cờ từ script JAR.

| Java system property | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `toeic.monitoring.signal-experiment-enabled` | `false` | Gate typed signals hiện có, client/server |
| `toeic.monitoring.usb-enabled` | `false` | Bật nguồn USB trong app thí sinh |
| `toeic.monitoring.usb-cadence-millis` | `1000` | Delay sau query, 250–10000 ms |
| `toeic.monitoring.usb-query-timeout-millis` | `5000` | Timeout query, 100–10000 ms |

Chỉ bắt đầu sau capability đủ ba loại USB và scoped heartbeat xác nhận collector
lease hiện tại. Dùng chung context/sequence/status emitter/queue/socket. Mất kết nối
thì pause/invalidate generation; chỉ resume sau xác thực capability/heartbeat mới.
Query cũ không vào generation mới. Stop/revoke/logout đóng nguồn; không đọc resource,
scan hoặc chờ cleanup trên FX thread. `awaitStopped` ngoài FX chờ cả USB và delivery.

Demo lưu inventory/hash source và JAR kể cả working tree chưa commit; từ chối nếu
source đổi lúc build. Test tự động không thay cho thao tác và quan sát vật lý.
PnP comparison, ADR/retention, H01–H12 và owner review còn pending; không tự đánh task
DONE hay bật mặc định ngoài lượt thử nghiệm được ủy quyền.
