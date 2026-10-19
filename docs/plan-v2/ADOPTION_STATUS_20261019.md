# Plan V2 adoption status — 19/10/2026

**TEAM_SCOPE_APPROVED / IMPLEMENTATION_KICKOFF.** Ngày 19/10/2026, người đại diện nhóm xác nhận **cả nhóm đã phê duyệt Plan V2 tối 18/10/2026**. Đây là lời xác nhận trong cuộc trao đổi, **không phải chữ ký hoặc biên bản độc lập của A/B/C**. Chấp thuận **phạm vi** khác với approval từng schema, endpoint, runtime OS API, code PR và kết quả test.

## Baseline sau import

- Source tại thời điểm kích hoạt: `main 20d119f5732022f387be8b31e8153489418c859c`; HEAD khi bắt đầu mỗi task phải fetch lại.
- CI `main` ở HEAD này đã SUCCESS (run [39186372875](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39186372875)).
- Phase 3: **`CODE_READY_STAGE3_ACCEPTANCE_PENDING`**, không phải `STAGE3_DONE`. PR #47 (monitoring CLOSE) và #57 (audio cache publication) đã merge; các P1 đã biết được fix trong phạm vi đã kiểm.
- Cần nghiệm thu thật: **LAN vật lý 0/41**, người B nghe LT01–LT04, MSI 0.3.11 máy thứ hai, C/B chốt FULL/DELTA và signoff đúng người. Các kiểm thử local/loopback/MediaPlayer **không** chứng minh việc này. Xem [handoff](../STAGE3_FINAL_HANDOFF.md).
- `docs/plan-v2/BACKLOG.json` và CSV gồm **36 task** đang ở trạng thái cần `BASE-01` đối soát, không tự đánh dấu DONE.

## Phạm vi V2 đã được nhóm duyệt

1. **EX-08**: 1 đề 200 câu/7 Part hợp pháp, đủ ảnh/audio/passage/group, phân quyền, dữ liệu không lộ answer key cho candidate; đi từ DB, HTTP, JavaFX đến kết quả.
2. **MON-09**: process + **USB mass-storage presence/connect/disconnect**, focus/minimize/fullscreen và display change, với lịch sử và giao diện giám thị đúng scope.
3. Nghiệm thu real-device (LAN, USB, MSI, nghe thật), E1/conditional E2, report/source bàn giao trước 31/10.
4. Giữ Java21/Spring Boot/JavaFX/PostgreSQL/WS, không thêm webcam/keylogging/read USB files/OS locks. Không thu thập serial USB hoặc thông tin riêng không cần thiết.

Phụ lục giới hạn của phạm vi này đã thêm vào [`CLAUDE.md`](../../CLAUDE.md) và [hợp đồng legacy](../plan-v1/02_HOP_DONG.md). Không sửa bản gốc giảng viên hoặc làm mất lịch sử 5 chặng.

## Các checkpoint kỹ thuật vẫn phải chốt bằng quyết định của owner

| Mã | Phạm vi được duyệt | Chi tiết cần A/B/C review/ghi lại trước khi sửa code liên quan |
|---|---|---|
| ADR-V2-01 | Chỉ **quan sát** USB mass-storage | A/C xác nhận disclosure/retention và scope; B xem UI. Không đọc/chặn thiết bị |
| ADR-V2-02 | 200Q/media | A/B duyệt nguồn có quyền, ordered content blocks, endpoint auth, image root, migration |
| ADR-V2-03 | Proctor START chuẩn | A/B quyết QD-21 deadline ở takeover hay start và các cổng quyền save/submit |
| ADR-V2-04 | Multi-signal events | C/A duyệt typed event ID/ACK/dedup/limits, v0 compatible, auth/persistence |
| ADR-V2-05 | USB sensor | C/B chọn PnP hay WMI/CIM sau probe trên Windows và thiết bị thật |
| ADR-V2-06 | Parser offline | A dùng Java mặc định; chỉ cho phép Python parser sau duyệt riêng |
| ADR-V2-07 | Release branch | A/C đối soát `main`/PR/test SHA đúng nguồn, không reset tracker |
| ADR-V2-08 | Evidence/novelty | C/A giữ FULL nếu DELTA gate chưa có bằng chứng đúng; không nhận E2 fake |

Không điền `APPROVED` từng ADR hoặc `OWNER_SIGNED` nếu chưa có evidence của chính owner. Các Task `BASE-01`, `BASE-04` được giao khởi động trong [kickoff](V2_EXECUTION_KICKOFF_20261019.md).

## Mốc và cách bắt đầu

- **20/10:** C/B quyết DELTA gate; thiếu LAN/MT evidence thì giữ FULL mặc định và ghi NOT_DECIDED/FAIL đúng thực tế.
- **Sau 23/10:** V2 target feature freeze (thay ưu tiên mốc 27/10 legacy đối với tính năng mới). Nếu P0 còn blocker, nhóm phải review phạm vi/hậu quả chứ không giả DONE.
- **30/10:** nội bộ handoff; **31/10 23:59 ICT:** deadline chính thức.

A/B/C đọc [kickoff](V2_EXECUTION_KICKOFF_20261019.md), chọn các task không bị dependency chặn và mở feature PR nhỏ. Agent không tự ký review, không tạo chứng nhận LAN/USB/media quyền sử dụng. Nhãn V2 “approved” **không** đồng nghĩa 36 tasks đã thực thi.

### Hồ sơ nhập

Gói gốc do người dùng cung cấp: `Toeic_Monitoring_Plan_V2_Agent_20261018(1).zip` (18/10). Đợt nhập đầu sửa control character tại link `03_TASK_CARDS.md`, chuẩn hóa CSV và tái tạo SHA256 không tự tham chiếu. Các task spec và `BACKLOG.csv/json` không đổi trong lượt kích hoạt này.
