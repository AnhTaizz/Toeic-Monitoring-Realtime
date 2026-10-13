# T3-A1 — Audio tài nguyên, READY và gate start

Ngày13/10/2026, Windows11 amd64, Microsoft JDK21.0.9, Maven3.9.16, PostgreSQL18 trong `toeic-db`. Base main `80ee1ba7c0b78d1ff1a5bdc8e748a73407df8216`; kiểm thử và [source/artifact hashes](source-audit.json) được ghi khi thay đổi còn ở working tree, trước khi commit. Bàn giao theo yêu cầu tiếp theo trên nhánh `feat/t3-a1-audio-manifest-ready`: `363bb90` DTO/schema, `8f98e11` API backend, `9ed067a` tests. Tài liệu và evidence có commit riêng; chưa có PR/merge trong phạm vi yêu cầu. [Đối chiếu code đã commit với source đã kiểm](committed-code-audit.json).

Theo yêu cầu mới, toàn bộ thay đổi T3-B1 của phiên trước đã hoàn tác: `client/pom.xml` và `ToeicClientApplication.java` trở về HEAD; sáu file mới trong package `client.audio` đã xóa. `git diff -- client` rỗng. T3-B1 không được tính hoàn thành.

## Thay đổi

- Protocol DTO audio dùng chung A/B, endpoint manifest/download/READY, proctor readiness và HTTP start gate. [Contract](../../docs/AUDIO_RESOURCES.md), [endpoint index](../../docs/PROTOCOL.md).
- Audio lấy từ file Listening được đề tham chiếu tại `TOEIC_AUDIO_ROOT/<examId>/<audioFile>`; chỉ đúng candidate sở hữu lượt ACTIVE tải được. Không đọc thư mục/gói đề hoặc answer-key, không cho proctor dùng đường tải candidate.
- Manifest chứa tên/kích thước/SHA-256, định danh cả ca và nội dung. File thiếu/rỗng/không hợp lệ/vượt giới hạn chặn manifest/start. READY gắn writerEpoch và manifest; takeover hoặc nội dung đổi làm READY cũ mất hiệu lực.
- Migration mới V9 lưu READY và startId/manifestId; không sửa V1–V8. Session -> attempt locks thống nhất cho READY/start; clock_timestamp sau khóa và kiểm file; không ACK trước COMMIT. Retry/concurrent start giữ một ID, không thay deadline/answers của lượt.
- Không thêm dependency hoặc thay code client/monitoring. `scripts/start-monitoring-server.ps1` nhận thêm TOEIC_AUDIO_ROOT; `.env` thật không đổi.

## Kết quả cuối

| Kiểm tra | Kết quả | Artifact |
|---|---|---|
| Maven package toàn5module, JDK21, offline cache | PASS:600 test chạy,0failure/0error;16test opt-in skip | [log](mvn-package.txt), [suite counts](package-results.json) |
| T3-A1 unit/auth policy/catalog | PASS15, nằm trong600 | AudioCatalogTest11, AudioReadinessPolicyTest2, AudioAuthorizationTest2 |
| Production HTTP/JDBC/Flyway + PostgreSQL, schema riêng | PASS8 test T3-A1,0skip | [log](postgres-smoke.txt), [method results](postgres-results.json) |
| ExamRuntimePostgresTest hồi quy hiện có | PASS11,0skip, có scheduler thật | Cùng lượt PostgreSQL, schema t2a_p0 riêng |
| Client rollback, diff formatting | PASS `git diff -- client` rỗng; `git diff --check` | Working tree |

Lượt PostgreSQL có19 test chạy. Không gọi16test bị skip trong lượt package là PASS;8audio và11exam (parametrized) đã chạy riêng thành công.

Lệnh thực tế:

```powershell
$env:JAVA_HOME = 'C:/Users/Lenovo/.jdks/ms-21.0.9'
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
mvn -B -o '-Dmaven.repo.local=C:/Users/Lenovo/.m2/repository' package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t3a1.ps1 -JavaHome $env:JAVA_HOME
```

Smoke tự đọc DB/seed từ `.env`, bổ sung defaults development khi không có cấu hình, không in credential. Chỉ tạo/dọn schema `t3a1_<UUID>` và `t2a_p0_<UUID>`, thư mục audio test trong `server/target` có dấu/khoảng trắng; không reset DB/volume. Seed user/đề/ca và WAV là fixture TEST; HTTP/server/Flyway/JDBC/PostgreSQL và byte file/hash là REAL. SpeakerTestPassed là báo cáo fixture, không phải evidence nghe loa thật.

Coverage đã PASS: thiếu READY/missing checksum/wrong checksum/no speaker confirmation; candidate khác/proctor/unassigned/unauth bị chặn; exact byte download và answer-key bị chặn; READY lặp giữ readyAt; withdraw/takeover/stale writer; thay một byte/mất file/manifest sau start; lượt expired/submitted/disabled/ca rỗng; concurrent start cùng ID; chờ khóa attempt đến quá deadline rồi từ chối; deferred constraint trigger gây COMMIT failure cho READY/start đều rollback và trả500, retry sau khi bỏ trigger thành công.

## Lượt lỗi đã giữ

- Lần package đầu không compile được fixture takeover do thiếu tham số clientSessionId; sửa bằng null đúng DTO. Package cuối PASS. Đây là lỗi fixture trong phiên, chưa phải runtime production.
- [Smoke env failure](postgres-smoke-initial-env-failure.txt): `.env` local thiếu DB_HOST; script đã bổ sung defaults giống app và giữ/restores biến môi trường.
- [Smoke JDBC/fixture failure](postgres-smoke-initial-jdbc-failure.txt): bind Instant trực tiếp khi start gây BadSqlGrammarException; sửa sang java.sql.Timestamp theo ExamService. Fixture ca rỗng ban đầu DELETE attempt đụng FK assignment; chuyển fixture sang tháo session_id của lượt thuộc schema riêng. Không nới constraint production. Lượt cuối19/19PASS.

Transcript được lưu UTF-8 để đọc trên GitHub; console Maven có thể hiển thị ký tự có dấu sai do encoding host, không đổi tên file/source thật. JSON kết quả lấy từ Surefire XML và chỉ chứa tên test/count/result; không lưu raw token/password.

## Giới hạn bàn giao

T3-A1 code và acceptance backend hoàn tất. C human review NOT RUN. LT01 toàn hệ thống PARTIAL: B1 kiểm file local/GUI/thử loa/package và T1-B4/máy thứ hai NOT RUN. T3-A2/B2/B3 chưa có WS start/playbackStarted/deadline trong lệnh/gián đoạn/resume policy hoặc tổ chức lại. HTTP start cấp phép bền vững cho A2, không tự phát audio. QD-07 codec/package vẫn chờ B; allow-list extension không chứng minh MediaPlayer hỗ trợ file cụ thể.

Server không quan sát được file local hay người dùng nghe loa; READY là client attestation. Operator phải giữ tài nguyên ổn định trong ca vì sửa trực tiếp file không nằm trong transaction DB. Kế hoạch gốc/TRACKER và các evidence cũ giữ nguyên; không giả review hoặc nghiệm thu các task còn lại.
