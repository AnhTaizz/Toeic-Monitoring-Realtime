# EX-04 — kiểm chứng V11 và review readiness EX-02

Ngày19/10/2026 · Owner A, schema C review, contract/render B review. Codex thực hiện kiểm kỹ thuật theo task.txt; **không tạo chữ ký owner, không merge, không đánh task DONE**.

## 1. Baseline và phạm vi

- Đã fetch: `origin/main f98a37f73d8035e9386e930412a02e9e92933015`, chứa PR65/V11. Nhánh `review/ex04-verification-ex02-contract-20261019` có worktree riêng; checkout gốc main3f5775b và các tài liệu/run.bat/untracked đang làm dở được giữ nguyên.
- Source tái hiện lỗi: `3077f409906d0e1d9e363f3a18f95f5106cd63c7`, chỉ thêm test/runner trên base. Source kiểm bản sửa: `975284d28a37159bdeb5bd8cba6b13732ba80ad1` thêm **V12**, không sửa V1–V11. Commit hồ sơ cuối không đổi test/SQL đã chạy.
- Đã đọc schema/API/protocol/QD21/QD35, V1–V11, service/validator/controllers/DTO, EX01–08 và seed README. Seed Test01 vẫn `importable:false`; không import seed, tải media hoặc tự tạo answer key/transcript cho nội dung bên thứ ba.
- PR65 đã merge. PR17/38/45 và draft53–56 là nhánh/packet lịch sử, không có rich-contract implementation đang chờ để merge lại; không sửa/đóng các PR đó.

## 2. Phân loại thiết kế/source/kiểm chứng

| Thành phần | Thiết kế đã ghi | Source hiện có | Kiểm thật / kết luận |
|---|---|---|---|
|Flyway|V1–V11 append-only|enabled=true, classpath:db/migration|VERIFIED fresh11/upgrade10→11/validate checksum và backend boot|
|Rich storage|4 bảng normalized, media ngoài DB|V11 có đủ bảng/FK/order/status|VERIFIED phần đã test; PARTIAL do NULL discriminator ở V11 và chưa filesystem/image config|
|Legacy import|DTO V5 và transaction|Service tạo group trước câu; validator kiểm orders/section/part/group|VERIFIED HTTP import/read/group/duplicate không overwrite, không lộ key|
|Rich import/read|Tài liệu mục tiêu V2|Vẫn flat passageText/audioFile, không rich DTO/mapper|MISSING; xem proposal EX02, không gọi schema là API hoàn chỉnh|
|Image media|Root/endpoint/auth/limits dự kiến|Chưa TOEIC_IMAGE_ROOT hoặc image controller|MISSING/BLOCKED cho render ảnh thật|
|Audio|Manifest/file/READY/start/no-replay hiện hành|AudioService/AudioCatalog/Listening có|Giữ nguyên; full regression báo riêng, không hearing/physical PASS|
|START toàn bài|V2 yêu cầu gate server|Chưa gate sessionIN_PROGRESS ở exam/save/submit/takeover|PARTIAL; HTTP reproducer SCHEDULED vẫn làm bài được; chưa đổi QD21|
|Dataset200|Một đề được xác minh nguồn/options/key/group/media|Seed candidate `importable:false`,4 nội dung/5HTML|BLOCKED cho import production/EX08; không sửa seed|

## 3. Môi trường và kết quả thực chạy

Windows11 x64 (OS build ghi trong từng JSON), Oracle JDK21.0.8, Maven3.9.11, PostgreSQL18.1 native x64. Mỗi lượt runner khởi tạo **cluster SCRAM mới**, loopback random port, database riêng `toeic_ex04_test`; mỗi case tạo/drop schema sở hữu `ex04_<uuid>`. Không dùng/xóa Docker volume, DB development hoặc production.

Nội dung đề là **MOCK tự tạo**. PostgreSQL/Flyway/production Spring/HTTP là thật; không dùng fixture này để kết luận đề TOEIC200/media production PASS. Raw logs/XML có thể chứa process environment nên chỉ giữ ignored; [evidence công bố](../evidence/ex04-v11-20261019/README.md) chỉ có case/status/timestamp/hash.

| Lượt | SHA | Kết quả |
|---|---|---|
|Reproducer NULL type trên V11|3077f40|**0 PASS / 2 FAIL / 0 ERROR / 0 SKIP**, DB đã chấp nhận row sai; failure giữ nguyên|
|Targeted schema + validation/controller|975284d|**58 PASS / 0 FAIL / 0 ERROR / 0 SKIP**; PG suite43case gồm2 HTTP,15case unit ở hai suite còn lại|
|Maven clean verify toàn project, bật PG|975284d|**984 PASS / 0 FAIL / 0 ERROR / 6 SKIP** (990case tổng; targeted là subset, không cộng thêm)|
|CI PR|HEAD PR cuối|Chỉ ghi theo GitHub checks; **không suy ra PostgreSQL PASS từ CI**|
|GUI/packaged/hearing/LAN/seed200 thật|—|NOT RUN trong task này|

Trong full run,5suite PostgreSQL có **110PASS/3SKIP** (subset của984/6, không cộng thêm). Sáu case SKIP gồm2symlink privilege ở client,1ScoreResultDialog GUI và3opt-in Listening GUI/installed/app-image. Xem tên case trong full-verify.json; không suy PASS cho các case bỏ qua.43casePG mới nằm trong cảtargeted vàfull;15unit targeted gồm ExamValidationTest8 +ExamControllerTest7.

Lệnh tái lập trên Windows, dùng JDK21/Maven/PostgreSQL đã cài:

```powershell
# Dùng đường dẫn cài đặt của máy; runner tự tạo database/credential/port test riêng.
./scripts/verify-ex04-postgres.ps1 -Mode Targeted `
  -PostgresBin '<postgres>/bin' -JavaHome '<jdk21>' -MavenCommand '<maven>/bin/mvn.cmd'
./scripts/verify-ex04-postgres.ps1 -Mode Full `
  -PostgresBin '<postgres>/bin' -JavaHome '<jdk21>' -MavenCommand '<maven>/bin/mvn.cmd'
```

Runner Targeted gọi `mvn --batch-mode --no-transfer-progress -pl server -am -Dtest=RichExamMigrationPostgresTest,ExamValidationTest,ExamControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`; Full gọi `mvn --batch-mode --no-transfer-progress clean verify`, `TOEIC_PG_INTEGRATION=true`. JAVA_TOOL_OPTIONS chỉ đặt timezoneUTC; password sinh ngẫu nhiên, không in và không lưu vào source. Không cần `.env` dự án. Cluster dừng ở finally, password file xóa và environment phục hồi. Kết quả cleanup thực tế nằm trong JSON từng run.

`-Mode Reproducer` ở commit3077f40 cố ý FAIL trên V11. Trên bản cóV12, cùng hai ca phải PASS; không đổi failure cũ thành kết quả mới. Runner metadata đã sửa cách chọn dòng Java version để bỏ banner JAVA_TOOL_OPTIONS; evidence lấy version từ stderr gốc, giữ hash raw result và XML, không sửa raw.

## 4. Fresh/upgrade/negative verification

| Nhóm kiểm | Kết quả và assertion |
|---|---|
|Fresh V1→V11|PASS:11 migration success/version1–11/checksum non-null/Flyway validate;4 bảng mới; unique indexes; correct-option FK DEFERRABLE INITIALLY DEFERRED|
|Backend startup|PASS: production Spring boot trên schema V11 đã nâng cấp; boot mặc định trên latest schema cóV12 ở test START hiện trạng|
|Upgrade V10→V11|PASS: snapshot users/login/exam/questions/options/session/attempt/assignment không mất; writer7/revision3/answers/deadline giữ; Q1–Q4 giữ order|
|Backfill group|PASS:G6/READING/Part6/minOrder1/UNVERIFIED; legacy group ba dấu cách →NULL; ungrouped NULL giữNULL|
|Bad legacy upgrade|PASS5ca: duplicate question/option order, orphan correct option, section/part mismatch, group dùng khácPart đều làm V11 FAIL và rollback toàn bộ DDL/data; không có history11 thành công giả; blank group cũ không bị sửa dở|
|Order constraints|PASS: duplicate question order, option order, question-owned và group-owned block order bị23505|
|Key/section/group|PASS: missing correct option bị23503 tại COMMIT; section/part sai23514; group exam khác23503|
|Block owner/TEXT|PASS:0/2owner và TEXT thiếu text/kèm media bị23514|
|Typed references|PASS: IMAGE→AUDIO, AUDIO→IMAGE hoặc media chỉ ở exam khác bị23503; block/media type không khớp bị23514 khi type có giá trị|
|LOCAL_VERIFIED|PASS4ca riêng: thiếu path/MIME/size/SHA bị23514; thêm negative size/hash/type dimensions/duration/storage enum|
|Source isolation|PASS: source exam khác23503; thiếu source-number pair23514; trùng source-question number23505; permission enum sai23514|
|V11 NULL media_type|**FAIL integrity**:2reproducer cho IMAGE/AUDIO với mediaId không tồn tại đều COMMIT được; xemF01|
|V12 correction|PASS:2NULL cases bị23514, valid TEXT/IMAGE/AUDIO giữ nguyên và history12/validate đúng; V11 bad row làmV12 rollback, không xóa/sửa row|
|Legacy HTTP|PASS: upgraded exam đọc được theo thứ tự; candidate khác403; import mới cógroup trả200; candidate import403; duplicate400 không overwrite; một exam dùng cho2ca|
|Candidate DTO|PASS: GET payload không correctOption/correct_option/answerKey/explanation/license/provenance private; vẫn flat chưa ảnh/orderedblocks|
|START gap reproducer|PASS tái hiện hiện trạng: SCHEDULED→GET200/takeover200/save200/submit200, session vẫnSCHEDULED; **không phải PASS tiêu chí EX07**|

Không cộng các case negative expected-exception thành failure của lượt PASS. Ngược lại, hai test baseline đỏ thật được giữ là FAIL, không bị xóa hoặc giảm assertions.

## 5. Findings và đề xuất tối thiểu

### F01 — P1: V11 bỏ qua typed FK khi media_type NULL

Reproducer tối thiểu trên schema V11 có examE1/questionQ1 hợp lệ:

```sql
INSERT INTO exam_content_blocks
 (exam_id,block_id,question_id,block_type,order_index,media_id,media_type)
VALUES ('E1','B1','Q1','IMAGE',1,'MISSING',NULL);
COMMIT;
```

V11 payload CHECK cho kết quả UNKNOWN ở nhánh `media_type=block_type`; PostgreSQL chấp nhận CHECK UNKNOWN. Composite FK MATCH SIMPLE bỏ qua khi một thành phần NULL. Vì vậy block có thể mồ côi hoặc bypass loại media. AUDIO có cùng lỗi. Mainf98 vẫn có lỗi, normalized runtime chưa được triển khai nên chưa khẳng định đã có dữ liệu production bị ảnh hưởng.

**Bản sửa trong nhánh review:** `V12__require_content_block_media_type.sql` thêm CHECK `block_type='TEXT' OR media_type IS NOT NULL`. Không đổi type/owner representation đã ghi trong QD35, không sửaV11, không backfill bằng phỏng đoán. Nếu DB đã có row sai, V12 thất bại transactionally; người vận hành phải đối chiếu nguồn rồi sửa dữ liệu có review trước khi migrate lại. Không chạy Flyway repair hoặc xóa row tự động.

Probe chỉ đọc trước upgrade:

```sql
SELECT exam_id, block_id, block_type, media_id
FROM exam_content_blocks
WHERE block_type IN ('IMAGE','AUDIO') AND media_type IS NULL;
```

### F02 — P1 readiness: rich schema chưa có import/read/media runtime

ExamImportRequest/ExamQuestionImportDto vẫn flat. ExamService tạo group tối thiểu nhưng không ghi sources/assets/blocks; CandidateQuestionDto không render được ordered images/documents. LOCAL_VERIFIED chỉ bảo đảm metadata không trống; DB không kiểm file tồn tại/hash/MIME/relative safety. Test đã chứng minh `../MOCK.png`, size0 có thể lưu vào metadata. REMOTE_REFERENCE có thể tham chiếu từ inventory blocks; đó không phải quyền download hoặc điều kiện phát hành đề.

Đề xuất: chốt [EX02 proposal](EX02_RICH_EXAM_CONTRACT_PROPOSAL.md), rồi EX03/05. Không thêm bảng nếu mô hình hiện tại đủ; không nhầm phép lưu inventory với acceptance EX04/08.

### F03 — P1 contract gate: START và QD21 chưa thống nhất

Đọc đề kiểm owner/assignment, save/submit/takeover kiểm ACTIVE/lock/epoch/deadline nhưng chưa kiểm sessionIN_PROGRESS. Deadline hiện khởi tại takeover hoặc legacy save-first, không phải proctor START; GET không bắt đầu giờ. HTTP test xác nhận bằng DB state và status200 thật. **Không sửa trong task này**; phương án chuyển tiếp/route-gating/lock/idempotency được nêu trong proposal, cần ADR trước EX07.

### F04 — P2: giới hạn integrity/representation cần validator rich

- FK question→group bảo đảm cùng exam, chưa bảo đảm question.section/part bằng group.section/part. Java validator import hiện kiểm consistency cùng group; test SQL cho thấy DB tự nó vẫn cho mismatch. Chốt thêm DB check/FK hay giữ validator trước khi importer mới chạy.
- V11 không ép questions/options order dương/contiguous hoặc profile200. V1 API normalize order<=0 theo vị trí; không đổi behavior đó trong task này. Rich profile phải kiểm1–200/7Part.
- Group order khi backfill dùngMIN(question order), service v1 dùng câu đầu trong mảng input; input không sắp thứ tự có thể cho group order khácMIN. Proposal chốt canonical ordering, chưa refactor service.
- Ordered blocks đủ lưu text/image/audio theo thứ tự; ranh giới/layout nhiều document Part7 chưa có DTO hoặc document container. Chốt với B trước renderer; không thêm bảng dựa trên suy đoán.

## 6. Mức đáp ứng từng Part

Fixture SQL nhỏ đã roundtrip7Part,3options choPart2, shared audioPart3/4, image+audioPart1, ordered TEXT/IMAGE/TEXTPart7. Đây là **khả năng biểu diễn schema**, không phải đầy đủ profile/production media hoặc UI.

| Part | Schema | API/GUI/media thật |
|---|---|---|
|1|Question IMAGE+AUDIO blocks, order riêng: VERIFIED với fixture|MISSING ảnh DTO/endpoint/render; media thật NOT RUN|
|2|Audio question +3options/nhãn: VERIFIED với fixture|DTO bắt prompt không rỗng, dùng nhãn hướng dẫn được; transcript không được bịa|
|3|Group/shared AUDIO +questions: VERIFIED mô hình|Bridge tới audio_file/manifest và rich client chưa chốt; group3/profile chưa ép|
|4|Group/shared AUDIO/optional IMAGE: VERIFIED mô hình|Tương tựPart3, media thật NOT RUN|
|5|Standalone prompt/options V5 tiếp tục hoạt động|HTTP legacy VERIFIED; profile4options rich chưa ép|
|6|Group/ordered TEXT và questions: VERIFIED mô hình|Chưa DTO ordered content, profile nhóm4 chưa ép|
|7|Group nhiều ordered TEXT/IMAGE: VERIFIED mô hình|Ranh giới/layout document và renderer chưa chốt|

## 7. Bàn giao

Đọc [gap matrix, Java records, JSON Part1/3/6/7, media/START proposal và roadmap](EX02_RICH_EXAM_CONTRACT_PROPOSAL.md). Chuỗi PR: **EX02 → EX03/05/07 → EX06 → EX08**, với dependency/việc song song ghi rõ; START cần ADR, media cần nguồn/file được xác minh.

V11 đã kiểm trên DB thật, nhưng **EX04 còn PARTIAL** do correction V12 chờ review/merge và filesystem/image runtime còn thiếu. EX02 là proposal, EX01/08 vẫn chưa đủ acceptance. Không đổi tracker/hours, seed.importable, answer keys, writer/revision/deadline, v0 hoặc defaultFULL. PR này không tự merge và không kết luận Phase3 physical/LAN/hearing PASS.
