# MON-07 — Kiểm tài liệu/fixture

**PROPOSED — NOT IMPLEMENTED — OWNER REVIEW PENDING.** 19/10/2026.

Source audit/base: `1d35b53729abade7b460d35ab07d042b6f9f3571`.
Phạm vi: [proposal](../MONITORING_MULTISIGNAL_CONTRACT_PROPOSAL.md) và [JSON MOCK](mock-exchanges.json).

## Kiểm tra thực hiện trong phiên

| Kiểm | Kết quả thực tế | Ý nghĩa/giới hạn |
|---|---|---|
| JSON UTF-8 strict/duplicate keys | PASS, 19 case MOCK parse được | Không gửi tới server |
| Required/optional/type/enum/bounds theo bảng đề xuất | 18 request đúng schema; 1 request cố ý có details.serial bị validator phát hiện đúng | Conflict/foreign/COMMIT-failure có schema đúng nhưng expected business khác nhau; không gọi chúng là runtime ACCEPTED |
| ACK/ERROR/correlation | PASS trên 6 exchange có response | Required fields, status/type/eventId, requestId/attemptId/traceId, retryable; duplicate giữ cùng content/receivedAt/sequence, conflict giữ ID đổi details |
| Server record/push/history/capabilities | PASS | Record trùng giữa HTTP/push; cursor/bounds hợp lệ; activity P1 vắng khỏi default capability |
| Markdown local links | 42 đích khác nhau tồn tại | Kiểm mọi link local trong proposal và link tới MON-07 ở các hồ sơ sửa; không nhận là mọi link lịch sử toàn repo đều đã kiểm |
| Source methods | 40 symbol trên 20 file source tồn tại | Đối chiếu file thật tại base SHA, không chứng minh behavior implementation mới |
| Ma trận implementation | 22 hàng đều NOT_RUN | Không tự điền PASS |
| Phạm vi + text/privacy scan và xem diff | 7 file docs/JSON được phép; 0 phát hiện secret/private path/raw/binary lớn | Scan introduced content cho known token/private-key/JWT markers và private paths, UTF-8/NUL/size + xem fixture MOCK; không công bố raw evidence cũ |
| Git | diff whitespace check đạt; chỉ docs/fixture | Production Java/DTO/migration/dependency/config/PROTOCOL và backlog/tracker không đổi |

Checker local dùng Python standard library, nằm ở `target/v2-c-mon07-document-check.py` của checkout gốc, ignored và **không phải file trên GitHub**. Lệnh thực tế từ repo gốc:

```powershell
python target/v2-c-mon07-document-check.py target/v2-c-mon07-contract-proposal
```

Checker kiểm JSON shape/type/time/bounds, ACK/errors, duplicate/conflict, page/record/capability, link/source symbol, trạng thái matrix và allowlist diff. Lần gọi đầu lỗi wrapper do so absolute link target với relative root; đã resolve root rồi chạy lại PASS. Không có lỗi runtime ứng dụng nào được tái hiện/chạy trong task này. Git cảnh báo LF sẽ normalize CRLF theo cấu hình checkout; diff chỉ thêm nội dung mới, không đổi evidence đã hash.

## Đối chiếu GitHub tại đầu phiên

- [PR70](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/70) OPEN/chưa merge; head `9a97cab646af40e45871cb76ffbfcfcb861f2954`, [CI39241246583](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39241246583) completed/success.
- [PR71](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/71) OPEN/chưa merge; head `3b81f4929f736828d112fc5bf456b53499ff1270`, base nhánh PR70, [CI39241297934](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39241297934) completed/success.
- Search MON-07/exact head và multi-signal/multisignal/USB title mọi trạng thái, remote refs/source main: chưa thấy proposal riêng trước khi tạo nhánh này. Không merge/retarget hai PR verification.

## Không thực hiện

Không chạy Maven, PostgreSQL, WS runtime, JavaFX/GUI, USB/display/LAN vật lý hoặc E1/E2. Toàn bộ 22 hàng implementation test matrix giữ NOT_RUN; fixture không chứng minh observer/protocol đã chạy. CI tự động của Draft PR (nếu có) được ghi riêng trên GitHub, không thay physical/owner review.

Không sửa production Java/DTO/SQL/dependency/config hoặc PROTOCOL đang vận hành. Không đổi backlog/tracker, không quyết gate BASE-03, không đánh MON-01/MON-07 DONE. Chưa có human approval A/B/C, chưa merge.
