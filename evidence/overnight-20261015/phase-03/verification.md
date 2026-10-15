# Phase 03 — hardening AT11 / integration

Source `f31ba3d` (base phase02 `524f606`). **TESTED LOCAL / acceptance PARTIAL / HUMAN A-B-C REVIEW PENDING**.

- Mở rộng ExamFlowPostgresSmoke đang có: exactserver40/local42 với request blocked và ACKfence sauCOMMIT; samewriter reconciliation, nguyên request object, takeover nhận server42, oldwriter409, lateACK sauclose không sửa phiên mới. Không tạo smoke trùng tầng hoặc writer owner mới.
- `mvn -o -B -ntp clean test`: lượt đầu FAIL compile do lambda test trả response thay vì future. Sửa hẹp lambda; clean-retest **839 PASS /0FAIL/0SKIP**. `mvn -o -B -ntp package` **839 PASS /0FAIL/0SKIP**, đếm XML trong test-counts.json (protocol44/client571/server223/spike1). Source/JAR hashes đính kèm.
- ExamFlow production SpringJAR/PG/HTTP: **41checks PASS,0GAP** (loss fault SIMULATED; transport/DB REAL). GUI production **8groups PASS**,6ảnh từ Stage thật, MediaWAV/HTTP/WS/serverrestart/PG REAL; testexam/WAV, autoclick speaker SIMULATED, không human hearing. Artifacts GUI bền ở jpackage-out/overnight-gui.
- C1 lần đầu FAIL BeanDefinitionOverrideException do runner đưa toàn bộ server/test-classes vào classpath, vô tình component scan fixture. Retest staging đúng từng smoke class như script chuẩn: **C1 FULL/epoch/reconnect/duplicate/conflict/autoSTALE/TTL/capacity/shutdown PASS**, ProcessHandle/ownedEdge REAL, fault readingsMOCK. **C2 late-event/gap/UNKNOWN/retry/conflict/permissions/cleanup PASS**, network/PG/worker REAL, readingsMOCK/faultlossSIMULATED. Không đổi monitoring production.
- Lệnh Java: PropertiesLauncher với loader.path client/testclasses+clientfatJAR cho GUI/ExamFlow; riêng C1/C2 chỉ staging FullSnapshotSmoke*.class/LateEventsAndGapsSmoke*.class và clientfatJAR; heartbeat200ms/100ms đúng scripts chuẩn. Không reset sharedDB; chỉ schema/server/children do smoke sở hữu được dọn.
- E1raw đầu bị clean xóa đã ghi đính chính phase02; không bịa phục hồi. Các smoke sau chuyển jpackage-out. E1final/E2/load,2máyLAN,manualhear/cablekill và humanreview NOT RUN ở checkpoint này.

[Ma trận AT11](AT11_matrix.md). Log public là trích dòng thật + rawSHA; raw local workspace gốc target/overnight-tools. Không publish token/password/PID/process list cá nhân.

### Lọc ảnh trước công bố

Ảnh proctor06 có bảng ProcessHandle/PID thật nên chỉ giữ raw local ở jpackage-out, bỏ khỏi cây công bố. Nó đã xuất hiện trong checkpoint local9bbe106; nhánh checkpoint này sẽ không push. Bản công bố sẽ tạo nhánh snapshot sạch từ baseintegration với tree cuối đã audit, giữ nguyên các checkpoint local (không rewrite). Chỉ5ảnh candidate TEST được public trong phase03.
