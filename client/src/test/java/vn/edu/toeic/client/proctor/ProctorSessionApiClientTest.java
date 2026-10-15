package vn.edu.toeic.client.proctor;

import static org.assertj.core.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

/** REAL loopback HTTP, MOCK server responses. No production authorization claims. */
class ProctorSessionApiClientTest {
    private HttpServer server;private ExecutorService worker;private ProctorSessionApiClient api;
    private volatile int code=200;private volatile String body="{}",authorization;
    @BeforeEach void start()throws Exception{
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);worker=Executors.newCachedThreadPool();server.setExecutor(worker);
        server.createContext("/",x->{authorization=x.getRequestHeaders().getFirst("Authorization");byte[] b=body.getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(code,b.length);try(var output=x.getResponseBody()){output.write(b);}});server.start();
        api=new ProctorSessionApiClient("http://127.0.0.1:"+server.getAddress().getPort(),"TEST-only-token",Duration.ofSeconds(2));
    }
    @AfterEach void stop(){api.close();server.stop(0);worker.shutdownNow();}
    @Test void exactLongsAndFinalResultArePreserved()throws Exception{
        body="{\"attemptId\":\"OLD\",\"sessionId\":\"S\",\"examId\":\"E\",\"writerEpoch\":9007199254740993,\"savedRevision\":9007199254740994,\"state\":\"TIMED_OUT\",\"totalQuestions\":1,\"score\":990,\"answers\":{\"L\":\"A\"},\"listeningInterrupted\":true}";
        var r=api.result("OLD");assertThat(r.writerEpoch()).isEqualTo(9007199254740993L);assertThat(r.savedRevision()).isEqualTo(9007199254740994L);assertThat(authorization).isEqualTo("Bearer TEST-only-token");
        body=body.replace("9007199254740993","9007199254740993.0");assertThatThrownBy(()->api.result("OLD")).hasMessageContaining("không hợp lệ");
    }
    @Test void malformedAndOversizedBodiesAreNotSuccess(){
        body="{bad";assertThatThrownBy(()->api.readiness("S")).isInstanceOf(java.io.IOException.class);
        body="x".repeat(2*1024*1024+1);assertThatThrownBy(()->api.readiness("S")).isInstanceOf(Exception.class);
    }
    @Test void permissionAndManifestErrorsHaveSafeMessages(){
        code=403;body="{\"code\":\"FORBIDDEN\",\"message\":\"secret-server-data\"}";
        assertThatThrownBy(()->api.readiness("S")).hasMessageContaining("phân công").hasMessageNotContaining("secret-server-data");
        code=409;body="{\"code\":\"CONFLICT\"}";assertThatThrownBy(()->api.readiness("S")).hasMessageContaining("READY").hasMessageContaining("CONFLICT");
    }
    @Test void completeResponseDeadlineAlsoBoundsSlowBody()throws Exception{
        var release=new CountDownLatch(1);server.removeContext("/");server.createContext("/",x->{x.sendResponseHeaders(200,100);try{release.await(4,TimeUnit.SECONDS);}catch(InterruptedException ignored){}finally{x.close();}});
        try(var quick=new ProctorSessionApiClient("http://127.0.0.1:"+server.getAddress().getPort(),"TEST",Duration.ofMillis(200))){
            assertThatThrownBy(()->quick.readiness("S")).isInstanceOf(TimeoutException.class);
        }finally{release.countDown();}
    }
    @Test void wrongSessionOrContradictoryAllReadyIsRejected(){
        body="{\"protocolVersion\":\"v0\",\"sessionId\":\"OTHER\",\"manifestId\":\""+"a".repeat(64)+"\",\"allReady\":true,\"attempts\":[]}";
        assertThatThrownBy(()->api.readiness("S")).hasMessageContaining("không hợp lệ");
        body=body.replace("OTHER","S");assertThatThrownBy(()->api.readiness("S")).hasMessageContaining("không hợp lệ");
    }
    @Test void existingLongSessionIsReadableWithoutRelaxingNewIdRules()throws Exception{
        String existing="AUDIO-S-"+java.util.UUID.randomUUID();
        body="{\"protocolVersion\":\"v0\",\"sessionId\":\""+existing+"\",\"manifestId\":\""+"a".repeat(64)+"\",\"allReady\":false,\"attempts\":[]}";
        assertThat(api.readiness(existing).sessionId()).isEqualTo(existing);
        assertThatThrownBy(()->ProctorInputs.id(existing)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void newIdentifiersCannotSmugglePathOrProduceOversizedAttempt(){
        assertThatThrownBy(()->ProctorInputs.id("../unsafe")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ProctorInputs.session("S","E","Title","60","bad/user","")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ProctorInputs.session("S","E","Title","0","candidate1","")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void importSchemaRejectsUnknownFieldsAndIncorrectAnswer(){
        String valid="{\"examId\":\"E\",\"title\":\"TEST\",\"questions\":[{\"questionId\":\"L\",\"section\":\"LISTENING\",\"part\":1,\"prompt\":\"TEST\",\"audioFile\":\"sample.wav\",\"correctOption\":\"A\",\"options\":[{\"optionId\":\"A\",\"optionText\":\"One\"},{\"optionId\":\"B\",\"optionText\":\"Two\"}]}]}";
        assertThat(ProctorInputs.parseExam(valid).questions()).hasSize(1);
        assertThatThrownBy(()->ProctorInputs.parseExam(valid.replace("\"A\",\"options\"","\"C\",\"options\""))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ProctorInputs.parseExam(valid.replace("\"title\"","\"unknown\""))).hasMessageContaining("schema");
        assertThatThrownBy(()->ProctorInputs.parseExam(valid.replace("\"part\":1","\"part\":1.5"))).hasMessageContaining("schema");
        assertThatThrownBy(()->ProctorInputs.parseExam(valid.replace("\"examId\":\"E\"","\"examId\":1"))).hasMessageContaining("schema");
    }
}
