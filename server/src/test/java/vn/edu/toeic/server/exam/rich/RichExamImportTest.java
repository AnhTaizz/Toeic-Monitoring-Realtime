package vn.edu.toeic.server.exam.rich;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import vn.edu.toeic.server.exam.ExamApiException;

class RichExamImportTest {
    record Mutation(String name,Consumer<JsonObject> change) {public String toString(){return name;}}
    static Stream<Mutation> invalidGraphs() {
        return Stream.of(
            new Mutation("schema1",t->t.addProperty("schemaVersion",1)),
            new Mutation("profile",t->t.getAsJsonObject("exam").addProperty("profile","SHORT")),
            new Mutation("bad exam ID",t->t.getAsJsonObject("exam").addProperty("examId","../escape")),
            new Mutation("199 questions",t->t.getAsJsonArray("questions").remove(199)),
            new Mutation("part range",t->RichExamFixtures.question(t,0).addProperty("part",2)),
            new Mutation("section",t->RichExamFixtures.question(t,100).addProperty("section","LISTENING")),
            new Mutation("order",t->RichExamFixtures.question(t,1).addProperty("orderIndex",1)),
            new Mutation("question ID",t->RichExamFixtures.question(t,1).addProperty("questionId","q001")),
            new Mutation("source duplicate",t->t.getAsJsonArray("sources").add(t.getAsJsonArray("sources").get(0).deepCopy())),
            new Mutation("source checksum duplicate",t->{var s=RichExamFixtures.at(t,"sources",0).deepCopy();s.addProperty("sourceRef","OTHER");t.getAsJsonArray("sources").add(s);}),
            new Mutation("source permission",t->RichExamFixtures.at(t,"sources",0).addProperty("permissionStatus","UNKNOWN")),
            new Mutation("source key",t->RichExamFixtures.at(t,"sources",0).addProperty("answerKeyStatus","UNVERIFIED")),
            new Mutation("source group",t->RichExamFixtures.at(t,"sources",0).addProperty("groupMappingStatus","UNVERIFIED")),
            new Mutation("source options",t->RichExamFixtures.at(t,"sources",0).addProperty("optionContentStatus","UNVERIFIED")),
            new Mutation("source media",t->RichExamFixtures.at(t,"sources",0).addProperty("mediaValidationStatus","NOT_RUN")),
            new Mutation("source traversal",t->RichExamFixtures.at(t,"sources",0).addProperty("sourcePath","../private")),
            new Mutation("source URL secret",t->RichExamFixtures.at(t,"sources",0).addProperty("sourceUrl","https://example.invalid/a?token=MOCK")),
            new Mutation("source relative URL",t->RichExamFixtures.at(t,"sources",0).addProperty("sourceUrl","relative")),
            new Mutation("unmapped source",t->RichExamFixtures.question(t,0).addProperty("sourceRef","MISSING")),
            new Mutation("source question number duplicate",t->RichExamFixtures.question(t,1).addProperty("sourceQuestionNumber",1)),
            new Mutation("question unverified",t->RichExamFixtures.question(t,0).addProperty("answerKeyStatus","UNVERIFIED")),
            new Mutation("bad scoring key",t->RichExamFixtures.question(t,0).getAsJsonObject("scoring").addProperty("correctOption","Z")),
            new Mutation("bad option count",t->RichExamFixtures.question(t,6).getAsJsonArray("options").remove(2)),
            new Mutation("bad option ID",t->RichExamFixtures.question(t,0).getAsJsonArray("options").get(0).getAsJsonObject().addProperty("optionId","X")),
            new Mutation("missing option text P3",t->RichExamFixtures.question(t,31).getAsJsonArray("options").get(0).getAsJsonObject().add("text",JsonNull.INSTANCE)),
            new Mutation("Part1 no image",t->RichExamFixtures.question(t,0).getAsJsonArray("blocks").remove(1)),
            new Mutation("Part5 group",t->RichExamFixtures.question(t,100).addProperty("groupId","g001")),
            new Mutation("orphan group",t->{var g=RichExamFixtures.group(t,0).deepCopy();g.addProperty("groupId","ORPHAN");t.getAsJsonArray("groups").add(g);}),
            new Mutation("group order",t->RichExamFixtures.group(t,0).addProperty("orderIndex",2)),
            new Mutation("group unverified",t->RichExamFixtures.group(t,0).addProperty("mappingStatus","UNVERIFIED")),
            new Mutation("Part3 membership",t->RichExamFixtures.question(t,34).addProperty("groupId","g032")),
            new Mutation("group no audio",t->RichExamFixtures.group(t,0).getAsJsonArray("blocks").remove(0)),
            new Mutation("missing media",t->RichExamFixtures.group(t,0).getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("mediaId","MISSING")),
            new Mutation("typed media",t->RichExamFixtures.group(t,0).getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("mediaType","IMAGE")),
            new Mutation("media remote",t->RichExamFixtures.media(t,0).addProperty("storageStatus","REMOTE_REFERENCE")),
            new Mutation("media duplicate",t->t.getAsJsonArray("media").add(t.getAsJsonArray("media").get(0).deepCopy())),
            new Mutation("media traversal",t->RichExamFixtures.media(t,0).addProperty("localFile","../image.png")),
            new Mutation("media reserved name",t->RichExamFixtures.media(t,0).addProperty("localFile","CON.png")),
            new Mutation("media invalid hash",t->RichExamFixtures.media(t,0).addProperty("sha256","bad")),
            new Mutation("media zero size",t->RichExamFixtures.media(t,0).addProperty("sizeBytes",0)),
            new Mutation("image null MIME",t->RichExamFixtures.media(t,0).add("mimeType",JsonNull.INSTANCE)),
            new Mutation("image oversized",t->RichExamFixtures.media(t,0).addProperty("sizeBytes",9*1024*1024)),
            new Mutation("image pixel bomb",t->{RichExamFixtures.media(t,0).addProperty("widthPx",8192);RichExamFixtures.media(t,0).addProperty("heightPx",8192);}),
            new Mutation("block duplicate",t->RichExamFixtures.question(t,1).getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("blockId",RichExamFixtures.question(t,0).getAsJsonArray("blocks").get(0).getAsJsonObject().get("blockId").getAsString())),
            new Mutation("block missing type",t->RichExamFixtures.question(t,0).getAsJsonArray("blocks").get(0).getAsJsonObject().remove("type")),
            new Mutation("block empty prompt",t->RichExamFixtures.question(t,0).getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("text"," ")),
            new Mutation("block unexpected payload",t->RichExamFixtures.question(t,0).getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("mediaId","img-grid")),
            new Mutation("orphan media",t->{var m=RichExamFixtures.media(t,0).deepCopy();m.addProperty("mediaId","ORPHAN");m.addProperty("localFile","orphan.png");t.getAsJsonArray("media").add(m);}),
            new Mutation("unknown nested field",t->RichExamFixtures.question(t,0).addProperty("owner","MISSING")),
            new Mutation("Part6 no documents",t->readingGroup(t,6).getAsJsonArray("documents").remove(0)),
            new Mutation("Part6 two documents",t->readingGroup(t,6).getAsJsonArray("documents").add(readingGroup(t,6).getAsJsonArray("documents").get(0).deepCopy())),
            new Mutation("Part7 no documents",t->readingGroup(t,7).add("documents",new JsonArray())),
            new Mutation("Part7 four documents",t->readingGroup(t,7).getAsJsonArray("documents").add(readingGroup(t,7).getAsJsonArray("documents").get(0).deepCopy())),
            new Mutation("document ID duplicate",t->document(t,1).addProperty("documentId","email")),
            new Mutation("document order duplicate",t->document(t,1).addProperty("orderIndex",1)),
            new Mutation("document block reset",t->document(t,1).getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("orderIndex",1)),
            new Mutation("document without text",t->document(t,0).add("blocks",new JsonArray())),
            new Mutation("reading shared blocks",t->readingGroup(t,7).getAsJsonArray("blocks").add(RichExamFixtures.group(t,0).getAsJsonArray("blocks").get(0).deepCopy())),
            new Mutation("Listening document",t->RichExamFixtures.group(t,0).getAsJsonArray("documents").add(document(t,0).deepCopy())),
            new Mutation("audio reused by group",t->RichExamFixtures.group(t,1).getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("mediaId","aud-001")),
            new Mutation("audio ordinal prefix",t->RichExamFixtures.media(t,3).addProperty("localFile","999-g001.wav")),
            new Mutation("file name collision",t->RichExamFixtures.media(t,1).addProperty("localFile","MOCK-GRID.PNG")),
            new Mutation("aggregate audio limit",t->t.getAsJsonArray("media").forEach(m->{if(m.getAsJsonObject().get("mediaType").getAsString().equals("AUDIO"))m.getAsJsonObject().addProperty("sizeBytes",5*1024*1024);})),
            new Mutation("unknown document field",t->document(t,0).addProperty("owner","MISSING"))
        );
    }
    private static JsonObject readingGroup(JsonObject tree,int part) {
        return tree.getAsJsonArray("groups").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(g->g.get("part").getAsInt()==part).findFirst().orElseThrow();
    }
    private static JsonObject document(JsonObject tree,int index) {return readingGroup(tree,7).getAsJsonArray("documents").get(index).getAsJsonObject();}
    @Test void mock200HasExactGraphAndLabelOnlyOptions() throws Exception {
        var r=RichExamFixtures.request();
        new RichExamValidator().validate(r);
        assertThat(r.questions()).hasSize(200); assertThat(r.groups()).hasSize(76); assertThat(r.media()).hasSize(57);
        assertThat(r.questions().getFirst().options().getFirst().text()).isNull();
        assertThatThrownBy(()->r.questions().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @ParameterizedTest(name="{0}") @MethodSource("invalidGraphs")
    void invalidGraphIsControlled400(Mutation mutation) throws Exception {
        var tree=RichExamFixtures.tree();mutation.change().accept(tree);
        assertThatThrownBy(()->new RichExamValidator().validate(RichExamFixtures.parse(tree.toString())))
                .isInstanceOfSatisfying(ExamApiException.class,e->assertThat(e.status().value()).isEqualTo(400));
    }
    static Stream<String> invalidJson() throws Exception {
        String valid=RichExamFixtures.tree().toString();
        return Stream.of(valid.replaceFirst("\\{","{\"schemaVersion\":2,"),
            valid.replaceFirst("\"orderIndex\":1,",""),
            valid.replaceFirst("\"schemaVersion\":2","\"schemaVersion\":null"),
            valid.replaceFirst("\"schemaVersion\":2","\"schemaVersion\":\"2\""),
            valid.replaceFirst("\"schemaVersion\":2","\"schemaVersion\":2.0"),
            valid.replaceFirst("\"schemaVersion\":2","\"schemaVersion\":2e0"),
            valid.replaceFirst("\"schemaVersion\":2","\"schemaVersion\":9223372036854775808"),
            valid.replace("\"LISTENING\"","\"listening\""),
            valid.replaceFirst("\"orderIndex\":1","\"orderIndex\":null"),
            valid.replaceFirst("\"orderIndex\":1","\"orderIndex\":1,\"orderIndex\":2"),
            valid+" {}",valid.substring(0,valid.length()-1),"{'schemaVersion':2}", "[]","NaN");
    }
    @ParameterizedTest @MethodSource("invalidJson")
    void strictJsonRejectsDuplicateKeysMissingPrimitivesAndWrongTypes(String json) {
        assertThatThrownBy(()->RichExamFixtures.parse(json)).isInstanceOf(ExamApiException.class);
    }
    @Test void oversizedUtf8AndMalformedUtf8AreRejected() {
        assertThatThrownBy(()->RichExamFixtures.parse(" ".repeat(RichExamJson.MAX_BYTES+1)))
                .isInstanceOfSatisfying(ExamApiException.class,e->assertThat(e.status().value()).isEqualTo(413));
        assertThatThrownBy(()->new RichExamJson().read(new ByteArrayInputStream(new byte[]{(byte)0xc0,(byte)0xaf})))
                .isInstanceOf(ExamApiException.class);
    }
    @ParameterizedTest @ValueSource(strings={"access_token","api_key","client_secret","X-Amz-Signature","refreshToken","%61ccess%5Ftoken"})
    void sourceUrlCredentialAliasesAreRejected(String parameter) throws Exception {
        var tree=RichExamFixtures.tree();
        RichExamFixtures.at(tree,"sources",0).addProperty("sourceUrl","https://example.invalid/a?"+parameter+"=MOCK");
        assertThatThrownBy(()->new RichExamValidator().validate(RichExamFixtures.parse(tree.toString())))
                .isInstanceOfSatisfying(ExamApiException.class,e->assertThat(e.status().value()).isEqualTo(400));
    }
    static Stream<String> invalidScalarJson() {
        return Stream.of("\\u0000","\\ud800","\\udc00")
                .map(escape->"{\"schemaVersion\":2,\"exam\":{\"title\":\""+escape+"\"}}");
    }
    @ParameterizedTest @MethodSource("invalidScalarJson")
    void escapedNulAndUnpairedSurrogatesRejectBeforePersistence(String body) {
        assertThatThrownBy(()->RichExamFixtures.parse(body)).isInstanceOf(ExamApiException.class);
    }
    @Test void validUnicodeAndPublicSourceQueryRemainAllowed() throws Exception {
        var tree=RichExamFixtures.tree();tree.getAsJsonObject("exam").addProperty("title","MOCK 中文 😀");
        RichExamFixtures.at(tree,"sources",0).addProperty("sourceUrl","https://example.invalid/a?exam=200&author=MOCK");
        var request=RichExamFixtures.parse(tree.toString());new RichExamValidator().validate(request);
        assertThat(request.exam().title()).isEqualTo("MOCK 中文 😀");
    }
}
