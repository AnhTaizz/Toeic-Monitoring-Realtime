# EX-02 — fixtures MOCK của contract candidate

**MOCK / PROPOSED_FOR_OWNER_REVIEW**, tự soạn ngày19/10/2026, không phải dữ liệu TOEIC thật. Prompts/options/keys, bitmap bàn cờ8×8 và WAV PCM mono8kHz im lặng10ms được tạo bằng `verify-design.ps1` ở đây, không lấy từ HTML Zenlish. Tác giả bản fixture cung cấp phần nội dung tự tạo theo **CC0-1.0**; không suy license này áp dụng dataset/source khác của repo. Không dùng âm im lặng làm bằng chứng hearing/playback/READY production.

[Contract](../../../EX02_RICH_EXAM_CONTRACT_V1.md), [ADR START](../../ADR-V2-03_SESSION_START_DEADLINE.md), [negative cases](negative.mock.json), [kết quả thiết kế](verification.mock.json).

| File | Dùng làm gì |
|---|---|
| import.mock.json | Exact proposed request:200 câu,6/25/39/30/30/16/54,76 group,55 assets (54audio+1ảnh reuse); scoring chỉ trong import |
| import-response.mock.json | Proposed201 response sau commit; đây không phải HTTP capture |
| candidate.mock.json | Projection allowlist để B dựng fixture view sau START; không key/provenance/path nguồn |
| preparation.mock.json | Lobby SCHEDULED, deadline/startedAt null; không nội dung đề |
| catalog.mock.json | Imported verified và imported legacy-unverified đều có200 câu; chỉ item đầu selectable, vẫn recheck media |
| create-session-{request,response}.mock.json | Proposed create ca201, server cấp UUID/attempts; IDs trong ví dụ là MOCK |
| start-{request,response}.mock.json | Existing Listening START shape; manifestId là placeholder MOCK, không runtime manifest |
| render-expected.mock.json | IDs dùng assert layout/group/playlist, không screenshot thực tế |
| negative.mock.json |19 mutation descriptors và10 access scenarios; mỗi mutation clone positive trước khi làm sai |
| verification.mock.json | Kết quả checker thiết kế đã chạy; không test server/DB/GUI |
| assets/source.txt | Provenance tự soạn và hash |
| assets/audio/mock-ex02-200/ |54 WAV có ordinal filename đúng order AudioCatalog |
| assets/images/mock-ex02-200/ |Ảnh hình học do generator tự vẽ |

Chạy bằng Windows PowerShell5.1 tại repository root (không cần server/PostgreSQL):

```powershell
# Kiểm fixture đã commit; script ghi lại verification.mock.json
powershell -NoProfile -ExecutionPolicy Bypass -File docs/plan-v2/fixtures/ex02/verify-design.ps1
# Chỉ dùng khi muốn tái sinh MOCK; không chạy vào thư mục media production
powershell -NoProfile -ExecutionPolicy Bypass -File docs/plan-v2/fixtures/ex02/verify-design.ps1 -Generate
# Kiểm JSON fields khớp records trong contract, links và compile signatures bằng JDK21
powershell -NoProfile -ExecutionPolicy Bypass -File docs/plan-v2/fixtures/ex02/verify-contract.ps1 -JavacPath javac
```

Checker là **oracle thiết kế một phần**, không phải validator triển khai EX03. Nó kiểm counts/order/group/ref/key/null discriminator/doc boundary, hash/size file thật, candidate forbidden keys, playlist và access-policy expectations. Không chứng minh toàn bộ constraints của contract (limits/strict duplicate JSON keys/permission evidence/decoder/Windows junction/DB transaction/cache/RBAC runtime). `missing-file` đổi reference sang file thật không tồn tại; `corrupt-file` trỏ đến `corrupt.png` chứa bytes cố ý không phải PNG, so với expected hash/size ảnh hợp lệ và bị từ chối, **chưa** test decoder. File corrupt không được positive request tham chiếu. `accessCases` chỉ chạy hàm policy MOCK, không HTTP response. Runtime acceptance ở ADR S01–S16 và EX03/05/06/08 còn NOT RUN.

Frontend phải thể hiện được các cấu trúc sau trên fixture view; checker chỉ xác nhận data shape, **GUI NOT RUN**:

- Part1 q001: ảnh bàn cờ và A/B/C/D không transcript; Part2 q007: A/B/C và audio reference.
- Part3 g032: q032–034 cùng aud-032, không ba nút phát; Part4 g071: shared audio + ảnh optional.
- Part5 q101: câu độc lập; Part6 g131: một passage trên bốn câu q131–134.
- Part7 g147: Email(doc1,block1), Notice(doc2,TEXT block2+IMAGE block3), Reply(doc3,block4). Không ghép thành passage mất ranh giới; question q147–149 theo sau shared documents.
- Các group/câu khác chỉ lấp đầy profile MOCK200 để kiểm mapping/order, không coi chúng là đề thi đã kiểm nội dung.

ManifestId không nằm trong fixture vì AudioCatalog tính theo session/files lúc runtime. `audioFileName` candidate là basename đối chiếu manifest hiện có, không local filesystem path hoặc remote URL. JSON chỉ là proposal, không được nạp vào server hiện tại rồi mong v2 endpoints đã tồn tại. Seed `data/seedingTest/generated` không thay đổi và tiếp tục `importable:false`.
