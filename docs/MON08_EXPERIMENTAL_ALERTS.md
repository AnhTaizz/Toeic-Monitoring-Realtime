# MON-08 — Nhóm ưu tiên giám thị xem xét (EXPERIMENTAL)

Slice C+B ngày 21/10/2026, theo ủy quyền người dùng implementation/test/push/PR/merge commit nếu CI và quy định đạt. Không ký thay A/B/C, không policy APPROVED hoặc MON-08 DONE. [Báo cáo/ảnh/receipt](../evidence/cb-mon08-alerts-20261021/REPORT.md). FULL mặc định; cả signal experiment và MON-08 mặc định OFF.

## Demo một lệnh

Windows có desktop JavaFX, JDK21 (`JAVA_HOME`), Maven trên PATH, PostgreSQL18 binaries:

```powershell
.\scripts\demo-mon08-alerts.ps1
# Tùy máy:
.\scripts\demo-mon08-alerts.ps1 -PostgresBin 'C:\Program Files\PostgreSQL\18\bin' -ViewSeconds 60
```

Runner tái sử dụng cluster/schema TEST cách ly, SCRAM/password stdin, seeder/import/create-session API của demo MON-09/USB. Hai attempts MOCK, một assigned proctor; không `.env`/DB dev. Fixture mở **Timeline đa tín hiệu**, giữ cửa sổ mặc định20s (0–120) rồi dọn TEST. Có thể chọn nhóm, xem chi tiết/evidence và bấm **Xem history gốc của nhóm**, chọn attempt/nguồn trong khoảng chờ; fixture khôi phục selection để tiếp tục kiểm. Maven/build trước cửa sổ có thể mất thời gian. `-OutputDirectory` phải là thư mục mới dưới checkout `target`, không ghi đè log cũ. TEST override source-cap=20 để chứng minh truncation bằng30 typed events; không thay default200. MOCK là nguồn quan sát, WS/HTTP/PG/JavaFX là implementation thật. Không gọi controls/Scene là OS mouse/keyboard hay human acceptance. Ctrl+C chưa nghiệm thu riêng; chỉ gọi run PASS khi receipt cleanup thành công.

## Policy đã có và phần thử nghiệm

Đã có `process-policy-v1`/`FullSnapshotPayload.EXECUTABLES` để chọn process: chrome/msedge/firefox/zalo/teams/discord/anydesk/teamviewer.exe. Đây không phải policy ưu tiên MON-08. Không tìm thấy ngưỡng MON-08 APPROVED trong repository; **toàn bộ bảng dưới và các ngưỡng là EXPERIMENTAL do người dùng cho phép lựa chọn để thử**. INFO/REVIEW/HIGH_ATTENTION là ưu tiên xem xét, không độ chắc chắn hành vi hoặc điểm số.

| ruleId | Đầu vào/điều kiện | Ưu tiên và ý nghĩa | Thiếu dữ liệu/không áp dụng |
|---|---|---|---|
| OBSERVATION | Process đúng policy/tên, metadata COMPLETE; typed USB/focus/window/display/activity và status READY | INFO cho quan sát. Riêng USB CONNECTED, focus LOST, window MINIMIZED: cùng loại/nguồn/run trong bucket60s có3–5 event → REVIEW, ≥6 → HIGH_ATTENTION | Không suy duration, thao tác vật lý, thiết bị duy nhất. Display/activity/restore/USB baseline-removal/READY/process không áp ngưỡng burst. Ngoài process policy không phân loại; raw vẫn còn |
| SOURCE_UNAVAILABLE | Observer status khác READY; hoặc process đúng policy/tên nhưng metadata không COMPLETE | REVIEW để biết nguồn/metadata chưa đủ; không phải bằng chứng hành vi thí sinh | Không suy UNKNOWN/ERROR thành quan sát không có thiết bị/process; READY lịch sử không bảo đảm hiện tại |
| DATA_GAP | SIGNAL_GAP hoặc monitoring process gap đã commit | REVIEW; eventCount là số bản ghi gap, droppedCount là tổng số event client báo bỏ | Không biết nội dung bị mất, không suy thêm hành vi; không có gap cũng không bảo đảm đủ |
| PRESENCE_INTERRUPTION | Server monitoring_interruptions.timeout_detected_at trong cửa sổ | REVIEW cho gián đoạn liên lạc đã ghi nhận | Không đồng nhất dashboard stale/observer health; không quyết định trạng thái hiện tại, không tự suy Listening |

Mỗi summary có ruleId/policyVersion, nguồn/type/run/attempt, windowStart/windowEnd, eventCount, giải thích tiếng Việt và tối đa5 evidence keys trỏ record gốc. UNKNOWN/error/gap không dùng ngưỡng burst và không tăng thành HIGH_ATTENTION. Không xóa/đổi history, tự chấm điểm, hủy/chặn bài hoặc thiết bị. Một hành động có thể tạo nhiều loại event; chúng không được cộng vào cùng rule/type để tăng count. Ngay trong một type, count vẫn chỉ là số event đã chọn, không số lần vi phạm.

## Cửa sổ, identity và coverage

Engine `AlertRules.evaluate(attempt,input,policy,cutoff)` không đọc clock/DB/cache. Cùng input/policy/cutoff cho cùng output; input reorder/duplicate không đổi count, identity xung đột bị từ chối. Group theo attempt + run + source + loại chuyển trạng thái + rule + bucket UTC cố định + policyVersion; SHA256 của key làm summary ID ổn định qua refresh. LOST/RESTORED và MINIMIZED/OTHER tách nhóm; không gộp khác run/attempt. Sort priority giảm dần, bucket start giảm dần, ID. Không suy nhân quả hoặc trừ clock hai máy.

Mỗi request server quản lý cutoff: nếu không chỉ định, đọc DB clock đúng một lần sau auth locks; truyền cutoff rõ vào engine. Khoảng `[cutoff-windowSeconds, cutoff)`, bucket UTC cố định theo **server received/recorded time**, clip bucket đầu/cuối theo khoảng. Event offline/đến muộn theo giờ server nhận, không sửa observedAt. Batching/reconnect có thể làm nhiều event cũ đến cùng phút; ngay giải thích trên UI cảnh báo điều này, không gọi đó là tốc độ thao tác tại client. Cùng cutoff nhưng lần đọc sau có commit đến muộn có thể thay input/count; cutoff không phải committed-prefix watermark hoặc cache bất biến.

Read model server dùng REPEATABLE_READ transaction snapshot committed, auth/token/user/assignment/ACTIVE locks hiện hữu trước mọi query dữ liệu. Không cache. Mỗi trong4 nguồn (typed/process/process gap/presence interruption) đọc LIMIT cap+1, giữ tối đa cap, mặc định200 (20–200); tối đa800 input/100 summaries/5 evidence mỗi nhóm. Không đọc cả history, không lấy500 dòng client làm toàn bộ đầu vào. Chọn bản ghi mới nhất theo server time + DB tie-break. Source bị cap ở sourceLimited; nhóm cap ở summaryLimited; gap/unavailable/excluded process đếm riêng. Giới hạn luôn hiện ngoài filter raw. WINDOW_ONLY luôn có, kể cả rỗng hoặc không cap; INFO/không có nhóm không chứng minh toàn khoảng bình thường. Process/current presence/Listening state ngoài classification, vẫn xem theo nguồn riêng trên timeline. Evidence không nằm trong500 raw UI được ghi rõ; không tạo raw giả để lấp.

Cấu hình dưới prefix `toeic.monitoring.alert.`: `bucket-seconds` default60/bound10–120; `window-seconds` default300/bound bucket..600 và chia hết bucket; `review-count` default3/bound2–10; `high-count` default6/bound review+1..20; `source-cap` default200/bound20–200. Policy version chứa các giá trị, ví dụ `mon08-experimental-v1-b60-w300-r3-h6-c200`. Đây là **read window**, không retention; không áp offline60s/retention30ngày.

## Contract HTTP mới — không đổi WS/schema lưu

`GET /api/v1/monitoring/attempts/{attemptId}/alert-summary[?cutoff=ISO_INSTANT]`, chỉ khi cả `toeic.monitoring.signal-experiment-enabled=true` và `toeic.monitoring.alert-experiment-enabled=true`. Token chỉ Bearer header; assigned PROCTOR/ACTIVE-only như signal history, không terminal expansion. Chỉ query cutoff một giá trị, >=1970 và không tương lai theo DB clock; query khác/invalid400; 401session,403scope/terminal,404OFF,503DB/transaction retryable. Data/controller error no-store; global auth/filter giữ behavior hiện hữu. Max512KiB cả server/client.

Response schema1 bắt buộc: `protocolVersion=v0`, `schemaVersion=1`, `attemptId`, `policyVersion`, `experimental=true`, `from`, `cutoff`, `coverage=WINDOW_ONLY`, `sourceLimited` (SIGNAL/PROCESS/PROCESS_GAP/PRESENCE), `summaryLimited`, `sourceCap`, `inputEvents`, `gapEvents`, `unavailableEvents`, `excludedProcessEvents`, `coverageNote`, `items` (≤100). Item exact: `id`, `attemptId`, `runId`, `source`, `eventType` (read projection, không wire type mới), `ruleId`, `policyVersion`, `priority`, `eventCount`, `droppedCount`, `windowStart`, `windowEnd`, `evidence`, `evidenceLimited`, `explanation`. droppedCount là **chuỗi số nguyên thập phân chính xác**, tối đa22 chữ số và <=800×Long.MAX_VALUE; không tràn long hoặc mất chính xác JSON number. Evidence keys namespaces signal/process/process-gap/presence kèm attempt/eventId; tối đa5, evidenceLimited khi count>5.

## Ownership/UI

`AlertController → AlertService.read → SignalService.authorizeHistory → capped SQL → typed/process normalization → AlertRules → AlertSummary.parse → TimelineController → MultiSignalTimelinePane`.

Không new scheduler/socket: thêm request trong cycle hiện hữu (poll3s khi rảnh, một cycle in flight, budget12s). 404/OFF hiển thị chưa bật, không kết luận. 5xx/invalid làm snapshot stale; 401/403/FAILED xóa summary cùng raw/state/cache; đổi attempt/generation/close hủy request cũ. Render trên FX, network/parser ngoài FX. View đóng API/listener/worker sở hữu, không đóng transport mượn. Raw history/state và các alert pane cũ không bị rewrite. USB provider production không nối; không observer/signal type/migration/dependency mới.

## Trước production/nghiệm thu

Cần nhóm duyệt policy/threshold và false-positive budget trên dữ liệu phù hợp, review B/A/C, hardware và lifecycle/desktop/LAN acceptance. Chuyển app khác để mở cửa sổ hỗ trợ, lặp do bounce hoặc delivery batch đều có thể là nguyên nhân vô hại; HIGH_ATTENTION chỉ mời xem record gốc. USB0/12/màn hình thứ hai/OS input/LAN hai máy NOT_RUN. Không E1/E2, MON-08 chưa DONE, gates/ADR/policy/owner pending và FULL/experiments OFF giữ nguyên.


## Lượt chung MON01–09

`.\scripts\demo-mon08-alerts.ps1 -Integrated` tái sử dụng entrypoint này để ghép process/FX/USB/status/gap → committed history/timeline/summary và app lifecycle. `-Integrated -Mode Real -ReadUsbStorage` chỉ thay nguồn USB bằng CIM minimal trong TEST. Các mode trước vẫn giữ. [Ma trận, provenance, prerequisite/cleanup và actual](MONITORING_INTEGRATION.md); không MON DONE/hardware/policy approval.
