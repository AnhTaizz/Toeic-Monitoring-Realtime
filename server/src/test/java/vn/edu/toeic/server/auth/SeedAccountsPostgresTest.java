package vn.edu.toeic.server.auth;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.*;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * Real PostgreSQL/Flyway/HTTP. The same owned schema is started four times: release default (demo off),
 * development opt-in, release default again, opt-in with another password. MOCK accounts and passwords.
 */
@EnabledIfEnvironmentVariable(named="TOEIC_PG_INTEGRATION",matches="true")
class SeedAccountsPostgresTest {
    private static final String DEMO="('candidate1','candidate2','proctor1')";
    @TempDir Path temporary;
    private String url,user,password,schema,base;
    private Connection admin,db;
    private ConfigurableApplicationContext app;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final String adminPassword="MOCK-seed-test-admin-"+UUID.randomUUID();

    @BeforeEach void database() throws Exception {
        url="jdbc:postgresql://"+env("DB_HOST")+":"+env("DB_PORT")+"/"+env("DB_NAME");
        user=env("DB_USER");password=env("DB_PASSWORD");schema="seed_accounts_"+UUID.randomUUID().toString().replace("-","");
        admin=DriverManager.getConnection(url,user,password);
        try(var s=admin.createStatement()){s.execute("CREATE SCHEMA "+schema);}
        db=DriverManager.getConnection(url+"?currentSchema="+schema,user,password);
    }
    @AfterEach void cleanup() throws Exception {
        try {stop();http.close();if(db!=null)db.close();}
        finally {if(admin!=null){try(var s=admin.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}admin.close();}}
    }

    @Test void releaseDefaultCreatesNoDemoAccountsAndNeverChangesExistingOnes() throws Exception {
        String firstSeed="MOCK-seed-"+UUID.randomUUID(),otherSeed="MOCK-seed-"+UUID.randomUUID();
        // Fresh database, release default: only the operator's Admin exists; no demo password logs in.
        start(null,null);
        assertThat(text("SELECT string_agg(username||':'||role,',' ORDER BY id) FROM user_accounts")).isEqualTo("seed-test-admin:ADMIN");
        assertThat(login("seed-test-admin",adminPassword)).isEqualTo(200);
        assertThat(login("proctor1",firstSeed)).isEqualTo(401);
        stop();
        // Explicit development opt-in on the same database creates the three demo accounts.
        start("true",firstSeed);
        assertThat(text("SELECT count(*) FROM user_accounts WHERE username IN "+DEMO)).isEqualTo("3");
        assertThat(login("proctor1",firstSeed)).isEqualTo(200);
        String digest=digest();
        stop();
        // Back to the release default: existing accounts stay exactly as they were.
        start(null,null);
        assertThat(digest()).isEqualTo(digest);
        assertThat(login("proctor1",firstSeed)).isEqualTo(200);
        stop();
        // Opt-in again with another password does not overwrite existing hashes.
        start("true",otherSeed);
        assertThat(digest()).isEqualTo(digest);
        assertThat(login("candidate1",otherSeed)).isEqualTo(401);
        assertThat(login("seed-test-admin",adminPassword)).isEqualTo(200);
    }

    private void start(String demoAccounts,String seedPassword) throws Exception {
        Files.createDirectories(temporary.resolve("audio"));Files.createDirectories(temporary.resolve("images"));
        var props=new HashMap<String,Object>();
        // Highest precedence: overrides the test-JVM opt-in from server/pom.xml for the release-default runs.
        props.put("toeic.seed.demo-accounts",demoAccounts==null?"false":demoAccounts);props.put("toeic.seed.password",seedPassword==null?"":seedPassword);
        props.put("toeic.admin.bootstrap.username","seed-test-admin");props.put("toeic.admin.bootstrap.password",adminPassword);
        props.put("server.port","0");props.put("server.address","127.0.0.1");
        props.put("spring.datasource.url",url+"?currentSchema="+schema);props.put("spring.datasource.username",user);props.put("spring.datasource.password",password);
        props.put("spring.flyway.schemas",schema);props.put("spring.flyway.default-schema",schema);
        props.put("toeic.audio.root",temporary.resolve("audio").toString());props.put("toeic.image.root",temporary.resolve("images").toString());
        props.put("exam.timeout.scheduler.enabled","false");props.put("toeic.listening.maintenance-enabled","false");props.put("logging.level.root","ERROR");
        var spring=new SpringApplication(vn.edu.toeic.server.ToeicServerApplication.class);spring.setBannerMode(Banner.Mode.OFF);
        spring.addInitializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("owned-seed-test",props)));
        app=spring.run();base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();
    }
    private void stop() {if(app!=null){app.close();app=null;}}
    private int login(String username,String secret) throws Exception {
        String body="{\"username\":\""+username+"\",\"password\":\""+secret+"\",\"requestId\":\""+UUID.randomUUID()+"\"}";
        return http.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/auth/login")).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString()).statusCode();
    }
    /** Identity, role, enabled flag and credential hash of every account; compared as a whole. */
    private String digest() throws SQLException {return text("SELECT md5(string_agg(id||':'||username||':'||role||':'||enabled||':'||password_hash,'|' ORDER BY id)) FROM user_accounts");}
    private String text(String sql) throws SQLException {try(var s=db.createStatement();var r=s.executeQuery(sql)){r.next();return r.getString(1);}}
    private static String env(String name){String v=System.getenv(name);if(v==null||v.isBlank())throw new IllegalStateException("Missing test setting "+name);return v;}
}
