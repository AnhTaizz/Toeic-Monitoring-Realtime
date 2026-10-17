# Cài đặt/runtime — INSTALLED_LOCAL_PASS

MSI0.3.7 SHA256 `0068d05946eb26cfd632b6be1b9b26ca61f0729c58a44a63646d92c554f2b698`, source `9bfc75ec7c2d91af6f714602727389547c3b3288`. Tất cả là lượt mới, không kế thừa MSI PASS PR37. Initial native probe Administrator=false, không có ToeicMonitor hiện hữu; script guard registry+MSI checksum/ProductCode+unique LOCALAPPDATA TEST path trước install. Cài/gỡ đúng ProductCode riêng, không sửa firewall/global PATH/JDK hoặc xóa journal người dùng.

| Kiểm tra thực | Kết quả và bằng chứng |
|---|---|
| Install quyền thường, Unicode/spaces | PASS; installer0, đúng thư mục TEST có dấu; ảnh/data profile riêng |
| Normal EXE không agent/IDE/JAVA_HOME/JDK PATH | PASS; normal login window, runtimeProcess load `runtime/bin/server/jvm.dll`, close child+wrapper0 |
| Thư mục cài chặn Write | PASS; deny ACL cho TEST, negative write bị AccessDenied; file count/hash trước/sau không đổi; ACL được phục hồi |
| Installed FullSuite | **876PASS/0failure/0error/0skip**, source9bfc75e; PG thật; app-image và installed flags cùng bật |
| Login Candidate/Proctor, B4 READY, Reading/Listening/shared writer, FULL | PASS local code/runtime subset; production EXE/Media/HTTP/WS/PG thật, fixture TEST |
| WAV/checksum/start/no replay/reconnect Reading/frozen Listening | PASS; speaker confirmation và socket close fault **SIMULATED**, không human hearing/physical disconnect |
| Installed submit/History/no takeover/B4 reorg | PASS bổ sung `extended-msi-install-04`: History final epoch giữ, old answers/deadline/revision/result bằng nhau sau reorg; new attempt empty/revision0/NOT_STARTED/not READY |
| Close/uninstall | PASS; owned schema/audio/context đóng, EXE/driver0, uninstall0, không còn bản cài TEST; cache/journal profile TEST giữ raw, cache người dùng không xóa |
| Máy B/LAN/nghe thật | **SECOND_MACHINE_NOT_RUN / PHYSICAL_LAN_NOT_RUN / HUMAN_HEARING_NOT_RUN** |

Lượt FullSuite dùng dedicated QA database trong container hiện hữu, hoàn tất trước04:50UTC. Sau khoảng nghỉ, container dùng chung đã dừng (read-only probe07:12UTC). Lượt extended dùng container PostgreSQL18.6 mới cùng image cache, loopback port động/tmpfs/--rm, không start/reset shared service. Fixture mới sạch và cleanup0schema/0connection; container TEST mới đã remove. Database QA ban đầu còn trong shared container đang dừng, xem BLOCKERS; không gọi cleanup toàn bộ PASS.

Private extended driver gọi fixture production PG hiện có với external TEST agent mở rộng. Chỉ thay compiled test agent trong ignored target trong lúc chạy, sau đó khôi phục byte gốc; production source/JAR/MSI nguyên vẹn. [Hai Java harness đã chạy](../review/harness/InstalledClientAgent.java), [driver](../review/harness/ExtendedInstalledDriver.java), [hash/provenance](HARNESS_PROVENANCE.json). Đây là một standalone scenario thêm, không cộng vào876JUnit. Confirmation UI tự động vẫn SIMULATED, không chữ ký operator. [History installed](../review/images/13-installed-history.png), [B4 installed](../review/images/14-installed-reorganization.png) đã được agent mở xem.

FAIL được giữ:035 title timeout;036 chỉ build trước finding cache; extended01 stderr PowerShell,02 quoting-D,03 DB connection refused sau shared stop. Đó là lỗi wrapper/môi trường, không xóa để lấy PASS. Owned PG initial readiness từng nhầm temporary init socket; probe TCP sau đó PASS. Mọi lần install đều gỡ đúng bản TEST; compiled agent restoration được kiểm. FullSuite876PASS không phụ thuộc các wrapper FAIL bổ sung.

Raw immutable logs/hash index: [RAW_LOG_INDEX](../RAW_LOG_INDEX.json), `jpackage-out/pr39-qa-20261016/all-install-raw` và `all-runtime-raw`; không publish raw MSI registry/process/environment/credential metadata. Native child/fixture cleanup counters trong [CLEANUP_SUMMARY](CLEANUP_SUMMARY.json).
