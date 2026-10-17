# Topology — PHYSICAL_LAN_NOT_RUN

Phiên QA chỉ có một Windows 11 x64 host qua WSL interop; người dùng xác nhận chỉ có máy hiện tại; yêu cầu chuẩn bị runbook LAN. Nhiều JVM/loopback trong automation không chứng minh LAN. Chưa có operator/người nghe thật.

Topology để owner chạy:

```text
Máy A TEST: PostgreSQL (DB riêng) ↔ server QA :18080
            Candidate1 + Proctor ── HTTP/WS IP LAN thật ── Candidate2: máy B Windows
```

Cả hai máy dùng MSI0.3.7 source `9bfc75ec7c2d91af6f714602727389547c3b3288`, kiểm MSI/JAR hash trong `../release/ARTIFACT_MANIFEST.json`. Máy B chỉ cần Windows/runtime bundled, không PostgreSQL/JDK/IDE. IP thật, host name và thông tin process chỉ lưu raw riêng; public dùng A/B. Chưa thay firewall/binding dịch vụ đang dùng chung.
