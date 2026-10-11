# T3-C1 · Encoder delta candidate — verification11/10/2026

## Source, scope và provenance

- Fetch/main remote mới nhất khi bắt đầu: `12e28386169c42b88883f23a5053baeb8fb0c3c9` sau C5PR#22. Nhánh `feat/t3-c1-monitoring-delta-encoder`; chưa có branch/PR delta hoặc production handler A/T3-A3. PR#17 UI và PR#23 exam fix đang mở riêng, không merge chúng trong task này.
- Implementation/contract/test/CLI: `33828d412c11cd5845e06addc1a11fc61762cfa1`. Bổ sung2test pending-ACK và giữ counter full lúc stop như baseline: `7041c6e6866774b8bcfe7891bd4d98192fc301a8`. Hai SHA đều giữ trong lịch sử; evidence/docs commit và Git merge cuối xem PR/final. Không amend source SHA đã ghi trong raw.
- JDK Temurin21.0.10+7, Maven3.9.15, Windows11 build26200, PostgreSQL18 container hiện có. Docker chưa chạy khi hồi quy đầu; khởi động Docker Desktop và `docker compose up -d --wait` với volume hiện có, không reset DB. Docker Engine runtime29.4.2. `.env`, original plans/tracker/migrations/server/exam flow không sửa. Không dependency mới.
- Final CLI run `3af87cf9-2a2a-4bd1-887f-1121af3b4813`, source7041c6e; full integration cuối `T2C1-fb0112a1-f0cc-49df-82c2-a8eedcf88e22`, cùng source7041c6e. Metadata giữ manifest/hashJAR/OS/JDK/workingTree thực tế. Working tree có docs chưa commit; **không gọi đây là clean toàn checkout**. Source/runtime đã commit, manifest228file được đối chiếu khớp hiện tại. Guide/nhật ký/evidence sau đó không đổi Java/CLI logic.
- Initial CLI/source33828d4 và hồi quy full/event/presence/measurement được giữ riêng; final chỉ đổi clear sequence khi delta stop (full stop counter giữ baseline) và thêm tests. C1/CLI/package được chạy lại trên7041c6e; C3/A4/C4 không chạy lại sau thay đổi này. Không tuyên bố mọi lượt mạng dùng7041c6e.

## Những gì đã cài

DeltaSnapshotPayload added/removed/identity canonical cùng policy/full; DeltaSnapshotDelivery wrapper yêu cầu explicit capability; RealtimeClient default false và CandidateMonitoringSession vẫn FullSnapshotDelivery. Reuse worker/lock/transport generation/ACK/backoff/stop của FullSnapshotDelivery, MonitoringDelivery event nguyên trạng. State gốc acknowledgedState+acknowledged chỉ cập nhật từ pending.target sau ACK đúng. Một pending immutable + coalescing1latest waiting, scan lỗi khác empty, metadata cùng identity dùng full. Retry cùng toàn envelope/payload, max5/ACK5s/backoff1–8s; hết retry cần scan mới→OPEN epoch mới→full1. Reconciliation30s monotonic từ ACK full, không vượt pending hoặc báo tươi từ scan lỗi. Auth revoke/global unauthorized/close dọn worker/listeners/RAM; shared socket giữ.

Replay dùng cùng client encoder/event code, adapter delta tuần tự **SIMULATED** (chưa T3-C2), oracle raw độc lập. Expected observation checkpoints tính process/metadata/epoch/sequence từ raw riêng, không lấy answer từ reducer; skip unchanged vẫn kiểm. Full/delta cùng event map ID/nội dung, không ép sequence giữa mode bằng nhau. Manual fixture/expected trong test resources, cố ý delta sai bị FAIL. Delta CLI chỉ clean/duplicate/reconnect/mutation; drop/reorder/resync gate chưa làm và bị từ chối. TEST C4 không chuyển schema.

## Đã chạy thật

| Kiểm tra | Kết quả | Mức bằng chứng |
|---|---|---|
| Baseline `mvn test` trên main12e2838 |493PASS,19:58:25 UTC+7| Chạy mới, không lấy số cũ |
| Final `mvn package` trên7041c6e |**531PASS**,0fail/error/skip,20:21:48 UTC+7| protocol35/client353/server142/spike1;38new invocations |
| DeltaSnapshotDeliveryTest |27PASS| Snapshot/transport/clock MOCK; callback/error/retry SIMULATED; scheduled worker/shutdown REAL |
| DeltaReplayTest8 + DeltaSnapshotPayloadTest3 |11PASS| Handwritten expected, raw oracle, canonical metadata/PID reuse/null start/reappear, cross-mode event identity/content |
| CLI fixture full/delta |PASS exit0|9successful observations,6events; source MOCK, ACK/network/epoch SIMULATED |
| CLI duplicate100/reconnect-at3 cả hai mode |PASS exit0, state/event maps tương đương| SIMULATED; không production resync proof |
| Delta mutation scan2 xóa added/removed |**FAIL đúng exit1**| Record3 expected Chrome+Zalo, actual Edge+Chrome; event history vẫn6 |
| Full mutation scan2 |**FAIL đúng exit1**| Hồi quy ca âm oracle full |
| TEST C4 và delta drop10 |Refused đúng exit2| Không giả hỗ trợ server/gate |
| Legacy full seed1234/dup20/drop10/reorder35/reconnect3 |PASS exit0| Giữ mô hình fault full C3, không MT gate delta |
| REAL ProcessHandle record6scans100ms, full/delta cùng trace |COMPLETE/PASS exit0| Final45business/delivered events; source REAL, toàn ACK/network SIMULATED. Không owned Edge delta live hoặc benchmark |
| Lặp6fixture reports giữa hai source runs |PASS byte-identical| Ledger audit kiểm fixture full/delta, reconnect full/delta, negative delta, legacy faults; không so REAL workloads khác nhau |
| Final full `smoke-t2c1.ps1 -Gui` |PASS trên7041c6e| REAL Spring/PG/HTTP/WS/ProcessHandle/owned Edge/proctor JavaFX component; autonomous STALE/TTL/capacity/shutdown; faults MOCK |
| Event `smoke-c3.ps1` |PASS trên33828d4| REAL Edge/collector/WS/PG/event ACK/server restart, overflow MOCK/ACK loss SIMULATED, cleanup |
| Presence `smoke-a4.ps1` |PASS trên33828d4| REAL scoped heartbeat/UNKNOWN/recovery/commit failure/owned JVM kill/cleanup; snapshots MOCK |
| Measurement `smoke-c4.ps1` |PASS trên33828d4| REAL transport/recorder/cleanup, source scan REAL riêng; event/overflow MOCK, ACK suppression SIMULATED |
| Artifact hashes/gzip restore/source manifest/summary regenerate |PASS| [Artifact audit](artifact-audit.json); final JSON/CSV regenerate giống byte |
| DB readonly audit |test_schemas=0/public_version=7| Không reset/migrate shared public; script tự dọn schema TEST |

531 gồm full/event/presence/dashboard/measurement/realtime/harness regressions của toàn module, không chỉ delta. 38test mới = protocol3 + client delivery27 + replay8. Extra pending cases kiểm1.000observations→queued1, event PID2/3/4 vẫn được phát riêng dù state3 bị coalesce, full deadline tới khi pending phải chờ ACK. Các trường ACK sai8case, retry exact copy, oldcallback/epoch, revoke, immediate ACK/scheduled tick và cleanup được kiểm bằng latch/clock kiểm soát, không lấy sleep/bấm tay làm điều kiện thứ tự.

## Failure được giữ, không đổi thành PASS

1. Package đầu với test ERROR STALE mới **FAIL** vì branch ERROR không retry được còn deadline ACK write trước. Sửa candidate deadline về now để pump retire base ở lần tiếp; package529 rồi final531 PASS. [Transcript](transcripts/initial-error-test-failed.txt).
2. Hồi quy DB đầu dừng phase=MIGRATION/PSQLException; Docker engine chưa chạy, chưa mở schema test. Sau khởi động Docker/compose volume cũ, hồi quy cả4script PASS. [Transcript](transcripts/initial-db-unavailable.txt). Không sửa credential hay reset dữ liệu để lấy PASS.
3. Audit exploratory đọc nhầm summary key `summaries` thay vì `rows`, bị KeyError; sửa cách đọc auditor, không đổi raw/summary/runtime. [Audit cuối](artifact-audit.json) kiểm key thật/COMPLETE/drop-unwritten-pending0.
4. Transcript PowerShell local dùng UTF-16 BOM. Bản sao evidence đầu đọc sai UTF-8 đã phát hiện qua NUL/binary diff; tạo lại **từ log gốc**, decode đúng BOM→UTF-8/LF, sanitized prefixes, update derived hashes và kiểm không NUL/replacement character. OriginalSha256 giữ nguyên, không sửa log gốc/raw artifact. Không code/test failure.
5. Delta/full sai cố ý và TEST/config refusal là **kết quả âm mong đợi**, không product failure chưa xử lý. Giữ reports như đã tạo, không chỉnh state/sequence/event thủ công.

## Artifact và cách tự kiểm

[checksums.json](checksums.json) ghi path/hash byte đã lưu; gzip có originalSha256 cho bytes giải nén. Transcripts là bản sao decode đúng UTF-16 BOM của PowerShell thành UTF-8, đã đổi prefix user home/workspace và chuẩn hóa LF/bỏ whitespace cuối dòng ở bản sao, được đánh dấu sanitizedTranscript, có originalSha256 của file local gốc. Raw trace/measurement/summary/metadata/replay giữ nguyên byte; file lớn gzip mtime0 không mất dữ liệu. Hai ảnh final Edge open/closed thuộc proctor component; không full candidate GUI.

- `artifacts/cli-final/`: metadata, REAL trace, fixture/reconnect/negative/legacy reports (REAL reports lớn nén gzip). Initial run33828d4 giữ ở cli-initial:49business events so với final45 vì ứng dụng nền thay đổi giữa hai captures; **không so hai workloads để kết luận delta**. Trong mỗi run hai mode dùng cùng một trace.
- `artifacts/full-final/`: full6endpoints/63categories COMPLETE/drop-unwritten-pending0 và source/JAR manifest; ảnh Edge. Full đầu33828d4 có64categories; measurement3endpoints/26categories; counts theo mỗi lượt, không chọn số đẹp hoặc đòi số category cố định.
- `transcripts/`: baseline/package final/CLI cả hai lần/full cuối/event/presence/measurement/failure DB/test đầu.

```powershell
mvn package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t3c1.ps1 -JavaHome '<JDK21>' -RealScan
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-t2c1.ps1 -JavaHome '<JDK21>' -Gui
```

CLI không cần DB; full regression cần Docker/compose/.env/Edge. [Guide](../../docs/MONITORING_DELTA_ENCODER.md) có lệnh riêng và exit0/1/2. Gzip tải/giải nén sẽ khôi phục original bytes/hash; đây không phải nguồn input bắt buộc cho self-test (fixture có sẵn). Full/v0 protocol và startup server/client hiện tại giữ nguyên.

## Review, giới hạn và bàn giao

- [Kiểm tra diff/link/privacy/protected paths](final-audit.json): không thấy local secret value, Bearer token hoặc private home path trong tập file đã kiểm; nguồn kế hoạch/server/.env/config giữ nguyên. Nhật ký chỉ thêm cuối, artifact cũ không sửa. Đây là audit file/source, không human review.
- **Production delta DISABLED**. Full baseline default, state server RAM/STALE6s/TTL5phút không thay. Encoder skip unchanged/full30s sẽ không đáp ứng freshness6s nếu nối thẳng: A/T3-C2 cần delta auth/binding/seq/dedup/resync/epoch/push/freshness và successful-scan refresh contract cho unchanged, không heartbeat thay scan/tăng timeout ngầm. B cần capability negotiation thực, transport correlation/guard/lifecycle, gate và measurement DELTA/control classification trước live/E2.
- Adapter replay không nhận quyền thật, socket, TTL/presence/DB/commit, thiếu delta không thực hiện server resync. Không MT02–MT06 toàn hệ thống, không nghiệm thu network delta/desktop pair. Retry client được kiểm bằng MOCK transport/controlled ACK; không ghi là mất ACK delta qua mạng thật.
- Human B/A review **NOT RUN**. ChatGPT planning/review **NOT RUN** theo opt-out trước đó. Candidate GUI toàn app/LAN/máy thứ hai/Python analyzer unit/E1/E2 **NOT RUN trong T3-C1**; analyzer đã chạy để regenerate summary thật. Không dùng replay runtime/byte minh họa tuyên bố tiết kiệm/nhanh hơn.
- User đã cho phép commit/push/PR/merge commit nếu không technical/GitHub blocker; giữ local/remote branch. GitHub checks/reviews/rules/mergeability kiểm cuối ghi trong PR/final; không giả reviewer đã approve. Không sửa tracker/task acceptance chính thức. **Dừng T3-C1**, không tự làm T3-C2/gate hoặc bật production.
