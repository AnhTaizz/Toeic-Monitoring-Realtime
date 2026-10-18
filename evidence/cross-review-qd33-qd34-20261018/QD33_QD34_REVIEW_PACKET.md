# Cross-review QD-33 và QD-34 — 18/10/2026

**Main được fetch và kiểm: `f86403489bdd01887b926f2f9e4b72ddf05d1423`. QD-33: REQUEST_CHANGES. QD-34: ACCEPT_WITH_LIMITATIONS.** Có **1 finding P1 mới**: publish/thay file cache đồng thời trên Windows vẫn race dù race tạo thư mục đã được sửa. Không phát hiện P0 hoặc P1 mới của takeover trong phạm vi kiểm. Owner B/A chưa ký; agent không APPROVE hoặc đổi trạng thái QD.

Kiểm mới: **JUnit 144 PASS / 0 FAIL / 0 ERROR / 1 SKIP**,145case gồm10PostgreSQL thật. Ngoài JUnit: **4 batch filesystem concurrency FAIL**, mỗi batch320operation; một HTTP lost-response probe **PASS**. Không dùng BUILD SUCCESS để phủ nhận lỗi probe. Không chạy full Maven suite/MSI/LAN/manual hearing.

## 1. Preflight và provenance

- Workspace gốc bẩn/worktree khác giữ nguyên; checkout có Git metadata riêng, branch `review/qd33-qd34-20261018` từ latest main. Đã đọc QD/PROTOCOL, stabilization report, hướng dẫn vai A/B/C, tracker và acceptance liên quan. Chỉ thêm thư mục evidence riêng theo yêu cầu task; nhật ký/test/handoff đặt trong packet này để tránh các file PR47/51/52/53 và tracker chung.
- PR45 hiện OPEN, chưa merge, HEAD `a6524823c6a8d97cc7652fe4b776279db17d7d55`; PR47 OPEN, base main, HEAD `3044e5bc963f7ac9d8cdc4d8410696c301080534`. Không sửa/comment/merge/close các PR đó; không lấy code47 vào lượt test main.
- [source-manifest.json](source-manifest.json) ghi25source/test/doc Git blob và SHA256 tại main đã kiểm, provenance CI/audio; [test-results.json](test-results.json) ghi từng case, [test-commands.json](test-commands.json) ghi selector và cách tái chạy; [cleanup](environment-cleanup.json), [checksum](SHA256SUMS.txt). Raw log/XML/system properties/PG data chỉ ở ignored target, không công bố secret hoặc đường dẫn riêng.
- [Stabilization report](../stabilize-20261017/REPORT.md) source3702b6d ghi race PR45 112/640 và bản thay thế0/640,924PASS/4SKIP,MSI0.3.10. Đây là **historical evidence**. Probe0/640 cũ chỉ file/verify tạo thư mục, không chứng minh concurrent download/publication. Lượt mới phân biệt hai rủi ro này.

## 2. QD-33 — contract, source và finding

### Contract được đối chiếu

[QD-33](../../docs/QUYET_DINH.md#qd-33--cache-audio-hợp-nhất-fix-race-pr40-với-policy-junction-thay-pr45) cho phép ancestor trên configured root là junction/8.3 hợp lệ; root và directory bên dưới phải thường, không redirect ra ngoài. Cache kiểm manifest/scope/name/size/SHA256; tải vào partial riêng rồi publish khi đủ byte/hash; READY kiểm lại file và chỉ thành công sau server ACK. Threat model không gồm tiến trình độc hại cùng user thay cây thư mục giữa validate và write. Điều đó **không loại trừ concurrent reader/download hợp lệ** giữa các instance dùng chung cache.

| Source/method | Điều đã kiểm |
|---|---|
| [AudioFiles](../../client/src/main/java/vn/edu/toeic/client/audio/AudioFiles.java) `validateFile`, `validate`, `file` | Chặn tên path/device không hợp lệ, duplicate không phân biệt hoa/thường, wrong scope và giới hạn; attempt UTF-8 hash + manifestId tạo directory; existing target phải regular NOFOLLOW và real parent khớp |
| `safeDirectory`, `plainDirectory` | Normalize root; tạo root; chỉ kiểm từ root trở xuống; BasicFileAttributes NOFOLLOW/isSymbolicLink/isOther; canonical child theo real root; catch FileAlreadyExistsException rồi kiểm lại khi tạo con đồng thời |
| `download`, L78–100 | Partial tên riêng cùng directory; stream đóng trước size/hash check; vượt/thiếu/sai hash không publish; **L97 `Files.move(partial,target,REPLACE_EXISTING)` không ATOMIC_MOVE/không coordination với reader hoặc writer khác**; finally xóa partial |
| `verify`, L103–125 | Đọc byte thực, size/hash lại; race giữa exists/toRealPath/open/size vẫn có thể làm verification lỗi khi target đang bị replace |
| [AudioPreparation](../../client/src/main/java/vn/edu/toeic/client/audio/AudioPreparation.java) `prepare`, `reportReady`, `withdraw`, `close` | Worker riêng, generation guard, tải/xác minh lại trước READY; retry chuẩn bị giữ writer hiện tại; sharedWriter không takeover thêm; server ACK khớp request/attempt/writer/manifest; đóng API và interrupt worker |
| [AudioApiClient](../../client/src/main/java/vn/edu/toeic/client/audio/AudioApiClient.java) `send`, `download`, `close` | Bearer header, không redirect; stream có hạn đóng, API close đóng stream; client không tự sửa manifest/hash |
| [AudioPreparationView](../../client/src/main/java/vn/edu/toeic/client/audio/AudioPreparationView.java) `cacheRoot` | Default LOCALAPPDATA/TOEIC Monitor/audio-cache, fallback user.home; files/speaker/journal bên dưới, không lấy MSI install directory làm default. Override `toeic.audio.cacheDirectory` do operator chọn, không có guard riêng nhận diện MSI directory |
| [ListeningPlayback](../../client/src/main/java/vn/edu/toeic/client/audio/ListeningPlayback.java), [AudioService](../../server/src/main/java/vn/edu/toeic/server/audio/AudioService.java) | Playback kiểm cache trước Media; server READY/start theo scope/writer/manifest/deadline/commit. Lỗi local không phải successful READY |

### Finding P1 — QD33-P1: concurrent publication còn race

**Trigger hợp lệ:** nhiều AudioFiles instance cùng root/attempt/manifest/name, cùng payload hợp lệ. Không có attacker, junction hoặc corruption injection. Mỗi round seed cache hợp lệ, CyclicBarrier mở8worker; download-only có8writer, mixed có4writer+4reader. Windows11/JDK21.0.8, filesystem thật; payload128KiB MOCK.

| Lượt mới | Mode | Round × thread × operation | OK | Lỗi | Final hash hợp lệ / partial còn |
|---|---|---|---:|---:|---|
| initial | download-only | 5×8×8=320 | 145 | **175** | 5/5 / 0 |
| initial | mixed read/download | 5×8×8=320 | 170 | **150** | 5/5 / 0 |
| confirmation, cây TEST mới | download-only | 5×8×8=320 | 105 | **215** | 5/5 / 0 |
| confirmation, cây TEST mới | mixed read/download | 5×8×8=320 | 175 | **145** | 5/5 / 0 |

5seed download và5final verify mỗi batch không nằm trong320. Không diễn giải tần suất stress thành tần suất production. Collector thu lỗi và exit0 để in đầy đủ; outcome là **FAIL** vì errors>0, không phải PASS process. [REPRODUCERS.md](REPRODUCERS.md) có source và lệnh đúng probe đã chạy; JSON có error counts từng type và stack locations đã lọc.

Confirmation: AccessDenied/FileAlreadyExists/NoSuchFile ở `download:97`; kiểm `file:43` cũng có NoSuchFile giữa exists/realpath, `unsafe:77` có thể báo lỗi trong lúc target đổi, `verify:108` có missing/size error. **Ảnh hưởng:** download/preparation hợp lệ bị lỗi, reader có thể không đọc được cache trong cửa sổ replacement; có thể chặn chuẩn bị/READY. Các kiểm cuối còn nguyên hash và0partial, chưa chứng minh file hỏng được READY, ghi vượt root hoặc mất answers. Không phân loại P0/data loss từ evidence này.

Root cause được đối chiếu source JDK cục bộ (hash trong manifest): WindowsFileCopy.move nhánh không atomic đọc thuộc tính, **DeleteFile target rồi MoveFileEx(...,0)**. Các instance có thể quan sát target mất/đổi và tranh nhau replace. [Java21 Files.move](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html#move(java.nio.file.Path,java.nio.file.Path,java.nio.file.CopyOption...)) chỉ cam kết atomic khi yêu cầu ATOMIC_MOVE; REPLACE_EXISTING riêng không đủ cho claim atomic publication. Đây là rủi ro đồng thời thông thường, khác TOCTOU attacker đã được loại khỏi threat model.

**Corrective recommendation cho B, PR riêng:** bổ sung regression concurrent publication + reader trên Windows, giữ các test junction/directory race; sửa tối thiểu protocol publication/coordination để mọi reader thấy target hợp lệ ổn định. Cần kiểm semantics atomic replace trên filesystem hỗ trợ, valid-existing-cache reuse và phối hợp các instance/process; không chỉ bọc synchronized từng AudioFiles hoặc retry mọi IOException. Chính sách fallback khi không hỗ trợ atomic phải fail rõ, không âm thầm nhận partial. Chạy lại reproducer với0lỗi và0partial, READY integration cùng policy root; chưa có patch hoặc khẳng định một dòng ATOMIC_MOVE sẽ giải quyết hết mọi sharing case.

### Findings P2 / residual risks

| Mã | Giới hạn và tác động |
|---|---|
| QD33-L1 | Validate rồi write/open là TOCTOU: cùng user thay directory/target sau kiểm vẫn ngoài mô hình đồ án. Không claim sandbox filesystem tuyệt đối, không thiết kế framework/ACL mới trong review |
| QD33-L2 | Không có fsync/crash-durability hoặc atomic whole-manifest transaction; từng file được kiểm trước publish, READY verify toàn manifest. Mất điện/kill giữa bước có thể cần tải lại; không biến file partial thành attestation |
| QD33-T1 | Symlink test SKIP do Windows thiếu privilege; junction root/child/ancestor và8.3 PASS thật. Không bật Developer Mode/elevation/đổi ACL để làm test PASS |
| QD33-L3 | Cache default nằm user data; override có thể được operator trỏ tới vị trí khác, quyền file kế thừa directory/user. Không có lượt MSI hoặc permission/installed-directory regression mới; không nhận guard tuyệt đối “mọi cấu hình đều không ghi vào MSI” |

### PR45 provenance và verdict

PR45 chỉ đổi AudioFiles và workflow. Workflow Windows `c1e3405678740e292747dd67951e29840e716a53` đã vào main qua `d562581c03c5513e97c5a087d568a87332c3f383`; patch-id đều `475510ffc80bb21224678f590831197431cef613`. Audio replacement `d53122135485f5a27015d4d03d196ea2c9bd03a5` có root/ancestor policy cộng catch concurrent create mà PR45 thiếu; AudioFiles/AudioFilesTest hiện giống blob ở stabilization source3702b6d. Không có phần patch PR45 độc lập cần merge lại; merge a652482 không chữa QD33-P1 vì download publication không được sửa ở đó.

**Technical verdict: REQUEST_CHANGES** vì QD33-P1. B đã có evidence để review, **chưa đủ để ký chấp nhận cache concurrency hiện tại**. PR45 superseded về provenance: **YES về kỹ thuật, PENDING B** về cleanup. B có thể đồng ý đóng PR45 như patch cũ và theo dõi P1 trong corrective PR riêng; việc đó không phải QD33 owner acceptance/P1 resolved. Task này giữ PR45 OPEN, không merge/close/comment thay B. A cần xác nhận workflow Windows/current-head CI và phạm vi opt-in bị skip, không lấy CI default thay PG/manual.

## 3. QD-34 — takeover semantics

Contract hiện tại: mỗi takeover hợp lệ dưới row lock có thể cấp epoch mới dù requestId lặp. `writerEpoch` cấp quyền ghi answers; `syncEpoch` là phiên monitoring OPEN/FULL, reconnect WS không tự takeover quyền ghi. Không mở rộng idempotency hay schema trong nhiệm vụ này.

| Source/method | Đối chiếu |
|---|---|
| [ExamController](../../server/src/main/java/vn/edu/toeic/server/exam/ExamController.java) `takeoverWriter` | Principal bắt buộc, URL/body attempt phải khớp; service proxy hoàn tất transaction trước response controller |
| [ExamService](../../server/src/main/java/vn/edu/toeic/server/exam/ExamService.java) `takeoverWriter` L1016–1120 | @Transactional; roleCANDIDATE, shape; SELECT FOR UPDATE OF a; ownership rồi ACTIVE/compatibility/exam trước mutation; clock_timestamp sau lock; chỉ initialize deadline nếu null, expired từ chối |
| cùng method | writerEpoch+1; answers/savedRevision không UPDATE; trả map và revision đã commit; không tra dedup requestId. Nếu Listening đã start/chưa complete, takeover ghi interrupted để không resume; không sửa deadline hoặc bài final |
| [ExamEntry](../../client/src/main/java/vn/edu/toeic/client/exam/ExamEntry.java) `open`, `claimWriter` | Status final trả Finished không takeover; kiểm paper trước claim; mỗi open tạo UUID request mới; IOException thất bại, không lặp POST. INVALID_STATE chỉ đọc lại status một lần |
| [ExamSession](../../client/src/main/java/vn/edu/toeic/client/exam/ExamSession.java) `reclaimWriter`, `onTakeoverResult` L675–711 | Chỉ explicit reclaim khi WRITER_REPLACED; chặn hai takeover in-flight; failure hiện bấm lại, không schedule retry; cấp lại thì nhận authoritative answers/revision/deadline và bỏ pending writer cũ |
| [ExamApiClient](../../client/src/main/java/vn/edu/toeic/client/exam/ExamApiClient.java) `takeoverWriter`, `post`, `sendRequest` | Một sendAsync, không retry loop; khác autosave/submit retry có payload đóng băng. Probe production client qua HTTP đọc POST rồi đóng trước response có đúng1request; lời gọi chủ động mới tạo request thứ2 |
| [CandidateExamView](../../client/src/main/java/vn/edu/toeic/client/exam/CandidateExamView.java), AudioPreparation | Shared ExamSession writer cho Reading/Listening, audio không xin epoch thứ2; status/reconnect không replay audio. Source đối chiếu và unit tests, không GUI manual mới |

### Evidence và trade-off

- **PG/HTTP thật:** `takeoverReplayAfterLostAckAdvancesEpochButNeverLosesAnswersDeadlineOrFinal` replay cùng request sau commit, epoch+1, old writer409STALE, answers/revision/deadline giữ; writer mới save/submit được; replay sauSUBMITTED409INVALID_STATE và result bất biến. Mất ACK trong case này là SIMULATED replay sau commit, không cable loss.
- `concurrentFirstEntriesReconnectAndTakeoverKeepOneDeadline`: hai request latch cùng khởi phát có epoch2/3 và chung deadline; reopenepoch4 vẫn deadline cũ, writer2 stale; foreign candidate403 trước tạo deadline. `unansweredAttemptTimesOutByAutonomousRuntimeScheduler`: timeout thật, takeover sauTIMED_OUT bị409, không mở lại. Ba case state read/scope bổ sung là tests đọc, không gọi là exhaustive takeover401/403 matrix.
- Client `lostTakeoverAckIsNeverRetriedAutomaticallyAndReopenUsesAFreshRequestId` và ExamSessionTest chạy mới với gatewayMOCK; read/final/stale/reclaim/no-replay/sharedWriter được kiểm. [HTTP probe](REPRODUCERS.md) bổ sung production HttpClient trên transport loopback thật, **commitMOCK**, faultSIMULATED; không thay PG commit test.
- Không mất **answers đã ACK** hoặc reset savedRevision/deadline trong các case đã thử. Local edits chưa ACK của writer bị thay có thể bị bỏ khi người dùng chọn nhận writer mới; đó là boundary contract, không nhận là server làm mất dữ liệu đã lưu.
- Có thể vô hiệu hóa writer đang hợp lệ khi người dùng takeover lại, kể cả gửi lại requestId. Đây là tác dụng có chủ đích của việc cấp writer mới; A/B phải chấp nhận UX nhiều cửa sổ/máy, không gọi là idempotent. Nếu cùngwriter reconnect thì chỉ đối chiếu status, không tự claim.
- [Java21 networking properties](https://docs.oracle.com/en/java/javase/21/core/java-networking.html) mặc định `jdk.httpclient.enableAllMethodRetry=false`; có retry connection failures ở tầng transport. Vì vậy claim kiểm được là **không tự replay POST đã đọc/mất response trong cấu hình đã thử**, không phải cấm mọi connection retry trong JDK. Không bật retry non-idempotent qua JVM options nếu chưa review contract.

**P0/P1 QD34: không phát hiện mới. P2:** không exactly-once; ambiguous response cần thao tác rõ ràng; backend không chống replay theo requestId. Nếu cần idempotency thật, A/B phải thiết kế durable request key theo attempt/user/requestId, payload fingerprint, epoch/result đã cấp và retention; auth/scope/final checks trước cached response, transaction cùng row lock, policy khi replay sau takeover khác. Cần schema/migration/contract/client regression riêng; không thêm trong task này.

**Technical verdict: ACCEPT_WITH_LIMITATIONS.** Đủ evidence kỹ thuật trong scope để A/B đọc và tự ký hoặc request bổ sung; không cần production change QD34 từ findings hiện tại. Owner approval vẫn **PENDING**. Không lấy verdict này thay nghiệm thu GUI/máy2/LAN hoặc release readiness.

## 4. Test matrix và cleanup

| Phạm vi mới | Kết quả | Nhãn / giới hạn |
|---|---|---|
| AudioFilesTest |22PASS /1SKIP | REAL Windows; unsafe-name10parameters, directory readers128calls, mixed file/verify640calls, junction/8.3, corrupt/size/cancel/partial; audioMOCK |
| AudioPreparation10, AudioApiClient5, ListeningPlayback8 |23PASS | Worker/file thật, gateway/HTTPfixture/media/hearing MOCK/SIMULATED; retry giữwriter/sharedwriter, thiếu/sai byte khôngREADY |
| ExamPaperAndEntry10, ExamApiClient28, ExamSession51 |89PASS | Entry/HTTPfixture/clock/gatewayMOCK; không tự replay takeover, server-state adoption, close/stale/final |
| AudioRuntimePostgresTest4 |4PASS | REAL Spring/HTTP/PG/files; corrupt/missing/reload/withdraw/takeover-readiness và commit-failure; speaker/faultSIMULATED |
| ExamRuntimePostgresTest6 |6PASS | REAL PG/HTTP/rowlock/scheduler; replay/concurrent entry/timeout và3state read-scope |
| Tổng JUnit |**144PASS /0FAIL /0ERROR /1SKIP** |145case,10PGsubset; không cộng probe/stress iterations thành JUnit |
| Concurrent publication probe |**4batch FAIL**, mỗi320counted operations | Hai lượt/cây mới, không inject filesystem fault; finalhash5/5 mỗibatch/0partial không phủ nhận transient race |
| HTTP lost-response probe |**1PASS** | Production ExamApiClient, REAL loopback; servercommitMOCK/response-lossSIMULATED |

Môi trường Windows11, OracleJDK21.0.8/Maven3.9.11/PG18.1; TEMP thuộc lượt trên system volume để chạy8.3 thật. Symlink SKIP đúng thiếu privilege, không tự đổiACL/DeveloperMode. Không có MOCK filesystem. Không chạy Maven full suite/package/MSI/LAN hoặc nghe thật; không nhận 924/926 hay0/640 historical là test mới.

Cluster PostgreSQL TEST mới chỉ bindloopback, từng suite UUIDschema, **0schema còn lại**, cluster đã dừng/status3. Cây TEMP sở hữu đã xóa,0reparse trước xóa. Wrapper pg_ctl ban đầu chờ handle tiến trình con đã dừng rồi xác minh cluster/resume; chưa có test ở wrapper đầu. Cleanup PowerShell5 gặp long-path, đã hoàn tất bằng JavaNIO chỉ trên cây TEST có guard. Log lỗi setup/cleanup giữ private; không reset sharedDB/Docker hoặc sửa.env. [environment-cleanup.json](environment-cleanup.json) ghi hậu kiểm thật.

## 5. Comment đề xuất và việc owner làm tiếp

Các comment sau **chưa đăng, chưa có chữ ký người thật**.

**B — QD33:**

> Tôi đã đọc packet trên mainf864034. QD33-P1 concurrent publication tái hiện hai lượt Windows; tôi [yêu cầu corrective PR / đề xuất phạm vi xử lý] và chưa ký chấp nhận concurrency hiện tại. Tôi [chấp nhận/không chấp nhận] policy ancestor/root và residual TOCTOU ngoài threat model; symlink privilege SKIP [đủ/chưa đủ]. Tôi [đồng ý/không đồng ý] PR45 là superseded về provenance; nếu đóng sẽ gắn corrective riêng cho P1, không ghi QD33 OWNER_APPROVED khi lỗi chưa xử lý.

**A — CI/QD34; B — client/QD34:**

> Tôi chấp nhận/request changes semantics takeover không idempotent theo requestId: một takeover mới có thể làm writer cũ STALE; answers đã commit, revision/deadline/final được bảo toàn trong tests. Tôi đã đọc giới hạn local edits chưa ACK, lost response và default HTTP retry; evidence [đủ/cần thêm regression …] trước ký. A xác nhận thêm workflowWindows/CI và PG scope, B xác nhận explicit reopen/reclaim/sharedWriter UX. Đây là chữ ký của [A/B], không phải agent.

Việc còn lại: **B** mở corrective PR nhỏ cho QD33-P1 với regression/reproducer trước-sau; **A/B** review QD34 và residual risks rồi ký nếu đồng ý; **A** kiểm CI Windows/current head, phân biệt opt-in; **B** quyết định superseded45 sau đọc findings, task này không cleanup PR. Không sửa production trong packet, không đổi protocol/tracker/QD approval hoặc PR47/51/52/53. Không cần thiết kế lại toàn bộ filesystem hoặc thêm idempotency feature để hoàn tất review.
