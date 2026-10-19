# EX-02 / ADR-V2-03 — hồ sơ thiết kế

Ngày19/10/2026. **CANDIDATE / PROPOSED_FOR_OWNER_REVIEW**, chưa owner sign-off. [Contract](../../docs/EX02_RICH_EXAM_CONTRACT_V1.md), [ADR](../../docs/plan-v2/ADR-V2-03_SESSION_START_DEADLINE.md), [decision register/roadmap](../../docs/plan-v2/EX02_DECISIONS_ROADMAP.md), [fixtures MOCK](../../docs/plan-v2/fixtures/ex02/README.md).

## Baseline và bảo toàn workspace

Đã native Git fetch origin/main, xác nhận `1d35b53729abade7b460d35ab07d042b6f9f3571` chứa PR65/V11 và PR66/V12. [CI baseline39214612088](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39214612088) SUCCESS. Branch riêng `docs/ex02-contract-adr-start-20261019` trong worktree ignored; root vẫn main3f5775b với docs/launcher/evidence chưa commit của người dùng, không checkout/reset/stash hoặc chép thay các file đó. Không sửa worktree thành viên, main, tracker, migrations, Java/POM hoặc generated seed.

Source đã đối chiếu: ExamService/ExamValidationService/ExamController; V1–V12; import/candidate/audio/session records; AudioCatalog/AudioService/ListeningService/ListeningDelivery; ExamEntry/ExamSession/CandidateExamView/ProctorSessionView/network adapters; PostgreSQL/HTTP regression và tài liệu schema/backend/protocol/QD21/Listening/V2. Source links gốc: [ExamService](../../server/src/main/java/vn/edu/toeic/server/exam/ExamService.java), [AudioService](../../server/src/main/java/vn/edu/toeic/server/audio/AudioService.java), [AudioCatalog](../../server/src/main/java/vn/edu/toeic/server/audio/AudioCatalog.java), [ListeningDelivery](../../server/src/main/java/vn/edu/toeic/server/audio/ListeningDelivery.java), [ExamEntry](../../client/src/main/java/vn/edu/toeic/client/exam/ExamEntry.java), [V11](../../server/src/main/resources/db/migration/V11__rich_exam_content.sql), [V12](../../server/src/main/resources/db/migration/V12__require_content_block_media_type.sql).

## Nội dung và giới hạn

- Chọn document metadata tối thiểu trên blocks để giữ ranh giới Part7; records/nullable/constraints, SQL mapping/transaction, image policy và audio ordinal bridge cụ thể. Migration tiếp theo chỉ là thiết kế, chưa có SQL.
- ADR chọn explicit PROCTORED_V2, thời gian từ existing Listening START; legacy QD21 giữ nguyên. Gate cả v1; session→attempt khóa ổn định; phân biệt cache autosave/submit và current-writer check. Không tạo START authority thứ hai.
- Fixture MOCK tự tạo 200 câu/76group/55media,54 WAV im lặng10ms và1PNG8×8 reuse. Part3 shared3câu, Part6 passage4câu, Part7 Email/Notice+image/Reply.19 mutation cases +10 access-policy cases; đây là kiểm thiết kế, không backend integration.

## Verification của lượt này

Windows PowerShell5.1 / Oracle JDK21.0.8, chạy trên branch này trước commit:

| Kiểm thực tế | Kết quả / evidence |
|---|---|
| verify-design.ps1 (fixture đã sinh, chạy lại sau sửa) | PASS: MOCK200 câu/76group/55media;54 filename đúng playlist;19 negative +10 access-policy scenarios đúng expected reason/status; [JSON kết quả](../../docs/plan-v2/fixtures/ex02/verification.mock.json) |
| verify-contract.ps1 -JavacPath javac | PASS:21 record signatures compile --release21;7 example roots/nested fields khớp records;12 fixture JSON parse;85 local Markdown links resolve; [JSON kết quả](../../docs/plan-v2/fixtures/ex02/contract-verification.mock.json) |
| Git scope và bảo toàn root | PASS: chỉ docs/fixtures/evidence/README/gitattributes; root HEAD/status đúng snapshot đầu phiên; no runtime/migrations/data/tracker diff |
| Git diff --check | PASS ở cả audit và staged diff, bao gồm toàn bộ90 file mới/thay đổi |
| Exact main recheck trước công bố | Vẫn1d35b53729abade7b460d35ab07d042b6f9f3571 |
| GitHub CI cho PR mới | NOT RUN tại snapshot hồ sơ trước push; kiểm exact HEAD và báo URL/kết quả riêng khi PR được mở |

Những lỗi checker trong quá trình chuẩn bị: parser PowerShell từ khóa `Sequence`, sort thuộc tính OrderedDictionary, display Select-Object trên hashtable và native javac thiếu Windows/JDK PATH đã được phát hiện/sửa; không failure production. Chữ ký compile cuối và hai checker trên đã PASS; không dùng kết quả trước sửa làm PASS cuối.

Runtime mới DTO/API/import/media decoder/START gate: **NOT IMPLEMENTED / NOT RUN**. Maven regression, PostgreSQL, JavaFX GUI, hearing, junction, physical LAN/máy2: **NOT RUN trong task thiết kế**. Số984PASS/6SKIP ở [EX04](../../docs/EX04_V11_VERIFICATION.md) là lịch sử source/test của task trước, không rerun hoặc thay cho test ca V2 này. CI của PR sẽ kiểm riêng exact HEAD; CI hiện hành không chứng minh target V2 đã tồn tại.

Owner A/B cần duyệt contract/ADR từng D01–D09; C review migration/auth/lock/timeout. Người dùng giao Codex review kỹ thuật, không đồng nghĩa cho phép giả chữ ký owner P0. Không merge PR theo task.txt. EX01 dữ liệu thật còn blockers quyền/options/keys/groups/media; seed importable:false. Phase3 physical acceptance và FULL/DELTA policy giữ nguyên.
