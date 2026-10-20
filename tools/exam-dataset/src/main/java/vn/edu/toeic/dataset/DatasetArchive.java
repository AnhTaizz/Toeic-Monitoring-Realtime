package vn.edu.toeic.dataset;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static vn.edu.toeic.dataset.Json.*;

/** Archive thư mục tự chứa, không HTTP/DB; chỉ restore sang thư mục mới và không vượt Git worktree. */
public final class DatasetArchive {
    public static void main(String[] args)throws Exception {
        if(args.length<1)throw new IllegalArgumentException("create|verify|restore --root REPO --datasets DIR --audio-root DIR --image-root DIR --destination NEW_EXTERNAL_DIR --archive DIR");
        Map<String,String> f=new HashMap<>();for(int i=1;i<args.length;i+=2){if(i+1==args.length||f.put(args[i],args[i+1])!=null)throw new IllegalArgumentException("Invalid flags");}
        var tool=new DatasetArchive();
        switch(args[0]) {
            case "create" -> tool.create(Path.of(f.get("--root")),Path.of(f.get("--datasets")),Path.of(f.get("--audio-root")),Path.of(f.get("--image-root")),Path.of(f.get("--destination")),f.getOrDefault("--source-commit","UNRECORDED"));
            case "verify" -> tool.verify(Path.of(f.get("--archive")));
            case "restore" -> tool.restore(Path.of(f.get("--archive")),Path.of(f.get("--destination")));
            default -> throw new IllegalArgumentException("Unknown archive command");
        }
    }
    public void create(Path repo,Path datasets,Path audio,Path images,Path destination,String commit)throws IOException {
        destination=destination.toAbsolutePath().normalize();
        repo=repo.toRealPath();datasets=datasets.toRealPath();assertExternalNew(destination,repo);
        var planned=new LinkedHashMap<String,Path>();var datasetRows=new JsonArray();int mediaCount=0;
        try(var dirs=Files.list(datasets)) {
            for(Path directory:dirs.filter(Files::isDirectory).sorted().toList()) {
                Path normalized=directory.resolve("normalized.json"),draft=directory.resolve("draft.v2.json");
                if(!Files.isRegularFile(normalized)||!Files.isRegularFile(draft))continue;
                var d=read(normalized);String id=str(d.getAsJsonObject("exam"),"examId");if(!safeFile(id))throw new IOException("Unsafe archived examId");
                JsonObject request=read(draft);if(num(request,"schemaVersion")!=2||!id.equals(str(request.getAsJsonObject("exam"),"examId")))throw new IOException("Draft identity/schema mismatch");
                addJsonTree(planned,directory,"datasets/"+directory.getFileName());
                var source=d.getAsJsonObject("source");Path sourcePath=repo.resolve(str(source,"path")).normalize();
                if(!sourcePath.startsWith(repo))throw new IOException("Unsafe source provenance path");
                if(Files.exists(sourcePath)) {if(!hash(sourcePath).equals(str(source,"sha256")))throw new IOException("Source checksum mismatch");plan(planned,"sources/"+sourcePath.getFileName(),sourcePath);}
                Set<String> ids=new HashSet<>();
                for(var value:d.getAsJsonArray("media")) {
                    var m=value.getAsJsonObject();String name=str(m,"localFile"),mid=str(m,"mediaId"),type=str(m,"mediaType");
                    if(!safeFile(name)||!safeFile(mid)||!ids.add(mid)||!Set.of("AUDIO","IMAGE").contains(type))throw new IOException("Unsafe/duplicate media");
                    Path p=(type.equals("AUDIO")?audio:images).resolve(id).resolve(name);regular(p);
                    if(m.get("sizeBytes").isJsonNull()||Files.size(p)!=m.get("sizeBytes").getAsLong()||!hash(p).equals(str(m,"sha256")))throw new IOException("Media size/checksum mismatch: "+id+"/"+name);
                    plan(planned,(type.equals("AUDIO")?"audio-resources/":"image-resources/")+id+"/"+name,p);mediaCount++;
                }
                datasetRows.add(obj("examId",id,"sourceSha256",str(source,"sha256"),"questions",d.getAsJsonArray("questions").size(),"media",d.getAsJsonArray("media").size(),
                        "verificationState","PRESERVED_FROM_JSON","importReady",false));
            }
        }
        if(datasetRows.isEmpty())throw new IOException("No normalized/draft pairs to archive");
        Path recovered=repo.resolve("tools/exam-dataset/target/recovery/ets2026_test01");if(Files.isDirectory(recovered))addJsonTree(planned,recovered,"recovery/ets2026_test01");
        Path receipts=repo.resolve("tools/exam-dataset/target/media-receipts");if(Files.isDirectory(receipts))addJsonTree(planned,receipts,"provenance/media-receipts");
        for(String report:List.of("source-audit.json","batch-report.json"))if(Files.exists(datasets.resolve(report)))plan(planned,"datasets/"+report,datasets.resolve(report));
        Path permission=repo.resolve("docs/ex01-html-pipeline/media-permission.json");if(Files.exists(permission))plan(planned,"provenance/media-permission.json",permission);
        Path manifest=repo.resolve("data/seedingTest/manifest.json");if(Files.exists(manifest))plan(planned,"provenance/source-manifest.json",manifest);
        // Test05 duplicate cũng được giữ raw nếu còn; không tạo thêm exam/media.
        Path duplicate=repo.resolve("data/seedingTest/ets2026_test05.html");if(Files.exists(duplicate))plan(planned,"sources/ets2026_test05.html",duplicate);
        Files.createDirectories(destination.getParent());Files.createDirectory(destination);var entries=new JsonArray();
        for(var p:planned.entrySet()) {
            Path target=safeResolve(destination,p.getKey());Files.createDirectories(target.getParent());regular(p.getValue());
            String before=hash(p.getValue());Files.copy(p.getValue(),target);
            if(!before.equals(hash(target))||!before.equals(hash(p.getValue())))throw new IOException("File changed during backup: "+p.getKey());
            entries.add(obj("path",p.getKey(),"sizeBytes",Files.size(target),"sha256",before));
        }
        write(destination.resolve("archive-manifest.json"),obj("formatVersion",1,"createdAt",Instant.now().toString(),"sourceCommit",commit,
                "scope","CAPTURED_DATA_ONLY_NOT_IMPORT_ACCEPTANCE","datasets",datasetRows,"mediaAssetCount",mediaCount,"files",entries));
        Files.writeString(destination.resolve("archive-manifest.sha256"),hash(destination.resolve("archive-manifest.json"))+"\n");
        verify(destination);
    }
    public JsonObject verify(Path archive)throws IOException {
        regular(archive.resolve("archive-manifest.json"));
        if(!hash(archive.resolve("archive-manifest.json")).equals(Files.readString(archive.resolve("archive-manifest.sha256")).strip()))throw new IOException("Archive manifest checksum mismatch");
        var manifest=read(archive.resolve("archive-manifest.json"));if(num(manifest,"formatVersion")!=1)throw new IOException("Unsupported archive format");
        var paths=new HashSet<String>();int media=0;
        for(var e:manifest.getAsJsonArray("files")) {
            var f=e.getAsJsonObject();String relative=str(f,"path");if(!paths.add(relative.toLowerCase(Locale.ROOT)))throw new IOException("Duplicate archive path");
            Path p=safeResolve(archive,relative);regular(p);
            if(Files.size(p)!=f.get("sizeBytes").getAsLong()||!hash(p).equals(str(f,"sha256")))throw new IOException("Archive file mismatch: "+relative);
            if(relative.startsWith("audio-resources/")||relative.startsWith("image-resources/"))media++;
        }
        if(media!=num(manifest,"mediaAssetCount"))throw new IOException("Archive media completeness mismatch");
        System.out.println("ARCHIVE_VERIFY_PASS files="+paths.size()+" media="+media);return manifest;
    }
    public void restore(Path archive,Path destination)throws IOException {
        destination=destination.toAbsolutePath().normalize();
        JsonObject manifest=verify(archive);assertExternalNew(destination,archive.toRealPath());
        Files.createDirectories(destination.getParent());Files.createDirectory(destination);
        for(var e:manifest.getAsJsonArray("files")) {
            var f=e.getAsJsonObject();Path target=safeResolve(destination,str(f,"path"));Files.createDirectories(target.getParent());Files.copy(safeResolve(archive,str(f,"path")),target);
            if(!hash(target).equals(str(f,"sha256")))throw new IOException("Restore checksum mismatch");
        }
        Files.copy(archive.resolve("archive-manifest.json"),destination.resolve("archive-manifest.json"));
        Files.copy(archive.resolve("archive-manifest.sha256"),destination.resolve("archive-manifest.sha256"));verify(destination);
        System.out.println("ARCHIVE_RESTORE_PASS captured data only; import verification unchanged");
    }
    private static void addJsonTree(Map<String,Path> planned,Path from,String prefix)throws IOException {
        try(var files=Files.walk(from)) {for(Path p:files.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".json")).sorted().toList())plan(planned,prefix+"/"+from.relativize(p).toString().replace('\\','/'),p);}
    }
    private static void plan(Map<String,Path> map,String name,Path path)throws IOException {if(map.putIfAbsent(name,path)!=null)throw new IOException("Duplicate backup path");}
    static void regular(Path p)throws IOException {if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(p)||!p.toRealPath().equals(p.toAbsolutePath().normalize()))throw new IOException("Missing/unsafe regular file: "+p.getFileName());}
    static Path safeResolve(Path root,String relative)throws IOException {
        if(relative==null||relative.isBlank()||relative.contains("\\")||relative.contains(":")||relative.startsWith("/")||Arrays.stream(relative.split("/")).anyMatch(x->x.equals("..")||x.equals(".")||x.isEmpty()))throw new IOException("Unsafe archive path");
        Path p=root.resolve(relative).toAbsolutePath().normalize();if(!p.startsWith(root.toAbsolutePath().normalize()))throw new IOException("Archive traversal");return p;
    }
    private static void assertExternalNew(Path destination,Path forbidden)throws IOException {
        destination=destination.toAbsolutePath().normalize();if(Files.exists(destination,LinkOption.NOFOLLOW_LINKS)||destination.startsWith(forbidden.toAbsolutePath().normalize()))throw new IOException("Destination must be new and external");
        for(Path p=destination.getParent();p!=null;p=p.getParent())if(Files.exists(p.resolve(".git")))throw new IOException("Backup/restore must be outside Git worktrees");
    }
}
