# EX-01 · Test 01 recovery, 20/10/2026

Test 01 vẫn **BLOCKED**. Khôi phục được 178/676 nội dung lựa chọn có nhãn nguyên văn (26,33%): 170 nội dung tiếng Anh và 8 ký hiệu vị trí `[1]–[4]`. Còn 495 nội dung chưa tìm thấy; 3 lexical prefixes chỉ là `EXTRACTED_NEEDS_REVIEW` và chưa đưa vào draft. Draft vì vậy còn 498 option texts null. Không dịch ngược, tự gán thứ tự từ vựng hoặc nâng verification.

## CP1: audit trước thuật toán

Commit `5867299` lưu [audit 25 câu](recovery/AUDIT_CP1.md) và [DOM evidence](recovery/audit25.json) trước commit parser `4884f8c`.

| Part | Question numbers đã audit |
| --- | --- |
| 3 | 32, 33, 34, 50, 68 |
| 4 | 71, 72, 80, 93, 100 |
| 5 | 101, 102, 108, 115, 130 |
| 6 | 131, 132, 135, 141, 146 |
| 7 | 147, 150, 163, 181, 196 |

Mỗi row có question DOM, explanation, đúng nhãn key, text và attributes; ảnh/group giữ selector thật. Không có script hoặc serialized question-data element; tìm các marker `choices`, `choice_answer`, `correct_answer`, `sub_questions` không tìm được payload lựa chọn ẩn. `answer`/`explanation` chủ yếu là DOM review. Không suy rằng bản sanitized còn response API/JavaScript của trang gốc.

20 câu mẫu Part 3/4/6/7 có A–D dịch tiếng Việt, cùng text tiếng Anh của đáp án đúng trong answer details. Câu101 có nhãn A–D kèm gloss, ba lexical prefixes phải review. Các danh sách từ vựng không nhãn của bốn câu Part5 còn lại không chứng minh thứ tự A–D. Mẫu25 không có bộ bốn English options đã xác minh.

Khi quét đủ200 câu: câu192 chứa nguyên văn bốn tên tiếng Anh có nhãn; câu157/171 có bộ ký hiệu `[1]–[4]`. Chúng được giữ nguyên có provenance, không gọi ký hiệu là tiếng Anh. Các chuỗi dịch không dấu “Trang 2”, “Giao hoa” bị loại khỏi English fields. Từ tiếng Anh vay mượn “résumé” trong prompt106 được giữ đúng.

## Kết quả đo trên toàn Test 01

| Chỉ số | Kết quả |
| --- | --- |
| Câu / Part 3–7 / option slots | 200 / 169 / 775 |
| Câu đủ bốn English options SOURCE_EXACT | 1/169 (câu192; 0,59%) |
| Câu đủ English candidates kể cả NEEDS_REVIEW | 2 (101 và192); chưa HUMAN_VERIFIED |
| Bộ bốn position markers SOURCE_EXACT | 2 (157 và171), không tính English sets |
| Translation-only distractor sets, tối đa một English key | 164; không đồng nghĩa toàn câu chỉ có tiếng Việt |
| English option texts SOURCE_EXACT / lexical prefix review | 170 / 3 |
| Position markers SOURCE_EXACT / original texts chưa có | 8 / 495 |
| Key labels đối chiếu với parser trước | 200/200 khớp; cùng nguồn answer details |
| Answer keys review độc lập / HUMAN_VERIFIED | 0 / 0 |
| Documents/boundaries HUMAN_VERIFIED | 0; 19 group Part6/7 đang chờ |
| Structural errors / candidate importable | 0 / false |

`SOURCE_EXACT` chỉ có nghĩa copy nguyên văn tại selector/file hash ghi trong provenance. Đây không phải chứng nhận ETS nguyên bản hay backend `VERIFIED`. Nhận diện ngôn ngữ dùng rule bảo thủ; mọi nội dung còn cần review. `HUMAN_VERIFIED` là trạng thái dành cho bằng chứng review thật, lần chạy này không sinh giá trị đó. Transcript Part1/2 chỉ ở evidence review, không vào candidate prompt/options. JSON V2 chỉ có DTO fields, không có reconstruction sidecar.

## Các field còn thiếu và cần review

| Part | Câu còn thiếu/cần review options | Labels chưa có | Labels chỉ NEEDS_REVIEW |
| --- | --- | --- | --- |
| 3 | 39 | 117 | 0 |
| 4 | 30 | 90 | 0 |
| 5 | 30 | 87 | 3 |
| 6 | 16 | 48 | 0 |
| 7 | 51 | 153 | 0 |

Original question/passage text cho câu131–146 còn thiếu; numeric blanks không được coi là prompt gốc. [Document sheet](../../data/seedingTest/generated/ex01/recovery/ets2026_test01/document-review.json) liệt kê đúng19 group, question numbers và image media IDs. Giữ ảnh/mapping gốc; số ảnh là evidence inventory, không chứng minh ranh giới single/double/triple passage. Không OCR chưa đối chiếu, không placeholder TEXT. Toàn bộ200 answer keys và73 group mappings vẫn cần review độc lập.

Labels theo đúng số câu (các câu157/171/192 không có missing option labels; vẫn UNVERIFIED):

| Question | Part | MISSING labels | NEEDS_REVIEW labels |
| --- | --- | --- | --- |
| 32 | 3 | A, C, D | — |
| 33 | 3 | A, B, C | — |
| 34 | 3 | A, C, D | — |
| 35 | 3 | A, B, D | — |
| 36 | 3 | B, C, D | — |
| 37 | 3 | A, B, C | — |
| 38 | 3 | B, C, D | — |
| 39 | 3 | A, B, C | — |
| 40 | 3 | B, C, D | — |
| 41 | 3 | A, B, D | — |
| 42 | 3 | A, B, C | — |
| 43 | 3 | A, C, D | — |
| 44 | 3 | A, B, D | — |
| 45 | 3 | A, B, D | — |
| 46 | 3 | B, C, D | — |
| 47 | 3 | B, C, D | — |
| 48 | 3 | A, B, C | — |
| 49 | 3 | A, B, D | — |
| 50 | 3 | A, B, D | — |
| 51 | 3 | A, C, D | — |
| 52 | 3 | B, C, D | — |
| 53 | 3 | A, B, D | — |
| 54 | 3 | B, C, D | — |
| 55 | 3 | A, C, D | — |
| 56 | 3 | A, C, D | — |
| 57 | 3 | B, C, D | — |
| 58 | 3 | A, B, D | — |
| 59 | 3 | B, C, D | — |
| 60 | 3 | A, B, D | — |
| 61 | 3 | A, C, D | — |
| 62 | 3 | A, B, D | — |
| 63 | 3 | A, B, D | — |
| 64 | 3 | A, B, C | — |
| 65 | 3 | A, B, C | — |
| 66 | 3 | B, C, D | — |
| 67 | 3 | A, B, D | — |
| 68 | 3 | A, C, D | — |
| 69 | 3 | B, C, D | — |
| 70 | 3 | A, B, D | — |
| 71 | 4 | A, C, D | — |
| 72 | 4 | A, B, D | — |
| 73 | 4 | B, C, D | — |
| 74 | 4 | A, C, D | — |
| 75 | 4 | A, B, C | — |
| 76 | 4 | B, C, D | — |
| 77 | 4 | A, C, D | — |
| 78 | 4 | A, B, C | — |
| 79 | 4 | B, C, D | — |
| 80 | 4 | A, B, C | — |
| 81 | 4 | B, C, D | — |
| 82 | 4 | A, C, D | — |
| 83 | 4 | A, B, C | — |
| 84 | 4 | B, C, D | — |
| 85 | 4 | A, B, C | — |
| 86 | 4 | B, C, D | — |
| 87 | 4 | A, C, D | — |
| 88 | 4 | A, B, D | — |
| 89 | 4 | A, B, C | — |
| 90 | 4 | A, C, D | — |
| 91 | 4 | A, B, D | — |
| 92 | 4 | A, C, D | — |
| 93 | 4 | B, C, D | — |
| 94 | 4 | A, B, C | — |
| 95 | 4 | B, C, D | — |
| 96 | 4 | A, B, D | — |
| 97 | 4 | A, B, C | — |
| 98 | 4 | A, B, C | — |
| 99 | 4 | A, B, D | — |
| 100 | 4 | B, C, D | — |
| 101 | 5 | — | A, C, D |
| 102 | 5 | B, C, D | — |
| 103 | 5 | B, C, D | — |
| 104 | 5 | A, C, D | — |
| 105 | 5 | A, C, D | — |
| 106 | 5 | A, B, D | — |
| 107 | 5 | A, B, D | — |
| 108 | 5 | A, B, C | — |
| 109 | 5 | B, C, D | — |
| 110 | 5 | B, C, D | — |
| 111 | 5 | A, B, D | — |
| 112 | 5 | A, B, D | — |
| 113 | 5 | B, C, D | — |
| 114 | 5 | A, B, D | — |
| 115 | 5 | A, B, D | — |
| 116 | 5 | A, B, C | — |
| 117 | 5 | B, C, D | — |
| 118 | 5 | A, B, C | — |
| 119 | 5 | A, B, D | — |
| 120 | 5 | A, C, D | — |
| 121 | 5 | A, B, C | — |
| 122 | 5 | A, B, D | — |
| 123 | 5 | A, C, D | — |
| 124 | 5 | A, B, D | — |
| 125 | 5 | B, C, D | — |
| 126 | 5 | B, C, D | — |
| 127 | 5 | A, B, C | — |
| 128 | 5 | A, B, C | — |
| 129 | 5 | A, B, C | — |
| 130 | 5 | A, C, D | — |
| 131 | 6 | A, B, C | — |
| 132 | 6 | A, B, D | — |
| 133 | 6 | B, C, D | — |
| 134 | 6 | A, C, D | — |
| 135 | 6 | A, B, C | — |
| 136 | 6 | A, B, D | — |
| 137 | 6 | A, C, D | — |
| 138 | 6 | B, C, D | — |
| 139 | 6 | B, C, D | — |
| 140 | 6 | A, B, D | — |
| 141 | 6 | A, C, D | — |
| 142 | 6 | A, B, C | — |
| 143 | 6 | A, B, D | — |
| 144 | 6 | A, B, D | — |
| 145 | 6 | A, C, D | — |
| 146 | 6 | B, C, D | — |
| 147 | 7 | A, B, D | — |
| 148 | 7 | A, B, C | — |
| 149 | 7 | A, C, D | — |
| 150 | 7 | A, B, D | — |
| 151 | 7 | A, B, C | — |
| 152 | 7 | A, B, D | — |
| 153 | 7 | A, C, D | — |
| 154 | 7 | A, B, D | — |
| 155 | 7 | A, B, C | — |
| 156 | 7 | A, B, D | — |
| 158 | 7 | A, B, C | — |
| 159 | 7 | A, C, D | — |
| 160 | 7 | A, B, D | — |
| 161 | 7 | A, B, D | — |
| 162 | 7 | A, B, C | — |
| 163 | 7 | A, C, D | — |
| 164 | 7 | A, C, D | — |
| 165 | 7 | A, C, D | — |
| 166 | 7 | B, C, D | — |
| 167 | 7 | A, C, D | — |
| 168 | 7 | A, B, D | — |
| 169 | 7 | A, C, D | — |
| 170 | 7 | A, C, D | — |
| 172 | 7 | A, B, C | — |
| 173 | 7 | B, C, D | — |
| 174 | 7 | A, C, D | — |
| 175 | 7 | B, C, D | — |
| 176 | 7 | B, C, D | — |
| 177 | 7 | A, B, C | — |
| 178 | 7 | A, B, D | — |
| 179 | 7 | A, B, C | — |
| 180 | 7 | A, B, D | — |
| 181 | 7 | A, B, C | — |
| 182 | 7 | A, B, C | — |
| 183 | 7 | B, C, D | — |
| 184 | 7 | A, B, D | — |
| 185 | 7 | A, C, D | — |
| 186 | 7 | A, C, D | — |
| 187 | 7 | B, C, D | — |
| 188 | 7 | A, C, D | — |
| 189 | 7 | B, C, D | — |
| 190 | 7 | A, B, C | — |
| 191 | 7 | A, B, C | — |
| 193 | 7 | A, C, D | — |
| 194 | 7 | B, C, D | — |
| 195 | 7 | B, C, D | — |
| 196 | 7 | A, C, D | — |
| 197 | 7 | B, C, D | — |
| 198 | 7 | A, B, C | — |
| 199 | 7 | A, B, D | — |
| 200 | 7 | B, C, D | — |

## Artifacts và nguồn bổ sung

[Snapshot versioned](../../data/seedingTest/generated/ex01/recovery/ets2026_test01/) gồm recovered-normalized, explanation-evidence, missing-original-options, answer-key-review, document-review, recovery-summary, quality-report và draft.v2 JSON. [SHA-256](recovery/artifacts-sha256.json) kiểm từng file; archive ngoài Git giữ cùng nội dung và media.

Cần original English A–D cho495 labels và đối chiếu ba prefixes A/C/D câu101; nguồn original question/passages cho131–146; TEXT/structure và boundary review cho19 group; answer-key review độc lập200 câu. Chưa có nguồn supplement thực trong lượt này. [Hướng dẫn xuất hợp lệ và format join](ORIGINAL_SOURCE_EXPORT.md). Join theo examId+part+questionNumber; nhãn A–D tường minh, không dựa thứ tự; key khác bị từ chối. HTML không khớp adapter bị từ chối và yêu cầu JSON có mapping, không đoán DOM.

## Kiểm thử và gate

Source `1b2bccf8f17e5f2468dc9e6729df8b0bcfa43a62`; 64 tests PASS / 0 FAIL / 0 ERROR / 0 SKIP: 47 baseline +12 recovery +5 archive. [Summary](recovery/tests-summary.json), [raw Maven](recovery/tests-final.log). Có ca VN có/không dấu, đảo nhãn, table/list/paragraph, gloss/teacher, thiếu option, conflict; real200 giữ keys và không lộ transcript; join sai key/mapping; baseline mutation bắt group không liên tiếp/document mismatch; archive sai checksum/traversal/overwrite/worktree. Một ca Unicode loanword từng FAIL đã sửa trước lượt64 PASS; không giữ kết quả FAIL thành PASS.

[DTO probe thật](recovery/contract-final.log) ở EX06 `448f58e659131ab189a81c378970a501cf8455cf`: shape PASS và validator từ chối source UNVERIFIED. Chỉ probe compile adapters, không phải ETS HTTP/PG. Các source/question/group statuses của V2 vẫn UNVERIFIED; media metadata LOCAL_VERIFIED giữ hash file thật. Không sửa importer, media API, PostgreSQL schema hoặc JavaFX.

[Backup/restore](BACKUP_RESTORE.md) đã thực hiện JSON+358media và PostgreSQL+catalog **MOCK**. ETS import/restore acceptance, GUI/hearing/LAN chưa chạy vì data gate chưa đạt. Không đánh dấu EX01/EX08 DONE hoặc thay BACKLOG/TRACKER/hours.
