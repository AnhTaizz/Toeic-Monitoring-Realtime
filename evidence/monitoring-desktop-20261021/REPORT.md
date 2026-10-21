# Desktop monitoring TEST — lượt người dùng và cleanup đã kiểm chứng

Base main `2a596deb50c52dbc0abf656f902046dae9e4030e`, Draft PR96; chỉ scripts/docs, không thay runtime Java/protocol/schema/defaults. FULL giữ mặc định; demo bật signal/alert cho process TEST. USB/LAN/second-display NOT_RUN; không MON DONE/human approval/policy verdict.

## Lượt đầu (chưa desktop PASS)

Build từ clean source `d50a4649142a31e2094c594a29c97abec431bf5c`; Maven package SUCCESS, tests skipped. Client JAR SHA256 `4688746213c40ca8acd53b96640dd837e86857cbef99c0a871086ba9fcb40a37`, server JAR `234a457ced645a162c8fe9495a18842c62e5a31df87dbd998b8cb606fe70711b`. Hai production JVM riêng, loopback HTTP/WS/PG thật; không JavaFX programmatic input hoặc MOCK observer. Đề một câu Reading tự soạn là TEST.

Người dùng báo sai mật khẩu. Kiểm đúng password file qua REST và chính production LoginApiClient trong JAR: cả candidate1/proctor1 PASS. Không xác định chính xác thao tác nhập lỗi, không gọi rerun là root cause. Code login có nút tài khoản mẫu xóa password; đã hướng dẫn chọn account trước, dán password sau. Người dùng sau đó xác nhận **đã vào**. Server thực tế SYNCED, sequence31 tại 12:23:26 UTC; typed7 gồm display1/status1/focus2/window INITIAL1/activity2. Không dùng số process/history chung để khẳng định owned TEST process đã được mở.

Thread dump lúc 12:23–12:24 UTC cho thấy role thực đăng nhập ngược nhãn launcher: slot candidate/profile có proctor workers; slot proctor/profile có process/full/signal workers. Nhãn PID/profile không thay role server. Không gọi thread name có chữ mock là provenance nguồn: runtime sources vẫn observer JavaFX hiện có, không test seam/source MOCK.

Lúc hết budget30 phút, chưa phát hiện owned chrome.exe TEST hoặc event minimized=true, chưa xác nhận proctor nhìn đúng dữ liệu/Stop/logout/đóng app chủ động. `appsClosedBeforeTeardown=false`; không coi auto teardown là thao tác người dùng. 12:25:35 UTC cleanup: apps0/serverfalse/workloads0, schema0, drop database0, cluster stop0, postmaster PID absent. Raw/artifact chỉ ở local ignored `target/desktop-first`, không có link GitHub tới raw.

Config/result cũ có mode=Mock do generic USB runner trước thay đổi metadata; chế độ Desktop không gọi nguồn USB/MOCK. Giữ nguyên raw cũ. Metadata Desktop được sửa ở `cd13b16`, không gán source mới cho lượt cũ. Budget cho handoff tiếp theo60 phút (`93d0e8d`), không đổi timeout production. Credential guide tiếp theo mang tên run riêng để tránh chọn nhầm file lượt cũ.

## Kiểm chứng mã và CI

PowerShell parser3script:0lỗi; guard3case Desktop trộn USB/Real/Integrated đều từ chối trước provisioning. [CI39596153087 tại cd13b16](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39596153087): SUCCESS, Maven1464 discovered=1307PASS/157SKIP/0failure/error; Python27PASS, LAN-kit MOCK script PASS (không LAN acceptance). CI này trước budget/credential filename updates; cần đối chiếu HEAD mới. Chưa merge PR96.

Tiếp tục cần thao tác thật: login/start/enter exam → owned TEST process/focus/minimize → proctor xác nhận → Stop/logout/close và cleanup. Hướng dẫn [ở đây](../../docs/MONITORING_DESKTOP_DEMO.md). Không chốt desktop PASS khi thiếu xác nhận.

## Lượt thứ hai — user sequence và cleanup đã kiểm chứng

Nguồn build sạch `4aacab4264e3dda2dfba10be3e11e2ef9eab7a29`, run `usb-58b5512e26404bc8`, 12:27:02–13:04:18 UTC (19:27–20:04 UTC+7), hai production JVM riêng. Maven package SUCCESS; hai JAR byte-identical với hash lượt đầu vì Java runtime không đổi. [Receipt](receipt.json) ghi SHA, hash, counts và raw-file hashes. Raw/log/JAR chỉ local ignored, không gắn link GitHub giả; log server lớn vẫn giữ private. Credential/token không nằm trong bản công bố.

Người dùng thao tác đăng nhập/start/vào thi, mở process TEST, đổi focus/thu nhỏ/khôi phục. Server FULL state SYNCED có đúng PID27812 của executable TEST, metadata COMPLETE; history có Chrome mới sau thời điểm process start. DTO history không có PID: liên hệ history chỉ theo tên/thời gian, không gọi mọi Chrome event là owned TEST event.

Người dùng ban đầu chỉ thấy process. REST/production MonitoringApiClient đọc đủ9endpoint PASS; production TimelineController diagnostic qua HTTP/WS thật trả123rows/stale=false, gồm24focus/14window. Đây là diagnostic read-only, không JavaFX input hoặc thay thế desktop acceptance. Sau khi mở **Timeline đa tín hiệu**, người dùng xác nhận: “à vào timeline đa tín hiệu thì thấy focus và window rồi”. Không sửa runtime/parser/controller khi chưa có lỗi implementation tái hiện; hướng dẫn được sửa rõ menu, bộ lọc Tất cả nguồn và bảng dưới nhóm cảnh báo. Không kết luận lỗi mật khẩu ban đầu đã rõ root cause từ các lần kiểm PASS.

Người dùng xác nhận **Thoát màn thi, Dừng giám sát đã mờ**. Đây là Stop do vòng đời thoát màn thi, không giả nhận một lần bấm nút Stop riêng. Kiểm thread sau exit:0 process/full-snapshot/monitoring-delivery/signal workers; TEST process đã thoát. Sequence1810/typed113 giữ nguyên tới trước khi app đóng. Các HTTP/exam/realtime workers của portal còn hợp lệ trước logout.

Người dùng xác nhận **đăng xuất về login và đóng cả hai app**. Runner ghi `appsClosedBeforeTeardown=true`; không force-close app để thay thao tác người dùng. Kiểm sau teardown: candidate/proctor/server/TEST PIDs đều absent, apps0/serverfalse/workload0; schema0, dropDB exit0, PGstop exit0, postmaster PID absent. State cuối STALE giữ snapshot/history theo thiết kế, không gọi STALE là collector còn chạy. Raw runner vẫn có desktopAcceptance=PENDING_USER_CONFIRMATION vì không tự ký xác nhận người dùng; receipt/report ghép riêng xác nhận thực tế, không sửa raw.

Final typed113 = display1/status1/focus28/window17/activity66. Process history153 là toàn bộ policy hiện có của candidate, không phải153 TEST events. Đây không phải đo E1/E2, số hành vi vi phạm hoặc bằng chứng hardware. Cảnh báo vẫn EXPERIMENTAL; FULL default, repository signal/alert OFF. USB/hotplug/second-display/LAN NOT_RUN, DELTA NOT_DECIDED, không MON DONE/human A/B/C approval/policy decision.

[CI39599752809 tại build4aacab4](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39599752809) SUCCESS: Java1307PASS/157SKIP/0failure/error, Python27PASS, LAN-kit MOCK step PASS. Commit cuối chỉ hướng dẫn/report/receipt, không Java/runtime/wire/schema/default changes; CI và merge theo HEAD cuối ở [PR96](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/96). Hai startup FAIL lịch sử không tái hiện cản lượt này; không suy root cause từ thành công của lượt mới.
