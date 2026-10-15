# Mục lục bằng chứng và log

`evidence/<task>/` là nơi tập trung bằng chứng được đưa lên GitHub để thành viên nhóm và công cụ chat web đối chiếu code, kết quả kiểm thử và giới hạn nghiệm thu. Mỗi dòng bên dưới dẫn tới báo cáo của task; báo cáo dẫn tiếp tới log, ảnh, raw, metadata và checksum tương ứng.

Để hiểu dự án trước khi viết prompt, đọc [tiến độ](../docs/TIEN_DO.md), [protocol](../docs/PROTOCOL.md) và [trạng thái kiểm thử](../docs/KIEM_THU.md), rồi mở bằng chứng đúng task. Kết quả thuộc SHA và môi trường ghi trong báo cáo, không mặc nhiên áp dụng cho commit mới hơn.

## Chặng 1

| Task | Nội dung | Báo cáo |
|---|---|---|
| Khởi tạo | Build, DB, login, process probe, package local | [Smoke](stage1/2026-10-03-skeleton-smoke.md), [kiểm tra trước merge](stage1/2026-10-03-merge-readiness.md) |
| T1-A2 | Xác thực REST và WebSocket | [Verification](t1-a2/2026-10-04-verification.md) |
| T1-A3 | Lưu event, ACK và cảnh báo | [Verification](t1-a3/2026-10-04-verification.md) |
| T1-A4 | Heartbeat, presence và lịch sử gián đoạn | [Verification](t1-a4/2026-10-04-verification.md) |
| T1-B2 | Adapter mạng và reconnect | [Verification](t1-b2/2026-10-04-verification.md), [tích hợp thật](t1-b2/2026-10-04-real-integration.md) |
| T1-B3 | Dashboard giám thị | [Verification](t1-b3/2026-10-04-verification.md) |
| T1-C2 | Collector ProcessHandle | [Verification](t1-c2/2026-10-04-verification.md) |
| T1-C3 | Queue, retry và gửi event | [Verification](t1-c3/2026-10-04-verification.md) |
| T1-C4 | Log đo, summary và khảo sát nguồn | [Verification](t1-c4/2026-10-05-verification.md) |

## Chặng 2

| Task | Nội dung | Báo cáo |
|---|---|---|
| T2-A1 | Import đề, tạo ca và lượt thi | [Verification](t2-a1/2026-10-05-verification.md) |
| T2-A2 | Autosave, revision và idempotency | [Verification](t2-a2/2026-10-05-verification.md) |
| T2-A3 | Submit, timeout và chấm điểm | [Verification](t2-a3/2026-10-05-verification.md) |
| T2-A4 | Takeover và tranh chấp PostgreSQL | [Verification](t2-a4/2026-10-05-verification.md) |
| T2-A5 | Giao dịch và bằng chứng quyền | [Verification](t2-a5/2026-10-05-verification.md) |
| T2-C1 | Full snapshot và process hiện tại | [Verification](t2-c1/2026-10-10-verification.md), [sửa scheduler và kiểm tra tự STALE/TTL](t2-c1/scheduling-fix/2026-10-10-verification.md) |
| T2-C2 | Event gửi bù và lịch sử gap | [Verification](t2-c2/2026-10-10-verification.md) |
| T2-C3 | Observation trace, replay và oracle | [Verification](t2-c3/2026-10-10-verification.md) |
| T2-C4 | Controlled process harness và ground truth độc lập | [Verification](t2-c4/2026-10-10-verification.md), [log kiểm tra](t2-c4/logs/) |
| T2-C5 | Phương pháp baseline/protocol và kiểm tra tài liệu | [Verification](t2-c5/2026-10-11-verification.md), [kiểm tra source/link/hash](t2-c5/doc-checks.json) |

| T3-C1 | Encoder delta candidate/ACKed base và replay oracle | [Verification](t3-c1/2026-10-11-verification.md), [artifact audit](t3-c1/artifact-audit.json) |

| T3-C2 | Reducer production, resync, capability và scan freshness | [Verification](t3-c2/2026-10-12-verification.md) |

## Cách lưu log mới

T3-B1: [Tải/checksum/thử loa/READY phía client](t3-b1/2026-10-14-verification.md), [metadata và kết quả](t3-b1/metadata.json). Java657 PASS với PG thật; app-image Unicode 6 checks PASS. Human A review/xác nhận nghe thủ công/máy thứ hai chưa chạy; T3-B1 hiện code complete trên branch riêng.

T3-B2: [Phát Listening theo lệnh server](t3-b2/2026-10-14-verification.md), [metadata](t3-b2/metadata.json). Java666 PASS với PG thật; app-image Unicode 7 checks PASS, gồm Media WAV và duplicate start không replay. Gián đoạn/reconnect B3, human A review/LAN/máy thứ hai chưa chạy.

T3-B3: [Gián đoạn và mở lại Listening](t3-b3/2026-10-14-verification.md), [metadata](t3-b3/metadata.json). Java669 PASS với PG thật; app-image Unicode 8 checks PASS, gồm MediaPlayer dừng khi disconnect, marker NETWORK_LOST và reconnect không replay. Review A/rút mạng/kill app thủ công/LAN/máy thứ hai chưa chạy.

T3-A1: [API audio/READY và gate start](t3-a1/2026-10-13-verification.md), [19 test PostgreSQL](t3-a1/postgres-results.json). T3-B1 đã hoàn tác; playback/WS/gián đoạn Listening thuộc các task kế tiếp.

- Lưu theo task trong `evidence/<task>/`; một lượt chạy bổ sung có thể dùng thư mục con riêng. Thêm báo cáo và liên kết vào mục lục này khi có bằng chứng mới.
- Transcript build/test dùng `.txt`; dữ liệu đo dùng JSONL/JSON/CSV; ảnh dùng PNG. Các định dạng này có thể commit theo cấu hình Git hiện tại. Log chạy local trong `logs/`, file `*.log`, `target/` và `/traces/` vẫn là dữ liệu tạm.
- Báo cáo ghi SHA, môi trường, cấu hình, lệnh, kết quả thật, giới hạn và đường dẫn artifact. Phân biệt REAL, MOCK, SIMULATED và REPLAY; giữ cả kết quả lỗi giúp giải thích thay đổi.
- Giữ raw nguyên bản và checksum theo quy ước của từng task. Summary phải sinh từ script; nếu nén artifact lớn, ghi cách giải nén và checksum dữ liệu gốc.
- Kiểm nội dung trước khi commit: không đưa mật khẩu, token, `.env` hoặc dữ liệu riêng tư vào bằng chứng. Mục lục dẫn tới báo cáo có thể đọc trực tiếp trên GitHub; artifact nén cần tải và giải nén để xem chi tiết.

Mục lục chỉ tổ chức bằng chứng đã có, không xác nhận task hoàn thành hoặc thay đổi trạng thái trong tracker.

- T3-C3: [Replay delta faults và campaign](t3-c3/2026-10-12-verification.md) —3MOCKworkloads/72cases/4negative/deterministic reports, full default; ACK-loss/live gate/E1/E2 chưa chạy.

- **T3-C4 ·13/10/2026:** [candidate thật/live preparation và final evidence](t3-c4/2026-10-13-verification.md):579Java/42localassertions/7regression commands latestPASS; failures/raw/checksums retained; FULLdefault, LAN/humanB/gate20/10 NOT DECIDED, noE1/E2.

- Backend A, PR #23: [đồng bộ main T3-C3 và review lại P0](t2-a-p0-sync-20261013/2026-10-13-verification.md) — `c64503f` (sau hai lần đồng bộ main), Maven 596, smoke C1/C2, Python 10 PASS; human review, GUI, LAN chưa chạy.

- T3-B5: [MSI Windows/per-user/runtime đi kèm và local verification](t3-b5/2026-10-15-verification.md) — source `acac38d`, clean687 PASS, MSI install/launch/B4/FULL/audio/close/uninstall PASS; IT01 PARTIAL, máy hai/LAN/nghe thật/A review NOT RUN. MSI/hash/metadata chỉ ở ignored target, không commit binary.

- B-ACCEPT-01 phía A: [API lịch sử lượt thi của thí sinh](t2-a-history/2026-10-12-verification.md) — 592 test với PostgreSQL thật, kiểm chéo client B `770c896` 13/13; nhánh cục bộ chưa merge, GUI của B chưa chạy.
- API lịch sử, nhánh để mở PR: [xếp lại trên PR #23 đã đồng bộ](t2-a-history/2026-10-13-restack.md) — `19f669e`, 602 test PASS, không lặp commit P0.


## Tích hợp Reading/Listening/history —15/10/2026

[Verification mới](integration/2026-10-15-verification.md):833tests/0skip; actual GUI8nhóm; ExamFlow28; concurrency/monitoring regression; source/JAR/log hashes và6ảnh. Human/LAN/E1/E2 NOT RUN; FULL default; không nâng tracker/gate từ số unit tests.
