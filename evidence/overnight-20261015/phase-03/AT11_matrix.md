# AT11 — PARTIAL cấp toàn case

| Subcase | Bằng chứng mới | Nhãn / kết quả |
|---|---|---|
| Server40/client42, request không tới | ExactAT11Flow HTTP/PG, full-map/revision giữ42, retry cùng object request | REAL DB/HTTP, SIMULATED request loss, PASS |
| COMMIT42 nhưng không ACK | CompletableFuture fence sau response200 production, đọc lại42, writer giữ, SAVED chỉ khi authoritative | REAL DB/HTTP, SIMULATED response loss, PASS |
| App mới takeover | Entry thứ2 nhận epoch+1/answers42; old writer409STALE; ACK cũ sauclose không đổi newwriter | REAL2model cùnghost, PASS; không LAN |
| Submit/timeout/deadline | ExamFlow41checks/0GAP + PG transaction/concurrency tests trong package839 | REAL DB/HTTP, fault subsets labelled, PASS kỹ thuật |
| Shared Reading/Listening | Production Application GUI8group, WAVMedia thật; samewriter, reconnect không takeover/autoplay; Reading giữ Listening committed | REAL local GUI/PG/HTTP/WS, TESTexam/WAV, speakerconfirmation SIMULATED, PASS subset |
| History/result | GUI historySUBMITTED và query raw status ownership/foreign scope trong test | REAL local GUI/DB; no takeover, PASS subset |
| Stale callback/shutdown | Model lifecycle generation/latecallback tests + GUI cleanup | MOCK model / REAL ownedshutdown, PASS subset |
| Ngắt mạng vật lý/máy khác/khởi động lại thủ công | Chưa có operator2máy thật/physical test | NOT RUN |

Không nâng AT11/LT/IT toàn hệ thống từ subset; A/B/C human review NOT RUN.
