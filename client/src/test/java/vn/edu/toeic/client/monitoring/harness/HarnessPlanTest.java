package vn.edu.toeic.client.monitoring.harness;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class HarnessPlanTest {
    @Test void sameSeedReproducesHandCheckedCardinalityDurationAndPhaseLimits() {
        var config=HarnessPlan.Config.defaults();var plan=HarnessPlan.create(config);
        assertThat(plan).isEqualTo(HarnessPlan.create(config)).hasSize(30);
        for(int target:List.of(200,800,3000))assertThat(plan.stream().filter(t -> t.targetMillis()==target)).hasSize(10);
        assertThat(plan).allMatch(t -> t.phaseMillis()>=0&&t.phaseMillis()<500);
        assertThat(plan.stream().map(HarnessPlan.Trial::trialId)).doesNotHaveDuplicates();
        assertThat(plan.stream().map(HarnessPlan.Trial::plannedLaunchElapsedNanos)).isSorted();
        var first=plan.getFirst();assertThat(first.plannedLaunchElapsedNanos()).isEqualTo((1000L+first.phaseMillis())*1000000);
    }
    @Test void anotherSeedChangesPlanButNotWorkload() {
        var a=HarnessPlan.Config.defaults();var b=new HarnessPlan.Config(500,77,10,2,3000,1500,180000,"UNRECORDED");
        assertThat(HarnessPlan.create(a)).isNotEqualTo(HarnessPlan.create(b));
        assertThat(HarnessPlan.create(b)).hasSize(30);
    }
    @Test void seedSevenSmallPlanMatchesWrittenExpectedSchedule() {
        assertThat(HarnessPlan.create(HarnessFixtures.config())).containsExactly(
                new HarnessPlan.Trial("trial-002",3000,44,244_000_000L),
                new HarnessPlan.Trial("trial-003",800,80,280_000_000L),
                new HarnessPlan.Trial("trial-001",200,85,285_000_000L));
    }
    @Test void rejectsUnsafeLimitsAndPlanThatCannotFit() {
        assertThatThrownBy(() -> new HarnessPlan.Config(0,1,10,2,3000,1500,180000,"UNRECORDED")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HarnessPlan.Config(100,1,31,2,3000,1500,180000,"UNRECORDED")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HarnessPlan.Config(100,1,10,5,3000,1500,180000,"UNRECORDED")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HarnessPlan.create(new HarnessPlan.Config(500,1,10,2,3000,1500,5000,"UNRECORDED"))).isInstanceOf(IllegalArgumentException.class);
    }
}
