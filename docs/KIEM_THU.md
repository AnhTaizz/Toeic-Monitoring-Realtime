# Trạng thái kiểm thử

Mô tả đầy đủ từng case (cách gây tình huống, kết quả bắt buộc) ở `Ke_hoach_LT_Mang_5_chang/03_KIEM_THU_VA_THUC_NGHIEM.md`. File này chỉ theo dõi đã chạy chưa và bằng chứng ở đâu.

Trạng thái dùng: `NOT RUN`, `PASS`, `FAIL`, `BLOCKED`. Chỉ ghi `PASS` khi đã chạy thật và có bằng chứng.

Mỗi lần chạy ghi đủ: commit SHA, OS/JDK/PostgreSQL, cấu hình, lệnh hoặc bước chạy, kết quả mong đợi, kết quả thật, log hoặc ảnh, người chạy. Lưu bằng chứng vào thư mục `evidence/<testId>/` (tạo khi có lần chạy đầu tiên).

## Answer / Submission

| ID | Tóm tắt | Owner / review | Task | Trạng thái | SHA | Bằng chứng |
|---|---|---|---|---|---|---|
| AT01 | Thí sinh B không đọc/sửa được bài của A, trước và sau khi nộp | A / C | T2-A4, T3-A4 | NOT RUN | | |
| AT02 | Chưa login hoặc sai role gọi import/start; WS thiếu token | A / C | T1-A2, T2-A1 | NOT RUN | | |
| AT03 | Revision 42 rồi 41: bản cũ không ghi đè | A / C | T2-A2 | NOT RUN | | |
| AT04 | Cùng revision cùng nội dung, khác nội dung, đổi thứ tự key | A / C | T2-A2 | NOT RUN | | |
| AT05 | requestId lặp; rollback không ACK thành công | A / C | T2-A2, T4-A1 | NOT RUN | | |
| AT06 | Autosave cũ đến sau submit không đổi bài đã chốt | A / C | T2-A3 | NOT RUN | | |
| AT07 | Chờ khóa qua deadline: quyết định theo giờ sau khóa | A / C | T2-A4 | NOT RUN | | |
| AT08 | Submit lặp, timeout, save đến gần nhau: một kết quả, chấm một lần | A / C | T2-A3, T4-A1 | NOT RUN | | |
| AT09 | Request của writer cũ không ghi được sau takeover | A / C | T2-A4 | NOT RUN | | |
| AT10 | Không nhận đáp án sau khi chốt hoặc sau deadline | A / C | T2-A4 | NOT RUN | | |
| AT11 | Server 40 / client 42, mất ACK, reconnect, mở app mới | B / A | T2-B4, T4-B1 | NOT RUN | | |

## Monitoring

| ID | Tóm tắt | Owner / review | Task | Trạng thái | SHA | Bằng chứng |
|---|---|---|---|---|---|---|
| MT01 | Full snapshot hợp lệ; event không lặp mỗi poll; collector không chạy trên giám thị | C / B | T1-C2/C3, T2-C1/C2 | NOT RUN | | |
| MT02 | Replay cùng message sau mất ACK | C / B | T3-C3 | NOT RUN | | |
| MT03 | Thiếu một delta → UNSYNCED, yêu cầu full | C / B | T3-C3 | NOT RUN | | |
| MT04 | Delta epoch cũ không sửa state epoch mới | C / B | T3-C3/C4 | NOT RUN | | |
| MT05 | Restart collector hoặc server → cần epoch và full mới | C / B | T3-C3/C4 | NOT RUN | | |
| MT06 | Không lùi state; full và delta cho cùng state và event set | C / B | T3-C3 | NOT RUN | | |
| MT07 | Event muộn, queue tràn; snapshot mới không xóa gap | C / B | T2-C2 | NOT RUN | | |
| MT08 | Kill client → server tự chuyển UNKNOWN | C / B | T2-C2 | NOT RUN | | |

## Listening và tích hợp

| ID | Tóm tắt | Owner / review | Task | Trạng thái | SHA | Bằng chứng |
|---|---|---|---|---|---|---|
| LT01 | Thiếu audio hoặc sai checksum → không READY | B / A | T3-B1 | NOT RUN | | |
| LT02 | Start lặp không phát lại; lỗi media khóa và ghi gián đoạn | B / A | T3-B2/B3 | NOT RUN | | |
| LT03 | Mất mạng giữa audio; mở lại không tự phát hay resume | B / A | T3-B3 | NOT RUN | | |
| LT04 | Tổ chức lại: attempt mới, dữ liệu và deadline cũ giữ nguyên | B / A | T3-B4, T3-A2 | NOT RUN | | |
| IT01 | Chạy từ package trên máy không có IDE, đường dẫn có dấu | B / A | T1-B4, T3-B5, T5-B1 | NOT RUN | | |
| IT02 | 2 thí sinh + 1 giám thị; một client gửi JSON sai hoặc rớt | A / C | T3-A4, T4-B1 | NOT RUN | | |
| IT03 | Mạng hoặc quét chậm, UI vẫn phản hồi; đóng app dọn worker | B / A | T1-B2, T4-B1 | NOT RUN | | |
| IT04 | DB hoặc server tắt giữa lúc xử lý; khởi động lại | A / C | T3-A3, T4-A1, T4-B1 | NOT RUN | | |

## Thực nghiệm

| Mã | Nội dung | Ai chạy | Task | Trạng thái | Nơi lưu raw |
|---|---|---|---|---|---|
| E1 | Polling 250/500/1.000/2.000 ms, 3 lần lặp (12 run) | B chạy, C cấp harness và phân tích | T4-B2, T4-C3 | NOT RUN | |
| E2 | Full snapshot vs delta, 3 trace × 3 lần × 2 chế độ (18 run) | C | T4-C2 | NOT RUN | |
| Tải | 1/5/10 client giả lập, 3 lần mỗi mức (9 run) | A | T4-A2 | NOT RUN | |

## Kiểm tra acceptance chặng 1 (ngoài 27 case chính thức)

Các kiểm tra dưới đây không thay đổi trạng thái AT/MT/LT/IT ở trên.

| Ngày | Phạm vi | SHA | Môi trường | Kết quả | Bằng chứng |
|---|---|---|---|---|---|
| 03/10/2026 | Maven build + unit test T1-A1/B1/C1 | `864f701` | Windows 11, JDK 21.0.8, Maven 3.9.11 | PASS, 5 module; 7/7 test | `evidence/stage1/2026-10-03-skeleton-smoke.md` |
| 03/10/2026 | Reset PostgreSQL, Flyway V1, login candidate/proctor/sai | code sau đó commit tại `bb48376` | PostgreSQL 18.6 Docker | PASS; 200/200/401 | `evidence/stage1/2026-10-03-skeleton-smoke.md` |
| 03/10/2026 | ProcessHandle probe | `1f012c4` | Windows 11, user thường | PASS có giới hạn; 133/200 process thiếu metadata | `evidence/stage1/2026-10-03-skeleton-smoke.md` |
| 03/10/2026 | Windows app-image local | `864f701` | JDK/jpackage 21.0.8 | PASS local; chưa chạy máy thứ hai | `evidence/stage1/2026-10-03-skeleton-smoke.md` |

## Review-fix PR #1 — 03/10/2026

Code được kiểm: `961feca789fa8d9e3ded554d0ecb21fccbe43d47`. `mvn test` chạy trước commit trên cùng code; `mvn package` chạy sau commit. Môi trường: Windows 11 x64, Oracle JDK 21.0.8, Maven 3.9.11, qua terminal WSL. Người chạy: Codex Agent. Không thay đổi trạng thái 27 case chính thức ở trên.

| Kiểm tra | Kết quả | Bằng chứng |
|---|---|---|
| `mvn test` | PASS — 5/5 module, 42/42 test; 0 failure/error/skipped | `evidence/stage1/2026-10-03-review-build.txt` |
| `mvn package` | PASS — 5/5 module, 42/42 test; fat JAR client được tạo | Cùng file trích log build |
| Login HTTP 200: malformed JSON, thiếu user, role ADMIN, body rỗng | PASS — future lỗi InvalidServerResponseException; không trả DTO; thông báo UI chung được kiểm ở mức hàm | LoginApiClientTest và trích log build |
| Các trường bắt buộc thiếu/null/blank/sai kiểu; JSON null/array/user sai kiểu/trailing data | PASS — response bị từ chối an toàn | LoginApiClientTest (35 lượt test mới tổng cộng) |
| LAN URL, thiếu scheme, HTTP chậm bằng latch, map candidate/proctor; HTTP proctor hợp lệ và HTTP 401 JSON/non-JSON | PASS — unit/HTTP local; không phải LAN máy thứ hai | LoginApiClientTest |
| Candidate/proctor/sai mật khẩu/server tắt trên GUI thật | NOT RUN — không có công cụ thao tác GUI trong phiên Agent | Phần review-fix trong smoke evidence |
| LAN second-machine test | NOT RUN — chưa có máy Windows thứ hai thật | Cùng smoke evidence |

Reset/migration PostgreSQL, HTTP login thật vào server, app-image và probe ProcessHandle không chạy lại trong phiên review; kết quả lịch sử ở các dòng acceptance phía trên vẫn thuộc phiên trước. Kiểm tra source xác nhận sendAsync/Platform.runLater/stop() vẫn giữ; chưa thay cho test tương tác GUI.

## Verification cuối trước merge PR #1 — 03/10/2026

HEAD đối chiếu: `ceb8131a558660057cf62c46f2466db8515b9b4e`. Chỉ cập nhật docs/evidence, không chạy lại build/test. Source không đổi so với code đã kiểm tại `961feca`; 42/42 PASS và package PASS tiếp tục trỏ tới log lượt chạy thật trước.

| Kiểm tra | Trạng thái | Lý do / bằng chứng |
|---|---|---|
| PR description phản ánh 42/42 test, package và review-fix | PASS | GitHub update PR #1 thành công; không còn dòng 7/7 tests cũ |
| Candidate GUI | NOT RUN | Không có công cụ thao tác GUI thật |
| Proctor GUI | NOT RUN | Cùng lý do |
| Wrong-password GUI | NOT RUN | Cùng lý do |
| Server-off / bad URL GUI | NOT RUN | Cùng lý do |
| Candidate/proctor/wrong password qua LAN máy thứ hai | NOT RUN | Không có máy Windows thứ hai thật được cung cấp |
| A reviewed B | NOT RUN | Chưa đủ xác nhận review thực tế; GitHub chưa có review/comment |
| B reviewed C | NOT RUN | Cùng lý do |
| C reviewed A | NOT RUN | Cùng lý do |

Bằng chứng và giới hạn xác nhận: `evidence/stage1/2026-10-03-merge-readiness.md`. Không thay đổi trạng thái 27 test chính thức hay TRACKER.json. Ready to merge: NO.
