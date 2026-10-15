# Đóng gói Windows và cấu hình runtime

Cần Windowsx64, JDK21 và JAR mới từ Maven. JDK21/JavaFX Win natives được kiểm trước build. QD26 launcher Unicode cần Windows SDK mt.exe; installerEXE cần WiX3 candle/light trên PATH. Script không tự cài/gỡ phần mềm hay đổi firewall/registry.

```powershell
mvn -B -ntp package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-package-preflight.ps1 -JavaHome $env:JAVA_HOME
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/package-windows.ps1 -Mode AppImage -OutputDir jpackage-out/qa-unique -JavaHome $env:JAVA_HOME -ManifestTool <path-mt.exe> -WithAudioSmoke
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-package-windows.ps1 -PackageDirectory jpackage-out/qa-unique
# Production installer, không kèm TEST launcher, không tự cài:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/package-windows.ps1 -Mode Installer -OutputDir jpackage-out/installer-unique -JavaHome $env:JAVA_HOME -ManifestTool <path-mt.exe>
```

OutputDir phải chưa tồn tại; dùng tên mới mỗi lượt. `-DryRun` chỉ prerequisite, không thay package smoke. Khi Git metadata không đọc được (ví dụ worktree tạo từWSL), truyền `-SourceSha <40hex đã xác minh>`; sourceDirty sẽ null, đối chiếu manifest bytes trong evidence, không tự ghi clean. Không claim byte-perfect reproducible vì jpackage có timestamp/config.

Bên cạnh app-image có `Start-ToeicMonitor.ps1`. Chạy với `-ServerUrl http://<server-host>:<port>` hoặc `-ConfigFile <file chỉ chứa origin>`. Thứ tự: argument > TOEIC_SERVER_URL của process > configfile (mặc định `%LOCALAPPDATA%/TOEIC Monitor/server-url.txt`) > GUI/default. DirectEXE dùng config app hiện có (systemproperty > env > default/GUI); installer không tự nhúng serverURL. Build `-ServerUrl` chỉ xuất `server-url.example.txt` ngoài image để operator chọn làm ConfigFile. Không để token/userpassword trong file này.

Cache/audio/playback markers mặc định ở LOCALAPPDATA/TOEIC Monitor/audio-cache, ngoài install. Upgrade/uninstall không được tự xóa marker để nghe lại. Dữ liệu nghe thử/checksum cần kiểm trong runtime đóng gói với path có dấu/space; `file.toURI()` dùng trong client. Sample WAV của smoke do test tạo, không tài nguyên bản quyền.

Máy15/10 hiện thiếu mt.exe/WiX: production package gate BLOCKED. Raw ASCII app-image đã build và Media8checks PASS với bundledruntime, API/WS MOCK; không phải installer/Unicodeparent/humanhearing/IT01 toàncase. [Evidence](../evidence/overnight-20261015/phase-05/verification.md). Cài/gỡ/máy sạch/máy2 chỉ thử trên máy được cấp phép; ghi OS/source/JAR/installerhash và operator thật.
