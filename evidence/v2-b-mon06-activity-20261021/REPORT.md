# MON-06 exam-local activity — báo cáo 21/10/2026

## Nguồn và phạm vi

- Nhánh: `feat/v2-b-mon06-exam-activity-20261021`.
- Stacked base: MON-05 `66f897c`; main gốc trước stack: `d2c8f01`.
- Owner/reviewer theo Plan V2: B/C; priority P1; reviewer C chưa ký.
- Experiment OFF mặc định. Không thu phím, clipboard, URL, chat, file, question/option ID hay text.

## Kết quả tự động

- Protocol: 4 case activity PASS.
- Adapter/monitor: 9 case PASS về aggregate, interval, idle threshold/reset, clock gap, bounds,
  queue overflow, old callback và lifecycle.
- JavaFX Stage thật: 1 case PASS; navigation + answer hợp lệ phát đúng marker, cùng đáp án không tăng.
- Focused protocol/client/transport/GUI: **94 PASS, 0 failure/error/skip**.
- Full reactor `mvn clean test`: **1378 total = 1232 PASS + 146 SKIP, 0 failure/error**,
  exit 0 (protocol 86, monitoring-spike 35, client 831/27 skip, server 426/119 skip).

Full run mặc định skip JavaFX GUI có gate môi trường và `SignalPipelinePostgresTest`; GUI case MON-06
được chạy riêng với `TOEIC_RICH_GUI=true`. `TOEIC_PG_INTEGRATION` không bật nên 30 case signal PG,
bao gồm case MON-06/V18 mới, là **NOT RUN**, không được tính PASS.

## Đối chiếu acceptance

| Tiêu chí | Bằng chứng | Trạng thái |
|---|---|---|
| Aggregate navigation/answer action | Adapter + JavaFX Stage test | PASS, UI thật/data-gateway MOCK |
| Elapsed no-interaction | Monotonic fixture, interval/threshold/reset | PASS tự động |
| Configurable threshold không kết tội | Strict bounds; reason trung tính, không verdict | PASS source/test |
| No-data/clock gap | Lùi clock/gap lớn → `PARTIAL/CLOCK_GAP`, không idleMillis | PASS tự động |
| Không dữ liệu riêng | Closed event/DTO fields + forbidden-field parser tests | PASS source/test |
| Stop sau session | Handler/Timeline remove, late callbacks ignored | PASS tự động |
| Capability không làm hỏng P0 | Activity chỉ start khi được quảng cáo; core type vẫn required riêng | PASS source/test transport |
| REAL WS/HTTP/PostgreSQL + V18 | Suite đã compile nhưng environment gate tắt | NOT RUN |
| Manual submit/timeout/history | Checklist trong `docs/MON06_ACTIVITY_MONITORING.md` | NOT RUN |
| Human review C | Chưa có chữ ký | NOT RUN |

Không đánh dấu MON-06 DONE cho tới khi REAL PG/WS, manual lifecycle và reviewer C hoàn tất.
