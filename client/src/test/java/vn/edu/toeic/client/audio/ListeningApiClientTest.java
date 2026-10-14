package vn.edu.toeic.client.audio;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import vn.edu.toeic.protocol.audio.*;
import vn.edu.toeic.protocol.exam.*;

/** REAL loopback HTTP with MOCK exam/event fixtures. */
class ListeningApiClientTest {
    HttpServer server;ListeningApiClient api;final Gson json=new Gson();volatile String override;volatile int status=200;
    @BeforeEach void start()throws Exception{
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v1/attempts/",exchange->{try(exchange){
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer TEST_ONLY_TOKEN");
            assertThat(exchange.getRequestURI().toString()).doesNotContain("TEST_ONLY_TOKEN");
            String path=exchange.getRequestURI().getPath();String request=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);byte[] response;
            if(path.endsWith("/exam"))response=(override==null?json.toJson(exam()):override).getBytes(StandardCharsets.UTF_8);
            else{JsonObject sent=JsonParser.parseString(request).getAsJsonObject();response=(override==null?status(sent.get("requestId").getAsString(),sent.get("startId").getAsString(),sent.get("writerEpoch").getAsString()):override).getBytes(StandardCharsets.UTF_8);}
            exchange.sendResponseHeaders(status,response.length);exchange.getResponseBody().write(response);
        }});server.start();api=new ListeningApiClient("http://127.0.0.1:"+server.getAddress().getPort(),"TEST_ONLY_TOKEN");
    }
    @AfterEach void stop(){api.close();server.stop(0);}
    @Test void readsCandidateSafeQuestionMappingAndExactWriterEventAck()throws Exception{
        assertThat(api.exam("ATTEMPT").questions()).singleElement().satisfies(q->{assertThat(q.audioFile()).isEqualTo("đoạn nghe.wav");assertThat(q.options()).extracting(CandidateOptionDto::optionText).containsExactly("Một");});
        var request=new ListeningEventRequest("REQ","START",9007199254740993L,"STARTED",null);
        var ack=api.event("ATTEMPT",request);assertThat(ack.writerEpoch()).isEqualTo(9007199254740993L);assertThat(ack.phase()).isEqualTo("PLAYING");
    }
    @Test void rejectsMalformedExamUncorrelatedEventAndHttpErrors(){
        override="{broken";assertThatThrownBy(()->api.exam("ATTEMPT")).isInstanceOf(IOException.class);
        override=status("OTHER","START","2");
        assertThatThrownBy(()->api.event("ATTEMPT",new ListeningEventRequest("REQ","START",2,"STARTED",null))).isInstanceOf(IOException.class).hasMessageContaining("xác nhận");
        override=null;status=403;assertThatThrownBy(()->api.exam("ATTEMPT")).isInstanceOf(IOException.class).hasMessageContaining("quyền");
    }
    @Test void rejectsCredentialUrlAndUseAfterClose(){
        assertThatThrownBy(()->new ListeningApiClient("http://name:secret@localhost","x")).isInstanceOf(IllegalArgumentException.class);
        api.close();assertThatThrownBy(()->api.exam("ATTEMPT")).isInstanceOf(IOException.class).hasMessageContaining("đóng");
    }
    private CandidateExamDto exam(){return new CandidateExamDto("EXAM","Đề MOCK",null,1,List.of(new CandidateQuestionDto("L1","LISTENING",1,"G",null,"đoạn nghe.wav","Chọn đáp án",1,List.of(new CandidateOptionDto("A","Một",1)))));}
    private String status(String request,String start,String writer){return "{\"protocolVersion\":\"v0\",\"requestId\":\""+request+"\",\"attemptId\":\"ATTEMPT\",\"sessionId\":\"SESSION\",\"startId\":\""+start+"\",\"manifestId\":\""+"a".repeat(64)+"\",\"writerEpoch\":"+writer+",\"phase\":\"PLAYING\",\"deadlineAt\":\"2026-10-14T11:00:00Z\",\"playbackStartedAt\":\"2026-10-14T10:00:01Z\",\"interrupted\":false}";}
}
