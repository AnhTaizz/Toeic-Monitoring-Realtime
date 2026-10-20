package vn.edu.toeic.server.exam.rich;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Consumer;
import vn.edu.toeic.protocol.exam.rich.*;

/** Fixture CC0 MOCK tự soạn, không phải dữ liệu thi production. */
final class RichExamFixtures {
    static final Path ROOT=Path.of("..","docs","plan-v2","fixtures","ex02");
    static final Gson JSON=new GsonBuilder().serializeNulls().create();
    static JsonObject tree() throws IOException {return JsonParser.parseString(Files.readString(ROOT.resolve("import.mock.json"))).getAsJsonObject();}
    static RichExamImportRequest parse(String text) throws IOException {
        return new RichExamJson().read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }
    static RichExamImportRequest request() throws IOException { return parse(tree().toString()); }
    static JsonObject at(JsonObject tree,String array,int index){return tree.getAsJsonArray(array).get(index).getAsJsonObject();}
    static JsonObject question(JsonObject tree,int index){return at(tree,"questions",index);}
    static JsonObject group(JsonObject tree,int index){return at(tree,"groups",index);}
    static JsonObject media(JsonObject tree,int index){return at(tree,"media",index);}
    static void files(Path audio,Path image,RichExamImportRequest request) throws IOException {
        for(var m:request.media()) {
            Path target=(m.mediaType()==MediaType.AUDIO?audio:image).resolve(request.exam().examId());
            Files.createDirectories(target);
            Files.copy(ROOT.resolve("assets").resolve(m.mediaType()==MediaType.AUDIO?"audio":"images").resolve("mock-ex02-200").resolve(m.localFile()),
                    target.resolve(m.localFile()),StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private RichExamFixtures(){}
}
