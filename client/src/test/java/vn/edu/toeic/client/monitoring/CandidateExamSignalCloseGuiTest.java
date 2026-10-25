package vn.edu.toeic.client.monitoring;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.edu.toeic.client.realtime.RealtimeClient;

/** REAL JavaFX observers, MOCK attempt/run; the realtime client never connects. No OS input. */
@EnabledIfEnvironmentVariable(named="TOEIC_MONITORING_SCOPE_GUI",matches="true")
class CandidateExamSignalCloseGuiTest {
    static <T>T fx(Callable<T> work)throws Exception{var f=new CompletableFuture<T>();Platform.runLater(()->{try{f.complete(work.call());}catch(Throwable t){f.completeExceptionally(t);}});return f.get(8,TimeUnit.SECONDS);}

    @Test void closeFromSocketThreadStopsTypedLaneWhileJavaFxThreadIsBusy()throws Exception {
        var ready=new CountDownLatch(1);
        try{Platform.startup(ready::countDown);}catch(IllegalStateException running){ready.countDown();}assertThat(ready.await(8,TimeUnit.SECONDS)).isTrue();Platform.setImplicitExit(false);
        String previous=System.getProperty(CandidateExamSignalMonitoring.ENABLE_PROPERTY);System.setProperty(CandidateExamSignalMonitoring.ENABLE_PROPERTY,"true");
        var busy=new CountDownLatch(1);
        try(var realtime=new RealtimeClient("http://127.0.0.1:9","MOCK-token")){
            var stage=fx(Stage::new);
            var monitor=fx(()->CandidateExamSignalMonitoring.start(stage,"http://127.0.0.1:9","MOCK-token","MOCK-attempt","MOCK-run",realtime));
            assertThat(monitor.deliveryStatus().active()).isTrue();
            var entered=new CountDownLatch(1);
            Platform.runLater(()->{entered.countDown();try{busy.await(20,TimeUnit.SECONDS);}catch(InterruptedException stop){Thread.currentThread().interrupt();}});
            assertThat(entered.await(8,TimeUnit.SECONDS)).isTrue();
            // Same thread kind as a socket rejection callback; it must not wait for the blocked FX thread.
            CompletableFuture.runAsync(monitor::close).get(2,TimeUnit.SECONDS);
            assertThat(monitor.deliveryStatus().active()).isFalse();
            assertThat(monitor.awaitStopped(Duration.ofSeconds(2))).isTrue();
            busy.countDown();
            // Queued listener/timeline detach runs on FX afterwards; later window changes are dropped quietly.
            fx(()->{stage.setIconified(true);stage.setFullScreen(true);return null;});
            assertThat(monitor.deliveryStatus().queued()).isZero();
            fx(()->{monitor.close();stage.close();return null;});
        }finally{
            busy.countDown();
            if(previous==null)System.clearProperty(CandidateExamSignalMonitoring.ENABLE_PROPERTY);else System.setProperty(CandidateExamSignalMonitoring.ENABLE_PROPERTY,previous);
        }
    }
}
