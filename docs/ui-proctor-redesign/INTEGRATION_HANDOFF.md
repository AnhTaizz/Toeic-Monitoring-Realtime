# Proctor redesign — hướng dẫn ghép vào ứng dụng (cho người tích hợp)

Nhánh `feat/ui-proctor-redesign-20261020`, base EX-07 `93c7019`. **Chưa ghép vào launcher.** Agent 2 không sửa `ToeicClientApplication.java` vì EX-06 (Agent 1) đang thay đổi file này. Chỉ ghép sau khi EX-06 ổn định và đã merge.

## 1. Constructor / factory

```java
// vn.edu.toeic.client.proctor.redesign.ProctorRedesignView (public, BorderPane + AutoCloseable)
public static ProctorRedesignView forLogin(String serverUrl, LoginResponse login, RealtimeClient transport,
        Runnable onLogout, Runnable onExpired)
```

Chữ ký giống hệt `new ProctorDashboardView(serverUrl, response, newClient, onLogout, onExpired)`. Factory:

- Ném `IllegalArgumentException` nếu role khác `PROCTOR`.
- Tự tạo 2 `MonitoringApiClient` (một cho `DashboardController`, một cho `RoomFeed`), 1 `ExamApiClient` (progress), `ProctorSessionView` (tạo khi mở mục "Điều khiển ca" lần đầu) và `ProctorSettings.FileStore.standard()`.
- **Không** tạo WebSocket mới. Dùng `RealtimeClient` truyền vào; gọi `transport.updateScope(...)` qua `DashboardController` như dashboard cũ.

## 2. Vị trí thay thế trong `ToeicClientApplication`

Bản base EX-07 và bản EX-06 hiện tại (`448f58e`) có cùng ba vị trí:

```diff
-import vn.edu.toeic.client.dashboard.ProctorDashboardView;
+import vn.edu.toeic.client.proctor.redesign.ProctorRedesignView;
 ...
-    private ProctorDashboardView dashboard;
+    private ProctorRedesignView dashboard;
 ...
         if ("PROCTOR".equals(response.user().role())) {
             long currentView = ++viewGeneration;
-            dashboard = new ProctorDashboardView(serverUrl, response, newClient,
+            dashboard = ProctorRedesignView.forLogin(serverUrl, response, newClient,
                     () -> leaveDashboard(stage, currentView, ""),
                     () -> leaveDashboard(stage, currentView, "Phiên hết hiệu lực. Hãy đăng nhập lại."));
-            Scene proctorScene = applyStyles(new Scene(dashboard, 1140, 780));
+            Scene proctorScene = applyStyles(new Scene(dashboard, 1366, 768));
             stage.setScene(proctorScene);
             newClient.connect(context);
             return;
         }
```

Đoạn `if (dashboard != null) { dashboard.close(); dashboard = null; }` (base dòng 535, EX-06 dòng 557) **giữ nguyên**. `close()` idempotent, dừng poller/timer/controller/listener của view, không đóng `RealtimeClient` (app vẫn tự đóng như cũ).

Nếu muốn giữ dashboard cũ làm phương án dự phòng, có thể chọn theo system property (ví dụ `toeic.proctor.legacyDashboard=true`). Việc này thuộc quyết định của người tích hợp, Agent 2 không tự thêm.

## 3. CSS

Không cần thêm gì vào Scene. View tự gắn `/proctor-redesign/proctor-redesign.css` ở **Parent**, nên stylesheet chỉ áp dụng cho cây con của nó và có độ ưu tiên cao hơn `styles.css` ở cấp Scene. Mọi selector đều bắt đầu bằng `.proctor-redesign` (có test kiểm tra). `applyStyles(...)` vẫn có thể giữ: `styles.css` không làm vỡ giao diện mới, còn view mới không ảnh hưởng màn thí sinh.

## 4. LoginResponse, RealtimeClient, callbacks

| Tham số | Dùng để |
|---|---|
| `serverUrl` | Base URL cho các client HTTP (v1 monitoring, progress, B4) |
| `login.token()` | Header `Authorization: Bearer` (không đưa vào URL hay log) |
| `login.user().displayName()` | Tên ở topbar |
| `login.attemptScope()` | Scope ban đầu của `DashboardController`; server kiểm lại bằng `/auth/me` |
| `transport` | Socket đang dùng: trạng thái kết nối, push `MONITOR_PRESENCE/WARNING/STATE`, `updateScope` |
| `onLogout` | Menu tài khoản → "Đăng xuất" |
| `onExpired` | Gọi **một lần** khi 401, role sai, hoặc `UNAUTHORIZED` qua WS |

## 5. Phụ thuộc vào Agent 1 / class đang thay đổi

- `ToeicClientApplication.java` (EX-06 đang sửa): chỉ cần 3 thay đổi ở mục 2.
- Không đụng `CandidateExamView`, `ExamSession`, `ExamApiClient` (chỉ gọi `getProctorProgress`), audio/Listening, `styles.css`, `toeic-room.css`.
- Class dashboard cũ (`ProctorDashboardView`, `DashboardController`, `DashboardModel`, `ProctorProgressView`, `ProctorSessionView/Controller`) giữ nguyên. Chỉ dùng public API và `ProctorDashboardView.deliveryLabel`.

## 6. Chức năng chưa có backend API (UI hiển thị rõ, không giả)

| Chức năng mockup | Trạng thái | Phụ thuộc |
|---|---|---|
| Đánh dấu đã xử lý, ghi chú xử lý | Disabled kèm giải thích | MON-08/MON-09: cần endpoint lưu note/status có transaction + phân quyền |
| Phần thi/câu đang làm (Listening · Part 3) | "Chưa có API" | API tiến độ theo section/part |
| Tiến trình ca theo Listening/Reading | Chỉ có tổng đáp án đã commit | Như trên |
| Cảnh báo focus, USB, fullscreen, display | Không hiển thị | MON-02–MON-09 chưa tích hợp production |
| Audit thao tác giám thị trong nhật ký | Không hiển thị | Chưa có log thao tác |
| Đồng bộ đồng hồ | "Chưa đo độ lệch" | Không có phép đo |
| Danh sách ca/tên đề | Topbar dùng `sessionId` từ progress | Chưa có API liệt kê ca cho giám thị |
| Âm báo | Switch disabled | Chưa có đường phát âm an toàn đã review |

## 7. Preview độc lập (không đụng launcher)

```bash
# GUI test thật (JavaFX) với nguồn MOCK
TOEIC_PROCTOR_GUI=true xvfb-run -a mvn -pl client test -Dtest='ProctorRedesign*Test'
# Ảnh chụp 7 màn ở 1600×900, 1440×900, 1366×768 → client/target/proctor-redesign-screens/
TOEIC_PROCTOR_SCREENSHOTS=true xvfb-run -a mvn -pl client test -Dtest=ProctorRedesignScreenshots \
  -DargLine=-Duser.timezone=Asia/Ho_Chi_Minh
```

Trên Windows bỏ `xvfb-run`. Fixture nằm ở `src/test` và mọi tên/ID đều có tiền tố `MOCK`. Không có dữ liệu giả trong code production.
