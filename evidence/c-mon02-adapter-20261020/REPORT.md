# MON-02 — USB snapshot adapter experiment, 20/10/2026

**USER_AUTHORIZED_EXPERIMENT; USB physical0/12 NOT_RUN; MON-02 NOT_DONE.**
Nguồn ủy quyền: attachment f307aeae, cho phép code/test/docs/push/PR và merge commit khi
review kỹ thuật/CI/rules đạt, giữ nhánh; không owner approval hoặc thu thập production.
[Semantics trước code](../../docs/MON02_USB_ADAPTER_EXPERIMENT.md), [receipt](receipt.json).

## Source, module và luồng dữ liệu

- Base main `e189b611419a041900276e2ee43b621ed540635f`; nhánh/worktree riêng
  `feat/c-mon02-usb-adapter-20261020`. Main/open PR kiểm thực tế, chưa adapter trùng.
  Không reset/pull checkout main gốc hoặc xóa/ghi đè nhánh/worktree có sẵn.
- Runtime/312focused **`aac75284270033113e5390c5e68103731ab3c450`**; test bổ sung ref
  rotation **`3ff9aaaf486e0930b520a620a25be82e5d29cb50`**,104client PASS/0SKIP, không đổi runtime/JAR. Source đầu
  `693cf4431423744495b7b691fd26cd040115d43d` có310PASS trước ownership fix; không dùng
  kết quả đó thay bản cuối. Publication HEAD/CI/PR/merge SHA ghi tại PR để tránh tự tham chiếu.
- Client: `UsbSignalAdapter.start/accept/stop/status`, `SignalRunContext.reserve/next/
  claimUsbInput`, thêm `SignalDelivery.matchesScope` (identity only, không auth).
- `monitoring-spike` vẫn CLI độc lập/không compile dependency. Server chỉ thêm **test
  scope dependency** để `MockUsbSnapshotProjection` dùng model `UsbSnapshot` và enum
  classification thật với dữ liệu MOCK. Không copy CIM/parser/resource/CLI hoặc provider;
  không dependency từ client tới spike, không scanner thứ hai/native library mới.
- MOCK probe-model snapshot → projection TEST → immutable adapter input → stateful diff
  → UsbStorageSignal/SignalMessage → queue/socket chung → server COMMIT → ACK → history.
  Không sửa protocol, DB migration/server main, RealtimeClient, process/FULL/DELTA/presence
  hoặc Listening. Không candidate production binding/new socket/queue/retry/type/push/rules.

## Semantics và ownership

READY đầu tiên xác nhận baseline. Rỗng chỉ tạo baseline nội bộ, không PRESENT count0;
N>0 phát **một** aggregate PRESENT_AT_START/count=N/ref absent/PARTIAL. Poll không đổi
không phát lại; diff xác nhận phát REMOVED trước CONNECTED, mọi event mang count cuối
snapshot. ERROR/UNKNOWN/UNSUPPORTED không làm mất baseline hoặc tạo false removal.
Recovery chỉ ghi diff quan sát lúc scan khỏe, không bịa thời điểm/transition trong gap.
Collision/invalid slot/>64USB giữ UNKNOWN, không clamp/dedup rồi giả đầy đủ; input≤128.

Disk Number/Index là slot uint32 tạm, **không physical identity**. Tất cả adapter events
PARTIAL. Aggregate baseline không ref; slot mới sau baseline có UUID opaque, ổn định
khi liên tục thấy; removal dùng cùng ref nếu còn correlation. Source gap bỏ correlation
nhưng giữ tập last-confirmed. Cùng index bị thay thiết bị giữa hai scan không phân biệt
được; không tự phát event hoặc nhận là cùng phần cứng chắc chắn. Mapping≤64 và dọn khi
stop. Không gửi số index/serial/PNP/volume/file/username hoặc hash các dữ liệu đó.

Owner cấp **một SignalRunContext cho mọi typed emitter**, nên USB không có counter riêng.
Context freeze IDs/sequence/time/origin; caller cung cấp giờ client microsecond/origin tại
scan, không trừ clock hai máy. One USB claim/context lifetime + adapter generation chặn
double binding/rebind old context/stale callback. Adapter chỉ RAM, không worker/listener/
IO/shutdown wait; stop không đóng queue/context/transport không thuộc sở hữu. Owner kết
thúc phiên phải đóng chúng/lease. Event đã enqueue trước adapter stop vẫn do queue sở hữu.
Source baseline tiến cả offline/queue-full; reject được ghi đúng admission/drop qua queue,
không giả ACK hoặc replay transition ở poll sau. Chưa status/gap wire hoặc policy offline.

## Kiểm chứng tự chạy

Windows11 x64/Temurin21.0.10+7/Maven3.9.15/PostgreSQL18.3 TEST loopback.

| Lượt | Source | Kết quả |
|---|---|---|
| Baseline unit tập trung, trước sửa | e189b61 | **154 PASS/0 SKIP/0 lỗi** |
| Unit mới và hồi quy | working candidate trước693cf44 | **174 PASS/0 SKIP**, fixture compile lỗi trước đó giữ log |
| Focused/PG đầu | 693cf44 | **310 PASS/0 SKIP** |
| Ownership regression trước sửa | runtime693cf44 +2test mới | **2/2 FAIL**, không skip/đổi expected |
| Client regression sau sửa | candidate aac7528 | **104 PASS/0 SKIP** |
| Focused cuối | aac7528 | **312 PASS/0 SKIP/0 lỗi** =33protocol +181client +34probe +64server |
| Client cuối/ref rotation bổ sung | 3ff9aaa | **104 PASS/0 SKIP/0 lỗi**, không thêm test method hoặc đổi runtime |
| Full reactor verify/JAR | aac7528 | **1315 total =1175 PASS/140 SKIP/0 lỗi**, PG opt-in false; PG kiểm riêng |

**25 case mới** =15adapter +3context +4projection +3PG, là subset các lượt trên; không
cộng baseline/lượt lặp thành unique tests. Full skips là2symlink/sidecar và GUI/package/
PG opt-ins, không gọi chúng PASS. CI Windows final exact HEAD được xác minh riêng tại PR.

- Unit dùng snapshot/time MOCK, expected timeline tính độc lập: empty/one/many baseline,
  unchanged/add/remove/multi-change, failures/unknown/recovery, index reuse/collision/limits,
  ref continuity/gap cleanup, shared sequence xen kẽ display/focus, queue reject/offline,
  immutable input/payload, concurrent40snapshot cùng tập chỉ một baseline, stop/restart/
  changed attempt/old callback/closed queue/context và one-time claim.
- **24 REAL PG/HTTP/WS pipeline cases**, gồm3adapter cases mới. Chuỗi MOCK `{1}` baseline
  → unchanged → `{1,2}` connect → ERROR → `{2,3}` recovery → empty tạo đúng6USB events:
  PRESENT/CONNECTED/REMOVED/CONNECTED/REMOVED/REMOVED, counts **1/2/2/2/0/0**, observation
  và timeline sequences1..6. Recovery time là scan+60s, ref của slot mới còn liên tục khớp
  removal; ref của slot đi qua gap bị bỏ. History readback khớp typed content.
- CONNECTED thứ hai mất ACK **SIMULATED**, retry giữ nguyên envelope/IDs/payload/origin/
  time; đúng1row và stored sequence2/duplicate true. Stop/restart stale callback không
  ghi row vào attempt cũ/mới; token revoke thật qua WS làm delivery/worker dừng và adapter
  không nhận thêm event. Regression typed display/focus/window/auth/rollback/history và
  process/FULL/presence giữ; không lặp case5001rows/campaign lớn hoặc E1/E2.
- Hai lượt PG thành công: **0 owned schemas / clusterStopExit0**, password file không còn;
  fixture đóng clients/server/context/listeners/worker. Adapter tự tạo0worker/subscription.
- **10 JAR checks PASS:** adapter/context/metadata/delivery classes khớp compiled, protocol
  dependency khớp, probe class/resource khớp; client fat JAR không chứa CimSnapshotSource,
  server JAR không chứa monitoring-spike dependency test. Artifact chỉ local ignored.
- Một **REAL standalone readiness** MSFT snapshot: **READY/count0, exit0, queryChildrenAlive0,
  pipeReadersTerminated=true**, output không bị giới hạn. Không chuyển sample thật vào
  adapter/pipeline; không hotplug, không xác minh phone/UASP/index reuse trên hardware.

## Findings và lỗi setup được giữ

1. Fake transport thiếu `onConnectionState`: compile đầu dừng trước adapter tests. Sửa
   fixture interface stub, giữ log, không tính test chưa chạy PASS.
2. Setup PG đầu: Windows log `could not bind IPv4 address ... Permission denied`, cluster
   shutdown **trước Maven**. Runner cũ sau đó mask bằng DateTime parse vì chưa có startUtc.
   Không có test feature ở lượt này. Không lưu số cổng FAIL nên **chưa chứng minh nguyên
   nhân là excluded port range**. Đã đọc netsh exclusions, runner local chọn range30000–39000
   khác dải đó và lưu selected-port; hai run mới thành công. Không đổi ACL/firewall/timeout,
   production config hoặc workflow; không rerun đến PASS rồi nhận root cause đã rõ.
3. `UsbSignalAdapter.start` chỉ kiểm lastRunId cục bộ: adapter thứ hai chưa có lastRunId
   nhận cùng context; A→B→A cũng nhận lại context còn active, gây baseline/ref mới trong
   cùng observer run. Hai regression **FAIL expected rejection/actual accepted** trên693cf44.
   `SignalRunContext.claimUsbInput` dùng một boolean bounded, atomic một claim suốt lifetime;
   start kiểm claim trước thay binding. Fix aac7528, cả2regression và toàn focused PASS;
   không đóng context/queue của emitter khác để né lỗi. Log/XML/hash lỗi còn local ignored.

## Reproduce, evidence và bàn giao

```powershell
mvn --batch-mode --no-transfer-progress -pl server -am "-Dtest=UsbSignalAdapterTest,SignalRunContextTest,MockUsbSnapshotProjectionTest,SignalProtocolTest,TypedSignalTest,SignalDeliveryTest,RealtimeClientTest,UsbObservationTrackerTest,UsbStorageProbeTest,CimSnapshotSourceTest,CimClassificationScriptTest,SignalDisabledTest" -Dsurefire.failIfNoSpecifiedTests=false test
mvn --batch-mode --no-transfer-progress -Duser.timezone=UTC verify
```

PG dùng ScriptBlock đọc runner `scripts/verify-mon07-postgres.ps1` (không đổi ExecutionPolicy),
thêm các suite adapter/projection/probe và chọn mọi SignalPipelinePostgresTest method trừ
fiveThousandOneCommittedSignalsPageWithoutLossAndKeepThroughDuringConcurrentWrites.
Runner/command exact, result/XML/build/raw hashes tại receipt; OutputRoot ngoài reactor
root target. Không root clean, đọc `.env`, dùng DB dev hoặc kill process theo tên.

Raw/JAR/log/XML dưới `target/adapter-check` và root `target/mon02-adapter-runs`, local
ignored; hash không phải link tải GitHub. Public chỉ report/receipt, không CIM object,
hardware identifiers, token/credentials hoặc đường dẫn cá nhân. Không sửa tracker/DONE.

C nối provider thật sau này cần C/B review packaging/module boundary (bridge hiện TEST
only), provider classification/slot reuse/multi-LUN/cadence/no-overlap, disclosure/quyền/
stop/revoke và H01–H12 có USB thật. B/emitters dùng chung context/queue, không counter
riêng. ADR-V2-05/policy/owner/hardware/gates vẫn pending. **USB0/12 NOT_RUN, MON-02 chưa
DONE, experiment OFF/FULL mặc định**; không tự bật production hoặc chuyển task khác.
