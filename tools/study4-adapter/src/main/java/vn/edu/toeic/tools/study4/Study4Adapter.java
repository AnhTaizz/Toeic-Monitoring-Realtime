package vn.edu.toeic.tools.study4;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import javax.imageio.ImageIO;
import vn.edu.toeic.protocol.exam.rich.*;

/** Offline adapter for a user-supplied STUDY4 export. It never downloads media. */
public final class Study4Adapter {
    private static final int[] EXPECTED = {6,25,39,30,30,16,54};
    private static final String SOURCE = "study4-export";
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting()
            .registerTypeAdapter(LocalDate.class, new JsonSerializer<LocalDate>() {
                @Override public JsonElement serialize(LocalDate value, Type type, JsonSerializationContext context) {
                    return new JsonPrimitive(value.toString());
                }
            }).create();

    public static void main(String[] args) throws Exception {
        Map<String,String> flags = flags(args);
        Path input = requiredPath(flags,"--input");
        Path output = requiredPath(flags,"--output");
        LocalDate permissionDate = LocalDate.parse(required(flags,"--permission-date"));
        Result result = new Study4Adapter().convert(input,output,permissionDate);
        System.out.printf(Locale.ROOT,"exam=%s questions=%d groups=%d audio=%d images=%d payloadSha256=%s%n",
                result.request().exam().examId(),result.request().questions().size(),result.request().groups().size(),
                result.audioCount(),result.imageCount(),result.payloadSha256());
    }

    public Result convert(Path input, Path output, LocalDate permissionDate) throws Exception {
        return convert(input.resolve("server_exam.json"), output, permissionDate, true);
    }

    /** Desktop import: the server export contains the answer key; a companion client export is optional. */
    public Result convertServerFile(Path serverFile, Path output, LocalDate permissionDate) throws Exception {
        return convert(serverFile, output, permissionDate, false);
    }

    private Result convert(Path serverFile, Path output, LocalDate permissionDate, boolean requireClient) throws Exception {
        serverFile=serverFile.toAbsolutePath().normalize();Path input=serverFile.getParent();
        input=input.toAbsolutePath().normalize(); output=output.toAbsolutePath().normalize();
        Path clientFile=input.resolve("client_exam.json");
        require(Files.isRegularFile(serverFile)&&(!requireClient||Files.isRegularFile(clientFile)),"missing server/client JSON");
        require(!Files.exists(output)||isEmpty(output),"output must not exist or must be empty");
        JsonObject server=read(serverFile);
        String examId=text(server,"id"); require(examId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}"),"invalid exam id");
        List<Row> serverRows=rows(server);
        if(requireClient) {
            JsonObject client=read(clientFile);
            require(!Files.readString(clientFile,StandardCharsets.UTF_8).contains("\"correctAnswer\""),"client JSON exposes correctAnswer");
            comparePublic(serverRows,rows(client));
        }
        int[] counts=new int[7];serverRows.forEach(r->counts[r.part()-1]++);
        require(Arrays.equals(counts,EXPECTED),"part distribution "+Arrays.toString(counts));
        require(serverRows.size()==200&&integer(server,"totalQuestions")==200,"expected 200 questions");

        Files.createDirectories(output); Path audioRoot=output.resolve("audio").resolve(examId),imageRoot=output.resolve("images").resolve(examId);
        List<MediaImport> media=new ArrayList<>(); Map<String,String> mediaIds=new LinkedHashMap<>(); int[] mediaCounts={0,0};
        List<GroupImport> groups=new ArrayList<>(); List<QuestionImport> questions=new ArrayList<>();
        int questionOrder=0;
        for(JsonElement itemValue:server.getAsJsonArray("items")) {
            JsonObject item=itemValue.getAsJsonObject(); int part=integer(item,"part"); List<JsonObject> itemQuestions=questions(item);
            int firstOrder=questionOrder+1; String groupId=part==5?null:String.format(Locale.ROOT,"g%03d",firstOrder);
            List<String> itemImages=strings(item,"imagePaths");
            if(part!=5) {
                List<ContentBlock> blocks=new ArrayList<>(); List<DocumentContent> documents=new ArrayList<>();
                if(part<=4) {
                    String audioPath=text(item,"audioPath"); require(audioPath!=null,"missing listening audio");
                    String audioId=stage(input,output,audioRoot,audioPath,MediaType.AUDIO,firstOrder,media,mediaIds,mediaCounts);
                    blocks.add(mediaBlock(groupId+"-audio",1,audioId,MediaType.AUDIO));
                    if(part>=3) for(String path:itemImages) {
                        String id=stage(input,output,imageRoot,path,MediaType.IMAGE,firstOrder,media,mediaIds,mediaCounts);
                        blocks.add(mediaBlock(groupId+"-image-"+blocks.size(),blocks.size()+1,id,MediaType.IMAGE));
                    }
                } else {
                    List<ContentBlock> documentBlocks=new ArrayList<>(); String passage=text(item,"passage");
                    require(passage!=null&&!passage.isBlank(),"missing reading passage");
                    documentBlocks.add(new ContentBlock(groupId+"-doc-text",BlockType.TEXT,1,passage,null,null));
                    for(String path:itemImages) {
                        String id=stage(input,output,imageRoot,path,MediaType.IMAGE,firstOrder,media,mediaIds,mediaCounts);
                        documentBlocks.add(mediaBlock(groupId+"-doc-image-"+documentBlocks.size(),documentBlocks.size()+1,id,MediaType.IMAGE));
                    }
                    documents.add(new DocumentContent(groupId+"-doc",1,null,documentBlocks));
                }
                groups.add(new GroupImport(groupId,section(part),part,null,firstOrder,Verification.VERIFIED,documents,blocks));
            }
            for(JsonObject raw:itemQuestions) {
                questionOrder++; require(integer(raw,"part")==part,"item/question part mismatch");
                List<ContentBlock> qBlocks=new ArrayList<>(); String prompt=text(raw,"content");
                if(prompt==null||prompt.isBlank())prompt="Question "+questionOrder;
                qBlocks.add(new ContentBlock(String.format(Locale.ROOT,"q%03d-text",questionOrder),BlockType.TEXT,1,prompt,null,null));
                if(part==1||part==5) for(String path:itemImages) {
                    String id=stage(input,output,imageRoot,path,MediaType.IMAGE,firstOrder,media,mediaIds,mediaCounts);
                    qBlocks.add(mediaBlock(String.format(Locale.ROOT,"q%03d-image-%d",questionOrder,qBlocks.size()),qBlocks.size()+1,id,MediaType.IMAGE));
                }
                List<OptionContent> options=new ArrayList<>(); JsonArray rawOptions=array(raw,"options");
                for(int n=0;n<rawOptions.size();n++) {
                    JsonObject option=rawOptions.get(n).getAsJsonObject();String key=text(option,"key");
                    require(key!=null&&key.equals(String.valueOf((char)('A'+n))),"invalid option order");
                    options.add(new OptionContent(key,n+1,part<=2?null:text(option,"text")));
                }
                String correct=text(raw,"correctAnswer");require(correct!=null,"missing correct answer");
                questions.add(new QuestionImport(String.format(Locale.ROOT,"q%03d",questionOrder),section(part),part,questionOrder,groupId,
                        SOURCE,questionOrder,Verification.VERIFIED,qBlocks,options,new ScoringKey(correct)));
            }
        }
        require(questionOrder==200,"converted question count");
        String sourceHash=sha256(serverFile);
        SourceImport source=new SourceImport(SOURCE,"STUDY4 export "+examId,"server_exam.json",null,
                "Third-party TOEIC practice content; internal project use only; redistribution prohibited.",PermissionStatus.USER_CONFIRMED,
                permissionDate,"User confirmed internal project use on "+permissionDate+"; no redistribution.",null,sourceHash,null,
                Verification.VERIFIED,Verification.VERIFIED,Verification.VERIFIED,"VERIFIED");
        RichExamImportRequest request=new RichExamImportRequest(2,new ExamMetadata(examId,text(server,"title"),
                "Internal 200-question acceptance dataset; generated locally from a user-supplied export.","TOEIC_200"),
                List.of(source),groups,media,questions);
        Path payload=output.resolve("rich-import.json");Files.writeString(payload,JSON.toJson(request)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        String payloadHash=sha256(payload); JsonObject manifest=new JsonObject();
        manifest.addProperty("examId",examId);manifest.addProperty("sourceSha256",sourceHash);manifest.addProperty("payloadSha256",payloadHash);
        manifest.addProperty("questions",questions.size());manifest.addProperty("groups",groups.size());manifest.addProperty("audio",mediaCounts[0]);manifest.addProperty("images",mediaCounts[1]);
        manifest.addProperty("permission","USER_CONFIRMED_INTERNAL_ONLY");manifest.addProperty("permissionConfirmedOn",permissionDate.toString());
        Files.writeString(output.resolve("manifest.json"),JSON.toJson(manifest)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        return new Result(request,payloadHash,mediaCounts[0],mediaCounts[1]);
    }

    private static String stage(Path input,Path output,Path destinationRoot,String rawPath,MediaType type,int firstOrder,
            List<MediaImport> media,Map<String,String> mediaIds,int[] counts)throws Exception {
        String normalized=rawPath.replace('\\','/'); Path source=input.resolve(normalized).normalize();
        require(source.startsWith(input)&&Files.isRegularFile(source),"unsafe or missing media: "+rawPath);
        require(source.toRealPath().startsWith(input.toRealPath()),"media is outside the selected exam folder: "+rawPath);
        require(Files.size(source)>0 && Files.size(source)<=(type==MediaType.AUDIO?64L:8L)*1024*1024,"media too large or empty: "+rawPath);
        String key=source.toString().toLowerCase(Locale.ROOT);if(mediaIds.containsKey(key))return mediaIds.get(key);
        int index=++counts[type==MediaType.AUDIO?0:1];String extension=extension(source.getFileName().toString());
        String id=(type==MediaType.AUDIO?"aud-":"img-")+String.format(Locale.ROOT,"%03d",index);
        String file=type==MediaType.AUDIO?String.format(Locale.ROOT,"%03d-%s.%s",firstOrder,id,extension):id+"."+extension;
        Files.createDirectories(destinationRoot);Path destination=destinationRoot.resolve(file);Files.copy(source,destination,StandardCopyOption.COPY_ATTRIBUTES);
        Integer width=null,height=null;String mime;
        if(type==MediaType.IMAGE){
            BufferedImage converted=null;
            try(var stream=ImageIO.createImageInputStream(destination.toFile())) {
                require(stream!=null,"image decode failed: "+rawPath);var readers=ImageIO.getImageReaders(stream);require(readers.hasNext(),"image decode failed: "+rawPath);
                var reader=readers.next();try{reader.setInput(stream);width=reader.getWidth(0);height=reader.getHeight(0);
                    require(width>0&&height>0&&width<=8192&&height<=8192&&(long)width*height<=16000000,"image dimensions too large");
                    String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                    require(Set.of("webp","jpeg","png").contains(format),"unsupported image format: "+rawPath);
                    if(format.equals("webp") || format.equals("png")&&!extension.equals("png") || format.equals("jpeg")&&extension.equals("png"))converted=reader.read(0);
                }finally{reader.dispose();}
            }
            if(converted!=null){Path old=destination;file=id+".png";destination=destinationRoot.resolve(file);
                if(destination.equals(old))Files.delete(old);
                require(ImageIO.write(converted,"png",destination.toFile()),"PNG conversion failed: "+rawPath);
                if(!destination.equals(old))Files.delete(old);extension="png";
                require(Files.size(destination)<=8L*1024*1024,"converted image too large: "+rawPath);
            }
            mime=extension.equals("png")?"image/png":"image/jpeg";
        }
        else mime=extension.equals("mp3")?"audio/mpeg":extension.equals("wav")?"audio/wav":"audio/mp4";
        long size=Files.size(destination);String hash=sha256(destination);
        media.add(new MediaImport(id,type,SOURCE,null,StorageStatus.LOCAL_VERIFIED,file,mime,size,hash,width,height,null,null));mediaIds.put(key,id);return id;
    }

    private static ContentBlock mediaBlock(String id,int order,String mediaId,MediaType type){return new ContentBlock(id,type==MediaType.AUDIO?BlockType.AUDIO:BlockType.IMAGE,order,null,mediaId,type);}
    private static Section section(int part){return part<=4?Section.LISTENING:Section.READING;}
    private static List<Row> rows(JsonObject root){List<Row> out=new ArrayList<>();for(JsonElement value:array(root,"items")){JsonObject item=value.getAsJsonObject();for(JsonObject q:questions(item))out.add(new Row(text(q,"id"),integer(q,"part"),text(q,"content"),optionSignature(q)));}return out;}
    private static void comparePublic(List<Row> server,List<Row> client){require(server.size()==client.size(),"client/server question count mismatch");for(int i=0;i<server.size();i++)require(server.get(i).equals(client.get(i)),"client/server public content mismatch at "+(i+1));}
    private static String optionSignature(JsonObject q){StringBuilder b=new StringBuilder();for(JsonElement e:array(q,"options")){JsonObject o=e.getAsJsonObject();b.append(text(o,"key")).append('\0').append(text(o,"text")).append('\1');}return b.toString();}
    private static List<JsonObject> questions(JsonObject item){JsonArray nested=array(item,"questions");if(!nested.isEmpty()){List<JsonObject> result=new ArrayList<>();nested.forEach(v->result.add(v.getAsJsonObject()));return result;}return List.of(item);}
    private static List<String> strings(JsonObject o,String name){List<String> out=new ArrayList<>();for(JsonElement e:array(o,name))if(!e.isJsonNull())out.add(e.getAsString());return out;}
    private static JsonArray array(JsonObject o,String name){JsonElement e=o.get(name);return e==null||e.isJsonNull()?new JsonArray():e.getAsJsonArray();}
    private static String text(JsonObject o,String name){JsonElement e=o.get(name);return e==null||e.isJsonNull()?null:e.getAsString();}
    private static int integer(JsonObject o,String name){JsonElement e=o.get(name);require(e!=null&&!e.isJsonNull(),"missing "+name);return e.getAsInt();}
    private static JsonObject read(Path p)throws IOException{return JsonParser.parseString(Files.readString(p,StandardCharsets.UTF_8)).getAsJsonObject();}
    private static boolean isEmpty(Path p)throws IOException{if(!Files.isDirectory(p))return false;try(var files=Files.list(p)){return files.findAny().isEmpty();}}
    private static String extension(String name){int dot=name.lastIndexOf('.');require(dot>0,"media extension missing");String value=name.substring(dot+1).toLowerCase(Locale.ROOT);require(Set.of("mp3","wav","m4a","jpg","jpeg","png").contains(value),"unsupported media extension");return value;}
    private static String sha256(Path p)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(p)){byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))>=0;)d.update(buffer,0,n);}return HexFormat.of().formatHex(d.digest());}
    private static Map<String,String> flags(String[] args){require(args.length%2==0,"arguments must be name/value pairs");Map<String,String> out=new LinkedHashMap<>();for(int i=0;i<args.length;i+=2){require(Set.of("--input","--output","--permission-date").contains(args[i]),"unknown argument "+args[i]);require(out.put(args[i],args[i+1])==null,"duplicate argument "+args[i]);}return out;}
    private static String required(Map<String,String> flags,String name){String value=flags.get(name);require(value!=null&&!value.isBlank(),"missing "+name);return value;}
    private static Path requiredPath(Map<String,String> flags,String name){return Path.of(required(flags,name));}
    private static void require(boolean value,String message){if(!value)throw new IllegalArgumentException(message);}
    private record Row(String id,int part,String content,String options){}
    public record Result(RichExamImportRequest request,String payloadSha256,int audioCount,int imageCount){}
}
