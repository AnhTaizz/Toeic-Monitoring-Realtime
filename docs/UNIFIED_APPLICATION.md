# Ứng dụng thống nhất Reading, Listening, B4 và progress

Nhánh `integrate/unified-reading-listening-b4-b5-20261015` tạo mới từ main `c0fd903`, hòa PR #35 và phần cần thiết của #38. [Quyết định từng conflict](../evidence/unified-20261016/conflict-resolution.md). Kết quả kiểm trên bản ghép sẽ nằm trong [verification](../evidence/unified-20261016/verification.md); số 848/687/MSI của các nhánh nguồn không phải kết quả mới.

## Thành phần giữ lại

Một `Launcher → ToeicClientApplication`, hai giao diện theo role server. Candidate dùng ExamEntry/CandidateExamView/ExamSession từ #35: một writer và full-map answers cho Reading/Listening, audio nhận epoch đó, history/result chỉ đọc. Reconnect giữ revision/local intent hợp lệ; takeover app mới bỏ writer cũ. Listening gián đoạn khóa phần nghe theo server, Reading tiếp tục trong deadline; không replay/resume.

Proctor giữ B4 #36 tại `client.proctor`, tab **Điều khiển ca / kết quả** là luồng import/create/READY/start/reorg duy nhất. Không có demo seeder hoặc controller #38 chạy song song. READY để tạo start intent mới phải còn trong 5 giây; retry luôn nguyên request cũ. Mất response đọc Listening authority và không tự phát. Tab **Tiến độ đã lưu** gọi một API page, không lấy đáp án local hoặc mở writer.

Progress: `GET /api/v1/proctor/attempts/progress?limit=50&afterAttemptId=<raw-encoded>`, theo assignment kể cả final, keyset C order, no-store. Không answers/writer epoch/correct options. `lastSavedAt` là decision_at của autosave đã commit, không timestamp COMMIT vật lý; submit-only có thể null. Lỗi giữ trang confirmed/readAt và nhãn stale; 401 trở về login; page không tiến trước ACK đọc hợp lệ. Không suy deadline local thành quyền ghi.

ID ACTIVE mới được kiểm toàn request trước mutation theo monitoring v0/max128. Không trim/transliterate để cấp lượt, collision không reopen final. Legacy raw history/query status giữ nguyên scope. QD-29 vẫn PROPOSED, NOT HUMAN APPROVED. QD-30 mô tả shared writer; QD-28 là MSI theo main.

## Chạy và kiểm

Dùng JDK21/JavaFX Windows và PostgreSQL cấu hình local `.env`, không đưa credential lên Git. Nạp DB variables vào child environment, đặt `TOEIC_PG_INTEGRATION=true` và `TOEIC_PROCTOR_GUI=true`, chạy `mvn -o -B -ntp clean test` và `mvn -o -B -ntp package`. Suite tạo/xóa schema TEST riêng. `TOEIC_INSTALLED_CLIENT` chỉ dùng khi có thư mục đã cài được phép; `TOEIC_PACKAGED_CLIENT` dùng thư mục app-image QA, không coi là installer. Hai opt-in này phải được ghi NOT RUN khi không có artifact.

[MSI và cấu hình runtime](WINDOWS_PACKAGING.md), [kiểm thủ công](ACCEPTANCE_B_MANUAL_MATRIX.md), [LAN hai máy](LAN_TWO_MACHINE_RUNBOOK.md). Không tự cài/gỡ, đổi firewall/registry hoặc xóa marker nghe. FULL mặc định; E2/gate20/10/load/human hearing/LAN chưa được tự nâng PASS. A review backend/QD-29/progress, B review UI/Media/packaging, C review monitoring/E1; không giả chữ ký.
