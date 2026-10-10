package vn.edu.toeic.client.monitoring.harness;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HarnessArtifactsTest {
    @TempDir Path directory;
    @Test void roundTripPreservesIndependentGroundTruthAndMeasuredDurations()throws Exception {
        var truth=HarnessFixtures.truth();Path file=directory.resolve("truth.jsonl");String checksum=HarnessArtifacts.writeGroundTruth(file,truth.header(),truth.trials(),"COMPLETE");
        var read=HarnessArtifacts.readGroundTruth(file);assertThat(read.trials()).isEqualTo(truth.trials());assertThat(read.header()).isEqualTo(truth.header());assertThat(read.checksum()).isEqualTo(checksum);
    }
    @Test void checksumCorruptionTruncationAndDuplicateFieldAreRejected()throws Exception {
        var truth=HarnessFixtures.truth();Path file=directory.resolve("truth.jsonl");HarnessArtifacts.writeGroundTruth(file,truth.header(),truth.trials(),"COMPLETE");String original=Files.readString(file);
        for(String bad:java.util.List.of(original.replace("java.exe","javaw.exe"),original.substring(0,original.lastIndexOf("{\"kind\":\"FINAL\"")),original.replace("\"kind\":\"HEADER\"","\"kind\":\"HEADER\",\"kind\":\"HEADER\""))) {
            Files.writeString(file,bad);assertThatThrownBy(() -> HarnessArtifacts.readGroundTruth(file)).isInstanceOf(java.io.IOException.class).hasMessageContaining("GROUND_TRUTH line");
        }
    }
    @Test void existingOutputAndUnsupportedOversizeAreRejected()throws Exception {
        Path file=directory.resolve("existing.json");Files.writeString(file,"original");
        assertThatThrownBy(() -> HarnessArtifacts.writeJson(file,"new")).isInstanceOf(java.io.IOException.class);assertThat(Files.readString(file)).isEqualTo("original");
        Files.write(file,new byte[1024*1024+1]);assertThatThrownBy(() -> HarnessArtifacts.readGroundTruth(file)).isInstanceOf(java.io.IOException.class);
    }
    @Test void manifestDetectsTamperingAndIncompleteRun()throws Exception {
        for(String name:java.util.List.of("ground-truth.jsonl","observations.jsonl","metadata.json","join.json"))Files.writeString(directory.resolve(name),"MOCK");
        HarnessArtifacts.manifest(directory,"COMPLETE");assertThat(HarnessArtifacts.verifyManifest(directory)).isTrue();
        Files.writeString(directory.resolve("observations.jsonl"),"tampered");assertThat(HarnessArtifacts.verifyManifest(directory)).isFalse();
    }
}
