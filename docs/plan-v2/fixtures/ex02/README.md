# EX-02 — fixtures MOCK và Visual Readiness Option A

**MOCK — design approval TEAM_APPROVED_AS_REPORTED_BY_PROJECT_REPRESENTATIVE.** [Nguồn phê duyệt19/10](../../EX02_TEAM_APPROVAL_20261019.md): “User-provided confirmation from the project team representative, 2026-10-19.” Đây không phải dữ liệu thi thật, GitHub Approving Review hoặc GUI/runtime PASS. [Contract](../../../EX02_RICH_EXAM_CONTRACT_V1.md), [ADR](../../ADR-V2-03_SESSION_START_DEADLINE.md).

Prompts/options/keys, bitmap bàn cờ8×8 và WAV PCM mono8kHz im lặng10ms được tự tạo bằng script ở đây, không lấy từ Zenlish. Nội dung fixture tự tạo theo **CC0-1.0**; không áp license này cho dataset khác. Hai PNG Part4/7-only là bản sao cùng PNG MOCK với mediaId/basename riêng để kiểm graph-derived allowlist, không khẳng định pixels khác nhau. Không dùng WAV im lặng để nghiệm thu hearing/READY production.

| File | Dùng làm gì |
|---|---|
| import.mock.json |Proposed request200 câu,6/25/39/30/30/16/54,76group,57media=54audio+3image; scoring chỉ trong import |
| candidate.mock.json |Post-START projection, committed visualManifestId, không keys/provenance/private paths |
| preparation.mock.json |SCHEDULED metadata, clock null, embedded Part1 visual manifest, không full exam |
| visual-manifest.mock.json |Six slots p1-1..6 theo Part1 question order, reuse đúng một unique img-grid; canonical hash binds attempt/writer/epoch/expected metadata |
| visual-ready-request.mock.json |ATTEST exact unique verified asset set, current writer/epoch/manifest/firstSlot; không boolean ready tùy ý |
| visual-ready-response.mock.json |ACK mẫu sau commit với readyAt/leaseExpiresAt; không HTTP capture |
| visual-readiness.mock.json |Assigned-proctor metadata cho hai members, Audio+Visual flags; allReady derived |
| visual-cases.mock.json |P01–P09 positive và N01–N20 negative design scenarios |
| visual-verification.mock.json |Kết quả29 cases visual, local PNG size/hash/.NET decode; JavaFX GUI NOT RUN |
| render-expected.mock.json |Part3/6/7 mapping và event order IMAGE→LAYOUT→PULSE1→PULSE2→PLAY, budget3000ms/heartbeat6000ms; là oracle thiết kế |
| negative.mock.json |Giữ19 mutation +10 access cases cũ; image-before-start giờ kiểm Part7-only403 theo D10, không xóa case |
| verification.mock.json |Tổng29 cases cũ +29 visual=58 model/design cases; không HTTP/PG/GUI/WS production proof |
| contract-verification.mock.json |Record signatures compile Java21, exact JSON fields/links; số records tăng theo DTO thực tế |
| catalog.mock.json, import-response.mock.json |Imported/verified flags và proposed201, không runtime response |
| create-session-{request,response}.mock.json |Proposed ca201, IDs giả do fixture đặt |
| start-{request,response}.mock.json |Listening START shape v0 giữ; audio manifestId placeholder cần thay bằng runtime ID |
| assets/ |Nguồn tự soạn,54WAV,PNG grid +copies vàcorrupt.png cố ý sai để kiểm rejection |

Chạy trên Windows PowerShell5.1, tại repository root:

```powershell
# Tái sinh fixture theo contract (không ghi vào media root production)
powershell -NoProfile -ExecutionPolicy Bypass -File docs/plan-v2/fixtures/ex02/verify-design.ps1 -Generate
# Kiểm lại đúng fixture đã commit
powershell -NoProfile -ExecutionPolicy Bypass -File docs/plan-v2/fixtures/ex02/verify-design.ps1
# JDK21 phải có sẵn; compile signatures vào target ignored
powershell -NoProfile -ExecutionPolicy Bypass -File docs/plan-v2/fixtures/ex02/verify-contract.ps1 -JavacPath javac
```

`visual-design.ps1` là helper của checker, không controller/client mới. Canonical hash dùng big-endian và writeUTF cho domain/IDs/MIME ASCII của fixture, khớp Java DataOutputStream; không hash JSON pretty-print. Writer/epoch đổi phải đổi ID. Checkers kiểm actual size/SHA256 của media và decode PNG thật bằng **System.Drawing/.NET**, kể cả reject corrupt.png. Đây không JavaFX Image, Scene/Stage, pulse hoặc MediaPlayer test; kiểm PLAY order chỉ dùng state model/trace. GUI acceptance V15–V17 phải chạy với Stage/layout/two pulses/widget snapshot thật ở EX06. Không gọi download HTTP200 hoặc object ImageView giả là render PASS.

Policy trước START: chỉ owner nhận manifest và ảnh được Part1 tham chiếu; img-p4-only/img-p7-only/foreign media403; revoked401; full question/options/answers409. Same asset được Part1 reuse nhiều lần là hợp lệ, dedup verify một asset nhưng giữ six slots. Cache chỉ RAM/no-store; chủ máy vẫn có thể trích xuất bytes ảnh Part1 trước START — ngoại lệ được nhóm chấp nhận, không bảo mật tuyệt đối. Manifest/ACK không có prompt/options/keys/raw source URL/local file path.

Visual state model kiểm current writer/epoch, stale rejection, lease renewal không bị retry cũ kéo dài, all-roster Audio+Visual trước START, no clock trước START và no replay/reset sau failure. Models không thực hiện locks/COMMIT/RBAC/WS/JavaFX, không chứng minh race/physical output. P09 kiểm SHA256 chuẩn hóa đoạn **QD21 hiện hành** không đổi; không chạy lại deadline service legacy. Backend HTTP/RBAC/PG/WS + actual JavaFX rendering/hearing/LAN **NOT RUN/NOT IMPLEMENTED**. Spec acceptance đầy đủ ở ADR S01–S16/V01–V20.

Renderer data vẫn phải thể hiện: Part1 q001 grid+A/B/C/D; Part2 q007 ba nhãn không transcript; Part3 g032 chung aud-032 cho q032–034; Part4 g071 optional image riêng; Part5 q101 độc lập; Part6 g131 một passage/bốn câu; Part7 g147 Email→Notice(TEXT+image)→Reply, giữ boundaries. V2 provider sau START lấy rich DTO rồi adapter in-memory sang segments hiện có; không GETv1/player thứ hai. Ảnh đầu chỉ được mở play gate sau rich DTO/layout/two visible pulses và Media onReady, có budget/fail-closed/interruption, không reset deadline.

Các checkers là **oracle thiết kế một phần**: không chứng minh strict duplicate JSON keys, toàn content limits/permission evidence, Windows junction/TOCTOU, transaction/locks, token revocation races, GUI responsiveness hoặc physical pixels. Seed ETS hiện hành vẫn importable:false, không đổi dataset/answer keys/media bên thứ ba hoặc đánh EX03–08 DONE.
