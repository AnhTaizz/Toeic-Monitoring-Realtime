package vn.edu.toeic.client.exam.rich;

import static org.assertj.core.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.protocol.exam.rich.*;

/** Fixture MOCK EX-02 (CC0, tự tạo): 200 câu 6/25/39/30/30/16/54. Không phải đề TOEIC thật. */
class RichExamModelTest {
    static final Path FIXTURES = Path.of("..", "docs", "plan-v2", "fixtures", "ex02");

    static String fixtureJson() throws IOException {
        return Files.readString(FIXTURES.resolve("candidate.mock.json"), StandardCharsets.UTF_8);
    }

    static RichCandidateExam parse(String json) throws IOException {
        try (var client = new RichExamApiClient("http://127.0.0.1:1", "MOCK-TOKEN")) {
            return client.json(json.getBytes(StandardCharsets.UTF_8), RichCandidateExam.class);
        }
    }

    static RichExamModel fixture() throws IOException { return RichExamModel.from(parse(fixtureJson())); }

    @Test void maps200QuestionsIntoSevenPartsInServerOrder() throws Exception {
        RichExamModel model = fixture();
        assertThat(model.totalQuestions()).isEqualTo(200);
        assertThat(model.parts()).extracting(RichExamModel.PartInfo::part).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(model.parts()).extracting(RichExamModel.PartInfo::questionCount).containsExactly(6, 25, 39, 30, 30, 16, 54);
        assertThat(model.questions()).extracting(RichExamModel.Question::number)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 200).boxed().toList());
        // Part 1/2/5: một câu một đơn vị; Part 3/4: nhóm 3 câu; Part 6: nhóm 4 câu.
        assertThat(model.part(1).units()).hasSize(6);
        assertThat(model.part(2).units()).hasSize(25);
        assertThat(model.part(3).units()).hasSize(13).allSatisfy(u -> assertThat(u.questions()).hasSize(3));
        assertThat(model.part(4).units()).hasSize(10).allSatisfy(u -> assertThat(u.questions()).hasSize(3));
        assertThat(model.part(5).units()).hasSize(30);
        assertThat(model.part(6).units()).hasSize(4).allSatisfy(u -> assertThat(u.questions()).hasSize(4));
        assertThat(model.allowedOptions()).hasSize(200);
    }

    @Test void part1HasPhotoAndLabelOnlyOptionsWithoutTranscript() throws Exception {
        RichExamModel model = fixture();
        RichExamModel.Question q1 = model.question("q001");
        assertThat(RichExamModel.ordered(q1.dto().blocks())).extracting(ContentBlock::type).contains(BlockType.IMAGE);
        String imageId = q1.dto().blocks().stream().filter(b -> b.type() == BlockType.IMAGE).findFirst().orElseThrow().mediaId();
        assertThat(model.media(imageId).imagePath()).startsWith("/api/v2/attempts/");
        assertThat(q1.dto().options()).hasSize(4).allSatisfy(o -> assertThat(o.text()).isNull());
        assertThat(q1.audioFile()).isNotBlank();
    }

    @Test void part2HasThreeLabelOnlyOptions() throws Exception {
        RichExamModel.Question q7 = fixture().question("q007");
        assertThat(q7.part()).isEqualTo(2);
        assertThat(q7.dto().options()).extracting(OptionContent::optionId).containsExactly("A", "B", "C");
        assertThat(q7.dto().options()).allSatisfy(o -> assertThat(o.text()).isNull());
    }

    @Test void part3And4GroupsShareOneAudioAndPart4KeepsOptionalFigure() throws Exception {
        RichExamModel model = fixture();
        RichExamModel.Unit g32 = model.unitOf("q033");
        assertThat(g32.group().groupId()).isEqualTo("g032");
        assertThat(g32.questions()).extracting(RichExamModel.Question::id).containsExactly("q032", "q033", "q034");
        assertThat(g32.questions()).extracting(RichExamModel.Question::audioFile).containsOnly(g32.questions().getFirst().audioFile());
        assertThat(model.unitForAudio(g32.questions().getFirst().audioFile())).isSameAs(g32);
        RichExamModel.Unit g71 = model.unitOf("q071");
        assertThat(RichExamModel.ordered(g71.group().blocks())).extracting(ContentBlock::type).containsExactly(BlockType.AUDIO, BlockType.IMAGE);
    }

    @Test void part6ShareOnePassageAndPart7KeepsDocumentBoundaries() throws Exception {
        RichExamModel model = fixture();
        RichExamModel.Unit g131 = model.unitOf("q131");
        assertThat(g131.questions()).extracting(RichExamModel.Question::number).containsExactly(131, 132, 133, 134);
        assertThat(RichExamModel.documents(g131.group())).hasSize(1);
        RichExamModel.Unit g147 = model.unitOf("q147");
        List<DocumentContent> docs = RichExamModel.documents(g147.group());
        assertThat(docs).extracting(DocumentContent::title).containsExactly("MOCK Email", "MOCK Notice", "MOCK Reply");
        assertThat(RichExamModel.ordered(docs.get(1).blocks())).extracting(ContentBlock::type).containsExactly(BlockType.TEXT, BlockType.IMAGE);
    }

    @Test void listeningBridgeKeepsOnePlaylistOfServerAudioWithLabelFallback() throws Exception {
        var legacy = fixture().toListeningExam();
        assertThat(legacy.questions()).hasSize(100).allSatisfy(q -> {
            assertThat(q.section()).isEqualTo("LISTENING");
            assertThat(q.audioFile()).isNotBlank();
            assertThat(q.passageText()).isNull();
        });
        var q7 = legacy.questions().stream().filter(q -> q.questionId().equals("q007")).findFirst().orElseThrow();
        assertThat(q7.options()).extracting(o -> o.optionText()).containsExactly("A", "B", "C");
        Map<String, Long> perAudio = legacy.questions().stream().filter(q -> q.part() == 3)
                .collect(Collectors.groupingBy(q -> q.audioFile(), Collectors.counting()));
        assertThat(perAudio).hasSize(13).allSatisfy((file, count) -> assertThat(count).isEqualTo(3));
    }

    @Test void responseWithScoringOrProvenanceKeysIsRejectedBeforeMapping() throws Exception {
        for (String key : List.of("correctOption", "answerKeyStatus", "sourceUrl", "localFile")) {
            JsonObject tree = JsonParser.parseString(fixtureJson()).getAsJsonObject();
            tree.getAsJsonArray("questions").get(5).getAsJsonObject().addProperty(key, "B");
            assertThatThrownBy(() -> parse(tree.toString())).as(key).isInstanceOf(IOException.class).hasMessageContaining("không được phép");
        }
    }

    @Test void structurallyBrokenExamIsRejected() throws Exception {
        JsonObject noDeadline = JsonParser.parseString(fixtureJson()).getAsJsonObject();
        noDeadline.add("deadlineAt", com.google.gson.JsonNull.INSTANCE);
        assertThatThrownBy(() -> RichExamModel.from(parse(noDeadline.toString()))).hasMessageContaining("START");

        JsonObject duplicateNumber = JsonParser.parseString(fixtureJson()).getAsJsonObject();
        duplicateNumber.getAsJsonArray("questions").get(1).getAsJsonObject().addProperty("orderIndex", 1);
        assertThatThrownBy(() -> RichExamModel.from(parse(duplicateNumber.toString()))).hasMessageContaining("thứ tự");

        JsonObject missingMedia = JsonParser.parseString(fixtureJson()).getAsJsonObject();
        missingMedia.add("media", new com.google.gson.JsonArray());
        assertThatThrownBy(() -> RichExamModel.from(parse(missingMedia.toString()))).isInstanceOf(IllegalArgumentException.class);
    }

    /** Part 6 chuẩn có câu chèn nguyên câu; fixture MOCK chỉ có nhãn ngắn nên test thêm option dài. */
    @Test void sentenceInsertionOptionTextIsKeptVerbatim() throws Exception {
        JsonObject tree = JsonParser.parseString(fixtureJson()).getAsJsonObject();
        String sentence = "MOCK: All staff must register for the workshop by Friday, as seats are limited.";
        for (var element : tree.getAsJsonArray("questions")) {
            var q = element.getAsJsonObject();
            if (q.get("questionId").getAsString().equals("q133"))
                q.getAsJsonArray("options").get(2).getAsJsonObject().addProperty("text", sentence);
        }
        var option = RichExamModel.from(parse(tree.toString())).question("q133").dto().options().get(2);
        assertThat(option.text()).isEqualTo(sentence);
    }
}
