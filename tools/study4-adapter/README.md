# STUDY4 internal dataset adapter

Công cụ Java offline chuyển thư mục `STUDY4_6852` do người dùng cung cấp sang `RichExamImportRequest` V2. Công cụ không tải mạng và không chứa/commit đề hoặc media.

```powershell
mvn -pl tools/study4-adapter -am package
java -cp "tools/study4-adapter/target/classes;protocol/target/classes;$env:USERPROFILE/.m2/repository/com/google/code/gson/gson/2.13.2/gson-2.13.2.jar" `
  vn.edu.toeic.tools.study4.Study4Adapter `
  --input target/study4-input/crawled-exams/STUDY4_6852 `
  --output target/study4-v2 `
  --permission-date 2026-10-22
```

Output gồm `rich-import.json`, `manifest.json`, `audio/<examId>/` và `images/<examId>/`. `target/` đã bị Git ignore. Chỉ dùng cho nội bộ theo xác nhận của người cung cấp; không phát hành lại nội dung bên thứ ba.

Desktop Admin dùng `convertServerFile` để đọc riêng `server_exam.json`, không cần client export. Các ảnh WebP (kể cả mang đuôi `.jpg`) được đọc bằng [TwelveMonkeys ImageIO](https://github.com/haraldk/TwelveMonkeys) rồi chuyển sang PNG trong thư mục staging; ảnh crawl gốc không thay đổi. Client shaded JAR cần `ServicesResourceTransformer` để giữ đăng ký ImageIO SPI.
