package vn.edu.toeic.server.monitoring.signal;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
class SignalDisabledTest {
    @Test void experimentalServiceAndRoutesAreAbsentByDefault(){new ApplicationContextRunner().withUserConfiguration(SignalService.class,SignalController.class).run(c->{assertThat(c).hasNotFailed();assertThat(c).doesNotHaveBean(SignalService.class);assertThat(c).doesNotHaveBean(SignalController.class);});}
}
