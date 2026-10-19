# Schema CSDL và ánh xạ bộ dữ liệu TOEIC

Tài liệu này mô tả schema PostgreSQL sau Flyway V11. Nguồn đối chiếu là năm file trong `data/seedingTest`, `manifest.json`, `DATA_IMPORT_CHECKLIST.md` và code runtime hiện tại. V11 chỉ sửa **cấu trúc lưu trữ**; chưa tự động import HTML và chưa xác nhận bộ dữ liệu đủ điều kiện phát hành đề thi.

## 1. Kết quả kiểm kê dữ liệu

Mỗi file HTML có 200 câu với phân bố Part 1–7 là `6/25/39/30/30/16/54`, tương ứng 100 Listening và 100 Reading.

| Thành phần mỗi đề | Số lượng quan sát được |
|---|---:|
| Câu hỏi | 200 |
| Nhóm Listening dùng chung | 23 |
| Nhóm Reading dùng chung | 19 |
| Tổng nhóm dùng chung | 42 |
| Thẻ audio | 54 |
| Thẻ ảnh trong toàn bộ HTML | 43 hoặc 48 |

Riêng seed mẫu Test 01, parser xác định 38 ảnh nằm trong question/group và loại 10 ảnh chrome của trang. Đây là lý do schema/media seed không lấy thẳng tổng số thẻ `<img>` trong manifest.

Các đặc điểm V5 chưa biểu diễn đúng:

- Audio Part 3/4 thuộc nhóm 3 câu, không thuộc riêng từng câu.
- Part 6/7 có passage/tài liệu dùng chung; một nhóm có thể cần nhiều block text/ảnh theo thứ tự.
- Part 1 có ảnh và audio gắn với câu riêng; Part 2 có audio gắn với câu riêng.
- Cần lưu nguồn, trạng thái quyền sử dụng, checksum tài liệu và checksum media.
- `group_id` cũ chỉ là chuỗi, chưa có khóa ngoại; thứ tự câu/option chưa được DB bảo đảm duy nhất.

Test 04 và Test 05 trùng byte. Năm file chỉ có bốn nội dung khác nhau. Media hiện chỉ là URL từ xa; đáp án, mapping group/passage và media local vẫn mang trạng thái chưa xác minh theo manifest.

## 2. Migration

Flyway chạy lần lượt V1–V11. Không sửa các migration đã áp dụng. Migration mới:

`server/src/main/resources/db/migration/V11__rich_exam_content.sql`

V11 giữ các cột V5 để API v1 hiện tại tiếp tục hoạt động, đồng thời thêm mô hình normalized cho dữ liệu phong phú.

## 3. Các bảng nghiệp vụ

### Auth và phiên đăng nhập

| Bảng | Chức năng |
|---|---|
| `user_accounts` | Tài khoản, role `CANDIDATE`/`PROCTOR`, mật khẩu BCrypt, trạng thái enabled. |
| `login_sessions` | Hash token, thời điểm cấp/hết hạn/thu hồi. Không lưu token thô. |

### Đề thi và nội dung

| Bảng | Chức năng |
|---|---|
| `exams` | Header đề thi và tổng số câu. |
| `exam_questions` | Câu hỏi, section, part, group, prompt, đáp án đúng và thứ tự. Có liên kết tùy chọn đến nguồn, số câu tại nguồn và trạng thái xác minh answer key. Các cột `passage_text`, `audio_file` được giữ cho API v1. |
| `exam_options` | Các lựa chọn của từng câu. |
| `exam_sources` | Nguồn/provenance, path/URL, trạng thái quyền, license document, trạng thái kiểm answer key/group/media và checksum file nguồn/manifest. |
| `exam_question_groups` | Nhóm dùng chung của Part 3/4/6/7, có section, part, thứ tự và trạng thái xác minh mapping. |
| `exam_media_assets` | Metadata ảnh/audio; phân biệt URL tham chiếu với file local đã kiểm MIME/bytes/SHA-256. Không lưu binary trong PostgreSQL. |
| `exam_content_blocks` | Chuỗi block `TEXT`, `IMAGE`, `AUDIO` có thứ tự, thuộc đúng một câu hoặc một group. |

Quan hệ chính:

```text
exams
  ├─ exam_sources
  ├─ exam_question_groups ─┐
  ├─ exam_questions ───────┼─ exam_content_blocks ── exam_media_assets
  │    └─ exam_options     │
  └────────────────────────┘
```

### Ca thi, lượt thi và Listening

| Bảng | Chức năng |
|---|---|
| `exam_sessions` | Ca thi, đề được chọn, thời lượng, trạng thái và lệnh start Listening. |
| `exam_session_proctors` | Giám thị được phân công vào ca. |
| `monitoring_attempts` | Lượt thi của candidate; writer epoch, deadline, revision, answers, kết quả và lifecycle Listening. |
| `exam_autosave_requests` | Idempotency log của autosave. |
| `exam_submit_requests` | Idempotency log và snapshot chấm điểm khi submit. |
| `attempt_audio_readiness` | READY cho đúng attempt/writer/manifest. |
| `listening_reorganizations` | Liên kết lượt bị gián đoạn với lượt thi thay thế. |

### Monitoring

| Bảng | Chức năng |
|---|---|
| `monitoring_proctor_assignments` | Scope giám thị theo attempt. |
| `monitoring_events` | Lịch sử process quan sát được, idempotent theo `(attempt_id,event_id)`. |
| `monitoring_gaps` | Khoảng mất event do queue overflow phía client. |
| `monitoring_presence` | Presence ONLINE/UNKNOWN mới nhất. |
| `monitoring_interruptions` | Lịch sử timeout/recovery do server quan sát. |

Current process state FULL/DELTA được giữ trong RAM của một server JVM, không nằm trong các bảng history.

## 4. Bất biến V11

- `(exam_id, order_index)` của câu hỏi là duy nhất.
- `(exam_id, question_id, order_index)` của option là duy nhất.
- `correct_option` phải tham chiếu một option thật của đúng câu; khóa ngoại được defer đến cuối transaction để importer có thể insert câu trước option.
- Part 1–4 chỉ thuộc `LISTENING`; Part 5–7 chỉ thuộc `READING`.
- `exam_questions.group_id` phải tham chiếu group cùng exam. V11 backfill group từ dữ liệu V5 trước khi thêm khóa ngoại.
- `source_id` và `source_question_number` của câu phải cùng null hoặc cùng có giá trị; số câu nguồn là duy nhất trong một source.
- Trạng thái `UNVERIFIED` được lưu rõ cho answer key và group mapping; việc có `correct_option` không đồng nghĩa đáp án đã được xác minh.
- Một content block thuộc đúng một owner: group hoặc question.
- Block `TEXT` phải có text và không có media; block `IMAGE`/`AUDIO` phải tham chiếu asset cùng loại.
- Asset `LOCAL_VERIFIED` bắt buộc có `local_file`, MIME, kích thước byte và SHA-256.
- File local không được trùng trong cùng exam.

## 5. Cách ánh xạ dữ liệu

| Dữ liệu nguồn | Bảng/cột đích |
|---|---|
| File HTML + manifest | `exam_sources`, gồm cả các trạng thái `NOT RUN`/`UNVERIFIED` tương ứng |
| Mỗi `article` câu hỏi | `exam_questions`, `source_question_number` và `answer_key_status` |
| Các lựa chọn A/B/C/D | `exam_options` |
| Listening 32–100 dùng chung | `exam_question_groups` + block `AUDIO` |
| Reading 131–200 dùng chung | `exam_question_groups` + các block `TEXT`/`IMAGE` theo thứ tự |
| Ảnh Part 1 | block `IMAGE` thuộc question |
| Audio Part 1/2 | block `AUDIO` thuộc question |
| URL media hiện tại | `exam_media_assets` với `REMOTE_REFERENCE` |
| Media đã tải và kiểm hash | cập nhật thành `LOCAL_VERIFIED` |

Không đưa phần giải thích/đáp án đúng vào DTO candidate. `correct_option` vẫn chỉ được server dùng để chấm điểm. Option trong seed candidate được trích từ phần review/explanation (nhiều câu là bản dịch); không ghi chúng vào `exam_options` production trước khi đối chiếu nội dung candidate-facing nguyên bản.

## 6. Giới hạn còn lại

- API `POST /api/v1/exams/import` vẫn dùng DTO V5; service đã tạo `exam_question_groups` từ `groupId` để giữ khóa ngoại, nhưng chưa nhận source/content blocks/media normalized của V11.
- `GET /api/v1/attempts/{attemptId}/exam` hiện trả `passageText` và `audioFile` dạng phẳng; chưa trả ordered content blocks hoặc ảnh.
- Chưa có importer HTML production. Parser sau này phải fail-closed khi thiếu option, đáp án, group hoặc media; không tự đoán dữ liệu.
- Seed candidate có thể tái tạo tại `data/seedingTest/generated/ets2026_test01.seed.json`; `importable: false` cho tới khi các blocker trong file được xử lý, bao gồm xác minh option candidate-facing độc lập với trang review.
- Chưa có file media local trong repository và chưa kiểm playback/render trong packaged client.
- Schema cho phép lưu inventory `REMOTE_REFERENCE`, nhưng một đề chỉ nên được đưa vào vận hành sau khi media bắt buộc đã thành `LOCAL_VERIFIED` và checklist import đạt đủ.

## 7. Kiểm chứng PostgreSQL và correction trong nhánh review

[Báo cáo EX04 ngày19/10](EX04_V11_VERIFICATION.md) có freshV1–V11/upgradeV10/negative/rollback/legacyHTTP trên PostgreSQL18.1 thật. V11 có lỗi CHECK UNKNOWN khi IMAGE/AUDIO thiếu media_type; nhánhreview bổ sung `V12__require_content_block_media_type.sql`, không sửaV11. V12 fail-closed với row sai có sẵn, không tự đoán/xóa dữ liệu. LOCAL_VERIFIED vẫn chỉ là ràng buộc metadata, chưa chứng minh file/path/MIME/hash thực tế; group-part matching cần validator và review contract. Không coi EX04 hoặc đề200 đã nghiệm thu đầy đủ. [Gap matrix và proposalEX02](EX02_RICH_EXAM_CONTRACT_PROPOSAL.md) chưa được triển khai thành API.

## Thiết kế tiếp theo — chưa có migration sau V12 trong task EX-02

[Contract candidate mục9](EX02_RICH_EXAM_CONTRACT_V1.md#9-mapping-postgresql-và-migration-đề-xuất) đề xuất reuse V11/V12, bổ sung content_version, option_content_status, document metadata trên blocks và session contract_version bằng migration append-only **sau owner review**. Không có DDL mới hoặc sửa V1–V12 ở PR thiết kế; không tự coi proposal này là schema hiện hành.
