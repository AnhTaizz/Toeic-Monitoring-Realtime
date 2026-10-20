package vn.edu.toeic.dataset;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.toeic.dataset.Json.*;

class HtmlExamParserTest {
    static final Path ROOT = Path.of(System.getProperty("dataset.root", "../.."));
    static JsonObject dataset;
    @BeforeAll static void parse() throws Exception { dataset = new HtmlExamParser().parse(ROOT, "data/seedingTest/ets2026_test01.html"); }
    @Test void extracts200QuestionsAndSourceParts() {
        var qs = dataset.getAsJsonArray("questions"); assertEquals(200, qs.size());
        int[] counts = new int[7]; var numbers = new HashSet<Integer>();
        for (var v : qs) { var q = v.getAsJsonObject(); counts[num(q, "part") - 1]++; numbers.add(num(q, "sourceQuestionNumber")); }
        assertArrayEquals(new int[]{6,25,39,30,30,16,54}, counts); assertEquals(200, numbers.size());
    }
    @Test void usesDomAncestryForSharedGroupsAndSingletons() {
        var qs = dataset.getAsJsonArray("questions");
        assertEquals(str(qs.get(31).getAsJsonObject(), "groupId"), str(qs.get(33).getAsJsonObject(), "groupId"));
        assertNotEquals(str(qs.get(30).getAsJsonObject(), "groupId"), str(qs.get(31).getAsJsonObject(), "groupId"));
        assertNull(str(qs.get(100).getAsJsonObject(), "groupId"));
        assertEquals(73, dataset.getAsJsonArray("groups").size());
    }
    @Test void extractsRecordedCorrectAnswerNotUsersWrongChoice() {
        var q = dataset.getAsJsonArray("questions").get(0).getAsJsonObject();
        assertEquals("B", str(q, "correctOption")); assertEquals("EXTRACTED", str(q, "answerKeyStatus"));
        assertEquals("#cau-1", str(q, "sourceLocator"));
    }
    @Test void neverPromotesExplanationOrTranslationToCandidateOptions() {
        var q = dataset.getAsJsonArray("questions").get(31).getAsJsonObject();
        assertFalse(q.getAsJsonArray("reviewOptionCandidates").isEmpty());
        for (var o : q.getAsJsonArray("options")) assertTrue(o.getAsJsonObject().get("text").isJsonNull());
        assertEquals("REJECTED", str(q.getAsJsonArray("reviewOptionCandidates").get(0).getAsJsonObject(), "status"));
    }
    @Test void part12DisplayHasNoTranscripts() {
        for (int n = 1; n <= 31; n++) {
            var q = dataset.getAsJsonArray("questions").get(n - 1).getAsJsonObject(); assertEquals(n + ".", str(q, "prompt"));
            for (var b : q.getAsJsonArray("blocks")) if ("TEXT".equals(str(b.getAsJsonObject(), "type"))) assertEquals(n + ".", str(b.getAsJsonObject(), "text"));
            for (var o : q.getAsJsonArray("options")) assertTrue(o.getAsJsonObject().get("text").isJsonNull());
        }
    }
    @Test void excludesPageChromeAndPreservesAllContentMedia() {
        var ms = dataset.getAsJsonArray("media");
        assertEquals(54, ms.asList().stream().filter(m -> "AUDIO".equals(str(m.getAsJsonObject(), "mediaType"))).count());
        assertEquals(38, ms.asList().stream().filter(m -> "IMAGE".equals(str(m.getAsJsonObject(), "mediaType"))).count());
        for (var m : ms) { assertNull(str(m.getAsJsonObject(), "sha256")); assertEquals("REMOTE_REFERENCE", str(m.getAsJsonObject(), "storageStatus")); }
    }
    @Test void doesNotInventDocumentBoundariesFromImages() {
        for (var v : dataset.getAsJsonArray("groups")) {
            var g = v.getAsJsonObject();
            if (num(g, "part") >= 6) { assertTrue(g.getAsJsonArray("documents").isEmpty()); assertFalse(g.getAsJsonArray("passageBlocks").isEmpty()); }
        }
    }
    @Test void everyLeafHasAReviewStatusAndOnlyMeasuredHashesAreVerified() {
        var reviews = dataset.getAsJsonObject("fieldReviews");
        assertTrue(reviews.size() > 10000);
        for (var e : reviews.entrySet()) if ("VERIFIED".equals(str(e.getValue().getAsJsonObject(), "status")))
            assertTrue(Set.of("/source/sha256", "/source/manifestSha256").contains(e.getKey()));
        assertEquals("NEEDS_REVIEW", str(reviews.getAsJsonObject("/questions/31/options/0/text"), "status"));
    }
    @Test void parsesNestedDomAndEntitiesWithoutRegexSlicing(@TempDir Path temp) throws Exception {
        Files.createDirectories(temp.resolve("data/seedingTest"));
        String html = "<article data-zen-history-answer data-question-number='101' data-part-name='Part 5' data-part-type='reading' id='cau-101'>"
                + "<div class='zen-history-question-content'><div class='zen-history-sub-question'>101. A <span>nested &amp; <b>valid</b></span> prompt.</div></div>"
                + "<div class='zen-history-answer-details'><p><strong>Đáp án đã chọn:</strong>D.</p><p><strong>Đáp án đúng:</strong>A.</p></div>"
                + "<div class='zen-history-explanation'><div><p>(A) untrusted<br>(B) untrusted</p></div></div></article>";
        Path input = temp.resolve("data/seedingTest/ets2026_test01.html"); Files.writeString(input, html);
        var manifest = read(ROOT.resolve("data/seedingTest/manifest.json"));
        manifest.getAsJsonArray("files").get(0).getAsJsonObject().addProperty("sha256", hash(input));
        write(temp.resolve("data/seedingTest/manifest.json"), manifest);
        var parsed = new HtmlExamParser().parse(temp, "data/seedingTest/ets2026_test01.html");
        assertEquals("101. A nested & valid prompt.", str(parsed.getAsJsonArray("questions").get(0).getAsJsonObject(), "prompt"));
    }
}
