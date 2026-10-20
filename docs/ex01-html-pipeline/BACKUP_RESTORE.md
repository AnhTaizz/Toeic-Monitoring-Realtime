# EX-01 · Backup và restore offline

Đã thực hiện thật JSON/media restore cho4 đề/800 câu và358 asset (189.902.302 byte), kiểm đủ757 file trong archive. Đã thực hiện PostgreSQL backup/restore trên **MOCK TEST** qua importer V2 và catalog thật. Đề ETS vẫn BLOCKED; archive giữ nguyên các khoảng trống và UNVERIFIED, không sinh dữ liệu đề bị thiếu.

## Phiên bản và vị trí

Source `1b2bccf8f17e5f2468dc9e6729df8b0bcfa43a62`. Bản trên máy hiện tại nằm ngoài mọi Git worktree dưới `E:/Project/toeic-ex01-backups/`:

- `dataset-20261020-1b2bccf/`: snapshot đầy đủ, manifestSHA `d56c25d61f7dba4453f5f6354163a3d6bdeb0838109c420e16355d8f158f4e52`.
- `restored-20261020-1b2bccf/`: restore sang thư mục mới, không đọc HTML/media gốc hoặc fetch website.
- `mock-db-2c399073aac8/`: pg_dump thật159.485byte, metadata/checksum, receipt restore TEST và media MOCK57 file.

Đây là vị trí backup thực nghiệm trên cùng ổ đĩa, chưa phải bản sao ở thiết bị độc lập. Khi chuyển laptop hãy chép nguyên archive sang ổ khác và verify tại máy nhận. Không dựa vào `target`/cache làm bản sao duy nhất. [Archive receipt](recovery/archive-summary.json), [manifest](recovery/archive-manifest.json), [raw CLI](recovery/archive-final.log). Mỗi lần backup tạo tên phiên bản mới; không ghi đè bản cũ.

```text
dataset-<date>-<sha>/
  archive-manifest.json, archive-manifest.sha256
  datasets/<exam-directory>/normalized.json, draft.v2.json, *-report.json, *-manifest.json
  recovery/ets2026_test01/recovered-normalized.json, draft.v2.json, evidence/review reports
  audio-resources/<examId>/<localFile>
  image-resources/<examId>/<localFile>
  sources/*.html
  provenance/source-manifest.json, media-permission.json, media-receipts/<examId>/*.json
mock-db-<run>/
  mock.sql, mock.sql.metadata.json, mock.sql.<new-test-db>.restore.json
  mock-roundtrip-report.json
  media/audio/<examId>/<localFile>, media/images/<examId>/<localFile>
```

Baseline `datasets/ets2026_test01/` và bản recovery `recovery/ets2026_test01/` đều được giữ; bản recovery mới có178 option texts nguồn. Không nhầm draft baseline với bản recovery, không import cả hai. Test05 trùng04 chỉ giữ raw/provenance, không tạo đề thứ5. Archive chứa normalized/V2, source hashes, quyền và metadata thật; nếu raw HTML thất lạc vẫn khôi phục được **những gì đã capture** từ JSON. Nội dung chưa có trước backup vẫn cần nguồn bổ sung. Nếu có supplement sau này, lưu riêng file raw/hash cùng snapshot mới; không chỉ lưu pointer tới file sẽ mất.

## Build và backup JSON/media

Chạy tại worktree ETL bằng PowerShell Windows với JDK21/Maven3.9.11; không cần backend hay PostgreSQL cho JSON/media:

```powershell
mvn -f tools/exam-dataset/pom.xml test dependency:build-classpath '-DincludeScope=runtime' '-Dmdep.outputFile=target/runtime-classpath.txt'
.\tools\exam-dataset\maintain.ps1 -Action recover
# Exit 2 khi chưa có HUMAN_VERIFIED; vẫn sinh reports và draft.
$sha = git rev-parse HEAD
.\tools\exam-dataset\maintain.ps1 -Action archive-create -Destination '<external>/dataset-new-version' -SourceCommit $sha
.\tools\exam-dataset\maintain.ps1 -Action archive-verify -Archive '<external>/dataset-new-version'
.\tools\exam-dataset\maintain.ps1 -Action archive-restore -Archive '<external>/dataset-new-version' -Destination '<external>/restore-new-version'
```

`-Datasets`, `-AudioRoot`, `-ImageRoot` cho phép chỉ định roots đầu vào khác. Tool chỉ nhận normalized+draft pairs, kiểm source identity/schemaVersion2 và mọi media checksum/size. Từ chối media thiếu/sai, traversal, symlink, destination đã tồn tại hoặc nằm trong Git worktree. Sau copy kiểm hash source trước/sau và destination; restore kiểm manifest **trước** khi tạo thư mục mới. SHA-256 chứng minh tính toàn vẹn so với manifest đã tin cậy, không tự chứng minh bản quyền hoặc tính nguyên bản ETS. Không binary media/SQL dump/server JAR trong Git.

## SQL backup bằng pg_dump

Script dùng `pg_dump --format=plain --encoding=UTF8 --no-owner --no-privileges`, không `--clean`/`--create`, không INSERT tự viết. Backup database đầy đủ: có thể gồm account hashes/session/app data, vì vậy giữ private ngoài Git. Quyền SQL trên schema phù hợp cần có trước khi chạy. Dùng `PGPASSFILE` riêng hoặc `PGPASSWORD` trong phiên; scripts không nhận/in mật khẩu. Ví dụ dưới đây lấy credentials từ password file đã cấu hình, không ghi literal password:

```powershell
$env:PGPASSFILE = '<private-password-file>'
.\scripts\backup-toeic-db.ps1 -PostgresBin '<postgres-18>/bin' -Database '<source-db>' -HostName '<db-host>' -Port 5432 -UserName '<backup-role>' -OutputFile '<external>/new-version.sql'
```

Giữ `.sql` và `.sql.metadata.json` cùng nhau. Metadata ghi SHA, size, source PG major/version và UTF8. Không ghi đè output. Binary media ở filesystem cần JSON/media archive riêng, pg_dump không chứa audio/images.

## SQL restore: chỉ tạo TEST DB mới

```powershell
.\scripts\restore-toeic-db.ps1 -PostgresBin '<postgres-18>/bin' -Database 'toeic_restore_test_newrun' -ConfirmRestoreDatabase 'toeic_restore_test_newrun' -InputFile '<external>/new-version.sql' -HostName '<isolated-test-host>' -Port 5432 -UserName '<test-db-owner>'
```

`Database` phải khớp `toeic_*test_*`, xác nhận đúng tên, target **chưa tồn tại**. Script kiểm checksum/UTF8/metadata, từ chối dump chuyển database/DDL database, kiểm PG major tương thích, rồi tự CREATE DB từ template0 và kiểm rỗng. Không pre-create DB, không DROP/TRUNCATE hoặc ghi đè DB có sẵn. Chạy `psql -X --single-transaction -v ON_ERROR_STOP=1 --file`, ghi receipt chỉ sau exit0. Nếu lỗi, giữ DB TEST do lượt này tạo để điều tra; script không xóa DB. Chỉ restore dump tin cậy do workflow này tạo; checksum không biến SQL không tin cậy thành an toàn. Receipt `SQL_RESTORE_ONLY_NOT_ETS_ACCEPTANCE` chưa chứng minh API/catalog hay nội dung đề đúng.

## Chuyển laptop và cấu hình media roots

1. Chép archive, SQL+metadata (nếu có), checksum và source Git commit sang thiết bị mới. Verify archive bằng tool Java trước khi dùng. Không cần đăng nhập Zenlish hoặc fetch lại website.
2. Restore archive sang thư mục mới ngoài Git. Với server tương thích EX03/05/07 đặt `TOEIC_AUDIO_ROOT=<restored>/audio-resources`, `TOEIC_IMAGE_ROOT=<restored>/image-resources`; dưới roots phải có đúng `<examId>/<localFile>`. `sourceUrl` chỉ provenance, JavaFX nhận media từ API local hiện có.
3. Nếu restore DB: dùng PostgreSQL cùng major hoặc mới hơn có kiểm chứng, role mới quản lý schema, server JAR tương thích migration/source version. Restore tới TEST mới trước, cấu hình `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD` riêng. Không trỏ vào DB QA/dev/shared để thử.
4. Kiểm counts, sorted-row fingerprints hoặc dữ liệu từng bảng `exams`, `exam_questions`, `exam_options`, `exam_question_groups`, `exam_sources`, `exam_media_assets`, `exam_content_blocks`; so key hash theo question order, source/group/media references và file SHA+size. Với dataset VERIFIED, login bình thường và GET `/api/v2/exams`: đúng examId, selectable=true, blockedReasons rỗng. Token không ghi log.
5. Với ETS hiện tại, không gọi import để thử vượt gate. Khi đã có review evidence đầy đủ, dùng importer V2 nguyên bản trên DB TEST mới rồi lặp backup→restore→catalog/media checks. Nếu chỉ có JSON archive thì import lại vẫn phải qua verification gate; tool không bỏ gate.

## Kết quả MOCK thực tế

[Receipt](recovery/mock-roundtrip-report.json): PostgreSQL18.1 (`180001`), source và restored DB trong cluster mới/cổng loopback riêng, đã dừng owned server+cluster sau kiểm thử. Không sửa schema/Flyway; JAR server có sẵn được copy read-only và pin SHA `967b6f00389e1510f9af97348f42f68cccb594601c166d5ce98f43cb73be6611`, không coi nhánh ETL cũ tự có backend V2.

Real V2 import MOCK HTTP201, source/catalog và catalog sau restore selectable=true, blockedReasons rỗng. Trước/sau khớp200 questions,775 options,76 groups,57 media,286 content blocks,1 source; fingerprints toàn row và200 scoring keys giống nhau.57media MOCK được copy ra backup ngoài Git, restore sang roots khác rồi server restored dùng các file đó; SHA/size khớp57/57. SQL SHA `63b1a7cb0fb6a5a74958d026ace7c56ca2565bb8bce54abecf7ff05fee62f6f4`.

Negative checks thật: target đã tồn tại, sai tên xác nhận, tên ngoài TEST, SQL checksum hỏng trước CREATE, backup overwrite đều bị từ chối; target fingerprints không đổi. Generator MOCK chạy trên bản sao fixture riêng, không sửa fixture tracked hoặc media Zenlish. [Harness](../../tools/exam-dataset/verify-db-roundtrip.ps1):

```powershell
.\tools\exam-dataset\verify-db-roundtrip.ps1 -PostgresBin '<postgres-18>/bin' -JavaExe '<jdk-21>/bin/java.exe' -ServerJar '<compatible-v2-server.jar>' -ExternalBackupRoot '<external-backups>'
```

Harness chỉ tự tạo cluster TEST, credentials ngẫu nhiên không log/hardcode, import fixture MOCK200 qua HTTP, và dừng đúng processes của mình. Không delete cluster/DB để còn evidence. Không dùng fixtures MOCK làm bằng chứng ETS đã verified, không kiểm GUI/hearing/LAN.

## Giới hạn phiên bản schema và tính sẵn sàng

Plain pg_dump lưu schema/Flyway history của nguồn; restore không tự chuyển schema về phiên bản cũ, không dump role/global cluster settings. PostgreSQL mới hơn không đảm bảo app cũ đọc schema mới. Kiểm binary pg_dump/psql, PG major, extensions/collation và Flyway history với JAR tương ứng ở TEST trước khi chuyển hệ thống. Migration tương lai cần workflow riêng đã review; không sửa migration hoặc importer để ép archive hiện tại PASS.

JSON/media restore: **PASS captured data**. PostgreSQL+catalog+media restore: **PASS MOCK only**. ETS original-content/answer/document verification và ETS end-to-end acceptance: **BLOCKED / NOT RUN**. Hai tình trạng này được giữ riêng trong manifest và reports.
