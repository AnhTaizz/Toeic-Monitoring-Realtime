# Số liệu thực nghiệm

Chỉ giữ raw, summary và báo cáo của các thực nghiệm. Raw không sửa tay; summary do script sinh. Mục lục và cách đọc ở [docs/EXPERIMENTS.md](../docs/EXPERIMENTS.md).

| Thư mục | Thực nghiệm |
|---|---|
| [qa05-e1-20261021/](qa05-e1-20261021/REPORT.md) | **E1**: chu kỳ polling, chiến dịch QA-05 (raw, analysis, hình, checksum) |
| [c-e1-derived-20261020/](c-e1-derived-20261020/) | E1: số liệu dẫn xuất và receipt |
| [t4-c1/](t4-c1/), [overnight-20261015/phase-02/](overnight-20261015/phase-02/) | E1: review phương pháp T4-C1 và lệnh chạy lịch sử |
| [t3-c3/](t3-c3/) | **E2**: fault replay full snapshot so với delta (`campaign-final/matrix.md`) |
| [t1-c4/](t1-c4/) | Đo monitoring chặng 1 (độ trễ event, heartbeat) |
| [t3-a5/](t3-a5/) | Mạng và đồng thời T3-A5 |

Benchmark phòng thi: `docs/account-pin/benchmark/` (ACCOUNT_PIN) và `docs/release-acceptance/benchmark/` (bản phát hành).

Bằng chứng kiểm thử theo từng task (smoke, verify, ảnh chụp, review) đã gỡ ngày 01/11/2026. Xem lại bằng `git show f3ccfd7:evidence/<đường dẫn>`, hoặc dùng tag `archive/pre-slim`.
