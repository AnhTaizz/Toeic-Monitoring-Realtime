package vn.edu.toeic.client.admin;

import static org.assertj.core.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class AdminAccountApiClientTest {
    private HttpServer server;
    private String base;
    @BeforeEach void start() throws Exception {server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.start();base="http://127.0.0.1:"+server.getAddress().getPort();}
    @AfterEach void stop(){server.stop(0);}
    private void respond(int code,String body,AtomicInteger count) {
        server.createContext("/api/v2/admin/accounts",e->{count.incrementAndGet();
            assertThat(e.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer MOCK-token");
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");
            e.sendResponseHeaders(code,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}});
    }
    @Test void errorResponseIsMeaningfulAndCreateIsNeverRetried() throws Exception {
        var count=new AtomicInteger();respond(409,"{\"error\":{\"message\":\"Mã đã tồn tại\"}}",count);
        try(var api=new AdminAccountApiClient(base,"MOCK-token")) {
            assertThatThrownBy(()->api.create("code","Name","PROCTOR")).isInstanceOf(AdminAccountApiClient.Rejected.class)
                .hasMessage("Mã đã tồn tại");
            assertThat(count.get()).isEqualTo(1);
        }
    }
    @Test void rejectsUnexpectedRoleFromServerAndNeverExposesInitialSecretInToString() throws Exception {
        var count=new AtomicInteger();respond(200,"{\"items\":[{\"userId\":1,\"username\":\"a\",\"displayName\":\"A\",\"role\":\"ADMIN\",\"enabled\":true}]}",count);
        try(var api=new AdminAccountApiClient(base,"MOCK-token")) {
            assertThatThrownBy(()->api.list("","","",null)).isInstanceOf(IllegalStateException.class);
        }
        assertThat(new vn.edu.toeic.protocol.admin.CreatedAccount(null,"MOCK-secret").toString()).doesNotContain("MOCK-secret");
    }
    @Test void urlAndTokenCannotInjectCredentialsOrSwitchApiOrigin() {
        for(String url:new String[]{"file:///tmp/a",base+"/api",base+"?x=1","http://user:pass@localhost"})
            assertThatThrownBy(()->new AdminAccountApiClient(url,"MOCK-token")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new AdminAccountApiClient(base,"bad\r\nToken")).isInstanceOf(IllegalArgumentException.class);
    }
}
