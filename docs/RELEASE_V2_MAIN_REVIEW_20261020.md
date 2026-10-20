# Release review: đưa V2 (EX-03/05/07/06 + Proctor UI) vào `main` — 20/10/2026

**Trạng thái: `BLOCKED_FLYWAY_DECISION` / `READY_FOR_OWNER_REVIEW`.** Chưa merge vào `main` và chưa ghi human approval. Lý do: A/C chưa chốt thứ tự Flyway (§4), cả chuỗi PR chưa có review nào (§3), và chưa có bằng chứng Windows/GUI thật.

## 1. Ảnh chụp trạng thái (fetch lúc 20/10, ~01:20 UTC)

| Ref | SHA | Ghi chú |
|---|---|---|
| `main` | `e0ef252` → **`46869eb`** (sau khi review bắt đầu, #71/#72/#73 được merge) | Thêm BASE-03 (#70), MON-01 (#71), contract MON-07 (#72, chỉ docs), USB probe (#73, module `monitoring-spike` + script + 1 server smoke test). **Không đổi migration** (vẫn V1–V12), không đổi code production server/client |
| `feat/ex03-rich-importer-20261019` | `03d42e3` | Tách từ `3bdefe6`. PR #75 `mergeable_state=dirty`: xung đột `docs/KIEM_THU.md`, `docs/TIEN_DO.md` với `main` mới |
| `feat/ex05-authenticated-images-20261019` | `6c5065d` | Chứa EX-03. `6c5065d` chỉ là commit docs, nằm sau `05cc694` |
| `feat/ex07-rich-session-lifecycle-20261019` | `93c7019` | Tách từ EX-05 `05cc694`, **không** chứa `6c5065d`. Merge vào EX-05 sẽ xung đột 3 file nhật ký docs |
| `feat/ex06-javafx-rich-renderer-20261020` | `448f58e` | Chứa EX-07 |
| `integrate/ex06-proctor-20261020` | xem PR #76 | EX-06 + UI giám thị `1359dba` + panel V2 + 2 fix của phiên này (§5) |
| `stabilize/v2-rich-main-candidate-20261020` | xem §2 | **Ứng viên merge.** Là integrate + `6c5065d` + `main` `46869eb`, xung đột docs đã giải (giữ cả hai bên). Tên `stabilize/**` để workflow CI Windows chạy khi push |

Ancestry đã kiểm bằng `git merge-base --is-ancestor`, không suy từ tên nhánh. Trong chuỗi, code và migration đều merge sạch; mọi xung đột chỉ ở các file nhật ký/tiến độ dạng append.

## 2. Kiểm thử

### CI GitHub (`Maven tests (Java 21)`, `windows-2025`, Temurin 21.0.12, `mvn clean verify`, không bỏ qua lỗi)

| Head | Kết quả | Ghi chú |
|---|---|---|
| PR #76 `4b70f1d` | **success**: protocol 45/0; client 739/0 fail/23 skip; server 391/0 fail/89 skip; spike 1/0 | `ControlledProcessHarnessTest` **8/8 PASS trên Windows** |
| PR #75 `03d42e3` | **success**: client 652/0/1 skip; server 354/0/78 skip | |

CI **skip toàn bộ** test `*PostgresTest` (89 trên #76) và mọi test GUI/E2E. Vì vậy CI xanh **không** chứng minh phần PostgreSQL, JavaFX hay E2E.

### Local trên PostgreSQL 18.1 thật

- Môi trường: cloud Linux 6.18, OpenJDK 21.0.12.1, Maven 3.9.11, Xvfb, ALSA null.
- PostgreSQL 18.1 lấy từ binary chính thức trong `io.zonky.test.postgres:embedded-postgres-binaries-linux-amd64:18.1.0`, đã kiểm SHA-1 theo Maven Central. Cluster TEST riêng trên loopback, port 55433.
- Mọi lượt chạy với `TOEIC_PG_INTEGRATION=true`, `TOEIC_PROCTOR_GUI=true`, `TOEIC_RICH_GUI=true`, cùng hai biến E2E trỏ vào server ứng viên.

| Lượt | Lệnh | Kết quả |
|---|---|---|
| `d13b3ff` | `mvn -B -fae clean verify` | **exit 1**. Client 744/4 FAIL/5 skip: 3 harness (baseline) + `RichAttemptE2eGuiTest` (thiếu điều kiện tiên quyết, §5.1). Server **SKIPPED** do client fail |
| `246ca2b` | `mvn -B clean verify -Dmaven.test.failure.ignore=true` | exit 0 **chỉ do cờ**. Client 744/4 FAIL: 3 harness + `ProctorV2E2eGuiTest` (regression, §5.2). Server 443/1 FAIL (LT04, baseline)/3 skip. Spike 1/0 |
| `56ea3e2` (sau fix) | `RichAttemptE2eGuiTest` + `ProctorV2E2eGuiTest` chạy cùng JVM, đúng thứ tự như suite, server PG18 | **PASS 5/5 lượt** |
| `f9901fd` (ứng viên cuối) | `mvn -B clean verify -Dmaven.test.failure.ignore=true` | exit 0 **chỉ do cờ**. Protocol 45/0. Client 745/**3 FAIL** (chỉ harness baseline)/5 skip. Server 443/**1 FAIL** (LT04 baseline)/3 skip. Spike 1/0. PASS: `ProctorV2E2eGuiTest`, `RichAttemptE2eGuiTest`, `RichExamImportPostgresTest` 17/0, `RichSessionLifecyclePostgresTest` 8/0, `RichImagePostgresTest` 3/0/1 skip, `RichExamMigrationPostgresTest` 43/0, `ExamRuntimePostgresTest` 38/0 (legacy V1), `AudioRuntimePostgresTest` 10/0, `ListeningRuntimePostgresTest` 18/1 FAIL (LT04)/2 skip |
| `f9901fd` CI | GitHub Actions run 39354563109 (`windows-2025`) | **success** (không có cờ bỏ qua; PG/GUI bị skip trên CI) |
| `f1abb66` (+ `main` `46869eb`) | `mvn -B clean verify -Dmaven.test.failure.ignore=true`, server PG18 dựng từ jar của chính commit | exit 0 **chỉ do cờ**. Protocol 45/0. Client 745/4 FAIL + 1 ERROR: harness 4 (baseline) + `ProctorRedesignGuiTest.alertDrawer…` (flaky, §5.5). Server 443/1 FAIL (LT04). Spike 35/**5 FAIL** `CimSnapshotSourceTest` (§5.6). Cả hai E2E và mọi `*PostgresTest` rich/V1 PASS |
| `f1abb66` CI | run 39355080010 (`windows-2025`) | **success**, gồm `CimSnapshotSourceTest` trên Windows |
| `e95e691` / `78f273b` | `ProctorRedesignGuiTest` chạy riêng sau fix chờ node | 12/12, PASS 5/5 lượt |

Kết quả `mvn clean verify` exit 0 khi không có cờ bỏ qua lỗi **chưa đạt được trên Linux** vì 3 lỗi baseline của harness (§5.3). Không được coi lượt exit 0 có `-Dmaven.test.failure.ignore=true` là tương đương.

**NOT RUN:** `WINDOWS_NOT_RUN` cho PG18 + GUI thật + E2E (CI Windows không chạy PG/GUI); LAN với hai máy; nghe bằng loa thật.

## 3. Chuỗi PR (stacked, base là dependency trực tiếp)

| Thứ tự | PR | Head → base | Migration | Merge vào base | Review |
|---|---|---|---|---|---|
| 1 | [#75](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/75) | EX-03 → `main` | V14 | **dirty** (2 file docs) | chưa có |
| 2 | [#77](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/77) (draft) | EX-05 → EX-03 | — | sạch | chưa có |
| 3 | [#78](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/78) (draft) | EX-07 → EX-05 | V15 | xung đột 3 file docs | chưa có |
| 4 | [#79](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/79) (draft) | EX-06 → EX-07 | — | sạch | chưa có |
| 5 | [#76](https://github.com/AnhTaizz/Toeic-Monitoring-Realtime/pull/76) (draft) | integrate → EX-06 | — | sạch | chưa có |

PR liên quan nhưng **không** thuộc chuỗi:
- #74 MON-07 (draft, base là #72): V13.
- #72 contract MON-07 (draft).
- #71 MON-01 (open, base `main`).
- #73 USB spike (draft).

**Cách giải xung đột docs** đã làm sẵn và kiểm trên `stabilize/…`: commit `d2846cd` (EX-05 docs) và commit merge `main`. Owner có hai lựa chọn:
- (a) Mỗi owner merge base vào nhánh của mình, giữ cả hai mục nhật ký, rồi push. Không rebase, không force push.
- (b) Merge lần lượt #75 → #77 → #78 → #79 → #76, mỗi bước giải đúng các xung đột docs ấy.

Squash hay merge commit do owner chọn. Khuyến nghị merge commit để giữ SHA đã có bằng chứng.

**Checklist cho mỗi bước merge** (chỉ làm sau quyết định Flyway và approval của người):
1. Kiểm ancestry; danh sách file đổi; migration của bước.
2. CI xanh trên đúng HEAD.
3. Có approval từ reviewer đúng vai (C xem A, A xem B, B xem C).
4. Không xoá test; không đổi contract V1/Listening; không lộ đáp án trước START; không nới auth (đã soát toàn chuỗi so với `main`: 0 test bị xoá, `BearerAuthenticationFilter` chỉ thêm attribute token hash).
5. Sau merge: kiểm lại `main` HEAD, danh sách Flyway và CI trên `main` trước khi sang bước tiếp.

## 4. Quyết định Flyway V13/V14/V15 — chờ A/C (`BLOCKED_FLYWAY_DECISION`)

Bằng chứng: 6 kịch bản chạy bằng jar thật trên PostgreSQL 18.1, mỗi kịch bản một DB TEST riêng ([`flyway-scenarios-pg18.txt`](../evidence/release-v2-20261020/flyway-scenarios-pg18.txt)).
- **Jar:**
  - `main` `e0ef252`: sha256 `43a604f4…`.
  - Ứng viên `d13b3ff`: sha256 `c676a8b3…`; schema giống `f9901fd`, vì sau đó chỉ đổi client và docs.
- **V13 của MON-07** lấy nguyên văn từ `feat/v2-c-mon07-signal-pipeline` (sha256 `3f795a49…`). Chỉ nạp qua `SPRING_FLYWAY_LOCATIONS` trên DB TEST, **không** sửa migration nào trong repo.

| # | DB ban đầu | Chạy | Kết quả |
|---|---|---|---|
| 1 | trống | ứng viên | STARTED, history 1–12, 14, 15 |
| 2 | V1–V12 (jar `main`) | ứng viên | STARTED, áp V14, V15 |
| 3 | V1–12, 14, 15 | ứng viên + V13 | **EXITED**: "Detected resolved migration not applied to database: 13" |
| 4 | V1–12, 14, 15 | ứng viên + MON-07 đổi thành **V16** | STARTED, history …, 15, 16 |
| 5a/5b/5c | `main` + V13 (V1–13) | ứng viên không V13 / có V13 | 5b **EXITED** ("applied migration not resolved locally: 13"); 5c STARTED |
| 6 | trống | ứng viên + V13 | STARTED, 1–15 |

**Phương án:**

- **A (khuyến nghị): merge chuỗi EX với V14/V15 trước; MON-07 đổi V13 → V16 trong PR #74 trước khi PR đó merge.**
  - DB mới, DB V1–V12 và DB dev đã có V14/V15 đều chạy (kịch bản 1, 2, 4).
  - Rủi ro: DB nào đã áp **V13 của MON-07** sẽ không chạy được bản `main` mới (5b), và lịch sử cũng không chuyển được sang V16. DB đó phải reset bằng `scripts/reset-db.ps1 -Force`. Chỉ làm vậy khi dữ liệu là MOCK/TEST; C phải xác nhận không có DB cần giữ đã áp V13.
  - Lỗ hổng khi V13 vẫn còn số cũ: nếu #74 merge mà chưa đổi số, mọi DB đã có V15 sẽ chết (kịch bản 3). Cần ghi điều kiện "đổi V16 trước merge" vào checklist review của #74.
- **B: merge MON-07 (V13) trước chuỗi EX.**
  - Chỉ hợp lệ khi #74 (đang draft, base là #72) đã đủ review và release.
  - Mọi DB dev đã áp V14/V15 phải reset (kịch bản 3).
  - Kéo MON-07 chưa xong vào `main` chỉ để lấp số V13 là trái yêu cầu.
- **Không dùng:** `outOfOrder`, V13 rỗng, `flyway repair`, sửa checksum, renumber V14/V15 (đã nằm trên 3 nhánh và nhiều DB dev).

**Cần owner xác minh để quyết nhanh:**
1. C: có DB nào cần giữ đã áp V13 không? Lệnh kiểm: `SELECT version FROM flyway_schema_history`.
2. A: DB dev/Windows của nhóm đang ở version nào?
3. C chấp thuận đổi số trong #74 (chỉ đổi tên file và tham chiếu trong test/docs; nội dung SQL giữ nguyên).
4. Kịch bản 4 mới kiểm schema; chưa chạy code MON-07 trên schema V16.

## 5. Lỗi hiện tại và phân loại

| Lỗi | Phân loại | Bằng chứng | Hướng xử lý |
|---|---|---|---|
| §5.1 `RichAttemptE2eGuiTest`: 409 khi tạo ca trên DB mới | **Test precondition (phụ thuộc thứ tự)**, không phải lỗi sản phẩm | Fail khi chạy trước `ProctorV2E2eGuiTest` trên DB chưa có đề | **Đã sửa** `3f5a114`: test tự import idempotent. DB PG18 trống → import 201 → PASS |
| §5.2 `ProctorV2E2eGuiTest`: UI không hiện ca mới (1/1 lượt suite PG18 tại `246ca2b`) | **REGRESSION của tích hợp** (panel V2 + wiring redesign) | stderr: `Not on FX application thread` tại `TopBar.update`/`OverviewPane.update`, gọi từ `ProctorV2Controller` → `refreshAll` trên worker `toeic-proctor-v2` | **Đã sửa** `56ea3e2`: `refreshScope` chuyển sang FX thread, controller theo dõi ca trước khi refresh. Test hồi quy FAIL khi chưa có fix, PASS khi có. E2E PASS 5/5. Tab V1 legacy dùng cùng đường gọi nên cũng được sửa |
| §5.3 `ControlledProcessHarnessTest` (3 FAIL, có lượt 4) | **BASELINE_FAILURE + ENVIRONMENT_DEPENDENT (Linux)** | `main` `e0ef252` Linux: 3 FAIL, 2/2 lượt. CI Windows 8/8 PASS. Dưới subreaper tự viết (reap tiến trình mồ côi): `knownOwnedDescendant…` PASS, còn 2 FAIL ở `resourcesCleaned()` cho NO_READY/HANG | PID 1 của container (`process_api`) không reap tiến trình cháu, giải thích được 1/3. 2 lỗi còn lại **chưa rõ nguyên nhân**. Chuyển C (owner harness) điều tra trên Linux. Không sửa, không disable |
| §5.4 `ListeningRuntimePostgresTest.proctorControlsLt04RealGui…` | **BASELINE_FAILURE trên môi trường này** | FAIL giống hệt trên `main` `3bdefe6`, EX-06 `448f58e` và nhánh tích hợp (Linux/Xvfb/ALSA null, chờ READY quá 35 s). Test chỉ chạy khi `TOEIC_PROCTOR_GUI=true`; CI không bật. Nhật ký cũ ghi từng PASS trên Windows | Cần B/C chạy lại trên Windows. Chưa rõ nguyên nhân trên Linux |
| §5.5 `ProctorRedesignGuiTest.settingsApply…` và `alertDrawerOpens…` | **FLAKY (thiết kế test)** | Lookup null ngay sau `show()`: node chỉ có sau lượt layout tiếp theo (cell của TableView, trang settings). Mỗi test fail 1 lần ở một lượt suite (PG16 và PG18) | **Đã sửa ở test** `e95e691`: chờ node rồi mới bấm. Không đổi code sản phẩm. Chạy riêng PASS 5/5. Tính flaky chỉ được loại trừ hẳn khi các lượt suite sau tiếp tục xanh |
| §5.6 `CimSnapshotSourceTest` (5 FAIL, module `monitoring-spike`, đến từ `main` #73) | **BASELINE_FAILURE + ENVIRONMENT_DEPENDENT (Linux)** | `main` `46869eb` trên Linux: 5 FAIL y hệt. CI Windows success | Test chạy tiến trình thật của Windows/CIM. Không thuộc chuỗi EX; C (owner MON-02) quyết có cần chặn chạy trên non-Windows không. Không sửa |

## 6. Việc còn thiếu trước khi merge `main`

1. A/C chốt phương án Flyway (§4).
2. Review và approval của người cho #75, #77, #78, #79, #76.
3. Giải xung đột docs của #75 và #78 (§3).
4. Chạy runbook Windows: PG18 + GUI thật, thí sinh và giám thị (`docs/INTEGRATION_EX06_PROCTOR_20261020.md` §5).
5. Sau khi merge: acceptance trên `main` thật (Phase D), rồi mới bàn giao cho Agent 4 (`docs/EX01_DATA_PIPELINE_HANDOFF.md`, hiện là `NOT_READY_FOR_AGENT4`).
