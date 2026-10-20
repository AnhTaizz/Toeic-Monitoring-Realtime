# MON-07 — Slice thử nghiệm DISPLAY_COUNT_OBSERVED

**USER_AUTHORIZED_EXPERIMENT — OWNER/PHYSICAL_ACCEPTANCE_PENDING — NOT_DONE.**

Review tiếp ngày20/10: [findings/fix/verification và hành vi migration OFF](REVIEW-20261020.md), source mới2d73605; số c77/0f9 bên dưới giữ như lịch sử, không gán cho source mới.

## Nguồn ủy quyền và phạm vi

Người dùng trong phiên19/10/2026: “Tôi cho phép triển khai luồng MON-07 thử nghiệm theo mục 11.1 của PR #72 tại c9a5829.” Chỉ áp dụng D07-01–04 cho fixture MOCK → client delivery → REAL WS → PostgreSQL TEST cách ly → ACK sau COMMIT → history phân trang. Đây là **ủy quyền triển khai thử nghiệm của người dùng**, không là chữ ký/approval A/B/C hoặc phê duyệt D07-05.

Base chính xác `c9a5829a022b6810d1d6db2da240d57b95d3ed81` của [PR72](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/72), chưa merge lúc audit; main `1d35b53729abade7b460d35ab07d042b6f9f3571`. Nhánh `feat/v2-c-mon07-signal-pipeline`; PR implementation stacked vào `docs/v2-c-mon07-contract-proposal`, không lặp proposal hoặc merge nguồn.

- Server service/routes chỉ tồn tại khi `toeic.monitoring.signal-experiment-enabled=true`; mặc định tắt. Không push, proctor header/consumer mới, dashboard hoặc hook CandidateMonitoringSession/observer.
- Chỉ type DISPLAY_COUNT_OBSERVED/schema1; capability đúng một type. Client harness explicit confirm sau fresh auth/scope/capability và scoped heartbeat trên current generation; reset khi disconnect/reconnect/đổi lease.
- V13 append-only sau V12; typed common columns/details JSONB, unique attempt/eventId và attempt/sequence, counter atomic cùng transaction. Không áp migration vào DB development.
- Lock order: attempt FOR SHARE → counter FOR UPDATE (writer) → login session/user FOR SHARE → assignment FOR SHARE (proctor reader); fresh auth sau counter wait. Revoke/user UPDATE và assignment DELETE tự xung đột row locks, terminal UPDATE chờ attempt lock. Không sửa mutator/pipeline cũ. Không socket IO trong transaction; ACK sau proxy COMMIT, không push.
- RAM256/1MiB/2flight-write, ACK5s/max5/backoff1/2/4/8s/pump25ms; guard mỗi dispatch, không retry khi write cũ chưa hoàn tất. FAILED/EXHAUSTED tính vào bound; overflow count/time/unconfirmed cục bộ, không phát SIGNAL_GAP/OBSERVER_STATUS chưa advertised.
- Offline60s/retention30ngày **chưa áp dụng**. Không observer/capture thật; TEST schema chỉ có dữ liệu MOCK và được DROP bằng fixture teardown. Không disk queue hoặc purge job production.

## Kiểm chứng trước review c77d8f8

Source **`c77d8f84d00581eef54dddfc5cf7768764f3fa68`**; [summary/cases](verification-c77d8f8.json), [receipt JAR mới](build-receipt-c77d8f8.json), [raw/hash bổ sung](raw-manifest-c77d8f8.json). Bản verification/receipt0f9d8e7 bên dưới giữ nguyên như lịch sử, không là source cuối. JAR/report/receipt0f9d8e7 và7cb7478 đã giữ riêng trong ignored artifact-history; ba JAR0f9d8e7 kiểm exacthash khớp receipt công bố.

| Lượt cuối | Run ID | Kết quả |
|---|---|---|
| Focused | mon07-20261019T152910Z-ec0b47d1 | **219PASS/0FAIL/ERROR/SKIP** |
| Full verify/JAR | mon07-20261019T153108Z-02884fe6 | **1034total=1028PASS/6SKIP/0FAIL/ERROR** |

**44case mới** là subset full: protocol17/delivery9/RealtimeClient3/default-disabled1/**14REAL PG/WS cases**. Hai lượt cuối đều0owned schema/clusterStopExit0. Các giới hạn scope/physical/typed-history>5000/RFC6455malformedframes và6skip vẫn như bên dưới.

Hai regression follow-up có FAIL trước sửa, giữ XML/log/source context:

- Session row lock giữ tới khi DB clock qua expiry: SELECT target-list clock_timestamp() được tính trước LockRows wait nên đã nhận token hết hạn (217tests/1FAIL trên3674633+regression). 7cb7478 đọc clock DB riêng **sau session/user locks**, focused217PASS/full1026PASS+6SKIP; nay cùng14case trong source cuối PASS.
- UNAUTHORIZED trước parse không có request correlation: adapter chuyểnFAILED nhưng delivery chỉ xử lý như disconnect, worker/RAM cònactive. Unit regression trên7cb7478 FAIL; c77d8f8 đóng delivery khi terminalFAILED, xử lý callback lúc khởi tạo an toàn, expose discarded count. Real token revoke kiểm worker tự dừng/không row/đếm1unconfirmed PASS. Không sửa transport legacy/auth mutators hoặc bật observer.

CI exact publication HEAD được theo dõi trong [Draft PR74](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/74); không lấy CI7cb7478 hoặc proposal thay HEAD cuối. Không tự ký A/B/C/MON07DONE/merge.

## Kiểm chứng lịch sử 0f9d8e7

Code/test source của lượt lịch sử **`0f9d8e78275f168cde25f2649ac008301a99b2fb`**. [Summary/case/status](verification.json), [build receipt/hash](build-receipt.json), [raw inventory/hash only](raw-manifest.json).

| Lượt | Source / run | Kết quả thực tế |
|---|---|---|
| Baseline, chưa opt-in PG | c9a5829 / baseline.log | 944total =874PASS/70SKIP,0FAIL/ERROR; BUILD SUCCESS |
| Focused cuối | 0f9d8e7 / mon07-20261019T150944Z-8d6a37ff | **216PASS/0FAIL/ERROR/SKIP**, gồm12REAL WS/PG cases |
| Full verify/build cuối | 0f9d8e7 / mon07-20261019T151101Z-4f839916 | **1031total =1025PASS/6SKIP/0FAIL/ERROR**, package/JAR SUCCESS |

Focused là subset full, không cộng hai tổng. Baseline không PG và disabled parameterized methods có số case khác khi enable; không lấy chênh tổng làm số test mới. 41case mới: protocol17/delivery8/RealtimeClient3/default-disabled1/PG12. Full6skip được liệt kê nguyên tên trong verification.json: symlink/sidecar, FX dialog, installed/app-image/proctor GUI opt-ins; không nhận GUI/physical PASS.

Windows build26200, Java21.0.10, Maven3.9.15, PostgreSQL18.3 (đọc từ runner thực tế). Hai lượt cuối đều **0owned schema còn lại, clusterStopExit0**, password file xóa/process env restored. Không reset DB dev hoặc volume. Server JAR có SignalService/SignalController/V13; receipt pin source và SHA256 ba JAR local, không claim đã launch riêng java -jar.

12PG/WS cases kiểm fixture → delivery → commit/ACK → history; ACK loss SIMULATED tại adapter/harness sau adapter nhận ACK thật, duplicate giữ1row/1sequence; actual disconnect/reconnect cần capability/scoped heartbeat mới và giữ origin cũ; strict duplicate-key/unknown/Unicode escape/byte cap; wrong-role/foreign/terminal/assignment/expired/revoked, kể cả duplicate; deferred COMMIT failure rollback cả row/counter, không success ACK; fixed-through pagination, concurrent initial counter/rollback, revoke trong counter wait và row-lock serialization với revoke/terminal/unassign. Không có push mới trong source; proctor socket kiểm không nhận MONITOR_SIGNAL.

**Failures/giới hạn giữ nguyên:**

- Runner lần đầu FAIL trước tạo cluster do ScriptBlock không có PSScriptRoot; thêm fallback repo root, không đổi ExecutionPolicy.
- Focused cb37ea5 runtime212PASS nhưng original result.json lọc nhầm XML EX04 cũ; **summary đó INVALID**, giữ nguyên raw/log/result và note. Runner sửa filter theo selected suite + modification time; 5887b58 focused212PASS và hai lượt cuối có summary đúng source.
- Review tìm integer getAsLong wrap (18446744073709551617→1) và capability run cũ được dùng lại sau đổi heartbeat lease cùng socket. Hai regression thêm trên5887b58 đã FAIL trước sửa, XML/log retained. 0f9d8e7 dùng Long.parseLong, giới hạn clock00..23 và expectedCollector + confirmed collector/generation; regression cuối PASS. Không lấy rerun PASS làm root cause.
- Compilation-only khi chưa bật PG có9caseSKIP ở source cũ, không PG PASS. Parser malformed UTF-8 được UNIT kiểm; wire test có lone-surrogate escape/oversized JSON, chưa test RFC6455 malformed UTF-8 frames.
- Typed history paging/concurrency dùng fixture nhỏ; >5000typed rows chưa chạy (legacy process/gap/interruption5000/5001 có regression riêng). Không nâng toàn22hàng proposal thành PASS; push/gap/status/observer/GUI/physical vẫn ngoài slice.

CI HEAD publish sẽ ghi đúng SHA/run trong description Draft PR; CI không thay12PG cases local hoặc owner acceptance.

Runner `scripts/verify-mon07-postgres.ps1` nhận PostgresBin/JavaHome/MavenRepository/OutputRoot, tạo cluster SCRAM/port loopback/database riêng, set process environment tạm, chạy tests, copy XML/hash, đếm schema còn lại, stop cluster và xóa password file. Không đọc .env/devDB, reset/xóa volume hay root clean. OutputRoot phải ngoài target của reactor để bảo toàn logs; logs/raw/XML private ignored, không GitHub downloads.

## Review và giới hạn

A/C cần review schema/lock order/auth/transaction/cursor; B review RealtimeClient admission/generation/cleanup. Chưa tạo chữ ký hoặc tự approve. Fixture MOCK và WS/PG loopback không là sensor, LAN, USB hotplug, E1/E2 hay hardware acceptance. Giữ FULL default, BASE03 NOT_DECIDED, MON01 technical verified/physical-B-BASE02pending, MON02 physical0/12NOT_RUN. MON07 chưa DONE; không merge.


## Remote audit cuối / bàn giao

Trong lúc chạy test, main tiến tới `3bdefe6ccc346a316a91f1e280fe4f5c74600cfd` (PR67 docs/fixture EX02); diff protocol/client/server/scripts/pom so với1d35b537 rỗng, Flyway main vẫnV12, không có collisionV13 tại lúc kiểm. Không lấy approval EX02 làm approval MON07. Proposal PR72 vẫnc9a5829/chưa merge; implementation giữ đúng base c9a5829 và Draft stacked, không merge/rebase để kéo thay đổi mới. Trước integration, owner cần reconcile docs/main/proposal và kiểm migration version lại.

Đã hoàn thành slice thử nghiệm kỹ thuật trong phạm vi user giao; MON07 tổng thể vẫn NOT_DONE/owner-physical review pending. Không sửa PR70–73 khác, dashboard, observer production hoặc D07-05.
