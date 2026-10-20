# EX-02 — đề xuất hợp đồng rich exam để A/B chốt

Tiếp nối ngày19/10/2026: [contract hiện hành](EX02_RICH_EXAM_CONTRACT_V1.md), [ADR-V2-03](plan-v2/ADR-V2-03_SESSION_START_DEADLINE.md), [decision register](plan-v2/EX02_DECISIONS_ROADMAP.md) đã TEAM_APPROVED_AS_REPORTED_BY_PROJECT_REPRESENTATIVE, gồm D10 Part1 Visual Readiness Option A. [Nguồn](plan-v2/EX02_TEAM_APPROVAL_20261019.md): “User-provided confirmation from the project team representative, 2026-10-19.” Phần bên dưới là proposal lịch sử trước xác nhận, không override ngoại lệ Part1/Audio+Visual READY và không phải API đã triển khai; không GitHub Approving Review/chữ ký riêng.

Ngày19/10/2026; đối chiếu base `f98a37f73d8035e9386e930412a02e9e92933015`. **ĐỀ XUẤT, CHƯA DUYỆT, CHƯA TRIỂN KHAI DTO/ENDPOINT.** Không đổi protocol WebSocket v0, QD-21, audio READY/no-replay hoặc client trong task kiểm chứng này. [Báo cáo PostgreSQL và findings](EX04_V11_VERIFICATION.md) phân biệt source, kiểm thử thật và việc chưa có.

## 1. Contract gap matrix

| Component | Existing implementation | Missing contract | Priority | Recommended change | Owner | Dependency |
|---|---|---|---|---|---|---|
| Schema | V11 sources/groups/assets/ordered blocks; V12 trong PR này chỉ đóng NULL discriminator | Mapping import/read và readiness của đề | P0 | Giữ V1–V11; chốt mapper/validator trước importer | A, C review | BASE-04, EX-02 |
| Import v1 | ExamImportRequest/ExamQuestionImportDto phẳng; transaction; group tối thiểu | Sources/media/blocks và profile200Q | P0 | HTTP rich contract riêng; giữ payload v1 để tương thích | A, B review | EX-01/02 |
| Source provenance | source_id/source_question_number/status/checksum có trong DB | Local sourceRef → numeric ID; bằng chứng xác minh options candidate-facing | P0 | Map ID trong transaction; manifest nguồn có checksum và review nội dung; không coi USER_CONFIRMED là giấy phép mới | A/B | EX-01, ADR-V2-02 |
| Seven-Part validation | Unique order, section/part, correct option; ít nhất2 options | Profile6/25/39/30/30/16/54, group size, media bắt buộc, câu/option gốc | P0 | Validator rich fail-closed, không ép v1 thành200Q | A/B | EX-02/03 |
| Candidate read | CandidateQuestionDto không correctOption; passageText/audioFile phẳng | Ordered group/question blocks và media descriptors | P0 | Candidate DTO riêng, allowlist, không serialize import/source/scoring objects | A, B | EX-02/05 |
| Shared audio | Audio manifest hiện đọc audio_file từ câu Listening | Bridge group audio → legacy audioFile/playlist | P0 | Chốt ánh xạ cùng file cho các câu cùng group; không tạo đường phát song song | A/B | EX-02/03, LT regression |
| Image delivery | Chưa image root/controller | Root, limits, MIME/decoder, auth/cache/download semantics | P0 | Image endpoint scoped theo attempt, kiểm quyền trước byte/ETag; giữ audio endpoint | A, B | EX-04/05 |
| Media readiness | V11 lưu REMOTE_REFERENCE hoặc LOCAL_VERIFIED metadata | File thật, MIME/size/hash/path và điều kiện được tạo ca | P0 | Chỉ inventory cho remote; production rich import phải kiểm local assets trước commit | A/B | EX-01/03/05 |
| Catalog | Chưa GET catalog; nhập examId thủ công | Metadata-only catalog và selectable/import-ready policy | P0 | RBAC proctor, không keys/blocks/provenance private; không suy ready từ total_questions | A/B | EX-02/07 |
| START/deadline | Attempt ACTIVE có thể nằm trong session SCHEDULED; QD21 takeover/save-first | Quyền đọc đề/lưu/nộp trước START; mốc deadline | P0 | ADR riêng, kiểm tất cả entry/retry/timeout; chưa đổi semantics | A/C, B review | BASE-04, EX-07 |
| Rendering | JavaFX text/group/audio hiện hữu | Part1 photo, Part6/7 nhiều document và async images | P0 | View dùng groupId/ordered blocks, không restart audio khi đổi câu | B, A review | EX-02/05/06 |
| EX-08 acceptance | Có regression Phase3; seed importable:false | Đề200Q thật/media hợp lệ, quyền/keys/groups xác minh | P0 | Giữ blockers; chỉ nghiệm thu sau PG/HTTP/GUI/media/physical evidence | A/B/C | EX-03/05/06/07 |

## 2. API đề xuất và tương thích

- `POST /api/v2/exams/import`: PROCTOR; nhận rich contract; trả examId/count/status sau COMMIT. **Đường dẫn v2 chỉ là đề xuất HTTP**, không đổi tên/version message WS v0. Giữ `/api/v1/exams/import` và behavior lỗi cũ; duplicate examId v1 hiện trả400 INVALID_INPUT, không âm thầm chuyển409.
- `GET /api/v2/attempts/{attemptId}/exam`: owner candidate hoặc assigned proctor theo policy đã duyệt; candidate response an toàn. Quyết định START gate ở mục6 là prerequisite, không tự kế thừa quyền đọc đề sớm cho rich exam.
- `GET /api/v2/attempts/{attemptId}/images/{mediaId}`: ảnh thuộc đúng đề của attempt, LOCAL_VERIFIED và referenced. Không nhận arbitrary path/source URL từ caller.
- Catalog và session START dành PR EX-07 sau ADR; chưa thêm endpoint chạy thật trong lượt này.
- Import duplicate phải từ chối, không overwrite. Dùng unique exam PK trong transaction để bắt race, map conflict an toàn. Toàn bộ source/group/question/option/media/block insert cùng transaction; lỗi file/validation hoặc COMMIT → không success response và không để exam selectable.

## 3. Java records đề xuất

Các type dưới đây là phác thảo contract; enum và giới hạn cuối cần A/B review. Import và candidate **không dùng chung class**. File không được thêm vào module protocol trong task review này.

```java
// Chỉ request PROCTOR -> server, không log toàn bộ payload.
record RichExamImportRequest(int schemaVersion, String examId, String title,
        String description, String profile, List<SourceImport> sources,
        List<GroupImport> groups, List<MediaImport> media, List<QuestionImport> questions) {}

record SourceImport(String sourceRef, String sourceName, String sourcePath,
        String sourceUrl, String sourceSha256, String manifestSha256,
        String permissionStatus, LocalDate permissionConfirmedOn,
        String permissionNote, String licenseDocument,
        String answerKeyStatus, String groupMappingStatus, String mediaValidationStatus) {}

record QuestionImport(String questionId, int orderIndex, String section, int part,
        String groupId, String sourceRef, Integer sourceQuestionNumber, String prompt,
        List<OptionImport> options, List<BlockImport> blocks, ScoringKeyImport scoring) {}
record ScoringKeyImport(String correctOption, String answerKeyStatus) {}
record OptionImport(String optionId, String optionText, int orderIndex) {}
record GroupImport(String groupId, String section, int part, String title,
        int orderIndex, String mappingStatus, List<BlockImport> blocks) {}
record BlockImport(String blockId, int orderIndex, String type, String text, String mediaId) {}
record MediaImport(String mediaId, String sourceRef, String mediaType,
        String storageStatus, String sourceUrl, String localFile, String mimeType,
        Long sizeBytes, String sha256, Integer widthPx, Integer heightPx,
        Long durationMs, String altText) {}

// Server -> candidate: chỉ có dữ liệu để làm bài; không nguồn/quyền/answer key/explanation.
record RichCandidateExam(String examId, String title, int totalQuestions,
        List<CandidateGroup> groups, List<CandidateRichQuestion> questions,
        List<CandidateMedia> media) {}
record CandidateGroup(String groupId, String section, int part, int orderIndex,
        List<CandidateBlock> blocks) {}
record CandidateRichQuestion(String questionId, int orderIndex, String section,
        int part, String groupId, String prompt, List<CandidateChoice> options,
        List<CandidateBlock> blocks) {}
record CandidateChoice(String optionId, String optionText, int orderIndex) {}
record CandidateBlock(String blockId, int orderIndex, String type, String text, String mediaId) {}
record CandidateMedia(String mediaId, String mediaType, String mimeType,
        long sizeBytes, String sha256, String downloadUrl,
        Integer widthPx, Integer heightPx, Long durationMs, String altText) {}
```

`sourceRef` là key cục bộ trong payload; mapper tạo exam_sources và lấy ID bằng RETURNING, không cho payload chỉ định numeric ID của exam khác. `block.media_type` ở DB **do server suy ra từ type**, không nhận một discriminator nullable thứ hai từ client. Owner block được suy từ list question/group chứa nó; không cho caller điền đồng thời hai owner.

V11 không có cột xác minh riêng nguồn option candidate-facing. Cần A/B chốt cách ghi nhận chứng cứ này trong manifest được pin bằng source.manifest_sha256, hoặc migration append-only nếu cần truy vấn trạng thái có cấu trúc. Không nhét trạng thái mới vào enum hiện hành hoặc xem text review/translation là option gốc.

## 4. Mapping và fail-closed

1. Header → exams. Không publish đề production nếu input/seed còn importable:false hoặc verification blockers.
2. Sources → exam_sources; map sourceRef sang ID cùng exam. Question sourceRef/number cùng có hoặc cùng null; rich TOEIC200 cần provenance đủ theo policy được duyệt.
3. Groups → exam_question_groups trước questions. ID duy nhất; section/part phải khớp mọi member; group order là min question order, không phụ thuộc thứ tự mảng JSON.
4. Questions/options → exam_questions/exam_options; key chỉ ghi vào correct_option. Profile TOEIC200 yêu cầu200 câu, số/thứ tự1–200,100 Listening/100 Reading, đúng phân bố7Part; không tự thêm câu/đáp án.
5. Media → exam_media_assets; type đúng, references cùng exam, relative path/MIME/size/SHA đúng file thật; chỉ LOCAL_VERIFIED được phát hành. REMOTE_REFERENCE vẫn dùng trong inventory offline, không được “nâng trạng thái” bằng DTO.
6. Blocks → exam_content_blocks. TEXT plain Unicode, không markup/script hoặc explanation; IMAGE/AUDIO tham chiếu asset cùng loại; một owner; order dương/duy nhất trong owner. blockId duy nhất trong exam.
7. Candidate mapper chọn cột/DTO tường minh. Không trả scoring/source/permission/licensing notes, local filesystem path, source URLs hoặc nội dung giải thích. Có key đúng ở DB không chứng minh nội dung đã xác minh.

Phải từ chối ID quá giới hạn DB, null/blank/unknown enum, duplicate IDs/orders, missing/foreign references, wrong correct option, nguồn/hash sai, file thiếu/corrupt, MIME giả, unsupported block và group mismatch. Không fallback về text trống hoặc mặc định “A”. Mảng phải có thứ tự xác định; SQL đọc luôn ORDER BY, không dựa insertion/PK.

| Part | Hợp đồng nội dung cần chốt |
|---|---|
|1|Question blocks IMAGE rồi AUDIO theo source; A/B/C/D có thể chỉ là nhãn âm thanh, không chế transcript|
|2|Question AUDIO,3 nhãn A/B/C; thiếu transcript không phải lý do tự tạo option text|
|3|Group3 câu, một conversation audio dùng chung; ảnh nếu nguồn có; cùng groupId/audio mapping|
|4|Group3 câu, một talk audio dùng chung; optional figure|
|5|Standalone prompt với4 lựa chọn candidate-facing|
|6|Group4 câu; passage/text/ảnh theo thứ tự nguồn, không gom mọi text trước mọi ảnh|
|7|Group của một/nhiều document; ordered blocks giữ thứ tự và ranh giới document; group cardinality theo source xác minh|

V11 hiện có ordered flat blocks và title group, **chưa có documentId/container** để giữ ranh giới hai tài liệu cùng loại khi nguồn cần layout riêng. A/B phải quyết định ranh giới được biểu diễn bằng block TEXT/IMAGE tự đủ hay cần thêm document metadata; không tự thêm bảng mới trong task review.

### Ví dụ rút gọn candidate: Part1/3/6/7

MOCK tự tạo để trao đổi hình dạng DTO, không phải payload200Q hợp lệ hoặc media đã kiểm. Các câu còn lại và phần lớn options cố ý lược bớt; không dùng ví dụ này làm seed production.

```json
{
  "examId": "MOCK-contract", "title": "MOCK", "totalQuestions": 200,
  "groups": [
    {"groupId":"G3","section":"LISTENING","part":3,"orderIndex":32,
     "blocks":[{"blockId":"g3-audio","orderIndex":1,"type":"AUDIO","mediaId":"conversation-1"}]},
    {"groupId":"G6","section":"READING","part":6,"orderIndex":131,
     "blocks":[{"blockId":"g6-passage","orderIndex":1,"type":"TEXT","text":"MOCK passage with blanks"}]},
    {"groupId":"G7","section":"READING","part":7,"orderIndex":176,
     "blocks":[{"blockId":"doc1","orderIndex":1,"type":"TEXT","text":"MOCK email"},
               {"blockId":"doc2","orderIndex":2,"type":"IMAGE","mediaId":"notice-1"},
               {"blockId":"doc3","orderIndex":3,"type":"TEXT","text":"MOCK reply"}]}
  ],
  "questions": [
    {"questionId":"Q1","orderIndex":1,"section":"LISTENING","part":1,"groupId":null,
     "prompt":"MOCK Question 1","options":[{"optionId":"A","optionText":"A","orderIndex":1}],
     "blocks":[{"blockId":"q1-photo","orderIndex":1,"type":"IMAGE","mediaId":"photo-1"},
               {"blockId":"q1-audio","orderIndex":2,"type":"AUDIO","mediaId":"question-1"}]},
    {"questionId":"Q32","orderIndex":32,"section":"LISTENING","part":3,"groupId":"G3","prompt":"MOCK question","options":[],"blocks":[]},
    {"questionId":"Q131","orderIndex":131,"section":"READING","part":6,"groupId":"G6","prompt":"MOCK blank","options":[],"blocks":[]},
    {"questionId":"Q176","orderIndex":176,"section":"READING","part":7,"groupId":"G7","prompt":"MOCK question","options":[],"blocks":[]}
  ],
  "media": [
    {"mediaId":"photo-1","mediaType":"IMAGE","mimeType":"image/png","sizeBytes":123,
     "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
     "downloadUrl":"/api/v2/attempts/MOCK-A/images/photo-1","widthPx":640,"heightPx":480,"altText":"MOCK source alt"},
    {"mediaId":"conversation-1","mediaType":"AUDIO","mimeType":"audio/wav","sizeBytes":123,
     "sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
     "downloadUrl":"/api/v1/attempts/MOCK-A/audio/files/mock-group.wav?manifestId=cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"}
  ]
}
```

Import dùng cùng bố cục groups/media/blocks, bổ sung sources và `scoring:{correctOption,answerKeyStatus}` riêng ở mỗi QuestionImport. Ví dụ chỉ cho endpoint PROCTOR: `"scoring":{"correctOption":"A","answerKeyStatus":"VERIFIED"}` cho một câu MOCK tự tạo. Candidate record/JSON phía trên không có trường đó. Không suy key TOEIC từ nhãn ví dụ.

## 5. Image/media contract cần A/B duyệt

- Đề xuất cấu hình `TOEIC_IMAGE_ROOT`, mặc định/operator directory riêng; chưa có trong application.properties. Binary ngoài DB/JAR. Không đưa third-party remote downloader vào request import hoặc candidate load.
- Root và file resolve canonical/real path, kiểm symlink/junction/8.3/case/Unicode theo nền audio đã có. Metadata chỉ relative filename/path được cho phép; reject absolute, drive/UNC, traversal, separators bất hợp lệ, null byte và escape sau resolve. Không trả đường dẫn local trong lỗi/DTO.
- Allowlist PNG/JPEG sau kiểm magic/decoder; chốt max bytes/pixels theo measured GUI/memory trước triển khai. Không chấp nhận SVG/HTML/executable bằng MIME tự khai. size>0, actual bytes/hash khớp; fail-closed nếu file đổi/mất trong lúc đọc.
- Kiểm Bearer/role/assignment/attempt/exam/reference **trước** lookup cache/ETag/304/bytes. Candidate khác403; chưa login401. Không suy quyền từ mediaId hoặc file tồn tại. Hạn chế response/cache/error không lộ file của exam khác.
- Tải bằng mediaId đã resolve từ DB, không query arbitrary path/URL. Header Content-Type chuẩn, nosniff, size/hash từ byte đã kiểm. Mặc định no-store tương tự audio; nếu sau này dùng ETag vẫn phải auth trước304 và test revoked access.
- Audio giữ `/api/v1/attempts/{id}/audio/manifest`, `/audio/files/{fileName}?manifestId=...`, READY và Listening START/no-replay. Đường bridge group audio phải đưa cùng tài nguyên vào manifest hiện hành; URL nguồn không được client phát trực tiếp. Metadata rich không tự cấp quyền phát audio.

## 6. START/deadline: chỉ đề xuất, chưa đổi QD-21

Hiện `getCandidateExam` kiểm owner/assigned/readable attempt; `autosaveAnswers`, `submitExam`, `takeoverWriter` khóa attempt và kiểm ACTIVE/epoch/deadline nhưng không bắt sessionIN_PROGRESS. Deadline khởi tạo tại takeover, hoặc legacy save-first, bằng DB clock sau khóa; GET đề/status không bắt đầu giờ. Listening start riêng mới chuyển session sang IN_PROGRESS. Test HTTP trong report ghi nhận hiện trạng SCHEDULED vẫn đọc/lưu/nộp được; đây là gap EX-07, không được sửa ngầm.

Đề xuất để A/C quyết định (B review ảnh hưởng client):

1. SCHEDULED: ACTIVE dùng monitoring/audio manifest/download/READY; candidate không nhận nội dung đề, không save/submit. Cần xác định rõ metadata/status được phép và proctor preview. Các API v1 cũng phải xử lý exam thuộc ca V2, tránh bypass bằng endpoint cũ.
2. START: proctor assigned, state/READY được server kiểm lại trong transaction; `startedAt` lấy DB clock; ACK/WS sau COMMIT. Chốt START toàn bài dùng chung hay tách Listening start; chính sách ca không có audio cũng phải có đường start.
3. Clock: chỉ proposal cho **ca V2 mới chưa có deadline** tính `startedAt + duration`. Không tính lại hoặc kéo dài bất kỳ deadline cũ nào. Cần ADR/version marker để phân biệt ca legacy; không dùng “deadline null” làm marker version. Những ca đã có deadline phải theo policy chuyển tiếp được phê duyệt hoặc cấp ca mới.
4. Takeover trước START: nếu được phép chuẩn bị writer/READY thì không bắt đầu clock mới theo policy V2; sau START không reset clock. Giữ writerEpoch/revision và Listening locked/no-auto-resume.
5. Lấy khóa theo thứ tự session→attempt nhất quán với audio start và mọi save/submit/takeover/timeout để tránh deadlock/race. Auth trước cache; recheck state/deadline sau lock. Duplicate start sau response mất phải trả authority cũ, không start/replay lại.
6. Quyết định retry của request đã commit trước khi ca đóng và quyền đọc bài đã chốt riêng; không áp gate hiện tại theo kiểu làm mất idempotency hoặc khóa xem kết quả. Unknown session/exam association fail-closed.

Acceptance cho ADR/EX-07: before/after START trên questions/save/submit/takeover cảv1/v2; READY còn dùng trước START; unauthorized/revoked; retry/start race; takeover/deadline cũ không tăng; DB failure không ACK/WS; restart/reconnect không replay. **Không endpoint/policy nào ở phần này đã được triển khai bởi PR kiểm chứng.**

## 7. Roadmap PR tiếp theo

1. **PR EX-02**: A/B chốt records/schema/profile/option provenance/document boundary/audio bridge/error codes, C review consistency; contract fixtures Part1/3/6/7 và candidate allowlist tests. Đây là dependency trước code xuyên module.
2. Sau hợp đồng có thể chia ba PR: **EX-03** offline Java parser/validator/atomic importer + PG roundtrip trên nguồn đủ điều kiện; **EX-05** image root/auth/filesystem/negative HTTP; **EX-07** catalog + START/deadline chỉ sau ADR/BASE-04 và BASE-02 review. EX-03/05 cần EX-04 đã review V11/V12; không tự bỏ dependency chỉ vì có file SQL.
3. **EX-06** B render JavaFX ordered groups/media sau EX-02/05; có thể làm fixture view cô lập song song EX-03/07 nhưng chưa gọi integrated PASS. Không quét/tải file trên FX thread, không mất answers/restart audio khi navigation.
4. **EX-08** sau EX-03/05/06/07: một đề200Q có quyền/key/options/group/media đã xác minh → import/catalog/assignment/READY/START/navigate/autosave/submit/server score; real PG/HTTP/WS/packaged UI, và bằng chứng manual/hardware riêng.

Không thay EX-01/02/04/08 thành DONE, không đổi tracker/hours, không cấp human sign-off trong proposal này.
