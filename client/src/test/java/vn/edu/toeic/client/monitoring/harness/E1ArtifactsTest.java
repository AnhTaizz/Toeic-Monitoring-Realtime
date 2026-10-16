package vn.edu.toeic.client.monitoring.harness;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class E1ArtifactsTest {
    @TempDir Path directory;

    @Test void readsE1ScheduleWithoutRelaxingLegacyOrWindowValidation() throws Exception {
        var window=new E1Window(1000,15000);
        var config=HarnessFixtures.config();
        var header=HarnessFixtures.header();
        var trials=window.plan(config).stream().map(plan -> new GroundTruth.Trial(plan,
                null,null,null,null,null,null,null,null,null,null,"UNREADABLE","NOT_LAUNCHED",null,false)).toList();
        Path path=directory.resolve("ground-truth.jsonl");
        HarnessArtifacts.writeGroundTruth(path,header,trials,"INCOMPLETE");
        assertThat(HarnessArtifacts.readGroundTruth(path,window).trials()).isEqualTo(trials);
        assertThatThrownBy(()->HarnessArtifacts.readGroundTruth(path)).isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(()->HarnessArtifacts.readGroundTruth(path,new E1Window(2000,15000))).isInstanceOf(java.io.IOException.class);
    }

    @Test void legacyScheduleStillRequiresExactSeededPlan() throws Exception {
        Path path=directory.resolve("legacy.jsonl");
        var truth=HarnessFixtures.truth();
        HarnessArtifacts.writeGroundTruth(path,truth.header(),truth.trials(),"COMPLETE");
        assertThat(HarnessArtifacts.readGroundTruth(path).trials()).isEqualTo(truth.trials());
    }
}
