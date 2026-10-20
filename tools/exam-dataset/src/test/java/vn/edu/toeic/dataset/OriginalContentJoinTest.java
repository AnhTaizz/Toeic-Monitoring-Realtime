package vn.edu.toeic.dataset;

import com.google.gson.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.toeic.dataset.Json.*;

/** MOCK supplemental exports: no test fixture grants human verification to ETS. */
class OriginalContentJoinTest {
    @TempDir Path temp;
    JsonObject dataset, recovery;
    static JsonObject baseDataset, baseRecovery;
    final OriginalContentJoin join = new OriginalContentJoin();
    @BeforeAll static void captureFixture() throws Exception {
        var fixture=new RecoveryTest();baseDataset=fixture.base();baseRecovery=fixture.realRecovery(baseDataset);
    }
    @BeforeEach void prepare() {dataset=baseDataset.deepCopy();recovery=baseRecovery.deepCopy();}
    JsonObject row(int number, String a, String b) {
        return obj("questionNumber",number,"part",HtmlExamParser.expectedPart(number),"options",arr(
                obj("optionId","D","text","Delta"),obj("optionId","B","text",b),
                obj("optionId","C","text","Gamma"),obj("optionId","A","text",a)));
    }
    Path source(String name, JsonArray questions, JsonArray groups) throws Exception {
        Path file = temp.resolve(name);
        write(file,obj("examId","ets2026-test01","questions",questions,"groups",groups)); return file;
    }
    JsonObject option(String label) {return recovery.getAsJsonArray("questions").get(100).getAsJsonObject().getAsJsonObject("originalEnglishOptions").getAsJsonObject(label);}
    JsonObject group(String text) {
        var blocks=arr(obj("type","TEXT","text",text));
        var first=dataset.getAsJsonArray("groups").asList().stream().map(JsonElement::getAsJsonObject).filter(x->num(x,"part")==6).findFirst().orElseThrow();
        for(var block:first.getAsJsonArray("passageBlocks"))if("IMAGE".equals(str(block.getAsJsonObject(),"type")))blocks.add(obj("type","IMAGE","mediaId",str(block.getAsJsonObject(),"mediaId")));
        return obj("part",6,"questionNumbers",arr(131,132,133,134),"documents",arr(
                obj("documentId","original-letter","title","MOCK Letter","text",text,"blocks",blocks)));
    }
    @Test void conflictingSourceExactOptionIsRefusedWithoutPartialChanges() throws Exception {
        var before = recovery.deepCopy();
        var file=source("MOCK-conflict.json",arr(row(101,"Alpha","Different from recorded source")),new JsonArray());
        var error=assertThrows(IllegalArgumentException.class,()->join.apply(dataset,recovery,file));
        assertTrue(error.getMessage().contains("conflict")); assertTrue(before.equals(recovery),"Rejected supplement must leave recovery unchanged");
    }
    @Test void secondSupplementCannotSilentlyReplaceFirstSource() throws Exception {
        join.apply(dataset,recovery,source("MOCK-first.json",arr(row(101,"Alpha","after")),new JsonArray()));
        var before=recovery.deepCopy();
        assertThrows(IllegalArgumentException.class,()->join.apply(dataset,recovery,source("MOCK-second.json",arr(row(101,"Different Alpha","after")),new JsonArray())));
        assertTrue(before.equals(recovery),"Rejected supplement must leave recovery unchanged");
    }
    @Test void matchingSourcesKeepBothHashesAndExplicitLabels() throws Exception {
        Path first=source("MOCK-first.json",arr(row(101,"Alpha","after")),new JsonArray());
        Path second=source("MOCK-second.json",arr(row(101,"Alpha","after")),new JsonArray());
        join.apply(dataset,recovery,first);join.apply(dataset,recovery,second);
        assertEquals("Alpha",str(option("A"),"value"));
        var provenance=option("A").getAsJsonArray("provenance");
        int originalCount=baseRecovery.getAsJsonArray("questions").get(100).getAsJsonObject().getAsJsonObject("originalEnglishOptions").getAsJsonObject("A").getAsJsonArray("provenance").size();
        assertEquals(originalCount+2,provenance.size());assertEquals(hash(first),str(provenance.get(originalCount).getAsJsonObject(),"sourceSha256"));
        assertEquals(hash(second),str(provenance.get(originalCount+1).getAsJsonObject(),"sourceSha256"));
        assertEquals("SOURCE_EXACT",str(option("A"),"status"));
        assertFalse(new DatasetValidator().validate(dataset).get("importable").getAsBoolean());
    }
    @Test void conflictingOriginalPromptIsRefused() throws Exception {
        var question=row(101,"Alpha","after");question.addProperty("prompt","MOCK conflicting question.");
        assertThrows(IllegalArgumentException.class,()->join.apply(dataset,recovery,source("MOCK-prompt.json",arr(question),new JsonArray())));
    }
    @Test void invalidLaterRowDoesNotModifyEarlierQuestion() throws Exception {
        var before=recovery.deepCopy();var later=row(102,"Alpha","Beta");later.addProperty("part",7);
        assertThrows(IllegalArgumentException.class,()->join.apply(dataset,recovery,source("MOCK-invalid-later.json",arr(row(101,"Alpha","after"),later),new JsonArray())));
        assertTrue(before.equals(recovery),"Rejected supplement must leave recovery unchanged");
    }
    @Test void duplicateDocumentGroupsAndIdsAreRefused() throws Exception {
        var g=group("MOCK original document.");
        assertThrows(IllegalArgumentException.class,()->join.apply(dataset,recovery,source("MOCK-duplicate-group.json",new JsonArray(),arr(g,g.deepCopy()))));
        var seven=dataset.getAsJsonArray("groups").asList().stream().map(JsonElement::getAsJsonObject).filter(x->num(x,"part")==7).findFirst().orElseThrow();
        var doc=obj("documentId","duplicate","text","MOCK document.");
        var row=obj("part",7,"questionNumbers",seven.get("questionNumbers"),"documents",arr(doc,doc.deepCopy()));
        assertThrows(IllegalArgumentException.class,()->join.apply(dataset,recovery,source("MOCK-duplicate-id.json",new JsonArray(),arr(row))));
    }
    @Test void documentFieldsRetainProvenanceAndMediaMapping() throws Exception {
        var g=dataset.getAsJsonArray("groups").asList().stream().map(JsonElement::getAsJsonObject).filter(x->num(x,"part")==6).findFirst().orElseThrow();
        var media=g.get("passageBlocks").deepCopy();String groupId=str(g,"groupId");
        Path file=source("MOCK-document.json",new JsonArray(),arr(group("MOCK original document.")));
        join.apply(dataset,recovery,file);
        var joined=dataset.getAsJsonArray("groups").asList().stream().map(JsonElement::getAsJsonObject).filter(x->groupId.equals(str(x,"groupId"))).findFirst().orElseThrow();
        assertEquals(media,joined.get("passageBlocks"));
        assertEquals("NEEDS_REVIEW",str(joined,"documentBoundaryStatus"));
        var fields=joined.getAsJsonObject("originalDocumentFieldProvenance");
        for(String field:new String[]{"documentId","title","text","orderIndex"})
            assertEquals(hash(file),str(fields.getAsJsonArray("/documents/0/"+field).get(0).getAsJsonObject(),"sourceSha256"));
    }
    @Test void reorderedTargetCannotJoinByArrayPositionAlone() throws Exception {
        dataset.getAsJsonArray("questions").get(100).getAsJsonObject().addProperty("sourceQuestionNumber",102);
        assertThrows(IllegalArgumentException.class,()->join.apply(dataset,recovery,source("MOCK-target-identity.json",arr(row(101,"Alpha","after")),new JsonArray())));
    }
    @Test void generatedTemplateAndPerQuestionBlockersRemainIncomplete() throws Exception {
        var result=RecoverExam.reconstruct(dataset,recovery);
        assertEquals(200,result.getAsJsonArray("question-blockers").size());
        var template=result.getAsJsonObject("original-source-template");
        assertEquals("INCOMPLETE_NOT_IMPORTABLE",str(template,"templateStatus"));
        assertEquals(19,template.getAsJsonArray("groups").size());
        Path file=temp.resolve("MOCK-incomplete-template.json");write(file,template);
        assertThrows(IllegalArgumentException.class,()->join.apply(dataset,recovery,file));
        for(var row:result.getAsJsonArray("question-blockers"))assertFalse(row.getAsJsonObject().get("answerKeyHumanVerified").getAsBoolean());
    }
    @Test void missingOrForeignDocumentImagesAreRejected() throws Exception {
        var missing=group("MOCK original document.");
        var doc=missing.getAsJsonArray("documents").get(0).getAsJsonObject();doc.remove("blocks");
        assertThrows(IllegalArgumentException.class,()->join.apply(dataset,recovery,source("MOCK-lost-image.json",new JsonArray(),arr(missing))));
        var foreign=group("MOCK original document.");foreign.getAsJsonArray("documents").get(0).getAsJsonObject().getAsJsonArray("blocks").add(obj("type","IMAGE","mediaId","foreign-media"));
        assertThrows(IllegalArgumentException.class,()->join.apply(dataset,recovery,source("MOCK-foreign-image.json",new JsonArray(),arr(foreign))));
    }
    @Test void converterKeepsExplicitDocumentMediaOrder() throws Exception {
        join.apply(dataset,recovery,source("MOCK-ordered-images.json",new JsonArray(),arr(group("MOCK original document."))));
        var draft=new V2Converter().draft(dataset);
        var group=draft.getAsJsonArray("groups").asList().stream().map(JsonElement::getAsJsonObject).filter(x->num(x,"part")==6).findFirst().orElseThrow();
        var blocks=group.getAsJsonArray("documents").get(0).getAsJsonObject().getAsJsonArray("blocks");
        assertEquals("TEXT",str(blocks.get(0).getAsJsonObject(),"type"));
        assertEquals("IMAGE",str(blocks.get(1).getAsJsonObject(),"type"));
        for(int i=0;i<blocks.size();i++)assertEquals(i+1,num(blocks.get(i).getAsJsonObject(),"orderIndex"));
    }
    @Test void htmlCannotBorrowIdentityFromAnotherExamContainer() throws Exception {
        Path file=temp.resolve("MOCK-multiple-exams.html");
        Files.writeString(file,"<main data-exam-id='ets2026-test01'></main><main data-exam-id='foreign'><article data-question-number='101' data-part='5'></article></main>");
        assertThrows(IllegalArgumentException.class,()->join.apply(dataset,recovery,file));
    }
    @Test void conflictingDocumentSupplementIsAtomic() throws Exception {
        join.apply(dataset,recovery,source("MOCK-document-first.json",new JsonArray(),arr(group("MOCK first document."))));
        var before=dataset.deepCopy();
        assertThrows(IllegalArgumentException.class,()->join.apply(dataset,recovery,source("MOCK-document-conflict.json",new JsonArray(),arr(group("MOCK different document.")))));
        assertTrue(before.equals(dataset),"Rejected supplement must leave documents unchanged");
    }
}
