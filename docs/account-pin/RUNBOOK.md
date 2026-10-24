# ACCOUNT_PIN — chạy và demo

Luồng chính: **Admin cấp tài khoản → Proctor chọn đề, tạo phòng → Candidate đăng nhập, nhập PIN chung → READY → Proctor START → làm bài, autosave và Submit.** Không cần lập danh sách Candidate trước hoặc phát PIN riêng.

## Chuẩn bị Server

- Java 21, PostgreSQL, Client và Server cùng bản build. Sao lưu DB trước khi nâng cấp; Flyway tự áp dụng migration V21 khi khởi động. Không sửa migration cũ hoặc chạy binary cũ vào DB mới mà chưa kiểm chứng.
- Điền DB và các media root trong `.env` theo `.env.example`. `TOEIC_AUDIO_ROOT` và `TOEIC_IMAGE_ROOT` phải chứa đúng file của đề đã import. Đề STUDY4 cần root STUDY4; không thay bằng media MOCK.
- Tạo **một lần** khóa HMAC cho PIN bằng PowerShell, rồi lưu dòng được tạo vào `.env` cục bộ của Server hoặc secret store. Không đưa vào Git, screenshot hoặc báo cáo:

```powershell
$bytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
'TOEIC_ROOM_PIN_KEY=' + [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_')
```

Giữ nguyên khóa qua mỗi lần restart và sao lưu cùng cấu hình bí mật. Đổi/mất khóa làm PIN hiện có không tra cứu được. Không tự tạo khóa mới mỗi lần chạy. Thiếu khóa: luồng legacy vẫn khởi động; tạo/JOIN phòng ACCOUNT_PIN bị từ chối rõ ràng.

Từ repository, chạy launcher đã đọc `.env`:

```powershell
.\scripts\start-monitoring-server.ps1 -JavaHome 'DUONG_DAN_JDK_21' -BindAddress 0.0.0.0 -Port 8080
```

Build theo README trước khi chạy. Server đang chạy từ JAR cũ không tự cập nhật khi source thay đổi. Các menu chạy cục bộ tự ép media MOCK không phù hợp với DB chứa đề STUDY4.

Bootstrap Admin dùng cơ chế hiện có: đặt `TOEIC_ADMIN_BOOTSTRAP_USERNAME` và `TOEIC_ADMIN_BOOTSTRAP_PASSWORD` riêng trong môi trường tiến trình Server; không dùng chung seed password. Không có public signup. Sau khi bootstrap thành công, dùng tài khoản đó đăng nhập trang Admin. Xem thêm [ADMIN-DEMO.txt](../phase45/ADMIN-DEMO.txt).

Trong portable bundle, `Start-Server.bat` dùng biến môi trường tiến trình; không tự đọc `.env` của repository. Cần cấp DB, seed password, media roots và `TOEIC_ROOM_PIN_KEY` trước khi chạy. Dùng **đường dẫn tuyệt đối** cho audio/image roots: các đường dẫn tương đối trong `.env` của repository không tự trỏ về repository khi chạy từ bundle. `Start-Client.bat -ServerUrl http://IP_SERVER:8080` mở Client.

## Demo với hai Candidate

1. Admin đăng nhập, tạo một Proctor và hai Candidate. Mỗi tài khoản có mật khẩu ngẫu nhiên riêng, chỉ hiển thị một lần; bàn giao riêng cho đúng người. Candidate bị mất mật khẩu: Admin chọn **Cấp mật khẩu**, xác nhận một lần. Không thể reset khi người đó đang thi. Reset thu hồi toàn bộ token cũ.
2. Proctor đăng nhập, vào **Điều khiển ca → Phòng thi TOEIC**, chọn đề đã xác minh media rồi **Tạo phòng**. Nhận PIN 5 số, sao chép và cung cấp cho lớp. PIN tự ẩn sau hai phút; clipboard do nút Copy tạo được xóa sau 30 giây nếu còn cùng giá trị.
3. Mở hai Client riêng. Mỗi Candidate đăng nhập bằng mã và mật khẩu được Admin cấp, nhập cùng PIN, bấm **Vào phòng**. Không nhập Room Code hoặc mã Candidate lần nữa.
4. Mỗi Client tải/chuẩn bị audio, thử loa, xác nhận nghe được và xác nhận sẵn sàng; phần mềm kiểm tra ảnh Part 1. Proctor theo dõi JOINED, kết nối và READY. JOIN lặp không tạo Attempt mới.
5. Proctor START. Phòng chưa có ai JOIN bị từ chối. Nếu có người chưa READY, chỉ có một cảnh báo xác nhận: người đó mất Listening của lượt này. Khi tất cả READY thì START trực tiếp.
6. Người READY đúng lúc START nghe Listening một lần và làm Reading. Người chưa READY hoàn tất chuẩn bị rồi làm **Reading trong thời gian còn lại**; không phát bù Listening, không cộng thời gian. Deadline do Server quyết định chung cho phòng.
7. Chọn đáp án, chờ autosave, kiểm tra cảnh báo Monitoring trên dashboard, rồi Submit và xác nhận. Reconnect transport không cấp lại writer hoặc reset deadline; mở bài trên Client mới vẫn tuân theo takeover/no-replay cũ.

Mất response tạo phòng: **không tự tạo lại**. Tải danh sách phòng, chọn phòng vừa tạo; PIN không được lưu plaintext nên dùng **Cấp lại PIN** trước START. PIN cũ và admission token cũ hết hiệu lực; membership, Attempt và dữ liệu bài thi giữ nguyên. Không cấp lại PIN sau START. Giới hạn 100 Candidate/phòng.

Nhập sai PIN 5 lần trong cửa sổ 5 phút khóa JOIN tài khoản 5 phút; đăng nhập lại không xóa hạn mức. PIN sai không đồng nghĩa mật khẩu/token tài khoản hết hạn. Luồng phòng V2/PIN cá nhân cũ và ca V1 nằm ở tab riêng; tài khoản legacy còn được dùng nút **Ca thi được phân công trước đây** sau đăng nhập Candidate. Candidate trước đây chỉ được tạo để vào phòng bằng PIN cá nhân có thể chưa có mật khẩu được bàn giao: Admin cấp lại mật khẩu trước khi dùng luồng mới; migration không tự thay credential cũ. Khi PIN phòng được cấp lại, Client đang dùng admission token cũ cần xác thực lại và nhập PIN mới.

## Checklist LAN và nghe vật lý

Tình trạng nghiệm thu hai máy Windows thật và người nghe trực tiếp: **NOT TESTED** nếu chưa có thiết bị. Test JavaFX cùng máy/HTTP/WS loopback không thay thế các bước dưới đây.

- [ ] Hai máy cùng LAN; xác định IPv4 Server bằng `ipconfig`. Client nhập `http://IP_SERVER:8080`, không dùng `localhost` của máy Candidate.
- [ ] Server bind `0.0.0.0`, cổng 8080 (hoặc cổng được chọn). Cho phép inbound TCP đúng cổng trên Windows Firewall, profile Private. Không mở PostgreSQL ra LAN chỉ để Client thi.
- [ ] Từ máy Candidate: `Test-NetConnection IP_SERVER -Port 8080` thành công; đăng nhập qua HTTP được. WebSocket `/ws/v1/realtime` kết nối, dashboard nhận Candidate và cảnh báo.
- [ ] Tải audio và ảnh qua cùng Server URL; không phụ thuộc đường dẫn ổ đĩa của máy Server trên máy Client. Không lỗi thiếu file/checksum.
- [ ] Người thật nghe sample bằng loa/tai nghe, xác nhận âm lượng/kênh âm thanh; không tự động coi sự kiện MediaPlayer hoàn thành là bằng chứng nghe được.
- [ ] Hai Candidate READY, START, nghe hết Listening không phát lại, làm Reading, autosave và Submit. Ghi version, giờ, máy và kết quả thực tế.
- [ ] Lặp với một người chưa READY: có cảnh báo; người sau chỉ làm Reading, deadline giống phòng. Thử mất mạng/reconnect, kiểm tra deadline và Listening không reset.
- [ ] Restart Server bằng quy trình có kiểm soát: phiên đang thi giữ deadline/answers; Listening đang phát chịu interruption/no-replay theo contract hiện có.
- [ ] Lưu bằng chứng đã che PIN/password/token; ghi riêng automated, same-machine GUI, LAN và physical hearing PASS/FAIL/NOT TESTED.

## Lệnh kiểm thử

Runner `scripts/verify-room-ux.ps1` tạo cluster PostgreSQL riêng, không dùng DB ứng dụng. Ví dụ đổi các đường dẫn theo máy:

```powershell
.\scripts\verify-room-ux.ps1 -Mode Targeted -PostgresBin 'DUONG_DAN_PG_BIN' -JavaHome 'DUONG_DAN_JDK_21' -MavenCommand 'DUONG_DAN_MVN_CMD' -SelectedTests 'AccountPinPostgresTest,RoomPinCodecTest,RoomAdmissionPostgresTest,RichSessionLifecyclePostgresTest,AdminAccountPostgresTest,RoomTokenAuthorizationTest'
.\scripts\verify-room-ux.ps1 -Mode Gui -PostgresBin 'DUONG_DAN_PG_BIN' -JavaHome 'DUONG_DAN_JDK_21' -MavenCommand 'DUONG_DAN_MVN_CMD' -SelectedTests 'AccountPinFlowPostgresGuiTest,RoomFlowPostgresGuiTest,AdminAccountGuiTest,ProctorV2SessionPaneGuiTest,LoginSceneGuiTest'
.\scripts\verify-room-ux.ps1 -Mode Full -AllGui -PostgresBin 'DUONG_DAN_PG_BIN' -JavaHome 'DUONG_DAN_JDK_21' -MavenCommand 'DUONG_DAN_MVN_CMD'
```

`result.json` chỉ tính XML sinh trong invocation đó. SKIP luôn ghi riêng. Log/XML có thể chứa cấu hình môi trường kiểm thử, chỉ chia sẻ bản đã rà soát; không commit các thư mục `.private`.
