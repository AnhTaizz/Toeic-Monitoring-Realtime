# T3-C4 — bàn giao chuẩn bị LAN vật lý, 18/10/2026

**LAN: NOT RUN. Case LAN thực tế: 0/41. Human B review: PENDING. Human C review: PENDING.** Người dùng xác nhận chưa có máy thứ hai, yêu cầu chuẩn bị bộ bàn giao B/C. Owners B + C, hạn20/10/2026. Không dùng local42/42 hoặc fixture làm physical PASS; không chốt gate FULL/DELTA.

Source nền là kết quả Task1 `3044e5bc963f7ac9d8cdc4d8410696c301080534`, chứa merge main `f86403489bdd01887b926f2f9e4b72ddf05d1423`. Runtime hai JAR build tại `3d938209ff3a4aa0d92dba7fca0e606fbc0b5245`; tới3044 chỉ hồ sơ. Nhánh chuẩn bị riêng `prepare/t3-c4-physical-lan-20261018` từ3044, không thay/push PR47. Workspace gốc và worktree khác giữ nguyên. Chỉ thêm script TEST/tài liệu, không sửa source Java, POM, config production, contract, tracker, QD, MSI hoặc migration.

## Bộ bàn giao có thật

- [Runbook](../../docs/T3_C4_PHYSICAL_LAN_ACCEPTANCE.md): A1–A4/B–H/MT02–MT06, preflight A/B và F Reading/Listening; no false ACK/duplicate history, cùng profile qua reconnect và cleanup.
- [Kit PowerShell](../../scripts/lan-t3c4/Common.ps1): đóng gói manifest, tạo run NOT RUN, read-only LAN preflight, launcher production, checkpoint HTTP lọc dữ liệu, fault console TEST và protocol probe bounded. Không đổi firewall/adapter; không tự chấm PASS nghiệm thu hoặc xác nhận người review.
- [Checklist41case](../../docs/templates/t3c4-lan/cases.csv): đúng cột yêu cầu, toàn bộ Actual/Evidence trống, NOT RUN và reviewer PENDING; [topology](../../docs/templates/t3c4-lan/topology.json), [human review](../../docs/templates/t3c4-lan/review.json), [SQL chỉ đọc](../../docs/templates/t3c4-lan/verify-history.sql).
- ZIP ignored local: `target/T3C4-3044e5b.zip` tính từ workspace gốc; giải nén thành `T3C4-3044e5b`,22file có manifest. Bundle JAR + kit cần JDK21, không phải MSI/app-image. Không kèm .env, credential/token, hostname/IP riêng hoặc profile build.

| Artifact | SHA256 |
|---|---|
| ZIP | `5b04dcb10d46ba760749af29b2abdeee126a9748512abe4d18b78e0d4881685a` |
| artifact-manifest.json | `613b72d4987d08da18c272ddeaab12c1afa3f01c3d1dd6f05a644ff8a5aa1730` |
| client-0.1.0-SNAPSHOT-all.jar | `a561c79fa4d3c00bb7f075207e31f49f3bc85bd6f79eed5cb145b7be79b28df3` |
| server-0.1.0-SNAPSHOT.jar | `0152113ac2ba7652660098d0a17a0a83a3687c34d8d79dcde593ce20fd101ecc` |

Hash được kiểm trên máy chuẩn bị và bản giải nén cùng máy; **chưa** được xác nhận trên máy B. B/C cần nhận ZIP/checksum qua kênh riêng và đối chiếu trên cả hai máy. Thư mục nguồn/ZIP/raw lớn ở target ignored; bản metadata đã lọc nằm tại đây. Runbook cũ được trỏ sang bộ3044; evidence ngày17/10 giữ nguyên, không dùng ZIP2b6c9ce.

## Kiểm tra đã chạy thật trong Task2

Windows PowerShell5.1 / JDK21.0.8. Không gọi server/LAN, không nhập credential, không chạy JavaFX/protocol probe/SQL. Có thực thi `java -version` để kiểm JDK.

| Kiểm tra | Kết quả | Evidence |
|---|---|---|
| Kit từ checkout: parse9script, origin guard, canonical digest, empty-array/null, no-overwrite, path traversal/hash tamper, JDK21 và checklist | PASS29/29 | [source validation](kit-validation-source.json) |
| Cùng kit từ ZIP giải nén: lặp lại kiểm tra trên | PASS29/29, cùng29 kiểm tra, không cộng thành58case khác nhau | [portable validation](kit-validation-portable.json) |
| Tạo ZIP, giải nén, kiểm22file/manifest và hash hai JAR | PASS | [portable verification](portable-verification.json), [checksums](bundle-checksums.json) |
| Khởi tạo run có41case và0physicalcase/NOT RUN/B PENDING | PASS trạng thái khởi tạo | [portable verification](portable-verification.json) |
| Sửa fixture CSV ở bản giải nén TEST rồi kiểm lại | PASS: từ chối tamper; bundle/ZIP gốc vẫn đúng hash | [portable verification](portable-verification.json) |
| Transport preflight A/B, WS protocol probe, fault console và HTTP checkpoint against server | NOT RUN | Chưa có topology hai máy |
| Physical F01–F12, nghe Listening, actual ACK/correlation/DB/cleanup | NOT RUN | Cần B/C thao tác/chứng kiến |
| Maven/PG/localT3C4/CI trên nhánh chuẩn bị này | NOT RUN; không thay Java/runtime | Task1 có lượt riêng, không nhận là test mới của Task2 |

Fixture đã chạy chỉ gồm địa chỉ documentation range, map/hash/file giả và tamper file TEST (**MOCK**, không truy cập mạng). Các case C/D/MT có `ACK_DROP`, `FUTURE_BASE`, replay và probe observations là **SIMULATED/MOCK đã chuẩn bị, chưa thực thi**. Không có case SIMULATED runtime nào đã chạy trong Task2. Probe chỉ là subset MT, kết quả script luôn ghi chưa đủ physical gate; phần event COMMIT/rollback, restart server và ba trace oracle cần evidence bổ sung theo runbook.

## Blocker và việc B/C cần làm

1. Có hai Windows vật lý và owner cấp LAN/port TEST; máyA PG/server/proctor/candidateA, máyB candidateB. Xác nhận topology thật, route dự phòng và quyền thao tác. Không tự đổi firewall/adapter máy khác.
2. Nhận/đối chiếu ZIP và cả hai JAR hash; cấp DB TEST riêng, tài khoản/assignment, đề/audio và các attempt mới cho Reading, Listening, probe. Không dùng credential hay IP nhạy cảm trong hồ sơ công bố.
3. Chạy preflight cả hai máy, A1–A4/B–H. F phải rút LAN thật, UNKNOWN/STALE đúng contract, A tiếp tục, B nối lại/re-auth/OPEN/epoch/FULL; outage dài có thể cần nút reconnect sau exhaustion.
4. Thu baseline/offline/recovered HTTP+UI+SQL/correlation, nghe thật Listening, đối chiếu writer/answers/deadline không reset và cleanup stop/logout. Không gọi mất heartbeat là queue-overflow gap nếu chưa có overflow.
5. Chạy fault/MT subset và replay/hook trên TEST riêng, nhãn rõ; điền Actual/PASS/FAIL/Evidence/Reviewer và chữ ký B/C. Tạo corrective PR riêng nếu có bug tái hiện cùng regression; không sửa thuật toán hoặc bật DELTA mặc định ở nhánh này.

Chưa phát hiện production blocker mới qua kiểm tra kit. Độ đúng physical LAN, fault console/probe runtime và cleanup trên máyB **chưa được đánh giá**; thiếu máyB là blocker nghiệm thu, không kết luận production đã PASS. PR47 vẫn READY_FOR_C_REVIEW, chưa merge và thiếu C approval; CI của Task1 tại [run39093801431](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39093801431) là evidence Task1. Nhánh chuẩn bị Task2 lưu local, không push/merge/approve thay owner. FULL default và DELTA gate NOT DECIDED.

Hậu kiểm static:17link local của runbook/report tồn tại; `git diff --check` PASS, diff Java/runtime so với3044 trống, bundle gốc giữ nguyên hash. Metadata kiểm tra ở `final-audit.json`. Checksum các file evidence công bố ở `SHA256SUMS.txt`; Git giữ nguyên byte thư mục evidence này trên Windows/WSL.
