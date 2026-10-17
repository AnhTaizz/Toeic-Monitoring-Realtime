# Environment readiness — 16/10/2026

| Hạng mục | Trạng thái thực | Bằng chứng |
|---|---|---|
| Windows11 x64 desktop | AVAILABLE | Native OS10.0.26200.0; JavaFX Application/Media chạy thật |
| Ordinary user | AVAILABLE | Administrator=false; no existing Toeic install trước test, uninstall hậu kiểm riêng |
| JDK21/Maven/JavaFX | AVAILABLE | Oracle21.0.8/Maven3.9.11/JavaFX21.0.12, probe mới và packaged JVM |
| WiX3/SDK mt | AVAILABLE sau portable acquisition | Official source/pin/signature; no global install/PATH change |
| PostgreSQL/Docker | AVAILABLE |18.6/29.4.3; tạo database QA UUID mới, test dùng schema riêng |
| Git/GitHub | AVAILABLE | Native Windows Git fetch/PR metadata; branch QA riêng |
| Worktree cũ | PARTIAL read-only audit |22 metadata records;6 native readable+14 Linux fallback;1dirty là root5untracked. Hai detached record không audit được (1missing path,1timeout/prunable); giữ nguyên, không prune/repair |
| Host Windows thứ hai | MISSING | Người dùng xác nhận chỉ có máy hiện tại, yêu cầu runbook |
| Physical LAN/human hearing/owner signatures | NOT RUN | Không dùng nhiều JVM hoặc autoclick thay nghiệm thu người thật |
| Global toolchain install/firewall/service reset | NOT APPLICABLE | Không cần, không thực hiện |

Đầu phiên native Git hiểu sai đích /mnt/e thành đường dẫn E:/mnt/e khi tạo worktree mới. Đã dùng worktree move chỉ trên worktree QA vừa tạo; xóa các thư mục trung gian rỗng do thao tác đó tạo. Không reset/clean/rebase/force-push branch cũ, không sửa dirty source/worktree khác. Native/Linux pointer khác nhau được audit read-only; không nhận các record lỗi là clean.

Raw probes ở `jpackage-out/pr39-qa-20261016/environment-probe-01.log`, `environment-versions-final-01.log`, `tools/provenance.json`, `worktree-audit-private.json`; paths/PID/credentials giữ private. Server DB version được query lại04:44 UTC, không kế thừa tài liệu nguồn.

Sau resume07:12UTC: shared PostgreSQL container đã exited từ khoảng nghỉ. Không restart. Container PostgreSQL18.6 TEST mới từ cached image/dynamic loopback/tmpfs đã dùng cho installed extended rồi remove; startup socket-probe/Go-template wrapper FAIL giữ log, TCP readiness sau đó PASS. QA database ban đầu trong shared container còn chờ owner cleanup.
