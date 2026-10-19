# EX-02 — Rich Exam Contract v1, thiết kế được nhóm thông qua

Ngày19/10/2026. **TEAM_APPROVED_AS_REPORTED_BY_PROJECT_REPRESENTATIVE**: D01–D09, ADR-V2-03 và D10 Option A được nhóm thông qua theo [nguồn xác nhận](plan-v2/EX02_TEAM_APPROVAL_20261019.md): “User-provided confirmation from the project team representative, 2026-10-19.” Đây không chữ ký từng thành viên hoặc GitHub Approving Review; runtime/integration/physical chưa được nghiệm thu. `schemaVersion=2` là phiên bản HTTP rich exam; “v1” trong tên tài liệu là lần phát hành thiết kế. WebSocket giữ protocol `v0`.

Baseline đã fetch lại: main `1d35b53729abade7b460d35ab07d042b6f9f3571`, đã có PR65/V11 và PR66/V12; PR67 đầu lượt ở `91786ba3fa54f94bc18bc28db5700e5454da69fc`, chưa merge. [CI baseline](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39214612088) SUCCESS. Đọc cùng [proposal đầu vào/lịch sử](EX02_RICH_EXAM_CONTRACT_PROPOSAL.md), [bằng chứng EX04](EX04_V11_VERIFICATION.md), [ADR START](plan-v2/ADR-V2-03_SESSION_START_DEADLINE.md), [quyết định và roadmap](plan-v2/EX02_DECISIONS_ROADMAP.md). Các API dưới đây là thiết kế được nhóm thông qua, **chưa callable**; không thay QD-21 legacy hoặc runtime trong PR này.

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

Ranges bắt buộc theo Part1..7 tương ứng **1–6 / 7–31 / 32–70 / 71–100 / 101–130 / 131–146 / 147–200**; không chỉ kiểm counts rồi cho phép các Part xen kẽ. Checker fixture là một phần oracle; EX03 phải kiểm cả ranges này.

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
| GET `/api/v2/attempts/{attemptId}/images/{mediaId}` | Owner CANDIDATE; SCHEDULED chỉ allowlist Part1 + visualManifestId, IN_PROGRESS theo scope rich | 200 bytes; lỗi mục7/7.1 |
| GET `/api/v2/exams?limit=20&afterExamId=...` | Bất kỳ PROCTOR đã auth; metadata-only | 200 `ExamCatalogPage` |
| GET `/api/v2/attempts/{attemptId}/preparation` | Owner CANDIDATE; SCHEDULED/IN_PROGRESS và ACTIVE | 200 `AttemptPreparation`; không câu hỏi |
| POST `/api/v2/attempts/{attemptId}/visual/ready` | Owner CANDIDATE; SCHEDULED, current writer/epoch/manifest; `VisualReadyRequest` → `VisualReadyResponse` | 200 sau commit, stale409 STALE/CONFLICT |
| GET `/api/v2/sessions/{sessionId}/readiness` | Assigned PROCTOR; metadata-only `SessionReadiness` | 200, không cấp START; unassigned403 |
| POST `/api/v2/sessions` | PROCTOR tạo ca và tự được phân công; payload mục6 | 201; media/content chưa đủ 409 INVALID_STATE |

Không thêm API preview câu hỏi cho proctor trong v2: giám thị chọn đề bằng metadata; candidate endpoint không cho PROCTOR kể cả được phân công. Không có public examId read. `limit` 1..100 mặc định20, cursor examId opaque hợp lệ, keyset `exam_id COLLATE "C" > cursor`, ascending; unknown query400. Trả cả imported legacy/unverified trong catalog để phân biệt; UI disable lựa chọn khi `selectable=false`.

GET v2 exam/preparation/images chỉ phục vụ ca PROCTORED_V2 có content_version2; owned legacy attempt trả409 INVALID_STATE sau auth/scope, client dùng v1 như cũ. Không chuyển đổi legacy DTO sang rich200 tự động. Strict unknown-field/enum policy ở mục2 chỉ áp dụng v2; parsing v1 giữ nguyên baseline, không âm thầm thêm required field hoặc đổi error code.

V2 SCHEDULED dùng preparation để đọc trạng thái lobby; các route v1 status chứa full answers (`/attempts/{attemptId}/status` và query alias `/attempts/status`) phải409 INVALID_STATE ở ca V2 trước START để không mở đường đọc answers. Login/history metadata vẫn được phép; IN_PROGRESS/final status theo quyền như ADR. Legacy giữ status semantics cũ. Không tạo endpoint đọc câu/options/answers từ visual manifest.

Auth/session token hợp lệ kiểm trước role/scope, trước lookup cache hoặc filesystem. Unknown/foreign attempt hoặc media không thuộc exam/không referenced trả403 FORBIDDEN đồng nhất, không lộ tồn tại. 401 UNAUTHORIZED token thiếu/hết hạn/revoked. Role sai403. ID/request malformed400 INVALID_INPUT sau auth. 413 INVALID_INPUT quá body; lỗi nội dung/enum/order/ref/key400 INVALID_INPUT; missing/hash/decode file trong import409 INVALID_STATE; duplicate409 CONFLICT; transient DB/IO503 RETRYABLE_SERVER_ERROR (`retryable=true`). File path không được xuất trong error. Dùng nguyên `ApiErrorResponse` v0, `error.code` hiện có, không thêm enum wire trong task này:

```json
{"protocolVersion":"v0","type":"ERROR","requestId":null,"traceId":"server-generated","error":{"code":"INVALID_STATE","message":"Nội dung chưa được phép truy cập","retryable":false}}
```

Import là **create-only**, không overwrite, không idempotent retry200; mất response201 → proctor kiểm catalog rồi quyết định, duplicate cùng payload vẫn409. Không import seed chưa xác minh vào runtime: thiếu key/options/groups/permission/media verification →400; file thiếu/corrupt →409; không tự điền A, tải remote, đổi flag hoặc tạo draft giả imported. Seed hiện hành `importable:false` giữ nguyên. Inventory remote ở EX01, không qua rich importer.

Thứ tự thực hiện trong task EX03 tiếp theo theo thiết kế đã được nhóm thông qua:

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
        String contractVersion, Instant startedAt, Instant deadlineAt, String visualManifestId,
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
        boolean listeningRequired, String audioManifestPath, VisualManifest visualManifest) {}
```

`CandidateMedia`: IMAGE có relative API imagePath đúng attempt+mediaId, audioFileName null; AUDIO có audioFileName là basename để join manifest hiện có, imagePath null; không phải localFile/path/URL nguồn. Image width/height bắt buộc >0, durationMs null; audio dimensions null, durationMs có thể null nếu codec chưa đo được. `startedAt/deadlineAt` trong RichCandidateExam non-null vì chỉ ACTIVE+IN_PROGRESS trước deadline mới được đọc; preparation trước START null cả hai. `RichCandidateExam.visualManifestId` là ID visual đã chốt tại START, để bridge đối chiếu context prefetched; không tự tính lại theo writer takeover sau START. Visual manifest trong preparation có writerEpoch đọc từ server nhưng không cấp writer; lobby phải takeover một lần, chia sẻ writer cho audio/visual và lấy preparation mới.

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

1. Token→role→attempt owner/exam binding→ADR state/deadline gate→media same-exam/reference/type/status; **trước filesystem/cache/header**. SCHEDULED thêm allowlist Part1/visualManifestId ở mục7.1, không blanket deny ảnh. Proctor không có route này. Không so sánh checksum trả về cho người không quyền.
2. Cấu hình root resolve real path lúc start; reject symlink/reparse points cho exam directory/file và mọi segment dưới trusted root; `toRealPath` và check containment theo filesystem Windows, không chỉ string prefix. Reject cả link nằm trong root để policy thống nhất. EX05 phải kiểm junction và Windows reparse attributes thực, không gọi test symlink SKIP là PASS. Không follow khi mở; verify identity/real path trước/sau read; root do operator kiểm soát read-only, chống TOCTOU bằng handle/identity và reject khi thay đổi. Không giả định NOFOLLOW_LINKS một mình chặn mọi junction.
3. Allow PNG/JPEG (image/png,image/jpeg), extension+magic bytes+decoder phải khớp; không SVG/GIF/HTML, không URL redirect/fetch. 1..8 MiB/file, tối đa100 image/đề, tổng64 MiB ảnh/đề; dimension mỗi chiều1..8192 và tổng ≤16,000,000 pixels. Đọc dimensions trước full decode; reject decode error/truncated file/oversized pixel bomb; hash bytes thực bằng SHA256 so metadata, size phải khớp; không chỉ tin MIME từ importer. Limits đã được duyệt thiết kế theo nguồn trên, chưa có đo performance với dataset thật.
4. Success200 bytes, exact Content-Type/Length, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `X-Image-SHA256`. **Chưa ETag, chưa304/Range/HEAD optimization**. If-None-Match/If-Modified-Since vẫn auth/state rồi200 hoặc lỗi, không304; Range được bỏ qua và trả full200 sau auth. Cache ảnh client chỉ RAM theo attempt+mediaId+hash, xóa khi logout/đóng bài, không public disk cache.
5. Authorized reference nhưng file missing/corrupt/hash mismatch/unsafe configured path →409 INVALID_STATE, không bytes; IO transient503 RETRYABLE_SERVER_ERROR; malformed mediaId400; foreign/unknown/wrong-Part/non-LOCAL_VERIFIED media403; auth401. Không fallback remote/placeholders để tuyên bố đủ media. Client khóa thao tác cần ảnh, hiển thị lỗi retry rõ, không làm FX thread treo. SCHEDULED ảnh Part1 hợp lệ được prefetch theo mục7.1; full exam/prompts/options/keys/passages vẫn bị chặn. Sau START ảnh lỗi phải theo interruption/no-replay, không reset deadline.

## 7.1. D10 — Part1 Visual Readiness Option A

**Ngoại lệ bảo mật được nhóm chấp nhận có chủ đích:** owner candidate của ca PROCTORED_V2 SCHEDULED được tải bytes ảnh Part1 trước START. Chủ máy có thể trích xuất bytes; RAM-only/no-store/ẩn ảnh dưới UI không bảo mật tuyệt đối trước chính người đó. Không gửi prompts/options/keys/passages, ảnh chỉ thuộc Part4/6/7, altText có thể tiết lộ nội dung, raw source URL hoặc filesystem path trong preparation. Không public static endpoint. Auth token/scope phải kiểm cả conditional/cache/retry; RAM cache theo attempt+writer+preparationEpoch+manifest+mediaId/hash, dọn logout/đóng lượt. Không tuyên bố RAM ngăn trích xuất.

Chọn **một contract**: preparation nhúng `visualManifest`; không thêm endpoint manifest riêng. Image download reuse route mục7, SCHEDULED bắt buộc một query `visualManifestId=<current ID>`. Query không là credential; không nhận local path/Part filter từ caller. Unknown/repeated query400. Quyền gồm owner, ACTIVE, session SCHEDULED, contentVersion2, verified IMAGE và **EXISTS question Part1 → IMAGE block → cùng mediaId trong cùng exam**. Không allowlist theo client hoặc số sáu assets. Sáu vị trí có thể reuse một media; asset được Part1 tham chiếu vẫn được phép dù cũng được Part khác dùng, nhưng không trả quan hệ/nội dung các Part kia. Fixture có một ảnh reuse cho sáu slots và hai mediaId chỉ thuộc Part4/7 để chứng minh deny.

Auth401/foreign attempt403 trước mọi data/cache/file; SCHEDULED unknown/wrong-exam/wrong-Part/unverified media403; đúng Part1 nhưng thiếu manifest query400 INVALID_INPUT, ID cũ409 CONFLICT, file hỏng/thiếu409 INVALID_STATE. IN_PROGRESS owner còn ACTIVE/trước deadline được đọc image referenced của đề; query nếu gửi vẫn phải đúng ID visual đã chốt, không bypass quyền. CLOSED/final409 INVALID_STATE, expired409 EXPIRED. `If-None-Match`/`If-Modified-Since` không đổi policy; **không304**, không ETag, no-store/nosniff như mục7. Full exam v1/v2 trước START409; không gửi answers trong preparation.

### Records và manifest identity

```java
enum VisualReadyAction { ATTEST, INVALIDATE }
record VisualManifest(int schemaVersion, String manifestId, String attemptId,
        String sessionId, String examId, int contentVersion, long writerEpoch,
        String preparationEpoch, List<VisualImageSlot> slots) {}
record VisualImageSlot(String slotId, int position, String mediaId, String downloadPath,
        String sha256, long sizeBytes, String mimeType, int widthPx, int heightPx) {}
record VisualAssetVerification(String mediaId, String sha256, long sizeBytes,
        int widthPx, int heightPx) {}
record VisualReadyRequest(String requestId, String attemptId, long writerEpoch,
        String preparationEpoch, String manifestId, VisualReadyAction action,
        String firstSlotId, List<VisualAssetVerification> verifiedAssets) {}
record VisualReadyResponse(int schemaVersion, String requestId, String attemptId,
        long writerEpoch, String preparationEpoch, String manifestId, String state,
        Instant readyAt, Instant leaseExpiresAt) {}
record SessionReadiness(int schemaVersion, String sessionId, Instant readAt,
        boolean allReady, List<AttemptReadiness> attempts) {}
record AttemptReadiness(String attemptId, long writerEpoch, String preparationEpoch,
        boolean audioReady, boolean visualReady, String visualManifestId,
        Instant visualLeaseExpiresAt, List<String> blockedReasons) {}
```

Manifest schemaVersion2, contentVersion2; six slots `p1-1`..`p1-6`, positions1..6 theo Part1 question order1..6, không questionId/prompt. Mỗi slot là ảnh required Part1 từ graph, không tự thêm ảnh group khác. `downloadPath` relative `/api/v2/attempts/{attemptId}/images/{mediaId}?visualManifestId={manifestId}`. `preparationEpoch` UUID do server cấp, không credential, đổi khi invalidate; không suy version bằng null deadline. Manifest stable trong cùng exam/session/attempt/writer/epoch/expected metadata, không có timestamp làm cache key.

Preparation SCHEDULED có visualManifest non-null, kể cả chưa READY; IN_PROGRESS trả visualManifest=null, client dùng full rich DTO và committed visualManifestId, không mint context mới để phát lại. Server xác minh ảnh bằng JDK ImageIO theo limits mục7, không thêm JavaFX/dependency decoder vào server.

Chọn canonical hash **SHA256 lowercase hex của Java DataOutputStream**: writeUTF(`EX02-P1-V1`), writeUTF(examId/sessionId/attemptId lần lượt), writeInt(contentVersion), writeLong(writerEpoch), writeUTF(preparationEpoch), writeInt(slotCount); từng slot theo position: writeUTF(slotId), writeInt(position), writeUTF(mediaId), writeLong(sizeBytes), writeUTF(sha256), writeUTF(mimeType), writeInt(widthPx), writeInt(heightPx). Big-endian; dùng expected metadata đã verified, không hash URL chứa chính manifestId hoặc source path. Thay writer/epoch/slot/expected bytes → ID mới. File thực đổi trái expected hash làm NOT_READY/409, không tự ký manifest mới cho bytes chưa verified. Rich exams create-only nên content graph bất biến sau import. Fixtures/checker tái tính hash theo quy tắc này.

### Handshake, retry và lifecycle

Client `NOT_PREPARED → DOWNLOADING → VALIDATING → UI_PREPARED → REPORTING → READY`; lỗi thành FAILED/NOT_READY, mất context thì quay NOT_PREPARED. Chỉ READY sau server ACK còn hiệu lực. Không coi HTTP200 download hoặc `new ImageView()` là đủ.

1. Một lobby coordinator takeover, giữ writer chung; lấy preparation mới. Download từng unique media vào RAM trên bounded worker, verify actual size/hash/MIME signature/dimensions và decode JavaFX Image xong (`progress=1`, `error=false`, width/height đúng) **trước** báo READY. File/network/hash không chạy FX thread. Decode lỗi/thiếu file không attestation.
2. Trên FX thread, gắn ImageView vào pane của scene thật dưới màn che trung tính (chưa cho thí sinh xem ảnh), set decoded image, applyCss/layout, kiểm geometry và snapshot widget fixture ở test; callbacks generation-scoped. Chuẩn bị ảnh đầu và toàn six slots trong RAM. Không có pending image loading/error. Bước này xác nhận UI_PREPARED, chưa khẳng định pixel vật lý đã hiện cho người dùng.
3. Audio pipeline hoàn tất checksum/speaker/codec và Audio READY ACK với cùng writer. Vì AudioPreparation hiện withdraw trước reportReady, coordinator phải chờ **ACK cuối**, rồi lấy lại preparation/epoch, đối chiếu RAM manifest hoặc prepare lại. Không attestation visual trước lần audio-withdraw cuối.
4. POST ATTEST: requestId1..128, route/body attempt giống nhau, writer>0, current epoch/manifest, firstSlotId=`p1-1`; `verifiedAssets` là tập **unique mediaId** chính xác của slots, không thiếu/thừa/trùng, hash/size/dimensions khớp. Sáu slots reuse một media → một verification entry. Không có boolean `ready=true` tùy ý. Server kiểm auth→session/attempt lock→current writer/epoch/manifest→Audio READY cùng epoch→verified resources→claims; sai writer409 STALE, epoch/manifest cũ409 CONFLICT, claims sai400 INVALID_INPUT, chưa Audio READY/file lỗi409 INVALID_STATE. Server đo/hash/decode resource hợp lệ, nhưng **không thể xác nhận client đã decode/render thật**; ATTEST là khai báo có scope, còn GUI tests phải chứng minh client làm đúng.
5. Persist và trả200 sau COMMIT: state READY, readyAt DBclock, leaseExpiresAt=readyAt+6000ms. Cấu hình mặc định visual lease6000ms/renew2000ms, renew nhỏ hơn nửa lease; client gửi ATTEST requestId mới mỗi2000ms khi audio/image/UI context vẫn hợp lệ. `readyAt` giữ mốc của context; renewal dùng một DBclock cho lastSeen/leaseExpiresAt, không deadline. Retry cùng requestId+canonical payload chỉ trả cùng receipt sau auth/current-scope và còn lease; **không gia hạn lease**. Cùng requestId payload khác409 CONFLICT. Lease hết hoặc row đã invalidated: retry không resurrect200,409 INVALID_STATE; client phải invalidate/prepare lại, không auto retry attest cũ. Request receipts keyed attempt+requestId, canonical assets sort mediaId, không so JSON text.
6. INVALIDATE trên cùng route: firstSlotId null, verifiedAssets=[]; auth/current writer/epoch/manifest/state như trên, xoay preparationEpoch, xóa cả audio+visual readiness và trả200 NOT_READY, readyAt/leaseExpiresAt null, manifestId/epoch mới. Riêng matched INVALIDATE retry: sau auth/current writer/state, đối chiếu canonical receipt và **epoch phản hồi** còn current trước khi xét request epoch cũ; chỉ trả cùng NOT_READY nếu chưa attested context mới, không xoay lần hai. Không dùng retry này để vô hiệu epoch mới của client khác. Client GET preparation lại. Đây là invalidate/ACK chuẩn bị, **không START authority**.

Invalidation áp dụng trước START: takeover (writer mới, epoch mới), đổi manifest/expected metadata hoặc file mất/hỏng, logout/revocation, rời lượt/đóng app, WS disconnect/reconnect, Audio READY withdrawn/lost/expired, visual lease hết, server restart. Reconnect trước START phải INVALIDATE và prepare/attest lại, không khôi phục READY từ cache; reconnect cùng epoch không tự gia hạn. Server dùng disconnect/revoke hooks và lease để giới hạn stale context; mất điện/mạng không báo được tức thời nên không hứa biết ngay khi candidate đóng app. START recheck login session đã attested còn valid và lease tại DBclock; readiness invalidators phải cùng lock order, không giữ auth-row lock rồi chờ session lock. Restart SCHEDULED rotate epoch/clear cả readiness **trước** nhận traffic; IN_PROGRESS giữ committed IDs/deadlines và áp Listening interruption hiện có, không reset/rearm. Sau START không gửi INVALIDATE để quay ca về lobby; lỗi đi theo interruption.

Storage design EX07, **không SQL/migration trong PR này**: attempt.preparation_epoch; attempt_audio_readiness.preparation_epoch cho V2; attempt_visual_readiness keyed attempt với writer/epoch/manifest/attested-login-session/asset digest/readyAt/lastSeen/leaseExpiresAt; request receipts scoped attempt+requestId; committed visual manifest ID trên attempt lúc START. Các rows/columns này chưa tồn tại, legacy dùng policy cũ. Proctor GET v2 readiness trả per-attempt audioReady/visualReady/lease và blockedReasons subset `AUDIO_NOT_READY,VISUAL_NOT_READY,VISUAL_EXPIRED,STALE_WRITER,STALE_MANIFEST,INACTIVE_ATTEMPT,SESSION_NOT_SCHEDULED`; không ảnh/questions/keys. `allReady` derived, không tin từ request; START vẫn kiểm lại dưới khóa.

### START, ảnh hiển thị và bridge Listening

TOEIC200 V2 chỉ START khi **mọi roster member Audio READY AND Visual READY** cùng writer/preparationEpoch/resources còn hợp lệ. Thiếu bất kỳ điều kiện →409 INVALID_STATE, session SCHEDULED, không startedAt/deadline/startId/WS, answers/revision/writer giữ. START vẫn một endpoint Listening v1, một DBclock sau khóa/verification, một COMMIT rồi WS; prefetch/READY/renewal không bắt đầu đồng hồ. Commit lưu visualManifestId dùng cho rich response. Retry receipt không yêu cầu reattest khi đã IN_PROGRESS, không tạo authority/đặt lại deadline; re-delivery cùng ID vẫn chỉ ARMED hợp lệ theo ListeningService.

Thứ tự bắt buộc: **prefetched Part1 images → Visual READY ACK → proctor START → rich candidate projection → image visible gate → MediaPlayer.play()**. Source hiện tại ListeningPlayback.prepare() gọi ListeningApi.exam() → GET v1 exam; ListeningPlaybackPane.render() gọi playCurrent trước render question pane, onReady gọi play(). Cả hai là dependency bắt buộc EX06; giữ nguyên source trong task này.

Chọn bridge V2: provider GET `/api/v2/attempts/{attemptId}/exam` sau START, đối chiếu visualManifestId đã commit với RAM context, cùng attempt/writer/audio manifest/startId; **không GET v1**. Adapter in-memory chuyển Listening questions theo group AUDIO → CandidateMedia.audioFileName thành CandidateExamDto/segments hiện có; prompt=TEXT blocks, options null-text thành nhãn, passageText null; full rich renderer vẫn dùng ordered documents, không degrade Part7. Một player và journal hiện có, Part3/4 shared audio chỉ một segment/group. Legacy provider/plan giữ nguyên.

V2 nhận command hợp lệ: reserve startId vào journal trước GET/scene transition; giữ dedupe cả khi prepare lỗi, báo INTERRUPTED cho authorization đã commit thay vì bỏ accepted key rồi chờ phát lại. Reuse HEARTBEAT event hiện có khi ARMED/PREPARING mỗi2000ms, không báo STARTED trước MediaPlayer.onPlaying; rich GET dùng async/bounded worker, không block scheduler heartbeat. Source server cho HEARTBEAT khi chưa playback_started_at; timeout mặc định6000ms giữ nguyên. Chọn tổng local monotonic budget **3000ms** từ accept command đến gọi play, mỗi HTTP tối đa min(2000ms, budget còn lại), không chờ vô hạn hoặc tăng timeout server. Response heartbeat stale/expired/interrupted chặn phát; command tới muộn có thể fail closed dù local budget chưa hết. Budget không hứa tránh mọi gián đoạn mạng/WS.

Trên FX thread sau rich DTO: gắn first ImageView đúng scene đang hiện, mở màn che, applyCss/layout; yêu cầu image decoded/no-error, node+ancestors visible/opacity>0, Stage showing, bounds có diện tích trong viewport. Đợi **hai pulse khác nhau** với điều kiện ổn định và generation/attempt/writer/startId còn đúng rồi mở cổng play; queued runLater hoặc tạo ImageView đơn thuần không đủ. Chỉ khi `imageVisibleGate && mediaOnReady && authorityValid && budgetRemaining` mới gọi play. Probe GUI tương lai phải snapshot **widget fixture của app** và kiểm event trace `IMAGE_ATTACHED → LAYOUT_DONE → VISIBLE_PULSE_1 → VISIBLE_PULSE_2 → PLAY_CALLED → ON_PLAYING`; không đoán từ JSON. Server/client không chứng minh pixel vật lý đã tới màn hình người dùng, đây là bảo đảm thứ tự trong scene graph cần GUI test.

Mọi segment Part1 tiếp theo dùng cùng gate với ảnh slot tương ứng; nếu đổi ảnh/render lỗi thì không play tiếp. Timeout, scene mất, image error, authority/network mất sau START: dừng/dispose player, journal interruption và gửi existing INTERRUPTED (MEDIA_ERROR cho image/render; NETWORK_LOST cho mạng), khóa Listening, không tự replay/resume hoặc reset/gia hạn deadline. Deadline đã commit tiếp tục chạy; giám thị dùng reorganize cấp attempt mới nếu cần. PRE-START READY không hứa không bao giờ có lỗi sau START. Chưa có GUI/HTTP/PG/WS runtime evidence cho flow này.

## 8. Audio bridge giữ player hiện hành

Mỗi Listening group có đúng một block AUDIO → mediaId → LOCAL_VERIFIED AUDIO asset → localFile. EX03 ghi cùng localFile vào `exam_questions.audio_file` cho mọi câu trong group, Reading null. Filename bắt đầu **ordinal ba chữ số** theo câu đầu, ví dụ `001-g001.wav`, `032-g032.wav`, `071-g071.wav`. Các tên sau prefix không ảnh hưởng thứ tự; cấm hai file/group/ordinal xung đột. `AudioCatalog` hiện distinct+sort lexicographic: đúng 54 files (6+25+13+10). Prefix có nội dung biến thiên trước ordinal có thể sort sai; không dùng. Group.order=min câu; verify playlist vs manifest trước import.

Giữ `TOEIC_AUDIO_ROOT/<examId>/<fileName>`, các container .wav/.mp3/.m4a, tối đa100 files,64 MiB/file,256 MiB tổng theo AudioCatalog, MIME metadata audio/wav,audio/mpeg,audio/mp4 tương ứng. EX03 server kiểm extension, size/hash theo manifest nguồn đã xác minh và signature tối thiểu: WAV RIFF ở offset0/WAVE ở8; MP3 ID3 hoặc MPEG frame sync đầu file; M4A ftyp ở offset4. Signature không chứng minh decode thành công. **Không thêm decoder/thư viện JavaFX vào server**: decode/playability dùng JavaFX media pipeline của B, probe từng file đã tải trước READY ở client V2; lỗi bất kỳ file không READY. LOCAL_VERIFIED/VERIFIED_AT_IMPORT chỉ xác nhận bytes/metadata đã kiểm, không đồng nghĩa playback READY; EX01/B phải xác minh codec/hearing thực. Catalog/current audio endpoint vẫn octet-stream có checksum; không thay endpoint/manifest algorithm, không bịa duration (không đo được thì null). Fixture dùng WAV PCM im lặng10ms để kiểm mapping, không hearing acceptance.

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

EX03 sở hữu content/document/options marker; EX07 sở hữu session marker và visual readiness storage mục7.1; A/C phối hợp cấp số migration theo thứ tự merge, không hai PR cùng dùng V13. Thiết kế đã được nhóm thông qua theo nguồn19/10; implementation/migration vẫn thuộc task sau, không có SQL mới trong PR67.

Không mở route GET rich exam/image khi chưa có gate EX07: EX03 có thể merge importer và mapper/service test riêng sau duyệt, nhưng registration endpoint candidate phải đi cùng gate để không xuất bản nội dung SCHEDULED. EX05 resolver có thể kiểm độc lập; không publish controller bỏ gate để chờ PR sau.

## 10. Backward compatibility và verification

| Thành phần | Legacy version1 | PROCTORED_V2 — thiết kế đã duyệt, chưa triển khai |
|---|---|---|
| Import v1 | Payload/status200/duplicate400 INVALID_INPUT/đề ngắn giữ | Không nhận rich fields; không gán contentVersion2 |
| Candidate exam v1 | DTO phẳng hiện có giữ | Từ chối409 INVALID_STATE “Cần client V2”; không đường bypass |
| Autosave/submit v1 | QD21 và response giữ | Wire giữ; thêm session gate/lock theo ADR; cached retry semantics ghi riêng |
| Manifest/READY | API/manifest audio giữ | Audio+Visual READY cùng epoch trước START; Part1-only prefetch, no304/no replay |
| Listening START v1 | COALESCE deadline hiện có giữ | Chính endpoint này là authority toàn bài, chỉ branch policy theo session version |
| JavaFX hiện tại | Chạy legacy bình thường | EX06 bắt buộc rich provider/segment bridge + visible-image gate trước play; không gọi GETv1 hoặc player thứ hai |
| WS v0 | Message/reducer/FULL default giữ | Giữ LISTENING_START; không thêm GENERAL_START hay đổi DELTA |

[Fixture README](plan-v2/fixtures/ex02/README.md) mô tả render expectations và checker thiết kế, gồm P01–P09/N01–N20 Option A. Negative cases phải kiểm đúng reason, không coi “JSON parse được” là validate runtime. EX03/05/07 phải thêm PG/HTTP/RBAC/rollback/race/path tests theo roadmap; EX06 cần JavaFX render/worker/visible-before-play/no-replay; EX08 cần dataset200 hợp lệ + media/quyền sử dụng thật + physical. Trong task này **runtime mới NOT RUN / NOT IMPLEMENTED**; fixture MOCK không phải tích hợp production PASS. Phê duyệt thiết kế đã ghi nguồn, không tự đánh implementation DONE.
