# EX-01 · Backup/restore cũ — LỊCH SỬ, không còn là workflow hiện hành

> **Thay thế bởi [Portable Exam Seed](PORTABLE_EXAM_SEED.md) từ commit `13e0745` (20/10/2026).** Từ commit đó, các thành phần sau đã bị xoá khỏi branch:
> - `scripts/backup-toeic-db.ps1`, `scripts/restore-toeic-db.ps1`, `scripts/toeic-db-common.ps1`;
> - `tools/exam-dataset/verify-db-roundtrip.ps1`;
> - `DatasetArchive.java` và các lệnh `maintain.ps1 -Action archive-create|archive-verify|archive-restore`.
>
> Lý do: yêu cầu thật là seed **từng đề**, không phải sao lưu toàn DB (gồm tài khoản, session, bài làm). Nhóm chỉ giữ một hệ thống. Tài liệu này lưu lại để truy vết kết quả đã đo và chỉ chỗ archive cũ. Đừng làm theo các lệnh cũ.

## Archive JSON/media đã tạo — vẫn giữ, không xoá

Archive tạo từ source `1b2bccf8f17e5f2468dc9e6729df8b0bcfa43a62`, nằm ngoài Git trên máy Windows của nhóm, dưới `E:/Project/toeic-ex01-backups/`:
- `dataset-20261020-1b2bccf/`: snapshot đầy đủ; manifest SHA `d56c25d61f7dba4453f5f6354163a3d6bdeb0838109c420e16355d8f158f4e52`; 757 file, 4 đề/800 câu, 358 media (189.902.302 byte).
- `restored-20261020-1b2bccf/`: bản đã restore thử sang thư mục mới.
- `mock-db-2c399073aac8/`: kết quả `pg_dump` MOCK cũ (159.485 byte) và receipt.

Đây là bản trên cùng ổ đĩa, chưa phải bản sao ở thiết bị độc lập. Các file trong Git còn trỏ tới archive: [receipt](recovery/archive-summary.json), [manifest](recovery/archive-manifest.json) và [CLI log](recovery/archive-final.log). Lệnh kiểm archive mà không cần tool cũ có ở [PORTABLE_EXAM_SEED.md §5](PORTABLE_EXAM_SEED.md#5-dữ-liệu-capture-cũ-archive-jsonmedia--không-xoá).

```text
dataset-<date>-<sha>/
  archive-manifest.json, archive-manifest.sha256
  datasets/<exam-directory>/normalized.json, draft.v2.json, *-report.json, *-manifest.json
  recovery/ets2026_test01/recovered-normalized.json, draft.v2.json, evidence/review reports
  audio-resources/<examId>/<localFile>, image-resources/<examId>/<localFile>
  sources/*.html
  provenance/source-manifest.json, media-permission.json, media-receipts/<examId>/*.json
```

- Baseline `datasets/ets2026_test01/` và bản recovery `recovery/ets2026_test01/` đều được giữ. Không import cả hai.
- Test05 trùng nội dung với Test04, chỉ giữ raw/provenance.
- Archive giữ nguyên các khoảng trống và trạng thái UNVERIFIED; nó không chứng minh bản quyền hay tính nguyên bản ETS.

## Kết quả đã đo bằng hệ thống cũ (giữ để truy vết)

| Hạng mục | Kết quả lúc đó |
|---|---|
| JSON/media archive create/verify/restore | PASS trên dữ liệu đã capture: 757 file / 358 media ([log](recovery/archive-final.log)) |
| `pg_dump` toàn DB MOCK → TEST mới → catalog | PASS, chỉ MOCK. PostgreSQL 18.1, JAR SHA `967b6f00…`, 200 câu / 775 options / 76 groups / 57 media / 286 blocks; SQL SHA `63b1a7cb…` ([receipt](recovery/mock-roundtrip-report.json), [receipt trên main](final-integration/main-mock-roundtrip.json)) |
| ETS original-content/answer/document và acceptance end-to-end | BLOCKED / NOT RUN |

Các receipt và log trong `recovery/` và `final-integration/` là **raw evidence của lượt chạy cũ**, không được sửa tay. Chúng nhắc tới tool đã xoá vì đó là tool đã dùng lúc đo.
