# Automated technical review — HUMAN C REVIEW NOT RUN

- P1 đã sửa phase01: chỉ `ExamService.createSession` cấp ACTIVE runtime; Listening reorganize gọi đường này, kiểm ID riêng trước mutation. `ON CONFLICT DO UPDATE state=ACTIVE` đã bỏ, không làm sống lại final.
- Seed runtime chỉ tạo user, không cấp attempt; SQL test/history legacy trực tiếp có nhãn TEST, không là issuance production. DB raw identifier giữ128codepoint/noNUL; WS/heartbeat/collector/message ID giữ ASCII. Không đổi auth/me ACTIVE-only.
- Full default/capability opt-in giữ nguyên. Monitoring syncEpoch khác writerEpoch; realtime generation guard không đổi. Queue RAM và state RAM/gap DB vẫn theo contract cũ.
- C cần review E1 window/seed/censoring/CPU sampler: policy test chỉ root sở hữu; cùng schedule giữa4poll, actual life khác target hold. Parent latency cận trên không kernel. Resource sampler overhead chung; CPU collector riêng process parent gồm harness. Không có RSS. Raw process metadata chỉ local, CSV public loại PID/name.
- C1/C2 live regression sẽ chạy trên final JAR, chưa được kế thừa kết quả PR35; delta gate/LAN/human review NOT RUN.

## Kết quả E1 bổ sung phase06

12REALrun thu đủ, nhưng11observation PID-only missing-start (8target200,3target800), GTstart vẫn đọc được. GiữINCONCLUSIVE; aggregate miss bounds thay cho imputation, không công bố mean thiếu3run như đầy đủ. CPU samples239+ mỗi run có; heap parent có carryover vì cùngJVM. C cần review khả năng kết luận, không gọi E1acceptancePASS. [Data](../phase-06/e1/analysis.md).
