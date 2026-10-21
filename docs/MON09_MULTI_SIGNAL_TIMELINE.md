# MON-09 — Timeline đa tín hiệu trong dashboard hiện có

Slice C+B ngày 21/10/2026. Người dùng ủy quyền implementation, kiểm thử, commit/push/PR và merge commit khi CI/quy định cho phép; đây không phải chữ ký hoặc approval của A/B/C. FULL mặc định, experiment OFF, không đánh MON-09 DONE. [Báo cáo và source receipt](../evidence/cb-mon09-timeline-20261021/REPORT.md).

## Chạy một lệnh trên Windows

Cần JDK 21 (`JAVA_HOME`), Maven trên PATH, PostgreSQL 18 binaries (mặc định `C:\Program Files\PostgreSQL\18\bin`), desktop JavaFX. Chạy tại checkout chứa slice này:

```powershell
.\scripts\demo-mon09-timeline.ps1 -Mode Mock
# Chỉ khi đồng ý quan sát CIM tối thiểu, không đọc file/serial:
.\scripts\demo-mon09-timeline.ps1 -Mode Real -ReadUsbStorage
```

Có thể truyền `-PostgresBin`, `-JavaHome`, `-ViewSeconds 60` (0–120) hoặc thư mục output mới dưới `target`. Runner tự dựng cluster/schema TEST riêng, seed MOCK accounts, tạo session/assignment qua API, bật lane chỉ cho lượt TEST, mở dashboard JavaFX tại mục **Timeline đa tín hiệu** khoảng 20 giây rồi teardown. Không cần đăng nhập tay, `.env`, server hay DB dev. Mock có hai attempts; có thể xem chọn lượt/nguồn trong khoảng chờ, fixture tự khôi phục selection để kiểm oracle. REAL chỉ có status nếu máy không quan sát thấy USB. Lần đầu Maven/build mất thời gian trước khi cửa sổ xuất hiện. Output tồn tại thì runner từ chối ghi đè; log/PG data/screenshots chưa rà ở `target` ignored. Không dùng root `clean` để giữ evidence.

Runner tái sử dụng `demo-usb-integrated.ps1`, không dựng provisioning thứ hai. Cluster password ngẫu nhiên qua stdin, SCRAM trước listen; teardown chỉ schema/cluster do fixture sở hữu. Dừng sớm/Ctrl+C trong slice GUI chưa có nghiệm thu riêng; chạy hữu hạn và xem receipt cleanup trước khi gọi thành công. Đây là demo TEST qua controls/Scene, không phải chứng nhận thao tác OS/hardware.

## Nguồn và luồng đang có

- Candidate: `CandidateExamSignalMonitoring.start` opt-in nối **các implementation đã có** `FxFocusMonitor`, `FxWindowMonitor`, `FxDisplayMonitor`, `FxActivityMonitor`. `SignalRunContext` dùng chính collector lease ID, một sequence và `SignalDelivery` chung; `RealtimeClient` giữ quyền/capability/lease, WS thật → transaction server → COMMIT → ACK. Không có observer mới, socket mới hoặc production default mới.
- USB: `CimUsbScanSource` chỉ ở **server test classpath**, gọi provider CIM có sẵn → `UsbScanCoordinator` → adapter/status → cùng delivery/WS. Không đưa bridge/provider/resource vào client/server production JAR, không đọc file/serial. UNKNOWN/error không tạo REMOVED giả; weak slot identity không phải serial.
- Consumer: `MonitoringApiClient.signals` → `SignalHistory.parse` (reuse `Signal.parse`) → `TimelineController` → `TimelineRows` → `MultiSignalTimelinePane` trong `ProctorRedesignView`. Typed live dùng **HTTP polling**, không phải WS push. Process/events/gaps/presence từ API/dashboard hiện hữu; process state hiển thị riêng, UNSYNCED chưa snapshot không bị diễn giải thành 0 process.
- Listening: reuse endpoint/status/parser/phân quyền hiện hữu. Panel hiển thị phase/interrupted; chỉ tạo dòng projection khi `interrupted=true` và có `interruptedAt`. Không tạo wire type hay lịch sử phát mới. REAL TEST hiện chỉ đọc NOT_STARTED; dòng interruption được kiểm bằng oracle MOCK, chưa có playback/interruption E2E trong slice.

## Giới hạn, thứ tự và vòng đời

Typed history dùng fixed-through, trang 100, tối đa 3 trang/mỗi cycle; khi còn backlog giữ watermark qua các cycle, đọc hết mới mở watermark mới. Poll 3 giây khi rảnh, toàn cycle timeout 12 giây, một cycle in flight, hủy requests khi đổi attempt/roster/logout/disconnect/close. 500 typed rows cache và tối đa 500 dòng gộp hiển thị; banner cho biết còn trang/đã giới hạn. Đây là cửa sổ hữu hạn, không phải toàn bộ lịch sử. Typed/Listening body ≤512 KiB; endpoint legacy giữ bound cũ 5000 rows/8 MiB; process panel tối đa 50 tên. Không tăng wire limit hoặc response bound cũ.

Sort deterministic: server received/recorded time giảm dần → source → **server timelineSequence** giảm dần (chỉ typed) → identity. Dedupe namespaced theo source/attempt/event identity; process/signal cùng ID không gộp; payload projection xung đột từ chối cả update. Không suy nhân quả toàn mạng hay trừ clock hai máy. Presence dùng timeoutDetectedAt/recoveredAt; Listening dùng interruptedAt và được ghi rõ là giờ trạng thái server, không phải packet receivedAt; thiếu observedAt để trống.

Mỗi cycle đọc lại auth/me + roster; assigned PROCTOR và ACTIVE-only giữ nguyên server. 401/403/connection FAILED xóa rows/state/cache; 401 gọi expired callback. Reconnect xác minh scope rồi catch-up; response cũ được generation/attempt/revision guard. Token chỉ header. HTTP/encode ảnh không chạy FX thread, render qua FX. View sở hữu HTTP API + scheduler/listener và đóng chúng, **mượn** realtime transport nên không đóng transport chung. Proctor không khởi tạo candidate collector/CIM. Hidden timeline ngừng selection; close vô hiệu callback và dọn worker.

UNKNOWN observer, UNKNOWN presence, dashboard stale, gap và READY lịch sử là các nghĩa khác nhau. Bộ lọc vẫn giữ số gap/unavailable bên ngoài bảng. Không có event không chứng minh không vi phạm; không gán tội, không đề nghị hủy bài.

## Phạm vi còn mở

USB vật lý 0/12 NOT_RUN, thay đổi màn hình vật lý, desktop OS mouse/keyboard và LAN hai máy NOT_RUN. Lifecycle Stage/controls tự động không thay human acceptance. Chưa phê duyệt offline60s/retention30ngày, ADR/policy/hardware/gates và human owner review vẫn pending. Không terminal-history auth expansion, MON-08, E1/E2 hoặc backlog status/hours changes. Báo cáo/PR phân biệt source runtime, build và source ảnh cũ; artifact/log raw chỉ ở ổ local ignored.
