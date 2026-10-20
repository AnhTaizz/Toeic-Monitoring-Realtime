# EX-01 — Kết quả tích hợp cuối Test 01, 20/10/2026

**ETS_TEST01_BLOCKED_BY_MISSING_ORIGINAL_SOURCE**. Đã hoàn thiện phần kiểm kỹ thuật và bàn giao nguồn bổ sung; chưa đạt nghiệm thu đề ETS. Không import ETS, không đánh dấu EX-01/EX-08 DONE.

## Source và phạm vi

- ETL base: `be3c7c3c8aaa2526edf720d5bbc156d70a9af5a7`; source đã kiểm: `f29005646daeb9b6fec49fd856485f8f82d8acb1` trên `feat/ex01-html-pipeline-20261020`, workspace `toeic-html-pipeline`.
- Backend: `ad8c370992423d58c730db1320d7fe247beb587f`, fetch từ `origin/main`; worktree TEST riêng `target/ex01-final-main-20261020`. SHA này có EX03/05/07/06, V13/V14/V15; phần NOT_READY trong handoff cũ trên main chưa phản ánh code đã merge. Lượt này đối chiếu trực tiếp code và runtime, không tự thay trạng thái owner approval trong handoff.
- Chỉ sửa tooling EX-01, tests và hồ sơ của vai A. B cần review nội dung; không tạo chữ ký owner. Không đổi backend production, validator, DTO, Flyway, JavaFX, protocol hoặc tracker/giờ công. Không làm recovery Test02–04.
- Workspace gốc có `docs/TIEN_DO.md`, `docs/NHAT_KY.md` chưa commit từ trước; giữ nguyên. ETL sạch trước khi bắt đầu. Không thao tác worktree `toeic-v2-windows-qa`; không reset, clean, repair, outOfOrder hoặc merge main.

## CP1 — Kiểm lại nguồn gốc

[Audit nguồn](final-integration/source-audit.json) đối chiếu hai bản HTML gốc được giữ ở `target/seed-publication-20261019/{originals,originals-before-main-checkout}`. Cả hai trùng SHA `2ae59fd99f7839fb096a61c0eaacac493e4d9c672e47674c103818de32c4fa7d`; 200/200 article có text giống bản công bố, không có câu khác. Mỗi bản có 65 script, hai JSON script; lượt quét các khóa serialized choices được nêu trong probe không tìm thấy payload lựa chọn gốc. Không công bố scripts/token/nonce/raw HTML gốc. Đây là kiểm tra có giới hạn, không chứng minh mọi kiểu mã hóa tùy ý đã được giải mã.

Các HTML/JSON còn lại của Test01 trong worktree và archive là nguồn review cùng hash hoặc artifacts dẫn xuất; không tìm thấy supplement original-source mới phù hợp. Không suy tiếng Anh từ bản dịch, không dùng passage image để tự xác nhận document boundary. Không tiếp tục nới regex để tạo thêm distractors.

| Chỉ số Test 01 | Kết quả cuối |
| --- | --- |
| Câu / Part distribution | 200 / 6,25,39,30,30,16,54 |
| Options text gốc cần có ở Part3–7 | 676 |
| SOURCE_EXACT đã giữ từ lượt trước | 178: 170 English + 8 position markers |
| Khôi phục thêm trong lượt này | **0** |
| Chưa tìm thấy / lexical prefixes cần review | **495 / 3** |
| Option texts null trong draft | 498; không điền ba prefixes chưa chắc chắn |
| Key labels đối chiếu parser trước | 200/200, vẫn cùng nguồn |
| Answer keys HUMAN_VERIFIED | **0/200** |
| Documents VERIFIED | **0**; 19 group Part6/7 chưa xác minh boundaries/TEXT |
| Group mappings cần review | 73 |
| Media Test01 kiểm lại offline | **92/92**, 54 audio + 38 ảnh |
| Structural errors / importable | 0 / false |

Xem [summary](final-integration/recovery-summary.json), [166 câu thiếu options](final-integration/missing-original-options.json), [blockers từng câu, đủ 200 câu](final-integration/question-blockers.json), [document review](final-integration/document-review.json). SOURCE_EXACT chỉ xác nhận literal trong nguồn có hash/locator, không phải HUMAN_VERIFIED hoặc backend VERIFIED.

## CP2 — Supplement và báo cáo thiếu dữ liệu

`maintain.ps1 -Action recover` giữ chức năng hiện có, bổ sung:

- Nhận nhiều supplement; join theo examId + part + questionNumber và nhãn A–D tường minh. HTML cần đúng một container exam; không lấy câu thuộc đề khác.
- Parse cùng snapshot bytes đã SHA-256. Từ chối key khác lịch sử, option/prompt SOURCE_EXACT mâu thuẫn, documents mâu thuẫn, group/ID trùng và mapping sai.
- Kiểm xong toàn file mới áp thay đổi; một row/group lỗi không làm thay đổi phần đã nhận trước đó. Giữ evidence cũ và nối evidence supplement cho trường trùng khớp.
- Documents có ordered TEXT/IMAGE blocks và provenance. Ảnh phải thuộc passage group gốc, đầy đủ và đúng thứ tự; không đoán ảnh thuộc document nào. Converter giữ các blocks tường minh; boundaries vẫn NEEDS_REVIEW.
- Sinh `question-blockers.json` và `original-source-template.json`. Template cố ý chứa null/empty cho nội dung chưa có và bị từ chối nếu dùng nguyên trạng.

[Template thực tế](final-integration/original-source-template.json) và [hướng dẫn export/join](ORIGINAL_SOURCE_EXPORT.md) dành cho người có quyền truy cập trang làm bài. Dùng normalized baseline và toàn bộ supplement liên quan cùng một lượt; lưu raw/hash ngoài Git. Parser không tự tạo key/document human verification hay payload import-ready.

## CP3 — Contract trên main chính thức

`verify-contract.ps1 -Ref <SHA-main> -MainWorktree <built-main>` dùng **classes production đã build**, gồm DTO/validator/ExamApiException thật; không dùng compile adapters trong lượt này. Main classes được ưu tiên trên classpath để stub cũ trong target không che production classes.

[Log contract](final-integration/main-contract.log): deserialize và exact DTO shape **PASS, 1608 records, schemaVersion=2**. `RichExamValidator` thật từ chối nguồn/options/key/group chưa verified: **PASS negative gate**. Vì dừng ở source verification, không suy ra graph thiếu documents đã hợp lệ. Không nâng statuses để thử đi qua gate; không gửi ETS lên HTTP.

## CP4–5 — PostgreSQL và backup/restore

[Receipt runtime đã lọc](final-integration/main-mock-roundtrip.json) dùng PostgreSQL **18.1**, cluster TEST native mới, loopback/cổng riêng do OS cấp; không dùng container `toeic-db`/5433, container QA/55434 hoặc DB của agent khác. Backend JAR build từ main có SHA-256 `c2e9349140ffbf93640cc599f9c5dd589de8da1823e067aa79edf704de8c6d20`.

- Flyway chạy tuần tự V1–V15 trên DB mới; không outOfOrder/repair/clean. Log migration đã được trích vào receipt.
- Chỉ đề tự tạo **MOCK**: HTTP201 → catalog selectable=true, blockedReasons=[] → pg_dump → restore sang DB TEST chưa tồn tại → catalog vẫn selectable=true. Source/restored media roots khác nhau.
- 200 câu, 775 options, 76 groups, 57 media, 286 blocks, 1 source và toàn row/scoring fingerprints khớp trước/sau. 57 media MOCK backup/restore khớp size/SHA.
- Năm negative checks thật: target tồn tại, sai confirmation, tên ngoài TEST, SQL checksum hỏng trước CREATE và backup overwrite đều bị từ chối; target không đổi.
- Hai server và cluster do harness tạo đã dừng; không còn postmaster.pid của cluster này. SQL/dump/raw logs giữ private ngoài Git/ignored, không công bố credential hoặc token.
- `archive-verify` chạy lại trên snapshot `dataset-20261020-1b2bccf`: **757 files / 358 media PASS**. Giữ toàn bộ 358 media cũ và snapshots tại `toeic-ex01-backups`; archive-create/restore và SQL scripts đã có test, không viết lại hệ thống backup. Bằng chứng JSON/media restore cũ và PostgreSQL MOCK cũ trong [BACKUP_RESTORE](BACKUP_RESTORE.md) vẫn giữ nguyên. Lượt PostgreSQL MOCK mới nằm ở `mock-db-15624644d99f`.

**ETS import HTTP201 / DB counts / catalog selectable / session smoke / ETS backup→restore→catalog: NOT RUN**, do data gate chưa đạt. Kết quả MOCK không chứng minh ETS hợp lệ; không gọi đây là kết quả ETS selectable=false từ API.

## CP6 — Kiểm thử và giới hạn

| Kiểm tra | Kết quả |
| --- | --- |
| Unit/regression tooling | 77 PASS, 0 FAIL/ERROR/SKIP; [case names + XML SHA](final-integration/tests-summary.json) |
| Regression trước sửa | 8 tests: 7 FAIL, 1 ERROR; [summary](final-integration/regression-before-fix.json) |
| Một lượt trung gian | 1 FAIL assertion số provenance: test quên evidence prefix cũ; sửa assertion tính cả nguồn cũ, giữ evidence đó |
| Main package | PASS, tests skipped ở lượt package; không coi là full Maven test PASS |
| Test01 offline media / recovery | 92 media PASS; content BLOCKED đúng exit2 |
| DTO / source verification rejection | PASS, classes main thật |
| PostgreSQL/mock HTTP/catalog/SQL restore | PASS **MOCK only** trên server/PG thật |
| Archive verify cũ | PASS captured data, không phải acceptance nội dung ETS |
| GUI, nghe bằng tai, LAN, dữ liệu ETS import | NOT RUN |

Unit fixtures supplement là MOCK; checks real200 chỉ chứng minh parser/invariants với nguồn đã capture. Không chạy lại full reactor tests/GUI của main. Hai lỗi runner ban đầu (WSL chặn interop; cwd native không theo Set-Location) đã được sửa bằng Windows native và đường dẫn POM tuyệt đối. Log build nhầm cwd giữ riêng, **không dùng làm bằng chứng main**. [Build log đã lọc](final-integration/main-package.log) chỉ nhận lượt đúng worktree.

## Dữ liệu cần bổ sung để mở gate ETS

1. Original A–D cho 495 labels trong missing sheet và đối chiếu ba prefix A/C/D câu101; không chỉ cung cấp bản dịch hoặc vocabulary không nhãn.
2. Original prompts/passages của câu131–146; document TEXT/ID/title/boundaries, ordered image mapping cho 19 group Part6/7, xác minh 73 group mappings.
3. Review đáp án độc lập 200 câu, có người/ngày/evidence thật; lưu căn cứ quyền sử dụng cùng export.
4. Khi đã đủ bằng chứng, chạy lại pipeline và validator main, rồi mới import ETS qua PROCTOR API trên DB TEST mới và kiểm counts/catalog/backup→restore. Không INSERT trực tiếp, không sửa validator hoặc statuses để qua gate.

Đã hoàn tất phần kỹ thuật có thể thực hiện từ nguồn hiện có. Phần import ETS dừng tại **ETS_TEST01_BLOCKED_BY_MISSING_ORIGINAL_SOURCE**, không chờ vô thời hạn và không tuyên bố DONE.
