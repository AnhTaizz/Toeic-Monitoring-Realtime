package vn.edu.toeic.server.exam.rich;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;
import vn.edu.toeic.protocol.exam.rich.*;
import vn.edu.toeic.server.exam.ExamApiException;

/** Graph validation is deliberately independent of v1's short-exam/normalization rules. */
@Component
public final class RichExamValidator {
    private static final int[] ENDS = {6,31,70,100,130,146,200};

    public void validate(RichExamImportRequest request) { new Check().run(request); }
    private static void require(boolean valid, String reason) {
        if (!valid) throw ExamApiException.invalidInput("Rich exam: " + reason);
    }
    static void id(String id) { require(id != null && id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}"), "ID không hợp lệ"); }
    private static void text(String value, int max, boolean optional) {
        require(optional && value == null || value != null && !value.isBlank() && value.length() <= max, "text/title quá giới hạn hoặc thiếu");
    }
    private static void hash(String value, boolean optional) { require(optional && value == null || value != null && value.matches("[a-f0-9]{64}"), "SHA256 không hợp lệ"); }
    private static <T> List<T> list(List<T> values, int max) { require(values != null && values.size() <= max && values.stream().noneMatch(Objects::isNull), "list không hợp lệ"); return values; }
    private static <T> Map<String,T> unique(List<T> values, java.util.function.Function<T,String> key) {
        Map<String,T> result = new LinkedHashMap<>();
        for (T value : values) { String id = key.apply(value); id(id); require(result.putIfAbsent(id,value) == null, "duplicate ID"); }
        return result;
    }
    private static void url(String value) {
        if (value == null) return;
        text(value,1024,false);
        try {
            URI uri = URI.create(value);
            require(uri.getScheme() != null && Set.of("http","https").contains(uri.getScheme()) && uri.getHost() != null && uri.getRawUserInfo() == null && uri.getRawFragment() == null, "source URL không hợp lệ");
            String query = uri.getRawQuery();
            if (query != null) for (String parameter : URLDecoder.decode(query, StandardCharsets.UTF_8).split("[&;]")) {
                String name = parameter.split("=",2)[0].toLowerCase(Locale.ROOT).replace("_","").replace("-","");
                require(!name.matches(".*(token|password|passwd|secret|credential|signature|nonce).*|key|apikey|auth|authorization|sig|jwt|bearer"),
                        "source URL có secret parameter");
            }
        } catch (IllegalArgumentException invalid) { throw ExamApiException.invalidInput("Rich exam: source URL không hợp lệ"); }
    }
    public static boolean safeFile(String name) {
        return name != null && name.length() <= 255 && name.matches("[A-Za-z0-9][A-Za-z0-9._-]*")
                && !name.contains("..") && !name.endsWith(".")
                && !name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?");
    }
    private static final class Check {
        final Set<String> blocks = new HashSet<>(), usedMedia = new HashSet<>(), usedSources = new HashSet<>();
        Map<String,SourceImport> sources; Map<String,GroupImport> groups; Map<String,MediaImport> media;
        final Map<String,List<QuestionImport>> members = new HashMap<>();
        int blockCount;
        void run(RichExamImportRequest r) {
            require(r != null && r.schemaVersion() == 2 && r.exam() != null, "schemaVersion=2 bắt buộc");
            id(r.exam().examId()); text(r.exam().title(),255,false); text(r.exam().description(),4000,true);
            require("TOEIC_200".equals(r.exam().profile()), "profile TOEIC_200 bắt buộc");
            sources = unique(list(r.sources(),16), SourceImport::sourceRef); require(!sources.isEmpty(), "thiếu nguồn");
            groups = unique(list(r.groups(),200), GroupImport::groupId);
            media = unique(list(r.media(),200), MediaImport::mediaId);
            Set<String> checksums = new HashSet<>();
            for (var s : sources.values()) {
                text(s.sourceName(),255,false); text(s.sourcePath(),1024,true); url(s.sourceUrl());
                require(s.sourcePath() != null || s.sourceUrl() != null, "thiếu source locator");
                if (s.sourcePath() != null) require(!s.sourcePath().startsWith("/") && !s.sourcePath().startsWith("\\") && !s.sourcePath().contains(":")
                        && Arrays.stream(s.sourcePath().split("[/\\\\]")).noneMatch(".."::equals), "sourcePath phải tương đối");
                text(s.copyrightNotice(),4000,true); text(s.permissionNote(),4000,true); text(s.licenseDocument(),4000,true);
                require(s.permissionStatus() != null && s.permissionStatus() != PermissionStatus.UNKNOWN && s.permissionConfirmedOn() != null, "permission chưa xác minh");
                if (s.permissionStatus() == PermissionStatus.USER_CONFIRMED) text(s.permissionNote(),4000,false);
                else text(s.licenseDocument(),4000,false);
                require(s.answerKeyStatus() == Verification.VERIFIED && s.groupMappingStatus() == Verification.VERIFIED
                        && s.optionContentStatus() == Verification.VERIFIED && "VERIFIED".equals(s.mediaValidationStatus()), "source/options/key/group/media chưa verified");
                hash(s.sourceSha256(),false); hash(s.manifestSha256(),true); require(checksums.add(s.sourceSha256()), "duplicate source checksum");
            }
            long audioBytes = 0, imageBytes = 0; int images = 0, audios = 0;
            Set<String> files = new HashSet<>();
            for (var m : media.values()) {
                source(m.sourceRef()); url(m.sourceUrl()); text(m.altText(),1000,true); hash(m.sha256(),false);
                require(m.mediaType() != null && m.storageStatus() == StorageStatus.LOCAL_VERIFIED && safeFile(m.localFile()) && files.add(m.localFile().toLowerCase(Locale.ROOT)), "media phải local verified/tên file unique an toàn");
                require(m.sizeBytes() > 0, "media size phải dương");
                if (m.mediaType() == MediaType.IMAGE) {
                    images++; imageBytes += m.sizeBytes();
                    require(m.sizeBytes() <= 8L*1024*1024 && m.widthPx() != null && m.heightPx() != null && m.widthPx() > 0 && m.heightPx() > 0
                            && m.widthPx() <= 8192 && m.heightPx() <= 8192 && (long)m.widthPx()*m.heightPx() <= 16000000 && m.durationMs() == null, "image limits/dimensions");
                    require(m.localFile().toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g)") && m.mimeType() != null && Set.of("image/png","image/jpeg").contains(m.mimeType()), "image MIME/extension");
                } else {
                    audios++; audioBytes += m.sizeBytes();
                    require(m.sizeBytes() <= 64L*1024*1024 && m.widthPx() == null && m.heightPx() == null && (m.durationMs() == null || m.durationMs() > 0), "audio limits/dimensions");
                    String name = m.localFile().toLowerCase(Locale.ROOT);
                    require(name.endsWith(".wav") && "audio/wav".equals(m.mimeType()) || name.endsWith(".mp3") && "audio/mpeg".equals(m.mimeType())
                            || name.endsWith(".m4a") && "audio/mp4".equals(m.mimeType()), "audio MIME/extension");
                }
            }
            require(images <= 100 && audios <= 100 && audioBytes <= 256L*1024*1024 && imageBytes <= 64L*1024*1024, "aggregate media limits");
            List<QuestionImport> questions = list(r.questions(),200); require(questions.size() == 200, "phải đúng 200 câu");
            unique(questions, QuestionImport::questionId); Set<String> sourceNumbers = new HashSet<>();
            for (int index=0; index<200; index++) {
                var q = questions.get(index); int order = index+1, part = 1; while(order > ENDS[part-1]) part++;
                require(q.orderIndex() == order && q.part() == part && q.section() == section(part), "Part range/order/section");
                source(q.sourceRef()); require(q.sourceQuestionNumber() > 0 && sourceNumbers.add(q.sourceRef()+":"+q.sourceQuestionNumber()) && q.answerKeyStatus() == Verification.VERIFIED, "source number/key status");
                if (part == 5) require(q.groupId() == null, "Part5 phải độc lập");
                else {
                    var g = groups.get(q.groupId()); require(g != null && g.part() == part && g.section() == q.section(), "group/part mismatch");
                    members.computeIfAbsent(q.groupId(),ignored->new ArrayList<>()).add(q);
                }
                int count = part == 2 ? 3 : 4; list(q.options(),4); require(q.options().size() == count, "option count");
                for(int n=0;n<count;n++) { var o=q.options().get(n); require(o.orderIndex()==n+1 && String.valueOf((char)('A'+n)).equals(o.optionId()), "option ID/order"); text(o.text(),2000,part<=2); }
                require(q.scoring()!=null && q.options().stream().anyMatch(o->o.optionId().equals(q.scoring().correctOption())), "invalid scoring key");
                inspect(q.blocks(),1,false); require(q.blocks().stream().anyMatch(b->b.type()==BlockType.TEXT), "thiếu prompt TEXT");
                if(part==1) require(q.blocks().stream().filter(b->b.type()==BlockType.IMAGE).count()==1, "Part1 cần một ảnh");
            }
            int previous = 0; Set<String> audioOwners = new HashSet<>();
            for(var g : groups.values()) {
                text(g.title(),255,true); var qs=members.get(g.groupId());
                require(qs!=null && g.mappingStatus()==Verification.VERIFIED && g.part()>=1 && g.part()<=7 && g.part()!=5 && g.section()==section(g.part()), "group rỗng/chưa verified");
                int expected = switch(g.part()){case 1,2->1;case 3,4->3;case 6->4;default->qs.size();};
                require(qs.size()==expected && qs.size()<=5 && g.orderIndex()==qs.getFirst().orderIndex() && g.orderIndex()>previous, "group order/count"); previous=g.orderIndex();
                for(int n=1;n<qs.size();n++) require(qs.get(n).orderIndex()==qs.get(n-1).orderIndex()+1, "group không liên tiếp");
                list(g.documents(),3); list(g.blocks(),2000);
                if(g.part()<=4) {
                    require(g.documents().isEmpty(), "Listening không document"); inspect(g.blocks(),1,true);
                    var audio=g.blocks().stream().filter(b->b.type()==BlockType.AUDIO).toList(); require(audio.size()==1 && audioOwners.add(audio.getFirst().mediaId()), "shared audio/group");
                    require(media.get(audio.getFirst().mediaId()).localFile().startsWith(String.format(Locale.ROOT,"%03d-",g.orderIndex())), "audio ordinal prefix");
                } else {
                    require(g.blocks().isEmpty() && !g.documents().isEmpty() && (g.part()!=6 || g.documents().size()==1), "document count/owner");
                    Set<String> ids=new HashSet<>(); int position=0, blockOrder=1;
                    for(var d:g.documents()) {
                        id(d.documentId()); text(d.title(),255,true); require(ids.add(d.documentId()) && d.orderIndex()==++position, "document ID/order");
                        blockOrder=inspect(d.blocks(),blockOrder,false);
                        require(d.blocks().stream().anyMatch(b->b.type()==BlockType.TEXT), "document thiếu TEXT");
                    }
                }
            }
            require(usedMedia.equals(media.keySet()) && usedSources.equals(sources.keySet()), "orphan media/source");
        }
        private void source(String ref) { require(ref != null && sources.containsKey(ref), "sourceRef ngoài đề"); usedSources.add(ref); }
        private int inspect(List<ContentBlock> values, int order, boolean audioAllowed) {
            for(var b:list(values,2000)) {
                id(b.blockId()); require(blocks.add(b.blockId()) && ++blockCount <= 2000 && b.orderIndex()==order++, "block duplicate/order/limit");
                require(b.type()!=null, "block discriminator null");
                if(b.type()==BlockType.TEXT) {text(b.text(),16000,false); require(b.mediaId()==null && b.mediaType()==null, "TEXT payload");}
                else {
                    var m=media.get(b.mediaId()); require(b.text()==null && b.mediaType()!=null && m!=null && b.mediaType().name().equals(b.type().name()) && m.mediaType()==b.mediaType(), "typed media reference");
                    require(audioAllowed || b.type()!=BlockType.AUDIO, "AUDIO chỉ nằm Listening group"); usedMedia.add(b.mediaId());
                }
            }
            return order;
        }
        private Section section(int part){return part<=4?Section.LISTENING:Section.READING;}
    }
}
