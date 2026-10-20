# E1 polling ProcessHandle

Runner Java tái sử dụng ControlledProcessHarness; analyzer Python stdlib.

## Contract join dẫn xuất v1

Quyết định trong phạm vi tool QA-05/E1: không thay raw/manifest/join lịch sử hoặc công thức.
`derive.py SUITE NEW_ROOT --join-tool JAR` gọi đúng HarnessCli join, không thu E1 mới.
Mỗi `NEW_ROOT/run-*/` chỉ có `join.json`, `provenance.json`; tên run chọn explicit, không fallback.
Analyzer nhận `--derived-joins NEW_ROOT --join-tool JAR`; thiếu provenance/tool/hash thì INVALID.
Mặc định vẫn đọc join gốc và giữ validation cũ.

Provenance `e1-derived-join-v1`: identity gồm directory/runId/clockDomain/collectorSessionId/
traceStartElapsedNanos; rawSourceSha lấy nguyên metadata; inputs khóa SHA256 cả7file gốc
(ground-truth, observations, metadata, join, manifest, resources, e1-manifest). Output khóa
`join.json` dẫn xuất. Tool có entry point/JAR SHA256 và version nhận diện bằng hash; creator
có script SHA256 và analyzer-helper SHA256, source chỉ nhận khi cả hai khớp Git. Source SHA JAR là null vì không thể xác minh source từ JAR bất kỳ;
không gán HEAD đang checkout. Source analyzer/creator chỉ nhận khi file khớp Git HEAD sạch,
nếu không để null và dùng byte hash đã kiểm. Hash không là chữ ký hay proof thuật toán đúng.

Analyzer kiểm toàn bộ gốc kể cả join cũ trước khi chọn join mới; ràng buộc run/clock/trial
và schema thật của ObservationJoiner, đối chiếu artifact/tool hashes. Creator script mặc định
là derive.py hiện hành; khi đọc provenance tạo bởi bản cũ, chọn rõ `--provenance-script FILE`
và kiểm hash, không tự tìm file. Giữ analyzer.py của creator cạnh derived.py cũ (nếu có
sourceSha thì dùng đúng worktree/Git object kiểm được cả cặp). Không thực thi command đọc từ provenance. Lệnh tái tạo là
argv template với input/tool/output placeholders, không lưu private path.

Output mới gồm CSV diff đủ trialId/conclusion/reason và trường ảnh hưởng metric, cùng receipt
input/output/tool hashes. Receipt không có timestamp tự sinh; dữ liệu CSV/JSON/Markdown được
cam kết deterministic theo cùng input/tool bytes, không phụ thuộc vị trí output. INVALID/
PARTIAL và các mẫu số/weighted CPU/latency/censor/unknown giữ nguyên. Không bypass validation.
CI chạy unittest với Python3.12; tool chỉ dùng stdlib Python3.11+, không cài package Python mới.

- [Hướng dẫn hiện tại, công thức, checklist và lệnh cho B](../../../docs/E1_T4C1_MEASUREMENT.md).
- [Review T4-C1 và kiểm chứng](../../../evidence/t4-c1/2026-10-16-review.md).
- [Lệnh lịch sử trước review](../../../evidence/overnight-20261015/phase-02/E1_RUN_COMMANDS.md).

Không gọi runner từ app production. SMOKE không thay12run; E1 không thay gate delta/E2. Summary mới phải ở output mới, không ghi đè raw/evidence lịch sử. Chạy `python -m unittest discover -s scripts/benchmarks/e1 -p 'test*.py' -v` để kiểm analyzer và fixture tính tay.
