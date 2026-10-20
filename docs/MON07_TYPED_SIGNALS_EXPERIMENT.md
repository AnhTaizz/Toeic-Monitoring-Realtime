# MON-07 — Typed signals, slice thử nghiệm 20/10/2026

**USER_AUTHORIZED_EXPERIMENT; owner policy/physical review PENDING.** Người dùng cho
phép mở rộng slice MOCK qua WS/PG TEST thật, gồm protocol/queue/persistence/history và
migration additive. Đây không là chữ ký A/B/C, ADR/D07 APPROVED hoặc MON-02/03/04/07 DONE.
Experiment mặc định OFF; FULL mặc định. Không observer production, push/dashboard,
activity/rules, offline60s hoặc retention30ngày.

## Contract chọn trước implementation

Giữ envelope v0 và common payload schemaVersion1 của slice display. Mỗi event có
eventId ổn định, observerSessionId random cho một explicit run, observationSequence
1..9007199254740991 dùng chung trong run; observedAt UTC microsecond là giờ client.
Origin/connection ID được freeze khi tạo, không đổi khi retry hoặc reconnect. Run ID
không cấp quyền. Server gán receivedAt/timelineSequence sau xác thực trong transaction.

| eventType | source | details | metadataQuality / validation | Ví dụ MOCK details |
|---|---|---|---|---|
| DISPLAY_COUNT_OBSERVED | JAVAFX_DISPLAY | count 0..32; reason INITIAL/CHANGED | COMPLETE; giữ tương thích | `{"count":1,"reason":"INITIAL"}` |
| USB_STORAGE_PRESENT_AT_START | WINDOWS_USB_STORAGE | deviceClass USB_MASS_STORAGE; storageCount 1..64; deviceRef optional | COMPLETE/PARTIAL; thiếu ref phải PARTIAL | `{"deviceClass":"USB_MASS_STORAGE","storageCount":1}` (PARTIAL) |
| USB_STORAGE_CONNECTED | WINDOWS_USB_STORAGE | cùng USB; count 1..64 | như trên | `{"deviceClass":"USB_MASS_STORAGE","storageCount":2,"deviceRef":"mock-usb-2"}` |
| USB_STORAGE_REMOVED | WINDOWS_USB_STORAGE | cùng USB; count 0..64 sau thay đổi | như trên | `{"deviceClass":"USB_MASS_STORAGE","storageCount":0,"deviceRef":"mock-usb-2"}` |
| EXAM_FOCUS_CHANGED | JAVAFX_EXAM_FOCUS | focused boolean; context INITIAL/EXAM_INTERNAL_DIALOG/OTHER_OR_UNKNOWN; lostDurationMillis optional 0..86400000 | COMPLETE; duration chỉ khi focused=true và context khác INITIAL | `{"focused":true,"context":"OTHER_OR_UNKNOWN","lostDurationMillis":125}` |
| EXAM_WINDOW_STATE | JAVAFX_EXAM_WINDOW | minimized/fullscreen boolean; reason INITIAL/CHANGED | COMPLETE | `{"minimized":false,"fullscreen":false,"reason":"CHANGED"}` |

Mọi field trên required trừ optional; null khác thiếu và bị từ chối. Không field lạ,
duplicate key, enum lạ, số giả integer, Unicode lỗi; bound giữ nguyên envelope8192 /
payload4096 / details1024 UTF-8 bytes, depth6. USB deviceRef là ASCII ID tối đa64 byte,
random theo run, không serial/path/volume/hardware ID/username hoặc hash các giá trị đó.
PARTIAL có ref vẫn được nhận khi classification chỉ một phần; count là số phân loại
được, không phải tổng thiết bị chắc chắn có. Chỉ USB mass-storage, không generic USB.

Focus duration do emitter tính từ monotonic start/end trong cùng client/run; protocol
chỉ kiểm bound/điều kiện, không thể chứng thực đồng hồ hoặc hành động thật từ payload.
Không trừ timestamp hai máy. Window state chỉ mô tả; INITIAL non-fullscreen không là
fullscreen exit, không tự phạt/hủy bài. Không đo được thì không dựng boolean/count.
OBSERVER_STATUS/SIGNAL_GAP/activity chưa triển khai, bị từ chối và không quảng cáo trong
capability. UNKNOWN/UNSUPPORTED không có nghĩa không USB hoặc luôn focused; giữ trạng
thái cục bộ và chờ slice status riêng, không gửi PRESENT count0 hay synthetic REMOVED.

## Pipeline và ranh giới tích hợp

Typed immutable values → SignalMessage → **cùng một SignalDelivery** → RealtimeClient
socket hiện có → handler/service → transaction event+counter → COMMIT → ACK → HTTP
history phân trang. Tất cả type chia sẻ RAM256/1MiB/2flight/2write/retry5; không quota
theo type. Capability theo connection/lease, run guard, retry immutable và cleanup cũ
được giữ. Server chỉ công bố sáu type khi flag TEST bật. Client nhận subset hợp lệ,
không nhận duplicate/unknown type, không gửi type ngoài subset đã confirm.

V16 append-only mở constraint V13 cho sáu mapping trên; không sửa V13–V15/checksum.
Main ad8c370 và các nhánh PR EX đang mở chỉ tới V15 lúc chọn version. **Flag OFF vẫn
có thể áp V16 qua Flyway mặc định**; OFF chỉ tắt lane runtime, không rollback DDL.
Nâng cấp TEST phải giữ display cũ; test cả fresh schema và upgrade15→16, không DB dev.

B sau này tạo FocusSignal/WindowSignal/DisplaySignal từ emitter được duyệt, C tạo
UsbStorageSignal từ classification đã đủ bằng chứng; tất cả dùng chung run/sequence,
origin và SignalDelivery. Không tự nối UsbObservationTracker hoặc JavaFX listeners
trong slice này. Probe hiện có là local DTO, không attestation protocol; aggregate
baseline phải được adapter xử lý đúng, không gửi per-device startup như tổng baseline.

Kết quả thực chạy/source SHA, cleanup, giới hạn và link PR được ghi ở evidence khi
hoàn tất. MOCK input / REAL transport+DB / SIMULATED faults được phân biệt riêng.
