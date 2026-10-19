# PR67 / EX-02 / ADR-V2-03 — Visual Readiness Option A

Ngày19/10/2026. **TEAM_APPROVED_AS_REPORTED_BY_PROJECT_REPRESENTATIVE**. [Nguồn xác nhận](../../docs/plan-v2/EX02_TEAM_APPROVAL_20261019.md): “User-provided confirmation from the project team representative, 2026-10-19.” Đại diện nhóm xác nhận A/B/C đồng ý D01–D09, ADR-V2-03 và D10 Option A; không chữ ký riêng, không tự tạo GitHub Approving Review. GitHub PR67 reviews đầu task=`[]`.

[Contract](../../docs/EX02_RICH_EXAM_CONTRACT_V1.md), [ADR](../../docs/plan-v2/ADR-V2-03_SESSION_START_DEADLINE.md), [decision register/roadmap](../../docs/plan-v2/EX02_DECISIONS_ROADMAP.md), [fixtures](../../docs/plan-v2/fixtures/ex02/README.md).

## Baseline và bảo toàn workspace

Đã GitHub API + native Git fetch lại main và chính branch PR67: main `1d35b53729abade7b460d35ab07d042b6f9f3571` chứa PR65/V11 và PR66/V12; PR67 còn OPEN/chưa merge, HEAD đầu task `91786ba3fa54f94bc18bc28db5700e5454da69fc`, mergeable, không xung đột. [CI baseline main39214612088](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39214612088) và [CI PR trước thay đổi39219165303](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39219165303) SUCCESS; không gán kết quả đó cho HEAD mới.

Dùng lại worktree clean của branch `docs/ex02-contract-adr-start-20261019` trong ignored target. Root main3f5775b và docs/launcher/evidence/task chưa commit của người dùng giữ nguyên; snapshot HEAD/status/hash các file tracked dirty trước thay đổi giữ riêng, không công bố môi trường/credential. Không checkout/reset/stash root, không sửa nhánh thành viên. Lần xét duyệt quyền đầu bị **timeout**, không safety rejection; retry một lần được phép và Git fetch chạy thành công. Không bypass permission.

## Audit source — hiện có và phần cần triển khai

- [ListeningPlayback](../../client/src/main/java/vn/edu/toeic/client/audio/ListeningPlayback.java): accept START → worker prepare → verify audio → `ListeningApi.exam` lấy DTO legacy → plan shared segments → journal.reserve → READY_TO_PLAY. Chưa có visual gate; prepare lỗi trước reserve có thể remove accepted key. V2 contract yêu cầu reserve/consume command trước async rich read và interruption khi lỗi sau commit, không replay.
- [ListeningApiClient](../../client/src/main/java/vn/edu/toeic/client/audio/ListeningApiClient.java): exam gọi GETv1, HTTP timeout30s hiện tại; EX06 phải dùng rich provider sau START và budget ngắn có fail-closed, không đi đường GETv1 bị V2 gate chặn.
- [ListeningPlaybackPane](../../client/src/main/java/vn/edu/toeic/client/audio/ListeningPlaybackPane.java): render READY_TO_PLAY gọi playCurrent trước dựng questions; MediaPlayer.onReady gọi play. Chưa image/scene/layout/pulse gate; không thể dùng source hiện tại để tuyên bố ảnh đã hiện trước audio.
- [AudioPreparation](../../client/src/main/java/vn/edu/toeic/client/audio/AudioPreparation.java)/[view](../../client/src/main/java/vn/edu/toeic/client/audio/AudioPreparationView.java): download/hash trên worker; speaker confirmation và server READY ACK; withdraw trước reportReady. V2 coordinator phải đợi ACK audio cuối, fetch epoch mới rồi visual ATTEST, không lấy visual ACK trước lần withdraw cuối.
- [ExamEntry](../../client/src/main/java/vn/edu/toeic/client/exam/ExamEntry.java)/[gateway](../../client/src/main/java/vn/edu/toeic/client/exam/ExamGateway.java)/[CandidateExamView](../../client/src/main/java/vn/edu/toeic/client/exam/CandidateExamView.java): entry status→flat exam→takeover, shared writer Reading/Listening; chưa lobby/prefetch/rich bridge.
- [ExamService](../../server/src/main/java/vn/edu/toeic/server/exam/ExamService.java) hiện chưa V2 gate, takeover/save-first có legacy deadline, status chứa answers. Target V2 dùng preparation metadata trước START, gate full exam/status/answers cả v1; legacy QD21 giữ.
- [AudioService](../../server/src/main/java/vn/edu/toeic/server/audio/AudioService.java) hiện Audio READY và START session→attempt locks, DBclock/deadline COALESCE; chưa visual storage/epoch/lease/combined predicate. [ListeningDelivery](../../server/src/main/java/vn/edu/toeic/server/audio/ListeningDelivery.java) AFTER_COMMIT. [ListeningService](../../server/src/main/java/vn/edu/toeic/server/audio/ListeningService.java) chấp nhận HEARTBEAT trước playback_started_at, timeout6000ms; restart/interruption/no-replay giữ.

**Không sửa các class trên hoặc Java/SQL/POM/protocol runtime trong task này.** Rich DTO/image/visual-ready/START target vẫn NOT IMPLEMENTED. EX04 [bằng chứng PG/HTTP lịch sử](../../docs/EX04_V11_VERIFICATION.md) là source khác, không chứng minh flow Option A đã chạy.

## Thay đổi thiết kế

Option A: preparation nhúng manifest Part1 canonical ID binds exam/session/attempt/writer/preparationEpoch/expected metadata; six slots có thể reuse một media. Image endpoint hiện được đề xuất chỉ cho owner SCHEDULED tải allowlist Part1 với current manifest; wrong-Part4/7-only/foreign/unverified403, revoked401, full exam/options/answers409, no304. Không raw source path/URL/keys/provenance; RAM/no-store không ngăn chủ máy trích xuất bytes — ngoại lệ được nhóm chấp nhận.

Visual lifecycle decode/UI_PREPARED→ATTEST/ACK current scope, lease6000ms/renew2000ms; server kiểm claims/resources/auth chứ không chứng minh JavaFX đã vẽ pixels. Takeover/invalidate/revoke/disconnect/audio loss/restart làm READY cũ không còn hợp lệ. START toàn roster Audio AND Visual, một DBclock sau khóa và verify, thiếu READY409 không mutation/deadline/startId/WS; retry không reset/rearm. Sau commit rich provider→existing segments/player→first ImageView visible/two pulses→play, budget3000ms và heartbeat policy giữ; lỗi interruption/reorganize, không replay/gia hạn. Đầy đủ DTO/errors/storage proposal trong contract, S01–S16+V01–V20 trong ADR; chưa migration mới.

## Verification của lượt Option A

Windows PowerShell5.1 / Oracle JDK21.0.8. Checkers chỉ oracle thiết kế, chưa backend/GUI runtime.

| Kiểm | Kết quả ở lần kiểm cuối |
|---|---|
| verify-design.ps1 -Generate, rồi kiểm fixture đã sinh |PASS cả lượt tạo và kiểm cuối:200Q/76group/57media,54audio;29 cases cũ giữ lại +9 visual positive/20 negative=58 design cases. [Kết quả tổng](../../docs/plan-v2/fixtures/ex02/verification.mock.json) và [visual](../../docs/plan-v2/fixtures/ex02/visual-verification.mock.json) ghi phạm vi MOCK và runtime NOT RUN |
| Local image files |Size/hash và .NET PNG decode/dimensions; corrupt PNG reject thật. **Không JavaFX GUI/pixels PASS** |
| verify-contract.ps1 / Java21 signatures / independent Java canonical manifest hash |PASS:28 records compile Java21,11 example roots/nested fields khớp;18 fixture JSON parse;98 local links; canonical manifest hash khớp Java DataOutputStream/SHA256 độc lập. [Result](../../docs/plan-v2/fixtures/ex02/contract-verification.mock.json) pin SHA256 contract đã kiểm |
| Git scope/diff / root preservation |PASS audit và staged diff --check (cả file mới), root HEAD/status/hash dirty files giữ; không production/source/migrations/data/tracker changes |
| CI HEAD mới |NOT RUN tại snapshot trước push; sau push phải chờ exact HEAD và ghi URL/kết quả trong PR/phản hồi cuối |
| Target HTTP/RBAC/PG/WS / JavaFX real rendering / hearing / physical LAN |NOT IMPLEMENTED / NOT RUN trong task này |

Lịch sử trước Option A: source91786ba có21 record signatures/7example roots/12fixtureJSON/85links và29 old-design cases PASS; [result cũ được pin theo SHA](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/blob/91786ba3fa54f94bc18bc28db5700e5454da69fc/docs/plan-v2/fixtures/ex02/contract-verification.mock.json). Lượt mới tăng DTO/cases/media theo D10, không giữ số21 cho đẹp.984PASS/6SKIP là lịch sử EX04, không rerun local Maven/PG trong task docs này.

## Bàn giao và blockers

Lượt Option A chạm37file so với91786ba (27existing+10new), toàn PR67 có100file so với main. Nhóm file: contract/ADR/decision register và nguồn phê duyệt; preparation/candidate/manifest/visual-ready/readiness/cases/results/render/negative fixtures, hai checker+visual helper và hai bản sao PNG MOCK; README/schema/backend/protocol/quyết định/test/progress/log/role handoff và report này. Không thêm third-party media hoặc code trong server/client/protocol.

Thiết kế được nhóm thông qua theo đại diện; không còn blocker “D01–D10/ADR chưa owner đồng ý”. Còn triển khai EX03/05/07/06, PG/auth/locks/WS/JavaFX tests, EX01 media/quyền/keys/options/group verification thật và EX08 integrated/physical acceptance. P01–P09/N01–N20 model PASS không thay GUI/API PASS. Không chỉnh seed importable:false, QD21 legacy, WSv0/FULL/no-replay hoặc tracker/hours.

Task yêu cầu update chính PR67 rồi dừng trước merge. Merge readiness sẽ kết luận sau checkers/diff/CI; không tự merge hoặc tạo approving reviews/chữ ký cá nhân.
