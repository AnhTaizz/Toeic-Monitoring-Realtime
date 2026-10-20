# MON-02 — adapter snapshot USB, thử nghiệm 20/10/2026

**USER_AUTHORIZED_EXPERIMENT; USB physical0/12 NOT_RUN; MON-02 chưa DONE.** Người dùng
chưa có USB và cho phép code/test/push/PR/merge commit trong slice MOCK. Không observer
candidate production, capture thật, dashboard/rules hoặc quyết định ADR/policy/gate.
FULL mặc định; experiment OFF. Không thêm signal type/migration/socket/scanner/queue.

## Ranh giới module chọn trước code

`monitoring-spike` hiện không có compile dependency, là CLI khảo sát độc lập; không biến
nó thành thư viện của client. Client thêm stateful `UsbSignalAdapter` nhận input tối thiểu
bất biến: trạng thái nguồn và danh sách **disk slots đã phân loại USB mass-storage**.
Bridge TEST trong server dùng `UsbSnapshot`/classification hiện có, lọc NON_USB và giữ
UNKNOWN/ERROR/UNSUPPORTED, với dependency monitoring-spike chỉ test scope. Không copy
CIM/parser/resource hoặc thêm native dependency; không coi UsbObservationTracker local
(baseline per-device) là wire adapter (baseline aggregate). Provider/CLI không bị sửa.

## Semantics

| Input | Thay đổi xác nhận | Wire signal/details/quality | Khi chưa đủ dữ liệu |
|---|---|---|---|
| Chưa baseline, UNKNOWN/ERROR/UNSUPPORTED | chưa có tập xác nhận | không event | giữ local UNKNOWN/UNSUPPORTED, chờ READY |
| READY rỗng đầu tiên | baseline rỗng hợp lệ | không PRESENT vì schema yêu cầu count≥1 | server không nhận bằng chứng absence/status từ việc thiếu event |
| READY có N slots đầu tiên (1..64) | aggregate baseline | một PRESENT_AT_START, count=N, bỏ ref, PARTIAL | không phát N baseline per-device |
| READY không đổi | không transition quan sát được | không event | không chứng minh không có thay đổi giữa hai scan |
| READY có slot mới | thêm slot USB phân loại được | CONNECTED cho mỗi slot mới, count=finalN, ref opaque mới, PARTIAL | chỉ là quan sát slot, không attestation thiết bị vật lý |
| READY thiếu slot đã biết | biến mất qua scan đầy đủ | REMOVED mỗi slot thiếu, count=finalN, ref trước đó nếu còn đáng tin, PARTIAL | không suy từ snapshot lỗi/rỗng giả |
| UNKNOWN/ERROR sau baseline | không xác nhận thay đổi | không event, giữ tập baseline; bỏ ref correlations | không tạo REMOVED; local health không READY |
| READY sau khoảng nguồn lỗi | chênh tập so last confirmed scan | chỉ diff thấy khi phục hồi; observedAt của scan phục hồi | không bịa thời điểm cắm/rút hoặc transitions trong khoảng thiếu dữ liệu |
| duplicate/missing/invalid slot hoặc >64 USB | không có snapshot đủ contract | không event, local UNKNOWN | không dedup collision/clamp count rồi giả đầy đủ |

Slot dựa đúng uint32 Number/Index của provider, **không danh tính phần cứng bền vững**.
Tất cả wire metadataQuality PARTIAL vì provider không chứng minh physical identity hoặc
đầy đủ transitions. storageCount là số disk slots USB phân loại được (multi-LUN có thể
nhiều disk/thiết bị), không tổng số hộp USB. Mouse/keyboard/phone charging không thành
storage chỉ vì cắm cáp; bridge chỉ lấy Classification.USB từ disk-class provider.

Baseline aggregate không gán một ref cho nhiều thiết bị. Ref chỉ sinh khi một slot mới
được quan sát sau baseline, ổn định khi slot liên tục hiện diện; removal dùng cùng ref.
Ref là UUID random trong run, không serial/PNP ID/path/username/hash disk number. Sau scan
lỗi/unknown/collision, ref correlations bị vô hiệu hóa; các slot còn chung khi phục hồi
không tự có ref mới nếu không có CONNECTED mới. Index biến mất rồi tái xuất hiện có ref
mới; index bị reuse giữa hai scan mà tập không đổi **không thể phát hiện**. Không hứa
phân biệt thiết bị thay thế cùng index. Map tối đa64 slots, dọn khi stop/start run mới.

## Ownership và delivery

Một `SignalRunContext` do owner phiên cấp cho **mọi** emitter, giữ attempt/run và sequence
dùng chung, freeze event/request/trace IDs. Adapter không có counter USB. Caller cung cấp
observedAt UTC microsecond của scan và origin lấy từ transport chung khi quan sát; không
trừ giờ hai máy. `SignalDelivery` duy nhất giữ quota/bytes/flights/retries/ACK hiện có.

`start(context, delivery)` explicit, trả generation; chỉ nhận đúng scope và run mới.
Context có một USB input claim dùng một lần: không cho hai adapter lặp baseline hoặc
tái bind context cũ sau một run khác, kể cả context còn active cho emitter khác.
`accept(generation, input, time, origin)` chỉ xử lý RAM, serialize bằng khóa adapter;
`stop()` vô hiệu hóa generation trước khi dọn map/binding. Callback cũ không vào run hoặc
attempt mới. Adapter không tạo worker/subscription, không scan/IO/đợi shutdown trên FX.
Owner phải chạy provider trên worker riêng hiện có khi được duyệt tích hợp sau này.

Source baseline tiến theo snapshot xác nhận, độc lập trạng thái mạng/admission. Queue
offline vẫn giữ frozen event; full trả kết quả không nhận và tăng drops của delivery,
không replay transition ở scan sau hoặc giả ACK. Adapter không tạo queue/retry riêng.
Delivery/context đã đóng làm adapter dừng nhận mới. Adapter stop không đóng context,
queue, transport hoặc heartbeat lease dùng chung; owner đóng chúng khi kết thúc phiên.
Event đã enqueue trước adapter stop vẫn thuộc queue/owner phiên, không bị xóa riêng.

## Trước khi nối production

Chưa có binding runtime từ CimSnapshotSource vào candidate. Bridge TEST là nơi kiểm
projection từ model probe sang input adapter; observer thật sau này cần review module/
packaging, provider/identity limitations, bounded cadence/no overlap, disclosure/consent,
scope/lease/stop/revoke và hardware H01–H12. UNKNOWN không có wire status trong slice này;
không thêm OBSERVER_STATUS/SIGNAL_GAP để lấp giới hạn. Owner policy/ADR-V2-05 vẫn pending.
