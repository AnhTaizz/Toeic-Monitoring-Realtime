# T2-C1 full snapshot baseline v1 — 10/10/2026

Người chạy: Codex Agent, theo ủy quyền implement/test/commit/push/PR của vai C. Base origin/main sau fetch: `8eba40595e396184fc7f93f6e38d73c0ae4af673`; trước sửa working tree sạch, chưa có branch/PR T2-C1. Nhánh `feat/t2-c1-monitoring-full-snapshot`, code commit **`6acd031e0c11ddb7ace97ace4f68359e23cf8fe1`**. Commit bàn giao sau đó chỉ docs/evidence, không đổi code đã kiểm. Chưa merge. Human A/B review và ChatGPT planning/review NOT RUN (người dùng đã chọn không kết nối ChatGPT).

## Điều kiện và lệnh thực chạy

Windows11 x64 build26200, Temurin21.0.10+7, Maven3.9.15, PostgreSQL18.6 trong container toeic-db, Compose5.1.3, Python3.15.0b3. DB local theo `.env` cổng5433; không commit `.env` hoặc secret. Không reset volume. Có Windows Edge; test chỉ mở/dừng cây process của riêng mình với profile UUID.

```powershell
# $jdk là thư mục JDK21 đang cài, không phải đường dẫn tới java.exe.
docker compose up -d --wait
mvn "-Dmaven.repo.local=<Maven cache của máy chạy>" package
python -m unittest discover -s scripts -p test_summarize_monitoring.py -v
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t2c1.ps1 -JavaHome $jdk -Gui
# Hồi quy: smoke-a2, a3, a4, c2, c3, c4 với -JavaHome $jdk; smoke-b3 thêm -Gui.
```

Baseline trước sửa: Java **376** (protocol23/client227/server125/spike1), A2 REAL PASS sau dựng DB. Maven lần đầu bị chặn ghi cache mặc định C:\.m2; chạy lại với cache sẵn có và escalation được duyệt. A2 lần đầu DB-off/connection refused; khởi động Docker Desktop hidden và compose, không sửa DB. Không có baseline đầy đủ các smoke khác; không suy diễn chúng PASS trước sửa.

Sau sửa: Java **408** (protocol29/client242/server136/spike1), 0 failure/error/skipped, package 5 module PASS. Python **10** PASS. Java/Python chạy trước code commit trên cùng source; full integration/GUI/log chạy lại **sau code commit** với working tree sạch tại lúc ghi metadata. Manifest source/JAR SHA256 trong [run-metadata.json](run-metadata.json). Không gán SHA của commit docs cho build cũ.

Run evidence: **T2C1-e540e5a0-665d-4a12-af97-33201a8e1b8a**, UTC05:13:09 ngày10/10. Full harness poll200ms/HB200ms; presence và full stale1200ms/scan25ms để kiểm timeout; TTL300000ms, maxAttempts4096, snapshot128, queue16+1pending, dedup64, retry full default5/ACK5s/backoff1..8s, WS65536byte. Production default poll1000ms/HB2000ms/full stale6000ms/scan500ms, không đổi bằng smoke override. Worker dọn bằng bounded wait, không đo latency/CPU/memory.

## Acceptance trong phạm vi full

| Kiểm tra | Kết quả / mức bằng chứng |
|---|---|
| Trước OPEN/full UNSYNCED; full1 SYNCED, scan tiếp tăng sequence | PASS — REAL HTTP/WS/ProcessHandle/server RAM/dashboard |
| Mở/đóng owned Edge: PID xuất hiện rồi biến mất, cảnh báo DB giữ nguyên | PASS — REAL, proctor JavaFX component/controls, ảnh bên dưới |
| Full rỗng thay tập; event/gap/interruption không bị xóa | PASS — REAL network/PG, tập rỗng và overflow report **MOCK** |
| Scan lỗi không thành full rỗng; queue16/snapshot128 vượt giới hạn không cắt tập | PASS — MOCK unit FullSnapshotDeliveryTest; lỗi OS thực NOT RUN |
| Full đầu bắt buộc1; sequence cũ không ghi đè; full được nhảy sequence | PASS — MOCK reducer unit |
| Reconnect epoch mới/full1; epoch cũ STALE | PASS — REAL reconnect/WS; socket cũ giành lại OPEN/close bị chặn có MOCK service unit |
| Retry đúng ACK lại; cùng ID/sequence khác nội dung CONFLICT; dedup có giới hạn | PASS — REAL exact retry/conflict với process set MOCK; window/canonical order MOCK unit |
| Write không phải ACK; ACK phải đúng request/trace/epoch/sequence | PASS — MOCK delivery/transport unit; REAL ACK integration |
| Guard socket/collector generation loại queued OPEN/FULL trước sendText; callback cũ không sửa phiên mới | PASS — MOCK blocked write/lifecycle tests, REAL reconnect |
| PROCTOR không gửi full/OPEN; foreign HTTP state 403; quyền lại trước duplicate | PASS — REAL role/HTTP; scope/owner revalidation MOCK service unit; event quyền/revoke REAL A2/A3/B2 hồi quy |
| HTTP về muộn không ghi đè push; đổi lượt/refresh/logout bỏ callback cũ | PASS — MOCK dashboard/controller/futures; REAL B3 chọn/refresh/logout component |
| Dừng full giữ STALE; TTL bỏ RAM và giải phóng capacity; history còn | PASS — REAL CLOSE/history; TTL/limits bằng clock MOCK |
| Full/source/transport/dashboard/measurement worker + listeners dọn | PASS — REAL worker cleanup + MOCK lifecycle/cancellation unit |
| Byte hook mới không đo đôi; 4 endpoints có FINAL, drops/unwritten/pending=0 | PASS — REAL log, summary COMPLETE cho file cung cấp; Python hand sum MOCK |

## Hồi quy và lỗi đã gặp

- A2/A3/A4/C2/C3/C4/B3: **PASS** trên package T2-C1. C2/C3 có Edge thật. A4/B3 snapshot MOCK, hard-kill owned JVM REAL; B3 GUI chỉ component proctor. C4 event/overflow MOCK, mất ACK ở observer SIMULATED. [checks.txt](checks.txt) trích dòng kết quả nguyên văn từ các log thật, không phải toàn server log.
- B2 wrapper cũ trên DB dev **FAIL** vì assert scope rỗng; query REAL xác nhận candidate1 có1attempt ACTIVE. Không xóa fixture. Cùng harness B2 **PASS** trong database `toeic_t2c1_b2_UUID` riêng; đợi dev seed trước harness, sau cùng DROP đúng owned DB. [b2-isolation.ps1](b2-isolation.ps1) là helper tái lập với JavaHome tham số, chạy từ root sau khi wrapper B2 đã tạo smoke-classes. Seed readiness và timezone UTC tránh login quá sớm/Asia-Saigon alias của máy; không sửa production auth.
- C3 lần đầu đọc ERROR của kênh full/close mới thay vì ERROR gap cần kiểm. Harness giờ chỉ nhận ERROR khớp requestId gap; cả deferred COMMIT failure/retry/rollback và conflict đã chạy lại PASS. A4/B3 strict migration==4 được đổi >=4 vì main có V7; tab history B3 index đổi theo tab mới. Không bỏ assertion nghiệp vụ.
- Full GUI lần đầu đã tới screenshot nhưng profile Edge cleanup gặp file lock; sửa bounded wait/retry chỉ trên owned temp profile, sau đó chạy lại PASS. Test UNKNOWN mới lần đầu dùng thiếu timeoutDetectedAt và parser từ chối đúng; bổ sung dữ liệu MOCK hợp lệ rồi toàn Java408 PASS. Không ghi những lượt FAIL là PASS.

## Artifact và kiểm lại

- [Ảnh Edge mở](t2c1-edge-open.png): owned PID4764 trong bảng process hiện tại; [ảnh Edge đóng](t2c1-edge-closed.png): không còn PID đó. Process Chrome/Zalo khác là quan sát thật của máy test; không phải nguồn MOCK. Scene JavaFX thật, ảnh chưa chỉnh sửa. Không phải GUI candidate toàn app.
- [raw/](raw/) có4file JSONL nguyên bản; không payload/path/commandline/token. [summary.json](summary.json), [summary.csv](summary.csv) do Python sinh, giữ nguyên. Tổng byte chỉ JSON application TX theo từng outcome, không framing/TCP/IP/TLS/HTTP, không TX+RX hoặc attempted+completed.
- [SHA256SUMS.txt](SHA256SUMS.txt) kiểm artifact, bỏ chính file checksum và trang verification này. Có thể chạy `python scripts/summarize-monitoring.py evidence/t2-c1/raw --output server/target/t2c1-evidence-summary`; exit0=COMPLETE, so summary sinh lại với file giữ nguyên.

## Bàn giao và giới hạn

**CODE_COMPLETE T2-C1; MT01 PARTIAL**. GUI candidate login/start/stop toàn app, LAN Windows thứ hai, human A/B review, server full RAM restart qua network, lỗi OS scan thật, đo performance: **NOT RUN**. Restart RAM semantics được ghi trong contract; chưa có test full restart riêng, không gán bằng chứng A4 presence restart cho full. Không triển khai T2-C2–C5, delta/resync delta, trace oracle, E1/E2 hay lỗi bài thi khác.

A cần review MonitoringStateService auth trong lock/RAM/TTL/registry/ACK; B review FullSnapshotPayload/MonitoringStateView/RealtimeClient correlation/guard và DashboardController/model/view. Event/gap/interruption storage của A giữ nguyên. Không migration mới. Contract QD-12 nói rõ ACK FULL chỉ RAM một JVM. TRACKER và kế hoạch gốc không sửa. PR chỉ bàn giao, chưa được human review/merge.
