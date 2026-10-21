# Desktop monitoring TEST — đang kiểm cùng người dùng

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
