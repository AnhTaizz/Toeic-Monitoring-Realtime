# MON-07 — Slice thử nghiệm DISPLAY_COUNT_OBSERVED

**USER_AUTHORIZED_EXPERIMENT — OWNER/PHYSICAL_ACCEPTANCE_PENDING — NOT_DONE.**

## Nguồn ủy quyền và phạm vi

Người dùng trong phiên19/10/2026: “Tôi cho phép triển khai luồng MON-07 thử nghiệm theo mục 11.1 của PR #72 tại c9a5829.” Chỉ áp dụng D07-01–04 cho fixture MOCK → client delivery → REAL WS → PostgreSQL TEST cách ly → ACK sau COMMIT → history phân trang. Đây là **ủy quyền triển khai thử nghiệm của người dùng**, không là chữ ký/approval A/B/C hoặc phê duyệt D07-05.

Base chính xác `c9a5829a022b6810d1d6db2da240d57b95d3ed81` của [PR72](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/72), chưa merge lúc audit; main `1d35b53729abade7b460d35ab07d042b6f9f3571`. Nhánh `feat/v2-c-mon07-signal-pipeline`; PR implementation stacked vào `docs/v2-c-mon07-contract-proposal`, không lặp proposal hoặc merge nguồn.

- Server service/routes chỉ tồn tại khi `toeic.monitoring.signal-experiment-enabled=true`; mặc định tắt. Không push, proctor header/consumer mới, dashboard hoặc hook CandidateMonitoringSession/observer.
- Chỉ type DISPLAY_COUNT_OBSERVED/schema1; capability đúng một type. Client harness explicit confirm sau fresh auth/scope/capability và scoped heartbeat trên current generation; reset khi disconnect/reconnect/đổi lease.
- V13 append-only sau V12; typed common columns/details JSONB, unique attempt/eventId và attempt/sequence, counter atomic cùng transaction. Không áp migration vào DB development.
- Lock order: attempt FOR SHARE → counter FOR UPDATE (writer) → login session/user FOR SHARE → assignment FOR SHARE (proctor reader); fresh auth sau counter wait. Revoke/user UPDATE và assignment DELETE tự xung đột row locks, terminal UPDATE chờ attempt lock. Không sửa mutator/pipeline cũ. Không socket IO trong transaction; ACK sau proxy COMMIT, không push.
- RAM256/1MiB/2flight-write, ACK5s/max5/backoff1/2/4/8s/pump25ms; guard mỗi dispatch, không retry khi write cũ chưa hoàn tất. FAILED/EXHAUSTED tính vào bound; overflow count/time/unconfirmed cục bộ, không phát SIGNAL_GAP/OBSERVER_STATUS chưa advertised.
- Offline60s/retention30ngày **chưa áp dụng**. Không observer/capture thật; TEST schema chỉ có dữ liệu MOCK và được DROP bằng fixture teardown. Không disk queue hoặc purge job production.

## Kiểm chứng

Baseline Maven trên proposal c9a5829 đã chạy SUCCESS; focused protocol/delivery/RealtimeClient ban đầu SUCCESS. Compilation-only với PostgreSQL chưa bật có testSKIP, **không là PG PASS**. Kết quả có source/runID/hash sẽ cập nhật sau runtime đầy đủ; chưa gán PASS cho ca chưa chạy.

Runner `scripts/verify-mon07-postgres.ps1` nhận PostgresBin/JavaHome/MavenRepository/OutputRoot, tạo cluster SCRAM/port loopback/database riêng, set process environment tạm, chạy tests, copy XML/hash, đếm schema còn lại, stop cluster và xóa password file. Không đọc .env/devDB, reset/xóa volume hay root clean. OutputRoot phải ngoài target của reactor để bảo toàn logs; logs/raw/XML private ignored, không GitHub downloads.

## Review và giới hạn

A/C cần review schema/lock order/auth/transaction/cursor; B review RealtimeClient admission/generation/cleanup. Chưa tạo chữ ký hoặc tự approve. Fixture MOCK và WS/PG loopback không là sensor, LAN, USB hotplug, E1/E2 hay hardware acceptance. Giữ FULL default, BASE03 NOT_DECIDED, MON01 technical verified/physical-B-BASE02pending, MON02 physical0/12NOT_RUN. MON07 chưa DONE; không merge.
