# Kết quả LAN thực tế

**PHYSICAL_LAN_NOT_RUN / SECOND_MACHINE_NOT_RUN.** Người dùng xác nhận chỉ có máy hiện tại và yêu cầu chuẩn bị runbook; không có máy B/remote access/operator. S0–S7: NOT RUN. IP LAN/port liên máy, nghe thật, physical disconnect, cùng build trên host B: NOT VERIFIED.

Local real HTTP/WS/PG/JavaFX/EXE được báo riêng trong FINAL_REPORT và release; fault đóng socket và xác nhận nghe tự động có nhãn SIMULATED. Không dùng chúng để điền kết quả LAN.

Mẫu ghi từng scenario:

| RunId/TestId | A/B/OS/source/hash | Operator/UTC | Expected | Actual/raw evidence | Fault label | Status |
|---|---|---|---|---|---|---|
| Chưa chạy | Chưa có host B | Chưa có | Theo checklist | Chưa có | NOT RUN | NOT RUN |
