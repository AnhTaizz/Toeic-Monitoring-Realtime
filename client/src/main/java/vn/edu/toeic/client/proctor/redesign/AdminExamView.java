package vn.edu.toeic.client.proctor.redesign;

import com.google.gson.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import vn.edu.toeic.client.admin.*;
import vn.edu.toeic.protocol.admin.*;

/** Two admin workflows: import a JSON file (including its local crawl media), or edit an existing exam. */
public final class AdminExamView extends VBox implements AutoCloseable {
    private final AdminExamApiClient api;
    private final Runnable expired;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"toeic-admin-exams");t.setDaemon(true);return t;});
    private final TableView<ExamSummary> table=new TableView<>();
    private final TextField search=new TextField();
    private final Label status=Ui.wrapping("","muted");
    private final Button find=new Button("Tìm kiếm"),first=new Button("Trang đầu"),next=new Button("Trang tiếp"),add=new Button("Import JSON");
    private String cursor;
    private boolean busy,closed,available;
    private Dialog<?> dialog;
    private AdminExamImport imported;
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().setStrictness(Strictness.STRICT).create();

    public AdminExamView(String url,String token,Runnable expired) {
        api=new AdminExamApiClient(url,token);this.expired=expired;
        setId("admin-exams");setSpacing(20);setPadding(new Insets(24));
        search.setId("admin-exam-search");search.setPromptText("Mã đề hoặc tên đề thi");search.setPrefColumnCount(28);
        find.setId("admin-exam-find");find.setOnAction(e->load(null));search.setOnAction(e->load(null));
        first.setOnAction(e->load(null));next.setOnAction(e->load(cursor));
        add.setId("admin-exam-add");add.getStyleClass().add("primary");add.setOnAction(e->{Path file=chooseFile(getScene().getWindow());if(file!=null)importFile(file);});
        HBox filters=new HBox(10,search,find);filters.setAlignment(Pos.CENTER_LEFT);
        HBox actions=new HBox(10,add,Ui.grow(),first,next);actions.setAlignment(Pos.CENTER_LEFT);
        table.setId("admin-exam-table");Ui.plainTable(table,"Chưa có đề thi. Chọn Import JSON để thêm đề.");
        table.getColumns().add(Ui.textColumn("Mã đề",180,ExamSummary::examId));
        table.getColumns().add(Ui.textColumn("Tên đề thi",300,ExamSummary::title));
        table.getColumns().add(Ui.textColumn("Số câu",90,e->String.valueOf(e.totalQuestions())));
        table.getColumns().add(Ui.nodeColumn("Trạng thái",160,e->Ui.badge(e.inUse()?"Đã dùng trong ca thi":"Chưa sử dụng",e.inUse()?"gray":"green")));
        table.getColumns().add(Ui.nodeColumn("Thao tác",100,exam->{
            Button edit=new Button("Sửa");edit.setId("admin-exam-edit-"+exam.examId());
            edit.setOnAction(e->submit(()->api.read(exam.examId()),text->editor(exam,text,null),null));return edit;
        }));
        status.setId("admin-exam-status");status.setMinHeight(36);
        VBox card=Ui.card(filters,actions,table,status);card.setSpacing(16);card.setPadding(new Insets(20));
        VBox.setVgrow(table,Priority.ALWAYS);VBox.setVgrow(card,Priority.ALWAYS);
        getChildren().setAll(Ui.pageHeader("Quản lý đề thi","Import đề từ JSON hoặc sửa đề đã có."),
                Ui.wrapping("STUDY4: chọn server_exam.json và giữ thư mục media cạnh tệp; âm thanh, hình ảnh sẽ được gửi lên server cùng đề.","muted"),card);
        load(null);
    }
    private void load(String after) {
        if(busy || closed)return;String term=search.getText();status.setText("Đang tải đề thi…");
        submit(()->api.list(term,after),page->{available=true;table.getItems().setAll(page.items());cursor=page.nextAfterExamId();controls();
            status.setText("Đã tải "+page.items().size()+" đề thi.");},message->{available=false;controls();status.setText(message);});
    }
    /** Also used by GUI tests with an actual selected file; all preparation happens off the FX thread. */
    void importFile(Path file) {
        if(busy || closed || dialog!=null || !available)return;
        status.setText("Đang đọc JSON và chuẩn bị âm thanh, hình ảnh…");
        submit(()->AdminExamImport.load(file),prepared->editor(null,prepared.json,prepared),null);
    }
    private void editor(ExamSummary exam,String content,AdminExamImport prepared) {
        if(closed || dialog!=null){if(prepared!=null)prepared.close();return;}
        imported=prepared;boolean metadataOnly=exam!=null && exam.inUse();
        var form=new Dialog<Void>();dialog=form;form.initOwner(getScene().getWindow());style(form);form.setResizable(true);
        form.setTitle(exam==null?"Import đề thi từ JSON":"Sửa đề thi");
        form.setHeaderText(exam==null?"Kiểm tra đề trước khi import":exam.title());
        TextField code=new TextField(),title=new TextField();TextArea description=new TextArea();description.setPrefRowCount(2);
        code.setId("admin-exam-code");title.setId("admin-exam-title");description.setId("admin-exam-description");
        code.setEditable(false);code.setPrefColumnCount(22);title.setPrefColumnCount(38);
        TextArea body=new TextArea(content);body.setId("admin-exam-content");body.setPrefRowCount(14);body.setWrapText(false);
        body.setEditable(!metadataOnly);body.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        Label summary=Ui.wrapping("","muted"),error=Ui.wrapping("");error.setId("admin-exam-form-status");
        var saveType=new ButtonType(exam==null?"Import đề thi":"Lưu thay đổi",ButtonBar.ButtonData.OK_DONE);
        var cancelType=new ButtonType("Hủy",ButtonBar.ButtonData.CANCEL_CLOSE);
        form.getDialogPane().getButtonTypes().setAll(saveType,cancelType);
        Button save=(Button)form.getDialogPane().lookupButton(saveType);save.setId("admin-exam-save");save.getStyleClass().add("primary");
        boolean[] syncing={false};
        Runnable read=()->{
            if(syncing[0])return;syncing[0]=true;
            try {
                var tree=AdminExamImport.parse(body.getText());var meta=metadata(tree);
                String id=meta.get("examId").getAsString();
                if(exam!=null && !exam.examId().equals(id))throw new IllegalArgumentException("Mã đề trong JSON phải giữ nguyên: "+exam.examId());
                code.setText(id);title.setText(meta.get("title").getAsString());
                description.setText(meta.has("description")&&!meta.get("description").isJsonNull()?meta.get("description").getAsString():"");
                int media=tree.has("media")?tree.getAsJsonArray("media").size():0;
                summary.setText((imported==null?"":imported.filename+" · ")+tree.getAsJsonArray("questions").size()+" câu hỏi · "+media+" tệp media"+(imported!=null&&imported.bundle!=null?" sẽ được tải lên server":""));
                error.setText("");save.setDisable(false);
            }catch(RuntimeException bad){error.setText(bad.getMessage()==null?"Nội dung JSON chưa hợp lệ.":bad.getMessage());save.setDisable(true);}
            finally{syncing[0]=false;}
        };
        Runnable write=()->{
            if(syncing[0] || metadataOnly)return;
            try{var tree=AdminExamImport.parse(body.getText());var meta=metadata(tree);meta.addProperty("title",title.getText());
                if(description.getText().isBlank())meta.add("description",JsonNull.INSTANCE);else meta.addProperty("description",description.getText());
                syncing[0]=true;body.setText(JSON.toJson(tree));save.setDisable(title.getText().isBlank());error.setText("");
            }catch(RuntimeException bad){error.setText("Sửa JSON hợp lệ trước khi cập nhật thông tin đề.");}
            finally{syncing[0]=false;}
        };
        body.textProperty().addListener((o,a,b)->read.run());title.textProperty().addListener((o,a,b)->write.run());description.textProperty().addListener((o,a,b)->write.run());
        read.run();
        Button replace=new Button("Chọn JSON thay thế");replace.setId("admin-exam-import");replace.setDisable(metadataOnly);
        replace.setOnAction(e->{Path file=chooseFile(form.getDialogPane().getScene().getWindow());if(file==null)return;
            form.getDialogPane().setDisable(true);
            submit(()->AdminExamImport.load(file),replacement->{
                if(exam!=null && !exam.examId().equals(metadata(AdminExamImport.parse(replacement.json)).get("examId").getAsString())) {
                    replacement.close();form.getDialogPane().setDisable(false);error.setText("Mã đề thay thế phải là "+exam.examId());return;
                }
                if(imported!=null)imported.close();imported=replacement;body.setText(replacement.json);read.run();form.getDialogPane().setDisable(false);
            },message->{form.getDialogPane().setDisable(false);error.setText(message);});
        });
        var details=new TitledPane("Nội dung đề (JSON)",body);details.setExpanded(exam!=null&&!metadataOnly);
        VBox fields=new VBox(12,new HBox(12,field("Mã đề",code),field("Tên đề thi",title)),field("Mô tả",description),summary);
        if(metadataOnly)fields.getChildren().add(Ui.wrapping("Đề đã dùng trong ca thi: bạn có thể sửa tên và mô tả. Nội dung câu hỏi, đáp án được giữ nguyên để bảo toàn kết quả.","muted"));
        else fields.getChildren().addAll(replace,details);
        fields.getChildren().add(error);fields.setPadding(new Insets(8));
        ScrollPane scroll=new ScrollPane(fields);scroll.setFitToWidth(true);scroll.setPrefViewportWidth(820);scroll.setMaxHeight(600);
        form.getDialogPane().setContent(scroll);
        save.addEventFilter(javafx.event.ActionEvent.ACTION,e->{
            e.consume();if(busy)return;
            String payload=body.getText(),name=title.getText().strip(),desc=description.getText();
            try{if(name.isEmpty())throw new IllegalArgumentException("Nhập tên đề thi.");if(!metadataOnly)AdminExamImport.parse(payload);}
            catch(RuntimeException bad){error.setText(bad.getMessage());return;}
            fields.setDisable(true);save.setDisable(true);form.getDialogPane().lookupButton(cancelType).setDisable(true);error.setText("Đang gửi đề và media lên server…");
            AdminExamImport source=imported;
            submit(()->{
                String id=exam==null?null:exam.examId();
                if(metadataOnly)api.metadata(id,name,desc);
                else if(source!=null && source.bundle!=null)api.upload(id,source.bundle(payload));
                else api.save(id,payload);return true;
            },ok->{form.close();load(null);},message->{fields.setDisable(false);save.setDisable(false);form.getDialogPane().lookupButton(cancelType).setDisable(false);error.setText(message);});
        });
        form.setOnCloseRequest(e->{if(busy&&!closed)e.consume();});
        form.setOnHidden(e->{if(imported!=null){imported.close();imported=null;}if(dialog==form)dialog=null;});form.show();
    }
    private static Path chooseFile(javafx.stage.Window owner) {
        var chooser=new FileChooser();chooser.setTitle("Chọn server_exam.json hoặc JSON đề thi");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Đề thi JSON","*.json"));var file=chooser.showOpenDialog(owner);return file==null?null:file.toPath();
    }
    private <T> void submit(Callable<T> work,Consumer<T> success,Consumer<String> failure) {
        if(busy||closed)return;busy=true;controls();
        worker.submit(()->{try{T result=work.call();Platform.runLater(()->{if(closed){if(result instanceof AdminExamImport prepared)prepared.close();return;}busy=false;controls();success.accept(result);});}
            catch(Exception problem){Platform.runLater(()->{if(closed)return;busy=false;controls();
                if(problem instanceof AdminAccountApiClient.Rejected rejected && rejected.status()==401){expired.run();return;}
                String message=problem instanceof AdminAccountApiClient.Rejected || problem instanceof IllegalArgumentException?problem.getMessage():"Không kết nối được server hoặc không đọc được tệp. Tải lại danh sách để kiểm tra trước khi thử lại.";
                if(failure==null)status.setText(message);else failure.accept(message);
            });}});
    }
    private void controls(){find.setDisable(busy);search.setDisable(busy);first.setDisable(busy);next.setDisable(busy||cursor==null);add.setDisable(busy||!available);table.setDisable(busy);}
    private static JsonObject metadata(JsonObject tree){return tree.has("exam")?tree.getAsJsonObject("exam"):tree;}
    private static VBox field(String label,javafx.scene.Node node){return new VBox(5,Ui.label(label,"field-label"),node);}
    private static void style(Dialog<?> dialog){dialog.getDialogPane().getStyleClass().add("proctor-redesign");dialog.getDialogPane().getStylesheets().add(AdminExamView.class.getResource(ProctorRedesignView.STYLESHEET).toExternalForm());}
    @Override public void close(){closed=true;if(dialog!=null){dialog.setOnCloseRequest(null);dialog.close();dialog=null;}worker.shutdownNow();api.close();}
}
