package vn.edu.toeic.client.proctor.redesign;

import static org.assertj.core.api.Assertions.*;
import static vn.edu.toeic.client.proctor.redesign.FxSupport.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.edu.toeic.protocol.auth.LoginResponse;

/** Real themed JavaFX controls + loopback HTTP stub. Real backend flow is RoomFlowPostgresGuiTest. */
@EnabledIfEnvironmentVariable(named="TOEIC_CANDIDATE_GUI",matches="true")
class AdminAccountGuiTest {
    private HttpServer server; private Stage stage; private AdminAccountView view;
    private final List<String> requests=new CopyOnWriteArrayList<>(), bodies=new CopyOnWriteArrayList<>();
    private final AtomicInteger postStatus=new AtomicInteger(409),getStatus=new AtomicInteger(200),expired=new AtomicInteger();
    private final AtomicBoolean dropped=new AtomicBoolean();
    @BeforeAll static void toolkit(){FxSupport.start();}
    @BeforeEach void open() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/v2/admin/accounts",e->{
            requests.add(e.getRequestMethod()+" "+e.getRequestURI());
            if(!"GET".equals(e.getRequestMethod()))bodies.add(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));int code="GET".equals(e.getRequestMethod())?getStatus.get():postStatus.get();
            if(dropped.get()){e.close();return;}
            String text=code==200?"{\"items\":[{\"userId\":10,\"username\":\"P-GUI\",\"displayName\":\"Nguyễn An\",\"role\":\"PROCTOR\",\"enabled\":true}],\"nextAfterUserId\":10}"
                :"{\"error\":{\"message\":\"Mã đã tồn tại\"}}";
            byte[] bytes=text.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(code,bytes.length);
            try(var out=e.getResponseBody()){out.write(bytes);}
        });server.start();
        view=fx(()->new AdminAccountView("http://127.0.0.1:"+server.getAddress().getPort(),login("ADMIN"),()->{},expired::incrementAndGet));
        stage=fx(()->{Stage s=new Stage();s.setScene(new Scene(view,1366,820));s.show();return s;});
        await("accounts loaded",()->!view.lookup("#admin-find").isDisabled());
    }
    @AfterEach void cleanup() throws Exception {fxRun(()->{view.close();stage.close();});server.stop(0);}
    private static LoginResponse login(String role){return new LoginResponse("v0","MOCK","MOCK","MOCK-token","Bearer","2099-01-01T00:00:00Z",new LoginResponse.UserView(1,"admin","Quản trị viên",role),List.of());}
    private static Node dialog(String id){for(Window w:Window.getWindows())if(w.isShowing()&&w.getScene()!=null){Node n=w.getScene().lookup("#"+id);if(n!=null)return n;}return null;}
    private void form() throws Exception {fxRun(()->((Button)view.lookup("#admin-add")).fire());await("form",()->dialog("admin-create-code")!=null);}
    @Test void sharedThemeLayoutFiltersAndPaginationAreUsable() throws Exception {
        fxRun(()->{view.applyCss();view.layout();});
        assertThat(fx(()->view.getStyleClass())).contains("proctor-redesign");
        assertThat(fx(()->view.getStylesheets().getFirst())).endsWith("proctor-redesign.css");
        assertThat(fx(()->view.lookup("#proctor-redesign"))).isNull();
        fxRun(()->{((TextField)view.lookup("#admin-search")).setText("Nguyễn");
            ((ComboBox<?>)view.lookup("#admin-role-filter")).getSelectionModel().select(1);
            ((ComboBox<?>)view.lookup("#admin-enabled-filter")).getSelectionModel().select(2);((Button)view.lookup("#admin-find")).fire();});
        await("filter completed",()->!view.lookup("#admin-find").isDisabled());
        assertThat(requests.getLast()).contains("role=PROCTOR","enabled=false","search=Nguy");
        fxRun(()->((Button)view.lookup("#admin-next")).fire());await("next completed",()->!view.lookup("#admin-find").isDisabled());
        assertThat(requests.getLast()).contains("afterUserId=10");
    }
    @Test void duplicateAndInvalidInputAreVisibleWithoutLosingFormData() throws Exception {
        form();fxRun(()->((Button)dialog("admin-create-save")).fire());
        assertThat(fx(()->((Label)dialog("admin-create-status")).getText())).contains("Nhập mã hợp lệ");
        fxRun(()->{((TextField)dialog("admin-create-code")).setText("DUPLICATE");((TextField)dialog("admin-create-name")).setText("Nguyễn An");
            ((PasswordField)dialog("admin-create-password")).setText("MOCK-pass-123");((PasswordField)dialog("admin-create-password-confirm")).setText("MOCK-pass-123");
            ((Button)dialog("admin-create-save")).fire();});
        await("duplicate error",()->((Label)dialog("admin-create-status")).getText().contains("đã tồn tại"));
        assertThat(fx(()->((TextField)dialog("admin-create-name")).getText())).isEqualTo("Nguyễn An");
        assertThat(requests.stream().filter(x->x.startsWith("POST")).count()).isEqualTo(1);
        assertThat(bodies.getLast()).contains("\"password\":\"MOCK-pass-123\"");
    }
    @Test void adminTypesThePasswordTwiceAndShortOrDifferentOnesAreNotSent() throws Exception {
        form();
        fxRun(()->{((TextField)dialog("admin-create-code")).setText("NEW-CAND");((TextField)dialog("admin-create-name")).setText("Nguyễn An");
            ((PasswordField)dialog("admin-create-password")).setText("short");((PasswordField)dialog("admin-create-password-confirm")).setText("short");
            ((Button)dialog("admin-create-save")).fire();});
        assertThat(fx(()->((Label)dialog("admin-create-status")).getText())).contains("từ 8 đến 128 ký tự");
        fxRun(()->{((PasswordField)dialog("admin-create-password")).setText("MOCK-pass-123");((PasswordField)dialog("admin-create-password-confirm")).setText("MOCK-pass-124");
            ((Button)dialog("admin-create-save")).fire();});
        assertThat(fx(()->((Label)dialog("admin-create-status")).getText())).contains("không khớp");
        assertThat(requests.stream().filter(x->x.startsWith("POST"))).isEmpty();
        assertThat(AdminAccountView.passwordProblem("MOCK-pass-123","MOCK-pass-123")).isNull();
    }
    @Test void networkFailureAndSessionExpiryDoNotBecomeSuccess() throws Exception {
        dropped.set(true);fxRun(()->((Button)view.lookup("#admin-find")).fire());
        await("network error",()->((Label)view.lookup("#admin-status")).getText().contains("Không kết nối"));
        dropped.set(false);getStatus.set(401);fxRun(()->((Button)view.lookup("#admin-find")).fire());
        await("session expiry",()->expired.get()==1);
    }
    @Test void rejectedDisableKeepsServerStateAndExplainsTheRefusal() throws Exception {
        await("row action rendered",()->view.lookup("#admin-toggle-10")!=null);
        fxRun(()->((Button)view.lookup("#admin-toggle-10")).fire());
        await("confirmation dialog",()->Window.getWindows().stream().anyMatch(w->w.isShowing() && w.getScene()!=null
                && w.getScene().getRoot() instanceof DialogPane));
        fxRun(()->{for(Window w:List.copyOf(Window.getWindows()))if(w.isShowing() && w.getScene()!=null && w.getScene().getRoot() instanceof DialogPane d)
            ((Button)d.lookupButton(ButtonType.OK)).fire();});
        await("backend refusal",()->((Label)view.lookup("#admin-status")).getText().contains("Mã đã tồn tại"));
        assertThat(fx(()->((Button)view.lookup("#admin-toggle-10")).getText())).isEqualTo("Khóa");
        getStatus.set(403);fxRun(()->((Button)view.lookup("#admin-find")).fire());
        await("forbidden rendered",()->!view.lookup("#admin-find").isDisabled());
        assertThat(fx(()->((Label)view.lookup("#admin-status")).getText())).contains("Mã đã tồn tại");assertThat(expired.get()).isZero();
    }
    @Test void nonAdminCannotConstructAdminOrProctorViews() {
        for(String role:List.of("CANDIDATE","PROCTOR"))
            assertThatThrownBy(()->new AdminAccountView("http://localhost",login(role),()->{},()->{})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ProctorRedesignView.forLogin("http://localhost",login("ADMIN"),null,()->{},()->{})).isInstanceOf(IllegalArgumentException.class);
    }
}
