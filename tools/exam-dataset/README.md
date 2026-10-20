# Pipeline EX-01: HTML review → normalized dataset → draft V2

Công cụ Java 21 chạy độc lập, không nằm trong reactor server/client. Parser dùng DOM jsoup, đọc Part từ thuộc tính HTML và group từ ancestry; không dùng regex để cắt HTML. Regex chỉ đọc các token văn bản đã qua DOM.

Nguồn hiện có: bốn đề khác nhau Test 01–04. Test 05 trùng byte Test 04 và bị loại khỏi ETL/import; `source-audit.json` ghi alias này.

## Chạy trên Windows

Yêu cầu JDK 21, Maven trên `PATH`. Từ repository:

```powershell
# Tests; lần đầu Maven có thể cần tải dependency mã nguồn/thư viện.
mvn -f tools/exam-dataset/pom.xml test

# Chuẩn bị một lần: tải các URL công khai đã được xác nhận quyền sử dụng.
./tools/exam-dataset/run.ps1 -Test all -Download `
  -Permissions docs/ex01-html-pipeline/media-permission.json `
  -AudioRoot ./audio-resources -ImageRoot ./image-resources

# Chạy lại hoàn toàn bằng media local; không có HTTP GET tới Zenlish.
./tools/exam-dataset/run.ps1 -Test all -Offline `
  -Permissions docs/ex01-html-pipeline/media-permission.json `
  -AudioRoot ./audio-resources -ImageRoot ./image-resources

# Contract main: build backend trong worktree TEST riêng, không merge ETL vào main.
./tools/exam-dataset/verify-contract.ps1 `
  -Ref origin/main -MainWorktree '<built-main-worktree>'
```

`-Offline` đặt Maven offline; HTTP tới Zenlish chỉ được thực hiện khi có `-Download`. PowerShell thực thi script phải được môi trường cho phép; có thể chạy từ PowerShell đã cấu hình theo chính sách máy. `run.cmd` là lựa chọn từ cmd: `tools\exam-dataset\run.cmd --test all --permissions docs/ex01-html-pipeline/media-permission.json --download true`.

`run.ps1` dùng process helper tường minh cho môi trường Windows PowerShell được mở qua WSL. Nếu `.git` worktree do môi trường khác tạo, `verify-contract.ps1` nhận thêm `-GitDirectory <git-common-directory>`.

Java CLI sau build và `dependency:build-classpath`:

```powershell
$cp = 'tools/exam-dataset/target/classes;' + (Get-Content tools/exam-dataset/target/runtime-classpath.txt -Raw).Trim()
java -cp $cp vn.edu.toeic.dataset.ExamDataset --root . --test 01 `
  --permissions docs/ex01-html-pipeline/media-permission.json `
  --audio-root ./audio-resources --image-root ./image-resources `
  --output tools/exam-dataset/target/output --download false
```

Có thể cung cấp `TOEIC_AUDIO_ROOT` và `TOEIC_IMAGE_ROOT` thay hai tham số root. File luôn ở `<root>/<examId>/<localFile>`. Audio có prefix số câu đầu group ba chữ số, ví dụ `032-audio-001.mp3`. Đường dẫn trong JSON chỉ là tên file, không phải đường dẫn máy cá nhân.

Exit code: `0` cho tests/contract probe thành công; pipeline `2` khi đã sinh artifacts nhưng dữ liệu **BLOCKED**; lỗi Java/IO `1`. Công cụ hiện xuất **draft**, chưa có luồng tự chứng nhận review nội dung hoặc tự import DB.

## Artifacts

Mặc định `tools/exam-dataset/target/output/` chứa `source-audit.json`, `batch-report.json` và mỗi đề:

| File | Nội dung |
|---|---|
| `normalized.json` | Metadata, questions, groups, passage blocks, options slots, đáp án nguồn, provenance và trạng thái từng field |
| `quality-report.json` | Lỗi cấu trúc và blocker xác minh, tách riêng |
| `media-manifest.json` | MediaId, sourceUrl, localFile, MIME, bytes/hash, pixel/duration/decode và `uses` ánh xạ câu/group |
| `download-manifest.json` | Quyền, ánh xạ, HTTP status/MIME, trạng thái tải/local verification từng asset |
| `draft.v2.json` | Chỉ field DTO `RichExamImportRequest`, schemaVersion 2; không phải payload import-ready |
| `blocked-report.json` | Blocker, media inspection, PostgreSQL/catalog NOT RUN |

`fieldReviews` dùng JSON Pointer tới mỗi leaf/collection rỗng, với `EXTRACTED / NEEDS_REVIEW / VERIFIED / REJECTED` và evidence. Hash nguồn được đo thật. Metadata media chỉ `VERIFIED` sau khi đo local bytes và decode; HTML width/height không được dùng làm kích thước thật.

Review candidates từ phần giải thích được giữ riêng và `REJECTED` cho mục đích candidate options. Part 1/2 chỉ có số câu và nhãn options, không có transcript trong draft V2. Part 3–7 hiện thiếu 676 text options gốc mỗi đề; công cụ không lấy bản dịch để lấp chỗ trống.

DOM hiện không đủ bằng chứng cho ranh giới document Part 6/7. Parser giữ ảnh/text theo thứ tự trong `passageBlocks`, để `documents=[]`. Draft không tự gán mỗi ảnh thành một document; vì vậy phần reading và media liên quan chưa có graph V2 hoàn chỉnh. Mọi mapping/đáp án/options vẫn cần review từ nguồn gốc.

## Media ETL và quyền

Quyền media được xác nhận riêng trong [media-permission.json](../../docs/ex01-html-pipeline/media-permission.json), gắn checksum từng HTML. Đây là lời xác nhận của người dùng ngày 20/10/2026; không phải văn bản do Zenlish cấp và không sửa thông tin quyền HTML lịch sử.

Downloader chỉ nhận HTTPS public `/wp-content/uploads/` của `zenlishtoeic.vn`, host nằm trong scope quyền và checksum nguồn khớp. Không nhận userinfo/query/fragment; không cookie, token, đăng nhập, CAPTCHA solver, redirect hoặc retry tự động. HTTP khác 200, HTML/CAPTCHA, sai MIME/signature, quá size hoặc decode lỗi đều thành blocker riêng.

File tải về được kiểm trong staging, publish atomically khi hợp lệ. Receipt trong `target/media-receipts/` gắn source URL, checksum HTML, quyền và SHA-256 media. Lượt sau kiểm lại receipt/hash và dùng file local; không tải lại file hợp lệ. File có sẵn thiếu receipt hoặc checksum thay đổi được giữ nguyên và báo BLOCKED. Muốn dùng media do operator cấp sẵn thì chạy chế độ kiểm local, không bật download.

Ảnh: JPEG/PNG signature + ImageIO decode toàn ảnh, pixel bounds theo EX03. WAV: Java Sound đọc toàn PCM và kiểm frame length. MP3: JLayer decode toàn bộ frame thành PCM và đo duration từ sample count. M4A chưa có decoder trong công cụ: báo BLOCKED, không `LOCAL_VERIFIED`. Decode kỹ thuật không thay việc nghe vật lý hoặc xác nhận clip thuộc đúng câu.

`sourceUrl` chỉ là provenance. Metadata V2 chứa localFile/hash/size/MIME; không sửa backend media API hoặc JavaFX và không thêm hotlink vào runtime. Các roots audio/images bị Git ignore; bộ bàn giao chỉ commit code, manifest/report và JSON draft.

## Thư viện

| Dependency | Lý do |
|---|---|
| jsoup 1.17.2 | HTML DOM, nested sections/articles và entities |
| Gson 2.13.2 | JSON; dùng cùng phiên bản repository |
| JLayer 1.0.1 | Decode MP3 thành PCM offline; không mở thiết bị âm thanh |
| JUnit Jupiter 5.12.2 | Tests parser, data quality, converter và media với transport/ảnh/WAV MOCK |

Kết quả đo và phần import còn bị chặn ở [REPORT.md](../../docs/ex01-html-pipeline/REPORT.md). Contract probe compile DTO/validator thật với hai adapter compile tối thiểu cho annotation/exception; đây là kiểm graph/DTO, **không** phải Spring HTTP/PostgreSQL.


## Recovery Test 01 và backup/restore (20/10/2026)

`maintain.ps1 -Action recover` mở rộng parser hiện có, mặc định Test01; tùy chọn `-Supplement <original-local.json|html>` join theo identity và giữ UNVERIFIED. Exit2 báo dataBLOCKED. Có178sourceexact option texts (170English+8position markers),495missing+3review;47baseline+12recovery+5archive=64tests PASS. Những số676/47 ở mô tả baseline phía trên thuộc lần parse trước recovery.

`maintain.ps1 -Action archive-create|archive-verify|archive-restore` giữ JSON/provenance/media trong thư mục ngoài Git; destination mới bắt buộc. [Hướng dẫn build/lệnh/roots/giới hạn](../../docs/ex01-html-pipeline/BACKUP_RESTORE.md), [kết quả recovery và missing fields](../../docs/ex01-html-pipeline/RECOVERY_REPORT.md), [format nguồn bổ sung](../../docs/ex01-html-pipeline/ORIGINAL_SOURCE_EXPORT.md).

`scripts/backup-toeic-db.ps1` dùngpg_dump, `scripts/restore-toeic-db.ps1` tạoTESTmới/rỗng vàpsqlrestore. `verify-db-roundtrip.ps1` đã chạy thật vớiMOCK200quaV2/PG18.1/catalog sau restore+copiedmedia, không ETS acceptance. Không credentials/binarymedia/SQLdump trong Git.

## Tích hợp main / Test01 cuối ngày20/10

[FINAL_INTEGRATION_REPORT](../../docs/ex01-html-pipeline/FINAL_INTEGRATION_REPORT.md) là trạng thái mới nhất của task. ETS vẫn `ETS_TEST01_BLOCKED_BY_MISSING_ORIGINAL_SOURCE`: không có thêm nguồn nguyên văn, không có payload import-ready. Không dùng kết quả PostgreSQL MOCK để kết luận ETS PASS.

Mặc định `verify-contract.ps1` đọc `origin/main`. Để dùng **DTO/validator/exception production đã build**, không dùng compile adapters:

```powershell
# Build backend trong một worktree TEST riêng của origin/main trước.
.\tools\exam-dataset\verify-contract.ps1 `
  -Ref '<verified-main-sha>' -MainWorktree '<built-main-worktree>' `
  -Draft '<recovery-output>/draft.v2.json'
```

`MainWorktree` phải khớp SHA của `Ref`; main server/protocol cần có target/classes và server JAR. Tool lấy đúng spring-web dependency từ JAR để dùng exception thật, đặt classes main trước adapters cũ trên classpath. Shape PASS + source gate rejection không chứng minh document graph thiếu đã hợp lệ. Bỏ `MainWorktree` là chế độ source-export/compile adapters lịch sử, phải ghi rõ mode khi báo cáo.

`maintain.ps1 -Action recover -Supplement @('first.json','second.html')` nhận nhiều nguồn, pin bytes SHA và từ chối conflicts trước khi áp toàn file. [Export/join](../../docs/ex01-html-pipeline/ORIGINAL_SOURCE_EXPORT.md) mô tả options, TEXT/IMAGE blocks và template sinh mới. `question-blockers.json` luôn có200row; `original-source-template.json` cố ý chưa hợp lệ đến khi có nội dung thật.

`contract/AuditOriginalSources.java` là probe đọc-only dùng Jsoup/Gson hiện có, so HTML gốc được lưu trước sanitization với bản công bố. Khi chạy bằng runtime classpath của tool, truyền lần lượt repo gốc chứa `target/seed-publication-20261019`, published Test01 HTML và output JSON. Chỉ xuất hashes/counts, không xuất scripts/tokens. Key scan không phải bộ giải mã mọi dạng serialized payload.
