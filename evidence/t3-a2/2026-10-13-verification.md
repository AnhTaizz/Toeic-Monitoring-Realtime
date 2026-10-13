# T3-A2 verification · 13/10/2026

Yêu cầu: tiếp tục T3-A2 sau T3-A1. Nhánh `feat/t3-a2-listening-lifecycle`; base main8894452 (PR #29), cùng tree với A1 f6e94db. Commit tách riêng: DTO/schema674fe8c, backend7c7b4b3, test56a0183, docsaa56ccb; evidence là commit tiếp theo. Contract: [LISTENING_LIFECYCLE.md](../../docs/LISTENING_LIFECYCLE.md). Human C review, client adapter/media/GUI và LAN chưa chạy.

## Kết quả mới

| Lượt | Kết quả | Log |
|---|---|---|
| Focused PostgreSQL sau sửa timestamp | PASS27, 0 skip | [postgres-second.txt](postgres-second.txt) |
| Thêm restart Spring và worker thật | PASS28, 0 skip | [postgres-final.txt](postgres-final.txt) |
| Thêm upgrade V9→V10 và deadline đã có | PASS29, 0 skip | [postgres-upgrade.txt](postgres-upgrade.txt) |
| Full package + PostgreSQL bật | PASS629, 0 fail/error/skip, 17:25:55 UTC+7 | [package-final.txt](package-final.txt) |

Full package: protocol43, client386, server199, monitoring-spike1. Từ XML Surefire mới: [package-results.json](package-results.json). PostgreSQL subset29 = Listening10 + audio8 + exam11: [postgres-results.json](postgres-results.json). Script `scripts/smoke-t3a2.ps1 -FullBuild -JavaHome <JDK21>` dùng Microsoft OpenJDK21.0.9/Maven3.9.16/offline cache; focused bỏ `-FullBuild`.

REAL: production Spring/Flyway/JDBC/HTTP/WebSocket, local PostgreSQL18, schema UUID riêng và WAV fixture trong đường dẫn có dấu/khoảng trắng. Gián đoạn MEDIA_ERROR/NETWORK_LOST được gửi có chủ đích, heartbeat aging bằng SQL và deferred trigger là fault injection; không phải loa/lỗi media/mất gói vật lý thật. Spring restart và worker timeout tự chạy thật trên cùng schema/auth; upgrade test migrate V9 rồi V10 trên schema riêng có ca đã start.

## Phạm vi được kiểm

- Start push sau COMMIT, đúng owner/scope, giám thị không nhận command; retry giữ startId/deadline, không phát lại sau STARTED hoặc reconnect. Deadline có sẵn giữ nguyên, deadline null được khởi tạo theo thời lượng ca.
- Deferred COMMIT failure rollback start/event: không start command, không ACK thành công, không cờ gián đoạn giả.
- LT04 backend: cờ đầu tiên bền vững; save/submit chặn thêm/sửa/xóa Listening, Reading được sửa và nộp; score/status/submit retry giữ interruption marker.
- Heartbeat Listening đúng start/writer; late heartbeat không resume; worker scan và save gate trước scan đều chặn; COMPLETED không bị timeout hoặc start lại.
- Takeover/restart không resume; old answers/deadline giữ nguyên. Actual Spring restart giữ login DB, worker mới đánh dấu lượt im lặng.
- Chỉ assigned proctor tổ chức lại; candidate/foreign proctor bị từ chối. Concurrent/retry cùng request tạo một replacement, request khác conflict; replacement có answers rỗng/deadline null/READY-start riêng. Old timeout chấm saved answers kèm marker, history vẫn đọc được.
- V9→V10 giữ old answers/writer/revision/deadline và gắn SERVER_RESTART cho lượt active đã start. Events/status kiểm owner/role/writer/start ID/payload, WS writer số lẻ bị từ chối.
- Regression toàn protocol/client/server/spike, audio A1 và exam P0 PASS. Không thay production client, monitoring presence/state/reducer hoặc migration V1–V9.

LT02/LT03/LT04 toàn hệ thống vẫn PARTIAL trong bảng kiểm thử; phần server đã đạt, media/GUI/B adapter và LAN chưa kiểm. Không đổi tracker/kế hoạch, không giả human C approval hoặc ghi giờ chưa đo.

## Lượt lỗi được giữ

- [postgres-first.txt](postgres-first.txt): 27 test, 11 failures +1 error. Phép cộng timestamp bind với interval khi đặt deadline làm start trả500. Sửa `CAST(:at AS TIMESTAMPTZ)`; regression/focused/full package sau đó PASS.
- [package-postgres.txt](package-postgres.txt): stdout dừng ở measurement test; exec session báo `mvn.cmd : MEASUREMENT_WRITER_FAILED raw log incomplete; monitoring unchanged`. PowerShell5 `ErrorActionPreference=Stop` đã dừng reactor tại stderr warning cố ý của negative test. Script hiện xét exit code Maven, giữ stderr trong log, khôi phục preference sau call. Full package mới PASS629. Đây là lỗi wrapper, không ghi lượt này là PASS; exception của wrapper nằm trong tool result, không nằm trong stdout file.

## Đối chiếu artifact

[source-audit.json](source-audit.json) ghi SHA25622 file code/schema/config/test/script đã kiểm, normalized LF SHA256 cho Git text conversion, checksum6 transcript và4 JAR. XML totals được sinh từ báo cáo Surefire của full package, không cộng từ số test dự kiến. Transcript đổi UTF16 PowerShell sang UTF8 và che các chuỗi trùng credential local (kể cả tên property thư viện): [redactions.json](redactions.json); hash là hash bytes sau bước này. Raw `.log` local bị gitignore, không đưa lên Git. Git attributes giữ exact bytes evidence khi checkout.

[committed-code-audit.json](committed-code-audit.json) xác nhận 22/22 Git blob khớp source đã test, 6 transcript và4 JAR giữ checksum. Sau build chỉ ghi docs/evidence và commit, không sửa code/test/script. Source-audit là snapshot trước commit; nhánh sau đó fast-forward từ f6e94db sang main8894452 có cùng tree rồi tạo các commit trên. Remote/push cuối xem Git history và bàn giao, không dùng kết quả T3-A1/C cũ thay test mới này.
