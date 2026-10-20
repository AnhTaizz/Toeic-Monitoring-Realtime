# QA-05/E1 — join dẫn xuất có provenance, 20/10/2026

**TOOLS_VERIFIED; E1_ACCEPTANCE_PENDING.** User ủy quyền tool/test/docs/push/PR/review rồi
merge commit nếu CI/quy định cho phép, giữ nhánh. Không campaign12/E2, production monitoring,
DB/wire/observer/GUI/USB/LAN/ADR/offline/retention hoặc gate changes. FULL default/MON07 OFF.

## Nguồn và thiết kế

Main fetched `159954bc7eb74b83e86d7963293fae8e0615a9e1`; worktree riêng nhánh
`tools/c-e1-derived-join-20261020`, không sửa/reset/xóa root/worktree cũ. Đọc CLAUDE/user
instructions/TIEN_DO/NHAT_KY/VAI_C/QA05/E1 guide, analyzer/tests, ObservationJoiner/HarnessCli/
GroundTruth/Trace/HarnessArtifacts/schema/manifest/provenance. Không AGENTS trong checkout;
user opt out codex-with-chatgpt vẫn áp dụng. Kiểm open PRs: chưa có PR triển khai chức năng này;
PR38 lịch sử tích hợp E1 chưa phải đường provenance explicit này, không sửa PR khác.

Contract nhỏ được ghi trong [README tool](../../scripts/benchmarks/e1/README.md) **trước code**,
không proposal/framework riêng. Chỉ stdlib Python; giữ ObservationJoiner/Java/formula nguyên vẹn.

`derived.py SUITE NEW --join-tool JAR` xác minh toàn bộ suite trước spawn, gọi đúng HarnessCli
join ra `NEW/run-*/join.json`, không thu thêm process; sau đó tạo `provenance.json` v1.
Provenance khóa7file gốc (cả manifest/e1-manifest/resources/join cũ), directory/run/clock/
collector/trace origin, raw source từ metadata, output hash, JAR hash/version, creator và
analyzer-helper hash/source nếu Git kiểm được, argv template tái tạo không private path.

Analyzer chỉ chọn qua `--derived-joins NEW --join-tool JAR`. Vẫn chạy validation đầy đủ join
gốc/raw trước lựa chọn; provenance không thay checksum lịch sử. Kiểm schema thật
controlled-observation-join-v1, identity/trial exact set, target/PID/start/lifetime/hold/status,
reason/counters/finite typed scan fields, trace checksum nội bộ và input/tool/script hashes.
Không silently fallback hoặc đọc path trong provenance; file cố định/contained, output mới.
CSV diff không PID/startInstant, giữ trial/conclusion/reason và các field ảnh hưởng metric.
`analysis-receipt.json` ghi ORIGINAL/DERIVED/input/output/tool hashes, kể cả run INVALID.
Cuối toàn lượt đọc lại hashes raw để bắt thay đổi sau khi run đã được xử lý.

Tooling verified source **`c7f12a762aff07e1ec7afee7f71813a3411ba7e7`**. Analyzer/creator
source tách raw/tool. JAR bất kỳ không tự chứng minh source SHA: provenance để sourceSha null,
dùng SHA256 làm tool version. Trong smoke này riêng, JAR đã có build receipt source5bbeaf2
tại [lượt lifecycle](../c-full-lifecycle-20261020/receipt.json); đã kiểm đúng binary hash và
diff Java production/JAR inputs so current bằng0, ghi proof hash trong receipt mới. Không lấy
HEAD của Python gán vào raw5bbeaf2 hoặc raw lịch sửc1601b5. Hash không là proof thuật toán đúng.

## Kiểm mới và actual

Windows11/Temurin21.0.10+7, Python3.15.0b3 có sẵn; CI pin3.12. Không local Maven/PG/GUI campaign.

| Kiểm | Actual / nguồn |
|---|---|
| Baseline nguyên bản159954b | 12Python PASS/0SKIP; log riêng từ bản script Git cũ |
| Giới hạn cũ, MOCK độc lập | Analyzer cũ đọc fixture hợp lệ; thay join mới vào raw MOCK nhưng giữ manifest cũ → RAW_CHECKSUM. Đây là giới hạn có chủ ý, không bug production |
| Bộ Python cuối | **27PASS/0FAIL/ERROR/SKIP**:12legacy +15tests mới; có subcases hash7file/run/clock/raw/tool/creator/schema/trial thiếu-trùng-thừa, output guard, default/identical/changed metrics, INVALID/PARTIAL, deterministic và raw-change cuối lượt |
| Fixture MOCK tính tay | Bucket200ms:2eligible/1detected/1unknown →1detected/1missed/0unknown; missRate.5/bounds.5..5, detected latency100ms, CPU collector10%/parent20% giữ. Unknown/censored bucket khác còn riêng |
| Một REAL smoke-short hiện có |1run500ms/3trials, status COMPLETE/resourcesCleaned=true; raw/tool source5bbeaf2, không E1 final. Dùng JAR verified đã có, không rebuild Java khi không đổi Java |
| Rejoin smoke cuối | exit0;3/3 trial join byte giống gốc,0trial changed; derived/original CSV/Markdown/campaign status bằng byte,7raw hashes trước/sau nguyên vẹn |
| Determinism | Hai lần analyze dẫn xuất cùng source/input/tool, output mới khác: toàn bộ8file giống byte, gồm diff và receipt. Không timestamp tự sinh |
| Campaign smoke | PARTIAL: thiếu4period×3run và full window/workload; không đổi mẫu số/unknown hoặc tự nghiệm thu |

Lệnh Java đầu FAIL trước main do PowerShell tách `-Dtoeic.e1.machineAlias=...`; giữ log,
quote nguyên argument rồi mới thu đúng **một** smoke-short thành công. Không retry measurement
tới PASS. Không sửa Joiner/expected/formula để tạo khác biệt; khác1trial chỉ fixture MOCK.

Raw lịch sử **NOT_AVAILABLE trong lượt này**. Đã kiểm đúng suite path từ
[raw-provenance lịch sử](../overnight-20261015/phase-06/e1/raw-provenance.json) dưới19root Git
worktree đăng ký, và target/integration-final-20261017, target/t4-c1 được guide/receipt chỉ ra;
không tìm thấy suite, không quét toàn ổ. Không dùng360trial/287/62/11 lịch sử làm kết quả mới.
Không chạy lại12lượt hoặc gọi smoke/MOCK là tái phân tích raw lịch sử.

## Lệnh và evidence

```text
python -m unittest discover -s scripts/benchmarks/e1 -p 'test*.py' -v
python scripts/benchmarks/e1/derived.py <original-suite> <NEW-derived-root> --join-tool <verified-jar>
python scripts/benchmarks/e1/analyze.py <original-suite> <NEW-analysis> --derived-joins <NEW-derived-root> --join-tool <same-jar>
python scripts/benchmarks/e1/analyze.py <original-suite> <NEW-original-analysis>
```

[Guide E1](../../docs/E1_T4C1_MEASUREMENT.md) đã thay đúng đoạn BLOCKED bằng các lệnh explicit,
provenance/schema/errors/diff/MOCK/limits; chỉ gỡ blocker công cụ, không nghiệm thu E1.
Creator cũ phải chọn rõ cặp script ở worktree cũ, không bỏ validation. Không generated
timestamp; source Git context là metadata đầu vào, thay source context thì receipt có thể khác
dù code bytes giống nhau. Output hashes không tự hash chính receipt, report/receipt ngoài giữ hash đó.

[Receipt kết quả/hashes](receipt.json). Raw/JAR/provenance đầy đủ/CSV/log ở root target ignored;
không upload raw hoặc copy artifact lịch sử. Public chỉ report/receipt/hash/scalar, không
credential/private path/PID/process name/raw machine fields. CI thêm27tests Python với3.12,
giữ nguyên Java clean verify/kit checks; kết quả exact publication HEAD/merge ghi tại PR.

Thiếu trước nghiệm thu E1: khôi phục raw lịch sử/checksums để chạy đường mới trên dữ liệu đó,
review C/B về phương pháp/source/machine/carryover/coverage, đủ12run hợp lệ nếu muốn kết luận
chiến dịch mới, và dependency BASE03/MON01/gate riêng. Không QA05 DONE hoặc tracker/hours changes.
