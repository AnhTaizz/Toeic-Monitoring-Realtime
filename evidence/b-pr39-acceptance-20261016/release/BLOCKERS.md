# Blockers và gate còn lại

Không còn P0/P1 đã phát hiện nhưng chưa sửa trong phạm vi QA. MSI_BUILT + INSTALLED_LOCAL_PASS; không installer/toolchain BLOCKED nữa.

| Mục | Trạng thái | Owner / hành động |
|---|---|---|
| Hai Windows vật lý, LAN thật, cài máy thứ hai | PHYSICAL_LAN_NOT_RUN / SECOND_MACHINE_NOT_RUN | B bố trí máy/operator theo lan/MANUAL_STEPS_FOR_OWNER; người dùng xác nhận hiện chỉ có một máy |
| Nghe thật/physical disconnect/thiết bị/DPI/codec ngoài WAV | NOT RUN | B nghe/đánh giá thật và ghi actual; không ký dựa autoclick |
| QD29/progress/backend contract | NEEDS_OWNER_REVIEW | A/C quyết định chính sách cấp ACTIVE/legacy ID/scope, A review B fixes; không coi PROPOSED là approved |
| FULL/epoch/gaps/E1-method/gate | NEEDS_OWNER_REVIEW | C review evidence/test semantics; không bật production DELTA, không chạy lại E1/E2 để cộng PASS |
| QA database đầu phiên | BLOCKED cleanup do shared container đang dừng | Owner xử lý database TEST có tên ghi trong private qa-database.txt khi shared container được phép hoạt động; agent không tự restart shared service |
| Reviewer độc lập/human A/B/C | NOT RUN | Đây là review kỹ thuật mới trong cùng Codex session, không thay chữ ký owner |

Container PostgreSQL TEST bổ sung đã remove,0connection/0schema/0driver/0installed-client trước stop. Shared container giữ trạng thái exited. Chỉ database TEST đầu phiên được giữ vì không thể query/drop khi service stopped; không xóa volume/application DB.

Lệnh cleanup cho owner sau khi tự xác minh quyền và shared DB đã hoạt động (không chạy trong phiên này):

```powershell
$db = [IO.File]::ReadAllText((Join-Path (Get-Location) 'jpackage-out/pr39-qa-20261016/qa-database.txt')).Trim()
if ($db -notmatch '^qa_b39_[a-f0-9]{16}$') { throw 'Unsafe TEST database name' }
# Dùng đúng role được cấp, kiểm không có session; không WITH FORCE, không down -v.
docker exec toeic-db psql -U <authorized-role> -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE $db;"
```

Đề xuất **CONDITIONALLY_READY** cho owner review, không tự merge main hoặc đóng/mark Ready các PR nguồn.
