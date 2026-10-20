# EX-03 — bằng chứng importer

Source `9244321692e348cfafcb2e85afe963398477b5e4`, base main `3bdefe6ccc346a316a91f1e280fe4f5c74600cfd`. [Report API/schema/acceptance/failure/giới hạn](../../docs/EX03_RICH_IMPORTER_VERIFICATION.md).

Full run `ex03-20261019T154906Z-34c16188`: **1098PASS/6SKIP/0FAIL/0ERROR**,114case mới EX03 PASS; PostgreSQL18.1/Flyway/HTTP thật, nội dung/media MOCK CC0. Fresh V1–V12,V14 (13scripts), upgrade12→14, legacy preservation/import/session/scoring,200Q/775options/76groups/57media/54audio,3documents, strict negatives/RBAC/missing/corrupt/barrier race/rollback và Windowsjunction đều đã chạy. Không là production200Q, hearing/GUI/physical acceptance; EX05/07/06 và tích hợp V13MON07 NOT RUN.

| File | Nội dung |
|---|---|
| [verification.json](verification.json) | source/run/toolchain/totals/suite counts,114case mới và6SKIP; unit9FAIL trước sửa ghi riêng |
| [raw-manifest.json](raw-manifest.json) | SHA256 của logs/XML/result/test snapshot private; không công bố nội dung có environment |
| [build-receipt.json](build-receipt.json) | Hash/size3JAR; server chứa importer/V14, không staleV13; không claim packaged GUI launch |

Raw ở target ignored theo runId, gồm cả các lượt failure; database test/schema sở hữu riêng, cleanup0schema/clusterStopExit0, password file đã xóa/env phục hồi. Không commit raw XML/log/token/credential hoặc PGdata. Main/workspace gốc và seed importable:false được giữ; không sửa tracker/hours.

Lệnh đã chạy qua runner Windows PowerShell5.1, thay đường dẫn tool bằng bản cài tại máy chạy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-ex03-postgres.ps1 -Mode Full -PostgresBin "<PostgreSQL18/bin>" -JavaHome "<JDK21>" -MavenCommand "mvn.cmd"
```

Runner chạy `mvn --batch-mode --no-transfer-progress clean verify` với TOEIC_PG_INTEGRATION=true và credentials test ngẫu nhiên. CI PR chạy riêng không tự bật PG; đối chiếu HEAD/Checks riêng, không suy PG hoặc physical PASS từ CI.
