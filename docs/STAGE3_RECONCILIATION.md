# Đối chiếu Chặng 3 — 18/10/2026

**Kết luận: STAGE3_PARTIAL.** Code tích hợp qua PR39/46/48/49/50 đã trên main; PR47 vẫn chờ C review và chưa merge. LAN vật lý **0/41 NOT RUN**, MSI máy thứ hai/nghe thủ công chưa chạy, human review và QD owner approval còn thiếu. FULL vẫn mặc định; gate DELTA **NOT_DECIDED**, không suy ra thất bại DELTA từ việc thiếu môi trường.

Snapshot GitHub: **18/10/2026 10:16:55 UTC**; main fetch lại là **`f86403489bdd01887b926f2f9e4b72ddf05d1423`**. [GitHub snapshot](../evidence/stage3-reconciliation-20261018/github-snapshot.json) và [Git audit](../evidence/stage3-reconciliation-20261018/git-audit.json) chứa SHA, ancestry, patch-id, review/CI và checklist. Đây là reconciliation của agent, không phải chữ ký A/B/C hoặc nghiệm thu production.

## 1. Cleanup đã thực hiện

| PR | Exact HEAD | Ancestry tới main / merge PR46 | Commit ngoài main | Kết quả GitHub |
|---|---|---|---:|---|
| [41](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/41) | `304bc509bf26966499d6d0fb98a922794743b2ee` | Cả hai exit 0 | 0 | CLOSED, không merge lại |
| [42](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/42) | `7af7046512d008a289df3877c0d43f4207634821` | Cả hai exit 0 | 0 | CLOSED, không merge lại |
| [43](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/43) | `772c6e9864cd6ce0ad45aad0433f5bfce53fee54` | Cả hai exit 0 | 0 | CLOSED, không merge lại |
| [45](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/45) | `a6524823c6a8d97cc7652fe4b776279db17d7d55` | Không là ancestor main | 2 | OPEN — **PENDING_B_SIGNOFF** |

Chuỗi merge thật được kiểm cả parents: [PR46 `c954e42c0716b24b19820b03159444a4fb9065ba`](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/commit/c954e42c0716b24b19820b03159444a4fb9065ba) → [PR39 `6ac8835b59fbb6b5c35873b860305eba0f9266bf`](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/commit/6ac8835b59fbb6b5c35873b860305eba0f9266bf) → main hiện tại. Với 41/42/43, không có commit riêng, nên không có production patch riêng chưa tích hợp; khác biệt tree so với main là thay đổi tiếp theo trên main, không phải patch bị bỏ sót.

Trước đóng, cả ba không có conversation comment, review submission hoặc inline review thread mới. PR47 đã target main và có merge main `167caf312767566417ad55840a008833844e1f15`; đóng base PR43 không xóa ref và không làm mất dependency Git. Đã đăng `ALREADY_INTEGRATED_VIA_PR46_PR39`, link merge thật rồi đóng theo ủy quyền task. Comment: [41](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/41#issuecomment-6248651978), [42](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/42#issuecomment-6248653264), [43](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/43#issuecomment-6248654381). Branch và lịch sử evidence giữ nguyên. Cleanup không hoàn tất checklist human review trong body PR cũ.

### PR45 và QD-33

- Workflow Windows gốc `c1e3405678740e292747dd67951e29840e716a53` được cherry-pick thành `d562581c03c5513e97c5a087d568a87332c3f383`, hiện là ancestor merge PR46/main. Hai patch-id đều `475510ffc80bb21224678f590831197431cef613`; workflow hiện dùng `windows-2025`, Java21, Maven test, có thêm trigger `stabilize/**`.
- Audio cache thay thế ở `d53122135485f5a27015d4d03d196ea2c9bd03a5`, đã vào main. `AudioFiles.safeDirectory` giữ catch `FileAlreadyExistsException` khi tạo con đồng thời; `plainDirectory` chặn symlink/junction/reparse ở root và bên dưới; ancestor trên root không bị áp policy root. `AudioFilesTest` có concurrent readers, root replacement, ancestor junction/8.3 và junction dưới root.
- [Stabilization report](../evidence/stabilize-20261017/REPORT.md): PR45 lỗi 112/640, bản thay thế 0/640, source `3702b6d`; đây là **historical evidence**, không phải probe mới của nhiệm vụ này. Giới hạn TOCTOU cùng user vẫn được ghi trong QD-33.
- QD-33 có phân tích/test kỹ thuật đã lưu; **chưa tìm được PR cross-review tổng hợp riêng của Task1** trong danh sách 30 PR mới nhất (cao nhất #53) và tìm `QD-33` chỉ thấy PR46. Không nhận packet không tìm thấy là đã công bố hoặc owner duyệt. PR45/46 không có comment sign-off; QD-33 vẫn chờ B, A xem CI.

Comment đóng **dự thảo cho B/owner**, chưa đăng và chưa thực hiện:

> SUPERSEDED_BY_PR46_PR39 — Sau khi B xác nhận chấp nhận QD-33 tại [link sign-off thật cần điền], đóng PR45 vì workflow đã tích hợp qua d562581 và audio cache thay thế qua d531221, rồi PR46 c954e42 → PR39 6ac8835. Không merge a652482 vì làm mất fix race. Bằng chứng 112/640 → 0/640 thuộc source3702b6d; không thay nghiệm thu LAN/MSI. Giữ branch/evidence lịch sử.

## 2. PR đang mở và CI theo đúng HEAD

| PR | HEAD / base | Trạng thái và CI | Việc còn thiếu |
|---|---|---|---|
| [47](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/47) | `3044e5bc963f7ac9d8cdc4d8410696c301080534` / main | OPEN, không Draft; [CI SUCCESS 39093801431](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39093801431) | C approval PENDING; chưa có merge thật |
| [51](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/51) | `b057b0fa5c4f99f00dfaa29d49c2030f1e8a5f16` / main | OPEN/Draft; [CI SUCCESS 39063829880](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39063829880) | B review PENDING; cập nhật mô tả source PR47 |
| [52](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/52) | `c58931e7567b4ff21315a5de3d93efb03d9c0581` / branch PR47 | OPEN/Draft; [CI SUCCESS 39097078572](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39097078572) | Base stacked hiện hợp lý vì47 chưa merge; B/C review và LAN còn thiếu |
| [53](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/53) | `b4177a7e7e2b28a73b622349be0a2a0c48c5945f` / main | OPEN/Draft; [CI SUCCESS 39112594829](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39112594829) | Packet QD31/32 đủ các mục; A/C sign-off PENDING |

Mỗi PR47/51/52/53 có 0 review submission, 0 conversation comment, 0 inline thread khi kiểm. Giá trị `merge_commit_sha` của PR OPEN có thể là merge thử của GitHub; chỉ ghi merge thật khi `merged:true`/`merged_at` có dữ liệu. Không lấy mergeable hoặc CI làm owner approval.

PR48/49/50 đã merged với merge commits lần lượt `a1e08459af07f6668e5dce34b7e67bcd605a34e1`, `c0f21b4f885f98742557ab0fc09994c0777f26c7`, `f86403489bdd01887b926f2f9e4b72ddf05d1423`; CI HEAD PR tương ứng SUCCESS [39056726100](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39056726100), [39059436361](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39059436361), [39059782824](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39059782824). API workflow dùng ở đây chỉ trả PR-triggered/trang đầu, không có run tại exact SHA main f864034; **không kết luận exact-main CI PASS/FAIL** từ danh sách rỗng.

### PR47: source review cuối

Source corrective cuối `3d938209ff3a4aa0d92dba7fca0e606fbc0b5245`; HEAD3044e5b bổ sung hồ sơ. Hai production file C cần review là `FullSnapshotDelivery.java` (owner C) và `RealtimeClient.java` (owner B): CLOSE có hạn chờ khi write future không hoàn thành, generation/guard cleanup và correlation đã hủy để ACK/ERROR/RESYNC muộn không phá phiên sau. [Report đúng HEAD](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/blob/3044e5bc963f7ac9d8cdc4d8410696c301080534/evidence/pr47-stabilize-20261018/REPORT.md). Code này **chưa nằm trên main**. Không sửa source branch47 trong reconciliation.

### PR51: điểm lệch cần sửa trước owner review

Mục9 T3-C5/body/ghi chú hiện còn nói47 Draft, base cũ và `stopped` chỉ đợi CLOSE write theo2b6c9ce. Hiện47 đã base main, không Draft, có fix3d93820 giới hạn chờ và cache correlation. Owner C cần cập nhật đoạn mô tả hiện tại và gắn evidence3044e5b; sau merge47 cập nhật lại main SHA/merge status. Rehearsal `eca6ad3` (927PASS/4SKIP, local42/42) phải giữ làm historical riêng, không đổi SHA hoặc nhận là kiểm trên3d93820. Nội dung state/event/history/restart trong bản nháp vẫn là cơ sở bàn giao, chưa thay B review.

### PR52: kit và nghiệm thu thực

47 HEAD là ancestor52;52 chỉ 3 commit trên47, 32 file bàn giao. Runbook phân biệt IP LAN thật với loopback, hai candidate+một proctor, F01–F12 Reading/Listening, re-auth/OPEN/epoch/FULL, correlation/SQL không false ACK, dedupe/history, cleanup và MT02–MT06 fault SIMULATED trên attempt riêng. Chưa có LAN evidence mới: CSV đủ 41 ID, mọi row NOT RUN, Actual/Evidence trống, reviewer PENDING. Kit validation29/29 là kiểm công cụ lịch sử, không phải acceptance41case; local42/42 ở47 cũng không thay LAN.

Bundle source `3044e5bc963f7ac9d8cdc4d8410696c301080534`, runtime `3d938209ff3a4aa0d92dba7fca0e606fbc0b5245`; ZIP SHA256 `5b04dcb10d46ba760749af29b2abdeee126a9748512abe4d18b78e0d4881685a`. ZIP không trên GitHub: B/C nhận từ máy build qua USB/kênh riêng được phép, kiểm hash độc lập trên cả hai máy trước giải nén/chạy. Bundle JAR cần JDK21, **không phải MSI**. Nếu source/runtime/artifact thay đổi, lập provenance/hash mới và regression phù hợp, không gán manifest cũ cho build mới.

### PR53: packet và giới hạn

[Packet](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/blob/b4177a7e7e2b28a73b622349be0a2a0c48c5945f/evidence/cross-review-20261018/QD31_QD32_REVIEW_PACKET.md) có contract, source/methods,166case evidence/sourceSHA, P0/P1/P2, known limitations, technical verdict, câu hỏi A/C và comment đề xuất. QD31/QD32 technical **ACCEPT**, owner vẫn PENDING; 166PASS gồm25PG thật +141unit/clientMOCK trên mainf864034. Đây là lượt Task5A, không chạy lại trong reconciliation. Unicode username không cấp được lượt mới; legacy audit/timeout, history>5000 không pagination, mapping409 và max-body/controller gap coverage đều ghi rõ. Hai câu ký trong packet là mẫu, không phải chữ ký thật.

## 3. Xung đột tài liệu và thứ tự tích hợp đề xuất

`git merge-tree --write-tree` trên exact HEAD: **47/51 CONFLICT**, **51/52 CONFLICT** tại `docs/NHAT_KY.md`, `docs/TIEN_DO.md`; KIEM_THU/VAI_C có overlap nhưng auto-merge ở snapshot này. 47/53,51/53,52/53 sạch. 47→52 là ancestry, không là hai nhánh độc lập cần merge tùy ý.

Thứ tự phụ thuộc đề xuất cho người có quyền merge sau review:

1. **47 → main** sau C review đúng3044e5b và CI đủ điều kiện. Không lấy CI53 hoặc rehearsal51 thay CI47.
2. **52 → main**: chỉ retarget sau khi47 đã merge và verify3044e5b là ancestor main; merge main vào branch52 nếu cần, không rebase. Đọc lại diff bảo đảm chỉ còn32file handoff, cập nhật current pointer khi cần, giữ provenance ZIP cũ. B/C review kit; merge docs không ghi LAN PASS.
3. **51 → main** sau owner cập nhật mô tả47/source và tích hợp main đã có47/52; giải quyết hai conflict bằng giữ đầy đủ log/evidence cả ba phía. B review bản nháp trước chốt T3-C5; không cherry-pick hoặc ghi đè số test/SHA.

**53 độc lập về file** nên A/C có thể review song song, merge theo owner/quy định GitHub; merge packet không đổi QD thành OWNER_APPROVED. PR reconciliation này chỉ thêm file mới nên review song song được; sau các merge khác phải fetch và refresh snapshot trước khi dùng làm trạng thái hiện tại.

## 4. Tracker reconciliation và bàn giao theo vai

Theo task mục6, dùng file mới để đối chiếu thay vì sửa đồng thời các tracker mà47/51/52 đang chỉnh. Không sửa `TRACKER.json`, nguồn kế hoạch hoặc raw. Các mục dưới là cập nhật cần owner đưa vào tracker chung sau khi tích hợp theo thứ tự trên; không phải tự xác nhận DONE.

| Hồ sơ đã đọc | Điểm không còn là trạng thái hiện tại | Nội dung đã đối chiếu / cần đưa vào sau tích hợp |
|---|---|---|
| TIEN_DO | “Chặng2”,39Draft, SDK/WiX luôn thiếu, các merge nguồn còn chờ | Đang hoàn thiện Chặng3;39/46/48–50 đã merge;47 chưa merge; MSI0.3.10 local đã có, máy2 còn thiếu; gate/reviews pending |
| NHAT_KY | Lệnh merge/closure đề xuất ngày17/10 | Giữ lịch sử; nhật ký phiên này ở mục6 dưới ghi cleanup41–43, giữ45, tạo report riêng |
| KIEM_THU | Số924/926, FAIL/skip cũ theo source3702b6d | Giữ nguyên; tách lượt47/source3d93820 và53/sourcef864034; không nâng27case/formal matrix từ CI |
| QUYET_DINH | IMPLEMENTED/PENDING QD31–34 | Code có trên main, technical review không thay owner; QD34 là phân tích+test, không có production implementation mới |
| VAI_A | Handoff source nhánh, human review còn thiếu | A review B/audio/client; A+C QD31/32, A+B QD34; A xem CI QD33; load vẫn NOT RUN |
| VAI_B | Máy2/nghe thật/B review chưa có | B ký QD33/superseded45, review C5/kit, chạy haiWindows/MSI/nghe thật; không coi bundleJAR làIT01 |
| VAI_C | C5 bản nháp cũ, đổi base47 còn là next step | Base47 đã main; C review source3d93820, sửa mô tả51, cùngB gate20/10; C xác nhận C1/E1 và QD31/32 |

| Hạng mục | Code / technical evidence | Nghiệm thu / owner còn thiếu |
|---|---|---|
| T3-A3/A4/A5 | PR48–50 đã vào main; hook/restart/transaction/multiclient/report có evidence | Human C review, LAN và report final chưa được xác nhận |
| PR47 integration | Source3d93820 trên branch47, CI xanh | C review và merge PENDING; production branches chưa thống nhất hoàn toàn |
| T3-C4 / MT02–06 | Replay và local live evidence có; FULL default | **PARTIAL**, LAN0/41 NOT RUN; C+B gate20/10 NOT_DECIDED |
| T3-C5 | Nháp đã có trên main, update51 chưa merge | **PARTIAL/DRAFT**, source47 trong51 cần sửa, B review PENDING |
| T3-B5 / IT01 | MSI0.3.10/source3702b6d installed local lịch sử PASS | MáyWindows thứ2 khôngIDE, nghe thật, humanA review NOT RUN; MSI này không chứng minh chứa47 |
| T3-AR | Evidence kỹ thuật tích hợp/client đã lưu | Chưa có A review phầnB và C xác nhận slot review đầy đủ; **PENDING** |
| T3-BR | Replay/live/handoff củaC có | Chưa có B review phầnC và A xác nhận; **PENDING** |
| T3-CR | Hook/transaction và packet53 có | Chưa có C review phầnA và B xác nhận; **PENDING** |
| QD-31 | IMPLEMENTED trên main; technical ACCEPT/53 | A+C PENDING; B cần biết Unicode username/legacy fail-closed |
| QD-32 | IMPLEMENTED; technical ACCEPT/53 | A+C PENDING; chấp nhận409/no pagination và audit khi>5000 |
| QD-33 | IMPLEMENTED; phân tích/stress lịch sử có | B PENDING, A xemCI; PR45 OPEN |
| QD-34 | Phân tích+regression: replay takeover tăngepoch, không mấtanswers/deadline/final | A+B PENDING; không tự thêm idempotency/schema |
| E1 / T4-C1 preparation | Historical12run/84raw hash; rejoin12/analyzer deterministic;287detected/62miss/11INCONCLUSIVE trên360eligible | C/B review phương pháp chưa ký; parent-clock upper-bound, CPU1core/heap khôngRSS, JIT/background limits; không claim kernel latency/production accuracy |
| E2 / T4-C2 | Chưa có campaign đủ điều kiện | **NOT RUN**; không nhận hiệu suất/lợi íchDELTA hoặc tự chọn fallback/gate |
| Load / T4-A2 | Chưa có1/5/10×3 campaign | **NOT RUN**, multiclient correctness loopback không phải load |

Readiness: **code integration còn47**, các CI PR đã nêu xanh nhưng không thay correctness/PG/manual gates; **physical acceptance thiếu máy2/LAN/MSI/nghe/B review**; **governance thiếu A/B/C sign-offs và QD31–34/gate**. Không phát hiện production correctness blocker mới trong lần đối chiếu tài liệu/Git này; đây không phải lượt code audit hoặc regression runtime mới. Giới hạn C1 profile-lock chỉ mitigated45s, root cause chưa xác nhận; giữ FAIL8s lịch sử và không biến5/5local thành bảo đảm LAN.

## 5. Tối đa năm việc còn lại và phạm vi chuyển Chặng 4

1. **C + B:** review đúng hai production file47/CI, xử lý yêu cầu nếu có rồi người có quyền merge47 theo quy định GitHub; C cập nhật mô tả source cuối trong51.
2. **A/B/C:** ký hoặc request changes QD31/32(A+C), QD33(B,A xemCI), QD34(A+B) và các slotAR/BR/CR theo tracker; B quyết định superseded45 rồi owner mới comment/close.
3. **B + C, hạn20/10:** có haiWindows thật, nhậnZIP/hash hai máy, chạy41case, đặc biệt F và Listening; A/B hoàn tất MSI máy2/IT01 bằng installer đúng source/hash, lưu chứng cứ thật và review. Không tự sửa firewall/adapter.
4. **Maintainer + B/C:** tích hợp docs theo47→52→51, retarget52 đúng lúc, giữ lịch sử/conflict cả hai phía, B reviewC5; review53 song song; refresh reconciliation rồi owner mới cập nhật tracker chính.
5. **A + C + B:** owner quyết gate T3-C4 từ evidence; chuẩn bị regressionT4-A1/T4-B1, load1/5/10×3 và E1/E2 theo dependency. Chỉ chạy campaign khi đủ điều kiện, giữ NOT RUN nếu chưa chạy; không tự bậtDELTA.

Có thể chuyển giao **tài liệu phương pháp, harness/seed/checksum E1, packet review, kitLAN và regression runners** để chuẩn bị Chặng4. Chưa đủ điều kiện ký Stage3 DONE hoặc nghiệm thuT4-B1/T4-C2/load/E2; không thực hiện campaign mới trong task cleanup này.

## 6. Nhật ký và kiểm chứng của phiên reconciliation

- Checkout riêng từ latest main, Git metadata riêng; workspace gốc bẩn và mọi worktree khác được bảo toàn. Không sửa production/test/build source hay branch47/51/52/53.
- Đã fetch, đọc7hồ sơ tracker/vai, TRACKER/contract/acceptance liên quan; kiểm API metadata/comments/reviews/threads/CI, Git ancestry/merge parents/patch-id/diff, thử merge-tree và đọc kit/packet đúng blobHEAD.
- Đã comment+close41/42/43 sau kiểm;45 giữOPEN PENDING_B_SIGNOFF. Không mergePR, xóa branch, ký owner hoặc thay defaultFULL.
- Kiểm mới của phiên này là kiểm Git/tài liệu/checklist/privacy/link và phạm vi diff; xem [validation.json](../evidence/stage3-reconciliation-20261018/validation.json). Maven/PG/GUI/physicalLAN/MSI/E1/E2/load runtime: **NOT RUN trong phiên này**. Số test dẫn lại luôn gắn nguồn/lượt lịch sử; không cộng vào lượt mới.
- Report mới chứa nhật ký, trạng thái kiểm thử và ghi chú làm dở thay cập nhật shared files đang conflict, đúng lựa chọn task mục6. Cần owner cập nhật tracker chung sau hợp nhất; không sửa raw hoặc xóa FAIL/SKIP/NOT RUN lịch sử.
