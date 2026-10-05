# T1-B4 verification — 05/10/2026

Người chạy: Claude Code Agent, trên máy build của nhóm. Kết luận: **T1-B4 PARTIAL**. Đóng gói, luồng chức năng và audio trên bản đóng gói đã chạy thật trên máy build; **máy Windows thứ hai và LAN giữa hai máy NOT RUN**; có **một FAIL** về đường dẫn (app đặt trong thư mục có ký tự ngoài code page ANSI). Human A review NOT RUN. TRACKER.json không đổi.

## Source và môi trường

- Base main: `a04aaa20963b9377809c8d1c0ec8298f638244b0` (sau PR #8). Nhánh `feat/t1-b4-windows-package-audio`.
- Code được kiểm: `2d5a9853393edde0e28adb7ae5af033b850de5b9` (javafx-media, harness TEST, fixture) và `9cbaf8b33a878ab6ae1069262faaa7b7511be7ed` (script). Commit tài liệu sau đó không đổi source/script. Không có thay đổi nào trong `client/src/main`, `server/` hay `protocol/`.
- Máy build: Windows 11 Home Single Language 10.0.26200, 64-bit; code page ANSI 1252 (system locale en-US). Oracle JDK 21.0.8, `jpackage` 21.0.8, Maven 3.9.11, JavaFX 21.0.12, Docker Desktop + PostgreSQL 18 (container của dự án, bind `127.0.0.1`).
- Thư viện thêm: `org.openjfx:javafx-media:21.0.12` (đã ghi README). Không thêm thư viện audio ngoài.

## Build và test

| Lượt | Thời điểm (UTC+7) | Kết quả | Log |
|---|---|---|---|
| Baseline `mvn test` trên `a04aaa2` | 16:17:48 | PASS, 5/5 module, 331 test (client 220, server 110, spike 1), 0 failure/error/skipped | [baseline-test.txt](baseline-test.txt) |
| Final `mvn test` | 16:43:27 | PASS, 5/5 module, 338 test (client 227, server 110, spike 1), 0 failure/error/skipped | [mvn-test.txt](mvn-test.txt) |
| Final `mvn package` | 16:44:25 | PASS, 338 test, fat JAR client và JAR server được tạo | [mvn-package.txt](mvn-package.txt) |

7 test mới (`AudioSmokeHarnessTest`): header/kích thước WAV PCM, tone không im lặng, từ chối thời lượng sai, ghi file vào thư mục có khoảng trắng + dấu, khử đường dẫn/URI khỏi thông báo lỗi. Đây là test thuần, không mở JavaFX và không phát audio; việc phát audio được kiểm bằng smoke bên dưới.

Cảnh báo đáng chú ý trong log (không làm fail build): shade báo `module-info.class` và `META-INF/MANIFEST.MF` trùng giữa các JAR JavaFX (nay thêm `javafx-media`), 1 class trùng giữa `gson` và `error_prone_annotations`; Mockito nạp agent động; cảnh báo CDS của HotSpot. Khi chạy app từ fat JAR, JavaFX in `Unsupported JavaFX configuration: classes were loaded from 'unnamed module'` — đã có từ B1, app vẫn chạy. Các dòng `NativeCommandError` trong log là do Windows PowerShell 5.1 bọc stderr của Maven, không phải lỗi build.

## Đóng gói

Lệnh chính thức: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/package-client.ps1` (thêm `-WithAudioSmoke` cho bản kiểm thử). Script gọi:

```
jpackage --type app-image --dest jpackage-out --name ToeicMonitor --input client/target/jpackage-input
  --main-jar client-0.1.0-SNAPSHOT-all.jar --main-class vn.edu.toeic.client.Launcher
  --app-version 0.1.0 --vendor "TOEIC Monitor Team"
```

| Mục | Kết quả |
|---|---|
| Lệnh `jpackage` cũ trong README (`--input client\target`), chạy lại trên source hiện tại ra thư mục tạm | Exit 0, app mở được cửa sổ, nhưng ảnh nặng **170,5 MB, 457 file**: `app\` 22,5 MB chứa cả `test-classes`, `surefire-reports`, log smoke cũ và ba JAR trên classpath. Vì vậy script mới chỉ đưa fat JAR vào `--input` |
| Bản bàn giao | `jpackage-out\ToeicMonitor`: **158,4 MB, 287 file** — `runtime\` 147,5 MB, `app\` 10,4 MB — [package-client.txt](package-client.txt) |
| Bản kiểm thử (`-WithAudioSmoke`) | 158,9 MB, 290 file; thêm `AudioSmoke.exe` và `audio-smoke-TEST.jar` (12 KB) — [package-client-audio-smoke.txt](package-client-audio-smoke.txt) |
| Runtime kèm theo | Có `runtime\bin\server\jvm.dll`, `runtime\release` ghi Java 21.0.8, 64 module; **không có `java.exe`** nên app không dựa vào JDK cài trên máy |
| JAR trong hai bản | Cùng SHA256 `BB9BB5458D4F638497304744E73C5FF042C0E21BB152717437068FD0CA26BC94` (cùng một lượt `mvn package`) |
| Native JavaFX Media trong fat JAR | `jfxmedia.dll`, `gstreamer-lite.dll`, `glib-lite.dll`, `fxplugins.dll` |
| Harness/test trong JAR production | Không có (`vn/edu/toeic/client/audio`: 0 entry) |
| Lỗi module/shade khi đóng gói | Không có |

## Đường dẫn: khoảng trắng và dấu tiếng Việt

`scripts/smoke-b4.ps1`, lượt cuối trên bản kiểm thử: **21 PASS, 1 FAIL, 1 BLOCKED** — [smoke-b4.txt](smoke-b4.txt). Thư mục thử nằm dưới `%TEMP%\toeic-b4` (không ghi đường dẫn đầy đủ).

| Vị trí app-image | Mở cửa sổ `TOEIC Monitor` | Đóng cửa sổ, hết process | Ghi chú |
|---|---|---|---|
| Thư mục build (`jpackage-out`) | PASS | PASS | |
| `TOEIC Test\ToeicMonitor` (khoảng trắng) | PASS | PASS | |
| `Thử nghiệm TOEIC\ToeicMonitor` (dấu ngoài code page 1252) | **FAIL** | BLOCKED | Launcher thoát mã 2, không có cửa sổ |

Chẩn đoán tay (lệnh ad hoc, không nằm trong script):

- Cùng app-image chép vào `Thí nghiêm cp1252\` (chỉ dùng `í`, `ê` — có trong code page 1252): mở cửa sổ, đóng, exit 0.
- Chạy `ToeicMonitor.exe` trong `Thử nghiệm TOEIC\` có gắn console: `Error: could not find java.dll` / `Error: Could not find Java SE Runtime Environment.`, exit 2.
- Kết luận: lỗi phụ thuộc vào việc ký tự có biểu diễn được trong code page ANSI của máy hay không, không phải vào "có dấu" nói chung. Trên máy code page khác (ví dụ 1258) tập ký tự lỗi sẽ khác; **chưa đo trên máy như vậy**.

Thử nghiệm ngoài repo (chỉ sửa bản chép trong `%TEMP%`, không đưa vào script): ghi lại manifest của `ToeicMonitor.exe` và `AudioSmoke.exe`, thêm `<activeCodePage xmlns="http://schemas.microsoft.com/SMI/2019/WindowsSettings">UTF-8</activeCodePage>` vào `windowsSettings` (phải bỏ thuộc tính read-only của exe trước).

| Sau khi nhúng manifest UTF-8, app ở `Thử nghiệm TOEIC\` | Kết quả |
|---|---|
| `ToeicMonitor.exe` mở cửa sổ, đóng cửa sổ | Chạy được, exit 0, không còn process |
| Audio WAV / MP3 / M4A từ `Âm thanh mẫu\` | PLAYED cả ba (READY→PLAYING→STOPPED) |
| Đường dẫn có dấu truyền qua tham số dòng lệnh | Ghi được file (trước khi vá thì không) |

Đây là bằng chứng rằng có cách khắc phục, **không** phải trạng thái của bản bàn giao. Chưa chạy lại luồng login/giám sát với code page UTF-8. Quyết định ghi ở QD-11 (đang chờ).

## Audio — JavaFX Media

Harness `AudioSmokeHarness` (test source, nhãn TEST): cửa sổ có Play/Stop và trạng thái LOADING/READY/PLAYING/STOPPED/ERROR; nạp file bằng `new Media(path.toUri().toString())`; bắt `MediaException` khi tạo `Media`, `Media.onError` và `MediaPlayer.onError`; chế độ `--auto` tự Play, chờ thời gian phát ≥ 500 ms rồi Stop, ghi kết quả và tự thoát (không gọi `System.exit`, nên process treo sẽ lộ ra). Thông báo lỗi được khử đường dẫn trước khi ghi.

Fixture TEST: tone 440 Hz dài 2 giây. WAV PCM 16-bit 22,05 kHz mono do harness sinh (88.244 byte); MP3 96 kbps (25.158 byte) và AAC/M4A 96 kbps (25.593 byte) chuyển mã bằng bộ mã hóa có sẵn của Windows, commit trong `client/src/test/resources/audio/`.

| Định dạng | App-image, app ở thư mục build | App-image, app ở `TOEIC Test\` | JDK dev (`java -cp`) |
|---|---|---|---|
| WAV PCM | PASS — playedMs 502 / duration 2000 | PASS — 512 / 2000 | PASS — 592 / 2000 |
| MP3 | PASS — 521 / 2061 | PASS — 511 / 2061 | PASS — 571 / 2061 |
| AAC (M4A) | PASS — 511 / 2020 | PASS — 511 / 2020 | PASS — 581 / 2020 |
| File chữ đổi đuôi `.mp3` | PASS — ERROR `ERROR_MEDIA_INVALID`, tự thoát | PASS | (không chạy) |
| File không tồn tại | PASS — ERROR `FILE_NOT_READABLE`, tự thoát | PASS | (không chạy) |

Trong mọi dòng PLAYED, file audio nằm trong thư mục `... Âm thanh mẫu\` (khoảng trắng + `ẫ` ngoài code page): `pathHasSpace=true`, `pathHasNonAscii=true`. Sau mỗi nhóm lượt không còn process nào của app-image.

Giới hạn phải nói rõ:

- **PLAYED** = JavaFX báo PLAYING và `currentTime` tăng. Không ai nghe bằng tai trong phiên này; không coi là bằng chứng loa có tiếng.
- Lượt đầu ([smoke-b4-first-run-argv.txt](smoke-b4-first-run-argv.txt)) truyền đường dẫn audio qua tham số dòng lệnh: với thư mục `Âm thanh mẫu` harness không ghi/đọc được file dù app ở thư mục thường. Nguyên nhân cùng gốc với lỗi đường dẫn ở trên (argv của JVM đi qua code page ANSI). Harness chuyển sang đọc đường dẫn từ biến môi trường; trong sản phẩm thật đường dẫn audio do code Java dựng nên không gặp chuyện này. Trong log lượt đầu, các dòng FAIL của `unicode-path` là do app không khởi động, còn các dòng FAIL của `space-path` và `dev-jdk` là do argv.
- Thao tác tay Play/Stop bằng UI Automation trên `AudioSmoke.exe` (MP3 trong thư mục có dấu): READY → Play → PLAYING → Stop → STOPPED, [ảnh](screenshots/audio-smoke-playing.png). Ảnh này chụp từ bản kiểm thử của lượt build trước lượt final (cùng source, JAR SHA256 `20B756D8…`).
- Chỉ thử tone 2 giây; chưa thử file dài, VBR, hay Windows bản N.

QD-07 đã chốt theo các số liệu này: MP3, file ngoài JAR, nạp bằng URI. Chi tiết trong `docs/QUYET_DINH.md`.

## Luồng chức năng trên bản bàn giao (server thật, cùng máy)

Server: `java -jar server\target\server-0.1.0-SNAPSHOT.jar` với biến `DB_*` từ `.env`, lắng nghe `0.0.0.0:8080`; PostgreSQL container của dự án. Lần khởi động đầu Flyway áp dụng V2–V4 lên schema `public` của DB dev (trước đó V1), không reset. Fixture TEST `DEMO-C3-A` tạo bằng `scripts/demo-c3.ps1 -Action Create`. Tài khoản seed MOCK `candidate1`/`proctor1`.

Client: `jpackage-out\ToeicMonitor\ToeicMonitor.exe` của **bản bàn giao** (JAR `BB9BB545…`), hai instance. Thao tác bằng Windows UI Automation trên chính các control JavaFX (đặt giá trị ô nhập, bấm nút, chọn dòng/tab); mật khẩu gửi bằng `WM_CHAR` vào cửa sổ app; ảnh chụp bằng `PrintWindow` nên chỉ chứa cửa sổ app. Đây là tương tác GUI thật trên bản đóng gói nhưng do Agent điều khiển, **không phải** thành viên tự thao tác.

Lượt 16:47:24–16:48:13: **18/18 bước PASS** — [packaged-gui-flow.txt](packaged-gui-flow.txt).

| Bước | Kết quả |
|---|---|
| Không đặt env → ô Server hiện `http://127.0.0.1:8080` (sửa được) | PASS |
| Sai mật khẩu → "Tên đăng nhập hoặc mật khẩu không đúng", vẫn ở màn đăng nhập | PASS — [ảnh](screenshots/login-wrong-password.png) |
| Proctor: gõ `http://192.168.x.x:8080` vào ô Server rồi đăng nhập → "Giám sát thí sinh", WS "Đã kết nối" | PASS |
| Candidate: `TOEIC_SERVER_URL=http://192.168.x.x:8080` → ô Server điền sẵn; đăng nhập → "Giao diện thí sinh", "Kết nối thời gian thực sẵn sàng" | PASS |
| Candidate thấy lượt `DEMO-C3-A`, bấm "Bắt đầu giám sát" → `Chưa xác nhận: 0 · Đã xác nhận: 21` với 21 process `msedge.exe` đang chạy sẵn trên máy (ProcessHandle thật) | PASS — [ảnh](screenshots/candidate-monitoring.png) |
| Sau 6 giây số xác nhận vẫn 21 (không sinh event mới ở mỗi poll) | PASS |
| Proctor chọn dòng `DEMO-C3-A` → "Quan sát thấy msedge.exe", trạng thái ONLINE "Vừa nhận heartbeat hợp lệ" | PASS — [ảnh](screenshots/proctor-dashboard-online.png) |
| Đóng cửa sổ candidate khi đang giám sát → process thoát; proctor chuyển UNKNOWN "Quá thời gian chờ heartbeat", tab lịch sử có dòng gián đoạn | PASS — [ảnh](screenshots/proctor-dashboard-unknown.png) |
| Đóng cửa sổ proctor → process thoát; không còn `ToeicMonitor.exe` | PASS |

Ghi chú trung thực về lượt này:

- URL `192.168.x.x` là IPv4 LAN (Wi-Fi) **của chính máy build**. Nó cho thấy client không dựa vào `localhost` và server nhận kết nối trên địa chỉ LAN, nhưng không đi qua mạng giữa hai máy và không qua tường lửa. Không tính là kiểm LAN.
- Bộ đếm proctor hiện "Cảnh báo: 43" và "Gián đoạn: 2": gồm 22 cảnh báo + 1 gián đoạn của lượt chạy thử trước đó (16:38, cùng fixture, bản build trước) vì `demo-c3.ps1 -Action Cleanup` lỗi nên fixture không reset được giữa hai lượt (xem dưới). Lượt này tạo 21 cảnh báo mới, khớp 21 process.
- Mỗi instance app-image gồm hai process `ToeicMonitor.exe` (launcher của jpackage và process con chứa JVM); process launcher thoát chậm hơn cửa sổ dưới 3 giây. Kiểm "hết process" được làm sau khoảng chờ đó.
- Sau khi đóng app: 0 process `ToeicMonitor`/`AudioSmoke`. Các process `java.exe` còn lại trên máy là language server của IDE, có từ trước phiên, không thuộc app. Hai lượt server không có dòng ERROR/Exception trong log.
- Ảnh dashboard lượt cuối bị cắt mép phải/dưới (cửa sổ 1100×740 lệch khỏi màn hình khi `PrintWindow`); nội dung là ảnh thật, không chỉnh sửa. Ảnh không chứa token, mật khẩu hay địa chỉ IP.
- Chưa thử trên bản đóng gói: server tắt/khởi động lại giữa phiên, login lại sau khi phiên hết hạn, nút "Dừng giám sát", `java-options` trong `ToeicMonitor.cfg`.
- Chữ "Collector chưa được bật ở task T1-B1" trên màn thí sinh là chữ cũ từ B1, không sửa trong B4.

## Máy Windows thứ hai và LAN — NOT RUN

Không có máy Windows thứ hai trong phiên này. Không thay bằng localhost hay bằng địa chỉ LAN của chính máy build.

Checklist cho người chạy (ghi phiên bản Windows, kiến trúc, SHA256 của `app\client-0.1.0-SNAPSHOT-all.jar`; không ghi tên máy/người dùng):

1. Máy thứ hai không cài IDE, Maven, JDK/JRE. Chép nguyên thư mục `ToeicMonitor\` vào đường dẫn ASCII (ví dụ `C:\TOEIC\ToeicMonitor`).
2. Máy server: chạy DB + server, cho phép TCP 8080 trên tường lửa mạng LAN. Không mở cổng PostgreSQL.
3. Mở `ToeicMonitor.exe`, nhập `http://<IPv4 LAN của máy server>:8080`.
4. Sai mật khẩu → báo lỗi; `candidate1` đăng nhập; `proctor1` đăng nhập (máy thứ hai hoặc máy server).
5. Candidate bật giám sát, mở Edge → proctor thấy "Quan sát thấy msedge.exe"; ONLINE; đóng candidate → UNKNOWN sau khoảng 6 giây.
6. Audio: cần bản `-WithAudioSmoke`; chạy `AudioSmoke.exe`, bấm Play và **nghe** tone; thử một file MP3 thật.
7. Đóng app, kiểm Task Manager không còn `ToeicMonitor.exe`.
8. Nếu tên người dùng Windows của máy đó có dấu: ghi lại app có mở được không (JavaFX giải nén native vào thư mục người dùng; chưa kiểm ở phiên này).

## Dọn dẹp sau phiên

- Server của phiên đã dừng; cổng 8080 không còn lắng nghe; container `toeic-db` đã `docker compose stop` (volume giữ nguyên). Docker Desktop do phiên này bật vẫn đang chạy.
- **Phát hiện ngoài phạm vi B:** `scripts/demo-c3.ps1 -Action Cleanup` thất bại với `violates foreign key constraint "monitoring_presence_attempt_id_fkey"` khi lượt `DEMO-C3-A` đã từng có heartbeat — script (của C, viết trước V4) chưa xóa `monitoring_presence`/`monitoring_interruptions`. Transaction rollback nên không mất dữ liệu. Phiên này xóa tay hai bảng đó cho đúng `attempt_id='DEMO-C3-A'` rồi chạy lại Cleanup: attempts 0, events 0, presence 0, interruptions 0, tài khoản giữ nguyên 3. Script chưa được sửa; cần C xử lý.
- Thư mục thử `%TEMP%\toeic-b4` đã xóa. `jpackage-out\ToeicMonitor` là bản bàn giao (không harness), nằm trong `.gitignore`.
- `.env`, `task.txt` không commit. Không reset DB, không đổi `.env`.

## Bảo mật

[security-scan.txt](security-scan.txt): diff so với `a04aaa2` không thêm token, mật khẩu thật, đường dẫn tuyệt đối cá nhân hay IP LAN thật; log đã thay gốc repo bằng `<repo>`, thư mục người dùng bằng `<home>`, IP bằng `192.168.x.x`.

## Trạng thái

| Mục | Trạng thái |
|---|---|
| App-image + runtime kèm theo | PASS |
| Chạy trên máy build không qua IDE/Maven | PASS |
| Login, WS, giám sát, cảnh báo, presence trên bản đóng gói (cùng máy, UI Automation) | PASS |
| Đóng app hết process | PASS |
| Thư mục có khoảng trắng | PASS |
| Thư mục có dấu trong code page ANSI | PASS (chẩn đoán tay) |
| Thư mục có dấu ngoài code page ANSI | **FAIL** — chờ QD-11 |
| Audio MP3 / WAV / M4A trong app-image, thư mục audio có dấu | PASS (trạng thái pipeline) |
| Nghe bằng tai | NOT RUN |
| Máy Windows thứ hai, LAN hai máy | NOT RUN |
| Human A review | NOT RUN |
| IT01 (phần chặng 1) | PARTIAL |
| T1-B4 | PARTIAL |
