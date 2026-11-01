# Tài liệu dự án

Hệ thống đã hoàn thành. Thư mục này chỉ còn tài liệu cần để hiểu, chạy, kiểm thử và bảo trì bản hiện tại.

> **Bằng chứng và tài liệu theo task đã được lưu trữ.** Số liệu thực nghiệm vẫn giữ (xem mục "Thực nghiệm"). Phần còn lại của `evidence/`, Plan V2, đề xuất MON/EX, báo cáo QA và hướng dẫn theo vai vẫn còn nguyên trong lịch sử git tại commit `f3ccfd7` (tag cục bộ `archive/pre-slim`). Các link `../evidence/...` hoặc tới file đã gỡ trong nhật ký, tiến độ, kiểm thử, quyết định là bản ghi lịch sử, được giữ nguyên. Xem lại bằng:
>
> ```powershell
> git show f3ccfd7:evidence/qa05-e1-20261021/REPORT.md
> git restore --source f3ccfd7 -- evidence/t3-c3      # khôi phục một thư mục khi cần
> ```

## Thiết kế và giao thức

| File | Nội dung |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Kiến trúc Client–Server, module, luồng chính |
| [PROTOCOL.md](PROTOCOL.md) | Endpoint và message đang dùng thật |
| [BACKEND_API.md](BACKEND_API.md) | Tài liệu tích hợp REST/WebSocket đầy đủ |
| [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md) | Schema PostgreSQL và bất biến dữ liệu |
| [PROCESS_GROUPS.md](PROCESS_GROUPS.md) | Danh sách process được giám sát (policy hiện hành) |
| [QUYET_DINH.md](QUYET_DINH.md) | Các quyết định kỹ thuật (QD-xx) |

## Chạy và vận hành

| File | Nội dung |
|---|---|
| [DEMO-RUNBOOK.md](DEMO-RUNBOOK.md) | Cài, chạy và demo |
| [deploy/CLOUDFLARE-TUNNEL.md](deploy/CLOUDFLARE-TUNNEL.md) | Chạy qua Internet bằng Cloudflare Tunnel |
| [account-pin/RUNBOOK.md](account-pin/RUNBOOK.md) | Luồng ACCOUNT_PIN, phòng thi và runner kiểm thử |
| [phase4/](phase4/), [phase45/ADMIN-DEMO.txt](phase45/ADMIN-DEMO.txt) | Runbook bản portable và khởi tạo Admin |
| [admin-exam-management.md](admin-exam-management.md) | Quản lý đề của Admin (được trích trên slide) |
| [manual-authoring-fix.md](manual-authoring-fix.md) | Tạo đề thủ công (được đóng vào bundle) |
| [READY_RECOVERY_FIX_20261101.md](READY_RECOVERY_FIX_20261101.md) | Phục hồi READY, START với `requireAllReady` và cách nâng cấp server/client |
| [T3_C4_PHYSICAL_LAN_ACCEPTANCE.md](T3_C4_PHYSICAL_LAN_ACCEPTANCE.md) | Nghiệm thu LAN hai máy (được đóng vào LAN kit) |
| [release/](release/) | Báo cáo phát hành và READ-ME của bản portable |

## Thực nghiệm

| File | Nội dung |
|---|---|
| [EXPERIMENTS.md](EXPERIMENTS.md) | Mục lục E1, E2 và các benchmark, kèm link raw/summary |
| [E1_T4C1_MEASUREMENT.md](E1_T4C1_MEASUREMENT.md), [MONITORING_BASELINE_METHOD.md](MONITORING_BASELINE_METHOD.md) | Phương pháp E1 và baseline |
| [MONITORING_MEASUREMENTS.md](MONITORING_MEASUREMENTS.md), [MONITORING_TRACE.md](MONITORING_TRACE.md) | Schema đo, trace và tái tạo summary |
| [MONITORING_DELTA_FAULT_REPLAY.md](MONITORING_DELTA_FAULT_REPLAY.md), [MONITORING_DELTA_LIVE_GATE.md](MONITORING_DELTA_LIVE_GATE.md) | E2: full snapshot so với delta |
| [T3_A5_NETWORK_CONCURRENCY_REPORT_DRAFT.md](T3_A5_NETWORK_CONCURRENCY_REPORT_DRAFT.md) | Mạng và đồng thời |
| [account-pin/IMPLEMENTATION-REPORT.md](account-pin/IMPLEMENTATION-REPORT.md), `account-pin/benchmark/`, `release-acceptance/benchmark/` | Benchmark phòng thi |
| [../evidence/README.md](../evidence/README.md) | Raw và báo cáo gốc của E1/E2 |

## Kế hoạch, kiểm thử, lịch sử

| File | Nội dung |
|---|---|
| [plan-v1/](plan-v1/README.md) | Kế hoạch, hợp đồng và nguồn môn học (không sửa `nguon/`) |
| [KIEM_THU.md](KIEM_THU.md) | Trạng thái test case |
| [TIEN_DO.md](TIEN_DO.md), [NHAT_KY.md](NHAT_KY.md) | Tiến độ và nhật ký từng buổi làm |

## Fixture dùng bởi test và script (không xóa)

- `plan-v2/fixtures/ex02/`: đề MOCK EX-02 (`RichExamContractTest`, `demo-ex06-v2.ps1`).
- `mon07-proposal/*.json`: message MOCK cho `SignalProtocolTest`, `TypedSignalTest`, `SignalPipelinePostgresTest`.
- `templates/t3c4-lan/`: mẫu LAN kit, CI chạy `Test-T3C4LanKit.ps1`.
