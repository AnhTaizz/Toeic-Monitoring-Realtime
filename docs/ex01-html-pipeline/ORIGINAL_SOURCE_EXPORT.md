# Xuất dữ liệu gốc có quyền truy cập để bổ sung Test 01

Chỉ xuất trang/tệp mà tài khoản của người cung cấp được phép xem và sử dụng. Quyền tải/dùng/công bố media đã được người dùng xác nhận trong phiên, lưu tại `media-permission.json`; đó không tự tạo thêm quyền vượt cơ chế kiểm soát truy cập.

1. Đăng nhập bình thường bằng tài khoản được cấp quyền; mở đúng Test01 và ghi exam/version, nguồn, ngày xuất, người xác nhận phạm vi quyền. Nếu trang không còn khả dụng, lấy bản lưu/JSON từ chủ sở hữu nội dung.
2. Dùng tính năng Export/Download của hệ thống nếu có; hoặc Save Page/DOM đang hiển thị hợp lệ. Lưu cả passage và nhãn A–D, không chỉ màn lời giải dịch. Nếu nội dung tải qua API, chỉ lưu response đã được tài khoản truy cập hợp lệ bằng công cụ trình duyệt/Export được phép; không tạo lại token, crawl endpoint khác, vượt CAPTCHA/paywall hay kiểm soát truy cập.
3. Loại cookie, Authorization, token, HAR chứa secrets và thông tin cá nhân không liên quan trước khi chuyển file. Không gửi credentials vào repo. Lưu raw riêng cùng SHA-256; giữ quan hệ question/group và tên file ảnh gốc. Không cần fetch media trong lúc thí sinh thi.
4. Nếu DOM có adapter bên dưới thì dùng HTML; nếu chưa có adapter, chuyển/export JSON có mapping tường minh từ nguồn hiển thị. Không dịch ngược hoặc tự dựng A–D từ danh sách vocabulary không nhãn. Chưa rõ nhãn thì giữ thiếu và nhờ người có nguồn xác nhận.
5. Cung cấp riêng original text/structure và questionNumbers cho19 groups Part6/7; xác nhận single/double/triple passage bằng nguồn. Giữ image mapping để đối chiếu. Cung cấp key review độc lập, ghi người/ngày/evidence; parser không tự ký HUMAN_VERIFIED.

## JSON local được hỗ trợ

Ví dụ sau là **MOCK mô tả format**, examId MOCK cố ý không join được vào ETS. Thay bằng chính text/key trích từ nguồn có bằng chứng, không dùng các câu MOCK để điền dữ liệu thiếu:

```json
{
  "examId": "MOCK-example",
  "questions": [{
    "questionNumber": 101, "part": 5,
    "prompt": "101. MOCK original prompt.", "correctOption": "B",
    "options": [
      {"optionId":"D","text":"MOCK Delta"},
      {"optionId":"A","text":"MOCK Alpha"},
      {"optionId":"B","text":"MOCK Beta"},
      {"optionId":"C","text":"MOCK Gamma"}
    ]
  }],
  "groups": [{
    "part": 6, "questionNumbers": [131,132,133,134],
    "documents": [{"documentId":"original-email","title":"MOCK email","text":"MOCK full original document text."}]
  }]
}
```

`questions` bắt buộc, có thể chỉ gồm những câu đã thu được; `groups` và `prompt`/`correctOption` có thể vắng. Exam thật dùng đúng `ets2026-test01`. Mỗi row cần đủ4 option labels (Part2 là3); key nếu có phải khớp lịch sử. Không nhận part/number ngoài range, trùng câu/nhãn, thiếu option hoặc tiếng Việt/teacher text làm original. Part1/2 bỏ text để tránh transcript trong candidate. Document `questionNumbers` phải khớp nguyên group đã parse, Part6 một document, Part7 tối đa3, text thật không rỗng. Tất cả vẫn UNVERIFIED cho tới review; supplemental key match không phải human approval.

## HTML adapter local được hỗ trợ

```html
<main data-exam-id="MOCK-example">
 <article data-question-number="101" data-part="5" data-correct-option="B">
  <p data-original-prompt>101. MOCK original prompt.</p>
  <p data-option-id="A">MOCK Alpha</p><p data-option-id="B">MOCK Beta</p>
  <p data-option-id="C">MOCK Gamma</p><p data-option-id="D">MOCK Delta</p>
 </article>
</main>
```

Đây là hợp đồng adapter tường minh, không phải khẳng định Zenlish tự xuất đúng attributes này. Không có `data-exam-id` thì tool từ chối; dùng JSON hoặc bổ sung adapter dựa DOM gốc đã được review. File SHA+selector/index được giữ trong provenance. Với source bổ sung cần lưu cả file gốc riêng cùng recovery archive.

```powershell
# Sau bước build ở BACKUP_RESTORE.md; không import backend ở đây.
.\tools\exam-dataset\maintain.ps1 -Action recover -Supplement '<original-export.json>' -OutputPath '<new-recovery-directory>'
# Exit 2 là BLOCKED có chủ đích, đọc quality/missing reports.
```

## Bổ sung ngày 20/10 — dùng template và ghép nhiều nguồn

[Template Test01](final-integration/original-source-template.json) được sinh từ dữ liệu hiện có; [blockers đủ200câu](final-integration/question-blockers.json) và [labels thiếu/cần review](final-integration/missing-original-options.json) chỉ rõ việc cần làm. Template **INCOMPLETE_NOT_IMPORTABLE**, có null và documents rỗng, không được dùng trực tiếp như dữ liệu gốc. Điền từ export hợp lệ; chỉ giữ rows đã hoàn chỉnh trong mỗi supplement. Không dùng chuỗi `TODO`/`MOCK`/bản dịch làm options thật.

Sau build, chạy trực tiếp trong PowerShell tại ETL worktree:

```powershell
# Dùng normalized baseline, giữ nguyên các raw supplements ở ngoài Git.
.\tools\exam-dataset\maintain.ps1 -Action recover `
  -Normalized '.\tools\exam-dataset\target\output\ets2026_test01\normalized.json' `
  -Supplement @('<export-original-1.json>', '<export-original-2.json>') `
  -OutputPath '<new-recovery-directory>'
```

Nguồn SOURCE_EXACT khác nội dung bị báo conflict thay vì ghi đè. Key khác lịch sử cũng bị từ chối. Người có nguồn phải giải quyết mâu thuẫn bằng evidence và review riêng; không đổi key/status để làm tool chạy qua. Một file bị từ chối không làm thay đổi dataset/recovery trong bộ nhớ; CLI không ghi output trước khi đã ghép xong. Nguồn khớp giữ cả evidence cũ và mới; SHA pin đúng bytes được parse. HTML cần đúng một `data-exam-id`; chỉ lấy questions trong container đó.

Với documents chứa ảnh, cung cấp **ordered `blocks`** thay cho text-only. Ví dụ sau là **MOCK mô tả format**, không điền vào ETS:

```json
{
  "examId": "MOCK-example",
  "questions": [],
  "groups": [{
    "part": 6,
    "questionNumbers": [131, 132, 133, 134],
    "documents": [{
      "documentId": "original-letter",
      "title": "MOCK title from source",
      "blocks": [
        {"type": "TEXT", "text": "MOCK full original document text."},
        {"type": "IMAGE", "mediaId": "MOCK-existing-passage-image"}
      ]
    }]
  }]
}
```

Trong dữ liệu thật, copy đúng mediaId từ normalized `groups[].passageBlocks`, giữ đầy đủ thứ tự ảnh của group và chỉ đặt ảnh vào document mà nguồn chứng minh. Không tự suy một ảnh = một document. Tool từ chối ảnh ngoài group, ảnh bị bỏ mất/đảo thứ tự, documents trùng ID hoặc khác nguồn cũ. TEXT không rỗng là bắt buộc; TEXT-only legacy vẫn dùng được cho group không có ảnh. Document/blocks giữ provenance riêng theo field; boundaries vẫn NEEDS_REVIEW. Adapter HTML hiện hỗ trợ questions; documents có ordered media cần JSON mapping tường minh.

`SOURCE_EXACT`, matching key và local media hash không thay cho review độc lập. Toàn bộ200 key và73 group/19 document-boundary reviews hiện còn chờ. Hãy giữ raw export đã loại secrets cùng SHA ngoài Git và đưa vào snapshot mới khi có nguồn; không chỉ giữ pointer tới file sẽ mất.
