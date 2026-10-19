# EX-05 — checkpoint triển khai ảnh có quyền

Base: EX03 `03d42e3c9084b2def3301349fbdbfb302a11e51e`, stacked trên PR75; main live `3bdefe6ccc346a316a91f1e280fe4f5c74600cfd`. PR75 CI39282268045 SUCCESS, chưa merge. PR74 Draft `7cb7478f2fba4478fd39925db930464d6cb34581` giữ V13; không sửa nhánh MON.

| Audit | Trạng thái | Bằng chứng source |
|---|---|---|
| V11/V12 trên main, scoped typed FK/deferred key | ALREADY_DONE | EX04 + migrations bất biến |
| Importer graph200/7Part, source map, transaction/recheck | ALREADY_DONE | PR75; evidence EX03 lịch sử1098PASS/6SKIP, chưa coi là lượt mới |
| Image bytes/hash/decode/path | NEED_FIX → IMPLEMENTED | RichMediaValidator.readImage trả chính bytes vừa kiểm; cap8MiB trước read |
| Part1 graph allowlist/canonical six-slot manifest | IMPLEMENTED | PartOneVisualManifest; độc lập fixture approved hash |
| Endpoint auth/scope/headers/commit | IMPLEMENTED, FAIL_CLOSED | RichImageService/Controller; không RichImageSessionAuthority →409 trước filesystem |
| V2 state/version/writer/epoch authority | BLOCKED_BY_EX07 | Interface bắt buộc kiểm dưới khóa session→attempt; chưa bean cấp quyền |
| V13/V14 integration | MERGE BLOCKED | V13 đang PR74 Draft; cần phối hợp merge/deploy13 trước14. Không outOfOrder/placeholder |
| JavaFX/provider/visual lease/START | NEED_IMPLEMENT | EX07/EX06 tiếp theo; chưa claim FE READY |

Ba review AI độc lập backend/frontend/security đã đọc source, không chạy test riêng hoặc tạo human approval. Review phát hiện mapping DB transient500 thay503 tại image boundary; đã bổ sung mapping503 và unit fault injection.

Verification đang chạy: targeted real PostgreSQL18.1/HTTP, MOCK200 CC0. Kết quả cuối/chính xác SHA sẽ ghi sau run; không gọi INTEGRATED/DONE. Focused XML tại thời điểm checkpoint có25case RichImageTest PASS; test503 mới thêm chưa được chạy trong lượt đó. Launcher thử đầu FAIL tham số PowerShell, lần sau FAIL import test trùng AccessDeniedException; đã sửa. Direct native Maven wrapper sau đó dừng bởi stderr Mockito/PowerShell NativeCommandError; không dùng launcher đó để kết luận full PASS. Runner dùng redirected async stdout/stderr.

Limits: image HTTP200 positive cần EX07 authority thật; chưa giả bean test để tuyên bố integration. Root cấu hình hiện resolve sau authorization theo implementation EX03; cần pin trusted root ổn định khi EX07 nối hoàn chỉnh. GUI_PHYSICAL_NOT_RUN; hearing/LAN/package mới NOT RUN. Tracker/hours/human signoff giữ nguyên.

## Clean verification source05cc694 — 20/10/2026 ICT

`mvn --batch-mode --no-transfer-progress clean verify` từ `scripts/verify-ex05-postgres.ps1 -Mode Full` tại **05cc694f12629e173c92707ec1a94399d0b55930**, Windows11/JDK21.0.8/Maven3.9.11/PostgreSQL18.1 TEST riêng loopback/SCRAM: **1133 total =1127PASS/6SKIP/0FAIL/0ERROR**. Run `ex05-20261019T165521Z-2e5d442d`;0schema còn lại, cluster stop0. [Summary máy sinh](../evidence/ex05-authenticated-images-20261019/verification.json) giữ tên case/skips/hash XML, không raw env/credentials.

29case mới EX05 PASS:26RichImageTest (metadata/files thật, policy/transaction fault MOCK) +3RichImagePostgresTest (PG/HTTP/Flyway thật, đề/media MOCK CC0). Golden canonical hash khớp fixture approved; sáu slot reuse một asset; metadata/context invalidation; bytes/hash/dimension/bounds; auth owner/foreign/role/missing/conditional; không authority trả409 trước bytes; graph chỉ Part1; real V13 thêm sau14 bị Flyway default từ chối. Importer114case và legacy regression cùng lượt PASS. Windows junction real test PASS trong RichMediaValidatorTest; không gọi hai symlink SKIP là junction PASS.

Targeted trước commit:200PASS/0SKIP, runtime chưa commit nên sourceSha result vẫn03d42e3, chưa gồm unit503 vừa thêm; không dùng lượt đó thay clean source05cc694. Hai lỗi launcher/compile ban đầu giữ thông tin tại trên; không tính thành test assertions FAIL.6SKIP giữ tên trong JSON: symlink2, GUI/result1, installed1/proctorGUI1/packaged1; GUI_PHYSICAL_NOT_RUN, hearing/LAN mới NOT RUN.

**READY_FOR_REVIEW nền EX05; chưa tích hợp authority EX07/JavaFX. MERGE BLOCKED V13/V14/dependency PR75; không merge/approve/human acceptance/tracker DONE.** Commit tiếp theo chỉ hồ sơ sau source đã kiểm. GitHub CI mới theo HEAD PR riêng, không gọi local PG là CI.
