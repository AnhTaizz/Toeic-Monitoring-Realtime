package vn.edu.toeic.client.admin;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.toeic.protocol.admin.ManualExamDraft;

class ManualExamFilesTest {
    @TempDir Path temporary;
    @Test void uploadContainsAudioAndImagesWithoutLeakingLocalPathsOrMutatingEditor() throws Exception {
        var document=new Gson().toJsonTree(ManualExamDraft.empty()).getAsJsonObject();
        var q=document.getAsJsonArray("questions").get(0).getAsJsonObject();
        Path audio=temporary.resolve("âm thanh câu 1.WAV"),image=temporary.resolve("hình ảnh.PNG");
        byte[] bytes="RIFF1234WAVEtest".getBytes();Files.write(audio,bytes);Files.write(image,new byte[]{1,2,3});
        try(var files=new ManualExamFiles()) {
            files.select(q,audio);files.selectImages(q,List.of(image));
            try(var zip=new ZipFile(files.bundle(document).toFile())) {
                var uploaded=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("manual-draft.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                var first=uploaded.getAsJsonArray("questions").get(0).getAsJsonObject();
                assertThat(uploaded.toString()).doesNotContain(temporary.toString());
                assertThat(first.get("audioFile").getAsString()).matches("001-[a-f0-9]{64}\\.wav");
                assertThat(zip.getInputStream(zip.getEntry("audio/"+first.get("audioFile").getAsString())).readAllBytes()).isEqualTo(bytes);
                assertThat(zip.getEntry("images/"+first.getAsJsonArray("imageFiles").get(0).getAsString())).isNotNull();
                assertThat(zip.size()).isEqualTo(3);
            }
            assertThat(q.get("audioFile").getAsString()).isEqualTo(audio.getFileName().toString());
            files.remove(q);files.removeImages(q);assertThat(files.hasFiles()).isFalse();
        }
    }
    @Test void aSharedConversationUploadsOnlyOneAudioFile() throws Exception {
        var document=new Gson().toJsonTree(ManualExamDraft.empty()).getAsJsonObject();
        Path audio=temporary.resolve("conversation.mp3");Files.write(audio,"ID3test audio".getBytes());
        try(var files=new ManualExamFiles()) {
            for(int n=31;n<34;n++)files.select(document.getAsJsonArray("questions").get(n).getAsJsonObject(),audio);
            try(var zip=new ZipFile(files.bundle(document).toFile())){assertThat(zip.size()).isEqualTo(2);assertThat(zip.stream().anyMatch(e->e.getName().startsWith("audio/032-"))).isTrue();}
        }
    }
    @Test void disallowsUnsupportedTypesAndMultiplePartOneImages() throws Exception {
        var q=new Gson().toJsonTree(ManualExamDraft.empty()).getAsJsonObject().getAsJsonArray("questions").get(0).getAsJsonObject();
        try(var files=new ManualExamFiles()) {
            assertThatThrownBy(()->files.select(q,temporary.resolve("file.exe"))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->files.selectImages(q,List.of(temporary.resolve("a.png"),temporary.resolve("b.png")))).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
