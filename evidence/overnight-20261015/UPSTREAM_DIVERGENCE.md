# Ref cuối và overlap với B4 mới trên main

- Ban đầu origin/main8f5ac40; trước công bố remote/main đã lên **0b9c5fa**, merge PR36 `T3-B4 proctor session controls` (parent8f5ac40 +124e500). Integration/PR35 vẫn **11c5da9**, không đổi head.
- `merge-base overnight integration =11c5da9`; `merge-base current-main integration =8f5ac40`. Target/merge-base cho DraftPR mới không đổi, nên có thể xuất bản riêng nhánh snapshot để review, không rebase/reset/force-push hoặc sửa main/PR35/PR36.
- Main mới có `client/proctor/ProctorSession{Api,Controller,View}` và sửa ProctorDashboardView. Overnight viết từ integration theo prompt, dùng dashboard/ProctorWorkflow/ProctorSessionView và tab progress riêng. **Hai cách cài B4 cần B/owner đối chiếu và chọn một**, không tự ghép hai controller song song.
- Chỗ overlap đã thấy: ProctorDashboardView, ListeningRuntimePostgresTest và docs. Backend progress/ID boundary, AT11, E1, packaging/evidence vẫn là đề xuất riêng; chưa kiểm tích hợp với main0b9c5fa.

Kế hoạch trước merge vào main (chưa thực hiện):

1. B/owner review UI/controller củaPR36 và overnight, giữ một luồng import/create/READY/start/reorg, ghép tab progress đãcommit vào luồng được chọn; A/C review endpoint và QD29.
2. Integrator được phép tạo worktree mới để hòa integration/PR35 với main0b9c5fa và các follow-up đã chọn. Không reset nhánh/PR người khác, không bật delta.
3. Chạy lại clean/package với PG opt-in, AT11/GUI/permissions/Listening/reorg trên code ghép thật; đếm XML mới, không kế thừa848. E1raw đã đo vẫn giữ nguyên; nếu collector/harness/JDK/policy đổi thì runId mới, không sửa số cũ.
4. Review người thật rồi mới quyết định merge. DraftPR follow-up không có nghĩa PR35/main merge-ready. CI/LAN/human hearing/installer còn riêng.
