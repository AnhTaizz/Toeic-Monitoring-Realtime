# T2-C5 — Kiểm tra phương pháp baseline, 11/10/2026

Base/main fetch `82557bf4a5daddd213469196c3501968b3bbe21e`, workspace sạch, chưa có nhánh C5. C4 PR#21 xác minh merged bằng merge commit b576a07; main có thêm commit mục lục evidence nên dùng82557bf. Nhánh `docs/t2-c5-monitoring-baseline-methodology`; tài liệu phương pháp commit `eb6ba596fc6f3872e3bcca0e87781efb05010092`. SHA bàn giao/PR/merge và HEAD cuối ở báo cáo/PR, không bịa merge tương lai tại đây.

## Công việc

Tạo [MONITORING_BASELINE_METHOD](../../docs/MONITORING_BASELINE_METHOD.md); đồng bộ PROTOCOL, MONITORING_MEASUREMENTS (FULL/state/ACK FULL có sequence, bổ sung đủ type hiện có), MONITORING_TRACE (C4 đã có, TEST không replay production), CONTROLLED_PROCESS_HARNESS, README/index và tiến độ/vai C/nhật ký/kiểm thử. Không quyết định kiến trúc mới, QUYET_DINH giữ nguyên. Chỉ tài liệu và kết quả kiểm tra file, không runtime/script/dependency.

Định nghĩa theo source: policy8tên/TEST, khóa collector/PID/start và thiếu metadata, empty khác failure, event/history khác full RAM/presence/gap, epoch/sequence/revision, ACK RAM vs transaction commit, deliveryStatus không latency. Default production riêng smoke. Method E1 phân biệt targetGO/parent lifetime/startup proxy/scan bounds, rate có eligibility chung/actual bins đề xuất, INCONCLUSIVE riêng, không clip PRE_GO âm. Kernel detection latency/CPU/RSS NOT MEASURED; thiếu window/warmup/sampler/analyzer. E2 phương pháp chưa có delta/gate/oracle delta/endpoint-window-category analyzer; giữ cùng trace/policy/workload/reliability, byte scope JSON WS không HTTP/handshake/framing/tổng kỳ thi. Không bịa field hiện chưa có.

## Đã kiểm tra mới trong C5

- Đọc task/hợp đồng/kế hoạch E1/E2 và source/test/evidence C1–C4. Rà method/field/default/schema và đường ACK transaction handler; source measurement MessageIdentity và Python summary types/CLI khớp doc. Source references mở trực tiếp từ tài liệu.
- Kiểm Markdown local links và heading anchors trong toàn bộ file Markdown thay đổi; tên method/production8tên, schema/type/defaults bằng script kiểm file tạm dưới ignored client/target. Đầu ra thực: [doc-checks.json](doc-checks.json). Script kiểm tạm không thêm công cụ E1/E2/runtime vào repository.
- Hash28file ledger C4, core manifest và FINAL GT/trace khớp exact bytes; hash27artifact C3 và gzip restoration khớp. Không sửa old artifacts/checksum. C3 owned-edge-seeded report PASS có6missingBusinessEvents, không nhầm PASS là delivery đủ; C4 summary27/3/0 và trial006 lifetime329,8944ms/hold211,9458ms đối chiếu raw.
- Git diff whitespace/scope và kiểm nội dung bí mật trên thay đổi; chỉ README/docs/evidence index/C5 report+checks. Không .env/tracker/kế hoạch gốc/migration/collector/harness/reducer/thi hoặc C1–C4 evidence thay đổi. Link web JDK là nguồn đã có trong guide, không coi audit local là kiểm truy cập web mới.

## Không chạy và giới hạn

Java493 ở C4, Python10 ở C3 là evidence task trước. **Không chạy lại** Maven/unit/analyzer unit/CLI replay/child smoke/GUI/network/DB hoặc benchmark trong C5; không phát sinh số PASS runtime mới. Không reset DB/kill process. E1/E2 NOT RUN, human B review NOT RUN, ChatGPT planning/review NOT RUN theo opt-out người dùng. MT toàn hệ thống/GUI candidate/LAN/package không được thay bởi kiểm file.

Giữ cả event concurrent FAIL và isolated PASS ở evidence C4, nguyên nhân chưa chứng minh; các lượt C4 exploratory25/5 và committed27/3 giữ đúng provenance. Initial audit chọn các report mixed/drop không thấy missing events nên assertion thất bại; sửa phạm vi đọc toàn report (bao gồm owned-edge-seeded gzip) và audit PASS. Đây là lỗi chọn dữ liệu của kiểm tra tài liệu, không thay artifact/product hoặc làm mất6events thành0.

Checklist B review và đầu vào E1/E2 nằm ở mục8 của phương pháp; checkbox đều chưa xác nhận. Chưa được human review không tự thành blocker merge tài liệu nếu GitHub không bắt buộc, nhưng phải được xử lý trước phép đo chính thức. Người dùng ủy quyền commit/push/PR/merge commit nếu không blocker và giữ nhánh. Dừng sau C5; không chặng3/delta/E1/E2.
