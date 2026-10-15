# E1 — lệnh chạy Windows/JDK21

```powershell
mvn -o -B -ntp package
$sourceSha = (git rev-parse HEAD).Trim()
$jar = 'client/target/client-0.1.0-SNAPSHOT-all.jar'
java -cp $jar vn.edu.toeic.client.monitoring.harness.E1Cli --dry-run jpackage-out/e1 $sourceSha
java -cp $jar vn.edu.toeic.client.monitoring.harness.E1Cli --smoke-short jpackage-out/e1 $sourceSha
# Chỉ sau smoke thành công, máy có tài nguyên và khoảng thời gian riêng ~28 phút:
java -cp $jar vn.edu.toeic.client.monitoring.harness.E1Cli --full jpackage-out/e1 $sourceSha
python scripts/benchmarks/e1/analyze.py jpackage-out/e1/E1-<suite-id> jpackage-out/e1-analysis-<unique-id>
python scripts/benchmarks/e1/test_analyze.py
```

Raw không overwrite; mỗi suite/run UUID riêng. Full12run,15s warmup+120s measure,10 mỗi target hold200/800/3000ms. Lịch launch/seed1234 giống nhau giữa poll; thứ tự poll xoay mỗi repetition. Collector scheduleWithFixedDelay như production, chu kỳ thực gồm scan+delay. Policy TEST owned root PID/start, không nới production8tên.

Ground truth parent monotonic launch/exit, không lấy detection làm truth. Actual process lifetime gồm startup/READY; target hold không đồng nghĩa lifetime chính xác. Chỉ lifecycle nằm trọn window đủ điều kiện; censored và INCONCLUSIVE riêng. Latency detected-only là parent launch API→scan completion, cận trên, không thời điểm kernel. CPU collector thread và parent JVM một core, heap parent (không RSS), samples500ms; -1/null khi không đọc được. `e1-manifest.json` khóa resources và manifest gốc. Analyzer tạo CSV không PID/name, từ chối checksum sai, không sửa raw.

Thất bại giữ raw INCOMPLETE và dừng suite; chạy lại phải UUID mới. Hủy Ctrl+C chỉ dọn process thuộc run, không kill-by-name. Human C review chưa thực hiện; SMOKE không là E1 final. E2/load/LAN riêng.
