package vn.edu.toeic.dataset;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.toeic.dataset.Json.*;

class PipelineTest {
    static JsonObject original;
    static final Path ROOT = HtmlExamParserTest.ROOT;
    @BeforeAll static void parse() throws Exception { original = new HtmlExamParser().parse(ROOT, "data/seedingTest/ets2026_test01.html"); }
    static Set<String> codes(JsonObject d) {
        var result = new HashSet<String>();
        for (var f : new DatasetValidator().validate(d).getAsJsonArray("findings")) result.add(str(f.getAsJsonObject(), "code"));
        return result;
    }
    @Test void actualTest01HasNoStructuralErrorsButIsBlocked() {
        var report = new DatasetValidator().validate(original);
        assertEquals(0, num(report.getAsJsonObject("summary"), "structuralErrors"));
        assertFalse(report.get("importable").getAsBoolean());
        assertTrue(codes(original).containsAll(Set.of("ANSWER_UNVERIFIED", "OPTIONS_UNVERIFIED", "DOCUMENT_BOUNDARIES", "MEDIA_UNVERIFIED", "SOURCE_REVIEW")));
        assertEquals(676, num(report.getAsJsonObject("summary"), "missingOriginalOptionTexts"));
    }
    @Test void catchesMissingAndDuplicateQuestions() {
        var d = original.deepCopy(); d.getAsJsonArray("questions").remove(0);
        assertTrue(codes(d).containsAll(Set.of("MISSING_QUESTION", "QUESTION_COUNT", "PART_ORDER")));
        d = original.deepCopy(); d.getAsJsonArray("questions").set(1, d.getAsJsonArray("questions").get(0).deepCopy());
        assertTrue(codes(d).containsAll(Set.of("DUPLICATE_QUESTION", "DUPLICATE_ID")));
    }
    @Test void catchesPartSwapDespiteSameCounts() {
        var d = original.deepCopy(); var qs = d.getAsJsonArray("questions");
        qs.get(5).getAsJsonObject().addProperty("part", 2); qs.get(6).getAsJsonObject().addProperty("part", 1);
        assertTrue(codes(d).contains("PART_ORDER"));
    }
    @Test void catchesInvalidKeyAndMissingOptions() {
        var d = original.deepCopy(); var q = d.getAsJsonArray("questions").get(0).getAsJsonObject();
        q.addProperty("correctOption", "Z"); q.getAsJsonArray("options").remove(0);
        assertTrue(codes(d).containsAll(Set.of("ANSWER_OUTSIDE_OPTIONS", "OPTION_COUNT", "OPTION_ORDER")));
    }
    @Test void catchesWrongMembershipAndSharedAudioReuse() {
        var d = original.deepCopy(); var gs = d.getAsJsonArray("groups");
        gs.get(1).getAsJsonObject().getAsJsonArray("questionNumbers").set(0, new JsonPrimitive(1));
        var first = gs.get(0).getAsJsonObject().getAsJsonArray("passageBlocks").get(0).getAsJsonObject();
        gs.get(1).getAsJsonObject().getAsJsonArray("passageBlocks").get(0).getAsJsonObject().addProperty("mediaId", str(first, "mediaId"));
        assertTrue(codes(d).containsAll(Set.of("GROUP_MEMBERS", "SHARED_AUDIO", "AUDIO_FILENAME")));
    }
    @Test void catchesMissingAndMismatchedMedia() {
        var d = original.deepCopy(); d.getAsJsonArray("media").remove(0);
        assertTrue(codes(d).contains("BLOCK_MEDIA"));
    }
    @Test void catchesTranscriptInjection() {
        var d = original.deepCopy(); d.getAsJsonArray("questions").get(0).getAsJsonObject().getAsJsonArray("options").get(0).getAsJsonObject().addProperty("text", "Transcript");
        assertTrue(codes(d).contains("TRANSCRIPT_LEAK"));
        var draft = new V2Converter().draft(d);
        assertTrue(draft.getAsJsonArray("questions").get(0).getAsJsonObject().getAsJsonArray("options").get(0).getAsJsonObject().get("text").isJsonNull());
    }
    @Test void catchesTranscriptInsideDisplayBlocks() {
        var d = original.deepCopy(); d.getAsJsonArray("questions").get(0).getAsJsonObject().getAsJsonArray("blocks").get(0).getAsJsonObject().addProperty("text", "MOCK transcript");
        assertTrue(codes(d).contains("TRANSCRIPT_LEAK"));
    }
    @Test void checksExplicitDocumentCountsOrderAndRequiredOriginalText() {
        var d = original.deepCopy();
        var group = d.getAsJsonArray("groups").asList().stream().map(JsonElement::getAsJsonObject).filter(g -> num(g, "part") == 6).findFirst().orElseThrow();
        group.add("documents", arr(obj("documentId", "mock-doc", "orderIndex", 2, "title", "MOCK", "blocks", new JsonArray()),
                obj("documentId", "mock-doc", "orderIndex", 2, "title", "MOCK", "blocks", new JsonArray())));
        assertTrue(codes(d).containsAll(Set.of("DOCUMENT_COUNT", "DOCUMENT_ORDER", "DOCUMENT_TEXT")));
    }
    @Test void catchesUnsafeAndDuplicateMediaFilenames() {
        var d = original.deepCopy();
        var ms = d.getAsJsonArray("media"); ms.get(0).getAsJsonObject().addProperty("localFile", "../outside.mp3");
        assertTrue(codes(d).contains("MEDIA_FILENAME"));
    }
    @Test void catchesChecksumMismatch() {
        var d = original.deepCopy(); d.getAsJsonObject("source").addProperty("sha256", "0".repeat(64));
        assertTrue(codes(d).contains("SOURCE_CHECKSUM"));
    }
    @Test void duplicateAuditMeasuresActualBytes() throws Exception {
        var audit = ExamDataset.auditSources(ROOT);
        assertTrue(audit.get("allChecksumsMatch").getAsBoolean()); assertEquals(4, num(audit, "uniqueContents"));
        var duplicate = audit.getAsJsonArray("duplicates").get(0).getAsJsonObject();
        assertEquals("data/seedingTest/ets2026_test05.html", str(duplicate, "path"));
        assertEquals("data/seedingTest/ets2026_test04.html", str(duplicate, "duplicateOf"));
    }
    @Test void rejectsExplicitTest05BeforeWritingArtifacts() {
        assertThrows(IllegalArgumentException.class, () -> ExamDataset.main(new String[]{"--test", "05"}));
    }
    @Test void draftHasOnlyDtoFieldsAndUsesUnverifiedEnums() {
        var draft = new V2Converter().draft(original);
        assertEquals(Set.of("schemaVersion", "exam", "sources", "groups", "media", "questions"), draft.keySet());
        assertEquals(2, num(draft, "schemaVersion"));
        for (var value : draft.getAsJsonArray("questions")) {
            var q = value.getAsJsonObject();
            assertEquals(Set.of("questionId", "section", "part", "orderIndex", "groupId", "sourceRef", "sourceQuestionNumber", "answerKeyStatus", "blocks", "options", "scoring"), q.keySet());
            assertEquals("UNVERIFIED", str(q, "answerKeyStatus"));
            assertFalse(q.has("reviewOptionCandidates")); assertFalse(q.has("sourceAnswerText"));
        }
        assertEquals("UNVERIFIED", str(draft.getAsJsonArray("sources").get(0).getAsJsonObject(), "optionContentStatus"));
    }
    @Test void converterPreservesQuestionImagesAndGroupAudioOwnership() {
        var draft = new V2Converter().draft(original);
        var q = draft.getAsJsonArray("questions").get(0).getAsJsonObject();
        assertEquals(2, q.getAsJsonArray("blocks").size());
        assertEquals("IMAGE", str(q.getAsJsonArray("blocks").get(1).getAsJsonObject(), "type"));
        var g = draft.getAsJsonArray("groups").get(0).getAsJsonObject();
        assertEquals("AUDIO", str(g.getAsJsonArray("blocks").get(0).getAsJsonObject(), "type"));
        assertTrue(g.getAsJsonArray("documents").isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"02", "03", "04"}) void otherUniqueInputsParseStructurally(String n) throws Exception {
        var d = new HtmlExamParser().parse(ROOT, "data/seedingTest/ets2026_test" + n + ".html");
        var report = new DatasetValidator().validate(d, ROOT);
        assertEquals(200, d.getAsJsonArray("questions").size());
        assertEquals(0, num(report.getAsJsonObject("summary"), "structuralErrors"));
        assertFalse(report.get("importable").getAsBoolean());
    }
    @Test void outputIsDeterministic() throws Exception {
        var again = new HtmlExamParser().parse(ROOT, "data/seedingTest/ets2026_test01.html");
        assertEquals(GSON.toJson(original), GSON.toJson(again));
        assertEquals(GSON.toJson(new V2Converter().draft(original)), GSON.toJson(new V2Converter().draft(again)));
    }
}
