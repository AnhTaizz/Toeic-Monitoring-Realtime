# Sửa UI phòng thi, lịch sử sau nộp và fullscreen — 27/10/2026

Nhánh `codex/fix-room-ui-history-fullscreen`, tạo từ `origin/fix/candidate-post-submit-history-fullscreen` tại `1187f0d`. Kiểm thử trên working tree chứa bản sửa; SHA trong receipt là SHA nền. Theo yêu cầu người dùng: không dùng `codex-with-chatgpt`, chỉ commit local, không push/PR/merge.

## Thay đổi

- **Chọn câu:** nền sáng trắng/cam, vùng cuộn sáng, nút đóng tiếng Việt, kích thước theo vùng hiển thị và trạng thái câu hiện tại/đã làm. Điều hướng vẫn dùng question ID của đề.
- **Lịch sử:** token tài khoản trước đây bị admission filter loại mọi lượt thi trong phòng PIN. Thêm quyền đọc riêng cho lịch sử/status của lượt đã chốt, đúng candidate và member đã JOIN. Token phòng vẫn bind một attempt; token tài khoản không được dùng quyền này để đọc đề/media, takeover, save/submit hay monitoring. Tab lịch sử tự làm mới; bảng co cột và xếp các kết quả đã tải theo thời gian kết thúc giảm dần.
- **Xác nhận giám thị:** dùng style sáng hiện có, nhãn tiếng Việt, nút cam xác nhận và nút hủy là mặc định khi Enter.
- **PIN → phòng chờ:** bỏ bước dựng màn chọn ca cũ rồi tìm nút để tự bấm. Dùng attempt trong JOIN response; hiện trạng thái kết nối, xác minh scope rồi mở lobby V2. Giữ cleanup/generation để callback của màn đã rời không mở lại phòng.
- **Fullscreen:** giữ một Scene chính khi đổi root; tránh việc thay Scene làm thoát native fullscreen trên Windows. Bật fullscreen khi chuyển màn, giữ Escape và thao tác OS. Gỡ listener close guard khi rời/đóng màn thi để Scene dùng lại không giữ listener cũ.

Chạm giao diện B, quyền đọc kết quả A và cách B khởi động hook collector C. Cần A review quyền đọc; không đổi DTO/wire, DB, START/deadline, observer hay thuật toán collector. Không sửa tracker hoặc ghi giờ công/DONE.

## Kiểm chứng

Các lệnh chạy ngoài sandbox để Java/HTTP/PostgreSQL tạo socket loopback; lần đầu trong sandbox bị chặn socket, không phải kết quả kiểm chứng sản phẩm.

- Compile client/server và test source: PASS.
- Client focused, bật `TOEIC_LOGIN_GUI`, `TOEIC_RICH_GUI`, `TOEIC_PROCTOR_GUI`, `TOEIC_MONITORING_SCOPE_GUI`: 72 PASS / 1 SKIP trên 73 ca. SKIP là bộ chụp thêm nhiều màn giám thị, cần `TOEIC_UI_SCREENSHOTS`; ảnh cho các lỗi đang sửa được chụp riêng. Các suite: `LoginSceneGuiTest`, `RichExamViewGuiTest`, `ProctorRoomViewGuiTest`, `ExamHistoryReadTest`, `ExamScopeRefreshGuiTest`, `ExamMonitoringIntegrationTest`. [Kết quả từng suite cuối](client-final-suites.json); log local: `target/room-ui-fixes-20261027/client-tests-final.log`.
- PostgreSQL/JavaFX: **82 ca PASS / 0 FAIL-ERROR / 0 SKIP** khi lấy kết quả cuối của các suite không trùng nhau: 79 ca server/legacy từ `room-ux-20261027T102535Z-4a4796ee`, cộng 3 ca `AccountPinFlowPostgresGuiTest` chạy lại riêng trong `room-ux-20261027T103213Z-facf1bab`. Đây là kết quả tổng hợp từ hai lượt, không phải một invocation. Xem [từng ca cuối](postgres-final-cases.json), [receipt regression](postgres-regression.json), [receipt PIN cuối](postgres-pin-final.json). Các cluster đều còn 0 schema và dừng exit 0.
- E2E production Admin/Proctor/hai Candidate với 2/2, 1/2, 0/2 READY: giữ cùng Scene sau PIN, không dựng màn `candidate-attempts`, fullscreen tại lobby và sau Hoàn tất, bài vừa nộp có trong lịch sử; START confirmation nền trắng và Hủy mặc định; giữ reconnect/READY/Listening/Reading/autosave/USB status. Đề và media MOCK, HTTP/WS/PostgreSQL/JavaFX và CIM thật.
- Backend: lịch sử/status cho đúng chủ bài SUBMITTED/TIMED_OUT, cả token cũ và đăng nhập mới; trước chốt/khác candidate/khác bound attempt/member chưa JOIN bị từ chối. Token tài khoản đọc kết quả vẫn bị 403 ở đề/preparation/takeover/answers/submit/monitoring. Giữ test CLOSED/INTERRUPTED phải fail closed, và proctor mất quyền sau xóa assignment. Có cả submit thực trong `zeroReadyStartsButOnlyLateReadingCanBeSubmitted`; fixture terminal gán bằng SQL ở ca riêng được ghi rõ MOCK.
- Đóng gói cuối: `mvn -pl client -am -Dtest=RichExamViewGuiTest -Dsurefire.failIfNoSpecifiedTests=false package` PASS, renderer 9/9 PASS sau thay listener close guard (trùng suite đã tính ở trên). `mvn -pl server -am -DskipTests -Dtoeic.server.artifact-name=server-ui-fixes-20261027 package` PASS. Server dùng tên JAR riêng; không chạy/restart Server từ lệnh build. [Đường dẫn artifact và SHA-256](built-artifacts.json). Log local: `target/room-ui-fixes-20261027/{client-package,server-package}.log`.

Lượt PG đầu (`room-ux-20261027T102004Z-4e8e5c18`): 74 PASS / 8 FAIL / 0 SKIP; cleanup 0 schema, stop exit 0. Bốn lỗi ở hai test GUI chờ ID event Chrome MOCK: collector thật có thể đã ghi Chrome trước và projection 30 giây giữ ID đầu. Test sửa để kiểm event MOCK tồn tại trong raw và UI nhận đúng ID từ REST projection. Hai lỗi assertion cũ đòi giám thị mất quyền đọc history khi chốt bài: sửa theo hành vi đã có trên nhánh nền, vẫn kiểm bị từ chối sau xóa assignment. Hai lỗi trạng thái CLOSED/INTERRUPTED ở đường đọc status mới: đã thêm điều kiện final state/submitted_at vào truy vấn fallback; giữ nguyên test từ chối.

Lượt PG thứ hai: 80 PASS / 2 FAIL / 0 SKIP. Hai assertion còn lại chờ "Chỉ làm Reading" sau khi bài đã chốt, trong khi nhánh nền đã ưu tiên nhãn "Đã nộp". Chuyển kiểm Reading-only về lúc ACTIVE, kiểm "Đã nộp" khi hoàn tất; chạy lại riêng ba biến thể PIN và tất cả PASS. [Receipt lượt đầu](postgres-initial.json) giữ nguyên để đối chiếu.

Ảnh thật từ JavaFX; đề/tài khoản/media **MOCK**. Không khẳng định nghe bằng tai, LAN hai máy, USB vật lý hoặc nghiệm thu MSI.

## Ảnh sau sửa

- [Cửa sổ chọn câu](question-palette.png)
- [Xác nhận bắt đầu phòng thi](proctor-start-confirm.png)
- [Lịch sử ngay sau nộp](candidate-history.png)

Để sử dụng bản sửa cần build/chạy lại cả Server và Client. Phiên bản MSI/app hoặc Server đang mở từ trước không tự nhận thay đổi source.
