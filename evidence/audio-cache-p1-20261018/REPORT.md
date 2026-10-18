# S3-P1-01 — sửa publication cache audio

Source production sửa: `56a27e98991052c25b294eeb61bb212ed7cd19b6`; test barrier stderr bổ sung tại `fdc2a3bb9022b86e7647a2a2989116752ac72488`, base main `f86403489bdd01887b926f2f9e4b72ddf05d1423`. Owner code B; A xem hồi quy/CI. Review kỹ thuật do Codex theo ủy quyền người dùng trong phiên; không gán chữ ký A/B/C. Lần push đầu bị automatic approval review từ chối; ngày19/10 người dùng đã cho phép push hai nhánh, mở PR và chạy CI. Chưa có CI mới hoặc merge main tại snapshot hồ sơ này.

## Thực thi

| Lượt | Counted operations | Unexpected failures | Hash cuối | Partial còn lại |
|---|---:|---:|---|---:|
| Trước sửa, exact source main | 1280 | 747 | 20/20 hợp lệ | 0 |
| Sau sửa, source cuối | 1280 | 0 | 20/20 hợp lệ | 0 |

Mỗi lượt: hai lần chạy × hai mode × 5 round × 8 worker × 8 operation. Mode download có 8 writer; mixed có 4 writer/4 verifier. Seed/final verify không cộng vào counted operations. Harness giữ nguyên khối Java từ PR55 `e3fdfea` (kèm [source](ConcurrentDownloadProbe.java)); payload 128 KiB `Random(33)` **MOCK**, filesystem Windows **REAL**. Không dùng tần suất stress suy ra tần suất production.

[Trước](before-exact.json), [sau](after-final.json), [case-by-case targeted](targeted-results.json). Targeted cuối: **54 PASS, 2 SKIP, 0 FAIL/ERROR**, gồm AudioFiles23, Sharing10, Preparation10, ApiClient5, Playback8. Hai SKIP là quyền tạo symlink; junction/ancestor8.3/Unicode và reader Windows `NOSHARE_DELETE` đã chạy thật. Multi-process: bốn JVM chia cache thiếu, rồi bốn JVM download/verify cache có sẵn; thêm cancellation khi JVM khác giữ khóa và recovery sau peer bị kill. Không gọi đây là LAN hoặc nghe thủ công.

Diagnostic đầu 742/1280 FAIL cũng được giữ tại ignored `target/stage3-audio-control/baseline-*.log`; export PowerShell đã làm đổi encoding chuỗi thông báo tiếng Việt. Sau đó export trực tiếp byte Git, xác minh byte-identical, compile và chạy lại hai cây TEST mới: 747/1280 FAIL ở bảng trên. Hai lượt này độc lập, không cộng thành một kết quả. Wrapper `java -version` ghi stderr làm PowerShell dừng cũng được lưu; không phải lỗi product.

## Root cause và policy

`Files.move(partial,target,REPLACE_EXISTING)` cũ thay một target đang được writer/reader khác sử dụng. Source `java.base/sun/nio/fs/WindowsFileCopy.java` trong `jdk-21/lib/src.zip` của JDK21.0.8 cho thấy nhánh non-atomic đọc attributes, `DeleteFile(target)`, rồi `MoveFileEx` không replace. Window đó tạo thiếu file/AccessDenied/FileAlreadyExists và cả lỗi kiểm đường dẫn trong stress. Hash file cuối đúng không phủ nhận lỗi availability này.

Download vẫn tải vào partial riêng và kiểm đủ byte/SHA trước publication. Dưới khóa, đọc lại byte target: đúng manifest thì **giữ nguyên file đó**, xóa partial của caller; chỉ file thiếu hoặc xác định sai byte/hash mới được sửa. Lỗi I/O khác không bị coi là cache hỏng để xóa tùy tiện. Server ACK/READY và worker nền giữ nguyên.

`file()`, `verify()` và publication dùng cùng khóa theo directory manifest. 64 `ReentrantLock` tĩnh có khóa theo canonical path phối hợp thread/instance và giới hạn RAM; sidecar `.audio-cache.lock` dùng `FileChannel.lock()` phối hợp JVM. Không xóa sidecar sau unlock vì process khác có thể đã mở cùng file. Sidecar không phải partial/playable resource. Lock chờ có thể interrupt; stream/channel/partial được đóng/dọn trong nhánh failure/cancel. Không giữ khóa trong HTTP download. Cache hợp lệ không bị replace nên MediaPlayer không cần tham gia lock.

FileLock thuộc JVM, không thay khóa thread; native lock phải dùng như advisory/cooperative. Tài liệu: [FileLock Java21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileLock.html), [Files.move Java21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html#move(java.nio.file.Path,java.nio.file.Path,java.nio.file.CopyOption...)). Không phụ thuộc `ATOMIC_MOVE` hoặc fallback bỏ coordination. Giữ validation root/child NOFOLLOW, junction/isOther/canonical child và tên/giới hạn manifest; sidecar từ chối link/directory, mở NOFOLLOW.

Phạm vi đã kiểm: local Windows filesystem, các process cùng phiên bản mới. Không cam kết network share hoặc client cũ không theo khóa. Không có descriptor-relative sandbox chống process độc hại cùng user (giới hạn QD33 cũ giữ nguyên). Hard kill/OS crash giữa download có thể để lại partial; verify không dùng partial và không READY từ partial. Dọn partial sau failure/cancellation thông thường đã được kiểm, không nhận đã kiểm crash recovery.

## Tái lập

Trên Windows/JDK21, từ root checkout bản sửa:

```powershell
mvn -pl client -am '-Dtest=AudioFilesTest,AudioFilesSharingTest,AudioPreparationTest,AudioApiClientTest,ListeningPlaybackTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
java --class-path 'client/target/classes;protocol/target/classes' evidence/audio-cache-p1-20261018/ConcurrentDownloadProbe.java "$env:TEMP/Toeic-P1-$([guid]::NewGuid())"
```

Chạy probe hai lần để đủ1280 operation; mỗi lần phải kiểm hai JSON `ok==attempts`, errors rỗng, `finalValidCaches==5`, `partialFiles==0`. Probe exit0 chỉ là thu số liệu, không tự PASS. Test JUnit không làm yếu assertion khi lỗi. Tất cả dữ liệu audio/gateway trong targeted là MOCK; không dùng thay nghiệm thu máy thứ hai/Listening.

Theo task.txt, PR47 và test56 được ghép trên nhánh local `stabilize/stage3-final-20261018` để kiểm source tích hợp; source/chi tiết full-run được ghi trong hồ sơ tích hợp riêng. Tracker không tự đổi DONE. Final MSI phải build lại từ main sau merge; MSI0.3.10/ZIP3044 là lịch sử.

Lượt clean-test tích hợp `2a0db72` FAIL3 case sharing vì child JVM in `JAVA_TOOL_OPTIONS` ra stderr nhưng fixture ghép stderr vào stdout READY barrier. Bản sửa test `fdc2a3b` tách hai stream, giữ assertion READY/exit/timeout và đọc stderr khi báo lỗi process; không đổi production. Log FAIL và XML được giữ trong ignored `target/stage3-audio-control/integrated-full-r2/`. Wrapper full-run ban đầu còn có lỗi chờ process/exitCode, đã sửa trong runner TEST riêng, không thay product. Lượt full tích hợp cuối có hồ sơ riêng, không dùng FAIL chẩn đoán làm PASS.
