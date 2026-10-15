# T3-B4 · Điều khiển ca giám thị · 15/10/2026

Base `8f5ac40`, branch `feat/t3-b4-proctor-session-controls`. Implementation `3d91ea9`; nhãn điểm `832e040`, source cuối `06fd215969a91a33b5f9b6b2dea1a063a45a171a`. [Guide](../../docs/PROCTOR_SESSION_CONTROLS.md). Kết quả Git/PR/merge cuối xem PR/final. Main có A1/A2/B1–B3; PR35 draft `11c5da9` chỉ đọc đối chiếu, không ghép.

## Môi trường và phạm vi

Windows 11 x64, Temurin JDK21.0.10+7, Maven3.9.15, PostgreSQL18.6 container hiện có; timezone UTC chỉ cho JVM test. JavaFX stage, HTTP/WS/Spring/Flyway/PG thật trên một máy, schema TEST UUID riêng/audio/cache owned. Không reset DB dev. Không backend production, migration, dependency, .env, tracker/kế hoạch hoặc artifact cũ thay đổi. FULL mặc định.

## Kết quả mới

Lượt cuối source `06fd215`, output local `target/t3-b4/c037e616644546939ba7f35702382e8f/package.txt`, kết thúc 14:25:21 UTC+7. Source runtime đã commit; chỉ docs/evidence đang dirty. Không dùng docs/merge SHA thay source đã test.

| Kiểm tra mới | Kết quả / mức bằng chứng |
|---|---|
| Baseline exact-main `8f5ac40` | **669/669 PASS**, 0 skip; git archive source riêng trong target, package với PostgreSQL thật. |
| Final clean package | **686/686 PASS**, 0 failure/error/skip: protocol43/client439/server203/spike1; JDK21/PG18.6, `verify-t3b4.ps1 -Gui`. |
| B4 controller/API | **15 test PASS**: 7 controller +8 API/input. Worker/HTTP loopback thật, gateway/response MOCK, latches lỗi có kiểm soát. JSON sai kiểu/field, quyền/error body, giới hạn body/deadline, long >2^53, stale READY/callback/close và frozen retry được kiểm. |
| API production B4 | PASS một test mới dùng client B4→Spring/PG thật: role/phân công sai bị từ chối, chưa đủ READY chặn start, start/reorganize retry cùng kết quả, ACTIVE/SUBMITTED/fresh replacement và scope. |
| LT04 GUI production views | PASS một test mới: dashboard và candidate audio Stage thật; import qua controller, tạo ca bằng nút, READY từng lượt, WAV/WS start, disconnect, tổ chức lại trước/sau scheduler timeout thực. Không gọi timeout bằng tay, không sửa deadline bằng UI/test để giả thành công. |
| B1–B3 media regression | Rerun **8 checks PASS** với classpath/JAR `06fd215`, MediaPlayer/WAV/files/Unicode paths thật, API/transport MOCK; không phải app-image/installer hoặc nghe thủ công. Exact-main đối chứng 8 PASS. Có một lượt trước FAIL do file lock, xem dưới. |
| Dashboard FULL regression | PASS `smoke-t2c1 -Gui`: ProcessHandle/owned Edge/HTTP/WS/PG/proctor Stage, epoch/duplicate/conflict/permission, autonomous STALE/TTL/capacity/shutdown; fault process sets MOCK. Run T2C1-7d9c0724-daf7-4377-b1e7-c2a9c80439eb, 6 endpoint/63 nhóm COMPLETE; không đo hiệu năng. |
| Tài liệu/phạm vi | PASS 186 link nội bộ/anchor cùng tên method trong nhãn link; diff/staged diff và mẫu secrets/phạm vi PASS, TEST helper không vào JAR production. Output lớn chỉ target, evidence mới chỉ report này và 2 PNG tiêu biểu (173.149 byte tổng). |
| Cleanup | GUI test chờ worker toeic-proctor-controls còn 0; controller/view/player/transport đóng. Sau tất cả suite: truy vấn pg_namespace còn 0 schema t3a2 TEST. C1 smoke tự dọn riêng owned schema/Edge. |

**LT04 cụ thể:** lượt cũ `B4-S-3a8e6d4d-80f-candidate1` có writerEpoch2, savedRevision1, answers `{L1:A,R1:B}`, deadline `2026-10-15T07:24:22.709531Z`. Replacement `B4-R-6a1d72e2-d0a-candidate1` khác ID, answers rỗng/revision0/writer1/deadline null/NOT_STARTED/chưa READY; sau đó phải chuẩn bị riêng. Cả trước và sau khi scheduler chốt lượt cũ, bốn trường cũ và nội dung JSON của hai row MOCK event/gap giữ nguyên. Server chỉ đổi phần chốt/điểm tự nhiên, old result TIMED_OUT/2 đúng/score2/marker NETWORK_LOST đọc được. Replacement lại bị ngắt MEDIA_ERROR bằng API TEST; sau scheduler chốt mới bấm tổ chức tiếp thành `B4-R2-28e60cce-7c2-candidate1`, không sửa lượt trước.

[Ảnh lượt cũ sau timeout, answers và mapping](lt04-old-timed-out.png), [ảnh tổ chức tiếp sau timeout](lt04-after-timeout-reorganization.png). Đã xem ảnh thật: chữ/controls/mapping đọc được, có scrollbar trong tab; đây là ảnh từ lượt final GUI, không dựng mockup. Các ID là fixture MOCK được tạo trong schema TEST.


## Lượt lỗi và cách đối chiếu

- Baseline đầu không chạy được vì Maven chọn cache C:/.m2; dùng cache người dùng hiện có. Sau đó DB từ chối timezone legacy Asia/Saigon; UTC chỉ cho JVM test. Đã dựng baseline exact-main bằng git archive trong target riêng, package **669/669**, 0 skip, PostgreSQL thật. Không gọi source B4 là baseline main.
- GUI đầu đặt expected điểm 990 sai. Source `ExamService.calculateScore()` trả score=correctCount, hai đáp án đúng là 2; sửa expected theo công thức thực tế và kiểm thêm correctCount=2, không đổi backend để ép 990.
- API fixture có ca cũ dài 44 ký tự; UI dùng nhầm validator mã mới 40 ký tự nên chưa tới server. Tách đọc mã có sẵn theo v0 128 ký tự khỏi tạo mã mới 40; regression long-session PASS. Không nới Tomcat/backend/scope.
- Một lượt build bắt assert ACTIVE “Chưa chốt điểm” khi client đã compile trước lúc sửa nhãn và test compile sau. Chốt source rồi clean module để không trộn class cũ. Lượt kế tiếp sau chuyển phiên bị PostgreSQL tắt/connection refused; bật lại Docker/volume cũ và chạy lại, không reset hoặc sửa credential.
- Rà import bổ sung kiểm kiểu JSON trước Gson chuyển DTO: part=1.5 và examId dạng số phải bị từ chối; giữ hai ca âm trong test, không tự làm tròn/đổi kiểu đầu vào.

- Hồi quy media đầu có 5 PASS rồi FAIL vì xóa WAV thử loa khi Windows còn giữ file. Đối chứng exact-main 8 PASS; 22 class audio production và AudioPackageSmoke.class byte-identical giữa main/B4. Rerun B4 mới 8 PASS, không sửa B1–B3 để ép kết quả. **Nguyên nhân cụ thể của lần file lock chưa chứng minh**, rerun PASS không được dùng làm bằng chứng nguyên nhân. Giữ log lỗi/đối chứng/rerun riêng trong target; không sao chép thành bulk evidence.

## Giới hạn và review

Fixtures đề hai câu, WAV PCM và event/gap history là MOCK. Candidate B1/B2/B3 dùng MediaPlayer/HTTP/WS thật; click “Tôi nghe được” tự động là SIMULATED, không có người nghe xác nhận. Ngắt transport là có kiểm soát, không rút cáp vật lý. Lần thứ hai INTERRUPTED/MEDIA_ERROR gửi qua API thật là injected; không giả lỗi loa vật lý. Import tự động gọi cùng controller sau chọn file, không thao tác native FileChooser. Hai cửa sổ trên một máy không là LAN. Harness đăng nhập qua LoginApiClient thật rồi mở Stage production ProctorDashboardView và AudioPreparationView; đây là GUI integration của các view, chưa thao tác toàn bộ ToeicClientApplication từ màn login bằng người dùng. Không gọi component/MOCK test là GUI end-to-end thủ công.

Human A review, native FileChooser/nghe thủ công, LAN/máy thứ hai, MP3/M4A, Reading+Listening tích hợp PR35, B5 và E1/E2 NOT RUN. Không giả approval. A cần xem phân công/quyền đọc bài đã chốt, semantics start/retry/response chưa xác nhận, bảo toàn lượt cũ trước/sau scheduler; không có hook backend mới cần triển khai. Merge B4 không merge/duyệt PR35 hay chốt delta gate.
