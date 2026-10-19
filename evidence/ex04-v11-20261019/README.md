# Bằng chứng EX-04 V11 / review EX-02 — 19/10/2026

- [Báo cáo kiểm chứng](../../docs/EX04_V11_VERIFICATION.md), [gap matrix và proposal DTO/API](../../docs/EX02_RICH_EXAM_CONTRACT_PROPOSAL.md).
- [Reproducer V11](repro-v11-null.json):2FAIL thật trướcV12 tại3077f40; không sửa failure lịch sử.
- [Targeted](targeted.json):58PASS tại975284d; trong đó43case PG/Flyway/HTTP với fixture MOCK tự tạo,15unit.
- [Full clean verify](full-verify.json):984PASS/0FAIL/0ERROR/6SKIP, source975284d; targeted là subset, không cộng vào tổng. Mỗi run0schema test và cluster stop exit0.
- [Fingerprint source](source-files.json): V1–V12 và test/runner; raw/XML hashes nằm trong từng result.

Environment: Windows native, Oracle Java21.0.8/Maven3.9.11/PostgreSQL18.1. Database test riêng `toeic_ex04_test`, loopback random port/clusterSCRAM mới/schema sở hữu. Không dùng seed ETS200, không tải media, không physical/LAN/hearing.

Các JSON công bố chỉ giữ case/status/count/timestamp/version/hash và cleanup; không properties, system-out/err, environment, token, password hoặc local path cá nhân. Raw results/logs/XML không sửa, giữ trong target ignored; versionJava trong bản dẫn xuất lấy từ stderr gốc bỏ banner JAVA_TOOL_OPTIONS, có rawResultSha256 để đối chiếu. Runner sửa bộ lọc metadata sau khi chạy, không đổi test/SQL đã đo.

Chạy lại theo lệnh trong report hoặc scripts/verify-ex04-postgres.ps1. Không chạy Docker down-v hoặc reset DB dùng chung. Không đánh EX01/02/04/08 DONE, không human signatures, không merge.
