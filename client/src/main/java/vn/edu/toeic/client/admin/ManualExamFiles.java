package vn.edu.toeic.client.admin;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** A portable draft and the local audio selected by its author. No absolute paths enter JSON. */
public final class ManualExamFiles implements AutoCloseable {
    private final Map<String,Path> audio=new LinkedHashMap<>();
    private final Path temporary;
    public ManualExamFiles() throws IOException {temporary=Files.createTempDirectory("toeic-manual-");}
    public void select(JsonObject question,Path file) {
        String name=file.getFileName().toString().toLowerCase(Locale.ROOT);
        if(!name.matches(".*\\.(mp3|wav|m4a)"))throw new IllegalArgumentException("Chọn âm thanh MP3, WAV hoặc M4A.");
        audio.put(question.get("questionId").getAsString(),file);
        question.addProperty("audioFile",file.getFileName().toString());
    }
    public void remove(JsonObject question){audio.remove(question.get("questionId").getAsString());question.addProperty("audioFile","");}
    public boolean hasAudio(){return !audio.isEmpty();}
    /** Hash and copy off the FX thread; filenames cannot collide or contain local path components. */
    public Path bundle(JsonObject source) throws IOException {
        var document=source.deepCopy();var files=new LinkedHashMap<String,Path>();long total=0;
        for(var item:document.getAsJsonArray("questions")) {
            var q=item.getAsJsonObject();Path file=audio.get(q.get("questionId").getAsString());if(file==null)continue;
            String original=file.getFileName().toString();String extension=original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT);
            byte[] bytes;
            try(var in=Files.newInputStream(file)){bytes=in.readNBytes(64*1024*1024+1);}
            if(bytes.length==0||bytes.length>64*1024*1024)throw new IOException("Mỗi tệp âm thanh phải từ 1 byte đến 64 MiB.");
            String name;
            try{name=String.format(Locale.ROOT,"%03d-",q.get("orderIndex").getAsInt())+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))+extension;}
            catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
            total+=bytes.length;if(total>256L*1024*1024)throw new IOException("Tổng âm thanh vượt 256 MiB.");
            Path snapshot=temporary.resolve(name);Files.write(snapshot,bytes);files.put(name,snapshot);q.addProperty("audioFile",name);
        }
        Path zip=Files.createTempFile(temporary,"draft-",".zip");
        try(var out=new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("manual-draft.json"));out.write(document.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));out.closeEntry();
            for(var entry:files.entrySet()){out.putNextEntry(new ZipEntry("audio/"+entry.getKey()));Files.copy(entry.getValue(),out);out.closeEntry();}
        }
        return zip;
    }
    public JsonObject open(Path zip) throws IOException {
        audio.clear();JsonObject document=null;var files=new HashMap<String,Path>();long total=0;
        try(var input=new ZipInputStream(Files.newInputStream(zip))) {
            Set<String> seen=new HashSet<>();
            for(ZipEntry entry;(entry=input.getNextEntry())!=null;) {
                String name=entry.getName();
                if(!seen.add(name)||seen.size()>101||!(name.equals("manual-draft.json")||name.matches("audio/[0-9]{3}-[a-f0-9]{64}\\.(mp3|wav|m4a)")))throw new IOException("Gói nháp không hợp lệ.");
                int limit=name.equals("manual-draft.json")?AdminExamApiClient.MAX_BYTES:64*1024*1024;
                byte[] bytes=input.readNBytes(limit+1);total+=bytes.length;
                if(bytes.length>limit||total>258L*1024*1024)throw new IOException("Gói nháp vượt giới hạn dung lượng.");
                if(name.equals("manual-draft.json"))document=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                else {Path file=temporary.resolve(name.substring(6));Files.write(file,bytes);files.put(name.substring(6),file);}
            }
        }
        if(document==null)throw new IOException("Gói nháp thiếu nội dung đề.");
        validateDocument(document);
        for(var item:document.getAsJsonArray("questions")) {
            var q=item.getAsJsonObject();String name=q.has("audioFile")&&!q.get("audioFile").isJsonNull()?q.get("audioFile").getAsString():"";
            if(files.containsKey(name))audio.put(q.get("questionId").getAsString(),files.get(name));
        }
        return document;
    }
    private static void validateDocument(JsonObject document) throws IOException {
        try {
            var draft=new Gson().fromJson(document,vn.edu.toeic.protocol.admin.ManualExamDraft.class);
            if(draft.examId()==null||draft.title()==null||draft.revision()<0||draft.questions()==null||draft.questions().size()!=200)
                throw new IllegalArgumentException();
            for(int i=0;i<200;i++) {
                var q=draft.questions().get(i);int part=vn.edu.toeic.protocol.admin.ManualExamDraft.partFor(i+1);
                if(q==null||!Objects.equals(q.questionId(),"q"+(i+1))||q.orderIndex()!=i+1||q.part()!=part
                    ||q.options()==null||q.options().size()!=(part==2?3:4))throw new IllegalArgumentException();
                for(int n=0;n<q.options().size();n++)if(q.options().get(n)==null||!Objects.equals(q.options().get(n).optionId(),""+(char)('A'+n)))throw new IllegalArgumentException();
            }
        }catch(RuntimeException bad){throw new IOException("Bản nháp phải giữ đúng khung 200 câu TOEIC.",bad);}
    }
    @Override public void close() throws IOException {
        try(var paths=Files.walk(temporary)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
    }
}
