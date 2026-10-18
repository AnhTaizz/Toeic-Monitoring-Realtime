# Bàn giao nghiệm thu cuối Phase3

19/10/2026 — LAN vật lý0/41; nghe thủ công và MSI máy thứ hai NOT RUN. Đây là hướng dẫn còn bắt buộc, không là chữ ký nghiệm thu. Người dùng đã giao Codex review kỹ thuật; người thao tác máy thật vẫn phải ghi Actual/evidence theo mẫu. FULL baseline, DELTA experimental opt-in, chưa có gate C/B.

## Artifact và source

Build từ clean main `cfbba580886ede7b1b5a4f56875197b10f284514`. MSI **0.3.11** ở `target/stage3-release-20261019/ToeicMonitor-0.3.11.msi`, SHA256 `edd0ceb55d2512d046c972aff2966382d0ad64051e29cc6a14035c643b67a214`. Bộ LAN `target/stage3-release-20261019/T3C4-cfbba58.zip`, ZIP SHA256 `2122b8b3dce57d5befb622603e9c674d9a0786d630b5ed93e75d8c668abf6ab1`, manifest SHA256 `fe2a8425305450e7e155b8ce2903246cc07943f06f88865688e1798f802744bb`. [Manifest độc lập trên Git](../evidence/stage3-execution-20261019/artifacts.json), [provenance](../evidence/stage3-execution-20261019/msi-build-info.json), [kết quả test](../evidence/stage3-execution-20261019/REPORT.md).

Người giữ máy build chuyển đúng MSI/ZIP/checksum qua USB/kênh riêng được nhóm cho phép, không `.env`/profile/raw; chưa có bằng chứng đã chuyển B. Trên cả A/B kiểm Get-FileHash -Algorithm SHA256 đối chiếu hash trên Git, dừng nếu sai. Giải nén ZIP vào thư mục mới rồi thực hiện [runbook đã cập nhật source/hash](T3_C4_PHYSICAL_LAN_ACCEPTANCE.md), không sửa manifest hoặc ghi đè lượt cũ. Kit cần JDK21 cho gate JAR; lượt MSI riêng chạy launcher bundled runtime không IDE/JDK trên PATH. Giữ MSI0.3.10/kit3044 lịch sử, không dùng nghiệm thu source cuối.

Clean test/package mỗi lượt942PASS/4SKIP và installed FullSuite943PASS/3SKIP trên một Windows host/PG thật. Local MSI đã cài Unicode path, launch không agent/JAVA_HOME/JDK PATH, kiểm bundled JVM, Reading/Listening/FULL qua TEST automation rồi gỡ; hearing/socket faults SIMULATED, second candidateREADY MOCK. Không thay nghiệm thu máy B. Release config/docs followup không đổi production Java/POM; buildSHA giữ rõ thay vì gán artifact sang SHA docs mới.

### Lệnh MSI trên máy B sạch

Dùng PowerShell thường trên tài khoản TEST không có ToeicMonitor cài sẵn; nếu đã có thì giữ nguyên và chọn tài khoản/máy TEST khác. Nhận MSI mới và kiểm hash như trên. Chạy cài giao diện, chọn thư mục có dấu/khoảng trắng, sau đó chỉ định EXE vừa cài:

```powershell
$msi=Read-Host 'Duong dan ToeicMonitor-0.3.11.msi'
if((Get-FileHash -LiteralPath $msi -Algorithm SHA256).Hash.ToLowerInvariant() -cne 'edd0ceb55d2512d046c972aff2966382d0ad64051e29cc6a14035c643b67a214'){throw 'MSI_HASH_MISMATCH_STOP'}
Start-Process "$env:SystemRoot\System32\msiexec.exe" -Wait -ArgumentList @('/i',('"'+$msi+'"'),'/norestart')
$exe=Read-Host 'Duong dan ToeicMonitor.exe vua cai trong thu muc Unicode'
$start=[Diagnostics.ProcessStartInfo]::new($exe);$start.UseShellExecute=$false
foreach($key in @('JAVA_HOME','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')){$start.EnvironmentVariables.Remove($key)}
$start.EnvironmentVariables['PATH']="$env:SystemRoot\System32;$env:SystemRoot"
$client=[Diagnostics.Process]::Start($start)
# Nhap origin LAN cua A trong GUI; chay Reading/Listening/FULL va LT01–04 ben duoi, dong qua GUI.
```

Sau khi đã chạy đủ bước thủ công, lưu evidence và đóng client qua GUI, B gỡ đúng sản phẩm TEST vừa cài trong cùng PowerShell:

```powershell
Start-Process "$env:SystemRoot\System32\msiexec.exe" -Wait -ArgumentList @('/x',('"'+$msi+'"'),'/norestart')
```

B ghi Windows/thiết bị loa/version/buildSHA/hash/Unicode path/expected/actual/ảnh và người chạy; A xem evidence. Bấm “Tôi nghe được” chỉ sau khi người B thật sự nghe đúng file/câu hỏi; không dùng âm tone MOCK của automation để nhận nội dung đề đã đúng. LT01–LT04 chưa chạy giữ NOT RUN.

## B/C — hai Windows vật lý

1. Trên A: PostgreSQL TEST riêng, server/JDK21, proctor và candidateA. Trên B: cài MSI mới bằng quyền thường, không IDE/JDK trên PATH, chạy candidateB. Mọi client dùng cùng origin IPv4 LAN của A; DB chỉ loopback A. Không đổi firewall/adapter ngoài thao tác ngắt LAN đã được người vận hành cho phép.
2. Dùng [41case gốc](templates/t3c4-lan/cases.csv), [topology](templates/t3c4-lan/topology.json), [review](templates/t3c4-lan/review.json) và [query giữ lịch sử](templates/t3c4-lan/verify-history.sql). Ghi alias máy/OS/người chạy/time/sourceSHA/expected/actual/ảnh hoặc correlation. Không điền PASS trước khi thực hiện, không đưa token/tài khoản/IP private vào Git.
3. Ưu tiên nhóm F: rút LAN thật của B giữa Reading/Listening; A vẫn thao tác được, UNKNOWN/STALE đúng; re-auth/OPEN/epoch/FULL mới; không false ACK, answers đã ACK giữ; Listening không autoplay; timeline/gap cũ còn sau hồi phục. Local42case có faults SIMULATED không thay các case này.
4. B nghe audio thật và câu hỏi tương ứng, test loa, startIDtrùng không phát hai lần, interruption dừng/khóa, reconnect/mở lại không replay/resume. Theo LT01–LT04 trong [ma trận B](ACCEPTANCE_B_MANUAL_MATRIX.md). Ghi nhận thiết bị/codec thực, không coi MediaPlayer unit test là nghe thật.
5. Cài/chạy/gỡ MSI trên B ở đường dẫn Unicode/khoảng trắng, không IDE/JDK trên PATH. Giữ version/source/hash installer, Reading/Listening/FULL và cleanup. A review bằng chứng; không cài đè hoặc gỡ ứng dụng không thuộc lượt TEST.

## Chốt

C cùng B ghi quyết định gate theo dữ liệu máy thật. Nếu DELTA chưa đủ MT02–MT06/LAN: giữ FULL và ghi DELTA chưa production-ready; E2 không phải điều kiện chứng minh lợi ích trong Phase3. Tracker/hours chỉ đổi sau xác nhận và evidence thật; không tự ký A/B/C. Chỉ dùng STAGE3_DONE khi toàn bộ physical/manual/installer gates bắt buộc đã đạt.
