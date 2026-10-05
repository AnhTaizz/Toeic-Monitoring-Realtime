# Audio TEST cho T1-B4

Hai file ở đây là fixture **TEST**, không phải nội dung đề thi và không được đóng vào JAR production.

| File | Nội dung | Kích thước |
|---|---|---:|
| `TEST-tone-440hz.mp3` | Tone hình sin 440 Hz, 2 giây, MP3 96 kbps 44,1 kHz stereo | 25.158 byte |
| `TEST-tone-440hz.m4a` | Cùng tone, AAC trong MP4 96 kbps 44,1 kHz stereo | 25.593 byte |

Nguồn: tone WAV PCM do `ToneWav` trong test source sinh ra (`AudioSmokeHarness --write-tone=<file>`), rồi chuyển mã bằng bộ mã hóa có sẵn của Windows 11 (`Windows.Media.Transcoding.MediaTranscoder`, profile `CreateMp3`/`CreateM4a` mức `Low`). Không có nội dung bản quyền.

File WAV không commit vì harness tự sinh được. `scripts/smoke-b4.ps1` dùng ba định dạng này để thử JavaFX Media trong app-image.
