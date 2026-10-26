package vn.edu.toeic.client.proctor.redesign;

import java.util.concurrent.*;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import vn.edu.toeic.client.admin.AdminAccountApiClient;
import vn.edu.toeic.protocol.admin.*;
import vn.edu.toeic.protocol.auth.LoginResponse;

/** Admin uses the same factories, scoped CSS, spacing, cards and navigation as the Proctor dashboard. */
public final class AdminAccountView extends BorderPane implements AutoCloseable {
    private final AdminAccountApiClient api;
    private final Runnable expired;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"toeic-admin");t.setDaemon(true);return t;});
    private final TableView<AccountView> table=new TableView<>();
    private final TextField search=new TextField();
    private final ComboBox<String> role=new ComboBox<>(), enabled=new ComboBox<>();
    private final Label status=Ui.wrapping("","muted");
    private final Button find=new Button("Tìm kiếm"), first=new Button("Trang đầu"), next=new Button("Trang tiếp"), add=new Button("Thêm tài khoản");
    private Long cursor;
    private boolean busy,closed;
    private Dialog<?> dialog;

    public AdminAccountView(String url,LoginResponse login,Runnable logout,Runnable expired) {
        if(login==null || login.user()==null || !"ADMIN".equals(login.user().role()))
            throw new IllegalArgumentException("Quản lý tài khoản chỉ dành cho ADMIN.");
        this.expired=expired;api=new AdminAccountApiClient(url,login.token());
        setId("admin-accounts");getStyleClass().add("proctor-redesign");
        getStylesheets().add(getClass().getResource(ProctorRedesignView.STYLESHEET).toExternalForm());
        Button leave=new Button("Đăng xuất");leave.setId("admin-logout");leave.setOnAction(e->logout.run());
        HBox top=new HBox(12,Ui.avatar(login.user().displayName(),false),
                new VBox(2,Ui.label("TOEIC Admin","brand-title"),Ui.label(login.user().displayName(),"brand-subtitle")),Ui.grow(),leave);
        top.setAlignment(Pos.CENTER_LEFT);top.getStyleClass().add("topbar");setTop(top);
        ToggleButton nav=new ToggleButton("Quản lý tài khoản");nav.getStyleClass().add("nav-item");
        nav.setSelected(true);nav.setMaxWidth(Double.MAX_VALUE);nav.setOnAction(e->nav.setSelected(true));
        VBox sidebar=new VBox(12,nav,Ui.grow(),Ui.wrapping("PROCTOR · CANDIDATE","muted","small"));sidebar.getStyleClass().add("sidebar");setLeft(sidebar);
        search.setId("admin-search");search.setPromptText("Username hoặc họ tên");search.setPrefColumnCount(22);
        role.setId("admin-role-filter");role.getItems().setAll("Tất cả vai trò","PROCTOR","CANDIDATE");role.getSelectionModel().selectFirst();
        enabled.setId("admin-enabled-filter");enabled.getItems().setAll("Tất cả trạng thái","Đang hoạt động","Đã khóa");enabled.getSelectionModel().selectFirst();
        find.setId("admin-find");find.setOnAction(e->load(null));search.setOnAction(e->load(null));
        first.setId("admin-first");first.setOnAction(e->load(null));next.setId("admin-next");next.setOnAction(e->load(cursor));next.setDisable(true);
        add.setId("admin-add");add.getStyleClass().add("primary");add.setOnAction(e->createForm());
        HBox filters=new HBox(10,search,role,enabled,find);filters.setAlignment(Pos.CENTER_LEFT);
        HBox actions=new HBox(10,add,Ui.grow(),first,next);actions.setAlignment(Pos.CENTER_LEFT);
        table.setId("admin-account-table");Ui.plainTable(table,"Không tìm thấy tài khoản.");
        table.getColumns().add(Ui.textColumn("Username",160,AccountView::username));
        table.getColumns().add(Ui.textColumn("Họ và tên",240,AccountView::displayName));
        table.getColumns().add(Ui.textColumn("Vai trò",130,AccountView::role));
        table.getColumns().add(Ui.nodeColumn("Trạng thái",150,a->Ui.badge(a.enabled()?"Hoạt động":"Đã khóa",a.enabled()?"green":"gray")));
        table.getColumns().add(Ui.nodeColumn("Thao tác",140,a->{
            Button button=new Button(a.enabled()?"Khóa":"Mở khóa");button.setId("admin-toggle-"+a.userId());
            button.setDisable(busy);button.setOnAction(e->confirmEnabled(a));
            if(!"CANDIDATE".equals(a.role()))return button;
            Button reset=new Button("Đổi mật khẩu");reset.setId("admin-reset-"+a.userId());reset.setDisable(busy);reset.setOnAction(e->passwordForm(a));
            return new HBox(6,button,reset);
        }));
        VBox card=Ui.card(filters,actions,table,status);card.setSpacing(16);card.setPadding(new Insets(20));VBox.setVgrow(table,Priority.ALWAYS);
        status.setId("admin-status");status.setMinHeight(32);
        VBox page=new VBox(20,Ui.pageHeader("Quản lý tài khoản","Tạo và quản lý giám thị, thí sinh dùng hệ thống thi TOEIC."),card);
        page.setPadding(new Insets(24));VBox.setVgrow(card,Priority.ALWAYS);setCenter(page);
        load(null);
    }

    private void load(Long after) {
        if(busy || closed)return;
        String term=search.getText(),r=role.getSelectionModel().getSelectedIndex()==0?"":role.getValue();
        String state=switch(enabled.getSelectionModel().getSelectedIndex()){case 1->"true";case 2->"false";default->"";};
        status.setText("Đang tải tài khoản…");
        submit(()->api.list(term,r,state,after),page->{table.getItems().setAll(page.items());cursor=page.nextAfterUserId();
            next.setDisable(cursor==null);status.setText("Đã tải "+page.items().size()+" tài khoản.");},null,false);
    }

    private void createForm() {
        if(busy || closed || dialog!=null)return;
        var form=new Dialog<Void>();dialog=form;form.initOwner(getScene().getWindow());style(form);
        form.setTitle("Thêm tài khoản");form.setHeaderText("Thêm Proctor hoặc Candidate");
        TextField code=new TextField(), name=new TextField();code.setId("admin-create-code");name.setId("admin-create-name");
        PasswordField secret=new PasswordField(), again=new PasswordField();
        secret.setId("admin-create-password");again.setId("admin-create-password-confirm");
        ComboBox<String> selectedRole=new ComboBox<>();selectedRole.setId("admin-create-role");selectedRole.getItems().setAll("CANDIDATE","PROCTOR");selectedRole.getSelectionModel().selectFirst();
        Label error=Ui.wrapping("");error.setId("admin-create-status");
        VBox fields=new VBox(10,field("Username / mã thí sinh",code),field("Họ và tên",name),field("Vai trò",selectedRole),
                field("Mật khẩu",secret),field("Nhập lại mật khẩu",again),
                Ui.wrapping("Mã phân biệt hoa/thường, tối đa 80 ký tự: chữ, số, . _ -. Mật khẩu từ 8 ký tự.","muted","small"),
                Ui.wrapping("Người dùng đăng nhập bằng mã và mật khẩu này. Thí sinh nhập thêm PIN chung của phòng.","muted","small"),error);
        fields.setPrefWidth(440);form.getDialogPane().setContent(fields);
        var saveType=new ButtonType("Lưu",ButtonBar.ButtonData.OK_DONE);form.getDialogPane().getButtonTypes().setAll(saveType,ButtonType.CANCEL);
        Button save=(Button)form.getDialogPane().lookupButton(saveType);save.setId("admin-create-save");save.getStyleClass().add("primary");
        save.addEventFilter(javafx.event.ActionEvent.ACTION,e->{
            e.consume();if(busy)return;
            if(!code.getText().strip().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}") || name.getText().isBlank()) {
                error.setText("Nhập mã hợp lệ và họ tên.");return;
            }
            String problem=passwordProblem(secret.getText(),again.getText());
            if(problem!=null){error.setText(problem);return;}
            String c=code.getText(),n=name.getText(),r=selectedRole.getValue(),chosen=secret.getText();
            fields.setDisable(true);save.setDisable(true);form.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
            error.setText("Đang lưu…");
            submit(()->api.create(c,n,r,chosen),created->{secret.clear();again.clear();form.close();
                status.setText("Đã tạo "+created.account().username()+" với mật khẩu bạn vừa nhập.");load(null);},failure->{
                    fields.setDisable(false);save.setDisable(false);form.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(false);error.setText(failure);
                },true);
        });
        form.setOnCloseRequest(e->{if(busy && !closed)e.consume();});
        form.setOnHidden(e->{secret.clear();again.clear();if(dialog==form)dialog=null;});form.show();
    }

    /** Same rule as the server: 8–128 characters, no control characters, typed the same twice. */
    static String passwordProblem(String password,String again) {
        int length=password.codePointCount(0,password.length());
        if(length<8 || length>128 || password.isBlank() || password.codePoints().anyMatch(Character::isISOControl))
            return "Mật khẩu từ 8 đến 128 ký tự, không chứa ký tự điều khiển.";
        return password.equals(again)?null:"Hai lần nhập mật khẩu không khớp.";
    }

    private void confirmEnabled(AccountView account) {
        if(busy || closed || dialog!=null)return;
        var confirm=new Alert(Alert.AlertType.CONFIRMATION,account.enabled()
                ?"Khóa "+account.username()+"? Các phiên đăng nhập sẽ bị thu hồi. Server sẽ từ chối nếu tài khoản đang phụ trách hoặc tham gia ca thi cần bảo vệ."
                :"Mở khóa "+account.username()+"? Người dùng phải xác thực lại; các token đã thu hồi không được phục hồi.",ButtonType.OK,ButtonType.CANCEL);
        dialog=confirm;confirm.initOwner(getScene().getWindow());style(confirm);confirm.setHeaderText(account.enabled()?"Khóa tài khoản":"Mở khóa tài khoản");
        confirm.setOnHidden(e->{dialog=null;if(confirm.getResult()==ButtonType.OK && !closed)
            submit(()->api.enabled(account.userId(),!account.enabled()),updated->{status.setText("Đã cập nhật "+updated.username()+".");load(null);},null,false);});
        confirm.show();
    }
    /** The Admin types the new password; existing logins of this candidate are signed out by the server. */
    private void passwordForm(AccountView account) {
        if(busy || closed || dialog!=null)return;
        var form=new Dialog<Void>();dialog=form;form.initOwner(getScene().getWindow());style(form);
        form.setTitle("Đổi mật khẩu");form.setHeaderText("Đổi mật khẩu cho "+account.username());
        PasswordField secret=new PasswordField(), again=new PasswordField();
        secret.setId("admin-reset-password");again.setId("admin-reset-password-confirm");
        Label error=Ui.wrapping("");error.setId("admin-reset-status");
        VBox fields=new VBox(10,field("Mật khẩu mới",secret),field("Nhập lại mật khẩu mới",again),
                Ui.wrapping("Thí sinh đang đăng nhập sẽ bị đăng xuất. Không đổi được khi thí sinh đang thi.","muted","small"),error);
        fields.setPrefWidth(420);form.getDialogPane().setContent(fields);
        var saveType=new ButtonType("Lưu",ButtonBar.ButtonData.OK_DONE);form.getDialogPane().getButtonTypes().setAll(saveType,ButtonType.CANCEL);
        Button save=(Button)form.getDialogPane().lookupButton(saveType);save.setId("admin-reset-save");save.getStyleClass().add("primary");
        save.addEventFilter(javafx.event.ActionEvent.ACTION,e->{
            e.consume();if(busy)return;
            String problem=passwordProblem(secret.getText(),again.getText());
            if(problem!=null){error.setText(problem);return;}
            String chosen=secret.getText();
            fields.setDisable(true);save.setDisable(true);form.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
            error.setText("Đang lưu…");
            submit(()->api.resetPassword(account.userId(),chosen),updated->{form.close();
                status.setText("Đã đổi mật khẩu cho "+updated.account().username()+"; các phiên đăng nhập cũ đã bị thu hồi.");},failure->{
                    fields.setDisable(false);save.setDisable(false);form.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(false);error.setText(failure);
                },false);
        });
        form.setOnCloseRequest(e->{if(busy && !closed)e.consume();});
        form.setOnHidden(e->{secret.clear();again.clear();if(dialog==form)dialog=null;});form.show();
    }
    private <T> void submit(Callable<T> work,Consumer<T> success,Consumer<String> failure,boolean create) {
        busy=true;controls();
        worker.submit(()->{
            try {T value=work.call();Platform.runLater(()->{if(closed)return;busy=false;controls();success.accept(value);});}
            catch(Exception problem){Platform.runLater(()->{
                if(closed)return;busy=false;controls();
                if(problem instanceof AdminAccountApiClient.Rejected rejection && rejection.status()==401){expired.run();return;}
                String message=problem instanceof AdminAccountApiClient.Rejected?problem.getMessage():create
                    ?"Mất kết nối hoặc phản hồi không hợp lệ. Tìm lại username trước khi thử lại: tài khoản có thể đã được tạo."
                    :"Không kết nối được server hoặc phản hồi không hợp lệ. Hãy kiểm tra mạng và tải lại.";
                if(failure!=null)failure.accept(message);else status.setText(message);
            });}
        });
    }
    private void controls(){find.setDisable(busy);first.setDisable(busy);next.setDisable(busy||cursor==null);add.setDisable(busy);
        search.setDisable(busy);role.setDisable(busy);enabled.setDisable(busy);table.setDisable(busy);table.refresh();}
    private static VBox field(String title,Node node){return new VBox(5,Ui.label(title,"field-label"),node);}
    private static void style(Dialog<?> dialog){dialog.getDialogPane().getStyleClass().add("proctor-redesign");
        dialog.getDialogPane().getStylesheets().add(AdminAccountView.class.getResource(ProctorRedesignView.STYLESHEET).toExternalForm());}
    @Override public void close(){closed=true;if(dialog!=null){dialog.setOnCloseRequest(null);dialog.close();dialog=null;}worker.shutdownNow();api.close();}
}
