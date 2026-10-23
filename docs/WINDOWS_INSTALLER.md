# T3-B5 · Bộ cài Windows và bàn giao máy thứ hai

MSI `ToeicMonitor-0.3.5.msi` cài client cho tài khoản Windows hiện tại. Client có Java 21 và JavaFX đi kèm; máy dùng không cần JDK, Maven, WiX hay IDE. Máy build vẫn cần các công cụ này. Chỉ đóng gói chức năng đã có trên main: đăng nhập, monitoring FULL, dashboard/điều khiển ca B4 và chuẩn bị/phát/khóa Listening B1–B3. Reading–Listening PR35 còn draft, chưa tích hợp vào installer này.

## Tạo installer trên máy build

Dùng Windows x64, JDK 21 của dự án, Maven, WiX **3** (`candle.exe`, `light.exe`) và Windows SDK `mt.exe` x64. Script kiểm tra công cụ, version MSI và Git sạch trước build. JDK 21 dùng WiX 3; không thay JDK cả dự án để dùng cú pháp WiX 4/5. Chọn MSI per-user để không cần cấp quyền Administrator cho ứng dụng.

Có thể dùng WiX portable và Microsoft SDK BuildTools trong `target/`, không cần cài toàn hệ thống. Ví dụ tải đúng các bản đã kiểm trên máy build:

```powershell
New-Item -ItemType Directory -Force target/t3-b5/tools | Out-Null
curl.exe -fLsS -o target/t3-b5/tools/wix.zip https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip
if ($LASTEXITCODE -ne 0) { throw 'Download WiX failed' }
curl.exe -fLsS -o target/t3-b5/tools/sdk.zip https://api.nuget.org/v3-flatcontainer/microsoft.windows.sdk.buildtools/10.0.26100.6584/microsoft.windows.sdk.buildtools.10.0.26100.6584.nupkg
if ($LASTEXITCODE -ne 0) { throw 'Download SDK failed' }
Get-FileHash target/t3-b5/tools/wix.zip,target/t3-b5/tools/sdk.zip
Expand-Archive target/t3-b5/tools/wix.zip target/t3-b5/tools/wix
Expand-Archive target/t3-b5/tools/sdk.zip target/t3-b5/tools/windows-sdk
Get-AuthenticodeSignature target/t3-b5/tools/windows-sdk/bin/10.0.26100.0/x64/mt.exe
```

SHA-256 tải quan sát được: WiX ZIP `6ac824e1642d6f7277d0ed7ea09411a508f6116ba6fae0aa5f2c7daa2ff43d31`; SDK package `0fe1f6a5e83781e72a6506c94aed73f17897e0ac4421aedd13560baddd228586`. Đây là hash ghi nhận khi tải từ nguồn chính thức, không phải checksum do nhà phát hành công bố độc lập. `mt.exe` đã có chữ ký Microsoft hợp lệ. Không đưa tools/ZIP vào Git.

```powershell
$jdk = '<thư mục JDK 21>'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/package-windows.ps1 `
  -JavaHome $jdk -WixHome target/t3-b5/tools/wix `
  -ManifestTool target/t3-b5/tools/windows-sdk/bin/10.0.26100.0/x64/mt.exe
```

Thêm `-Offline` nếu Maven cache đã đủ. Có thể truyền tool ở vị trí khác; không có đường dẫn cá nhân trong script. `-AllowDirty` chỉ cho bản phát triển, metadata ghi `dirty=true`; không dùng làm artifact bàn giao. Tham số `-AppVersion` mặc định `0.3.5`, tuân giới hạn MSI `255.255.65535`; tăng version khi phát hành bản khác. UUID nâng cấp được giữ ổn định, chưa triển khai auto-update hay ký số installer.

Script clean/build riêng `protocol,client` bằng Maven (`-DskipTests`, **không phải một lượt chạy test**), chỉ copy client shaded JAR vào staging; tái sử dụng app-image B1 rồi thêm `build-info.json`. `mt.exe` thêm `activeCodePage=UTF-8` vào manifest EXE để hỗ trợ đường dẫn tiếng Việt, giữ manifest DPI/asInvoker gốc. MSI được tạo từ chính image đã sửa; không sinh lại launcher. Runtime chứa module JDK cần cho HTTP/TLS/desktop và `java.instrument` để chạy agent kiểm thử ngoài package; installer không chứa agent.

Đầu ra `target/t3-b5/package-<UUID>/`: MSI, `.msi.sha256`, `build-info.json`, log build. Metadata có version, commit đầy đủ, dirty flag, runtime/tool versions và hash JAR/MSI. Bên trong app chỉ có metadata nguồn và hash JAR (MSI hash nằm ở file bên ngoài, tránh tự tham chiếu). Git thay đổi giữa lúc build sẽ làm script từ chối bàn giao. App-image trung gian ở `jpackage-out/`; các output này đã được ignore, không commit EXE/MSI/JAR/runtime/raw log.

## Cài, cấu hình, chạy và gỡ

Chuyển **MSI + `.sha256` + `build-info.json`** cùng thư mục sang máy dùng. So hash bằng `Get-FileHash -Algorithm SHA256 '<file.msi>'` với báo cáo và file bàn giao. Nhấp đúp MSI, chọn thư mục có khoảng trắng/dấu tiếng Việt, hoàn tất cài. Mở **ToeicMonitor** ở Start Menu. Installer chưa ký số; không tắt cơ chế bảo vệ Windows để né thông báo. Ghi lại nếu chính sách của máy chặn cài/chạy.

Nhập URL của máy server trong ô **Server**, ví dụ `http://192.168.1.20:8080` (địa chỉ minh họa, thay IP thật). Hoặc khởi động từ PowerShell:

```powershell
$env:TOEIC_SERVER_URL = 'http://192.168.1.20:8080'
& '<thư mục đã cài>\ToeicMonitor.exe'
```

Biến trên có hiệu lực cho lần chạy từ terminal đó; Start Menu dùng môi trường Windows của người dùng, hoặc nhập lại URL tại login. System property `toeic.server.url` có ưu tiên cao hơn biến môi trường. Không ghi token/mật khẩu vào launcher/config/command line. Dùng tài khoản được server cấp và nhập mật khẩu qua màn hình đăng nhập.

Gỡ bằng **Settings → Apps → Installed apps → ToeicMonitor → Uninstall**, hoặc:

```powershell
Start-Process msiexec.exe -Wait -ArgumentList '/x "<đường dẫn MSI>"'
```

MSI chỉ gỡ các file cài. Cache/journal người dùng được giữ: không xóa dấu chống phát lại để nghe lại một lượt đang thi. Khi cần tổ chức lại, giám thị tạo attempt mới bằng B4.

## Tách dữ liệu client và server

| Vị trí | Nội dung / quyền ghi |
|---|---|
| `<install>/ToeicMonitor.exe`, `app/`, `runtime/` | Chương trình, JAR JavaFX, metadata, runtime. Chỉ đọc khi chạy; không chứa `.env`, server, PostgreSQL hay audio đề. |
| `%LOCALAPPDATA%/TOEIC Monitor/audio-cache/files` | Audio tải từ server, chia theo hash attempt/manifest; ghi bằng quyền người dùng. |
| `audio-cache/speaker`, `audio-cache/playback-started` | WAV thử loa tạm và journal chống phát lại/gián đoạn. Journal được giữ qua restart/uninstall. |
| `%TEMP%` của người dùng | JavaFX/native library tạm; không cần ghi vào thư mục cài. |
| Log/trace | Ứng dụng thường không tạo file log/trace. Measurement/trace mặc định OFF; nếu chủ động bật, phải cấu hình `toeic.measurement.directory` / `toeic.trace.directory` tới thư mục người dùng có quyền ghi (mặc định CLI dev là đường dẫn tương đối). |
| Máy server | Spring Boot, PostgreSQL, `.env` riêng, `<TOEIC_AUDIO_ROOT>/<examId>/<audioFile>` và `<TOEIC_IMAGE_ROOT>/<examId>/<localFile>`. Không copy các dữ liệu này vào MSI hoặc sang thư mục cài client. |

## Chạy kiểm thử tự động trên máy build

```powershell
docker compose up -d --wait
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-windows-installer.ps1 `
  -Installer '<MSI vừa tạo>' -JavaHome $jdk -FullSuite
```

Chạy bằng tài khoản thường. Script từ chối nếu đang elevated hoặc đã cài ToeicMonitor (tránh thay/xóa bản đang dùng). Nó sở hữu một lần cài TEST vào `%LOCALAPPDATA%/TOEIC B5 TEST/<UUID>/Cài đặt tiếng Việt có khoảng trắng/ToeicMonitor`, chặn ghi bằng ACL, kiểm toàn bộ file cài không thay đổi, rồi khôi phục ACL/gỡ trong `finally`.

Lượt đầu mở launcher **không agent**, bỏ JAVA_HOME/JVM options và chỉ giữ Windows trên PATH. Jpackage fork JVM con: probe đi theo đúng child/path, kiểm cửa sổ login và DLL `runtime/bin/server/jvm.dll`, gửi đóng cửa sổ, chờ cả wrapper/JVM thoát.

Lượt sau agent **TEST-only ở client/src/test**, tạo riêng dưới `server/target/`, điều khiển UI production trong EXE đã cài. Spring/HTTP/WS/Flyway/PostgreSQL thật chạy trên port/schema UUID riêng; không reset DB dev. Credential chỉ truyền qua environment tiến trình TEST, không vào MSI hoặc command line. Dùng profile LOCALAPPDATA TEST có dấu để không chạm cache người dùng. Có GUI login hai vai, tải READY B4, FULL với ACK/event thật, tải/checksum WAV, thử MediaPlayer, READY, WS start, ngắt socket server thực và reconnect giữ khóa, player disposal, journal và đóng app/worker/process. Audio đề fixture 30 giây im lặng; click “Tôi nghe được” **SIMULATED**, không phải người nghe xác nhận. Ngắt socket điều khiển là **SIMULATED network fault**, không rút dây mạng vật lý.

Không có `-FullSuite` thì chỉ chạy case installed integration; không được gọi kết quả đó là full regression. Nếu thiếu `TOEIC_INSTALLED_CLIENT`, JUnit bỏ qua case opt-in này. Script đọc `.env` cục bộ hoặc biến DB hiện có, không sửa/commit chúng. Output trong `target/t3-b5/install-<UUID>` và `server/target/t3-b5-installed/`; server test dọn schema/audio sở hữu, TEST cache/log để lại trong ignored target để đối chiếu. Nếu gỡ thất bại, script báo ProductCode/log để gỡ đúng bản TEST, không báo PASS.

## Checklist IT01 trên máy Windows thứ hai — chưa chạy

Máy thứ hai phải là máy Windows khác thật; hai JVM/cửa sổ/tài khoản trên máy build không đủ. Không cần mang repository, tools hoặc TEST agent sang máy thứ hai.

1. Ghi ngày/người chạy, Windows version/build/x64, có IDE/Java sẵn hay không (`where.exe java`, `java -version` nếu có). Ghi commit/version/hash MSI từ metadata, tính lại hash sau copy. Không suy ra “máy không Java” chỉ từ PATH đã được bỏ Java trên máy build.
2. Trên máy server, giữ DB/audio riêng; chạy `scripts/start-monitoring-server.ps1 -JavaHome '<JDK21>' -BindAddress 0.0.0.0 -Port 8080`. Có JAR server đã build, `.env` hợp lệ và audio theo [A1](AUDIO_RESOURCES.md). Không bật `-DeltaExperimental`. Chủ máy mở đúng cổng TCP qua chính sách mạng hiện có, không tắt firewall. Từ máy hai chạy `Test-NetConnection <IP-server> -Port 8080`.
3. Cài MSI bằng tài khoản thường vào đường dẫn có dấu/khoảng trắng. Mở Start Menu, đăng nhập với IP server. Ghi đường dẫn JVM thực bằng Task Manager/Process Explorer: child ToeicMonitor.exe tải `<install>/runtime/bin/server/jvm.dll`. Nếu Java không có sẵn mà app chạy, ghi rõ quan sát đó.
4. Giám thị mở tab **Điều khiển ca / kết quả**, import JSON/tạo ca mới theo [B4](PROCTOR_SESSION_CONTROLS.md). Cấp WAV hợp lệ riêng phía server, dùng mã TEST riêng. Đăng nhập lại candidate sau khi được cấp lượt. Không dùng ca monitoring demo như ca Listening.
5. Candidate chọn đúng lượt và bắt đầu monitoring: **FULL**, có xác nhận; giám thị thấy process. Tải audio; ghi kết quả checksum/thư mục cache ngoài install. **Thử loa**, người thật nghe mẫu rồi mới bấm **Tôi nghe được**, xác nhận READY. Giám thị refresh READY rồi Start Listening; người thật nghe audio đề và ghi kết quả. Không dùng fixture im lặng của automation để nghiệm thu nghe.
6. Khi đang phát, ngắt mạng máy candidate thực: audio dừng/khóa. Nối lại rồi mở lại app; không tự phát lại hoặc resume. Giám thị đọc lý do gián đoạn, tạo attempt mới nếu muốn thử lượt khác; không xóa journal/reset attempt cũ.
7. Đóng app, xác nhận cả wrapper/JVM con và worker không còn; mở lại login không lỗi quyền ghi. Gỡ qua Settings, launcher/shortcut biến mất; dữ liệu server và journal người dùng không bị xóa. Ghi PASS/FAIL từng thao tác, log/ảnh tối thiểu không có credential, người xác nhận nghe và reviewer A.

[Báo cáo local B5](../evidence/t3-b5/2026-10-15-verification.md) chỉ chứng minh phần đã chạy trên máy build. **IT01 vẫn PARTIAL** tới khi có bằng chứng máy hai không IDE và review/nghe thật. Không nghiệm thu T4/E1/E2 hay Reading–Listening PR35 từ kết quả đóng gói.

## Nguồn công cụ

[JDK21 packaging overview](https://docs.oracle.com/en/java/javase/21/jpackage/packaging-overview.html), [jpackage JDK21 options](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jpackage.html), [WiX3.14.1 portable](https://github.com/wixtoolset/wix3/releases/tag/wix3141rtm), [Microsoft SDK BuildTools](https://www.nuget.org/packages/Microsoft.Windows.SDK.BuildTools/10.0.26100.6584), [UTF-8 activeCodePage](https://learn.microsoft.com/en-us/windows/apps/design/globalizing/use-utf8-code-page), [msiexec options](https://learn.microsoft.com/en-us/windows/win32/msi/command-line-options). Đây là công cụ build/test; không thêm thư viện ngoài vào runtime Maven của dự án.


## Tích hợp 16/10/2026

Bản thống nhất giữ nền B4/MSI main, chuyển guard/progress/config cần thiết từ #38. Source và test mới xem [báo cáo](../evidence/unified-20261016/verification.md); không kế thừa PASS installer hoặc số test của nhánh nguồn. [Cách chạy hiện tại](UNIFIED_APPLICATION.md).
