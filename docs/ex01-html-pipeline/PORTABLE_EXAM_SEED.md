# EX-01 · Portable Exam Seed (workflow chính)

Quy trình: **HTML → parse một lần → import V2 vào DB TEST → Portable Exam Seed → (có thể bỏ HTML) → restore đề vào PostgreSQL khi cần.**

Mỗi pack chứa **một đề**: dữ liệu 7 bảng đề, đáp án, documents, media metadata, file audio/ảnh, manifest SHA-256 và script restore. Pack không chứa tài khoản, ca thi, bài làm, monitoring, lịch sử Flyway hay schema. Đây là cách duy nhất hiện hành để mang đề sang PostgreSQL khác. Backup `pg_dump` toàn DB cũ đã bị bỏ ở commit `13e0745` (xem [BACKUP_RESTORE.md](BACKUP_RESTORE.md), chỉ còn giá trị lịch sử).

> **ETS vẫn BLOCKED.** Seed chỉ tạo được từ đề mà backend V2 đã nhận (HTTP 201) và catalog báo `selectable=true` với mọi trạng thái `VERIFIED`. `PortableExamSeed.validate` từ chối trạng thái `UNVERIFIED`. Không nâng trạng thái hay sửa validator để ép ETS qua gate.

## 1. Thành phần

| File | Vai trò |
|---|---|
| `scripts/create-exam-seed.ps1` | Chạy backend V2 riêng trên DB TEST mới, import `draft.v2.json` qua HTTP, kiểm catalog, export đúng 7 bảng của examId rồi gọi Java để đóng pack |
| `scripts/restore-exam-seed.ps1` + `scripts/exam-seed-common.ps1` | Restore. Được chép **vào trong pack**, chỉ cần PowerShell + `psql`; không cần repo, Java, HTML hay website |
| `tools/exam-dataset/.../PortableExamSeed.java` | `create` (sinh `seed.sql` từ dữ liệu đã chọn, chép media, ghi manifest) và `verify` |
| `tools/exam-dataset/verify-portable-seed.ps1` | Harness MOCK end-to-end: cluster nguồn → pack → xoá fixture nguồn → cluster đích → restore độc lập → kiểm |

Cấu trúc pack:

```text
<pack>/
  seed-manifest.json, seed-manifest.sha256   # size + SHA-256 mọi file, counts, schema signature, backend commit/JAR SHA
  seed.sql                                   # INSERT vào 7 bảng đề, sinh lại được từ exam-data.json
  exam-data.json                             # dữ liệu đã chọn từ DB nguồn (có đáp án)
  restore-exam-seed.ps1, exam-seed-common.ps1
  audio-resources/<examId>/<localFile>
  image-resources/<examId>/<localFile>
```

## 2. Đảm bảo an toàn (đã review trong code)

- **Chỉ dữ liệu một đề.**
  - Snapshot phải có đúng 7 bảng và đúng tập cột.
  - Mọi dòng phải có `exam_id` bằng examId của pack.
  - Bỏ `created_by`, các ID tự tăng và `source_id`.
  - `seed.sql` chỉ có `INSERT INTO public.<7 bảng>`: không `UPDATE`/`DELETE`/`DROP`/`ON CONFLICT`, không bảng tài khoản/ca/monitoring.
- **Không ghi đè.**
  - Script kiểm trước; SQL cũng có guard `RAISE EXCEPTION` nếu examId đã tồn tại.
  - Có `pg_advisory_xact_lock` theo examId.
  - Destination media phải là thư mục mới, nằm ngoài Git, không có symlink/reparse point.
- **Atomic.**
  - Chạy `psql -X --single-transaction -v ON_ERROR_STOP=1`.
  - `SET CONSTRAINTS ALL DEFERRED` cho FK `correct_option`.
  - Bất kỳ lỗi nào thì rollback toàn bộ.
- **Foreign key trên DB khác.**
  - ID mới do DB đích cấp.
  - `source_id` được nối lại bằng `(SELECT id FROM exam_sources WHERE exam_id=… AND source_sha256=…)`.
  - `exams.created_by` lấy từ `toeic.seed.proctor_id`, phải là tài khoản PROCTOR đang `enabled` ở DB đích.
- **Chống SQL injection.**
  - Mọi giá trị là literal có quote kép (`standard_conforming_strings=on`), chỉ nhận số nguyên.
  - examId theo regex `[A-Za-z0-9][A-Za-z0-9_-]{0,127}`, ProctorUserId kiểu `long`.
  - `verify` từ chối `seed.sql` không khớp với SQL sinh lại từ `exam-data.json`.
- **Đường dẫn và media.**
  - Từ chối traversal (`..`, `\`, `:`, đường dẫn tuyệt đối), file lạ, file trùng, symlink.
  - Media phải có đúng size và SHA trong metadata DB, kiểm cả lúc tạo, lúc verify và sau khi copy khi restore.
- **Đúng schema.** Restore so chữ ký cột của 7 bảng với pack và yêu cầu Flyway V14 + V15 đã chạy thành công.
- **Chỉ DB TEST.** Tên DB phải theo `toeic_*test_*`, host `127.0.0.1`, không dùng port 5433/55434; phải nhập lại đúng tên DB để xác nhận.

**Giới hạn cần biết:**
- SHA-256 chỉ phát hiện hỏng dữ liệu, **không phải chữ ký**. Script restore tự kiểm chính pack chứa nó, nên chỉ restore pack từ nguồn tin cậy.
- Pack chứa **đáp án**: giữ private, không commit, không đưa cho thí sinh.
- Media được chép vào Destination **trước** transaction DB. Nếu SQL lỗi, DB không đổi nhưng thư mục đó vẫn còn; lần chạy lại phải dùng Destination mới.
- Bước đếm dòng sau restore chạy **sau** commit. Nếu lệch, script dừng và giữ DB TEST để điều tra.
- Pack gắn với schema của 7 bảng. Khi migration sau này đổi các bảng đó, restore sẽ từ chối; phải tạo lại seed bằng backend mới.
- Tool sinh ra **thư mục**. Nén ZIP để chuyển máy là việc làm tay; sau khi giải nén, restore kiểm lại mọi file.

## 3. Export seed cho một đề (Windows, PostgreSQL 18, JDK 21)

Yêu cầu:
- Một cluster PostgreSQL TEST riêng, chạy trên loopback và port riêng (không phải 5433/55434).
- Một DB **trống** mới tạo, tên dạng `toeic_<x>_test_<y>`.
- Một JAR backend V2 khớp schema (build từ `main` đã có V14/V15). Bản thân nhánh ETL không có API import V2.
- File `draft.v2.json` đã `VERIFIED`.
- Media roots có đúng `<root>/<examId>/<localFile>`.

```powershell
mvn -f tools/exam-dataset/pom.xml test dependency:build-classpath '-DincludeScope=runtime' '-Dmdep.outputFile=target/runtime-classpath.txt'
$env:PGPASSWORD = '<mật khẩu role TEST, chỉ đặt trong phiên>'
$env:TOEIC_SEED_PASSWORD = '<mật khẩu seed tài khoản TEST>'
.\scripts\create-exam-seed.ps1 -PostgresBin '<pg18>\bin' -Database 'toeic_seed_source_test_1' -Port 55470 -UserName '<test-role>' `
  -ServerJar '<backend-v2.jar>' -JavaExe '<jdk21>\bin\java.exe' -BackendCommit '<40-hex commit của JAR>' `
  -ImportJson '<dataset>\draft.v2.json' -ExamId '<examId>' -AudioRoot '<audio-root>' -ImageRoot '<image-root>' `
  -Destination '<ngoài Git>\seeds\<examId>-<ngày>'
```

Kết quả `PORTABLE_SEED_CREATE_PASS`.
- Server bị từ chối import (ví dụ ETS còn UNVERIFIED) thì **không tạo seed**.
- Log riêng nằm trong `tools/exam-dataset/target/seed-export-*` (đã ignore).
- Sau khi pack PASS và đã chép pack sang nơi lưu thứ hai, HTML gốc không còn cần để restore.

## 4. Restore vào PostgreSQL mới

1. Tạo DB TEST mới. Chạy backend V2 một lần trỏ vào DB đó để Flyway tạo schema và tài khoản seed, rồi dừng. Lấy ID giám thị: `SELECT id FROM user_accounts WHERE role='PROCTOR' AND enabled`.
2. Chạy script **trong pack** (không cần repo):

```powershell
$env:PGPASSWORD = '<mật khẩu role TEST>'
& '<pack>\restore-exam-seed.ps1' -PostgresBin '<pg18>\bin' -Database 'toeic_demo_test_1' -ConfirmDatabase 'toeic_demo_test_1' `
  -ProctorUserId <id> -Port 55471 -UserName '<test-role>' -Destination '<thư mục media mới>'
```

3. Chạy backend với `TOEIC_AUDIO_ROOT=<Destination>\audio-resources` và `TOEIC_IMAGE_ROOT=<Destination>\image-resources`. Đăng nhập giám thị, kiểm `GET /api/v2/exams`: examId có `selectable=true`, `blockedReasons=[]`. Receipt nằm ở `<Destination>\restore-receipt.json`.

Kết quả `PORTABLE_SEED_RESTORE_PASS`. Các trường hợp bị từ chối: đề đã tồn tại, sai tên xác nhận, DB không phải TEST, schema khác, chưa có V14/V15, ProctorUserId không phải PROCTOR, pack hoặc media hỏng.

**Lưu ý cho demo:** DB mặc định của `docker-compose` là `toeic`, port 5432/5433, nên **bị chốt an toàn từ chối**. Hiện phải dùng một DB tên `toeic_*test_*` trên port khác cho demo. Có cho phép restore vào DB demo mặc định hay không là quyết định của owner A, chưa đổi trong code.

## 5. Dữ liệu capture cũ (archive JSON/media) — không xoá

`DatasetArchive` (archive-create/verify/restore) đã bị bỏ ở `13e0745`, nhằm chỉ giữ một hệ thống. Dữ liệu capture **không mất**:
- **Trong Git:** HTML nguồn `data/seedingTest/ets2026_test0[1-5].html`, JSON normalized/draft/recovery của Test 01 (`data/seedingTest/generated/ex01/`), manifest tải media của 4 đề (`docs/ex01-html-pipeline/media/*.download-manifest.json`), và bản sao manifest của archive (`docs/ex01-html-pipeline/recovery/archive-manifest.json`, SHA `d56c25d6…`; 757 file, 358 media = 216 audio + 142 ảnh).
- **Ngoài Git:** 358 file media nhị phân nằm trong media roots local và trong snapshot `dataset-20261020-1b2bccf` (máy Windows của nhóm). Snapshot đó tự đủ: file gốc + `archive-manifest.json`. **Đây là bản media duy nhất ngoài máy local của ETS.** Đừng xoá trước khi ETS được xử lý xong; đã chép sang thiết bị thứ hai thì kiểm lại ở máy nhận.

Kiểm archive không cần tool cũ: lưu đoạn sau thành `verify-archive.py` ngoài repo và chạy `python -I verify-archive.py <archive>`. Exit 0 nghĩa là manifest và mọi file khớp. Trên cloud, lệnh này mới thử trên archive giả lập (cả ca nguyên vẹn lẫn ca bị hỏng); **chưa chạy trên archive thật**.

```python
import hashlib, json, pathlib, sys
a = pathlib.Path(sys.argv[1]); h = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert h(a / "archive-manifest.json") == (a / "archive-manifest.sha256").read_text().strip(), "manifest checksum mismatch"
m = json.loads((a / "archive-manifest.json").read_text(encoding="utf-8"))
bad = [f["path"] for f in m["files"] if not (a / f["path"]).is_file()
       or (a / f["path"]).stat().st_size != f["sizeBytes"] or h(a / f["path"]) != f["sha256"]]
media = sum(f["path"].startswith(("audio-resources/", "image-resources/")) for f in m["files"])
print("files", len(m["files"]), "media", media, "/", m["mediaAssetCount"], "mismatch", len(bad), bad[:5])
sys.exit(1 if bad or media != m["mediaAssetCount"] else 0)
```

Lấy lại media cho pipeline thì chép `audio-resources/` và `image-resources/` của archive làm media roots. Cần đối chiếu với SHA của archive trong Git: so `archive-manifest.sha256` của archive với `d56c25d6…`.

## 6. Kết quả kiểm thử

| Lượt | Ai/đâu | Kết quả | Trạng thái bằng chứng |
|---|---|---|---|
| `mvn test` (`tools/exam-dataset`) tại `13e0745` | Codex, Windows | 82 PASS | Báo cáo của Codex |
| Cùng lệnh tại `13e0745` | Claude cloud, Linux, JDK 21.0.12, Maven 3.9.11 | **82/82 PASS** | Chạy lại thật |
| `verify-portable-seed.ps1`, PostgreSQL 18, MOCK `mock-ex02-200` | Codex, Windows | 200 câu / 775 options / 24 documents / 57 media; restore độc lập sau khi xoá fixture nguồn; catalog selectable; fingerprint ngoài đề không đổi | **Theo báo cáo Codex; không có receipt trong Git**, cloud không kiểm được |
| Lõi cùng các bước đó trên Linux, PostgreSQL 18.1 | Claude cloud | **PASS** (chi tiết bên dưới) | Script PowerShell **NOT RUN** (cloud không có PowerShell); harness Python dùng nguyên văn SQL export |
| ETS Test 01 tạo seed | — | **BLOCKED** (UNVERIFIED) | Đúng thiết kế |

**Lượt Linux trên cloud:**
- Môi trường: PostgreSQL 18.1, jar backend build từ `main` `ad8c370` (V1–V15), sha256 `f6e8ba00…`.
- Nguồn: import MOCK qua HTTP 201, catalog `selectable=true`.
- Pack: Java `create` + `verify` PASS. Counts: 1 exam, 1 source, 76 groups, 57 media, 200 câu, 775 options, 286 blocks. `seed.sql` chỉ ghi 7 bảng.
- Đích: DB mới, đẩy sequence lên 10000, chữ ký schema khớp.
- Từ chối đúng: CANDIDATE đứng tên giám thị, SQL bị cắt giữa chừng, đề trùng; sau các lần từ chối DB vẫn 0 dòng đề.
- Sau restore:
  - Dữ liệu 7 bảng **giống hệt** nguồn (so text JSON export).
  - 24 documents; `created_by` là giám thị ở DB đích.
  - ID đã cấp lại (≥ 10000).
  - Fingerprint các bảng ngoài đề không đổi.
  - Catalog 200 câu, `selectable=true`, `blockedReasons=[]`.
