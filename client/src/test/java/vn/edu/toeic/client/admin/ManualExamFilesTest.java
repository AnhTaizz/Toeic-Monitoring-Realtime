package vn.edu.toeic.client.admin;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.toeic.protocol.admin.ManualExamDraft;

class ManualExamFilesTest {
    @TempDir Path temporary;
    @Test void portableDraftRoundTripsPartialContentAndLocalAudioWithoutAbsolutePaths() throws Exception {
        var document=new Gson().toJsonTree(ManualExamDraft.empty()).getAsJsonObject();
        var q=document.getAsJsonArray("questions").get(0).getAsJsonObject();q.addProperty("prompt","Đang soạn dở");
        Path local=temporary.resolve("âm thanh câu 1.WAV");byte[] bytes="RIFF1234WAVEtest".getBytes();Files.write(local,bytes);
        try(var files=new ManualExamFiles();var reopened=new ManualExamFiles()) {
            files.select(q,local);Path bundle=files.bundle(document);
            var loaded=reopened.open(bundle);
            assertThat(loaded.getAsJsonArray("questions")).hasSize(200);
            assertThat(loaded.toString()).doesNotContain(temporary.toString());
            var first=loaded.getAsJsonArray("questions").get(0).getAsJsonObject();
            assertThat(first.get("prompt").getAsString()).isEqualTo("Đang soạn dở");
            assertThat(first.get("audioFile").getAsString()).matches("001-[a-f0-9]{64}\\.wav");
            try(var zip=new ZipFile(reopened.bundle(loaded).toFile())) {
                assertThat(zip.getInputStream(zip.getEntry("audio/"+first.get("audioFile").getAsString())).readAllBytes()).isEqualTo(bytes);
            }
            reopened.remove(first);assertThat(reopened.hasAudio()).isFalse();
        }
    }
    @Test void rejectsTraversalAndUnsupportedFiles() throws Exception {
        Path zip=temporary.resolve("bad.zip");
        try(var out=new ZipOutputStream(Files.newOutputStream(zip))) {out.putNextEntry(new ZipEntry("../outside.wav"));out.write(1);}
        try(var files=new ManualExamFiles()) {
            assertThatThrownBy(()->files.open(zip)).hasMessageContaining("không hợp lệ");
            assertThatThrownBy(()->files.select(new JsonObject(),temporary.resolve("bad.exe"))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(Files.exists(temporary.resolve("outside.wav"))).isFalse();
    }
    @Test void rejectsMalformedDraftBeforeOpeningTheEditor() throws Exception {
        Path zip=temporary.resolve("malformed.zip");
        try(var out=new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("manual-draft.json"));out.write("{\"questions\":[]}".getBytes());
        }
        try(var files=new ManualExamFiles()) {
            assertThatThrownBy(()->files.open(zip)).hasMessageContaining("200 câu TOEIC");
        }
    }
}
