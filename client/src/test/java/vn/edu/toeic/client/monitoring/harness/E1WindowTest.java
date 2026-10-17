package vn.edu.toeic.client.monitoring.harness;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class E1WindowTest {
    @Test void seededPlanSameAcrossPollPeriodsAndIncludesThirtyTargets() {
        var window=new E1Window(15000,120000);var baseline=window.plan(HarnessPlan.Config.defaults());
        assertThat(baseline).hasSize(30);
        for(int duration:new int[]{200,800,3000})assertThat(baseline.stream().filter(t->t.targetMillis()==duration).count()).isEqualTo(10);
        for(int poll:new int[]{250,500,1000,2000})assertThat(window.plan(new HarnessPlan.Config(poll,1234,10,2,3000,1500,180000,"UNRECORDED"))).isEqualTo(baseline);
        assertThat(baseline).allMatch(t->window.contains(t.plannedLaunchElapsedNanos(),t.plannedLaunchElapsedNanos()+6000000000L));
    }
    @Test void censorsIncompleteWindowsAndRejectsDenseSchedule() {
        var window=new E1Window(1000,15000);
        assertThat(window.contains(999999999L,2000000000L)).isFalse();
        assertThat(window.contains(1000000000L,16000000000L)).isTrue();
        assertThat(window.contains(2000000000L,16000000001L)).isFalse();
        assertThatThrownBy(()->window.plan(HarnessPlan.Config.defaults())).isInstanceOf(IllegalArgumentException.class);
    }
}
