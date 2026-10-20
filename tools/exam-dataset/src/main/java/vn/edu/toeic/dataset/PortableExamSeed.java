package vn.edu.toeic.dataset;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static vn.edu.toeic.dataset.Json.*;

/** Pack chỉ dữ liệu một đề đã import qua V2. Không dump schema/accounts hoặc tạo nội dung đề. */
public final class PortableExamSeed {
    static final Map<String,List<String>> COLUMNS = new LinkedHashMap<>();
    static {
        COLUMNS.put("exams",List.of("exam_id","title","description","total_questions","created_at","content_version"));
        COLUMNS.put("exam_sources",List.of("exam_id","source_name","source_path","source_url","copyright_notice","permission_status","permission_confirmed_on","permission_note","license_document","answer_key_status","group_mapping_status","media_validation_status","source_sha256","manifest_sha256","imported_at","option_content_status"));
        COLUMNS.put("exam_question_groups",List.of("exam_id","group_id","section","part","title","order_index","mapping_status"));
        COLUMNS.put("exam_media_assets",List.of("exam_id","media_id","media_type","source_sha256","source_url","storage_status","local_file","mime_type","size_bytes","sha256","width_px","height_px","duration_ms","alt_text"));
        COLUMNS.put("exam_questions",List.of("exam_id","question_id","section","part","group_id","passage_text","audio_file","prompt","correct_option","order_index","source_sha256","source_question_number","answer_key_status"));
        COLUMNS.put("exam_options",List.of("exam_id","question_id","option_id","option_text","order_index"));
        COLUMNS.put("exam_content_blocks",List.of("exam_id","block_id","group_id","question_id","block_type","order_index","text_content","media_id","media_type","document_id","document_order","document_title"));
    }
    public static void main(String[] args)throws Exception {
        if(args.length<1)throw new IllegalArgumentException("create|verify [--snapshot DB_JSON --audio-root DIR --image-root DIR --scripts DIR --destination NEW_EXTERNAL_DIR --pack DIR]");
        var f=new HashMap<String,String>();for(int i=1;i<args.length;i+=2)if(i+1==args.length||f.put(args[i],args[i+1])!=null)throw new IllegalArgumentException("Invalid flags");
        var tool=new PortableExamSeed();
        if(args[0].equals("create"))tool.create(Path.of(f.get("--snapshot")),Path.of(f.get("--audio-root")),Path.of(f.get("--image-root")),Path.of(f.get("--scripts")),Path.of(f.get("--destination")));
        else if(args[0].equals("verify"))tool.verify(Path.of(f.get("--pack")));
        else throw new IllegalArgumentException("Unknown seed action");
    }
    static String literal(JsonElement value) {
        if(value==null||value.isJsonNull())return "NULL";
        if(!value.isJsonPrimitive())throw new IllegalArgumentException("SQL values must be scalar");
        var p=value.getAsJsonPrimitive();
        if(p.isNumber()){String n=p.getAsString();if(!n.matches("-?[0-9]+"))throw new IllegalArgumentException("Integer required");return n;}
        if(p.isBoolean())return p.getAsBoolean()?"TRUE":"FALSE";
        return "'"+p.getAsString().replace("'","''")+"'";
    }
    static JsonObject validate(JsonObject snapshot) {
        String exam=str(snapshot,"examId");if(!safeFile(exam)||exam.length()>128)throw new IllegalArgumentException("Invalid examId");
        if(!"V2_HTTP_201_AND_SELECTABLE".equals(str(snapshot,"validation")))throw new IllegalArgumentException("Backend V2 validation receipt required");
        var tables=snapshot.getAsJsonObject("tables");if(tables==null||!tables.keySet().equals(COLUMNS.keySet()))throw new IllegalArgumentException("Only seven exam tables allowed");
        for(var entry:COLUMNS.entrySet())for(var element:tables.getAsJsonArray(entry.getKey())) {
            var row=element.getAsJsonObject();if(!row.keySet().equals(new HashSet<>(entry.getValue()))||!exam.equals(str(row,"exam_id")))throw new IllegalArgumentException("Unexpected columns or foreign exam row");
        }
        if(tables.getAsJsonArray("exams").size()!=1||num(tables.getAsJsonArray("exams").get(0).getAsJsonObject(),"content_version")!=2||num(tables.getAsJsonArray("exams").get(0).getAsJsonObject(),"total_questions")!=200||tables.getAsJsonArray("exam_questions").size()!=200)throw new IllegalArgumentException("Verified V2 200-question exam required");
        for(var v:tables.getAsJsonArray("exam_sources"))for(String status:List.of("answer_key_status","option_content_status","group_mapping_status","media_validation_status"))if(!"VERIFIED".equals(str(v.getAsJsonObject(),status)))throw new IllegalArgumentException("Unverified ETS/content cannot become a seed");
        if(tables.getAsJsonArray("exam_sources").isEmpty())throw new IllegalArgumentException("Source required");
        for(var v:tables.getAsJsonArray("exam_questions"))if(!"VERIFIED".equals(str(v.getAsJsonObject(),"answer_key_status")))throw new IllegalArgumentException("Unverified key");
        for(var v:tables.getAsJsonArray("exam_question_groups"))if(!"VERIFIED".equals(str(v.getAsJsonObject(),"mapping_status")))throw new IllegalArgumentException("Unverified group");
        for(var v:tables.getAsJsonArray("exam_media_assets")) {var m=v.getAsJsonObject();if(!"LOCAL_VERIFIED".equals(str(m,"storage_status"))||!safeFile(str(m,"local_file"))||!Set.of("AUDIO","IMAGE").contains(str(m,"media_type")))throw new IllegalArgumentException("Local verified media required");}
        if(!snapshot.has("schemaColumns")||snapshot.getAsJsonArray("schemaColumns").isEmpty())throw new IllegalArgumentException("Schema signature required");
        return tables;
    }
    static String sql(JsonObject snapshot) {
        var tables=validate(snapshot);String exam=str(snapshot,"examId"),quoted=literal(snapshot.get("examId"));
        var sql=new StringBuilder("-- PORTABLE_EXAM_SEED_V1: seven exam tables only; run in one transaction.\nSET LOCAL standard_conforming_strings = on;\nSET CONSTRAINTS ALL DEFERRED;\n");
        sql.append("SELECT pg_advisory_xact_lock(hashtextextended('portable-exam-seed:' || ").append(quoted).append(",0));\n");
        sql.append("DO $seed_guard$ BEGIN IF EXISTS(SELECT 1 FROM public.exams WHERE exam_id=").append(quoted).append(") THEN RAISE EXCEPTION 'Seed examId already exists; no overwrite'; END IF; IF NOT EXISTS(SELECT 1 FROM public.user_accounts WHERE id=current_setting('toeic.seed.proctor_id')::bigint AND role='PROCTOR' AND enabled) THEN RAISE EXCEPTION 'Target PROCTOR required'; END IF; END $seed_guard$;\n");
        for(var entry:COLUMNS.entrySet())for(var value:tables.getAsJsonArray(entry.getKey())) {
            var row=value.getAsJsonObject();var columns=new ArrayList<String>();var values=new ArrayList<String>();
            for(String column:entry.getValue()) {
                if(column.equals("source_sha256")&&!entry.getKey().equals("exam_sources")) {
                    columns.add("source_id");var sha=row.get(column);
                    values.add(sha.isJsonNull()?"NULL":"(SELECT id FROM public.exam_sources WHERE exam_id="+quoted+" AND source_sha256="+literal(sha)+")");
                } else {columns.add(column);values.add(literal(row.get(column)));}
            }
            if(entry.getKey().equals("exams")){columns.add("created_by");values.add("current_setting('toeic.seed.proctor_id')::bigint");}
            sql.append("INSERT INTO public.").append(entry.getKey()).append(" (").append(String.join(",",columns)).append(") VALUES (").append(String.join(",",values)).append(");\n");
        }
        return sql.toString();
    }
    public void create(Path snapshotFile,Path audio,Path images,Path scripts,Path destination)throws IOException {
        var snapshot=read(snapshotFile);var tables=validate(snapshot);String exam=str(snapshot,"examId");
        destination=destination.toAbsolutePath().normalize();externalNew(destination);
        var media=new LinkedHashMap<String,Path>();var mediaHashes=new HashMap<String,String>();
        for(var v:tables.getAsJsonArray("exam_media_assets")) {
            var m=v.getAsJsonObject();String kind="AUDIO".equals(str(m,"media_type"))?"audio-resources":"image-resources";
            String relative=kind+"/"+exam+"/"+str(m,"local_file");Path file=(kind.equals("audio-resources")?audio:images).resolve(exam).resolve(str(m,"local_file"));
            regular(file);if(Files.size(file)!=m.get("size_bytes").getAsLong()||!hash(file).equals(str(m,"sha256")))throw new IOException("Media checksum/size mismatch");
            mediaHashes.put(relative,str(m,"sha256"));
            if(media.putIfAbsent(relative,file)!=null)throw new IOException("Duplicate media path");
        }
        for(String script:List.of("restore-exam-seed.ps1","exam-seed-common.ps1"))regular(scripts.resolve(script));
        Files.createDirectories(destination.getParent());Files.createDirectory(destination);
        write(destination.resolve("exam-data.json"),snapshot);Files.writeString(destination.resolve("seed.sql"),sql(snapshot));
        for(var e:media.entrySet()){Path target=safeResolve(destination,e.getKey());Files.createDirectories(target.getParent());Files.copy(e.getValue(),target);if(!hash(target).equals(mediaHashes.get(e.getKey()))||!hash(e.getValue()).equals(mediaHashes.get(e.getKey())))throw new IOException("Media changed during copy");}
        for(String script:List.of("restore-exam-seed.ps1","exam-seed-common.ps1"))Files.copy(scripts.resolve(script),destination.resolve(script));
        var files=new JsonArray();try(var walk=Files.walk(destination)){for(Path p:walk.filter(Files::isRegularFile).sorted().toList())files.add(obj("path",destination.relativize(p).toString().replace('\\','/'),"sizeBytes",Files.size(p),"sha256",hash(p)));}
        var counts=new JsonObject();for(String table:COLUMNS.keySet())counts.addProperty(table,tables.getAsJsonArray(table).size());
        write(destination.resolve("seed-manifest.json"),obj("formatVersion",1,"producer","PORTABLE_EXAM_SEED_V1","examId",exam,"validation","V2_HTTP_201_AND_SELECTABLE","backendCommit",str(snapshot,"backendCommit"),"backendJarSha256",str(snapshot,"backendJarSha256"),"schemaColumns",snapshot.get("schemaColumns"),"counts",counts,"mediaCount",media.size(),"files",files));
        Files.writeString(destination.resolve("seed-manifest.sha256"),hash(destination.resolve("seed-manifest.json"))+"\n");verify(destination);
    }
    public JsonObject verify(Path pack)throws IOException {
        regular(pack.resolve("seed-manifest.json"));regular(pack.resolve("seed-manifest.sha256"));
        if(!hash(pack.resolve("seed-manifest.json")).equals(Files.readString(pack.resolve("seed-manifest.sha256")).strip()))throw new IOException("Manifest checksum mismatch");
        var m=read(pack.resolve("seed-manifest.json"));if(num(m,"formatVersion")!=1||!"PORTABLE_EXAM_SEED_V1".equals(str(m,"producer")))throw new IOException("Unsupported pack");
        Set<String> paths=new HashSet<>(List.of("seed-manifest.json","seed-manifest.sha256"));
        for(var v:m.getAsJsonArray("files")){var e=v.getAsJsonObject();String relative=str(e,"path");if(!paths.add(relative))throw new IOException("Duplicate pack file");Path p=safeResolve(pack,relative);regular(p);if(Files.size(p)!=e.get("sizeBytes").getAsLong()||!hash(p).equals(str(e,"sha256")))throw new IOException("Pack file checksum mismatch");}
        var d=read(pack.resolve("exam-data.json"));validate(d);if(!Files.readString(pack.resolve("seed.sql")).equals(sql(d)))throw new IOException("SQL does not match selected DB data");
        int mediaCount=0;
        for(var value:d.getAsJsonObject("tables").getAsJsonArray("exam_media_assets")) {
            var row=value.getAsJsonObject();String kind="AUDIO".equals(str(row,"media_type"))?"audio-resources":"image-resources";
            Path file=safeResolve(pack,kind+"/"+str(d,"examId")+"/"+str(row,"local_file"));regular(file);
            if(!hash(file).equals(str(row,"sha256"))||Files.size(file)!=row.get("size_bytes").getAsLong())throw new IOException("Media differs from validated DB metadata");mediaCount++;
        }
        if(mediaCount!=num(m,"mediaCount")||!str(m,"examId").equals(str(d,"examId")))throw new IOException("Manifest/data identity or media count mismatch");
        try(var walk=Files.walk(pack)){for(Path p:walk.toList()){if(Files.isSymbolicLink(p))throw new IOException("Pack symlink refused");if(Files.isRegularFile(p)&&!paths.contains(pack.relativize(p).toString().replace('\\','/')))throw new IOException("Unexpected pack file");}}
        System.out.println("PORTABLE_SEED_VERIFY_PASS exam="+str(m,"examId")+" media="+num(m,"mediaCount"));return m;
    }
    static Path safeResolve(Path root,String relative)throws IOException {
        if(relative==null||relative.isBlank()||relative.contains("\\")||relative.contains(":" )||relative.startsWith("/")||Arrays.stream(relative.split("/",-1)).anyMatch(x->x.isEmpty()||x.equals(".")||x.equals("..")))throw new IOException("Unsafe pack path");
        Path p=root.resolve(relative).toAbsolutePath().normalize();if(!p.startsWith(root.toAbsolutePath().normalize()))throw new IOException("Path traversal");return p;
    }
    static void regular(Path p)throws IOException {if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||!p.toRealPath().equals(p.toAbsolutePath().normalize()))throw new IOException("Missing or unsafe regular file");}
    static void externalNew(Path p)throws IOException {
        if(Files.exists(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Destination exists; refuse overwrite");
        for(Path parent=p.getParent();parent!=null;parent=parent.getParent())if(Files.exists(parent.resolve(".git")))throw new IOException("Seed pack/media output must be outside Git worktrees");
        Path parent=p.getParent();while(!Files.exists(parent))parent=parent.getParent();if(!parent.toRealPath().equals(parent.toAbsolutePath().normalize()))throw new IOException("Output ancestor symlink/junction refused");
    }
}
