# V2 IMPLEMENTATION KICKOFF — 19/10/2026

**Trạng thái:** Plan V2 đã được nhóm duyệt **phạm vi** theo xác nhận của người đại diện ngày 19/10 (buổi duyệt tối 18/10). **Chưa ký riêng các technical ADR**, chưa chạy physical acceptance và chưa có task V2 mới được code ở commit kích hoạt. `main` đầu vào: `20d119f5732022f387be8b31e8153489418c859c`, CI run 39186372875 SUCCESS. Luôn fetch HEAD mới trước khi bắt đầu task.

## Mục tiêu vận hành

- Giữ source Phase 3 code-ready đang có, không mở lại hai P1 đã fix nếu không có reproducer mới.
- Hoàn thành điểm mới **EX-08: 200 câu/7 Part/media đúng quyền** và **MON-09: đa tín hiệu process/USB-storage/focus/display** bằng các vertical slice nhỏ, auth/ACK/DB/GUI thật.
- Quyết gate C4 và physical LAN/MSI/Listening còn NOT RUN. FULL default; DELTA không tự bật.
- Đến **sau 23/10** đóng băng tính năng mới theo V2; dùng 24–30/10 cho QA/USB/LAN/hardware, E1, conditional E2, report, release. Hạn chính thức **31/10 23:59 ICT**.

## Công việc khởi động ngay — 3 luồng song song

| Owner → Review | Task bắt đầu | Việc làm trong lượt đầu | Bàn giao / điểm dừng |
|---|---|---|---|
| **A → C** | `BASE-01`, `BASE-04`, `EX-01` | Fetch exact main/PR/test, lập gap matrix; kiểm quyền và số lượng của **một** bộ 200 câu 6/25/39/30/30/16/54; chỉ phác đồ ordered media blocks/endpoint auth; đọc QD-21 session START | Evidence nguồn dataset/media hợp pháp (hoặc BLOCKED), phân loại already implemented/missing; đề xuất contract cho A/B/C review, chưa viết migration khi chưa duyệt representation |
| **B → A/C** | `BASE-02` acceptance vật lý; `UI-01` độc lập; spike trước `MON-03/04/05` | Tìm Windows máy B, thử MSI 0.3.11, nghe LT01–LT04, chuẩn bị cắm/rút USB thật + màn hình thứ hai (nếu có), thiết kế UI tokens; proof-of-concept focus/display không tự gửi wire | Ghi Windows/OS/devices/SHA/hash và ACTUAL/NOT RUN đúng thực tế; UI-01 chỉ PR style cô lập; không gọi MOCK là PHYSICAL PASS |
| **C → B/A** | `BASE-03`, `MON-01`, đề xuất `MON-07`, spike `MON-02` | Quyết gate FULL/DELTA trước 20/10; kiểm process pipeline end-to-end; đề xuất typed event `eventId`/scope/dedup/ACK-after-commit; so PnP/WMI/CIM trên Windows nếu có USB vật lý | Gate PASS/FAIL/NOT_DECIDED có evidence; protocol v0 **chưa đổi**; chọn USB API bằng evidence và B review; mở PR integration MON-07 sau A/C duyệt schema |

A/B/C có thể nghiên cứu/mock/prototype cô lập trước dependencies; **không merge** interface trái contract. Owner ghi comment review thật, agent không tạo chữ ký thay người.

## Chuỗi phụ thuộc ưu tiên, không đánh dấu DONE trước

1. **`BASE-01`** đối chiếu live main/PR/CI/hợp đồng 18 task cũ và backlog mới. Không hồi sinh task Phase3 đã code và test.
2. **`BASE-04`** đóng các quyết định kỹ thuật ADR-V2-02/03/04/05 (các policy khác theo từng owner) trước đổi schema/protocol. QD21 deadline và START là blocker nếu chưa thống nhất.
3. **200Q:** `EX-01` → `EX-02` → `EX-04` (Flyway append-only) → `EX-05` (auth image/media) → `EX-06` (JavaFX 7 Part) song song `EX-03` import + `EX-07` catalog/start → **`EX-08` 200Q end-to-end**. Không scrape/publish nội dung có bản quyền khi không có quyền.
4. **Monitoring:** `MON-01` + approved event contract → `MON-07` (typed event WS/PG) → `MON-02` USB, B `MON-03/04/05` focus/window/display → **`MON-09` proctor timeline**. Chỉ quan sát trong ca được phân quyền, no USB file reads, no screenshots.
5. **QA:** `QA-01/02/03/04/05` và `QA-06` có điều kiện gate; `REL-01/02/03/04` theo evidence thật. Tách E2 khỏi Phase 3 khi DELTA không đủ bằng chứng.

## Definition of Ready của mỗi PR V2

- Chỉ một task chính/tính năng hoặc 1 vertical slice liên kết; owner/reviewer và dependency rõ, base SHA cụ thể.
- Khóa DTO, HTTP/WS auth/scope, SQL migration version, transaction/ACK, observer lifetime trước khi code xuyên module; khi chưa khóa thì làm probe trên nhánh cô lập.
- Failing/repro test nếu sửa bug; targeted + module/PG/WS/GUI regression; không xóa test, không weaken assertion.
- Với EX-01 phải có quyền sử dụng các audio/hình/đề; với MON-02 cần USB vật lý; với LAN cần hai Windows thật; không gọi nguồn synthetic là TOEIC bản quyền thật.
- Mọi evidence: source SHA, môi trường, run ID, expected/actual, PASS/FAIL/NOT RUN, người thao tác.
- Code thực nghiệm có review, **không merge** nếu fail CI hoặc thay công khai protocol v0 chưa duyệt.

## 4 rủi ro phải phản ánh trong daily sync

1. **Dataset 200 câu/hình/audio & quyền sử dụng** có sẵn hay không? Thiếu → EX-08 BLOCKED, không tự bịa answer key/nguồn.
2. **Máy Windows B/USB mass storage/monitor thứ hai** có thật? Thiếu → LAN/USB/Display physical NOT RUN, ảnh hưởng P0.
3. **QD-21 START/deadline** và **new event protocol** chưa chốt có thể làm A/B/C tích hợp sai.
4. **Mốc freeze sau 23/10 rất gấp** cho 200 câu và USB. Nếu P0 chưa đủ, chỉ cắt P1 UI polish/idle/rules trước; bất kỳ giảm phạm vi P0 nào phải được nhóm quyết định, không giả nghiệm thu.

## Điều phối và liên kết

- [Entrypoint](../../PLAN_V2_START_HERE.md) · [36 tasks](03_TASK_CARDS.md) · [owners](04_EXECUTION_OWNERSHIP.md) · [technical ADR](07_RISKS_DECISIONS.md).
- [Phase3 physical runbook](../STAGE3_FINAL_HANDOFF.md) · [status/approval record](ADOPTION_STATUS_20261019.md).
- Backlog V2 `TO_AUDIT` phải được A đối chiếu; **không reset** legacy `TRACKER.json` và không gán `DONE` cho 36 task từ lời duyệt kế hoạch.
