# Ổn định hóa bản tích hợp cuối — 17/10/2026

Người đọc: owner A/B/C trước khi review và merge. Mọi số dưới đây lấy từ XML/log của lượt chạy được nêu SHA; số lịch sử của RC 0.3.9 (`c224ba8`) không được cộng hay gán cho source mới.

## A. Kết luận

| Mục | Trạng thái |
|---|---|
| Kỹ thuật | **TECHNICALLY_COMPLETE** cho phần tự động hóa được. Không còn P0/P1 có bằng chứng. |
| Merge | **READY_FOR_OWNER_MERGE** — chờ owner duyệt QD-31..34 và review; agent không merge. |
| Phát hành | **RELEASE_NOT_READY** — LAN hai máy, nghe thật, máy sạch, tải, E2, báo cáo, Compilatio chưa làm. |

## B. Lỗi đã xử lý

| Vấn đề | Ban đầu | Root cause | Sửa | File chính | Test | Trạng thái |
|---|---|---|---|---|---|---|
| QD-29 legacy ACTIVE | Lượt ACTIVE có ID ngoài monitoring v0 vẫn takeover/save/submit/READY được ở server; chỉ client chặn | Guard chỉ ở UI | `ExamService.requireMonitorable` sau row lock + ownership/state ở takeover/autosave/submit; cùng guard ở `AudioService.ready`; 409 `INVALID_STATE` message cố định | `ExamService`, `AudioService`, `AttemptIdentifier`, `ToeicClientApplication` | `ExamRuntimePostgresTest.qd29*` (4), `ExamPaperAndEntryTest`, `ExamMonitoringIntegrationTest` | IMPLEMENTED |
| QD-29 tạo ca đồng thời | Request thua nhận 500 | Count-check rồi INSERT; unique violation rơi vào handler chung | `ON CONFLICT (session_id) DO NOTHING` → 400 | `ExamService.createSession` | Latch PG; bỏ fix → 500 (đã thử) | IMPLEMENTED |
| Lịch sử >5000 (PR43) | 500 `RETRYABLE_SERVER_ERROR` `retryable:true`; `gaps` chưa có test | `IllegalStateException` vào handler chung | `MonitoringHistoryLimit`: đủ ≤5000 hoặc 409 `INVALID_STATE` `retryable:false`; client `HistoryLimitException`, dashboard thông báo riêng, trần body 4→8 MiB | 3 service lịch sử, `MonitoringApiClient`, `DashboardController` | `MonitoringHistoryBoundsPostgresTest` (4), `MonitoringApiClientTest`, `DashboardControllerTest` | IMPLEMENTED |
| PR45 cache audio | PR45: 18/128 lỗi (RC); lại 112/640 (lượt này) | PR45 dựa trên main nên mất `catch FileAlreadyExistsException` của PR40; RC kiểm real path mọi ancestor nên từ chối root dưới junction/8.3 | Không kiểm ancestor trên root; root và con phải là thư mục thường + real path khớp; giữ catch race | `AudioFiles` | `AudioFilesTest` (+7); probe: bản mới 0/640 | IMPLEMENTED; PR45 SUPERSEDED |
| CI Windows (PR45) | Chỉ có trên PR45 | — | Cherry-pick `c1e3405` (giữ tác giả) + trigger push `stabilize/**` | `.github/workflows/pr-ci.yml` | Actions 39017363076 (`3702b6d`) SUCCESS | DONE |
| E1 | Cần kiểm lại sau đổi công thức CPU | — | Không đổi code; kiểm lại raw/analyzer độc lập | `docs/E1_T4C1_MEASUREMENT.md` | 84/84 hash, 12/12 Python, output 2 lần giống byte | VERIFIED, chờ C |
| C1 cleanup | Lỗi xóa profile Edge vẫn in "PASS … Edge/FX resources cleaned"; lỗi finally che lỗi gốc, bỏ qua DROP SCHEMA | finally không cô lập từng bước | `SmokeCleanup`: mọi bước chạy, PASS/FAIL từng thành phần, gắn suppressed; `main` bắt mọi `Throwable` và exit 1 | `FullSnapshotSmoke`, `LateEventsAndGapsSmoke`, `SmokeCleanup` | `SmokeCleanupTest` (3); smoke ở mục D | FIXED |
| C1 khóa profile Edge | FAIL lịch sử "root cause UNRESOLVED" | Khi FAIL: không còn tiến trình Edge, Restart Manager không thấy tiến trình user-mode giữ file; SQLite journal tự nhả sau ~8,7 s và ~21,8 s. Nghi bộ lọc kernel/quét real-time — **chưa xác nhận** | Dừng browser trước con (tránh respawn); hạn 8→45 s; chẩn đoán in tên file tương đối, loại tiến trình, bên giữ khóa, thời gian nhả | `FullSnapshotSmoke` | 8 s: 24 lượt full → 13 PASS, 10 FAIL cleanup, 1 FAIL Edge không lên dashboard trong 12 s. 45 s: 5/5 full, 5/5 events | MITIGATED; nguyên nhân gốc chưa xác nhận |
| Takeover | Không idempotent theo requestId | Thiết kế: mỗi takeover tăng epoch | Không đổi code; test chứng minh không mất đáp án/deadline/kết quả chốt, client không tự retry | — | `takeoverReplayAfterLostAck…`, `lostTakeoverAckIsNeverRetried…` | ANALYZED, chờ A/B (QD-34) |

Chi tiết quyết định: `docs/QUYET_DINH.md` QD-31..QD-34. Contract: `docs/PROTOCOL.md` mục "Ổn định hóa17/10/2026".

## C. Git

| Mục | Giá trị |
|---|---|
| main | `07c4024ee728ce533149d8c0175e35084ee8931a` |
| PR39/40/41/42/43 HEAD | `093429a` / `3fc5bb4` / `304bc50` / `7af7046` / `772c6e9` (fetch lại 17/10, không đổi) |
| PR45 HEAD | `a652482` — không merge, superseded |
| Nền | RC `test/unified-final-20261017` `a447bfb` (source `c224ba8`, tree `22858b4`) |
| Nhánh | `stabilize/final-rc-20261017` |
| Source đã test/MSI | `3702b6d009e4ade0ac151bad6a408c1e24b8539c`, tree `1cc75692b3f067254cf774c68671d53eb5c0af85` |
| So với RC `c224ba8` | 67 file, +4467/−75 (production/test: 23 file, +785/−64); 16 commit |
| So với main | 357 file, +32548/−272; merge-tree sạch, chứa đủ 5 HEAD PR và main |
| Merge thật đã làm | Không có |

## D. Hồi quy (source `3702b6d` trừ khi ghi khác)

| Suite | PASS | FAIL | SKIP | Bằng chứng | Nhãn |
|---|---:|---:|---:|---|---|
| Maven `clean test` (Java 21.0.8) | 924 | 0 | 4 | `clean-counts.json` (82 suite XML) | REAL PG 18/GUI + MOCK/SIMULATED |
| Maven `package` | 924 | 0 | 4 | `package-counts.json` | như trên |
| MSI 0.3.10 installed FullSuite | 926 | 0 | 2 | `installed-counts.json`, `installed-run.json` | REAL installed/PG/GUI |
| ExamFlow/AT11 standalone | 41 checks | 0 | — | `RUNS.json` flow | REAL PG/HTTP; mất ACK SIMULATED |
| PostgreSQL concurrency AT01/07/09/10 | PASS | 0 | — | `RUNS.json` concurrency | REAL PG/latch |
| GUI Reading/Listening | 11 nhóm | 0 | — | `RUNS.json` gui | REAL GUI; nghe SIMULATED |
| C1 FULL + C2 late/gap/UNKNOWN | PASS, cleanup từng thành phần PASS | 0 | — | `RUNS.json` full/events | REAL PG/WS/Edge/GUI; process set MOCK |
| GitHub Actions windows-2025 (`3702b6d`; cùng kết quả ở `4fbc538`) | 854 (911 chạy, đếm ở `4fbc538`) | 0 | 57 (opt-in PG/GUI) | run 39017363076 SUCCESS; 39015251363 | REAL CI, không thay PG/MSI |
| Python E1 analyzer | 12 | 0 | 0 | `e1-raw-integrity-recheck.json` | MOCK fixture |
| Probe race cache 16×8×5 | 640/640 | 0 | — | `pr45-race-probe.txt` | REAL filesystem, MOCK manifest |

Skip trong 924: `symlinkInsideCacheRoot…` (Windows không cho tạo symlink khi thiếu quyền), `cacheRootUnderAncestorShortName…` (pipeline đặt TEMP trên E:, ổ này tắt 8.3; test này PASS khi TEMP trên C: ở lượt targeted), 2 test cần app-image/installed (bước installed bật cả hai). Test mới đều nằm trong tổng, không cộng thêm.

Lượt FAIL trung gian giữ nguyên trong `RUNS.json`: `final-4fbc538` (classpath smoke thiếu `SmokeCleanup` → JVM treo, lỗi pipeline của agent; sửa ở `3702b6d`), `final-3702b6d` và `-r2` (môi trường: build MSI mồ côi của lượt trước giữ file `target/`). CI run 39012744164 (`804e272`) FAIL vì một test client assert thông báo cũ — sửa ở `7c0491e`.

## E. MSI

| Mục | Giá trị |
|---|---|
| Version | 0.3.10 (0.3.9 giữ nguyên làm lịch sử) |
| Source | `3702b6d009e4ade0ac151bad6a408c1e24b8539c`, dirty=false |
| SHA256 MSI | `d69a6f5944471bcb2ae2323e953cbb759d1a5e4752f27ed07917aa2df2355881` |
| Client JAR trong image | `e67940ef3ae9be9d7e6339cd11c53bc615901f8399ff361278fc72f0f1e11a42` |
| Tương đương class | 5440 class, cùng tập, 0 khác byte với JAR build lại |
| Runtime | Java 21.0.8 bundled, JavaFX natives có, không TEST agent/secret, FULL mặc định |
| Cài thử | User thường, INSTALLDIR Unicode/khoảng trắng, chạy EXE thường không JAVA_HOME, cấm ghi thư mục cài, file cài không đổi, gỡ sạch: PASS (máy build, không phải máy thứ hai) |
| Lý do build lại | 10 file production đổi so với RC (`AttemptIdentifier`, `ExamService`, `AudioService`, 3 service lịch sử + `MonitoringHistoryLimit`, `MonitoringApiClient`, `DashboardController`, `AudioFiles`, `ToeicClientApplication`) |

Ghi chú vận hành: lượt `final-4fbc538` bị dừng giữa chừng đã để lại một bản cài TEST 0.3.10 (từ `4fbc538`) trong thư mục TEST riêng; đã gỡ theo product code trước lượt cài thật, xác nhận không còn sản phẩm ToeicMonitor nào.

## F. Quyết định contract

| Mục | IMPLEMENTED | PROPOSED | OWNER_APPROVED |
|---|---|---|---|
| QD-31 cấp mới fail-closed + legacy ACTIVE chỉ đọc | Có | — | Chưa (A, C) |
| QD-32 lịch sử >5000 → 409 không retry | Có | Phân trang để sau | Chưa (A, C) |
| QD-33 cache audio thay PR45 | Có | Đóng PR45 | Chưa (B) |
| QD-34 takeover không idempotent | Không đổi code | Giữ như hiện tại | Chưa (A, B) |

## G. Còn chặn

- Code: không có P0/P1 mở. Biết trước: cửa sổ TOCTOU của cache audio khi tiến trình cùng user thay thư mục; username ngoài ASCII không cấp được lượt mới.
- Duyệt: QD-31..34, PR39–43 review, C xác nhận C1/E1. Không có review/comment nào trên GitHub khi kiểm.
- Thủ công: `ACCEPTANCE_CHECKLIST.md` — LAN hai máy, nghe thật, máy sạch.
- Thực nghiệm: tải 1/5/10×3, E2 (nếu không làm: giữ FULL, ghi rõ).
- Nộp bài: báo cáo cuối, Compilatio, bàn giao source/report.

## H. Quy trình merge (một đường duy nhất)

Draft PR `stabilize/final-rc-20261017` → `integrate/unified-reading-listening-b4-b5-20261015` (nhánh của PR39).

1. Trước khi bấm: `git fetch origin` rồi kiểm `origin/integrate/unified-reading-listening-b4-b5-20261015` = `093429a` và head Draft PR = commit cuối của nhánh stabilization (SHA ghi trên PR). Nếu PR39 đã đổi, dừng và tích hợp lại.
2. A, B, C duyệt trên Draft PR (QD-31..34, C1/E1). Không squash, không rebase.
3. Merge Draft PR bằng **Create a merge commit**. Vì nhánh stabilization chứa `093429a`, đây là merge sạch (merge-tree kiểm 17/10). PR40 (`3fc5bb4` nằm trong nhánh PR39) sẽ được GitHub đánh dấu merged.
4. Đóng PR41, PR42, PR43 với ghi chú "đã vào PR39 qua Draft PR, HEAD `304bc50`/`7af7046`/`772c6e9` là ancestor". Đóng PR45 "superseded bởi `d531221` + `d562581`". Không merge riêng các PR này nữa (tránh lặp commit).
5. Kiểm: `git merge-base --is-ancestor <SHA head Draft PR> origin/integrate/unified-reading-listening-b4-b5-20261015` → 0; CI của PR39 chạy lại và PASS.
6. Sau các gate phát hành ở mục G: merge PR39 → main bằng merge commit; kiểm `git merge-base --is-ancestor 093429a origin/main` và `git diff <SHA head Draft PR> origin/main --stat` chỉ còn khác phần đổi trên main sau 17/10.

## I. Năm việc cuối

1. A + C: trả lời QD-31, QD-32 trên Draft PR; A + B: QD-34; B: QD-33 và đóng PR45.
2. C: xác nhận kết quả C1 (cleanup từng thành phần, hạn 45 s) và số E1 kiểm lại; B xem phương pháp E1.
3. B: LAN hai máy + máy sạch + nghe thật theo `ACCEPTANCE_CHECKLIST.md`, dùng MSI 0.3.10 đúng SHA256.
4. A: thực nghiệm tải 1/5/10 × 3; C: E2 hoặc ghi fallback FULL trước 27/10.
5. Cả nhóm: merge theo mục H, viết báo cáo cuối và chạy Compilatio trước 30/10.
