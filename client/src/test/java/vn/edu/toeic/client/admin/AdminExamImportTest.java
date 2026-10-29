package vn.edu.toeic.client.admin;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class AdminExamImportTest {
    @TempDir Path temporary;
    @Test void crawlWithLocalMediaIsConvertedWithoutACompanionClientExport() throws Exception {
        Path file=mockCrawl(temporary);
        Files.writeString(temporary.resolve("client_exam.json"),"outdated companion; the selected server export is authoritative");
        try(var prepared=AdminExamImport.load(file)) {
            var tree=AdminExamImport.parse(prepared.json);
            assertThat(tree.getAsJsonArray("questions")).hasSize(200);
            assertThat(tree.get("schemaVersion").getAsInt()).isEqualTo(2);
            assertThat(tree.getAsJsonArray("media").size()).isGreaterThan(50);
            Path zip=prepared.bundle(prepared.json.replace("MOCK crawl","Edited title"));
            try(var archive=new ZipFile(zip.toFile())) {
                assertThat(archive.getEntry("rich-import.json")).isNotNull();
                assertThat(archive.getEntry("audio/MOCK-CRAWL/001-aud-001.wav")).isNotNull();
                assertThat(new String(archive.getInputStream(archive.getEntry("rich-import.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).contains("Edited title");
            }
        }
        assertThat(Files.exists(file)).isTrue();assertThat(Files.exists(temporary.resolve("media/audio/1.wav"))).isTrue();
    }
    @Test void missingLocalMediaAndInvalidJsonHaveActionableErrors() throws Exception {
        Path file=mockCrawl(temporary);Files.delete(temporary.resolve("media/audio/1.wav"));
        assertThatThrownBy(()->AdminExamImport.load(file)).hasMessageContaining("media").hasMessageContaining("200");
        assertThatThrownBy(()->AdminExamImport.parse("{bad json")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void renamedExamRepackagesEveryMediaUnderTheNewIdWithoutChangingItsBytes() throws Exception {
        try(var prepared=AdminExamImport.load(mockCrawl(temporary));var original=new ZipFile(prepared.bundle.toFile())) {
            var graph=AdminExamImport.parse(prepared.json);graph.getAsJsonObject("exam").addProperty("examId","oicaidjt");
            Path file=prepared.bundle(graph.toString());
            try(var renamed=new ZipFile(file.toFile())) {
                for(var media:graph.getAsJsonArray("media")) {
                    var item=media.getAsJsonObject();String folder=item.get("mediaType").getAsString().equals("AUDIO")?"audio/":"images/";
                    String suffix="/"+item.get("localFile").getAsString();
                    var old=original.getEntry(folder+"MOCK-CRAWL"+suffix);var updated=renamed.getEntry(folder+"oicaidjt"+suffix);
                    assertThat(updated).isNotNull();
                    assertThat(renamed.getInputStream(updated).readAllBytes()).isEqualTo(original.getInputStream(old).readAllBytes());
                    assertThat(renamed.getEntry(folder+"MOCK-CRAWL"+suffix)).isNull();
                }
            }
        }
    }
    @Test void changingAMediaFilenameFailsBeforeUploadingWithTheMissingName() throws Exception {
        try(var prepared=AdminExamImport.load(mockCrawl(temporary))) {
            var graph=AdminExamImport.parse(prepared.json);graph.getAsJsonArray("media").get(0).getAsJsonObject().addProperty("localFile","missing-audio.wav");
            assertThatThrownBy(()->prepared.bundle(graph.toString())).hasMessageContaining("missing-audio.wav").hasMessageContaining("chọn lại");
        }
    }
    public static Path mockCrawl(Path root) throws Exception {
        Path assets=Path.of("..","docs","plan-v2","fixtures","ex02","assets");
        Path wave=assets.resolve("audio/mock-ex02-200/001-g001.wav"),image=assets.resolve("images/mock-ex02-200/mock-grid.png");
        Files.createDirectories(root.resolve("media/audio"));Files.createDirectories(root.resolve("media/images"));
        Files.copy(image,root.resolve("media/images/mock.png"));
        var json=new JsonObject();json.addProperty("id","MOCK-CRAWL");json.addProperty("title","MOCK crawl");json.addProperty("totalQuestions",200);
        var items=new JsonArray();json.add("items",items);int number=0;
        int[] counts={6,25,39,30,30,16,54};
        for(int part=1;part<=7;part++)for(int index=0;index<counts[part-1];) {
            var item=new JsonObject();item.addProperty("part",part);item.addProperty("passage",part>=6?"MOCK passage":null);
            if(part<=4){String path="media/audio/"+(number+1)+".wav";Files.copy(wave,root.resolve(path));item.addProperty("audioPath",path);}
            var images=new JsonArray();if(part==1)images.add("media/images/mock.png");item.add("imagePaths",images);
            var questions=new JsonArray();item.add("questions",questions);int size=part==3||part==4||part==7?3:part==6?4:1;
            for(int n=0;n<size;n++){number++;index++;var q=new JsonObject();q.addProperty("id","q"+number);q.addProperty("part",part);q.addProperty("content","MOCK question "+number);q.addProperty("correctAnswer","A");
                var options=new JsonArray();q.add("options",options);for(int option=0;option<(part==2?3:4);option++){var o=new JsonObject();o.addProperty("key",String.valueOf((char)('A'+option)));o.addProperty("text","MOCK option "+option);options.add(o);}questions.add(q);}
            items.add(item);
        }
        Path file=root.resolve("server_exam.json");Files.writeString(file,json.toString());return file;
    }
}
