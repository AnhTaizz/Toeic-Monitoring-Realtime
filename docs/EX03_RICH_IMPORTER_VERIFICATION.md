# EX-03 — Rich TOEIC importer, 19/10/2026

Triển khai production code theo [EX02 đã được thông qua](EX02_RICH_EXAM_CONTRACT_V1.md) và [ADR-V2-03](plan-v2/ADR-V2-03_SESSION_START_DEADLINE.md). Owner A, C review transaction/schema/auth, B review DTO dùng chung và audio bridge. Không tạo chữ ký hay GitHub Approving Review thay các thành viên.

Baseline main `3bdefe6ccc346a316a91f1e280fe4f5c74600cfd`, PR67 đã merge; [CI đúng merge SHA](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39273580511) SUCCESS. Nhánh `feat/ex03-rich-importer-20261019`, worktree riêng; workspace gốc và file chưa commit được bảo toàn. Runtime/test/runner source cuối `9244321692e348cfafcb2e85afe963398477b5e4`; evidence cuối chỉ áp dụng source đã kiểm. Các commit sau source này chỉ được chứa tài liệu/evidence.

## API và transaction

`POST /api/v2/exams/import`, Content-Type application/json, Bearer PROCTOR. Controller kiểm role trước khi đọc body; service kiểm lại role trước validation/filesystem/DB. Parser v2 riêng: UTF-8 hợp lệ, tối đa2MiB, depth12, unknown fields/enum và duplicate keys bị từ chối; primitive required không được thiếu/null, integer không nhận string/float/exponent/overflow. NUL và surrogate đơn bị reject trước persistence; Unicode hợp lệ, gồm emoji, được giữ. Source URL không userinfo/fragment và chặn tên query chứa credential, kể cả access_token/api_key/client_secret/X-Amz-Signature, camelCase hoặc percent-encoded. Public query exam/author vẫn được phép. Không đổi converter/validator v1.

11 records và7 enums trong `protocol.exam.rich` theo đúng chữ ký import EX02, lists bất biến. Validator kiểm chính xác200 câu với ranges/counts7Part, stable IDs/orders, sources/checksums/permission/verification, graph, group/document boundaries, typed blocks/media, options và scoring keys. Part1/2 cho option chỉ nhãn; Part3/4 ba câu/audio chung; Part6 một document; Part7 một đến ba documents và block order liên tục toàn group.

`RichMediaValidator` chỉ đọc `TOEIC_AUDIO_ROOT/<examId>/<localFile>` và `TOEIC_IMAGE_ROOT/<examId>/<localFile>` (default image-resources). Không đọc sourcePath, tải URL hoặc ghi media. Kiểm basename/path, symlink/junction/reparse dưới trusted root bằng NOFOLLOW + attributes + real path, identity/size/hash trước/sau read. PNG/JPEG kiểm extension/MIME/magic/dimensions trước JDK ImageIO decode; audio WAV/MP3/M4A chỉ kiểm signature tối thiểu như contract, không khẳng định codec/hearing. Các root resolve khi import đã được auth để app legacy không cần provision root ảnh khi boot.

Validator đo lại bytes ngoài transaction, rồi hash/identity lại trước commit. Operator phải xuất bản files bất biến trong root do operator kiểm soát, chỉ đọc cho server; DB transaction không rollback filesystem và không bảo đảm file không bị thay sau commit. EX05/07 phải recheck khi cấp quyền sử dụng.

`RichExamService` dùng TransactionTemplate; repository insert exam create-only `ON CONFLICT DO NOTHING`, sources `RETURNING id` map sourceRef nội bộ → groups → media → questions/options → blocks. Duy trì prompt TEXT nối newline, passage_text NULL, option_text='' cho lựa chọn chỉ nhãn; group AUDIO bridge sang audio_file trên từng câu. Kiểm số row cùng exam và `SET CONSTRAINTS ALL IMMEDIATE`; mọi lỗi rollback toàn bộ DB. Response201 chỉ sau TransactionTemplate commit; duplicate/race409 CONFLICT. Không có overwrite hoặc retry200/idempotency.

| Kết quả | HTTP / error.code |
|---|---|
| Commit thành công |201; schemaVersion2, importStatus IMPORTED, contentValidation VERIFIED, mediaVerification VERIFIED_AT_IMPORT |
| Missing/expired/revoked token; sai role |401 UNAUTHORIZED;403 FORBIDDEN |
| JSON/graph/provenance chưa verified |400 INVALID_INPUT |
| Body quá2MiB |413 INVALID_INPUT |
| Local file thiếu/hỏng/unsafe/hash hoặc dimensions sai |409 INVALID_STATE |
| examId đã tồn tại, kể cả race |409 CONFLICT |
| IO/DB tạm thời, kể cả connection failure tại commit |503 RETRYABLE_SERVER_ERROR, retryable=true |
| Lỗi DB bất ngờ/constraint do fault injection |500 qua error handler hiện hành; không201 |

Error dùng ApiErrorResponse v0, không SQL/credential/đường dẫn. VERIFIED_AT_IMPORT mô tả bytes đã kiểm tại import, không Audio/Visual READY hoặc production dataset acceptance.

## Migration và tương thích

**V14**, không V13: đối chiếu main và PR70–74 ngày19/10 thấy [PR74 MON07](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/74) đã có `V13__experimental_monitoring_signals.sql` tại `7cb7478f2fba4478fd39925db930464d6cb34581`; các nhánh70–73 vẫn V12. Theo task.txt chọn số kế tiếp. Không sửa PR74 hoặc mang monitoring vào EX03.

`V14__rich_import_contract.sql` chỉ thêm exams.content_version SMALLINT DEFAULT1 CHECK1/2; exam_sources.option_content_status DEFAULT UNVERIFIED; block.document_id/order/title nullable và CHECK pair/group owner. Import rich ghi2, import v1 giữ1. Document ID/order/title nhất quán qua validator; CHECK không tự chứng minh graph cross-row. Không sửa V1–V12, không thêm session version/visual tables.

Trên nhánh EX03 chưa tích hợp PR74, Flyway fresh chạy13 scripts (V1–V12,V14), upgrade12→14 một script; số14 là version cuối, không phải14 scripts. **Cần A/C phối hợp thứ tự merge/deploy với migration V13 của PR74 hoặc cấp lại số trước khi merge.** Không thêm placeholder, bật outOfOrder hay áp migration vào DB dev/production để vượt dependency. V14 được kiểm riêng, chưa claim tích hợp MON07.

V1 create-session từ đề rich trả409 INVALID_STATE trước mutation, tránh sử dụng rich qua client legacy khi EX07 chưa có. V1 import/đề ngắn, QD21/save/submit/scoring, WS và Listening runtime giữ. Test EX04 HTTP nâng từ V11 lên latest trước boot app mới; các test lịch sử DDL V10/11/12 vẫn kiểm đúng target riêng.

Không đăng ký rich candidate GET, image controller, preparation/visual READY/catalog hoặc START gate. Không có candidate projection chứa scoring/source locator/provenance. Seed ETS vẫn importable:false; không tạo key/options/license thay dữ liệu chưa xác minh.

## Kiểm chứng

**READY_FOR_REVIEW — local verification PASS**, không merge hoặc nghiệm thu production. Full `mvn --batch-mode --no-transfer-progress clean verify` tại source9244321, run `ex03-20261019T154906Z-34c16188`: **1104 total =1098PASS/6SKIP/0FAIL/0ERROR**, BUILD SUCCESS. Windows11 build26200, Oracle JDK21.0.8, Maven3.9.11, PostgreSQL18.1. Cả114 case mới EX03 PASS; targeted là subset, không cộng totals. [Evidence máy đọc](../evidence/ex03-rich-importer-20261019/verification.json), [build receipt](../evidence/ex03-rich-importer-20261019/build-receipt.json), [hash raw](../evidence/ex03-rich-importer-20261019/raw-manifest.json).

[Runner](../scripts/verify-ex03-postgres.ps1) tạo PostgreSQL18 cluster SCRAM/loopback/database riêng, password ngẫu nhiên chỉ process/private files; mỗi test sở hữu schema và temp media roots. Lượt cuối **0 owned schemas remaining/clusterStopExit0**;3JAR có hash, server JAR có importer/V14 và không chứa V13 cũ. XML/log/environment/PGdata chỉ local ignored, không commit credential hoặc raw XML. CI PR xem GitHub Checks theo HEAD riêng; local PG không thay CI và CI không tự bật PG opt-in.

Giữ lịch sử âm: lượt đầu `ex03-20261019T152410Z-aa3e6cbb` có129PASS/11ERROR/0FAIL do test helper thiếu segment examId trong đường dẫn assets, đã sửa. Lượt targeted trung gian `ex03-20261019T152732Z-ca580c8a` 158PASS/0SKIP, tree chưa commit/V13 trước khi đổi số; không gán kết quả đó cho final V14. Full `ex03-20261019T153209Z-78831a71` tại c16b617:1085PASS/1FAIL/6SKIP,0ERROR; tất cả103 case EX03 PASS, failure là test legacy `candidateAttemptHistoryHidesNonExamRowsAndRejectsRevokedSessions` so giờ PostgreSQL với JVM lệch khoảng5ms. d3b0c19 sửa test so exact persisted submitted_at và clock_timestamp() cùng DB, không nới tolerance hoặc đổi production history; bổ sung read-back toàn metadata media và hai sourceRef→RETURNING ID. Mỗi lượt trước đều0schema còn lại/cluster stop0; raw giữ local. Lượt cuối là authority cho kết luận review.

Full `ex03-20261019T153851Z-888f1906` tại d3b0c19:1088PASS/6SKIP/0FAIL/0ERROR, cleanup0/stop0. Rà soát cuối bổ sung10 unit cases: trên code d3b0c19 + tests mới,9FAIL/82PASS/0ERROR trước sửa chứng minh query credential aliases và JSON NUL/surrogate chưa bị reject. Raw log/XML/test-source snapshot giữ riêng, không gán case mới đó cho full d3b0c19 đã chạy trước. Commit9244321 sửa parser/validator và bổ sung HTTP400 cho đầy đủ payload; positive Unicode/emoji và query công khai vẫn được kiểm. Không dùng full PASS cũ thay kết quả source9244321.

Fixture200 câu là [MOCK tự soạn CC0](plan-v2/fixtures/ex02/README.md), ảnh grid8×8 và54WAV im lặng10ms; PostgreSQL/Flyway/Spring HTTP chạy thật. Fault injection và commit-manager unit là MOCK/SIMULATED được ghi riêng; không dùng làm production media/physical acceptance.

| Acceptance | Kết quả trên source cuối |
|---|---|
| Fresh1→14, upgrade12→14, checksum/history/defaults và legacy preservation |PASS — PostgreSQL/Flyway thật; versions1–12,14 |
|200câu/775options/76groups/57media, options/scoring và media metadata read-back |PASS — fixture MOCK, HTTP201 rồi connection độc lập thấy rows đã commit |
| Part7 Email/Notice/Reply giữ3boundaries;54audio order/group bridge |PASS — DB read-back, không GUI/hearing claim |
| Hai sourceRef→RETURNING IDs, scoped question/media provenance |PASS — PostgreSQL thật |
| Strict JSON/profile/ranges/graph/documents/keys/status/credential aliases/Unicode |PASS —91unit, negative HTTP thực, không DB mutation |
| Local PNG decode/hash/dimensions, WAV signature; missing/corrupt/misdeclared |PASS — bytes thật trong temp roots; không production media |
| Duplicate concurrent import |PASS — barrier, PostgreSQL thật, đúng một commit và một409 |
| Midway/deferred FK failure và file đổi trong transaction |PASS — fault injection, rollback tất cả rows import; failure responses không201 |
| Auth trước body/files; PROCTOR/CANDIDATE và không có candidate GET rich/image |PASS — HTTP thật + boundary unit |
| IO503 retryable; connection failure tại commit không có receipt thành công |PASS — unit fault injection MOCK; không giả lập thành PG commit-success proof |
| V1 short import/session/exam/save/submit/scoring; auth/WS/Listening regression |PASS — suite nền full, gồm38ExamRuntimePostgresTest cases; không đổi runtime ngoài guard rich |
| Junction Windows dưới image root |PASS — tạo junction thật; không dùng SKIP symlink thay kết quả |
| Full clean verify/package và3JAR receipt |PASS —1098PASS/6SKIP,0FAIL/ERROR |

6SKIP giữ nguyên tên trong verification.json: `AudioFilesSharingTest.lockSidecarCannotRedirectOutsideCache`, `AudioFilesTest.symlinkInsideCacheRootIsRejectedWhereLinksCanBeCreated` (không tạo được symlink); `ScoreResultDialogTest.warningFollowsOnlyServerFlagAndKeepsConfirmedResult`, `ListeningRuntimePostgresTest.installedProductionClientUsesBundledRuntimeAndRealAudioLifecycle`, `proctorControlsLt04RealGuiBeforeAndAfterAutomaticTimeout`, `appImageProductionClientUsesUnifiedReadingListeningWithoutInstallerClaim` (GUI/installed/app-image opt-in chưa bật). Không đổi các SKIP này thành PASS.

EX05 còn scoped image delivery/Part1 allowlist/cache headers và resolver integration; EX07 còn session version/catalog/preparation/visual lease/combined START gates và race/WS evidence; EX06 còn JavaFX renderer/document panes, rich provider/audio adapter và visible-image-before-play. GUI/hearing/máy2/LAN, dataset200 production và integrated EX05/07/06: **NOT RUN**. Không đổi tracker/hours hoặc tự đánh EX03/Phase3 nghiệm thu DONE; task yêu cầu mở PR, không merge.
