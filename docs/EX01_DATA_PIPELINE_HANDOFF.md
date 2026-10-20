# Bàn giao cho Agent 4: pipeline HTML → `RichExamImportRequest` → PostgreSQL

## Trạng thái: `NOT_READY_FOR_AGENT4` (bản nháp, 20/10/2026)

**Agent 4 chưa được bắt đầu từ `main`.** Lý do:
- `main` (`e0ef252`) chưa có EX-03/V14, EX-05, EX-07/V15, EX-06, Proctor UI, nên `POST /api/v2/exams/import` chưa tồn tại trên `main`.
- Quyết định Flyway V13/V14/V15 còn chặn (`BLOCKED_FLYWAY_DECISION`), xem [release review](RELEASE_V2_MAIN_REVIEW_20261020.md) §4.

Tài liệu này mô tả contract **đang có trên chuỗi PR #75 → #77 → #78 → #79 → #76**. Các số liệu đã đo trên ứng viên `stabilize/v2-rich-main-candidate-20261020`.

Sau khi chuỗi merge hợp lệ và acceptance Phase D trên `main` PASS, người phụ trách release phải cập nhật tài liệu này:
- ghi full SHA của `main`;
- chạy lại import/catalog trên chính `main`;
- đổi trạng thái thành `READY_FOR_AGENT4`.

## 1. Agent 4 sẽ làm gì (khi được mở)

1. Tạo **nhánh riêng từ `main` đã xác minh** (không từ nhánh tích hợp), ví dụ `feat/ex01-html-pipeline-<ngày>`.
2. Bóc tách HTML TOEIC thành dữ liệu trung gian. Giữ nguồn, đường dẫn và checksum từng file.
3. Chuẩn hóa đúng **200 câu / 7 Part**: số câu theo Part là 6/25/39/30/30/16/54; dải câu là 1–6, 7–31, 32–70, 71–100, 101–130, 131–146, 147–200.
4. Viết converter sang `RichExamImportRequest` (schemaVersion **2**, profile `TOEIC_200`).
5. Đánh giá đáp án, options, passage/document và media. Chỉ đặt `VERIFIED` cho phần đã kiểm chứng được, ghi rõ cách kiểm.
6. **Chỉ import nội dung đã xác minh** qua `POST /api/v2/exams/import`, dùng tài khoản PROCTOR, trên DB TEST trước.
7. **Không** sửa schema, API hay UI nếu không bắt buộc. Thiếu gì thì báo kèm bằng chứng cho owner A/B/C (xem [ZENLISH_TO_V2_IMPORT_GAPS.md](ZENLISH_TO_V2_IMPORT_GAPS.md)).

## 2. Contract import (source of truth)

- **DTO** trong `protocol/src/main/java/vn/edu/toeic/protocol/exam/rich/`:
  - `RichExamImportRequest(schemaVersion, exam, sources, groups, media, questions)`.
  - `ExamMetadata(examId, title, description, profile)`.
  - `SourceImport`: ref, name/path/url, copyright, `PermissionStatus`, ngày xác nhận, license, sha256 nguồn/manifest, trạng thái answer key / group mapping / option content, `mediaValidationStatus`.
  - `GroupImport(groupId, section, part, title, orderIndex, mappingStatus, documents, blocks)`, `DocumentContent(documentId, orderIndex, title, blocks)`.
  - `QuestionImport(questionId, section, part, orderIndex, groupId, sourceRef, sourceQuestionNumber, answerKeyStatus, blocks, options, scoring)`.
  - `ContentBlock(blockId, type TEXT|IMAGE|AUDIO, orderIndex, text, mediaId, mediaType)`, `OptionContent(optionId, orderIndex, text)`, `ScoringKey(correctOption)`.
  - `MediaImport(mediaId, mediaType, sourceRef, sourceUrl, storageStatus, localFile, mimeType, sizeBytes, sha256, widthPx, heightPx, durationMs, altText)`.
- **Quy tắc chi tiết:** [EX02_RICH_EXAM_CONTRACT_V1.md](EX02_RICH_EXAM_CONTRACT_V1.md) §2–§4 (Part/group/media, thứ tự, provenance), §9 (mapping DB). Bằng chứng importer: [EX03_RICH_IMPORTER_VERIFICATION.md](EX03_RICH_IMPORTER_VERIFICATION.md).
- **API:**
  - `POST /api/v2/exams/import` (PROCTOR) trả **201 chỉ sau commit**. Import là create-only: trùng thì 409, sai graph/JSON thì 400, media thiếu/hỏng thì 409, quá 2 MiB thì 413.
  - Kiểm lại bằng `GET /api/v2/exams?limit=20[&afterExamId=]`: đề dùng được phải có `selectable=true` và `blockedReasons=[]`.
- **Schema DB:** source of truth là Flyway trong `server/src/main/resources/db/migration`.
  - V11: content groups/media/blocks. V12: media type bắt buộc. V14: `content_version`, option content status, document metadata. V15: preparation/visual readiness.
  - Bảng đo được trên DB PG18 mới: `exams`, `exam_sources`, `exam_question_groups`, `exam_questions`, `exam_options`, `exam_content_blocks`, `exam_media_assets`, `exam_sessions`, `exam_session_proctors`, `monitoring_attempts`, `attempt_audio_readiness`, `attempt_visual_readiness`, `visual_ready_requests`, `listening_reorganizations` và các bảng auth/monitoring/autosave/submit.
  - **Không ghi thẳng vào DB**; luôn đi qua API import để có validate và transaction.
- **Media root:**
  - Server đọc `toeic.audio.root=${TOEIC_AUDIO_ROOT}` và `toeic.image.root=${TOEIC_IMAGE_ROOT}`.
  - File được tra theo **`<root>/<examId>/<localFile>`**. Server tự đo size và SHA-256, không tin `LOCAL_VERIFIED` do client gửi.
  - Operator phải đặt file trước khi import, vào thư mục chỉ đọc. Server không tải file từ xa.

## 3. Fixture MOCK để tham chiếu

- **Nơi để:** `docs/plan-v2/fixtures/ex02/import.mock.json` (đề `mock-ex02-200`, tự soạn, CC0, **không phải dữ liệu thật**). Asset ở `docs/plan-v2/fixtures/ex02/assets/{audio,images}/mock-ex02-200/`.
- **Cách import:**
  - Đặt `TOEIC_AUDIO_ROOT=…/assets/audio` và `TOEIC_IMAGE_ROOT=…/assets/images`, rồi gửi `POST /api/v2/exams/import` với token PROCTOR.
  - Hoặc trên Windows: `scripts/demo-ex06-v2.ps1 -ImportOnly` (NOT RUN trên Windows).
- **Kết quả đo trên ứng viên** (PostgreSQL 18.1, DB TEST mới):
  - Import 201.
  - `exams`: `mock-ex02-200 | content_version 2 | 200 câu`.
  - Catalog: `schemaVersion 2`, item `mock-ex02-200`, 200 câu, `VERIFIED`, `VERIFIED_AT_IMPORT`, `selectable=true`, `blockedReasons=[]`.
  - Lần import thứ hai trả 409.
  - Fixture có 1 source, 76 group, 57 media, 200 câu.

## 4. Ràng buộc Agent 4 phải giữ

- Chỉ dùng nguồn có quyền rõ ràng (`PermissionStatus`). Không crawl hay tải đề TOEIC.
- Không bịa đáp án, transcript, option text hay media. Thiếu thì để `UNVERIFIED`; server sẽ chặn ca thi.
- Không đưa đáp án vào candidate DTO hay log.
- `correctOption` chỉ nằm trong `ScoringKey` của request import, nhánh chỉ server nhận.
- Mỗi lần chạy pipeline ghi SHA, máy, OS, nguồn và checksum. Dữ liệu raw không sửa tay; summary do script sinh ra.
- Fixture giả phải gắn nhãn `MOCK`.
- Python chỉ dùng để phân tích dữ liệu (theo CLAUDE.md). Converter production nếu cần chạy trong app thì viết bằng Java. Owner chốt điều này trước khi code.

## 5. Điều kiện để chuyển `READY_FOR_AGENT4`

1. Chuỗi #75 → #77 → #78 → #79 → #76 đã merge vào `main` và GitHub xác nhận.
2. Flyway đã được A/C chốt và đã kiểm danh sách migration trên `main`.
3. CI `main` xanh. Acceptance Phase D trên `main` PASS: server + PostgreSQL TEST, import/catalog, giám thị tạo ca và START bằng JavaFX, thí sinh làm bài và nộp, điểm đúng fixture.
4. Đã ghi full SHA của `main` vào tài liệu này.
