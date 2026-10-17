# Phân tích E1

Thiết kế chiến dịch: xem campaign-status.json; PARTIAL không phải chiến dịch E1 đầy đủ. Không tự suy human review từ số run.

Sinh từ raw/checksum; latency chỉ detected, parent launch API → scan-end (cận trên), không clock kernel. Memory=heap JVM parent; CPU một core, scope collector thread và parent process riêng.

Analyzer SHA256: `ae6a522070f7770cbc8f2eeb6a95e7b0606d2fd5ae66d375960f2dfb9ecffb52`. Các bảng dùng trung bình và phương sai mẫu giữa các run; không dùng ba dòng duration của một run như ba lần lặp độc lập.

Dữ liệu: 12 run; 360 trials; eligible=360; censored=0; inconclusive=11.

| Poll ms | Collector CPU % một core: mean / variance | Parent CPU %: mean / variance | Heap mean MiB: mean / variance |
|---|---|---|---|
| 250 | 29.88129385847191 / 4.362065898327426 (n=3) | 34.719119879702404 / 5.2974811877019015 (n=3) | 58.80475220150418 / 1508.1324032603256 (n=3) |
| 500 | 18.850932791551532 / 3.1633424685780076 (n=3) | 23.636668590243897 / 4.292109151520786 (n=3) | 82.7168507999844 / 2154.1254605350305 (n=3) |
| 1000 | 10.324939612728688 / 0.6775033292418736 (n=3) | 15.101693409930416 / 1.396584997838431 (n=3) | 71.16631896760728 / 1978.2472855649785 (n=3) |
| 2000 | 5.543774501635268 / 0.40337652409838287 (n=3) | 10.459951914562552 / 0.9732595259088735 (n=3) | 59.20790744357639 / 1495.9994641718108 (n=3) |

CPU trong run = tổng delta CPU / tổng delta wall của khoảng đọc được, không trung bình các phần trăm. resourceStatus PARTIAL nếu coverage <95%, gap >2 chu kỳ lấy mẫu, counter thiếu/giảm hoặc heap thiếu. Boundary không nội suy. Heap mean là trung bình mẫu, peak là cực đại được lấy mẫu, không peak liên tục. Các giá trị PARTIAL giữ để chẩn đoán, không nghiệm thu CPU toàn cửa sổ.

Variance CPU có đơn vị %², heap có đơn vị MiB²; n là số run đọc được metric, None nghĩa thiếu dữ liệu/không đủ2run để tính variance. Peak heap và latency từng run ở summary.csv. Không dùng missed latency=0.

| Target hold ms | Parent-observed lifetime ms: min / mean / max |
|---|---|
| 200 | 431.185 / 540.3085608833334 / 766.874 |
| 800 | 1007.8105 / 1143.8000892 / 1358.4357 |
| 3000 | 3161.2406 / 3340.0246016749998 / 3505.4549 |

Target hold khác lifetime quan sát ở parent (gồm startup/READY/scheduling); không gọi target200ms là process lifetime chính xác200ms.

- Poll 250ms, target hold 200ms: n=0; mean miss=None; variance=None.
- Poll 250ms, target hold 800ms: n=3; mean miss=0.0; variance=0.0.
- Poll 250ms, target hold 3000ms: n=3; mean miss=0.0; variance=0.0.
- Poll 500ms, target hold 200ms: n=1; mean miss=0.2; variance=None.
- Poll 500ms, target hold 800ms: n=3; mean miss=0.0; variance=0.0.
- Poll 500ms, target hold 3000ms: n=3; mean miss=0.0; variance=0.0.
- Poll 1000ms, target hold 200ms: n=1; mean miss=0.6; variance=None.
- Poll 1000ms, target hold 800ms: n=2; mean miss=0.0; variance=0.0.
- Poll 1000ms, target hold 3000ms: n=3; mean miss=0.0; variance=0.0.
- Poll 2000ms, target hold 200ms: n=2; mean miss=0.8; variance=0.020000000000000014.
- Poll 2000ms, target hold 800ms: n=2; mean miss=0.45; variance=0.0049999999999999975.
- Poll 2000ms, target hold 3000ms: n=3; mean miss=0.0; variance=0.0.

Mẫu chưa kết luận theo reason: {'PID_ONLY_MISSING_START_INCONCLUSIVE': 11}.
Các mean miss rate có n<3 ở trên chỉ mô tả các run có toàn bộ identity xác định trong bucket; không dùng chúng như mean đầy đủ3repeats hoặc lặng lẽ loại unknown. Bảng dưới giữ toàn bộ mẫu eligible, gồm unknown.

| Poll ms | Target hold ms | Eligible / detected / missed / unknown | Miss range min..max |
|---|---|---|---|
| 250 | 200 | 30 / 27 / 0 / 3 | 0.0 .. 0.1 |
| 250 | 800 | 30 / 30 / 0 / 0 | 0.0 .. 0.0 |
| 250 | 3000 | 30 / 30 / 0 / 0 | 0.0 .. 0.0 |
| 500 | 200 | 30 / 18 / 10 / 2 | 0.3333333333333333 .. 0.4 |
| 500 | 800 | 30 / 30 / 0 / 0 | 0.0 .. 0.0 |
| 500 | 3000 | 30 / 30 / 0 / 0 | 0.0 .. 0.0 |
| 1000 | 200 | 30 / 11 / 17 / 2 | 0.5666666666666667 .. 0.6333333333333333 |
| 1000 | 800 | 30 / 29 / 0 / 1 | 0.0 .. 0.03333333333333333 |
| 1000 | 3000 | 30 / 30 / 0 / 0 | 0.0 .. 0.0 |
| 2000 | 200 | 30 / 6 / 23 / 1 | 0.7666666666666667 .. 0.8 |
| 2000 | 800 | 30 / 16 / 12 / 2 | 0.4 .. 0.4666666666666667 |
| 2000 | 3000 | 30 / 30 / 0 / 0 | 0.0 .. 0.0 |

Miss range là bounds trên chính tập mẫu thu được: known missed/eligible đến (known missed+unknown)/eligible; không gán một kết quả cho unknown, không phải confidence interval thống kê hoặc phép suy thời điểm process mở. PID-only thiếu start vẫn INCONCLUSIVE theo oracle gốc, không nới identity để làm kết quả đẹp.

SMOKE không là E1 final. E1 final cần đủ12run/3repeats, C review; chỉ đại diện máy/fixture/policy TEST này. Suite chạy tuần tự trong một parent JVM với collector/window mới mỗi run: heap/GC/JIT có thể carryover, các run không chứng minh độc lập hoàn toàn. Ứng dụng nền của workstation không bị đóng băng; rotated order giảm nhưng không loại mọi nhiễu. Không suy hành vi người dùng; không E2/delta/load.