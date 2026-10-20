# Proctor redesign — kết quả kiểm thử (Agent 2)

Số liệu dưới đây là kết quả chạy thật ngày 19/10/2026 (UTC) trong cloud container. **Chưa** chạy trên Windows, LAN, server thật hay PostgreSQL thật cho phần UI này. Không cập nhật `docs/KIEM_THU.md` hay tracker dùng chung; người tích hợp chép lại sau khi review.

## Môi trường

- Linux 6.18 x86_64, OpenJDK 21.0.12, Maven 3.9.11, JavaFX 21.0.12 (theo POM).
- Màn hình ảo Xvfb `1920x1080x24`, font hệ thống Inter/DejaVu (Windows dùng Segoe UI nên chữ sẽ hẹp hơn ảnh chụp).
- `LANG=C.UTF-8`. Không đặt biến này thì 4 test có sẵn về đường dẫn Unicode lỗi `InvalidPath` do locale container, không liên quan UI.
- Mọi dữ liệu trong test và ảnh là **MOCK** (`RedesignFixtures`). Không có server, socket hay DB thật.

## Kết quả

| Bộ test | Lệnh | Kết quả |
|---|---|---|
| `RoomModelTest` (thuần) | `mvn -pl client test -Dtest=RoomModelTest` | **8/8 PASS** |
| `RoomFeedTest` (thuần, executor inline) | `-Dtest=RoomFeedTest` | **8/8 PASS** |
| `ProctorSettingsTest` (file thật trong `@TempDir`) | `-Dtest=ProctorSettingsTest` | **4/4 PASS** |
| `ProctorRedesignGuiTest` (JavaFX thật, Xvfb) | `TOEIC_PROCTOR_GUI=true xvfb-run -a mvn -pl client test -Dtest=ProctorRedesignGuiTest` | **12/12 PASS**. Bộ 11 test của CP4 chạy 3 lần liên tiếp, PASS cả 3 |
| `ProctorRedesignScreenshots` | `TOEIC_PROCTOR_SCREENSHOTS=true xvfb-run ...` | **1/1 PASS**, sinh 21 PNG |
| Regression `protocol` + `client` | `LANG=C.UTF-8 mvn -pl protocol,client -am test` | protocol **45/0/0**. client **682 run, 3 fail, 0 error, 16 skip** |

Ba lỗi client đều ở `ControlledProcessHarnessTest`, là harness tiến trình con thiết kế cho Windows. Đã chạy riêng test này 3 lần trên **base `93c7019`** (worktree tạm) và 3 lần trên nhánh này: base lỗi 3/3/3, nhánh lỗi 2/4/2. Lỗi dao động theo timing và có sẵn trên base (EX-07 doc cũng ghi 3 lỗi này). Không sửa và không skip test này. 16 skip gồm 10 method GUI của Agent 2 (tắt khi không có `TOEIC_PROCTOR_GUI=true`) và 6 test có sẵn (`AudioFilesTest` 4, `AudioFilesSharingTest` 1, `ScoreResultDialogTest` 1). `ProctorRedesignScreenshots` không khớp pattern include mặc định của surefire, nên chỉ chạy khi gọi `-Dtest=` tường minh.

`mvn` toàn repo (gồm server cần PostgreSQL): **NOT RUN** trong phiên này. Agent 2 không đổi server.

## Bài test bắt buộc → test tương ứng

| # | Yêu cầu | Test |
|---|---|---|
| 1 | Sidebar điều hướng 5 mục | `sidebarNavigatesThroughAllFiveSectionsAndSessionControls` |
| 2 | Bảng render đúng snapshot | `tableRowsComeFromSnapshotsInServerOrder`, `overviewAndCandidateTablesRender...` |
| 3 | Search/filter/sort giữ thứ tự server, không nhầm thí sinh | `searchFilterAndSortNeverMixCandidates...`, GUI search |
| 4 | Drawer thí sinh mở/đóng đúng record | `candidateDrawerOpensTheRightRecordAndClosingKeepsMonitoring` |
| 5 | Drawer cảnh báo mở/đóng đúng event | `alertDrawerOpensTheRightEventAndHasNoFakeAcknowledgement` |
| 6 | ONLINE / OFFLINE / UNKNOWN / stale | `distinguishesOnlineOfflineUnknownStaleAndNotMonitored` |
| 7 | Tiến độ = số đáp án đã commit | `progressIsTheCommittedSavedAnswerCountOverTheWholeExam` |
| 8 | Reconnect không nhân đôi event | `reconnectAndRepeatedPushesNeverDuplicateTimelineEvents` |
| 9 | Không có ACK giả khi thiếu API xử lý | `alertDrawer...NoFakeAcknowledgement`, `alertsUseOnlyBackendStates...` |
| 10 | Wrong-role / foreign-attempt không lộ dữ liệu | `wrongRoleExpiresWithoutShowingData`, `foreignAttemptsAreNeitherOpenedNorShown`, `pushesForAttemptsOutsideTheRosterAreIgnored`, `forbiddenAttemptIsDropped...` |
| 11 | Settings lưu/khôi phục đúng phạm vi | `settingsApplyInSessionSaveOnlyWhenWritten...`, `ProctorSettingsTest` |
| 12 | Close/logout dọn subscription/worker | `closeAndLogoutReleaseListenersWorkersAndApis`, `closeRemovesListenerAndClosesApi` |
| 13 | CSS không ảnh hưởng Candidate UI | `stylesheetIsScopedAndDoesNotRestyleCandidateUi` |
| 14 | Responsive 1366×768, 1440×900, 1600×900 | `layoutFitsTargetSizesWithoutHorizontalOverflow` (3 kích thước) |

## Ảnh chụp

`docs/ui-proctor-redesign/screens/MOCK_*.png`: 7 màn ở 1600×900, cùng Overview và Candidate Detail ở 1366×768 và Candidate List ở 1440×900. Ảnh render bằng JavaFX thật trên Xvfb với dữ liệu MOCK, không phải ảnh mockup. Chưa có ảnh chụp trên Windows (**GUI Windows: NOT RUN**).

So với mockup:
- Ở 1366 và 1440, Overview chuyển hai thẻ bên phải xuống dưới bảng để bảng không bị cắt. Từ khoảng 1240 px vùng nội dung trở lên thì hiển thị hai cột như mockup.
- Logo là ô chữ "T" màu cam, không dùng mascot cáo của mockup (không có asset được cấp phép).
- Các nhãn "Chưa xử lý/Đã xử lý" của mockup được thay bằng trạng thái backend thật (xem `AUDIT.md`).
