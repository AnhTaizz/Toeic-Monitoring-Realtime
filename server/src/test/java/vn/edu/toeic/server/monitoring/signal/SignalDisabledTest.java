package vn.edu.toeic.server.monitoring.signal;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
class SignalDisabledTest {
    @Test void experimentalServiceAndRoutesAreAbsentByDefault(){new ApplicationContextRunner().withUserConfiguration(SignalService.class,SignalController.class,AlertService.class,AlertController.class).run(c->{assertThat(c).hasNotFailed();assertThat(c).doesNotHaveBean(SignalService.class);assertThat(c).doesNotHaveBean(SignalController.class);assertThat(c).doesNotHaveBean(AlertService.class);assertThat(c).doesNotHaveBean(AlertController.class);});}
    @ParameterizedTest @CsvSource({"false,false","true,false","false,true"})
    void alertReadModelRequiresBothExplicitFlags(boolean signal,boolean alert){new ApplicationContextRunner().withPropertyValues("toeic.monitoring.signal-experiment-enabled="+signal,"toeic.monitoring.alert-experiment-enabled="+alert).withUserConfiguration(AlertService.class,AlertController.class).run(c->{assertThat(c).hasNotFailed();assertThat(c).doesNotHaveBean(AlertService.class);assertThat(c).doesNotHaveBean(AlertController.class);});}
}
