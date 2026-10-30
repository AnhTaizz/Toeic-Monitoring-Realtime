package vn.edu.toeic.client.admin;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import vn.edu.toeic.protocol.admin.ManualExamDraft;

/** Selected files packaged for one server save; the ZIP is an internal transport detail. */
public final class ManualExamFiles implements AutoCloseable {
    private final Map<String,Path> audio=new LinkedHashMap<>();
    private final Map<String,List<Path>> images=new LinkedHashMap<>();
    private final Path temporary=Files.createTempDirectory("toeic-manual-media-");
    public ManualExamFiles() throws IOException {}
    public void select(JsonObject question,Path file) {
        if(!extension(file).matches("mp3|wav|m4a"))throw new IllegalArgumentException("Chọn âm thanh MP3, WAV hoặc M4A.");
        audio.put(question.get("questionId").getAsString(),file);question.addProperty("audioFile",file.getFileName().toString());
    }
    public void remove(JsonObject question){audio.remove(question.get("questionId").getAsString());question.addProperty("audioFile","");}
    public void selectImages(JsonObject question,List<Path> files) {
        if(files.size()>10||question.get("part").getAsInt()==1&&files.size()!=1)throw new IllegalArgumentException("Part 1 chọn một ảnh; các Part khác tối đa 10 ảnh mỗi câu.");
        for(Path file:files)if(!extension(file).matches("png|jpg|jpeg"))throw new IllegalArgumentException("Chọn ảnh PNG hoặc JPEG.");
        images.put(question.get("questionId").getAsString(),List.copyOf(files));
        var names=new JsonArray();for(Path file:files)names.add(file.getFileName().toString());question.add("imageFiles",names);
    }
    public void removeImages(JsonObject question){images.remove(question.get("questionId").getAsString());question.add("imageFiles",new JsonArray());}
    public List<Path> selectedImages(JsonObject question){return images.getOrDefault(question.get("questionId").getAsString(),List.of());}
    public boolean hasFiles(){return !audio.isEmpty()||!images.isEmpty();}
    public Path bundle(JsonObject source) throws IOException {
        var document=source.deepCopy();var files=new LinkedHashMap<String,Path>();long[] totals={0,0};int[] counts={0,0};
        for(var item:document.getAsJsonArray("questions")) {
            var q=item.getAsJsonObject();String id=q.get("questionId").getAsString();int number=q.get("orderIndex").getAsInt();
            if(audio.containsKey(id))q.addProperty("audioFile",snapshot(audio.get(id),ManualExamDraft.groupStart(number),false,files,totals,counts));
            if(images.containsKey(id)) {
                var names=new JsonArray();for(Path file:images.get(id))names.add(snapshot(file,number,true,files,totals,counts));q.add("imageFiles",names);
            }
        }
        Path zip=Files.createTempFile(temporary,"upload-",".zip");
        try(var out=new ZipOutputStream(Files.newOutputStream(zip))) {
            byte[] bytes=document.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if(bytes.length>AdminExamApiClient.MAX_BYTES)throw new IOException("Nội dung đề vượt 2 MiB.");
            out.putNextEntry(new ZipEntry("manual-draft.json"));out.write(bytes);out.closeEntry();
            for(var entry:files.entrySet()){out.putNextEntry(new ZipEntry(entry.getKey()));Files.copy(entry.getValue(),out);out.closeEntry();}
        }
        return zip;
    }
    private String snapshot(Path file,int ordinal,boolean image,Map<String,Path> files,long[] totals,int[] counts) throws IOException {
        int limit=(image?8:64)*1024*1024,index=image?1:0;byte[] bytes;
        try(var in=Files.newInputStream(file)){bytes=in.readNBytes(limit+1);}
        if(bytes.length==0||bytes.length>limit)throw new IOException(image?"Mỗi ảnh tối đa 8 MiB.":"Mỗi tệp âm thanh tối đa 64 MiB.");
        String name;
        try{name=String.format(Locale.ROOT,"%03d-",ordinal)+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))+"."+extension(file);}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        String entry=(image?"images/":"audio/")+name;
        if(!files.containsKey(entry)) {
            totals[index]+=bytes.length;counts[index]++;
            if(counts[index]>100||totals[index]>(image?64L:256L)*1024*1024)throw new IOException("Media vượt giới hạn dung lượng hoặc số tệp của đề.");
            Path snapshot=temporary.resolve(name);Files.write(snapshot,bytes);files.put(entry,snapshot);
        }
        return name;
    }
    private static String extension(Path file){String name=file.getFileName().toString();return name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);}
    @Override public void close() throws IOException {try(var paths=Files.walk(temporary)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
}
