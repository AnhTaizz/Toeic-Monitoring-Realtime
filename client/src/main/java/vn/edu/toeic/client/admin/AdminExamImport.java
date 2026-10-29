package vn.edu.toeic.client.admin;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.time.*;
import java.util.Comparator;
import java.util.zip.*;
import vn.edu.toeic.tools.study4.Study4Adapter;

/** Local conversion owns only its temporary workspace, never changes the selected crawl or its media. */
public final class AdminExamImport implements AutoCloseable {
    private final Path temporary;
    public final String json, filename;
    public final Path bundle;
    private AdminExamImport(Path temporary,String json,String filename,Path bundle) {
        this.temporary=temporary;this.json=json;this.filename=filename;this.bundle=bundle;
    }
    public static AdminExamImport load(Path file) throws Exception {
        String text;
        try(var input=Files.newInputStream(file)) {
            byte[] bytes=input.readNBytes(AdminExamApiClient.MAX_BYTES+1);
            if(bytes.length>AdminExamApiClient.MAX_BYTES)throw new IllegalArgumentException("JSON vượt quá 2 MiB.");
            text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        }
        if(text.startsWith("\uFEFF"))text=text.substring(1);
        JsonObject root=parse(text);
        if(!root.has("items"))return new AdminExamImport(null,text,file.getFileName().toString(),null);
        Path temp=Files.createTempDirectory("toeic-crawl-import-");
        try {
            Path converted=temp.resolve("converted");
            new Study4Adapter().convertServerFile(file,converted,LocalDate.now(ZoneId.of("Asia/Bangkok")));
            String normalized=Files.readString(converted.resolve("rich-import.json"),StandardCharsets.UTF_8);
            parse(normalized);
            Path zip=temp.resolve("upload.zip");
            try(var out=new ZipOutputStream(Files.newOutputStream(zip));var paths=Files.walk(converted)) {
                for(Path path:paths.filter(Files::isRegularFile).toList()) {
                    String entry=converted.relativize(path).toString().replace('\\','/');if(entry.equals("manifest.json"))continue;
                    out.putNextEntry(new ZipEntry(entry));Files.copy(path,out);out.closeEntry();
                }
            }
            return new AdminExamImport(temp,normalized,file.getFileName().toString(),zip);
        } catch(Exception failure) {cleanup(temp);throw new IllegalArgumentException("Không chuyển được đề STUDY4: "+failure.getMessage()+". Giữ nguyên thư mục media cạnh server_exam.json và kiểm tra đáp án đủ 200 câu.",failure);}
    }
    public static JsonObject parse(String text) {
        try {
            var json=new GsonBuilder().setStrictness(Strictness.STRICT).create().fromJson(text,JsonObject.class);
            if(json==null)throw new IllegalArgumentException("Tệp JSON phải chứa một đề thi.");
            if(json.has("items")) {
                if(!json.has("id") || !json.has("title"))throw new IllegalArgumentException("JSON crawl thiếu id hoặc title.");return json;
            }
            var metadata=json.has("exam")?json.getAsJsonObject("exam"):json;
            if(!metadata.has("examId") || metadata.get("examId").getAsString().isBlank() || !metadata.has("title")
                    || metadata.get("title").getAsString().isBlank() || !json.has("questions") || json.getAsJsonArray("questions").isEmpty())throw new IllegalArgumentException("JSON thiếu mã đề, tên đề hoặc danh sách câu hỏi.");
            if(json.has("schemaVersion") && (!json.get("schemaVersion").toString().equals("2") || !json.has("exam")))
                throw new IllegalArgumentException("JSON này chưa phải định dạng import v2. Chọn server_exam.json hoặc rich-import.json.");
            return json;
        }catch(JsonParseException|IllegalStateException|NullPointerException failure){throw new IllegalArgumentException("JSON không hợp lệ hoặc thiếu mã đề, tên đề, câu hỏi.",failure);}
    }
    public Path bundle(String payload) throws IOException {
        if(bundle==null)return null;
        Path updated=temporary.resolve("upload-edited.zip");
        try(var input=new ZipInputStream(Files.newInputStream(bundle));var output=new ZipOutputStream(Files.newOutputStream(updated))) {
            for(ZipEntry entry;(entry=input.getNextEntry())!=null;) {
                output.putNextEntry(new ZipEntry(entry.getName()));
                if(entry.getName().equals("rich-import.json"))output.write(payload.getBytes(StandardCharsets.UTF_8));
                else input.transferTo(output);output.closeEntry();
            }
        }
        return updated;
    }
    @Override public void close(){if(temporary!=null)cleanup(temporary);}
    private static void cleanup(Path directory) {
        try(var paths=Files.walk(directory)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
        catch(IOException failure){directory.toFile().deleteOnExit();}
    }
}
