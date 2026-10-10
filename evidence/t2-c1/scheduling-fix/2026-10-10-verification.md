# PR#18 — sửa tác vụ bảo trì C1 chưa được kích hoạt

Ngày10/10/2026 · Agent chạy theo yêu cầu sửa C1 của người dùng. [PR#18](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/18) giữ mở, không merge hoặc làm C2. Base main8eba405; đầu phiên HEAD/remote30fa6d9, working tree sạch. Code sửa **e07bb7ef90fd2326dd120b681c3c7029c6a2bda0**; commit sau chỉ docs/evidence. Source/JAR/checksum của lượt cuối trong [metadata](run-metadata.json).

## Lỗi và tái hiện

Người dùng review source tìm ra maintain có Scheduled nhưng server không có EnableScheduling hoặc cơ chế kích hoạt tương đương. Unit cũ gọi maintain trực tiếp và ca CLOSE thật chuyển STALE trực tiếp, nên chưa chứng minh bảo trì tự chạy. Kết quả cũ giữ nguyên, không coi chúng là bằng chứng autonomous STALE/TTL.

Trước sửa production, build code cũ cộng harness mới với TTL4s/maxAttempts1. Smoke giữ WS/scoped heartbeat nhưng chỉ gửi một full, không CLOSE, không HTTP state refresh hoặc gọi maintain. **FAIL phase=AUTO_STALE_WITHOUT_HTTP_OR_CLOSE** vì không nhận push STALE. [Trích kết quả](before-fix-extract.txt). TTL phase chưa tới trong lượt âm này, không ghi nó đã chạy. Schema/server/owned Edge được dọn.

## Cách sửa và phạm vi

- Thêm bean MonitoringStateMaintenance: một ScheduledThreadPoolExecutor daemon `toeic-state-maintenance`, fixed delay theo scan-ms. Nó gọi maintain độc lập với annotation scheduling của Spring. Bỏ Scheduled trên maintain để không bị chạy hai lần nếu A bật scheduling toàn server sau này.
- RuntimeException không hủy các lần scan tiếp theo; log cố định, không in exception/cause/credential. PreDestroy shutdownNow và đợi worker tối đa5s; unit xác nhận interrupt tác vụ đang chạy, task về sau bị hủy, close lặp an toàn.
- Store PreDestroy xóa RAM và chặn request muộn; maintain/publish khi đã đóng không cập nhật/phát dữ liệu. Dependency Spring maintenance→store giúp hủy worker trước store.
- Không EnableScheduling toàn app; test context không có ScheduledAnnotationBeanPostProcessor. Không sửa ExamTimeoutService/ToeicServerApplication. Scheduler timeout bài thi của A cần A review/sửa riêng; không coi lỗi đó đã được xử lý ở đây. Không migration/reset DB hoặc sửa kế hoạch/tracker/.env.

## Lệnh, cấu hình và bằng chứng

Windows11 build26200 / Temurin21.0.10+7 / Maven3.9.15 / PostgreSQL18.6 Docker, Python3.15.0b3. Script đọc DB `.env` local cổng5433, không in credentials. Schema TEST UUID và server port riêng; chỉ dừng Edge/process do harness sở hữu.

```powershell
mvn "-Dmaven.repo.local=<cache Maven của máy chạy>" package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t2c1.ps1 -JavaHome <JDK21> -Gui
# Hồi quy smoke-a4/c3/c4 và smoke-b3 -Gui với cùng JDK.
python scripts/summarize-monitoring.py evidence/t2-c1/scheduling-fix/raw --output server/target/t2c1-scheduling-evidence-summary
```

Full harness poll200ms/HB200ms, presence stale1200ms/scan25ms, full stale1200ms/scan25ms/TTL4000ms/maxAttempts1. Các giới hạn queue16+1pending, full128, dedup64, retry5/ACK5s giữ nguyên. Production default full stale6s/scan500ms/TTL5phút/max4096 không đổi. Scaled timers phục vụ acceptance, không phải benchmark.

Lượt cuối: **T2C1-c4692b28-08d6-461e-8368-902af2c5ddf2**, sourceHead e07bb7e, workingTree sạch tại lúc ghi metadata. Code manifest so với source sau smoke:0mismatch. Package/unit chạy trước commit trên cùng code; full smoke chạy lại sau code commit. [Java test extract](java-test-extract.txt), [checks](checks.txt).

| Kiểm tra | Kết quả thật / mức bằng chứng |
|---|---|
| Toàn Java test/package | **PASS413**, protocol29/client242/server141/spike1;0failure/error/skipped |
| Worker tự gọi; tiếp tục sau RuntimeException; close/interrupt/drain; interval sai; RAM clear/reject late | **PASS**, 5unit mới: executor REAL, service/registry MOCK |
| Khởi động Spring thật và gửi OPEN/full1 → SYNCED | **PASS REAL** WS/PG/server/proctor RealtimeClient; process probe MOCK |
| Ngừng full, giữ socket và scoped heartbeat; STALE push giữ tập cuối/sequence1 | **PASS REAL**; không CLOSE/read HTTP state/manual maintain; kiểm heartbeat ACK tiếp tục và socket CONNECTED |
| Capacity1: OPEN B RETRYABLE_SERVER_ERROR trước TTL; TTL tự push UNSYNCED/tập rỗng; OPEN B ACK sau TTL | **PASS REAL**, TTL4s, cùng server/store/context; không duyệt các slot bằng getter hoặc HTTP để làm TTL chạy |
| Event/gap/interruption history không đổi qua STALE/TTL | **PASS REAL** SQL count trong schema riêng; không tạo heartbeat loss vì vẫn giữ heartbeat |
| Spring close xóa RAM, worker state-maintenance và client/recorder workers kết thúc | **PASS REAL** bean destroy/activeAttempts0 và bounded thread termination check |
| Full Edge mở/đóng, epoch/retry/conflict/empty/scope/CLOSE/history/GUI proctor component | **PASS REAL**, fault process sets MOCK; ảnh thật mới giữ nguyên |
| A4/C3/C4/B3 hồi quy | **PASS**, B3 có Gui component; A4/B3 source MOCK và hard-kill owned JVM REAL, C3 Edge REAL/ACK suppression SIMULATED, C4 event MOCK/ACK suppression SIMULATED |
| Log6endpoint có FINAL/drop0/unwritten0/pending0, summary63category | **COMPLETE REAL** cho file cung cấp; regenerate JSON giống byte file lưu |

Python unittest10 là kết quả phiên trước; không chạy lại vì Python analyzer không đổi. Summary Python được chạy thật trên raw mới và raw evidence. A2/A3/B2/C2 không chạy lại trong lượt sửa này; không gán bằng chứng hồi quy cũ cho commit mới.

## Artifacts và giới hạn

[Raw6file](raw/) nguyên bản, [summary JSON](summary.json)/[CSV](summary.csv) do Python sinh không chỉnh sửa; [SHA256SUMS](SHA256SUMS.txt) bao phủ artifacts trừ chính file checksum/trang này. Gitattributes có -text cho cả thư mục T2-C1 để giữ nguyên byte. [Edge mở](t2c1-edge-open.png)/[đóng](t2c1-edge-closed.png) từ Scene JavaFX thật; không phải GUI candidate toàn app. Dữ liệu fault process MOCK, chuỗi mạng/socket/Spring/DB REAL. Không ghi đường dẫn full/command line/token/password vào raw.

GUI candidate toàn app, LAN máy thứ hai, full server restart qua mạng, lỗi OS scan thực/performance và human A/B review tổng thể: **NOT RUN**; MT01 vẫn PARTIAL. Review nguồn đã có phát hiện của người dùng; chưa giả lập approve của A/B. Giữ PR#18 mở để review bản sửa, chưa merge hoặc chuyển task.
