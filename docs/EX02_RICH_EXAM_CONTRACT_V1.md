# EX-02 — Rich Exam Contract v1, bản ứng viên

Ngày 19/10/2026. **CANDIDATE / PROPOSED_FOR_OWNER_REVIEW**, chưa triển khai, chưa được A/B/C ký duyệt. `schemaVersion=2` là phiên bản HTTP rich exam; “v1” trong tên tài liệu là lần phát hành bản thiết kế. WebSocket giữ protocol `v0`.

Baseline đã fetch: main `1d35b53729abade7b460d35ab07d042b6f9f3571`, đã có PR65/V11 và PR66/V12; [CI baseline](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39214612088) SUCCESS. Đọc cùng [proposal đầu vào](EX02_RICH_EXAM_CONTRACT_PROPOSAL.md), [bằng chứng EX04](EX04_V11_VERIFICATION.md), [ADR START](plan-v2/ADR-V2-03_SESSION_START_DEADLINE.md), [quyết định và roadmap](plan-v2/EX02_DECISIONS_ROADMAP.md). Đây là khuyến nghị cụ thể để owner duyệt, không thay QD-21 hiện hành.

## 1. Bằng chứng source và lựa chọn mô hình

| Hiện có trên baseline | Lựa chọn đề xuất | Tác động / acceptance |
|---|---|---|
| V11 có sources, groups, assets, blocks; V12 bắt buộc media discriminator | Tái sử dụng bốn bảng; thêm metadata tối thiểu ở migration tiếp theo sau duyệt | Không sửa V1–V12; kiểm upgrade/fresh trên PG thật ở EX03 |
| `ExamService.importExam` và DTO v1 phẳng, cho phép đề ngắn | DTO rich riêng, profile `TOEIC_200`; giữ nguyên v1 | Legacy HTTP regression, không ép đề ngắn đủ 200 |
| Blocks không có document identity | Part6/7 dùng document lồng blocks; lưu document metadata trên blocks | Fixture Email → Notice + image → Reply phải render thành ba vùng riêng |
| `AudioCatalog.load` distinct/sort tên file; `AudioService.names` đọc audio_file của câu | Một audio/group, lặp cùng filename trên câu thành viên; filename bắt đầu bằng ordinal ba chữ số | Manifest 54 file theo thứ tự câu; không tạo playlist/player thứ hai |
| `ExamService.getCandidateExam` chưa gate session; takeover có thể đặt deadline trước START | Gate theo explicit session version trong ADR | Phải chặn cả route v1, không chỉ v2 |
| JavaFX hiện nhận DTO phẳng qua ExamSession/ExamGateway, chưa có rich lobby/renderer | Lobby metadata trước START, sau START mới fetch rich DTO | Old client dùng ca V2 nhận lỗi, không âm thầm render thiếu ảnh/document |

Part7: phương án chỉ ordered blocks + dòng phân cách ít DDL nhưng không phân biệt xuống đoạn với tài liệu mới, không giữ được identity khi đổi layout. **Chọn documentId/documentOrder/title tường minh**, không dùng TEXT đặc biệt hay suy từ nội dung. Không tạo bảng rich song song. V11/V12 tự nó chưa lưu được boundary này; fixture `g147` minh họa thiếu sót. Group Part6 có đúng một document, Part7 có 1–3; document rỗng bị từ chối. Blocks có order toàn group xuyên qua documents (không reset ở mỗi document), phù hợp unique index V11.

## 2. DTO import — chỉ server/proctor được nhận

Java 21, `java.util.List`, `java.time.LocalDate`. Mỗi record khai báo public trong file riêng khi triển khai; đoạn này là chữ ký thiết kế, không phải source production. Enums JSON dùng đúng tên bên dưới; unknown field/enum, duplicate JSON key, thiếu required field phải bị từ chối, không silently drop. Lists copy bất biến trong constructor ở implementation. Không dùng chung import record cho candidate.

```java
enum Section { LISTENING, READING }
enum BlockType { TEXT, IMAGE, AUDIO }
enum MediaType { IMAGE, AUDIO }
enum Verification { UNVERIFIED, VERIFIED }
enum PermissionStatus { UNKNOWN, USER_CONFIRMED, DOCUMENTED, LICENSED }
enum StorageStatus { REMOTE_REFERENCE, LOCAL_VERIFIED }
enum ValidationStatus { VERIFIED }
record RichExamImportRequest(int schemaVersion, ExamMetadata exam,
        List<SourceImport> sources, List<GroupImport> groups,
        List<MediaImport> media, List<QuestionImport> questions) {}
record ExamMetadata(String examId, String title, String description, String profile) {}
record SourceImport(String sourceRef, String sourceName, String sourcePath,
        String sourceUrl, String copyrightNotice, PermissionStatus permissionStatus,
        LocalDate permissionConfirmedOn, String permissionNote, String licenseDocument,
        String sourceSha256, String manifestSha256, Verification answerKeyStatus,
        Verification groupMappingStatus, Verification optionContentStatus,
        String mediaValidationStatus) {}
record GroupImport(String groupId, Section section, int part, String title,
        int orderIndex, Verification mappingStatus,
        List<DocumentContent> documents, List<ContentBlock> blocks) {}
record DocumentContent(String documentId, int orderIndex, String title,
        List<ContentBlock> blocks) {}
record ContentBlock(String blockId, BlockType type, int orderIndex,
        String text, String mediaId, MediaType mediaType) {}
record MediaImport(String mediaId, MediaType mediaType, String sourceRef,
        String sourceUrl, StorageStatus storageStatus, String localFile,
        String mimeType, long sizeBytes, String sha256, Integer widthPx,
        Integer heightPx, Long durationMs, String altText) {}
record QuestionImport(String questionId, Section section, int part,
        int orderIndex, String groupId, String sourceRef, int sourceQuestionNumber,
        Verification answerKeyStatus, List<ContentBlock> blocks,
        List<OptionContent> options, ScoringKey scoring) {}
record OptionContent(String optionId, int orderIndex, String text) {}
record ScoringKey(String correctOption) {}
record RichExamImportResponse(int schemaVersion, String examId, String importStatus,
        ValidationStatus contentValidation, String mediaVerification,
        int totalQuestions, int totalGroups, int totalMedia) {}
```

`mediaValidationStatus` chỉ `NOT_RUN|PARTIAL|VERIFIED`, theo V11; rich import chỉ nhận VERIFIED. `profile` chỉ `TOEIC_200`. `sourceRef` chỉ có trong request, scope theo đề, server map sang BIGINT; không cho caller tự đặt database source ID. `ScoringKey` không có trọng số riêng: mỗi câu đúng = 1, giữ công thức score hiện hành (correctCount; không suy đây là điểm TOEIC chuẩn hóa). Không có explanation/transcript sinh tự động.

Required: mọi field ngoài các nullable sau phải non-null: exam.description; source.sourcePath/sourceUrl (ít nhất một nonblank), copyrightNotice/permissionNote/licenseDocument/manifestSha256; group.title; document.title; block.text/mediaId/mediaType tùy discriminator; question.groupId; media.sourceUrl/widthPx/heightPx/durationMs/altText; option.text tùy Part. Source permission DOCUMENTED/LICENSED cần licenseDocument nonblank; USER_CONFIRMED cần permissionNote và ngày xác nhận, không tự đổi thành LICENSED. sourcePath chỉ là locator tương đối của provenance, **không đọc file tùy ý từ field này**. Không có token/query secret trong sourceUrl; chỉ URL http(s) không userinfo, không fetch URL.

Mọi ID theo `[A-Za-z0-9][A-Za-z0-9_-]{0,127}`, ngoại trừ documentId scope group; blockId/mediaId/groupId/questionId unique toàn đề. Strings trim trước so sánh; ID có whitespace bị reject thay vì sửa. Title 1–255, description ≤4000; text block ≤16000, option.text ≤2000, altText ≤1000; URL/sourcePath ≤1024, sourceName ≤255, note/license/copyright ≤4000. JSON UTF-8 ≤2 MiB, sources 1–16, media ≤200, blocks tổng ≤2000, depth ≤12. Hash lowercase 64 hex. Primitive numeric thiếu/null phải bị validator nhận diện **trước** bind default 0; order strictly positive, không normalize như v1. Không nhận HTML/Markdown executable: text hiển thị literal JavaFX Text/Label, không WebView.

## 3. Quy tắc 7 Part và thứ tự

Questions luôn 200, orderIndex chính xác 1..200, ID ổn định không phụ thuộc array position; mảng được gửi tăng order. Counts Part1..7 = **6/25/39/30/30/16/54**, Listening 1..100, Reading 101..200. Option order 1..N, optionId đúng A/B/C/D (Part2 A/B/C); key phải thuộc options. Part1/2 `text=null` được phép cho lựa chọn chỉ nhãn, không bịa transcript. Các Part khác text nonblank. Câu nào cũng có ≥1 TEXT block cho prompt/hướng dẫn được phép dùng.

| Part | Group bắt buộc / số câu | Shared content | Question content / media |
|---|---|---|---|
| 1 | Một group/câu, 6 group | Đúng 1 AUDIO | Đúng 1 IMAGE + prompt; 4 nhãn |
| 2 | Một group/câu, 25 group | Đúng 1 AUDIO, không transcript bắt buộc | Prompt; 3 nhãn |
| 3 | 13 group × 3 câu | Đúng 1 AUDIO; TEXT/IMAGE tùy chọn | 4 options có text |
| 4 | 10 group × 3 câu | Đúng 1 AUDIO; IMAGE/TEXT tùy chọn | 4 options có text |
| 5 | groupId null | Không shared content | TEXT, 4 options; không AUDIO |
| 6 | 4 group × 4 câu | 1 document có TEXT, IMAGE tùy chọn | TEXT vị trí blank, 4 options; không AUDIO |
| 7 | Mỗi group 1–5 câu, tổng 54 | 1–3 documents, mỗi document có TEXT, IMAGE tùy chọn | Prompt + 4 options; không AUDIO |

Group không rỗng, cùng section/part, câu thành viên liên tiếp; `group.orderIndex = min(question.orderIndex)` để đúng thứ tự render/playlist. Mọi group/media/source phải được tham chiếu; không orphan. Listening groups có `documents=[]`, Part6/7 có `blocks=[]`. Mỗi block đúng một owner suy từ vị trí lồng: question, group hoặc document (document vẫn thuộc group); không nhận field owner kép. Block orders 1..N theo owner; ở document tổng group là 1..N, ranges document liên tiếp, không interleave. Document order 1..N; documentId unique trong group. TEXT: text nonblank, mediaId/mediaType null; IMAGE/AUDIO: text null, mediaId tồn tại cùng đề, mediaType=type=asset.mediaType và không null. Mọi AUDIO chỉ nằm ở group Listening, một asset không chia sẻ cho hai group; group dùng cùng một file cho toàn bộ câu thành viên. Ảnh được reuse trong cùng đề.

## 4. HTTP và import transaction

| Method / route đề xuất | Auth / response | Status |
|---|---|---|
| POST `/api/v2/exams/import` | PROCTOR, `RichExamImportRequest` → `RichExamImportResponse` | 201 sau commit; duplicate 409 CONFLICT |
| GET `/api/v2/attempts/{attemptId}/exam` | Owner CANDIDATE, gate ADR → `RichCandidateExam` | 200; SCHEDULED/CLOSED/final 409 INVALID_STATE |
| GET `/api/v2/attempts/{attemptId}/images/{mediaId}` | Owner CANDIDATE, cùng gate | 200 bytes; lỗi mục7 |
| GET `/api/v2/exams?limit=20&afterExamId=...` | Bất kỳ PROCTOR đã auth; metadata-only | 200 `ExamCatalogPage` |
| GET `/api/v2/attempts/{attemptId}/preparation` | Owner CANDIDATE; SCHEDULED/IN_PROGRESS và ACTIVE | 200 `AttemptPreparation`; không câu hỏi |
| POST `/api/v2/sessions` | PROCTOR tạo ca và tự được phân công; payload mục6 | 201; media/content chưa đủ 409 INVALID_STATE |

Không thêm API preview câu hỏi cho proctor trong v2: giám thị chọn đề bằng metadata; candidate endpoint không cho PROCTOR kể cả được phân công. Không có public examId read. `limit` 1..100 mặc định20, cursor examId opaque hợp lệ, keyset `exam_id COLLATE "C" > cursor`, ascending; unknown query400. Trả cả imported legacy/unverified trong catalog để phân biệt; UI disable lựa chọn khi `selectable=false`.

GET v2 exam/preparation/images chỉ phục vụ ca PROCTORED_V2 có content_version2; owned legacy attempt trả409 INVALID_STATE sau auth/scope, client dùng v1 như cũ. Không chuyển đổi legacy DTO sang rich200 tự động. Strict unknown-field/enum policy ở mục2 chỉ áp dụng v2; parsing v1 giữ nguyên baseline, không âm thầm thêm required field hoặc đổi error code.

Auth/session token hợp lệ kiểm trước role/scope, trước lookup cache hoặc filesystem. Unknown/foreign attempt hoặc media không thuộc exam/không referenced trả403 FORBIDDEN đồng nhất, không lộ tồn tại. 401 UNAUTHORIZED token thiếu/hết hạn/revoked. Role sai403. ID/request malformed400 INVALID_INPUT sau auth. 413 INVALID_INPUT quá body; lỗi nội dung/enum/order/ref/key400 INVALID_INPUT; missing/hash/decode file trong import409 INVALID_STATE; duplicate409 CONFLICT; transient DB/IO503 RETRYABLE_SERVER_ERROR (`retryable=true`). File path không được xuất trong error. Dùng nguyên `ApiErrorResponse` v0, `error.code` hiện có, không thêm enum wire trong task này:

```json
{"protocolVersion":"v0","type":"ERROR","requestId":null,"traceId":"server-generated","error":{"code":"INVALID_STATE","message":"Nội dung chưa được phép truy cập","retryable":false}}
```

Import là **create-only**, không overwrite, không idempotent retry200; mất response201 → proctor kiểm catalog rồi quyết định, duplicate cùng payload vẫn409. Không import seed chưa xác minh vào runtime: thiếu key/options/groups/permission/media verification →400; file thiếu/corrupt →409; không tự điền A, tải remote, đổi flag hoặc tạo draft giả imported. Seed hiện hành `importable:false` giữ nguyên. Inventory remote ở EX01, không qua rich importer.

Thứ tự thực hiện EX03 sau owner review:

1. Authenticate PROCTOR; giới hạn body, strict parse; validate graph, profile, required fields, uniqueness, keys, trạng thái provenance. Verify local media theo mục7/8 ngoài transaction để hạn chế khóa; server tự đo/hash, không tin `LOCAL_VERIFIED` caller. Không write/copy/download filesystem.
2. Bắt transaction; insert `exams` (unique PK xử lý race thành409); content_version=2. Insert sources theo request, `RETURNING id`, map sourceRef→id chỉ trong transaction. Không persist sourceRef thành FK text; source checksum duplicate reject.
3. Insert groups; insert media mapped source_id; insert questions mapped group/source, legacy audio_file bridge và prompt/plain fallback; insert options; insert blocks cùng document metadata. FK correct_option deferred đến COMMIT; không disable constraint. Mọi SQL có exam scope, không global-ID lookup.
4. Kiểm lại metadata/file identity/size/hash dưới transaction trước commit, kiểm graph/query counts/readiness từ DB, `SET CONSTRAINTS ALL IMMEDIATE` để bắt deferred FK; bất kỳ lỗi rollback **toàn bộ DB**. File bất biến do operator xuất bản trong root read-only trước import; transaction DB không thể rollback filesystem. Không tuyên bố atomic giữa disk/DB. File đổi sau commit bị chặn lúc tạo ca/START/download, cần sửa file theo đúng hash hoặc import đề mới, không sửa đề đang dùng.
5. Response201 chỉ sau commit; lỗi commit không success. Cạnh tranh cùng examId chỉ một writer thắng. Test PG latch cho race và lỗi giữa options/blocks; không fixture MOCK để gọi production data PASS.

## 5. Candidate DTO allowlist và render

Java `Instant` ISO-8601 UTC; records ở mục2 `ContentBlock`, `DocumentContent`, `OptionContent` chỉ chứa display data nên được tái dùng. **Không** tái dùng SourceImport/MediaImport/QuestionImport/ScoringKey.

```java
record RichCandidateExam(int schemaVersion, String attemptId, String sessionId,
        String examId, String title, String description, String profile,
        String contractVersion, Instant startedAt, Instant deadlineAt,
        List<CandidateGroup> groups, List<CandidateQuestion> questions,
        List<CandidateMedia> media) {}
record CandidateGroup(String groupId, Section section, int part, String title,
        int orderIndex, List<DocumentContent> documents, List<ContentBlock> blocks) {}
record CandidateQuestion(String questionId, Section section, int part, int orderIndex,
        String groupId, List<ContentBlock> blocks, List<OptionContent> options) {}
record CandidateMedia(String mediaId, MediaType mediaType, String mimeType,
        long sizeBytes, String sha256, Integer widthPx, Integer heightPx,
        Long durationMs, String altText, String imagePath, String audioFileName) {}
record AttemptPreparation(int schemaVersion, String attemptId, String sessionId,
        String examId, String title, String contractVersion, String sessionState,
        String attemptState, int durationMinutes, Instant startedAt, Instant deadlineAt,
        boolean listeningRequired, String audioManifestPath) {}
```

`CandidateMedia`: IMAGE có relative API imagePath đúng attempt+mediaId, audioFileName null; AUDIO có audioFileName là basename để join manifest hiện có, imagePath null; không phải localFile/path/URL nguồn. Image width/height bắt buộc >0, durationMs null; audio dimensions null, durationMs có thể null nếu codec chưa đo được. `startedAt/deadlineAt` trong RichCandidateExam non-null vì chỉ ACTIVE+IN_PROGRESS trước deadline mới được đọc; preparation trước START null cả hai. Không trả writerEpoch ở preparation; writer lấy takeover hiện có.

Renderer: sort group/question/block/document theo order, render shared group một lần trước câu; question selection giữ cùng document pane, không seek/restart audio. Part1 ảnh + nhãn; Part2 nhãn không transcript; Part3 một audio chung ba câu; Part4 ảnh trong shared pane; Part6 một passage với bốn câu; Part7 ba document panes (fixture g147), trong Notice TEXT rồi IMAGE, Reply vùng thứ ba. Image tải/decode trên bounded worker, timeout/cancel khi đóng view; update scene trên FX thread. Network/file/hash/ProcessHandle không trên FX Application Thread.

Projection phải select explicit columns; không serialize entity rồi remove key. Recursively assert allowlist và cấm `correctOption`, `scoring`, `answerKeyStatus`, explanations, sourceRef/sourceId/sourceQuestionNumber/sourceUrl/sourcePath/license/permission, localFile, credential/token. Cấm cả key alias và đáp án chèn trong altText/prompt từ mapper; nội dung do EX01 người review kiểm, máy không chứng minh semantic absence hoàn toàn. Không gửi options.text chứa lời giải.

JSON đầy đủ có trong [import MOCK200](plan-v2/fixtures/ex02/import.mock.json), [candidate projection](plan-v2/fixtures/ex02/candidate.mock.json), [preparation](plan-v2/fixtures/ex02/preparation.mock.json), [catalog](plan-v2/fixtures/ex02/catalog.mock.json). Đây là examples theo schema, không HTTP capture runtime. Các keys MOCK tự soạn chỉ nằm ở import, không phải answer key bên thứ ba.

## 6. Catalog và tạo ca

```java
record ExamCatalogPage(int schemaVersion, List<ExamCatalogItem> items, String nextAfterExamId) {}
record ExamCatalogItem(String examId, String title, int contentVersion,
        String profile, int totalQuestions, String importStatus,
        String contentValidation, String mediaVerification,
        boolean selectable, boolean requiresMediaRecheck, List<String> blockedReasons) {}
record CreateRichSessionRequest(String examId, int durationMinutes,
        List<Long> candidateUserIds, List<Long> proctorUserIds) {}
record CreateRichSessionResponse(String sessionId, String examId, String contractVersion,
        String state, List<CreatedAttempt> attempts) {}
record CreatedAttempt(long candidateUserId, String attemptId) {}
```

`importStatus=IMPORTED` chỉ nghĩa row đã commit. `contentValidation=VERIFIED` iff content_version=2 và toàn sources/question keys/group mapping/options verified, permission đủ evidence như mục2, graph đúng profile; còn lại UNVERIFIED. `mediaVerification=VERIFIED_AT_IMPORT` iff mỗi asset referenced LOCAL_VERIFIED+metadata đầy đủ và nguồnmedia VERIFIED, ngược lại UNVERIFIED. **Không có trạng thái client READY trong catalog.** `selectable` đúng iff VERIFIED + VERIFIED_AT_IMPORT; `requiresMediaRecheck=true` luôn. blockedReasons ordered subset `LEGACY_CONTENT, CONTENT_UNVERIFIED, MEDIA_UNVERIFIED`; legacy profile=null, selectable=false. Không kiểm filesystem từng item catalog: byte bị mất sau import có thể chưa phản ánh trên list, server **bắt buộc kiểm lại khi tạo ca**, rồi manifest/READY/START/download; UI ghi “đã kiểm khi nhập; sẽ kiểm lại khi tạo ca”. Không suy selectability từ 200 câu.

Create request: durationMinutes 1..240 integer, candidates 1..100 unique enabled CANDIDATE, proctors 1..20 unique enabled PROCTOR gồm caller; không cấp role theo request. Server đặt session.title=exam.title và duration_seconds=durationMinutes×60 để khớp V5. 201 response: `contractVersion=PROCTORED_V2`, state=SCHEDULED, attempts ACTIVE/writer=1 theo default hiện hành/deadline=null; client vẫn phải takeover lấy writer mới trước READY. Cả ca/roster/proctors trong một transaction, cấp UUID bằng server; không requestId trong create nên retry không tự dedup, UI đọc ca trước khi lặp. EX07 phải reuse validation/transaction v1, giữ v1 response200 hiện có cho legacy. Chỉ profile có Listening được tạo V2, reading-only rich bị400 INVALID_INPUT. Route v1 create từ content_version2 phải409 INVALID_STATE để tránh bypass. JSON create request/response ở thư mục fixtures; start request/response giữ record hiện có, manifest placeholder MOCK phải được thay bằng runtime ID.

## 7. Authenticated image delivery

Chọn cấu hình mới `TOEIC_IMAGE_ROOT` ngoài JAR, không dùng static resource public, không nhận root từ request. Root/examId/localFile, localFile một basename ASCII1..255 `[A-Za-z0-9][A-Za-z0-9._-]*`, không `..`, colon/slash/backslash/control/trailing dot hoặc space, không Windows reserved device names (CON/PRN/AUX/NUL/COM1..9/LPT1..9 kể cả extension), chỉ .png/.jpg/.jpeg. Áp dụng basename safety tương tự cho audio; examId an toàn mục2. Source path không quyết định disk path.

1. Token→role→attempt owner/exam binding→ADR state/deadline gate→media same-exam/reference/type/status; **trước filesystem/cache/header**. Proctor không có route này. Không so sánh checksum trả về cho người không quyền.
2. Cấu hình root resolve real path lúc start; reject symlink/reparse points cho exam directory/file và mọi segment dưới trusted root; `toRealPath` và check containment theo filesystem Windows, không chỉ string prefix. Reject cả link nằm trong root để policy thống nhất. EX05 phải kiểm junction và Windows reparse attributes thực, không gọi test symlink SKIP là PASS. Không follow khi mở; verify identity/real path trước/sau read; root do operator kiểm soát read-only, chống TOCTOU bằng handle/identity và reject khi thay đổi. Không giả định NOFOLLOW_LINKS một mình chặn mọi junction.
3. Allow PNG/JPEG (image/png,image/jpeg), extension+magic bytes+decoder phải khớp; không SVG/GIF/HTML, không URL redirect/fetch. 1..8 MiB/file, tối đa100 image/đề, tổng64 MiB ảnh/đề; dimension mỗi chiều1..8192 và tổng ≤16,000,000 pixels. Đọc dimensions trước full decode; reject decode error/truncated file/oversized pixel bomb; hash bytes thực bằng SHA256 so metadata, size phải khớp; không chỉ tin MIME từ importer. Limits là lựa chọn thiết kế chờ B review với dataset thật, chưa có đo performance.
4. Success200 bytes, exact Content-Type/Length, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `X-Image-SHA256`. **Chưa ETag, chưa304/Range/HEAD optimization**. If-None-Match/If-Modified-Since vẫn auth/state rồi200 hoặc lỗi, không304; Range được bỏ qua và trả full200 sau auth. Cache ảnh client chỉ RAM theo attempt+mediaId+hash, xóa khi logout/đóng bài, không public disk cache.
5. Authorized reference nhưng file missing/corrupt/hash mismatch/unsafe configured path →409 INVALID_STATE, không bytes; IO transient503 RETRYABLE_SERVER_ERROR; malformed mediaId400; foreign/unknown media403; auth401. Không fallback remote/placeholders để tuyên bố đủ media. Client khóa thao tác cần ảnh, hiển thị lỗi retry rõ, không làm FX thread treo. Pre-START image409 như đề đầy đủ; audio chuẩn bị được phép theo ADR.

## 8. Audio bridge giữ player hiện hành

Mỗi Listening group có đúng một block AUDIO → mediaId → LOCAL_VERIFIED AUDIO asset → localFile. EX03 ghi cùng localFile vào `exam_questions.audio_file` cho mọi câu trong group, Reading null. Filename bắt đầu **ordinal ba chữ số** theo câu đầu, ví dụ `001-g001.wav`, `032-g032.wav`, `071-g071.wav`. Các tên sau prefix không ảnh hưởng thứ tự; cấm hai file/group/ordinal xung đột. `AudioCatalog` hiện distinct+sort lexicographic: đúng 54 files (6+25+13+10). Prefix có nội dung biến thiên trước ordinal có thể sort sai; không dùng. Group.order=min câu; verify playlist vs manifest trước import.

Giữ `TOEIC_AUDIO_ROOT/<examId>/<fileName>`, các codec .wav/.mp3/.m4a, tối đa100 files,64 MiB/file,256 MiB tổng theo AudioCatalog, MIME metadata chuẩn audio/wav,audio/mpeg,audio/mp4 ứng với extension và decoder B kiểm. Catalog/current audio endpoint vẫn octet-stream có checksum; không thay endpoint/manifest algorithm. Import phải validate bytes/checksum/container decode được bằng công cụ Java hiện có trước VERIFIED, không bịa duration. Fixture dùng WAV PCM im lặng10ms để kiểm mapping, không hearing acceptance.

Candidate nhận manifest qua GET `/api/v1/attempts/{attemptId}/audio/manifest`, tải `/audio/files/{fileName}?manifestId=...`, checksum và speaker test rồi POST `/audio/ready` với writerEpoch hiện hành. ManifestId tiếp tục gồm sessionId/examId/files theo `AudioCatalog`, không lấy seed checksum làm manifestId. Proctor xem readiness, một START duy nhất cấp `startId`; WS `LISTENING_START` sau COMMIT. Không direct remote URL, không stream, không nút phát riêng trong renderer, không replay/resume khi reconnect. Takeover sau START giữ interruption/no-replay. Xem ma trận ADR cho gate và retry.

## 9. Mapping PostgreSQL và migration đề xuất

| JSON | V11/V12 / V5 | Ghi chú |
|---|---|---|
| exam metadata | exams | total_questions từ questions, không tin caller |
| sources/sourceRef | exam_sources/id | RETURNING map; duplicate source_sha256 reject; không gửi provenance xuống candidate |
| groups | exam_question_groups | order=min câu; mapping_status VERIFIED |
| media/sourceRef | exam_media_assets/source_id | no binary in DB; đo MIME/size/hash, FK scoped exam |
| questions/sourceRef | exam_questions/source_id/source_question_number | prompt concat TEXT blocks với newline; passage_text null rich để không nhân đôi documents; audio_file bridge mục8 |
| options/scoring | exam_options / exam_questions.correct_option | correctOption server-only, deferred FK kiểm commit; Part1/2 text null lưu option_text='' vì V5 NOT NULL; rich read NULLIF(text,'') cho hai Part này, hiển thị optionId, không bịa transcript |
| block nested | exam_content_blocks | owner question/group, text_content/media typed; order global owner |

**Chỉ đề xuất append-only migration tiếp theo (dự kiến V13 nếu chưa ai dùng số), không có SQL migration trong PR này:**

- `exams.content_version SMALLINT NOT NULL DEFAULT 1`, CHECK1/2. Legacy importer giữ1, rich2. Không suy version theo số câu/đáp án/media.
- `exam_sources.option_content_status VARCHAR(24) NOT NULL DEFAULT 'UNVERIFIED'`, CHECK UNVERIFIED/VERIFIED; giữ nguyên dữ liệu legacy chưa xác minh. Cần field vì V11 chỉ có key/group/media status.
- `exam_content_blocks.document_id VARCHAR(128) NULL`, `document_order INT NULL`, `document_title VARCHAR(255) NULL`. All-null cho owner question/Listening group. Với document: group_id nonnull, document_id/order nonnull, order>0; title nullable. DocumentId/order/title nhất quán trên mọi block cùng document; kiểm map hai chiều ID↔order trong importer/read validator. Không unique(document_id) trên blocks vì nhiều block/document; unique block owner order hiện có giữ nguyên. DB CHECK pair/all-null và importer kiểm cross-row consistency; không hứa DB CHECK đơn lẻ chứng minh toàn graph.
- `exam_sessions.contract_version VARCHAR(24) NOT NULL DEFAULT 'LEGACY_V1'`, CHECK LEGACY_V1/PROCTORED_V2; policy ADR. Không auto-upgrade ca tồn tại; không inference deadlineNULL. Reorganization phải copy version và cấp attempts mới.

EX03 sở hữu content/document/options marker; EX07 sở hữu session marker; A/C phối hợp cấp số migration theo thứ tự merge, không hai PR cùng dùng V13. Migration/version là blocker owner review, không đã được chấp thuận bởi việc duyệt phạm vi V2.

Không mở route GET rich exam/image khi chưa có gate EX07: EX03 có thể merge importer và mapper/service test riêng sau duyệt, nhưng registration endpoint candidate phải đi cùng gate để không xuất bản nội dung SCHEDULED. EX05 resolver có thể kiểm độc lập; không publish controller bỏ gate để chờ PR sau.

## 10. Backward compatibility và verification

| Thành phần | Legacy version1 | PROCTORED_V2 mới / điều cần duyệt |
|---|---|---|
| Import v1 | Payload/status200/duplicate400 INVALID_INPUT/đề ngắn giữ | Không nhận rich fields; không gán contentVersion2 |
| Candidate exam v1 | DTO phẳng hiện có giữ | Từ chối409 INVALID_STATE “Cần client V2”; không đường bypass |
| Autosave/submit v1 | QD21 và response giữ | Wire giữ; thêm session gate/lock theo ADR; cached retry semantics ghi riêng |
| Manifest/READY | API/manifest giữ | Download chuẩn bị SCHEDULED; null deadline cho READY; no replay |
| Listening START v1 | COALESCE deadline hiện có giữ | Chính endpoint này là authority toàn bài, chỉ branch policy theo session version |
| JavaFX hiện tại | Chạy legacy bình thường | Không hỗ trợ ca mới cho đến EX06 lobby/renderer; version error rõ |
| WS v0 | Message/reducer/FULL default giữ | Giữ LISTENING_START; không thêm GENERAL_START hay đổi DELTA |

[Fixture README](plan-v2/fixtures/ex02/README.md) mô tả render expectations và checker thiết kế. Negative cases phải kiểm đúng reason, không coi “JSON parse được” là validate runtime. EX03/05/07 phải thêm PG/HTTP/RBAC/rollback/race/path tests theo roadmap; EX06 cần JavaFX render/worker/no-replay; EX08 cần dataset200 hợp lệ + media/quyền sử dụng thật + physical. Trong task này **runtime mới NOT RUN / NOT IMPLEMENTED**; fixture MOCK không phải tích hợp production PASS. Owner actions cụ thể ở decision register.
