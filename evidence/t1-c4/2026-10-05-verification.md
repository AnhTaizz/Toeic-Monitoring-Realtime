# T1-C4 verification — 05/10/2026

Trạng thái: CODE_COMPLETE cho C4; không nghiệm thu toàn prototype/MT01/MT08, không E1/E2. Người chạy: Codex Agent theo task C4. Người dùng đã chọn không kết nối ChatGPT; không có claim ChatGPT plan/review. Human B review: NOT RUN.

## Source và build

- Base main PR#8: `a04aaa20963b9377809c8d1c0ec8298f638244b0`; fetch lần cuối trước PR vẫn cùng SHA. Nhánh `feat/t1-c4-monitoring-measurement`. B4 có nhánh remote nhưng chưa ở main; C4 không sửa B4.
- Commit recorder/định nghĩa `ffbb5db`; transport `c935f82`; tests/harness/summary `c70c93e7029d90288b921d050502ff9610db80e6`. Definition byte/outcome được viết trước code. Docs/evidence sau commit này không đổi code/script/test; kiểm git diff phạm vi source PASS.
- Final build là detached checkout sạch tại c70c93e. `.env` chỉ copy ignored để kết nối DB, không commit/đưa vào metadata. Metadata `sourceDirty=false`, 126 source/script/test file SHA256 tương ứng source build; hash JAR và thời điểm build ở [run-metadata.json](run-metadata.json).
- Windows11 x64, Temurin21.0.10+7, Maven3.9.15, Python3.15.0b3, PostgreSQL18.6. Script cần Python3.11+ stdlib, không package Python mới. Chỉ thử trên môi trường này.
- Baseline chạy lại `mvn test`331 PASS lúc20:26:45 UTC+7. Final `mvn test`366 PASS lúc20:56:53; `mvn package`366 PASS lúc20:57:53 UTC+7. Lệnh thêm `-o -B -ntp -Dmaven.repo.local=<local-cache>` cho cache sẵn có; không skip tests.
- 35 lượt JUnit mới: protocol23 + client7 + server5. Tổng protocol23/client227/server115/spike1=366. Ba case concurrent server có thêm assertion nhưng không tính là test mới. Python unittest9 PASS riêng, không cộng vào366. Harness main integration không bị gọi tự động bởi Surefire.

## Các kiểm tra đã chạy

| Kiểm tra | Kết quả | Bằng chứng/giới hạn |
|---|---|---|
| UTF-8 ASCII/tiếng Việt/emoji toàn JSON | PASS | MessageMeasurementsTest; RealtimeClientTest và RealtimeMeasurementTest đối chiếu đúng chuỗi socket. Không dùng String.length để đo byte. |
| TX attempted/completed/failed, retry và ACK riêng | PASS MOCK | Ticket một terminal outcome; cùng event retry tính2; future thất bại không cộng completed; ACK duplicate không thêm semantic acceptance. |
| Fragment/Unicode qua surrogate boundary/malformed/binary/oversized | PASS MOCK | Full text đếm1, demand/rejection/correlation giữ; binary/oversize UNMEASURED bytesnull; INVALID không phản chiếu input. |
| ACK/ERROR/warning/presence server cùng boundary | PASS MOCK + REAL | 4 type test exact-byte; server IOException thất bại/close; all7 type trong demo. |
| Concurrent server send buffer | PASS MOCK | Latch giữ raw write đầu; response thứ2 chỉ enqueue: attempted1/completed0, release→completed2. Không coi decorator return là socket write. |
| Counter concurrency/bounds | PASS MOCK | 4 producer ×200 không mất count; không map theo ID. |
| Recorder full/writer failure/timeout/close | PASS MOCK | Queue1/drop9 vẫn counter10, factory I/O failure vẫn delivery/ACK; stalled writer interrupt/daemon/timeout/unwritten; pending TX close trong budget; disabled không I/O/counter. |
| Known trace hand calculation | PASS MOCK | 2 event ×100 +1 ACK ×20 = TX220 mỗi outcome; không cộng RX. Failure write-completed0; input hash và output tái lập. |
| Summary invalid/drop/truncated/duplicate/schema/domain | PASS MOCK | 9 Python tests, raw/counter mismatch giữ hai số; oversized SHA bao phủ toàn file, duplicate endpoint không cộng đôi. |
| PG/Spring/HTTP/WS production candidate/proctor/dashboard model | PASS REAL | Own TEST schema+port; login/scope/B2/DB/event/gap ACK/warning/presence/ERROR thật. Không GUI. |
| Windows ProcessHandle collector | PASS REAL | Quét production source trên Windows, processesScanned>0, stop future hoàn thành; chỉ diagnostic tổng. Không claim event MOCK là process thật. |
| Retry fault | PASS SIMULATED + REAL transport | Chặn ACK đầu ở observer C3 sau B2 đã nhận/validate; retry>=2, DB1/dashboard1. Không phải packet loss thật. |
| Overflow | PASS MOCK source + REAL persistence | Capacity1/in-flight1, snapshot3process giữ1/drop2, MONITORING_GAP lưu sum2 và ACK; event retained lên dashboard. |
| Recorder final/raw summary | PASS REAL | 3 file FINAL khỏe: dropped0/unwritten0/pending0, writerFailed=false/flushTimedOut=false; COMPLETE26categories. |
| B3 regression | PASS REAL | Production dashboard/B2/C3/PG V4, own child JVM hard-kill/UNKNOWN/recovery/history, intentional disconnect/stale/HTTP recovery/dedup, auth403/401 và cleanup. Process source MOCK, GUI NOT RUN. |
| Cleanup/security/JAR/source | PASS | Owned workers gone, SQL TEST schemas0; no local credentials/private paths/raw payload; production JAR không test/harness/client test dependency; protected files unchanged. |

Transcripts UTF-8 đã thay đường dẫn máy/user/JDK bằng placeholder: [maven-test.txt](maven-test.txt), [maven-package.txt](maven-package.txt), [summary-tests.txt](summary-tests.txt), [integration.txt](integration.txt), [b3-regression.txt](b3-regression.txt). Các mã MEASUREMENT_WRITER_FAILED/FLUSH_TIMEOUT trong unit log là lỗi được tiêm có nhãn MOCK, không phải lỗi demo final.

Lượt đầu có hai assertion parser trong test đọc nhầm METADATA/FINAL và lỗi chữ ký override binary/constant protocol trong test. Đã sửa trước final build. Một bản sao build tạm bị copy thừa thư mục target; dừng copy ngay, dùng checkout sạch mới cho final. Dọn các bản sao bị automatic review từ chối `blocked by policy`; để nguyên trong target ignored, không retry xóa/kill. Không dùng exploratory run làm evidence final.

## Raw và phép đối chiếu

Run `C4-23e3008b-e226-4c32-ba70-991b5e9365e1`, bắt đầu20:59:03 UTC+7 (raw13:59:03 UTC). [raw/](raw/) có3file, tổng131dòng/82551byte trên đĩa. File là nguyên bản từ recorder đã lọc metadata, không sửa tay nội dung để sanitize; chỉ copy bytes, đối chiếu SHA256 với summary. Không có payload/processName/PID/token/password/path/OS username. ClockDomain:

- candidate CLIENT `76f4b9e7-8ba0-46bc-8d74-6b3227df3aa0`;
- proctor CLIENT `ea74067b-ebcc-4b03-99f1-6f757aa42cde`;
- SERVER `0aa53ff1-6cd0-427c-8d80-0f530620196e`.

Một JVM demo có các recorder mốc riêng. UTC chỉ timestamp; không trừ nanoTime giữa domains/máy để gọi là latency. recordIndex là log ordinal, wire sequence vẫn null. Metadata ghi settings transport từng file; settings poll/delivery/presence/policy và fault/source label nằm ở run-metadata. Event/overflow nguồn MOCK, transport REAL; collector scan REAL là bước riêng. Chưa có CPU/memory/miss-rate/latency/bandwidth TCP/IP.

| MESSAGE/TX/ATTEMPTED | Messages | UTF-8 bytes |
|---|---:|---:|
| CLIENT proctor HEARTBEAT | 5 | 1240 |
| CLIENT candidate HEARTBEAT | 5 | 1565 |
| CLIENT candidate MONITORING_GAP | 1 | 454 |
| CLIENT candidate PROCESS_OBSERVED | 4 | 2032 |
| SERVER ACK | 14 | 3841 |
| SERVER ERROR | 1 | 325 |
| SERVER MONITOR_PRESENCE | 5 | 2205 |
| SERVER MONITOR_WARNING | 2 | 906 |
| Tổng CLIENT TX + SERVER TX | 37 | 12568 |

CLIENT5291 + SERVER7277 =12568. WRITE_COMPLETED báo riêng cũng12568; không cộng hai outcome thành25136, không cộng RX vào tổng gửi. RX đối chiếu server nhận5291/client nhận7277 trong phiên này; bằng nhau không chứng minh mọi run đều bằng vì có thể lỗi/gửi dở/thiếu endpoint. Dung lượng log82551 không phải byte message12568.

PROCESS_OBSERVED có event đầu3 lần gửi: hai lần immutable retry sau ACK observer bị chặn, và lần3 cố tình sửa PID để kiểm server CONFLICT/ERROR. Event thứ2 là retained overflow, gửi1. Raw không chứa PID/payload; nguồn/test phase ở harness/metadata/evidence giải thích lần3 là negative test, không gọi cả3 là retry giống nhau. DB chỉ2 event, dashboard2 warning; gap drop2.

[summary.json](summary.json) và [summary.csv](summary.csv) do script tạo. Tái tạo:

```powershell
python scripts/summarize-monitoring.py evidence/t1-c4/raw --output server/target/c4-summary-check
python -m unittest discover -s scripts -p test_summarize_monitoring.py -v
```

Đã chạy lại từ raw commit sample: JSON/CSV hash byte-identical, raw unchanged. `.gitattributes` đặt raw JSONL `-text` và summary JSON/CSV LF để Git trên Windows không đổi byte/hash. COMPLETE chỉ chứng minh những file cung cấp đủ control/counter/raw, không chứng minh mọi endpoint ngoài danh sách đều được bật recorder. Muốn demo mới: build rồi chạy `scripts/smoke-c4.ps1`, settings cố định trong harness; heartbeat scheduling làm số message giữa các run có thể khác. Không coi12568 là số đo toàn kỳ thi hay lợi ích delta.

## Khảo sát và tài liệu

[ProcessHandle/WMI survey](../../docs/PROCESS_MONITORING_SURVEY.md) dùng nguồn Oracle ProcessHandle/Info và Microsoft Win32_ProcessStartTrace. ProcessHandle đã chạy; WMI/ETW/JNI/JNA/Windows service NOT RUN, không triển khai. Chọn JDK polling vì phù hợp code/stack/thời gian, vẫn có thiếu metadata và process sống giữa hai poll có thể bỏ sót. Không claim event tuyệt đối không mất, nhanh hơn, novelty hoặc tối ưu delta.

README/docsREADME/PROTOCOL/TIEN_DO/NHAT_KY/KIEM_THU/VAI_C/schema/survey/QD11 cập nhật. Cross-owner: B RealtimeClient + tests, A handler/registry + tests; không đổi wire v0, auth/scope, service COMMIT/ACK, collector/delivery/presence/dashboard semantics. TRACKER changed: NO; 01–03/nguon/migrations unchanged.

Chưa chạy: human B review (và human A cho cross-owner), GUI toàn luồng candidate/proctor, LAN/package máy thứ2, WMI/ETW, E1/E2, full/delta. C4 có CODE_COMPLETE khi technical gates/mergeability PASS; trạng thái Git/PR/merge cuối trong report. Dừng C4, không B4/chặng2.
