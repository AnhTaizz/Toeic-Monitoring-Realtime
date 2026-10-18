# Task 2.1 — công bố bàn giao T3-C4, 18/10/2026

Phạm vi: đưa tài liệu/template/script TEST và báo cáo đã lọc lên GitHub để B/C sử dụng. Báo cáo `REPORT.md` ghi kết quả Task2 trước công bố và giữ nguyên byte; kết quả nghiệm thu không thay đổi: **LAN NOT RUN, 0/41; human B review PENDING**. Không sửa production code hoặc source branch PR47, không merge PR.

- Nhánh công bố: [prepare/t3-c4-physical-lan-20261018](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/tree/prepare/t3-c4-physical-lan-20261018).
- Commit local đã review: `e4c86aac3f523075ac97b87a08c35e1e2518efca`; chỉ một commit sau PR47 HEAD tại preflight.
- Fetch và GitHub cùng xác nhận PR47 HEAD `3044e5bc963f7ac9d8cdc4d8410696c301080534`, nhánh `gate/t3-c4-lan-20261017`. Đã chạy `merge-base --is-ancestor` thành công trước công bố; Draft PR phải nhắm nhánh này để chỉ chứa phần bàn giao.
- Source artifact `3044e5bc963f7ac9d8cdc4d8410696c301080534`; runtime build `3d938209ff3a4aa0d92dba7fca0e606fbc0b5245`. Các commit công bố sau e4c86aa chỉ cập nhật tài liệu/evidence.
- ZIP local `target/T3C4-3044e5b.zip`, SHA256 `5b04dcb10d46ba760749af29b2abdeee126a9748512abe4d18b78e0d4881685a`. ZIP/binaries không được commit hoặc đính kèm PR trong task này. B/C nhận từ người giữ máy build qua kênh riêng được phép; không có URL download ZIP giả.

[Runbook nhận ZIP và kiểm SHA256 trên cả hai máy](../../docs/T3_C4_PHYSICAL_LAN_ACCEPTANCE.md#1-tạo-và-chuyển-bộ-bàn-giao) quy định kiểm ZIP trước khi giải nén/chạy, đối chiếu manifest độc lập và từng JAR, không dùng hash mới thay hash sai. Không đóng gói lại ZIP cũ để thêm hướng dẫn mới. Việc chuyển ZIP tới B/C chưa được thực hiện trong task này.

Kiểm công bố gồm toàn bộ diff, allowlist text, không binary/raw/credential/IP nội bộ, ancestry, hash ZIP/22file,41case duy nhất còn NOT RUN/Actual trống/reviewer PENDING và cú pháp các khối lệnh PowerShell trong runbook. Kết quả có máy kiểm trong `publish-audit.json`. Kit29/29 PASS là kết quả Task2 đã lưu, không nhận là lượt runtime mới. Maven/PG/JavaFX/fault probe/physical LAN không chạy trong Task2.1.

B/C cần nhận/kiểm artifact, có hai Windows vật lý cùng LAN, cấp TEST DB/tài khoản/assignment/đề/audio, chạy preflight A/B rồi A1–A4/B–H/MT02–MT06. Nhóm F phải rút mạng B thật, chứng minh A tiếp tục và B phục hồi re-auth/OPEN/epoch/FULL; thu UI/HTTP/SQL/correlation, kiểm Reading/Listening và cleanup. Case MOCK/SIMULATED được tách riêng; chữ ký B/C phải do người thật điền. FULL vẫn mặc định, DELTA gate NOT DECIDED, tracker/QD owner approval giữ nguyên.
