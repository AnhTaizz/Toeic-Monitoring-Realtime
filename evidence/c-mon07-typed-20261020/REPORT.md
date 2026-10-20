# MON-07 — typed USB/focus/window experiment, 20/10/2026

**USER_AUTHORIZED_EXPERIMENT; OWNER/PHYSICAL_REVIEW_PENDING; MON-07 NOT_DONE.**
Người dùng cho phép slice code/test/docs/push/PR và merge commit khi đủ CI/quy định,
không ký thay A/B/C. [Contract trước code](../../docs/MON07_TYPED_SIGNALS_EXPERIMENT.md),
[protocol đang triển khai](../../docs/PROTOCOL.md), [receipt/hashes](receipt.json).

## Source và phạm vi

- Base `ad8c370992423d58c730db1320d7fe247beb587f`, nhánh
  `feat/c-mon07-typed-signals-20261020`, worktree mới sạch. Root main cũ không reset/pull;
  các nhánh/worktree trước được giữ. Main/open PR đã kiểm trước chọn migration: không
  slice typed trùng; EX PR77/78/79/76 chỉ tới V14/V15, không V16. Kiểm lại trước merge.
- Runtime/test cuối **`abb282e81b4c6f8a3258ded26ff9c9a95b3a5aff`** sửa raw byte bounds
  sau finding final review. Triển khai đầu a0e9200/test c71ddde đã có252PASS nhưng chưa
  phát hiện bypass whitespace; không dùng chúng thay source cuối. JAR/receipt cũ giữ
  riêng tại `typed-check/previous-build-a0e`, mỗi artifact giữ source/hash của chính nó.
  Publication HEAD/CI/merge SHA được ghi tại PR để tránh vòng tự tham chiếu receipt/commit.
- Giữ DISPLAY_COUNT_OBSERVED; thêm USB_STORAGE_PRESENT_AT_START, USB_STORAGE_CONNECTED,
  USB_STORAGE_REMOVED, EXAM_FOCUS_CHANGED, EXAM_WINDOW_STATE. Chưa OBSERVER_STATUS,
  SIGNAL_GAP, activity, rules, push hoặc dashboard; không gắn production observer.

## Code và bất biến

`Signal` sealed interface + bốn immutable records; `Signal.parse` dispatch exact schema,
source/details/quality/bounds. USB chỉ mass-storage, missing ref→PARTIAL, ref random theo
run ≤64 byte; không file/serial/volume path/hardware ID. Focus duration chỉ ở restored,
context khác INITIAL, 0..86400000; emitter phải đo monotonic cùng run, parser không thể
chứng thực đồng hồ. Window chỉ state/INITIAL hoặc CHANGED, không verdict/hủy bài.
UNKNOWN/UNSUPPORTED không được biến thành count0/false; status wire chưa có, giữ hạn chế.

`SignalJson.wireObjectSizes` đo original UTF-8 object spans sau strict syntax parse,
trước normalization, gồm whitespace/escape; không chỉ đo compact JSON. Shape hợp lệ có
ba mức object envelope/payload/details; validator schema vẫn kiểm đầy đủ, helper không
là parser thay thế và không áp lên parser process. Test exact4096/1024 nhận, +1 từ chối;
WS padded payload/details INVALID_INPUT và không insert.

SignalMessage → **cùng SignalDelivery không đổi quota** → RealtimeClient/socket hiện có
→ handler → SignalService transaction event/counter → COMMIT → ACK → HTTP history.
RAM256/1MiB/2flight/2write, retry5 và frozen ID/payload/origin/time vẫn chung cho mọi type.
Capability chỉ sáu type khi bật, client nhận subset hợp lệ/không trùng; gắn đúng lease,
run/attempt/socket generation và kiểm lại type ngay trước write. Default old display
payload giữ tương thích; experimental client cũ chỉ nhận exact một-type capability có
thể cần nâng cấp đồng bộ với server TEST, không giả backward capability negotiation.

Persistence bind type/source/quality thay literal display; DB read qua cùng strict typed
parser. Canonical equality dùng record, không JSON key order; same ID khác type/details
conflict. Không đổi auth/lock order, expiry sau lock wait, atomic counter, ACK sau proxy
COMMIT, fixed-through/no-store/limit100/body cap, shared process/FULL/DELTA/presence/Listening.

V16 append-only mở check constraints của V13, giữ nguyên checksum/nội dung V13–V15.
Upgrade15→16 so nguyên display row JSON/counter và migration checksums trước/sau; history
đọc lại display cũ. Fresh16 nhận đủ type. **OFF vẫn áp V16 qua Flyway mặc định**, nhưng
không service/routes; valid-auth capability404 và signal WS INVALID_INPUT, không row/counter.
Không `.env`, DB dev dùng chung, Flyway clean/outOfOrder hoặc migration sửa lịch sử.

## Kiểm chứng tự chạy

Windows11 x64, Temurin21.0.10+7, Maven3.9.15, PostgreSQL18.3 local TEST cách ly.

| Lượt | Source | Kết quả |
|---|---|---|
| Baseline trước code | ad8c370 | **227 PASS / 0 SKIP / 0 lỗi**, có16 pipeline PG cases |
| Focused đầu sau triển khai | a0e9200 | **252 PASS / 0 SKIP / 0 lỗi** |
| Focused trước finding byte bounds | c71ddde | **252 PASS / 0 SKIP / 0 lỗi** |
| Focused cuối sau fix | abb282e | **253 PASS / 0 SKIP / 0 lỗi** =33 protocol +163 client +57 server;21 pipeline PG cases |
| Toàn reactor `verify` + JAR | runtime a0e9200 | **1289 total =1152 PASS /137 SKIP /0 lỗi**, PG opt-in false; PG đã kiểm riêng |
| Toàn reactor `verify` + JAR cuối | abb282e | **1290 total =1153 PASS /137 SKIP /0 lỗi**, PG đã kiểm riêng253PASS |

26 case mới =16 protocol +5 client +5 PG, là subset các lượt trên, không cộng repeated
runs thành test mới. Full local có2 skip symlink/sidecar, các GUI/package và PG opt-ins
còn lại; receipt có danh sách. Không gọi skip là PASS hoặc CI là hardware acceptance.

- Protocol: sáu type, USB count/ref/quality bounds, focus duration/context/booleans,
  window state, wrong source/details/quality, unknown/status/gap/activity, private/extra
  fields, duplicate keys/integer/Unicode/byte limits và canonical/immutable content.
- Delivery/transport: mixed type queue tổng bytes/count/flights, retry giữ nguyên USB/
  focus content, subset không bị caller mutate, duplicate/unknown capability, run guard,
  capability shrink pre-write, old typed callback; legacy lifecycle/reconnect regression.
- **21 REAL PG/HTTP/WS cases** gồm11 MOCK states/6type qua shared delivery, COMMIT/ACK/
  readback; riêng USB_CONNECTED mất ACK SIMULATED rồi retry duplicate cùng sequence1,
  đúng1row/1sequence. Canonical reordered USB JSON duplicate; changed count/type conflict
  giữ row. Window deferred-COMMIT-failure SIMULATED rollback event/counter và không ACK
  thành công. Focus stored duplicate vẫn kiểm foreign/role/terminal/expired/revoked token.
- History nhiều type xen kẽ, paginate3/item order/fixed-through11 khi thêm row12; upgrade
 15→16 và OFF thật. Các barrier auth/lock/rollback/process/FULL/presence cũ chạy lại.
  Không lặp case5001 rows đã có evidence trước; không E1/E2/GUI/hardware mới.
- Bốn lượt TEST còn **0 owned schemas**, **clusterStopExit0**, password file không còn.
  Server/client contexts, sockets, listeners/workers đóng qua fixture teardown.

## FAIL được giữ và cách sửa

1. Baseline Maven lần đầu dừng trước test vì cache mặc định không writable; dùng cache
   đã cài, không đổi source hoặc dependency. Log giữ riêng, không tính là test FAIL.
2. Lượt typed đầu:17 legacy protocol tests có1FAIL vì fixture lịch sử USB hợp lệ nay
   được parser nhận. Giữ nguyên fixture cũ; test phân biệt schema validity với capability,
   không sửa input để né test.15 typed protocol mới đã PASS ở cùng lượt đó.
3. `queuedTypedSignalRechecksCapabilityWhenSubsetShrinks`: **1/1 FAIL**, future ghi USB
   completed thành công sau khi subset chỉ còn display. Sửa write guard kiểm eventType
   trong capability hiện tại ngay trước IO, không đổi default/quota/retry hoặc swallow lỗi.
   Regression cuối PASS. FAIL/XML/hash còn local ignored; đây là lỗi trong code mở rộng
   đang phát triển, không gán commit base có regression capability mới chưa tồn tại.
4. Runner local dùng `#methods` để bỏ lượt5001 làm bộ lọc inventory thiếu XML PG:
   receipt cũ báo211/231 thay vì227/252. Giữ nguyên original result/log, tạo
   `result-reconciled.json` từ module summaries và XML21case. Final runner sửa đúng bộ
   tổng hợp local, đếm trực tiếp252; không thay pipeline hoặc workflow để che lỗi.
5. Final review trên publication10cabe2: test raw payload padded vượt4096 trong envelope
   dưới8192 **1/1 FAIL** (expected rejection nhưng parse thành công). Nguyên nhân đo
   compact JSON sau parse nên mất whitespace; thêm size check original object spans.
   Exact boundary/+1 payload và details PASS sau sửa, PG WS từ chối cả hai padded cases.
   Lượt focused cuối253PASS; không merge CI xanh của HEAD cũ thay cho bản sửa này.

## Artifact và lệnh

Raw/log/XML/TEST cluster/JAR dưới các root `target/mon07-typed-runs` và worktree
`target/typed-check`, đều ignored/local; **không GitHub downloads**. Receipt công bố
hash/scalar/tên test, không token/credential/đường dẫn cá nhân. JAR/receipt trước sửa giữ
riêng; JAR cuối source abb282e có **11 entry checks PASS** (kể cả SignalJson mới,
protocol dependency và V16) khớp compiled/source. Hash/source build cuối nằm trong
receipt, không relabel JAR a0e9200.

```powershell
mvn --batch-mode --no-transfer-progress -pl server -am "-Dtest=SignalProtocolTest,TypedSignalTest,SignalDeliveryTest,RealtimeClientTest,SignalDisabledTest" -Dsurefire.failIfNoSpecifiedTests=false test
mvn --batch-mode --no-transfer-progress -Duser.timezone=UTC verify
```

PG dùng runner `scripts/verify-mon07-postgres.ps1` trên TEST cluster tự tạo, PostgresBin/
JavaHome/MavenRepository rõ ràng, OutputRoot ignored **ngoài reactor root target**. Trong
lượt này nạp script bằng ScriptBlock (không đổi ExecutionPolicy), thêm TypedSignalTest,
chọn toàn bộ method SignalPipelinePostgresTest **trừ** fiveThousandOneCommittedSignalsPageWithoutLossAndKeepThroughDuringConcurrentWrites.
Command exact và hashes của runner local/result/XML nằm trong receipt; không đọc `.env`.

## Bàn giao và giới hạn

B có FocusSignal/WindowSignal/DisplaySignal để nối emitter sau review lifecycle/notice,
monotonic duration/internal dialogs/debounce. C có UsbStorageSignal để nối sau hardware/
classification/ref mapping và aggregate startup; không nối trực tiếp probe-local DTO hoặc
coi phone charging là USB mass-storage evidence. Dùng một observer run/sequence/origin,
một queue; stop/revoke/logout/close phải giữ lifecycle đã kiểm, không callback sang run mới.

USB physical0/12, LAN0/41 và desktop/human acceptance vẫn pending; MON-02/03/04/07 không
DONE. ADR/D07/offline60s/retention30ngày chưa human approved; chưa capture thật. FULL
mặc định, experiment OFF, BASE-03 gate NOT_DECIDED giữ nguyên. Chỉ merge kỹ thuật slice
khi final review/CI/rules đạt; merge không thay owner policy hoặc hardware acceptance.
