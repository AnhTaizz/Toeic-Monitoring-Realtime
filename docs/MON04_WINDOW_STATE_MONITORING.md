# MON-04 — quan sát minimize/fullscreen cửa sổ thi

## Thiết kế runtime

MON-04 dùng chung `CandidateExamSignalMonitoring`, `SignalRunContext`, `SignalDelivery`,
capability và scoped heartbeat với MON-03. Nhánh triển khai được xếp chồng trên
MON-03 để mỗi attempt chỉ có một observer run/sequence/queue. Runtime vẫn mặc định
OFF và chỉ bật khi cả server/client có:

```text
-Dtoeic.monitoring.signal-experiment-enabled=true
```

`FxWindowMonitor` chỉ đọc `iconifiedProperty`, `fullScreenProperty` và
`showingProperty` của Stage thi. Nó không đọc cửa sổ khác, không gọi
`requestFocus`, không tự chuyển fullscreen/minimize và không khóa hay hủy bài.

Run phát một baseline `INITIAL`. Trạng thái non-fullscreen ban đầu là
`fullscreen=false, reason=INITIAL`, không phải sự kiện thoát fullscreen. Sau baseline,
mỗi trạng thái kết hợp mới phát `CHANGED`; callback trùng bị bỏ. Nhiều thay đổi
property trong cùng JavaFX pulse được gộp thành một snapshot hiện tại, tránh
event trung gian do window manager. Khi Stage không còn showing hoặc candidate lifecycle đóng,
listener bị gỡ và callback đã defer không được phát.

## Kiểm tra tự động

```powershell
mvn --% -pl client -am -Dtest=FxWindowMonitorTest,WindowSignalAdapterTest,FxFocusMonitorTest,FocusSignalAdapterTest,SignalRunContextTest,MonitoringDeliveryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

## Checklist desktop thủ công

1. Bật signal experiment ở server và candidate client; vào một attempt ACTIVE.
2. Khi Stage không fullscreen, kiểm baseline `INITIAL,false,false`; không gọi đó là
   fullscreen-exit.
3. Minimize và restore Stage bằng window controls. Kiểm hai `CHANGED` theo thứ tự,
   không duplicate và UI không tự giành focus.
4. Chỉ khi bản app/harness được phép thực sự gọi `Stage.setFullScreen(true)`, thoát
   fullscreen và kiểm transition `true → false`. Maximize không phải fullscreen và không
   được dùng thay evidence này.
5. Rời/đóng Stage, thao tác lại và xác nhận history không có event mới.
6. Ghi SHA, Windows/JDK, attempt ID và observed/received sequence; không ghi token/mật khẩu.

Kết quả desktop phải ghi ACTUAL hoặc NOT RUN. Property test MOCK không thay manual GUI.
