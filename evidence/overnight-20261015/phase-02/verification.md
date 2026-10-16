# Phase 02 — automated technical review, E1 harness

CODE PROPOSED / TESTED LOCAL / HUMAN C REVIEW PENDING. Phase01 source5072bc9; phase02 dirty source byte hashes/JAR hash tại smoke-provenance.json, checkpoint SHA trong summary cuối.

- Mở rộng ControlledProcessHarness có window tùy chọn; đường cũ không window giữ nguyên. E1 CLI chỉ chạy rõ --dry-run/--smoke-short/--full; schedule giống seed giữa4poll, rotate repetition,30target/run; sampler500ms CPU collector/parent riêng và heap parent. Không thay policy production/FULL.
- Targeted `mvn -o -B -ntp -Dtest=E1WindowTest,ControlledProcessHarnessTest,ObservationJoinerTest,AttemptIdentifierBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false package`: initial22 PASS; final lượt tiếp FAIL1/22 tại normal child harness INCOMPLETE (cause chưa chứng minh); thêm assertion mã lỗi/cleanup, retest cùng22 PASS,0skip. Không bỏ test/giấu failure; raw logs workspace gốc target/overnight-tools/phase02-{targeted,final,retest}.log.
- Python analyzer3 PASS: nearest-rank detected-only percentile, CPU one-core/invalid, window/censoring. Dry-run exit0/no spawn; SMOKE1run16s + bracket scan,3owned children200/800/3000ms, ProcessHandle REAL, candidate gate SIMULATED, COMPLETE/resourcesCleaned=true. Analyzer từ raw PASS, mutation resources checksum rejected PASS; summary SMOKE đính kèm, không gọi E1 final.
- Raw immutable/checksum nằm target/e1 trong overnight worktree; không publish PID/process metadata. Resource integrity manifest riêng không đổi schema strict C4 cũ.
- C1/C2 live regressions sẽ chạy trên final JAR ở phase03/06, chưa claim PASS ở đây. E1 full/E2/load/LAN/human C review NOT RUN tại checkpoint này.

[Lệnh cho B](E1_RUN_COMMANDS.md), [các điểm C cần review](C_REVIEW_REQUEST.md). Latency đo parent launch→scan-end là cận trên; startup/READY gồm trong lifetime, target hold không là lifetime200ms chính xác. Giới hạn này phải giữ khi phân tích.

### Đính chính bảo toàn raw (phase03)

Lượt SMOKE đầu ở `target/e1` đã bị Maven clean phase03 xóa ngoài ý muốn; summary/hash đã commit vẫn là kết quả thực nhưng raw này không còn để tái kiểm. Không coi provenance cũ là bộ dữ liệu tái lập đầy đủ. Sẽ chạy lại SMOKE và lưu `jpackage-out/e1` ngoài mọi target; không sửa/bịa lại raw đã mất. Lỗi vận hành này giữ trong evidence.
