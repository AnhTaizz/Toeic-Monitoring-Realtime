# Ref trước công bố và overlap với main mới

- Ban đầu origin/main `8f5ac40`; remote tiến tới `0b9c5fa` qua PR #36 `T3-B4 proctor session controls`, rồi **`c0fd9036ba340b0b0a686fb2ce637050b9a3b7b4`** qua PR #37 `T3-B5 Windows MSI installer`. Integration/PR #35 vẫn **`11c5da9ad3094a103cd6db93693aa64bbb132eaa`**, không đổi head.
- `merge-base overnight integration = 11c5da9`; `merge-base current-main integration = 8f5ac40`. Nhánh đích/merge-base cho Draft PR không đổi. Chỉ công bố nhánh snapshot riêng, không rebase/reset/force-push hay sửa main/PR #35/#36/#37.
- PR #36 có `client/proctor/ProctorSession{Api,Controller,View}` và sửa `ProctorDashboardView`. Overnight xây từ integration, dùng `dashboard/ProctorWorkflow/ProctorSessionView` và tab progress riêng. **B/owner cần chọn một luồng B4**, không ghép hai controller song song.
- PR #37 có `scripts/package-windows.ps1` cùng tên nhưng hợp đồng tham số khác, `smoke-windows-installer.ps1`, sửa `package-t3b1.ps1` và thêm installed-client test agent. **Pipeline B5 cần được đối chiếu, không overwrite script của owner.** Overnight còn có wrapper cấu hình runtime ngoài JAR và QA app-image smoke; cần giữ hành vi đã chọn qua test mới.
- Overlap thấy trực tiếp: `ProctorDashboardView`, `ListeningRuntimePostgresTest`, `scripts/package-windows.ps1`, `scripts/package-t3b1.ps1` và docs. Backend progress/ID boundary, AT11 và E1 là đề xuất riêng. Source overnight đã kiểm **chưa tích hợp với main c0fd903**.
- Evidence PR #37 ghi Temurin 21.0.10/Maven 3.9.15, portable WiX/SDK ở ignored target trên môi trường của lượt đó. Phiên overnight dùng Oracle 21.0.8/Maven 3.9.11; các path `target/t3-b5/tools/...` và SDK mặc định đã kiểm không tồn tại trên máy này. Không kế thừa MSI/687 tests/installed-client PASS của PR #37 để đổi kết quả overnight. Các kết quả BLOCKED/NOT RUN tại phase 05 vẫn giữ nguyên theo thời điểm và môi trường thực.

Kế hoạch trước merge vào main (chưa thực hiện):

1. B/owner đối chiếu B4 của PR #36 và overnight, chọn một controller import/create/READY/start/reorg rồi ghép progress committed. A/C review endpoint và QD-29. Đối chiếu B5 PR #37, chọn một pipeline và giữ các yêu cầu Unicode/runtime config/no-secret/no-TEST-agent trong artifact phát hành.
2. Integrator được phép tạo worktree mới để hòa integration/PR #35 với main mới và các follow-up đã chọn. Không reset nhánh/PR người khác, không bật delta.
3. Chạy lại clean/package với PostgreSQL opt-in, AT11/GUI/permissions/Listening/reorg và build/installed-client smoke trên code đã ghép; đếm XML mới, không kế thừa 848 hoặc 687. Raw E1 đã đo giữ nguyên; nếu collector/harness/JDK/policy đổi thì dùng runId mới, không sửa số cũ.
4. Có review người thật rồi mới quyết định merge. Draft follow-up không chứng minh PR #35/main merge-ready. CI/LAN/nghe thật/máy thứ hai vẫn là các mục riêng.
