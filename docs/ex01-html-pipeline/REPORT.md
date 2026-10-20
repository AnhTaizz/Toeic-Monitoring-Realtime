# EX-01 — Bàn giao pipeline HTML và media offline

Ngày 20/10/2026. Branch `feat/ex01-html-pipeline-20261020`, worktree riêng `E:\Project\toeic-html-pipeline`, base `origin/main` đã fetch là `a6fba31`. Không sửa tracked files ở main/Windows QA hoặc worktree khác; không merge/cherry-pick backend.

**CP1–CP4 đã có code và kiểm kỹ thuật. Dữ liệu đề vẫn BLOCKED; CP5 PostgreSQL NOT RUN.** Không đánh EX-01 DONE, không thay BACKLOG/TRACKER/giờ công hoặc tạo chữ ký review A/B.

## 1. Dữ liệu đã parse và đo

| Đề | Câu | Option slots | Group V2 | Audio local | Ảnh local | Tổng bytes media | Lỗi cấu trúc |
|---|---:|---:|---:|---:|---:|---:|---:|
| ETS 2026 Test 01 | 200 | 775 | 73 | 54 | 38 | 47.340.695 | 0 |
| ETS 2026 Test 02 | 200 | 775 | 73 | 54 | 33 | 46.628.280 | 0 |
| ETS 2026 Test 03 | 200 | 775 | 73 | 54 | 38 | 48.196.221 | 0 |
| ETS 2026 Test 04 | 200 | 775 | 73 | 54 | 33 | 47.737.106 | 0 |

Mỗi đề có đúng **6/25/39/30/30/16/54** câu theo Part 1–7, đủ số 1–200. 73 group gồm 42 section group trong DOM và 31 group đơn Part1/2 chuẩn hóa từ audio của từng article. Không suy Part chỉ từ số câu: parser đọc thuộc tính Part/section, validator đối chiếu ranges/order.

775 là số ô lựa chọn, **không phải 775 text đã được xác minh**. Part1/2 có 99 ô chỉ nhãn, text null. Mỗi đề còn thiếu **676 text options gốc** của 169 câu Part3–7. Các bản dịch/option trong explanation được giữ làm review evidence, không đưa vào options candidate.

Test05 có checksum trùng Test04, được ghi alias trong [source-audit.json](source-audit.json); không tải lại hoặc tạo payload/import Test05. [measured-summary.json](measured-summary.json) và [batch-report.json](batch-report.json) chứa số liệu máy sinh.

## 2. Media thực tế và hoạt động offline

Đã tải hợp lệ **358 asset, 189.902.302 bytes**, gồm **216 audio MP3 + 142 ảnh**. Tất cả audio decode toàn bộ frame thành PCM bằng JLayer; duration được đo từ sample count. Tất cả ảnh được kiểm signature, decode bằng ImageIO và đo dimensions. MIME header HTTP được đối chiếu MIME từ bytes; size/hash đo thật, áp limits EX03.

Người dùng xác nhận nguyên văn **“Có quyền tải, sử dụng và công bố media”** trong task ngày20/10/2026. [media-permission.json](media-permission.json) lưu `USER_CONFIRMED`, phạm vi host/source checksums và quyền download/use/publish. Không ghi thành giấy phép do Zenlish cấp; `licenseDocument` vẫn null. Xác nhận media này tách khỏi quyền công bố HTML ngày19/10.

Chỉ GET HTTPS public `/wp-content/uploads/` của Zenlish, không cookie/auth/token/query/redirect/CAPTCHA solver hoặc retry tự động. Test01 lượt đầu tải92 file; lượt ETL cả4 đề dùng cache92 file này và tải266 file còn lại. Không có asset bị chặn tải/kiểm trong lượt thực tế cuối.

File thật được giữ local, không nằm trong commit:

```text
TOEIC_AUDIO_ROOT = <worktree>/audio-resources
TOEIC_IMAGE_ROOT = <worktree>/image-resources
<root>/<examId>/<localFile>
ví dụ audio-resources/ets2026-test01/032-audio-001.mp3
```

Receipt ở `tools/exam-dataset/target/media-receipts/<examId>/<mediaId>.json` gắn URL nguồn, checksum HTML, quyền và hash media. Lượt chạy offline cuối không bật Download; cả358 asset là **OFFLINE_VERIFIED**, SHA-256 được pin theo receipt lần ETL đầu. Code và unit test xác nhận chế độ manifest/offline không gọi fetcher. File đổi checksum bị BLOCKED, không tải lại hay ghi đè để che lỗi.

[Manifest từng đề](media/) ghi `uses` với câu/group, số câu thành viên, Part, DOM selector và blockId; kèm sourceUrl, localFile, MIME, bytes/hash, pixel/duration/decode. `LOCAL_VERIFIED` ở đây có bằng chứng bytes/decode thực, không xác nhận nội dung/mapping hoặc nghe vật lý. **Nghe thật/JavaFX/render/LAN NOT RUN.**

`sourceUrl` chỉ là provenance trong import metadata. Không sửa Spring media API, schema/Flyway hoặc JavaFX; runtime tiếp tục dùng media local qua EX03/EX05/EX07. Chưa có bằng chứng app thi hoàn chỉnh offline vì đề chưa import được.

## 3. Field còn cần xác minh

Mỗi đề còn các blocker trong quality report:

- 200 answer keys: đã trích đúng trường “Đáp án đúng” của nguồn, chưa đối chiếu đáp án gốc.
- 169 câu Part3–7: thiếu676 text options gốc. Không dùng bản dịch để thay thế.
- 73 group mappings: có ánh xạ DOM và shared audio, còn review nội dung từ đề gốc.
- 19 group Part6/7: cần documentId/order/title, ranh giới tài liệu và TEXT gốc. HTML chủ yếu cho ảnh passage; không tự coi mỗi ảnh là một document hoặc bịa TEXT để qua validator.
- Review nguồn/nội dung bởi A/B còn thiếu. Quyền media đã được người dùng xác nhận; không còn blocker thiếu file media trong lượt cuối.

`fieldReviews` ghi trạng thái từng field: EXTRACTED/NEEDS_REVIEW/VERIFIED/REJECTED và evidence. Chỉ hash nguồn và metadata media đo/decode thật được đánh VERIFIED tự động; nội dung không được tự nâng trạng thái.

## 4. Converter V2 và artifacts

Đã sinh `RichExamImportRequest`, `schemaVersion=2`, đúng tên field/type/enum DTO EX03 đọc từ EX06 `448f58e659131ab189a81c378970a501cf8455cf`. Part1/2 không có transcript trong prompt/options candidate; explanation và source answer text không được copy sang request.

Đây là **draft**, không có `import-ready.v2.json`. Sources/question answer keys/group mappings/options giữ UNVERIFIED. `mediaValidationStatus=VERIFIED` chỉ khi tất cả media đã kiểm local bytes/decode. Phần reading documents chưa có bằng chứng mapping nên để rỗng; ảnh passage gốc vẫn đầy đủ trong normalized dataset, chưa tạo graph document giả để vượt importer.

Snapshot Test01 được commit tại [data/seedingTest/generated/ex01/ets2026_test01](../../data/seedingTest/generated/ex01/ets2026_test01/): normalized, draft V2, media/download manifest, quality và blocked report. Output đầy đủ Test01–04 ở `tools/exam-dataset/target/output/`, sinh lại từ script. Manifest media cả4 đề và summary được lưu trong thư mục báo cáo; binary media không commit dù người dùng đã xác nhận quyền công bố.

[artifacts-sha256.json](artifacts-sha256.json) lưu checksum/size các artifacts bàn giao. Gitattributes pin manifest đầu vào và JSON artifacts theo LF, giữ nguyên bytes log contract để checksum không đổi qua Windows/WSL. Seed/script PowerShell cũ được giữ nguyên để đối chiếu, không biến seed cũ thành import-ready.

## 5. Kiểm thử đã chạy

Source code **`e73e325ad03865d6ff0a85d318e6e19a68a01424`**, Oracle JDK21.0.8 Windows chạy qua WSL, Maven3.9.11:

| Nhóm | PASS | FAIL / ERROR / SKIP |
|---|---:|---|
| HtmlExamParserTest | 10 | 0 / 0 / 0 |
| LocalMediaInspectorTest | 8 | 0 / 0 / 0 |
| MediaDownloaderTest | 10 | 0 / 0 / 0 |
| PipelineTest | 19 | 0 / 0 / 0 |
| Tổng | **47** | **0 / 0 / 0** |

[tests-summary.json](tests-summary.json) được tổng hợp từ Surefire XML. Test negative gồm thiếu/trùng câu, swap Part vẫn giữ counts, key ngoài options, transcript leak, group membership/shared audio, document boundaries/order/TEXT, media references, thiếu quyền, token URL/host, HTTP401/403/redirect/CAPTCHA200, MIME giả, ảnh hỏng, MP3 không có PCM, cache checksum thay đổi và file operator có sẵn. Fixtures HTTP/PNG/WAV là **MOCK**, không lấy unit tests làm bằng chứng tải Zenlish thật.

[contract-probe.log](contract-probe.log): cả4 draft PASS kiểm shape bằng các record thật, deserialize được, và đều bị `RichExamValidator` thật từ chối ở verification gate. Hai compile adapters tối thiểu chỉ thay annotation/exception trong harness; không thay validator/DTO hoặc mô phỏng server/PG. Vì validator dừng ở nguồn UNVERIFIED, kết quả này không khẳng định các graph draft thiếu documents đã hợp lệ để import.

Lượt chuẩn bị môi trường trước đó từng thiếu dependency Maven offline/JUnit launcher và gặp Surefire tìm `java` không có `.exe` trong PowerShell mở qua WSL. Đã sửa runner dùng executable/process rõ ràng và JVM fork `java.exe`; lượt cuối47 tests PASS. Native pipeline trả **2 = BLOCKED**, đã kiểm mã thoát riêng. Khi gọi script qua một lớp PowerShell `-Command` khác, cần truyền tiếp `$LASTEXITCODE` để không đổi2 thành1.

Full reactor server/client, Spring HTTP, PostgreSQL, catalog, GUI/hearing/LAN: **NOT RUN trong task này**.

## 6. PostgreSQL và catalog

**Không có đề nào được task này import PostgreSQL thực sự.** Không gọi POST import/SQL INSERT, không chạy DB production, không sửa backend validator hoặc migrations.

Baseline branch `a6fba31` có FlywayV13, chưa chứa EX03/V14 và EX07/V15. Task chỉ đọc contract ở EX06, không dùng baseline V13 để giả làm backend V2. CP5 chỉ bắt đầu khi dữ liệu đã verified và có backend tích hợp phù hợp + PostgreSQL TEST cách ly.

HTTP201/200 questions/options/groups/media read-back/catalog `selectable=true`/`blockedReasons=[]`: **NOT VERIFIED**. Chưa gọi catalog để kết luận selectable; đây không phải kết quả selectable=false từ API.

## 7. SHA, lệnh chạy lại và việc tiếp theo

Checkpoints: CP1 `57bbaa7`; CP2 `53d5b78`; CP3 `40a508d`; CP4 `e73e325`. Commit sau CP4 chỉ lưu hồ sơ/artifacts, không đổi source đã kiểm. Xem `git log --oneline origin/main..HEAD` để lấy SHA bàn giao hiện tại.

Chạy từ worktree với JDK21/Maven trên PATH:

```powershell
# Một lần ETL: URL public được phép dùng; receipt hợp lệ tránh tải lại.
./tools/exam-dataset/run.ps1 -Test all -Download `
  -Permissions docs/ex01-html-pipeline/media-permission.json `
  -AudioRoot ./audio-resources -ImageRoot ./image-resources

# Tái lập offline: Maven offline, toàn bộ media từ local roots, pin receipt hash.
./tools/exam-dataset/run.ps1 -Test all -Offline `
  -Permissions docs/ex01-html-pipeline/media-permission.json `
  -AudioRoot ./audio-resources -ImageRoot ./image-resources

./tools/exam-dataset/verify-contract.ps1 `
  -Ref 448f58e659131ab189a81c378970a501cf8455cf
```

Pipeline exit2 là kết quả dự kiến với nội dung hiện tại. Không đổi UNVERIFIED thành VERIFIED để làm exit0. Muốn tiếp tục CP5, người cung cấp/A-B cần bổ sung options gốc, answer key đã review, document TEXT/boundaries và evidence mapping clip/câu. Sau đó mới chuẩn bị request verified, chạy validator thật và import qua API trên DB TEST; không bypass importer. [README công cụ](../../tools/exam-dataset/README.md) mô tả CLI và giới hạn.
