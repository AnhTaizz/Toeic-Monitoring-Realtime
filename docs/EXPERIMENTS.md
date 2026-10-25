# Thực nghiệm và kết quả

Mục lục tới các thực nghiệm đã chạy thật. Trang này **không chép lại hay làm tròn số liệu**; số liệu, cấu hình, SHA, máy và seed nằm trong báo cáo gốc. Raw không sửa tay; summary do script sinh. Phương pháp và tiêu chí ở [plan-v1/03_KIEM_THU_VA_THUC_NGHIEM.md](plan-v1/03_KIEM_THU_VA_THUC_NGHIEM.md).

| Thực nghiệm | Câu hỏi | Kết quả và raw | Ghi chú |
|---|---|---|---|
| **E1** — chu kỳ polling | Poll 250/500/1000/2000 ms bỏ sót process ngắn bao nhiêu, tốn bao nhiêu CPU | Chiến dịch QA-05: [evidence/qa05-e1-20261021/REPORT.md](../evidence/qa05-e1-20261021/REPORT.md) (analysis, hình, checksum); phương pháp [E1_T4C1_MEASUREMENT.md](E1_T4C1_MEASUREMENT.md), [MONITORING_BASELINE_METHOD.md](MONITORING_BASELINE_METHOD.md) | Một máy lab, client giả lập có kiểm soát; xem giới hạn trong báo cáo |
| **E2** — full snapshot so với delta | Delta có lợi về byte/độ đúng khi có lỗi mạng không | Fault replay T3-C3: [MONITORING_DELTA_FAULT_REPLAY.md](MONITORING_DELTA_FAULT_REPLAY.md), [evidence/t3-c3](../evidence/t3-c3); gate live: [MONITORING_DELTA_LIVE_GATE.md](MONITORING_DELTA_LIVE_GATE.md) | Bản phát hành giữ **full snapshot mặc định**, delta tắt (`--toeic.state.delta-enabled=false`) |
| Đo monitoring T1-C4/T2 | Độ trễ event, heartbeat, gap | [MONITORING_MEASUREMENTS.md](MONITORING_MEASUREMENTS.md), [MONITORING_TRACE.md](MONITORING_TRACE.md) | Lịch sử chặng 1–2 |
| Benchmark phòng thi Phase 4 | Tải nhiều Candidate trên phòng V2 | [phase4/PHASE4-REPORT.txt](phase4/PHASE4-REPORT.txt), [phase4/VERIFICATION-METHOD.txt](phase4/VERIFICATION-METHOD.txt) | Trước ACCOUNT_PIN |
| Benchmark ACCOUNT_PIN | Tải JOIN/READY/START với PIN chung | [account-pin/benchmark/summary.json](account-pin/benchmark/summary.json), [account-pin/IMPLEMENTATION-REPORT.md](account-pin/IMPLEMENTATION-REPORT.md#account_pin-benchmark) | Source ACCOUNT_PIN trước tích hợp; không thay cho kiểm thử bản phát hành |
| Mạng và đồng thời T3-A5 | Nhiều client, transaction | [T3_A5_NETWORK_CONCURRENCY_REPORT_DRAFT.md](T3_A5_NETWORK_CONCURRENCY_REPORT_DRAFT.md) | Bản nháp |

Kiểm thử của bản phát hành hiện tại (regression, portable, nâng cấp DB) không phải thực nghiệm; xem [release/RELEASE-REPORT.md](release/RELEASE-REPORT.md).

Phân biệt khi đọc: client giả lập khác desktop thật; không trừ timestamp của hai máy chưa đồng bộ; kết quả âm được giữ nguyên như đo.
