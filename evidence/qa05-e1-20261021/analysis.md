# Phân tích E1

Thiết kế chiến dịch: xem campaign-status.json; PARTIAL không phải chiến dịch E1 đầy đủ. Không tự suy human review từ số run.

Sinh từ raw/checksum; latency chỉ detected, parent launch API → scan-end (cận trên), không clock kernel. Memory=heap JVM parent; CPU một core, scope collector thread và parent process riêng.

Analyzer SHA256: `0c049cbcddd4b780698d36b4b55a7c1152f214e33d3c728f0f6c41d657bca950`. Các bảng dùng trung bình và phương sai mẫu giữa các run; không dùng ba dòng duration của một run như ba lần lặp độc lập.

Dữ liệu: 12 run; 360 trials; eligible=360; censored=0; inconclusive=3.

| Poll ms | Collector CPU % một core: mean / variance | Parent CPU %: mean / variance | Heap mean MiB: mean / variance |
|---|---|---|---|
| 250 | 17.010991751689513 / 0.3556569280322702 (n=3) | 19.39071096055864 / 0.08738007452786874 (n=3) | 70.41153779559666 / 2188.2646095166706 (n=3) |
| 500 | 9.021725910703283 / 0.14468360012612988 (n=3) | 11.21396006326415 / 0.18114522271784958 (n=3) | 44.44830788506402 / 99.56642409542458 (n=3) |
| 1000 | 4.94245093761342 / 0.039117031946499535 (n=3) | 7.160886091115546 / 0.13945242835466115 (n=3) | 68.75916917588975 / 674.9441564984065 (n=3) |
| 2000 | 2.5670380536910815 / 0.047254257350694516 (n=3) | 4.637227672927096 / 0.026732435368430077 (n=3) | 98.28694695366754 / 683.495185059469 (n=3) |

CPU trong run = tổng delta CPU / tổng delta wall của khoảng đọc được, không trung bình các phần trăm. resourceStatus PARTIAL nếu coverage <95%, gap >2 chu kỳ lấy mẫu, counter thiếu/giảm hoặc heap thiếu. Boundary không nội suy. Heap mean là trung bình mẫu, peak là cực đại được lấy mẫu, không peak liên tục. Các giá trị PARTIAL giữ để chẩn đoán, không nghiệm thu CPU toàn cửa sổ.

Variance CPU có đơn vị %², heap có đơn vị MiB²; n là số run đọc được metric, None nghĩa thiếu dữ liệu/không đủ2run để tính variance. Peak heap và latency từng run ở summary.csv. Không dùng missed latency=0.

| Target hold ms | Parent-observed lifetime ms: min / mean / max |
|---|---|
| 200 | 326.0639 / 348.46713166666666 / 440.293299 |
| 800 | 918.7633 / 946.2455875 / 1053.879399 |
| 3000 | 3121.1394 / 3149.7015208333332 / 3296.3729 |

Target hold khác lifetime quan sát ở parent (gồm startup/READY/scheduling); không gọi target200ms là process lifetime chính xác200ms.

- Poll 250ms, target hold 200ms: n=2; mean miss=0.0; variance=0.0.
- Poll 250ms, target hold 800ms: n=3; mean miss=0.0; variance=0.0.
- Poll 250ms, target hold 3000ms: n=3; mean miss=0.0; variance=0.0.
- Poll 500ms, target hold 200ms: n=3; mean miss=0.4666666666666667; variance=0.02333333333333333.
- Poll 500ms, target hold 800ms: n=3; mean miss=0.0; variance=0.0.
- Poll 500ms, target hold 3000ms: n=3; mean miss=0.0; variance=0.0.
- Poll 1000ms, target hold 200ms: n=3; mean miss=0.8666666666666667; variance=0.003333333333333332.
- Poll 1000ms, target hold 800ms: n=3; mean miss=0.1; variance=0.0.
- Poll 1000ms, target hold 3000ms: n=3; mean miss=0.0; variance=0.0.
- Poll 2000ms, target hold 200ms: n=2; mean miss=0.75; variance=0.005000000000000009.
- Poll 2000ms, target hold 800ms: n=2; mean miss=0.6499999999999999; variance=0.0049999999999999975.
- Poll 2000ms, target hold 3000ms: n=3; mean miss=0.0; variance=0.0.

Mẫu chưa kết luận theo reason: {'PID_ONLY_MISSING_START_INCONCLUSIVE': 3}.
Các mean miss rate có n<3 ở trên chỉ mô tả các run có toàn bộ identity xác định trong bucket; không dùng chúng như mean đầy đủ3repeats hoặc lặng lẽ loại unknown. Bảng dưới giữ toàn bộ mẫu eligible, gồm unknown.

| Poll ms | Target hold ms | Eligible / detected / missed / unknown | Miss range min..max |
|---|---|---|---|
| 250 | 200 | 30 / 29 / 0 / 1 | 0.0 .. 0.03333333333333333 |
| 250 | 800 | 30 / 30 / 0 / 0 | 0.0 .. 0.0 |
| 250 | 3000 | 30 / 30 / 0 / 0 | 0.0 .. 0.0 |
| 500 | 200 | 30 / 16 / 14 / 0 | 0.4666666666666667 .. 0.4666666666666667 |
| 500 | 800 | 30 / 30 / 0 / 0 | 0.0 .. 0.0 |
| 500 | 3000 | 30 / 30 / 0 / 0 | 0.0 .. 0.0 |
| 1000 | 200 | 30 / 4 / 26 / 0 | 0.8666666666666667 .. 0.8666666666666667 |
| 1000 | 800 | 30 / 27 / 3 / 0 | 0.1 .. 0.1 |
| 1000 | 3000 | 30 / 30 / 0 / 0 | 0.0 .. 0.0 |
| 2000 | 200 | 30 / 7 / 22 / 1 | 0.7333333333333333 .. 0.7666666666666667 |
| 2000 | 800 | 30 / 10 / 19 / 1 | 0.6333333333333333 .. 0.6666666666666666 |
| 2000 | 3000 | 30 / 30 / 0 / 0 | 0.0 .. 0.0 |

Miss range là bounds trên chính tập mẫu thu được: known missed/eligible đến (known missed+unknown)/eligible; không gán một kết quả cho unknown, không phải confidence interval thống kê hoặc phép suy thời điểm process mở. PID-only thiếu start vẫn INCONCLUSIVE theo oracle gốc, không nới identity để làm kết quả đẹp.

SMOKE không là E1 final. E1 final cần đủ12run/3repeats, C review; chỉ đại diện máy/fixture/policy TEST này. Suite chạy tuần tự trong một parent JVM với collector/window mới mỗi run: heap/GC/JIT có thể carryover, các run không chứng minh độc lập hoàn toàn. Ứng dụng nền của workstation không bị đóng băng; rotated order giảm nhưng không loại mọi nhiễu. Không suy hành vi người dùng; không E2/delta/load.