# Tài liệu làm việc của nhóm

Thư mục này là nơi ghi lại việc đang làm, để bất kỳ ai (hoặc Claude) mở project lên đều biết đang ở đâu và làm tiếp cái gì. Bộ Plan V1 nằm ở `docs/plan-v1/` và không thay đổi hằng ngày; các file ở đây thì có.

## Kế hoạch dự án

- [**Plan V1** — 5 chặng, hợp đồng và nguồn môn học](plan-v1/README.md)
- [**Plan V2** — 36 task đã duyệt phạm vi](plan-v2/README.md)

**Ghi chú:** Plan V1 được chuyển nguyên file (không sửa bản nguồn); đường dẫn trong hồ sơ lịch sử có thể vẫn dùng tên cũ. 

## File nào dùng để làm gì

| File | Nội dung | Ai cập nhật | Khi nào |
|---|---|---|---|
| [TIEN_DO.md](TIEN_DO.md) | Đang ở chặng nào, mỗi người đang làm gì, bước tiếp theo, đang bị chặn bởi gì | Cả ba | Cuối mỗi buổi làm |
| [NHAT_KY.md](NHAT_KY.md) | Nhật ký từng buổi: đã làm gì, commit nào, còn dở gì | Người vừa làm | Cuối mỗi buổi làm |
| [QUYET_DINH.md](QUYET_DINH.md) | Các quyết định kỹ thuật đã chốt và đang chờ chốt | Người ra quyết định, reviewer duyệt | Khi chốt một lựa chọn |
| [PROTOCOL.md](PROTOCOL.md) | Danh mục endpoint và message đang dùng thật | C (monitoring), A (auth, thi) | Khi thêm hoặc đổi message |
| [AUDIO_CLIENT_PREPARATION.md](AUDIO_CLIENT_PREPARATION.md) | Tải/checksum/thử loa/READY trên candidate và cách kiểm package T3-B1 | B | Khi đổi luồng chuẩn bị audio |
| [LISTENING_CLIENT_PLAYBACK.md](LISTENING_CLIENT_PLAYBACK.md) | T3-B2: nhận start, chống phát lặp, playlist/câu hỏi và lifecycle client | B | Khi đổi phát Listening phía candidate |
| [PROCTOR_SESSION_CONTROLS.md](PROCTOR_SESSION_CONTROLS.md) | T3-B4: import/tạo ca, READY/start, kết quả và tổ chức lại LT04 | B | Khi đổi điều khiển ca |
| [WINDOWS_INSTALLER.md](WINDOWS_INSTALLER.md) | T3-B5: tạo MSI, cài/chạy/gỡ, cấu hình LAN và checklist IT01 máy thứ hai | B | Khi đổi đóng gói hoặc thêm kiểm chứng máy hai |
| [LISTENING_INTERRUPTION_RECOVERY.md](LISTENING_INTERRUPTION_RECOVERY.md) | T3-B3: dừng/ghi gián đoạn, status khi reconnect và khóa không replay | B | Khi đổi lỗi media hoặc reconnect Listening |
| [AUDIO_RESOURCES.md](AUDIO_RESOURCES.md) | T3-A1: cấp tài nguyên, manifest/checksum, READY và gate start; contract cho B/A2 | A | Khi đổi contract audio |
| [LISTENING_LIFECYCLE.md](LISTENING_LIFECYCLE.md) | T3-A2: lệnh start WS, heartbeat/gián đoạn, khóa đáp án và tổ chức lại | A | Khi đổi contract Listening |
| [MONITORING_SYNC_REPORT_DRAFT.md](MONITORING_SYNC_REPORT_DRAFT.md) | T3-C5: giải thích đồng bộ, ACK, ví dụ phục hồi, bằng chứng và giới hạn gate | C | Khi đối chiếu báo cáo với source |
| [MONITORING_BASELINE_METHOD.md](MONITORING_BASELINE_METHOD.md) | Phương pháp baseline, định nghĩa E1/E2, nguồn đối chiếu và checklist B review | C | Trước khi đo hoặc đổi định nghĩa |
| [MONITORING_MEASUREMENTS.md](MONITORING_MEASUREMENTS.md) | Schema JSONL, byte/outcome, cấu hình và tái tạo summary C4 | C | Khi đổi cách đo |
| [PROCESS_MONITORING_SURVEY.md](PROCESS_MONITORING_SURVEY.md) | ProcessHandle/WMI, nguồn chính thức và giới hạn quan sát | C | Khi khảo sát phương pháp |
| [KIEM_THU.md](KIEM_THU.md) | Trạng thái 27 test case và nơi lưu bằng chứng | Owner của test | Khi chạy test |
| [../evidence/README.md](../evidence/README.md) | Mục lục báo cáo, log và artifact theo task để đọc trên GitHub | Người bổ sung bằng chứng | Khi thêm lượt kiểm thử hoặc task mới |
| [thanh-vien/VAI_A.md](thanh-vien/VAI_A.md) | Task và hướng dẫn cho vai A (server, DB, giao dịch) | A | Ghi chú làm dở |
| [thanh-vien/VAI_B.md](thanh-vien/VAI_B.md) | Task và hướng dẫn cho vai B (JavaFX, audio, đóng gói) | B | Ghi chú làm dở |
| [thanh-vien/VAI_C.md](thanh-vien/VAI_C.md) | Task và hướng dẫn cho vai C (monitoring, thực nghiệm) | C | Ghi chú làm dở |

Trạng thái chính thức của từng task (TODO/đang làm/xong, giờ thực tế, bằng chứng) vẫn nằm ở `docs/plan-v1/TRACKER.json`. Các file ở đây không lặp lại trạng thái đó.

Bàn giao B3 ngày04/10: [evidence dashboard](../evidence/t1-b3/2026-10-04-verification.md) ghi 331 tests, REAL integration và Stage proctor; [README chạy dashboard](../README.md#dashboard-giám-thị-t1-b3). TRACKER giữ nguyên theo yêu cầu task; trạng thái code/component và nghiệm thu toàn prototype được ghi riêng.

## Bắt đầu một buổi làm

1. Đọc [TIEN_DO.md](TIEN_DO.md): xem dòng của mình và mục "Đang bị chặn".
2. Đọc mục cuối cùng của mình trong [NHAT_KY.md](NHAT_KY.md) để biết lần trước dừng ở đâu.
3. Mở file vai của mình, tìm task đang làm, đọc "Ghi chú làm dở".
4. Nếu task phụ thuộc contract của người khác, xem [PROTOCOL.md](PROTOCOL.md) đã có mẫu chưa.

## Kết thúc một buổi làm

1. Commit code (kể cả đang dở, trên nhánh riêng).
2. Thêm một mục vào [NHAT_KY.md](NHAT_KY.md).
3. Sửa dòng của mình trong [TIEN_DO.md](TIEN_DO.md).
4. Nếu task chưa xong, ghi vào "Ghi chú làm dở" của task đó trong file vai: đang dừng ở bước nào, cái gì chưa chạy.
5. Nếu task xong và có bằng chứng thật, cập nhật `TRACKER.json` (`status`, `actual_hours`, `evidence`).
6. Nếu có chốt lựa chọn kỹ thuật, ghi vào [QUYET_DINH.md](QUYET_DINH.md). Nếu có đổi message, sửa [PROTOCOL.md](PROTOCOL.md) và báo người dùng message đó.

## Quy ước

- Bằng chứng là thứ kiểm tra lại được: commit SHA, tên test đã chạy, đường dẫn file log. "Đã thử thấy ổn" không phải bằng chứng.
- Dữ liệu giả ghi nhãn `MOCK`.
- Không ghi kết quả chưa đo, không ghi Passed cho test chưa chạy.

T2-C3: [trace/schema/lệnh CLI](MONITORING_TRACE.md), [evidence/checksums](../evidence/t2-c3/2026-10-10-verification.md). Reuse full/event encoder và shared C1 reducer, oracle độc lập, recorder mặc định tắt. REAL source khác REPLAY và SIMULATED faults; Human B review NOT RUN; chưa delta/T2-C4/E1/E2.

T2-C4: [harness/CLI/ground truth/clock mapping/TEST policy](CONTROLLED_PROCESS_HARNESS.md), [evidence thực chạy](../evidence/t2-c4/2026-10-10-verification.md). Java493 PASS, Windows30child đủ ba nhóm, artifact/cleanup PASS; Human B review và E1/E2 NOT RUN. Dừng sau C4.

- [T3-C1 encoder delta candidate: contract, ACK, lifecycle và replay](MONITORING_DELTA_ENCODER.md). FULL vẫn mặc định; server delta đã có ở T3-C2, tích hợp opt-in ở T3-C4; gate chưa chốt.

- [T3-C2 reducer delta/resync, capability thử nghiệm và scan freshness](MONITORING_DELTA_REDUCER.md). Full mặc định, gate chưa chạy.

- T3-C3: [Replay delta faults và campaign](MONITORING_DELTA_FAULT_REPLAY.md) —3MOCKworkloads/72cases/4negative/deterministic reports, full default; ACK-loss không thuộc mô hình replay; kiểm local C4 xem link bên dưới, LAN/gate/E1/E2 chưa chốt.

- T3-C4: [Candidate thật, cấu hình FULL/DELTA và checklist live gate](MONITORING_DELTA_LIVE_GATE.md). FULL mặc định, nghiệm thu LAN/human B review còn riêng.
