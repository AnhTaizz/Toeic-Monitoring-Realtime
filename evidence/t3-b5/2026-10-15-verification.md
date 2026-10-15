# T3-B5 · MSI Windows — 15/10/2026

Branch `feat/t3-b5-windows-installer`, base main/B4 `0b9c5fa1c441a4a5537825f55c06fea9680ad9f1`, workspace sạch trước sửa. Implementation `6fafcad`; source/test/guide cuối **`acac38df9f033c70c9d030957541108caf95609a`**. Commit báo cáo sau đó chỉ đổi tài liệu. Main/PR35 được kiểm lại trước bàn giao; PR35 draft `11c5da9ad3094a103cd6db93693aa64bbb132eaa` không ghép/chỉnh/merge. FULL mặc định.

Môi trường thực: Windows11 Pro x64, 25H2 build26200.9711; Temurin21.0.10+7, Maven3.9.15, OpenJFX21.0.12, PostgreSQL18.6 Docker `toeic-db`. WiX3.14.1.8722 portable, Microsoft SDK mt.exe10.0.26100.6584 (chữ ký Microsoft Valid), công cụ chỉ ở ignored target, không đổi OS locale/JDK/dependency toàn dự án. Các lượt MSI chạy **không elevated**, xác nhận WindowsPrincipal Administrator=false; máy này có JDK/IDE nên không giả là máy sạch.

## Artifact bàn giao

- **MSI thực**, per-user, version0.3.5, 37,860,724 bytes (không gọi app-image là installer).
- Local: `target/t3-b5/package-875b76a96f894ea29dd7df99e0c94dd8/ToeicMonitor-0.3.5.msi`.
- SHA-256 **`7c145740789a934079d402bc6f72bd3f1b0bde152ba3711e61836a84bfa169f0`**.
- `build-info.json` và `.msi.sha256` cùng thư mục: source `acac38d`, dirty=false, runtime21.0.10, FULL, tool versions. JAR trong MSI SHA-256 `58a308e95c12250a4061a06daec5c4f7426f32f80571e9d76e7267724319e783`.
- Audit MSI File table156 file; app chỉ có `.jpackage.xml`, `ToeicMonitor.cfg`, production client JAR và build-info. Không `.env`, credential, raw log/cache/test agent/server JAR. JavaFX/natives ở shaded JAR, JDK runtime đi kèm. Cài xong kiểm lại metadata/JAR hash và hash toàn bộ file cài trước/sau chạy; không thay đổi. Artifact/tools/runtime/log/cache không commit; Git chỉ code/script/test/docs/report này, evidence cũ giữ nguyên.

## Lệnh và kết quả mới

Thực hiện tại root repository; `<JDK21>` là JDK nêu trên, tool paths dưới target như [hướng dẫn](../../docs/WINDOWS_INSTALLER.md).

| Kiểm tra đã chạy | Kết quả / phạm vi |
|---|---|
| Baseline exact-main `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-t3b4.ps1 -JavaHome '<JDK21>' -Gui` | BUILD SUCCESS, 2m49s, PostgreSQL/GUI bật. Không gán số686 lịch sử B4 thành kết quả mới. Raw baseline root target bị dọn trong lỗi script thử đầu, chỉ còn summary của lần gọi công cụ; xem lỗi bên dưới. |
| `scripts/package-windows.ps1 -JavaHome '<JDK21>' -WixHome target/t3-b5/tools/wix -ManifestTool target/t3-b5/tools/windows-sdk/bin/10.0.26100.0/x64/mt.exe -Offline` | PASS, Git sạch acac38d; Maven clean/package `protocol,client` có skipTests, sau đó app-image + manifest UTF-8 + MSI. Log `target/t3-b5/final-package.log` và package folder trên. |
| `scripts/smoke-windows-installer.ps1 -Installer '<MSI trên>' -JavaHome '<JDK21>' -FullSuite` | **687 PASS, 0 failure/error/skip**: protocol43/client439/server204/spike1, 2m39s Maven. JUnit XML mới trong module target/surefire-reports; includes13 ListeningRuntimePostgresTest, installed EXE và B4 GUI LT04. |
| Cài → mở không agent → đóng → mở test integration → đóng → gỡ | PASS bằng msiexec/Windows cơ chế chuẩn, Unicode install và ACL deny Write cho người dùng thường. Bỏ JAVA_HOME/JVM options, PATH chỉ Windows; đúng cửa sổ login và DLL `<install>/runtime/bin/server/jvm.dll`, đóng cả wrapper/JVM exit0. |
| UI production trong EXE đã cài | PASS login proctor/candidate, B4 đọc READY2 lượt từ HTTP thật, FULL + ACK/event thật; cache tải/checksum, MediaPlayer thử loa hoàn tất, READY ACK, start WS, MediaPlayer PLAYING. Agent ngoài MSI điều khiển JavaFX controls, không thay production Launcher. |
| Listening gián đoạn/đóng app | PASS đóng socket thật phía server (fault SIMULATED), MediaPlayer dispose/BLOCKED, HTTP ghi INTERRUPTED sau reconnect, không replay, journal giữ; app đóng bằng window lifecycle, worker/player và cả JVM/wrapper kết thúc. Candidate2 READY là MOCK input qua real API; click nghe SIMULATED, audio fixture đề WAV30s im lặng. |
| `scripts/smoke-t2c1.ps1 -JavaHome '<JDK21>' -Gui` | PASS mới: ProcessHandle/Edge/full/HTTP/WS/PG/dashboard, epoch/duplicate/conflict/empty/scope/stop/history; STALE tự push với socket mở, TTL4000ms tự giải phóng capacity, shutdown dọn RAM;6endpoints/63categories Summary COMPLETE. Đây là smoke classpath riêng trên source B5, không nói runner C1 nằm trong MSI, không coi dữ liệu này là E1/E2. |

Raw giữ local: `target/t3-b5/final-verification.log`; `target/t3-b5/install-ab667f995ba94933965621b7adf68d3e/{tests.log,install.log,ordinary-launch.txt,result.txt,uninstall.log}`; `server/target/t3-b5-installed/5f124e24-8321-4dd9-8ba5-51017afeea98/` (runtime.txt, passed.txt, signals, stdout/stderr, TEST agent và profile/cache riêng); `target/t3-b5/full-dashboard-smoke.log`; `server/target/t2c1-runtime/T2C1-e98fce14-18f2-4b94-8ec2-d4e2b052025d/`. MSI đã được gỡ sau test, test schema/audio/owned socket/Edge được dọn; cache/log TEST dưới ignored target giữ để đối chiếu, không đụng profile/journal hay dữ liệu DB dev.

## Lỗi trung gian được giữ đúng là lỗi

- Script thử đầu dùng Maven `-am clean`, chọn cả root POM và dọn root target, gồm raw baseline; clean dừng do file log đang mở. Đã sửa thành `-pl protocol,client` để giữ root tooling/logs. Không xóa/untrack evidence cũ trong Git. `target/t3-b5/first-package.log` và package-36014d2ea0494e9b8158a9bcef8d31c9/build.log giữ lỗi này; baseline log đã mất không được tái tạo giả.
- Guard JAR thử đầu bắt nhầm `javafx.scene.DepthTest` là test; sửa chỉ kiểm namespace dự án. `second-package.log` giữ lỗi; production JAR không có TEST agent/classes.
- Probe không agent lần đầu kiểm nhầm wrapper, không thấy window; lần sau Get-Process không giữ handle nên exit code JVM thành null dù đã đóng. Diagnostic xác nhận child/window/close=true và wrapper0; giữ handle trước đóng cho child0. `release-verification.log` / `release-verification-rerun.log` giữ FAIL; cuối `final-verification.log` PASS dùng probe sửa. Finally dọn cả child để không orphan JVM.
- JavaFX21 shaded/classpath phát warning “unnamed module”; warning có trong raw stderr, không giấu thành kết quả nghe. JavaFX UI/native media vẫn được kiểm thực và exit0; không đổi dependency để né warning.

## Mức hoàn thành và phần chưa chạy

**Phần đóng gói/local hoàn tất; IT01 PARTIAL, chưa nghiệm thu.** Máy Windows thứ hai không IDE/không có Java, LAN hai máy, người thật nghe loa/audio đề, rút dây mạng/kill thủ công, MP3/M4A và human A review **NOT RUN**. Agent review là đọc code/test của Codex; không giả có ChatGPT planning/review (opt-out hiện có) hay human approval. Không dùng hai JVM/tài khoản trên máy này để gọi là hai máy.

Chuyển MSI + hash + metadata đúng thư mục trên sang máy thứ hai, tính lại hash, làm [checklist7 bước](../../docs/WINDOWS_INSTALLER.md#checklist-it01-trên-máy-windows-thứ-hai--chưa-chạy), ghi OS/Java/IDE/build SHA/server IP/người nghe/từng PASS-FAIL. Server/DB/audio đặt riêng, tài khoản nhập qua login. FULL không đổi, PR35 Reading–Listening chưa tích hợp; không nghiệm thu toàn luồng đề/Reading, T4/E1/E2 hay sửa tracker/kế hoạch/.env/migrations. Merge B5 nếu GitHub không blocker chỉ là bàn giao implementation, không nâng IT01 thành PASS.
