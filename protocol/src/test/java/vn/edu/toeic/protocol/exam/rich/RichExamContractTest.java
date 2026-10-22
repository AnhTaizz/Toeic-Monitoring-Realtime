package vn.edu.toeic.protocol.exam.rich;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import vn.edu.toeic.protocol.exam.CandidateQuestionDto;
import vn.edu.toeic.protocol.exam.ExamQuestionImportDto;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Contract-level EX-02 checks using only the self-authored MOCK fixture. */
class RichExamContractTest {
    private static final Gson JSON = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class,
                    (com.google.gson.JsonDeserializer<LocalDate>) (value, type, context) ->
                            LocalDate.parse(value.getAsString()))
            .create();

    @Test
    void importFixtureSupportsSevenPartsReusableMediaAndOrderedContent() throws IOException {
        RichExamImportRequest exam = JSON.fromJson(read("import.mock.json"), RichExamImportRequest.class);

        assertThat(exam.schemaVersion()).isEqualTo(2);
        assertThat(exam.questions()).hasSize(200);
        Map<Integer, Long> partCounts = exam.questions().stream()
                .collect(Collectors.groupingBy(QuestionImport::part, Collectors.counting()));
        assertThat(partCounts).containsExactlyInAnyOrderEntriesOf(Map.of(
                1, 6L, 2, 25L, 3, 39L, 4, 30L, 5, 30L, 6, 16L, 7, 54L));
        assertThat(exam.questions()).extracting(QuestionImport::orderIndex)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 200).boxed().toList());

        QuestionImport part1 = question(exam, "q001");
        assertThat(part1.blocks()).extracting(ContentBlock::type)
                .containsExactly(BlockType.TEXT, BlockType.IMAGE);
        assertThat(part1.options()).extracting(OptionContent::optionId)
                .containsExactly("A", "B", "C", "D");
        assertThat(part1.options()).extracting(OptionContent::text).containsOnlyNulls();

        QuestionImport part2 = question(exam, "q007");
        assertThat(part2.options()).extracting(OptionContent::optionId)
                .containsExactly("A", "B", "C");
        assertThat(part2.options()).extracting(OptionContent::text).containsOnlyNulls();

        GroupImport part3 = group(exam, "g032");
        assertThat(part3.blocks()).singleElement().satisfies(block -> {
            assertThat(block.type()).isEqualTo(BlockType.AUDIO);
            assertThat(block.mediaId()).isEqualTo("aud-032");
        });
        assertThat(exam.questions().stream().filter(q -> "g032".equals(q.groupId())))
                .extracting(QuestionImport::questionId).containsExactly("q032", "q033", "q034");

        GroupImport part6 = group(exam, "g131");
        assertThat(part6.documents()).singleElement()
                .extracting(DocumentContent::documentId).isEqualTo("passage");
        assertThat(exam.questions().stream().filter(q -> "g131".equals(q.groupId()))).hasSize(4);

        GroupImport part7 = group(exam, "g147");
        assertThat(part7.documents()).extracting(DocumentContent::documentId)
                .containsExactly("email", "notice", "reply");
        assertThat(part7.documents().stream().flatMap(document -> document.blocks().stream()))
                .extracting(ContentBlock::orderIndex).containsExactly(1, 2, 3, 4);
    }

    @Test
    void candidateProjectionHasNoAnswerKeyOrImportProvenance() throws IOException {
        JsonObject candidate = JsonParser.parseString(read("candidate.mock.json")).getAsJsonObject();

        assertThat(candidate.getAsJsonArray("questions")).hasSize(200);
        assertNoPrivateFields(candidate);
        assertThat(componentNames(CandidateQuestion.class))
                .containsExactly("questionId", "section", "part", "orderIndex", "groupId", "blocks", "options")
                .doesNotContain("scoring", "correctOption", "sourceRef", "answerKeyStatus");
        assertThat(componentNames(QuestionImport.class)).contains("scoring", "sourceRef", "answerKeyStatus");
        assertThat(componentNames(ScoringKey.class)).containsExactly("correctOption");
    }

    @Test
    void legacyFlatTextAudioContractsRemainUnchanged() {
        assertThat(componentNames(CandidateQuestionDto.class)).containsExactly(
                "questionId", "section", "part", "groupId", "passageText", "audioFile",
                "prompt", "orderIndex", "options");
        assertThat(componentNames(ExamQuestionImportDto.class)).containsExactly(
                "questionId", "section", "part", "groupId", "passageText", "audioFile",
                "prompt", "correctOption", "orderIndex", "options");
    }

    private static QuestionImport question(RichExamImportRequest exam, String id) {
        return exam.questions().stream().filter(question -> id.equals(question.questionId()))
                .findFirst().orElseThrow();
    }

    private static GroupImport group(RichExamImportRequest exam, String id) {
        return exam.groups().stream().filter(group -> id.equals(group.groupId()))
                .findFirst().orElseThrow();
    }

    private static List<String> componentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static void assertNoPrivateFields(JsonElement value) {
        if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(RichExamContractTest::assertNoPrivateFields);
            return;
        }
        if (!value.isJsonObject()) return;
        Set<String> forbidden = Set.of("correctoption", "scoring", "answerkeystatus", "sourceref",
                "sourceurl", "sourcepath", "sourcequestionnumber", "localfile", "permissionstatus");
        value.getAsJsonObject().entrySet().forEach(entry -> {
            assertThat(entry.getKey().toLowerCase(Locale.ROOT)).isNotIn(forbidden);
            assertNoPrivateFields(entry.getValue());
        });
    }

    private static String read(String name) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path fixture = current.resolve("docs/plan-v2/fixtures/ex02").resolve(name);
            if (Files.isRegularFile(fixture)) return Files.readString(fixture);
            current = current.getParent();
        }
        throw new IOException("Cannot locate EX-02 fixture " + name);
    }
}
