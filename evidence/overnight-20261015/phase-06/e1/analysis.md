# Phân tích E1

Sinh từ raw/checksum; latency chỉ detected, parent launch API → scan-end (cận trên), không clock kernel. Memory=heap JVM parent; CPU một core, scope collector thread và parent process riêng.

Analyzer SHA256: `e19c001b7fa5f1c9db2372407d7c183d6cd245dd5cd0295f2a241f8d2e4c379c`. Các bảng dùng trung bình và phương sai mẫu giữa các run; không dùng ba dòng duration của một run như ba lần lặp độc lập.

Dữ liệu: 12 run; 360 trials; eligible=360; censored=0; inconclusive=11.

| Poll ms | Collector CPU % một core: mean / variance | Parent CPU %: mean / variance | Heap mean MiB: mean / variance |
|---|---|---|---|
| 250 | 29.886005010420096 / 4.381437370304419 (n=3) | 34.721362103466774 / 5.319606865425505 (n=3) | 58.87430725736099 / 1511.1743434782957 (n=3) |
| 500 | 18.85541988934186 / 3.1792246425858055 (n=3) | 23.639588317332244 / 4.318947212925876 (n=3) | 82.76290521129572 / 2151.4144776265834 (n=3) |
| 1000 | 10.319298878935424 / 0.7000386669727707 (n=3) | 15.092699013300713 / 1.4260873507499552 (n=3) | 71.1941817322203 / 1974.159142167531 (n=3) |
| 2000 | 5.55641605155827 / 0.4004601316132693 (n=3) | 10.466915310769012 / 0.9641057296516294 (n=3) | 59.25552483083813 / 1496.2530627580425 (n=3) |

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

Đối chiếu hai đầu dải đo: collector CPU trung bình250ms=29.886005010420096% một core,2000ms=5.55641605155827%; miss rate targethold200ms tương ứng=None và 0.8 (tỷ lệ0..1).

SMOKE không là E1 final. E1 final cần đủ12run/3repeats, C review; chỉ đại diện máy/fixture/policy TEST này. Suite chạy tuần tự trong một parent JVM với collector/window mới mỗi run: heap/GC/JIT có thể carryover, các run không chứng minh độc lập hoàn toàn. Ứng dụng nền của workstation không bị đóng băng; rotated order giảm nhưng không loại mọi nhiễu. Không suy hành vi người dùng; không E2/delta/load.