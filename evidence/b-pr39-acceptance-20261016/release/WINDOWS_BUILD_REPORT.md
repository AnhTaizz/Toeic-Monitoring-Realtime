# Windows build — MSI_BUILT

MSI mới **0.3.7**, source sạch `9bfc75ec7c2d91af6f714602727389547c3b3288`; pipeline chính `scripts/package-windows.ps1` + `package-t3b1.ps1`, không AllowDirty. Không sửa script package đã chứng minh của main; chỉ sửa điều kiện title trong smoke. WiX/SDK lấy portable vào ignored TEST tooling, không cài global/đổi global PATH/JDK/tắt bảo vệ Windows.

| Công cụ | Xác minh mới |
|---|---|
| Windows | Windows11 x64, build10.0.26200.0, quyền thường Administrator=false |
| Java/Maven | Oracle21.0.8 x64; Maven3.9.11; JavaFX21.0.12 từ dependencies/artifact |
| PostgreSQL/Docker |18.6 (Debian18.6-1.pgdg13+2), Docker29.4.3; DB QA mới riêng, không reset shared volume/data |
| WiX |3.14.1.8722, candle/light không ký; official archive hash khớp pin đã ghi trong repo |
| SDK mt.exe |10.0.26100.6584, Authenticode Valid/Microsoft; archive hash khớp pin |

Nguồn trực tiếp: [WiX3.14.1 official release](https://github.com/wixtoolset/wix3/releases/tag/wix3141rtm), [Microsoft SDK BuildTools official package](https://www.nuget.org/packages/Microsoft.Windows.SDK.BuildTools/10.0.26100.6584). Pin trong repo là observed checksum, không tự gọi là independent publisher checksum. URL download/hash/signature/tool versions nằm [manifest](ARTIFACT_MANIFEST.json).

Artifact: `jpackage-out/pr39-qa-20261016/release-0.3.7/ToeicMonitor-0.3.7.msi`. MSI SHA256 `0068d05946eb26cfd632b6be1b9b26ca61f0729c58a44a63646d92c554f2b698`; production client JAR SHA256 `e6efeaeb42990a73794ad42ba55b272bebc749fceae2967b51f283928091dfed`. Không tái sử dụng MSI PR37. Source HEAD/dirty được pipeline kiểm cả trước/sau build. Shaded JAR chỉ từ client main source/dependencies (bao gồm code measurement/E1 hiện có của PR39), không server hoặc test agent. Test agent dùng ngoài package; launcher config không javaagent/DELTA opt-in; JavaFX controls/media/natives có đủ. Không có .env/test classes/Smoke/InstalledClientAgent trong scan; known secret values >=8byte từ environment không match các entry giải nén, không emit giá trị. Không tuyên bố chứng minh tuyệt đối mọi secret tiềm ẩn.

Lệnh đã chạy (đường dẫn tooling tương đối checkout):

```powershell
.\scripts\package-windows.ps1 -JavaHome <JDK21-x64> -WixHome <ignored-tools/wix> -ManifestTool <ignored-tools/sdk/bin/10.0.26100.0/x64/mt.exe> -AppVersion 0.3.7 -Offline
```

Log `final-msi-build-02.log` và metadata/hash ở rawRoot. Build dùng -DskipTests, không được tính là test PASS. Preflight/parser/negative/config có nhãn REAL/MOCK theo script; tải WiX lần đầu timeout180s và retry setup lỗi thư mục đã tồn tại được giữ log; lần tiếp tục đã xác minh xong trước build. Artifact035/source093 dùng tái hiện title FAIL;036/sourceDcb bị thay bởi037 sau finding cache, không phát hành làm ứng viên cuối.
