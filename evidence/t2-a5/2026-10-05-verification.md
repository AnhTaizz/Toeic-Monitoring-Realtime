# Bằng chứng kiểm thử — Task T2-A5: Tài liệu giao dịch và bằng chứng quyền

- **Ngày thực hiện:** 05/10/2026
- **Người thực hiện:** Vai A
- **Nhánh:** `feat/t2-a5-transaction-docs-and-auth-evidence`
- **Mục tiêu:** Hoàn thành nhiệm vụ T2-A5 (Chặng 2) theo `Ke_hoach_LT_Mang_5_chang/01_KE_HOACH.md`, `02_HOP_DONG.md` (Mục 2), và `03_KIEM_THU_VA_THUC_NGHIEM.md` (AT01–AT10).

---

## 1. Tóm tắt kết quả Task T2-A5

1. **Tài liệu chuyên đề Giao dịch và Phân quyền (`docs/GIAO_DICH_VA_QUYEN.md`):**
   - Đầy đủ 4 sơ đồ tuần tự Mermaid: Autosave, Submit/Scoring, Background Timeout, Takeover Writer/Reconnect.
   - Bảng mẫu response chuẩn cho toàn bộ mã thành công (`SAVED`, `ALREADY_SAVED`, `SUBMITTED`) và mã lỗi (`STALE`, `CONFLICT`, `EXPIRED`, `INVALID_STATE`, `INVALID_INPUT`, `FORBIDDEN`, `UNAUTHORIZED`).
   - Trích xuất log thực tế và câu truy vấn SQL có `FOR UPDATE OF a`, `clock_timestamp()`, `writer_epoch`, `saved_revision`.
   - Lập luận kỹ thuật chi tiết cho 4 câu hỏi bảo vệ đồ án trọng tâm (clock_timestamp vs now, idempotency authorization, submit/save race conditions, split-brain writer epoch).

2. **Cập nhật Trạng thái Toàn bộ Bộ Test Cases Answer / Submission (AT01 -> AT10):**
   - Cập nhật [docs/KIEM_THU.md](file:///c:/Users/Lenovo/OneDrive/Máy%20tính/LTM/Toeic-Monitoring-Realtime/docs/KIEM_THU.md) toàn bộ các test cases AT01 đến AT10 sang trạng thái **PASS**.

---

## 2. Bảng Tổng Hợp Kiểm Thử Nghiệm Thu Chặng 2 (AT01 – AT10)

| Test ID | Tóm tắt kịch bản | Môi trường | Lệnh / Script | SHA | Trạng thái | Bằng chứng |
|---|---|---|---|---|---|---|
| **AT01** | Thí sinh B không đọc/sửa được bài của A (trước và sau khi nộp) | Windows 11 / JDK 25 / PostgreSQL 18.6 | `scripts/smoke-t2a4.ps1` | `60e1ced` | **PASS** | `evidence/t2-a4/2026-10-05-verification.md` |
| **AT02** | Chưa login hoặc sai role gọi import/start; WS thiếu token | Windows 11 / JDK 25 / PostgreSQL 18.6 | `scripts/smoke-t2a1.ps1` | `5fafab2` | **PASS** | `evidence/t2-a1/2026-10-05-verification.md` |
| **AT03** | Revision 42 rồi 41: bản cũ không ghi đè | Windows 11 / JDK 25 / PostgreSQL 18.6 | `scripts/smoke-t2a2.ps1` | `443458d` | **PASS** | `evidence/t2-a2/2026-10-05-verification.md` |
| **AT04** | Cùng revision cùng nội dung (200), khác nội dung (409), đổi thứ tự key JSON | Windows 11 / JDK 25 / PostgreSQL 18.6 | `scripts/smoke-t2a2.ps1` | `443458d` | **PASS** | `evidence/t2-a2/2026-10-05-verification.md` |
| **AT05** | requestId lặp; retry cùng payload (200), khác payload (409) | Windows 11 / JDK 25 / PostgreSQL 18.6 | `scripts/smoke-t2a2.ps1` | `443458d` | **PASS** | `evidence/t2-a2/2026-10-05-verification.md` |
| **AT06** | Autosave cũ hoặc nộp lại đến sau submit không đổi bài đã chốt | Windows 11 / JDK 25 / PostgreSQL 18.6 | `scripts/smoke-t2a3.ps1` | `f28099b` | **PASS** | `evidence/t2-a3/2026-10-05-verification.md` |
| **AT07** | Chờ khóa qua deadline: quyết định theo giờ `clock_timestamp()` sau khóa | Windows 11 / JDK 25 / PostgreSQL 18.6 | `scripts/smoke-t2a4.ps1` | `60e1ced` | **PASS** | `evidence/t2-a4/2026-10-05-verification.md` |
| **AT08** | Submit lặp, timeout, save đến gần nhau: một kết quả, chấm một lần | Windows 11 / JDK 25 / PostgreSQL 18.6 | `scripts/smoke-t2a3.ps1` | `f28099b` | **PASS** | `evidence/t2-a3/2026-10-05-verification.md` |
| **AT09** | Request của writer cũ không ghi được sau takeover (409 STALE) | Windows 11 / JDK 25 / PostgreSQL 18.6 | `scripts/smoke-t2a4.ps1` | `60e1ced` | **PASS** | `evidence/t2-a4/2026-10-05-verification.md` |
| **AT10** | Không nhận đáp án sau khi chốt hoặc sau deadline (409 EXPIRED/INVALID_STATE) | Windows 11 / JDK 25 / PostgreSQL 18.6 | `scripts/smoke-t2a4.ps1` | `60e1ced` | **PASS** | `evidence/t2-a4/2026-10-05-verification.md` |

---

## 3. Kết luận

- Toàn bộ các yêu cầu của Vai A trong Chặng 2 (T2-A1, T2-A2, T2-A3, T2-A4, T2-A5) đã hoàn thành 100%, vượt qua tất cả các bài kiểm thử unit/integration (375 tests) và smoke test thực tế trên PostgreSQL 18.6.
- Toàn bộ tài liệu hợp đồng, hướng dẫn giao dịch, mã lỗi và bằng chứng kiểm thử đã sẵn sàng cho Vai B và Vai C tích hợp.
