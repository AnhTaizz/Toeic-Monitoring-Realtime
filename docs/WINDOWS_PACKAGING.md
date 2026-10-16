# Đóng gói Windows thống nhất: một pipeline MSI

Pipeline phát hành là `scripts/package-windows.ps1` từ PR #37, theo [MSI guide](WINDOWS_INSTALLER.md). Giữ MSI per-user, version/upgrade UUID, app-image Unicode đã sửa manifest, bundled runtime/JavaFX và production-only JAR. Không có builder EXE/`-Mode AppImage|Installer` hoặc `-SourceSha` thứ hai trong bản ghép. Script không tự cài/gỡ hoặc đổi firewall/registry.

```powershell
# Kiểm parser/negative và prerequisites; không build/cài:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-package-preflight.ps1 -JavaHome '<JDK21>'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/package-windows.ps1 -JavaHome '<JDK21>' -WixHome '<WiX3>' -ManifestTool '<SDK/mt.exe>' -PreflightOnly
# MSI chính, source đã commit và được test:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/package-windows.ps1 -JavaHome '<JDK21>' -WixHome '<WiX3>' -ManifestTool '<SDK/mt.exe>' -Offline
# Chỉ app-image QA/audio qua helper hiện hữu, không phải installer:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/package-t3b1.ps1 -JavaHome '<JDK21>' -ManifestTool '<SDK/mt.exe>' -WithAudioSmoke
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-package-windows.ps1 -ImageDirectory '<QA image>'
```

`-PreflightOnly` chỉ xác minh toolchain, không chứng minh artifact hoặc IT01. `-AsciiDiagnostic` của QA smoke chỉ chạy EXE tại đường dẫn ASCII khi thiếu SDK; không chứng minh launcher dưới parent Unicode. Package helpers lưu dưới `jpackage-out`, scripts MSI giữ root tooling/log ngoài module clean như #37. Source metadata lấy Git HEAD thật; worktree tích hợp dùng pointer Git tương đối tương thích Windows/WSL.

## Địa chỉ server ngoài binary

Direct EXE: ô login do người dùng chỉnh → giá trị được dùng; mặc định lấy JVM `toeic.server.url` > process `TOEIC_SERVER_URL` > `%LOCALAPPDATA%/TOEIC Monitor/server-url.txt` > localhost mặc định. Launcher đọc user file tối đa 4 KiB **trước JavaFX**, không file I/O trên FX thread. Chỉ HTTP(S) origin không userinfo/query/fragment/path; file sai có thông báo an toàn và cho nhập lại. Không ghi URL lỗi/token/mật khẩu vào log.

MSI có `Start-ToeicMonitor.ps1` cạnh EXE; wrapper nhận `-ServerUrl`, `-ConfigFile`, `-ClientDirectory` và `-ValidateOnly`. Thứ tự wrapper: argument > process environment > user file > GUI/default. Script không sửa config hay lưu credential. Config ngoài install nên không bị đóng gói/ghi đè khi upgrade. Shortcut native vẫn dùng ClientSettings cùng quy tắc direct EXE.

Cache/journal ở user profile ngoài install; marker chống replay không được tự xóa khi upgrade/uninstall. TEST audio launcher/agent không nằm trong MSI production; app-image QA và external TEST agent luôn ghi nhãn riêng. Nghe thật, cài/gỡ trên máy được phép và máy thứ hai/LAN vẫn cần [checklist](ACCEPTANCE_B_MANUAL_MATRIX.md). [Kết quả bản thống nhất](../evidence/unified-20261016/verification.md) ghi tool thật và BLOCKED/NOT RUN nếu thiếu, không kế thừa MSI PASS của #37.
