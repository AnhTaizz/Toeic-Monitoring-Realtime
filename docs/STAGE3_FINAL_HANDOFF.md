# Bàn giao nghiệm thu cuối Phase3

19/10/2026 — LAN vật lý0/41; nghe thủ công và MSI máy thứ hai NOT RUN. Đây là hướng dẫn còn bắt buộc, không là chữ ký nghiệm thu. Người dùng đã giao Codex review kỹ thuật; người thao tác máy thật vẫn phải ghi Actual/evidence theo mẫu. FULL baseline, DELTA experimental opt-in, chưa có gate C/B.

## Artifact và source

Bản mới chỉ phát hành từ clean main sau PR47/57 và workflow tích hợp. Khi build hoàn tất, đọc sourceSHA, MSI version/SHA256 và hai JAR hash trong manifest bản build; kiểm cả A/B trước khi chạy. Không dùng MSI0.3.10 hoặc kit3044 làm bản cuối. Kit3044 vẫn giữ nguyên checksum/guards cho source cũ; copy checklist/templates từ PR52, không nhận một hash tự sửa trong kit cũ.

## B/C — hai Windows vật lý

1. Trên A: PostgreSQL TEST riêng, server/JDK21, proctor và candidateA. Trên B: cài MSI mới bằng quyền thường, không IDE/JDK trên PATH, chạy candidateB. Mọi client dùng cùng origin IPv4 LAN của A; DB chỉ loopback A. Không đổi firewall/adapter ngoài thao tác ngắt LAN đã được người vận hành cho phép.
2. Dùng [41case gốc](templates/t3c4-lan/cases.csv), [topology](templates/t3c4-lan/topology.json), [review](templates/t3c4-lan/review.json) và [query giữ lịch sử](templates/t3c4-lan/verify-history.sql). Ghi alias máy/OS/người chạy/time/sourceSHA/expected/actual/ảnh hoặc correlation. Không điền PASS trước khi thực hiện, không đưa token/tài khoản/IP private vào Git.
3. Ưu tiên nhóm F: rút LAN thật của B giữa Reading/Listening; A vẫn thao tác được, UNKNOWN/STALE đúng; re-auth/OPEN/epoch/FULL mới; không false ACK, answers đã ACK giữ; Listening không autoplay; timeline/gap cũ còn sau hồi phục. Local42case có faults SIMULATED không thay các case này.
4. B nghe audio thật và câu hỏi tương ứng, test loa, startIDtrùng không phát hai lần, interruption dừng/khóa, reconnect/mở lại không replay/resume. Theo LT01–LT04 trong [ma trận B](ACCEPTANCE_B_MANUAL_MATRIX.md). Ghi nhận thiết bị/codec thực, không coi MediaPlayer unit test là nghe thật.
5. Cài/chạy/gỡ MSI trên B ở đường dẫn Unicode/khoảng trắng, không IDE/JDK trên PATH. Giữ version/source/hash installer, Reading/Listening/FULL và cleanup. A review bằng chứng; không cài đè hoặc gỡ ứng dụng không thuộc lượt TEST.

## Chốt

C cùng B ghi quyết định gate theo dữ liệu máy thật. Nếu DELTA chưa đủ MT02–MT06/LAN: giữ FULL và ghi DELTA chưa production-ready; E2 không phải điều kiện chứng minh lợi ích trong Phase3. Tracker/hours chỉ đổi sau xác nhận và evidence thật; không tự ký A/B/C. Chỉ dùng STAGE3_DONE khi toàn bộ physical/manual/installer gates bắt buộc đã đạt.
