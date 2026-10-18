# Đối chiếu Chặng 3 — cập nhật sau Task1 và Task2

**Kết luận hiện tại: STAGE3_BLOCKED.** Main vẫn f86403489bdd01887b926f2f9e4b72ddf05d1423. P1 cache audio concurrent publication còn mở; CLOSE trên main cần PR47 chưa merge. Physical LAN, MSI máy thứ hai, nghe thật và owner sign-offs còn thiếu. **FULL là baseline; DELTA experimental opt-in; QD19 chưa chốt.**

Đây là cập nhật docs-only trên **PR54 hiện có**, sau khi tìm thấy Task1/PR55 và Task2/PR56. Không tạo PR reconciliation trùng. [Snapshot mới](../evidence/stage3-reconciliation-20261018/task3-refresh/github-before.json) có thời điểm, exact HEAD, CI, reviews và comments. [Bản reconciliation 10:16 UTC](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/blob/2170f4f30d8cfa6c891b9c6e79bbf3603fb270fc/docs/STAGE3_RECONCILIATION.md) được giữ tại commit gốc; nhận định cũ chưa tìm thấy Task1 và kết luận PARTIAL không còn mô tả trạng thái hiện tại.

## 1. Cleanup đã có và kiểm chứng mới

PR41/42/43 **đã CLOSED trước phiên này**. Người đăng comment cleanup là AnhTaizz; không đăng lại, không reopen/close lần nữa và không nhận các thao tác cũ là do phiên mới thực hiện.

| PR | Exact HEAD đã đối chiếu lại | HEAD vào PR46 → PR39 → main | Commit ngoài main | Disposition hiện tại |
|---|---|---|---:|---|
| [41](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/41) | 304bc509bf26966499d6d0fb98a922794743b2ee | PASS | 0 | ALREADY_INTEGRATED_VIA_PR46_PR39; CLOSED |
| [42](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/42) | 7af7046512d008a289df3877c0d43f4207634821 | PASS | 0 | ALREADY_INTEGRATED_VIA_PR46_PR39; CLOSED |
| [43](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/43) | 772c6e9864cd6ce0ad45aad0433f5bfce53fee54 | PASS | 0 | ALREADY_INTEGRATED_VIA_PR46_PR39; CLOSED |
| [45](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/45) | a6524823c6a8d97cc7652fe4b776279db17d7d55 | Không là ancestor main | 2 | OPEN — PENDING_B_SIGNOFF |

Chuỗi merge thật, đã kiểm cả parents và ancestry: [PR46 c954e42c0716b24b19820b03159444a4fb9065ba](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/commit/c954e42c0716b24b19820b03159444a4fb9065ba) → [PR39 6ac8835b59fbb6b5c35873b860305eba0f9266bf](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/commit/6ac8835b59fbb6b5c35873b860305eba0f9266bf) → main f864034. Cả ba HEAD cũ có zero unique commits và zero unique production-patch commits; thay đổi tiếp theo trên main không được hiểu là patch cũ chưa tích hợp hoặc lý do để revert main về tree cũ.

Comment cleanup đã tồn tại: [41](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/41#issuecomment-6248651978), [42](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/42#issuecomment-6248653264), [43](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/43#issuecomment-6248654381). Không có review submission hoặc comment yêu cầu patch mới sau cleanup trong dữ liệu đã kiểm. Branch local/remote còn giữ. PR47 target main, đã chứa merge main167caf3; không còn phụ thuộc base PR43 đang đóng.

Lệnh kiểm mới, dùng exact HEAD từ API:
```powershell
$prHead=Read-Host 'Exact HEAD SHA tu API'
git merge-base --is-ancestor $prHead c954e42c0716b24b19820b03159444a4fb9065ba
git merge-base --is-ancestor c954e42c0716b24b19820b03159444a4fb9065ba 6ac8835b59fbb6b5c35873b860305eba0f9266bf
git merge-base --is-ancestor 6ac8835b59fbb6b5c35873b860305eba0f9266bf origin/main
git rev-list --count ("origin/main.."+$prHead)
```

Thay placeholder trước khi chạy. [Git ancestry](../evidence/stage3-reconciliation-20261018/task3-refresh/git-ancestry.json) lưu exit code từng bước, SHA, danh sách path và patch-id; [cleanup actions](../evidence/stage3-reconciliation-20261018/task3-refresh/cleanup-actions.json) phân biệt kiểm lại với mutation. Không còn PR OPEN nào trong41/42/43 cần đóng thêm. Không merge lại hoặc xóa branch.

### PR45: giữ mở, không ép superseded

- Workflow Windows gốc c1e3405678740e292747dd67951e29840e716a53 và cherry-pick d562581c03c5513e97c5a087d568a87332c3f383 có cùng stable patch-id **475510ffc80bb21224678f590831197431cef613**; cherry-pick đã vào main. CI đúng HEAD PR45 run39006248748 SUCCESS, một run cancelled cũ cũng được giữ.
- Cache thay thế d53122135485f5a27015d4d03d196ea2c9bd03a5 đã vào main, giữ catch tạo thư mục đồng thời và policy junction/8.3; blob AudioFiles hiện khác patch PR45. Task1 trên cùng main có regression concurrentReadersCanCreateTheSameManifestCacheDirectory và junction/8.3 PASS. Đây là evidence Task1, không chạy lại trong reconciliation.
- **Publication race mới vẫn FAIL**: Task1/PR55 ghi467 lỗi/1280 operations trong4 batch Windows, source f864034, dữ liệu audio MOCK; file cuối đúng hash/0partial không xóa lỗi ghi đồng thời. QD33 technical verdict hiện là **REQUEST_CHANGES**, thay nhận định ổn định hóa cũ. B cần corrective PR riêng; Task3 không sửa code.
- Không có B sign-off trong review/comment của PR45/46 hoặc packet đã kiểm. **PENDING_B_SIGNOFF**, giữ OPEN; không lấy CI hoặc kết luận agent làm owner approval.

Mẫu comment đóng sau khi B xác nhận, **chưa đăng, chưa là chữ ký**:

> SUPERSEDED_BY_PR46_PR39 — B đã xác nhận superseded tại [LINK_SIGNOFF_THAT_CAN_DIEN]. Workflow đã tích hợp qua d562581, cache cũ được thay qua d531221 → PR46 c954e42 → PR39 6ac8835. Đóng PR45, không merge a652482 và giữ branch/evidence. Quyết định bỏ patch cũ không xác nhận cache hiện tại hết lỗi: P1 publication của Task1 vẫn cần corrective PR/evidence riêng, liên kết [LINK_FIX_OR_ACKNOWLEDGED_TRACKING_CAN_DIEN]. Không dùng0/640 lịch sử để phủ467/1280 FAIL mới.

Chỉ owner có xác nhận thật mới điền và đăng mẫu trên.

## 2. PR hiện tại và CI đúng commit

Các SHA đầy đủ, timestamps và CI run lưu trong snapshot. Mọi PR bên dưới còn OPEN, chưa merged, không có review submission/comment xác nhận owner tại checkpoint mới.

| PR | HEAD / base | CI exact HEAD | Nội dung cần đối soát |
|---|---|---|---|
| [47](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/47) | 3044e5b / main; Ready for Review | [SUCCESS39093801431](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39093801431) | C approval, B transport review PENDING; source cuối3d93820 chưa vào main |
| [51](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/51) | b057b0f / main; Draft | [SUCCESS39063829880](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39063829880) | C5 mô tả PR47 cũ; B review PENDING; có overlap tracker với47/52 |
| [52](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/52) | c58931e / branch47; Draft | [SUCCESS39097078572](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39097078572) | Base stacked hợp lý vì47 chưa merge; physical0/41; B/C PENDING |
| [53](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/53) | b4177a7 / main; Draft | [SUCCESS39112594829](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39112594829) | QD31/32 technical ACCEPT; A/C chưa ký |
| [55 Task1](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/55) | e3fdfea / main; Draft | [FAIL39139325117](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39139325117) | Cross-review đã tồn tại; hai P1 trên main, human AR/BR/CR PENDING |
| [56 Task2](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/56) | 7d98514 / main; Draft | [SUCCESS39141524216](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/actions/runs/39141524216) | Test wait fix9bd052a + acceptance/C5 patch/handoff; không production change |
| [54 reconciliation](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/54) | Trước cập nhật2170f4f / main; Draft | SUCCESS39119112689 **chỉ cho2170f4f** | Phiên này bổ sung docs-only bằng commit mới; CI HEAD mới theo PR, không kế thừa PASS cũ |

Main **không có workflow run tại exact f864034** trong GitHub REST actions/runs?head_sha=… (total_count0). Workflow hiện chạy pull_request hoặc push stabilize/**, không push main. [Main CI lookup](../evidence/stage3-reconciliation-20261018/task3-refresh/main-ci.json) ghi đúng kết quả: không suy exact-main PASS/FAIL từ CI PR hoặc danh sách rỗng.

PR47 chỉ sửa hai production file FullSnapshotDelivery (C) và RealtimeClient (B): đợi worker và giới hạn chờ write CLOSE, guard ngăn write xếp hàng chạy sau hạn; cache correlation đã hủy có giới hạn, ignore ACK/ERROR/RESYNC muộn; UNAUTHORIZED toàn phiên vẫn dừng. CLOSE best-effort không cam kết server ACK. Mergeable không thay review; không merge PR này trong Task3.

PR51 chưa cập nhật source cuối: còn Draft/base cũ/2b6c9ce và rehearsal927/4. Task2/PR56 đã có [patch cho đúng báo cáo C5](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/blob/7d98514ec64b9fd3e96c3b9bc0d729cd5dcd462c/evidence/release-acceptance-20261018/C5_PR51.patch), git apply --check PASS trên b057b0f; owner chưa apply, B chưa review. Không tạo C5 trùng hoặc ghi đè branch51.

PR52 giữ9 scripts/4 templates/41 case, tất cả Actual/Evidence trống, NOT RUN/reviewer PENDING. Source kit3044e5b, runtime3d93820; ZIP expected SHA256 **5b04dcb10d46ba760749af29b2abdeee126a9748512abe4d18b78e0d4881685a**. Task2 chưa tìm lại ZIP trong output/recovery; không xác minh lại hash trong Task3 và không tạo kit mới. Runbook có đúng topology A server/PG TEST/proctor/candidate A, B candidate B; nhóm F yêu cầu mất LAN vật lý, A vẫn save, B UNKNOWN/STALE rồi re-auth/OPEN/epoch/FULL, không false ACK/mất answers đã ACK/autoplay Listening. Đây là hướng dẫn, chưa là evidence đã chạy.

PR53 đủ contract/source/166case evidence/P0-P1-P2/limitations/questions trong phạm vi QD31/32,25PG +141unit/client MOCK là lịch sử của packet. Technical ACCEPT không thay A+C sign-off; giới hạn Unicode issuance, legacy audit/timeout,409 history/no pagination vẫn mở.

CI55 FAIL ở CandidateModeSessionTest kiểm event ngay sau state write. PR56 thêm bounded await cho worker event, giữ assertion; local8/8 và CI56 SUCCESS. Không xóa FAIL55 hoặc gọi CI55 xanh. Task3 không port test fix vào branch docs-only; owner tích hợp56 trước rồi đồng bộ main vào55 và chạy CI đúng HEAD mới.

## 3. Conflict và thứ tự tích hợp đề xuất

[Overlap và phép ghép ảo](../evidence/stage3-reconciliation-20261018/task3-refresh/docs-overlap.json) dùng exact HEAD, không tạo merge commit hay thay refs/index/worktree.

| Cặp | File cùng sửa | Kết quả ghép ảo |
|---|---|---|
| 47 /51 | KIEM_THU, NHAT_KY, TIEN_DO, VAI_C | CONFLICT tại NHAT_KY và TIEN_DO |
| 51 /52 | Cùng4 file trên | CONFLICT tại NHAT_KY và TIEN_DO |
| 47 /52 | Có overlap nhưng47 là ancestor52 | Sạch theo ancestry; không merge52 vào47 |
| 47 /56 | Không overlap | Sạch |
| 53 /55;55 /56 | Không overlap | Sạch |

Đề xuất thứ tự **sau owner review**, không thực hiện merge:
1. Review56/test wait; khi được phép, tích hợp vào main rồi owner đồng bộ main vào55 bằng merge thường, giữ commit/evidence và lấy CI mới. 53/55 là packet riêng, có thể review song song; không chuyển QD/AR/BR/CR thành APPROVED do merge docs.
2. B cung cấp corrective cache P1 và regression; C+B review47 đúng HEAD. Maintainer chỉ tích hợp production khi đủ điều kiện correctness, CI và owner confirmations; bảo toàn nhánh.
3. Sau47 MERGED, retarget52 về main, kiểm ancestry/diff/CI và source/runtime artifact. Giữ provenance kit cũ; nếu source cần kit mới, version hóa manifest/hash riêng theo lý do kỹ thuật.
4. Owner51 cập nhật báo cáo bằng patch56 (hoặc tương đương đã review), lấy main có47/52 và giải quyết conflict bằng giữ đầy đủ lịch sử cả hai phía. B review C5 trước ACCEPTED.
5. PR54 chỉ cập nhật reconciliation nên không conflict các file tiến độ. Trước dùng làm trạng thái cuối, fetch/reconcile lại các HEAD đã merge; sau đó mới đưa phần current vào tracker chung theo xác nhận owner. Không cherry-pick evidence tùy tiện, không force-push.

## 4. Đối soát tracker và trạng thái nghiệm thu

Đã đọc đủ TIEN_DO, NHAT_KY, KIEM_THU, QUYET_DINH và VAI_A/B/C cùng TRACKER.json từ latest main. [Read-document manifest](../evidence/stage3-reconciliation-20261018/task3-refresh/read-documents.json) có SHA/line counts; [18 task đối soát](../evidence/stage3-reconciliation-20261018/task3-refresh/tracker-reconciliation.json) có trạng thái nguồn, observed technical status, owner/reviewer và điều kiện còn thiếu.

TRACKER hiện có **15 task T3 TODO và3 IN_PROGRESS** (B1–B3), mọi actual_hours null. TODO không phản ánh đủ code đã tồn tại; tuy nhiên các tiêu chí human/gate chưa đủ để chuyển DONE. Giữ nguyên TRACKER và7 shared docs vì conflict47/51/52 đã tái lập; bảng này là reconciliation hiện tại, không là tracker thứ hai hoặc lời xác nhận hoàn thành.

| Hồ sơ | Điểm cần cập nhật sau hợp nhất | Cách bảo toàn lịch sử |
|---|---|---|
| TIEN_DO | Chặng2/PR39 Draft không còn current;47 chưa merge, cache P1, MSI mới blocked, máy2/gate pending | Giữ các số/lượt cũ gắn SHA |
| NHAT_KY | Cleanup41–43 đã thực hiện trước phiên; nay recheck,55/56 đã công bố | Append nhật ký current sau hòa conflict; nhật ký phiên này ở mục7 |
| KIEM_THU | MT02–06/LT/IT còn partial; thêm local3d93820 và Task1/2 theo source; physical0/41 | Không nâng toàn27case từ CI; giữ FAIL/SKIP/NOT RUN |
| QUYET_DINH | QD31/32 technical ACCEPT; QD33 REQUEST_CHANGES; QD34 ACCEPT_WITH_LIMITATIONS; owner pending | Không ghi OWNER_APPROVED; QD10 presence, QD19 gate |
| VAI_A | Backend/hooks/concurrency/report đã tích hợp; A review B và QD34 còn thiếu | Không coi implementation/review agent là human slot |
| VAI_B | Cache P1, QD33/superseded45, reviewC5, nghe/MSI máy2/LAN | Giữ evidence installer lịch sử; không thay bằng app-image |
| VAI_C | Review47/C1/E1/QD31–32; C5 patch có, B review chưa có; gate chưa quyết | Không sửa raw hoặc promote DELTA |

| Hạng mục | Technical / code evidence | Acceptance / owner hiện tại |
|---|---|---|
| PR47 | Source3d93820, CI3044 SUCCESS; chưa main | C/B review và integration PENDING |
| T3-A1–A4 | Backend audio/lifecycle/hooks/transaction đã main; local PG/HTTP/WS evidence | Human C review và physical acceptance PENDING |
| T3-A5 | Bản nháp trên main | C review pending; nhãn envelope v1 trong report lệch protocol v0 |
| T3-B1–B4 | Client/audio/shared writer/proctor đã main | Cache P1; A review/hearing/physical LAN chưa hoàn tất |
| T3-C1–C3 | Encoder/reducer/replay có; FULL default | B review pending; MT02–06 toàn gate PARTIAL |
| T3-C4 | Local42/42 source3d93820 lịch sử, fault SIMULATED | LAN0 PASS/0 FAIL/41 NOT RUN; B review/C owner decision PENDING |
| T3-C5 | Nháp main + update51 + patch56 chưa apply | REVIEW_PENDING, B chưa xác nhận |
| T3-B5 | MSI0.3.10 source3702b6d installed local lịch sử | SECOND_MACHINE_NOT_RUN; MSI final blocked bởi47/cache/tool prerequisites |
| T3-AR | REQUEST_CHANGES theo Task1 | A review B; C xác nhận ghi chú; human PENDING |
| T3-BR | REQUEST_CHANGES_ON_MAIN theo Task1, chờ47 | B review C; A xác nhận ghi chú; human PENDING |
| T3-CR | ACCEPT_WITH_LIMITATIONS theo Task1 | C review A; B xác nhận ghi chú; human PENDING |
| QD31 /QD32 | IMPLEMENTED; technical ACCEPT/53 | A+C PENDING; history>5000 trả409 không pagination |
| QD33 | Policy/directory replacement có; publication FAIL/55 | REQUEST_CHANGES; B PENDING_B_SIGNOFF; A xem CI;45 OPEN |
| QD34 | Phân tích + PG regression, không production implementation mới | ACCEPT_WITH_LIMITATIONS; A+B PENDING |
| E1 | 12run/84raw và tái phân tích17/10 là lịch sử; Python12PASS Task1 | Raw hiện NOT AVAILABLE theo Task2; C/B method review pending; không reanalysis mới |
| E2 | Chưa có campaign | NOT RUN; không performance claim |
| Load | Chưa có1/5/10×3 campaign | NOT RUN; multiclient correctness không là load |

[Evidence crosscheck](../evidence/stage3-reconciliation-20261018/task3-refresh/evidence-crosscheck.json) phân biệt đọc lại evidence với chạy mới. MSI0.3.10 không chứa PR47; app-image không thay MSI. Task2 tìm JDK21/jpackage có, WiX3/mt.exe chưa có ở vị trí đã kiểm; Task3 không chạy lại inventory/build.

E1: giữ287/62/11 trên360eligible và CPU/heap/parent-clock conclusions ở đúng source lịch sử, không dùng làm số mới của final main. Raw thiếu nên không tự tái phân tích; khi tìm lại, kiểm manifest gốc, rejoin xuất riêng, so trial/reason rồi analyzer CPU/coverage. Join khác byte manifest → giữ derived provenance riêng và BLOCKED nếu tool chưa hỗ trợ, không sửa/seal raw để vượt checksum. E2/load/nghe thật chưa chạy tiếp tục NOT RUN.

## 5. Readiness và các owner approvals còn thiếu

| Trục | Đánh giá |
|---|---|
| Code integration | BLOCKED: main còn hai P1 đã xác nhận, production47 chưa vào; exact-main CI chưa có run |
| Physical acceptance | Chưa đủ: LAN41case/MSI máy2/nghe thật NOT RUN, human B pending |
| Contract & governance | A/B/C signatures, QD31–34, C5 và gate T3-C4 chưa đủ |

Kết luận duy nhất cho thời điểm này là **STAGE3_BLOCKED**, không chốt DONE bằng docs hoặc CI.

Cần xác nhận thật:
- **A+C:** QD31/32; B nắm thông báo legacy/Unicode/history409.
- **B:** corrective cache P1, policy QD33 và disposition superseded45; **A** xem CI.
- **A+B:** QD34 takeover tradeoff.
- **C+B:** source47 (C FullSnapshotDelivery, B RealtimeClient); **C** xác nhận C1 cleanup và E1-method theo evidence; **B** review C5.
- **T3-AR/BR/CR:** A→B, B→C, C→A cùng reviewer xác nhận ghi chú theo từng task, không ký thay nhau.
- **B thực hiện/A review** MSI và Listening; **B+C** physical LAN và C owner gate QD19. Không suy B/C approval từ mẫu review.json chưa điền.

## 6. Năm việc còn lại và phần có thể chuyển Chặng4

1. **B + C/maintainer:** sửa cache publication P1 bằng corrective PR riêng; review47 đúng HEAD, lấy CI/owner xác nhận rồi tích hợp production khi đủ gate.
2. **A/B/C:** xác nhận hoặc request changes QD31–34 và AR/BR/CR/C1/E1; B quyết superseded45 trước owner comment/close. Giữ branch và evidence.
3. **B+C, A hỗ trợ TEST:** tìm đúng ZIP/source/hash, có2 Windows vật lý; chạy41case đặc biệt F/Reading/Listening, MSI thật máy2/nghe thật; ghi actual/evidence/operator/reviewer. Không tái tạo kit nếu chưa có lý do kỹ thuật.
4. **Maintainer + B/C:** review56/53/55, xử lý CI55 qua test fix56 theo merge thường;47→52(retarget đúng lúc)→51(update source/giữ logs) theo approvals; refresh54 rồi đồng bộ tracker chính.
5. **C+B và A:** quyết QD19 từ evidence thật, giữ FULL nếu gate thiếu; chuẩn bị phương pháp/source freeze/harness/trace/metadata cho Chặng4, giữ E2/load NOT RUN tới khi chạy đúng điều kiện.

Có thể bàn giao **công cụ regression/replay, phương pháp CPU/coverage/byte accounting, seed/metadata templates, report drafts và packet review** để chuẩn bị Chặng4. E1 raw thiếu nên chưa đủ chốt lại dataset lịch sử. Chưa đủ nghiệm thu Stage3 hoặc mở campaign DELTA performance như đã qua gate. Nếu gate không qua, kế hoạch cho phép chuẩn bị hướng FULL/reconnect, nhưng không chạy campaign mới trong nhiệm vụ reconciliation này.

## 7. Nhật ký phiên cập nhật và giới hạn kiểm chứng

- Fetch và API mới trước thao tác; root main sạch f864034, giữ nguyên. Worktree riêng ngoài target, đồng bộ fast-forward với PR54 HEAD2170f4f rồi bổ sung commit thường; không tạo duplicate PR hoặc sửa lịch sử Git.
- Đã thực thi kiểm ancestry/merge parents/unique production patches/workflow patch-id, đọc8 hồ sơ, đối chiếu18task, source kit/evidence/CI/reviews/comments và conflict ảo. Các kiểm mới là **STATIC/REAL GitHub/Git reads**, không runtime regression.
- Cleanup41–43 đã có trước: phiên này **0 close,0 duplicate cleanup comment**. PR45 giữ OPEN PENDING_B_SIGNOFF; closure text vẫn dự thảo. PR47/51/52/53/55/56 giữ nguyên head/base/state; không merge/close/markReady/approve thay owner.
- Runtime Maven/PostgreSQL/GUI/MSI/physical LAN/hearing/E1/E2/load **NOT RUN trong Task3**. CI PR54 có thể tự chạy khi push; chỉ nhận kết quả khi gắn exact HEAD mới. Số166/123/12/8/42/29 kế thừa đúng packet gốc, không tính vào lượt test mới.
- Shared7 docs, TRACKER, production/test/build source, .env và raw không đổi. Báo cáo current này được cập nhật; toàn bộ evidence cũ của PR54 giữ nguyên byte. SHA256SUMS cũ bind commit2170f4f, kiểm bằng Git blobs gốc theo [historical-bindings](../evidence/stage3-reconciliation-20261018/task3-refresh/historical-bindings.json); không sửa checksum cũ cho khớp báo cáo current. [SHA mới](../evidence/stage3-reconciliation-20261018/task3-refresh/SHA256SUMS.txt) bind bản current và refresh riêng.
- ChatGPT planning/review NOT RUN theo opt-out; không lấy agent làm human sign-off. [Validation mới](../evidence/stage3-reconciliation-20261018/task3-refresh/validation.json) ghi expected/actual của đối soát. Không đổi giờ công hoặc tự ghi DONE.
