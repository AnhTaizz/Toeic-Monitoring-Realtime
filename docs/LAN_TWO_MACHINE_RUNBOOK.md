# LAN hai máy — NOT RUN

Chuẩn bị theo cấu hình thực, không tự sửa firewall/registry/service. Chọn hostServer có PostgreSQL và serverJava; máyWindows2 chạy candidate2; candidate1 trên host hoặc máy riêng, một proctor instance theo phân công. Hai cửa sổ trên một máy không là LAN. Credentials gửi ngoài Git, không đưa token vào URL/log.

```text
Máy A: PostgreSQL ↔ Server HTTP/WS ← candidate1 (hoặc máy thứ3)
                               ↑ LAN thật (IP/port đã kiểm)
Máy B: candidate2 → Server      │
Giám thị: proctor → cùng Server (ghi rõ host nào)
```

1. Ghi IP/port thật từ config server, host/OS/JDK/JavaFX/PG, timestamp/source SHA và SHA256 cùng một JAR/installer trên mỗi máy. Chỉ mở port đã được người quản trị cho phép; DB vẫn chỉ serverhost, candidate không cần DBcredential.
2. Kiểm server đang lắng nghe đúng interface, URL HTTP/HTTPS origin không path/query/credential; chạy preflight chỉ đọc. Cài/gỡ chỉ trên máy được cấp phép, ghi installer hash/WiX/runtime; không dùng IDE làm proof máy sạch.
3. Đăng nhập2candidate và proctor được assigned; tạo ca từ đề/audio hợp lệ. Xác nhận FULL mặc định, PROCTOR không collector, roster/scope đúng. Deadline/score/savedRevision luôn từ server.
4. Chạy [ma trận thủ công](ACCEPTANCE_B_MANUAL_MATRIX.md) AT11, LT01–LT04, IT01–IT04, MT01/07/08. Nghe thật, rút mạng thật khi đang phát, kill chỉ candidate app test đã xác minh; reconnect không autoplay. Giám thị nhận UNKNOWN/recovery/timeline; progress chỉ đãcommit và stale khi đọc lỗi.
5. Reorganization phải có xác nhận/lý do, old attempt before/after answers/deadline/result giống; new ID riêng. Lấy writer từ app2 rồi chứng minh app1 rejected. Final history chỉ đọc không takeover.
6. A kiểm transaction/DB failures và C kiểm heartbeat/epoch/full/resync/gaps. Chỉ restart server/DB test do run sở hữu khi được phép, không resetvolume hay đụng process khác. Đồng hồ host khác không tự coi đã đồng bộ; latency dùng cùng clockdomain.
7. Lưu command/config không secrets, screenshots đã lọc PII, HTTP/DB expected/actual, PID owned chỉ local, logs/checksums/source và binary. Ghi topology2máy thật, người thao tác/nghe/review thật. Thu raw giữ nguyên, summary script sinh. Không đánh PASS phần chưa chạy.

Human A/C review/QD29 approval và quyết định merge vẫn pending; runbook là chuẩn bị, không phải evidence LAN đã PASS.
