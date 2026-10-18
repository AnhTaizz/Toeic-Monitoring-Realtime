# Dự thảo quyết định T3-C4 — C xác nhận, B review

**PENDING — chưa là owner decision.** Liên kết QD19 DELTA opt-in/live gate; QD10 là presence heartbeat và được giữ nguyên. Mốc20/10/2026.

Đề xuất: giữ **FULL làm baseline chính thức**. DELTA chỉ experimental opt-in sau capability ACK hợp lệ trên socket hiện tại; không tự promote, không kết luận hiệu năng. Main f864034; PR47 HEAD3044e5b chưa merge; thiếu C/B review, còn cache publication P1 cần B xử lý.

| Evidence | Hiện trạng và ý nghĩa |
|---|---|
| MT02 duplicate/retry | Replay/C2/C4 local có subset; không đủ nghiệm thu physical LAN |
| MT03 mất chuỗi/RESYNC | Có unit/local fault SIMULATED; giữ last-known, fresh FULL recovery |
| MT04 epoch cũ | Có reducer/server/local bằng chứng; còn cần LAN protocol checkpoint |
| MT05 client/server restart | A3/C2/local có; không thay restart thực trong topology A/B |
| MT06 FULL/DELTA bằng nhau | Có ba trace MOCK/replay oracle, local; không tự suy complete history khi có drop |
| Local C4 mới42/42 | Source3d93820, Windows GUI/ProcessHandle/Edge/PG/HTTP/WS REAL, faults SIMULATED; measurement INCOMPLETE do hard kill; evidence cũ, không chạy lại |
| Physical LAN A–H/F | 0 PASS/0 FAIL/41 NOT RUN; máy2 chưa có, ZIP kit chưa tìm lại |
| Capability/ACK/reconnect | Source và test hỗ trợ; PR47 cần vào main đúng gate; chưa nghiệm thu LAN |
| Human B review | PENDING |
| E2 | NOT RUN; không CPU/latency/byte-saving claim |
| Production blockers | Main CLOSE chờ PR47; cache concurrent publication P1 chờ corrective PR B |

Thiếu evidence không phải chứng minh DELTA sai. Tuy nhiên chưa đủ điều kiện thông qua functional gate. Nếu trước20/10 vẫn thiếu LAN/B/C, giữ FULL và ghi DELTA thử nghiệm chưa qua gate. Nếu sau đó đủ LAN và approvals, C có thể đề xuất đạt gate chức năng; hiệu năng vẫn cần E2 riêng.

Cần C điền: reviewed source SHA, thời điểm, tên người xác nhận, quyết định GIỮ FULL / đề xuất DELTA đạt gate / REQUEST_CHANGES, giới hạn và evidence. C status **PENDING**.
Cần B điền: callback/guard/UX/Reading–Listening/physical F/MSI hearing, source/evidence và review. B status **PENDING**.

Agent không ký, không sửa QD19/tracker để chốt. Quyết định cuối phải dựa trên final main và artifact thực được hash trên A/B, không dùng MSI0.3.10 hay app-image cũ như sourcePR47.
