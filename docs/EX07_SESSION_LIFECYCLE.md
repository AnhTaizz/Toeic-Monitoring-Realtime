# EX-07 — Ca PROCTORED_V2: catalog, lobby, Audio + Visual READY, START

**Trạng thái: CODE + PG/HTTP TEST LOCAL PASS, chưa review/merge, chưa DONE.** Nhánh `feat/ex07-rich-session-lifecycle-20261019`, xếp chồng trên EX-05 `05cc694` ← EX-03 (PR75). GUI JavaFX, nghe thật, LAN, máy thứ hai: **NOT RUN**. Thiết kế nguồn: [contract EX-02 §4–7.1](EX02_RICH_EXAM_CONTRACT_V1.md), [ADR-V2-03](plan-v2/ADR-V2-03_SESSION_START_DEADLINE.md).

## 1. Dành cho FE (EX-06, vai B): luồng gọi API

Mọi request dùng `Authorization: Bearer <token>`; lỗi theo `ApiErrorResponse` v0 hiện có (`error.code`). Response V2 đều `Cache-Control: no-store`.

**Giám thị**

1. `GET /api/v2/exams?limit=20&afterExamId=...`: trả `ExamCatalogPage`. Chỉ đề có `selectable=true` mới được chọn; nếu false thì UI hiện `blockedReasons`.
2. `POST /api/v2/sessions` với body `CreateRichSessionRequest{examId,durationMinutes 1..240,candidateUserIds,proctorUserIds (phải gồm chính mình)}`: trả 201 `CreateRichSessionResponse`, state `SCHEDULED`, attempts không có deadline.
3. `GET /api/v2/sessions/{sessionId}/readiness`: trả `SessionReadiness`, gồm `allReady` và với mỗi attempt `audioReady/visualReady/blockedReasons`. Dùng để poll; kết quả **không** cấp quyền START.
4. START: `POST /api/v1/sessions/{sessionId}/listening/start {requestId,manifestId}`. Đây là endpoint Listening v1 duy nhất (ADR quyết định 2). `manifestId` là audio manifest của ca. Thiếu READY → 409 `INVALID_STATE`, không có thay đổi nào. Gửi lại sau khi đã START → 200 với cùng `startId/startedAt`. Manifest khác → 409 `CONFLICT`.

**Thí sinh (lobby, ca SCHEDULED)**

1. `POST /api/v1/attempts/{id}/takeover`: lấy writer chung cho audio và visual. V2 trước START **không** tạo deadline và trả `answers={}`.
2. `GET /api/v2/attempts/{id}/preparation`: trả `AttemptPreparation`, gồm `visualManifest` với 6 slot `p1-1..p1-6`, `preparationEpoch`, `writerEpoch` và `downloadPath` của từng ảnh. Không có câu hỏi, option hay đáp án.
3. Tải ảnh `GET downloadPath` (đã kèm `?visualManifestId=`) vào RAM, kiểm hash/size/kích thước, decode và gắn vào scene. Phần này là việc của EX-06, chưa làm.
4. Audio như hiện hành: `GET /api/v1/attempts/{id}/audio/manifest`, tải file, kiểm checksum, thử loa, rồi `POST /api/v1/attempts/{id}/audio/ready`. **Lưu ý:** gửi `ready=false` (withdraw) sẽ xoay `preparationEpoch` và xóa cả Visual READY. Vì vậy phải lấy lại preparation sau ACK audio cuối cùng.
5. `POST /api/v2/attempts/{id}/visual/ready` với `VisualReadyRequest{action:ATTEST, firstSlotId:"p1-1", verifiedAssets: tập mediaId duy nhất}`. Trả 200 `READY` kèm `leaseExpiresAt`, mặc định readyAt + 6000 ms. Client renew mỗi 2000 ms bằng `requestId` mới. Gửi lại cùng `requestId` sẽ nhận lại đúng receipt cũ và **không** gia hạn lease.
6. Khi mất context (reconnect, đổi màn, lỗi ảnh): gửi `action:INVALIDATE` (`firstSlotId:null`, `verifiedAssets:[]`). Server trả `NOT_READY` với epoch và manifest mới; sau đó GET preparation lại.

**Thí sinh sau START (ca IN_PROGRESS)**

- `GET /api/v2/attempts/{id}/exam`: trả `RichCandidateExam`, có `startedAt/deadlineAt/visualManifestId` (là ID đã chốt tại START).
- Ảnh: `GET /api/v2/attempts/{id}/images/{mediaId}` (query không bắt buộc; nếu có thì phải đúng ID đã chốt).
- Autosave/submit dùng route v1 như cũ. V2 kiểm writer hiện hành trước, writer cũ bị 409 `STALE`.
- `GET /api/v1/attempts/{id}/exam` của ca V2 **luôn** 409, client V2 không được gọi route này.

**Mã lỗi chính:** 401 token thiếu/hết hạn/bị thu hồi · 403 attempt/media ngoài quyền · 400 payload sai · 409 `STALE` (writer cũ), `CONFLICT` (epoch/manifest cũ, requestId trùng nhưng payload khác), `INVALID_STATE` (sai trạng thái, thiếu READY, lease hết hạn), `EXPIRED` (đã quá deadline).

## 2. Đã triển khai (server)

| Phần | Vị trí |
|---|---|
| V15: `exam_sessions.contract_version` (mặc định LEGACY_V1), `monitoring_attempts.preparation_epoch/committed_visual_manifest_id`, `attempt_audio_readiness.preparation_epoch`, `attempt_visual_readiness`, `visual_ready_requests` | `V15__proctored_v2_preparation.sql` |
| Khóa V2 theo thứ tự session → attempt (lookup không khóa, khóa xong mới kiểm lại) | `V2SessionPolicy` |
| Catalog, create V2, preparation, rich projection | `RichSessionService`, `RichExamGraphStore.candidate` |
| Visual ATTEST/INVALIDATE, receipt theo attempt+requestId, lease theo DB clock | `VisualReadinessService.ready` |
| Readiness của giám thị | `VisualReadinessService.readiness` |
| START V2: khóa session → roster attempts (`COLLATE "C"`) → readiness → login rows, verify file, gọi `clock_timestamp()` một lần, đặt `deadline = startedAt + duration` (không dùng COALESCE), chốt `committed_visual_manifest_id` | `AudioService.start` + `VisualReadinessService.verifyStart` |
| Gate v1: exam/status SCHEDULED 409; save/submit trước START 409; takeover SCHEDULED không tạo deadline và xóa READY; timeout bỏ qua SCHEDULED | `ExamService` |
| Reorganize lượt V2: chép PROCTORED_V2 sang ca/attempt mới SCHEDULED, không có deadline | `ListeningService.reorganize`, `RichSessionService.createReplacement` |
| Restart khi ca còn SCHEDULED: xoay epoch và xóa READY trước khi web server nhận request | `V2PreparationRecovery` |
| Authority cho ảnh EX-05 (`RichImageSessionAuthority`) | `V2SessionPolicy.lockAndAuthorize` |

Legacy V1 (QD-21 entry/takeover deadline, START dùng COALESCE), Listening no-replay và monitoring v0 không đổi. Các nhánh đều quyết định theo `contract_version` đã lưu trong DB.

## 3. Kiểm thử thực tế (19–20/10/2026, cloud container)

Môi trường: Linux, OpenJDK 21.0.12, Maven, **PostgreSQL 16.15** (cluster tạm, không phải bản 18 đã khóa), fixture `mock-ex02-200` MOCK CC0.

- `RichSessionLifecyclePostgresTest`: **8/8 PASS**. Riêng hai test race (concurrent START, START vs takeover) chạy lại 3 lần, **PASS cả 3**.
- `mvn clean verify` có `TOEIC_PG_INTEGRATION=true`:
  - protocol 45/0/0.
  - **server 443 run / 0 fail / 0 error / 4 skip**, gồm toàn bộ test PG legacy và EX-03/EX-05.
  - monitoring-spike 1/0.
  - client 652 run / **3 fail** ở `ControlledProcessHarnessTest`. Lỗi này cũng có trên `main` trong container Linux; harness là thiết kế cho Windows, không liên quan EX-07.
- Lỗi đã gặp trước khi sửa, ghi lại để không mất dấu:
  - WIP `6226cce` làm 38 test server ERROR vì fixture slice thiếu bean `V2SessionPolicy`, và môi trường test thiếu `TOEIC_SEED_PASSWORD`.
  - Lần chạy đầu của test mới có 2 lỗi do chính helper của test: body withdraw audio thiếu field, và đọc `visualManifest` sau START.

Đối chiếu ADR (trạng thái là **LOCAL PASS**, không phải nghiệm thu):

- S01, S02, S03, S04, S05 (một phần: foreign/proctor/anonymous/revoked), S06, S07, S12, S13 (một phần: takeover sau START giữ deadline), S14, S16 (một phần).
- V01, V02, V05, V06, V07 (phía server), V08, V09, V10, V11 (takeover, lease hết hạn, revoke tuần tự), V12, V13 (một phần).
- Restart, timeout, reorganize.

**NOT RUN:**
- S08 (latch rollback giữa chừng), S09/S13/V19 (WS delivery, reconnect thật), S10, S11, S15.
- V03 (conditional headers), V04 (file hỏng tại START), V14–V18, V20 (GUI/JavaFX).
- WS delivery `LISTENING_START` sau COMMIT với V2.
- Test race hiện dùng barrier, kết quả theo thứ tự tự nhiên của hai request. Chưa ép từng thứ tự bằng latch trong transaction.

## 4. Còn thiếu, rủi ro, cần owner quyết

1. **Thứ tự Flyway:** EX-03 dùng V14 vì PR74 (MON-07) đã giữ V13; EX-07 dùng V15. Nếu V14/V15 merge trước V13 thì Flyway mặc định sẽ từ chối V13. A/C phải chốt thứ tự merge hoặc đánh số lại. Không sửa V13.
2. **Chi phí verify:** ATTEST và renew (2 giây/lần, cho mỗi thí sinh) đang chạy `verifiedResources`. Hàm này hash lại **toàn bộ** media của đề (54 audio + ảnh) trong khi giữ khóa session. Với đề thật, con số này có thể lớn. Phương án là chỉ kiểm ảnh Part1 khi ATTEST và giữ verify đầy đủ khi START. Agent đã thử đổi nhưng bị chặn vì việc này thu hẹp phạm vi verify, nên **owner A/C phải quyết** và đo thật trước.
3. WS disconnect chưa hook để invalidate READY. Hiện chỉ dựa vào lease (6 s) và kiểm login tại START, đúng mức tối thiểu contract cho phép.
4. Readiness của giám thị dùng `FOR UPDATE` để có snapshot nhất quán, nên poll dày sẽ tranh khóa với ATTEST. Cần đo.
5. EX-06 (renderer, image-visible gate, rich provider), EX-08 và MON-* nằm ngoài task này.
