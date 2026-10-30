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

/** Admin authoring: a resumable manual draft, JSON/media import, and existing exam editing. */
public final class AdminExamView extends VBox implements AutoCloseable {
    private final AdminExamApiClient api;
    private final Runnable expired;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"toeic-admin-exams");t.setDaemon(true);return t;});
    private final TableView<ExamSummary> table=new TableView<>();
    private final TextField search=new TextField();
    private final Label status=Ui.wrapping("","muted");
    private final Button find=new Button("Tìm kiếm"),first=new Button("Trang đầu"),next=new Button("Trang tiếp"),add=new Button("Import JSON");
    private final Button manualAdd=new Button("Tạo đề bằng tay");
    private final Button openDraft=new Button("Mở nháp từ máy…");
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
        manualAdd.setId("admin-exam-manual-add");manualAdd.getStyleClass().add("primary");
        manualAdd.setOnAction(e->manualEditor(null,JSON.toJsonTree(ManualExamDraft.empty()).getAsJsonObject()));
        openDraft.setId("admin-exam-open-draft");openDraft.setOnAction(e->{
            var picker=new FileChooser();picker.setTitle("Mở bản nháp TOEIC");picker.getExtensionFilters().add(new FileChooser.ExtensionFilter("Bản nháp TOEIC","*.zip"));
            var file=picker.showOpenDialog(getScene().getWindow());if(file==null)return;
            submit(()->{var files=new ManualExamFiles();try{return new Object[]{files,files.open(file.toPath())};}catch(Exception bad){files.close();throw bad;}},loaded->{
                var tree=(JsonObject)loaded[1];var draft=JSON.fromJson(tree,ManualExamDraft.class);
                var summary=draft.revision()==0?null:new ExamSummary(draft.examId(),draft.title(),draft.description(),200,1,false,"PENDING",draft.completedQuestions(),true);
                manualEditor(summary,tree,(ManualExamFiles)loaded[0]);
            },null);
        });
        HBox actions=new HBox(10,manualAdd,openDraft,add,Ui.grow(),first,next);actions.setAlignment(Pos.CENTER_LEFT);
        table.setId("admin-exam-table");Ui.plainTable(table,"Chưa có đề thi. Chọn Import JSON để thêm đề.");
        table.getColumns().add(Ui.textColumn("Mã đề",180,ExamSummary::examId));
        table.getColumns().add(Ui.textColumn("Tên đề thi",300,ExamSummary::title));
        table.getColumns().add(Ui.textColumn("Số câu",110,e->e.manual()?e.completedQuestions()+" / 200":String.valueOf(e.totalQuestions())));
        table.getColumns().add(Ui.nodeColumn("Trạng thái",170,e->Ui.badge("PENDING".equals(e.authoringStatus())?"Pending · Đang soạn":e.inUse()?"Đã dùng trong ca thi":"Hoàn thiện", "PENDING".equals(e.authoringStatus())?"amber":e.inUse()?"gray":"green")));
        table.getColumns().add(Ui.nodeColumn("Thao tác",100,exam->{
            Button edit=new Button("Sửa");edit.setId("admin-exam-edit-"+exam.examId());
            edit.setOnAction(e->{if(exam.manual()&&!exam.inUse())submit(()->api.readManual(exam.examId()),text->manualEditor(exam,JSON.fromJson(text,JsonObject.class)),null);
                else submit(()->api.read(exam.examId()),text->editQuestions(exam,text),null);});return edit;
        }));
        status.setId("admin-exam-status");status.setMinHeight(36);
        VBox card=Ui.card(filters,actions,table,status);card.setSpacing(16);card.setPadding(new Insets(20));
        VBox.setVgrow(table,Priority.ALWAYS);VBox.setVgrow(card,Priority.ALWAYS);
        getChildren().setAll(Ui.pageHeader("Quản lý đề thi","Tạo đề bằng tay với khung 200 câu, import JSON hoặc sửa đề đã có."),
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
    private void manualEditor(ExamSummary exam,JsonObject source) {
        try{manualEditor(exam,source,new ManualExamFiles());}catch(java.io.IOException failure){status.setText("Không tạo được thư mục tạm cho bản nháp.");}
    }
    private void manualEditor(ExamSummary exam,JsonObject source,ManualExamFiles media) {
        if(closed||busy||dialog!=null){try{media.close();}catch(java.io.IOException ignored){}return;}
        var form=new Dialog<Void>();dialog=form;form.initOwner(getScene().getWindow());style(form);form.setResizable(true);
        form.setTitle(exam==null?"Tạo đề bằng tay":"Tiếp tục soạn đề");form.setHeaderText("TOEIC · 200 câu · 7 Part");
        var code=new TextField(source.get("examId").getAsString());code.setId("admin-exam-code");code.setEditable(exam==null);
        code.setPromptText("Ví dụ: TOEIC-2026-01");
        var title=new TextField(source.get("title").getAsString());title.setId("admin-exam-title");title.setPrefColumnCount(36);
        var description=new TextArea(source.has("description")&&!source.get("description").isJsonNull()?source.get("description").getAsString():"");
        description.setId("admin-exam-description");description.setPrefRowCount(2);
        var questions=new ExamQuestionEditor(source,true);questions.localFiles(media);var progress=Ui.wrapping("");progress.setId("admin-exam-progress");
        var error=Ui.wrapping("");error.setId("admin-exam-form-status");boolean[] dirty={false},saved={false};
        Runnable update=()->{
            int count=JSON.fromJson(questions.document(),ManualExamDraft.class).completedQuestions();
            progress.setText("Đã hoàn thiện "+count+" / 200 câu · "+(count==200?"Hoàn thiện":"PENDING — có thể lưu và nhập tiếp sau"));
        };
        questions.onChange(()->{dirty[0]=true;update.run();});update.run();
        code.textProperty().addListener((o,a,b)->dirty[0]=true);title.textProperty().addListener((o,a,b)->dirty[0]=true);
        description.textProperty().addListener((o,a,b)->dirty[0]=true);
        var saveType=new ButtonType("Lưu lên server",ButtonBar.ButtonData.OK_DONE);var cancelType=new ButtonType("Đóng",ButtonBar.ButtonData.CANCEL_CLOSE);
        var localType=new ButtonType("Lưu nháp vào máy…",ButtonBar.ButtonData.OTHER);
        form.getDialogPane().getButtonTypes().setAll(localType,saveType,cancelType);
        var local=(Button)form.getDialogPane().lookupButton(localType);local.setId("admin-exam-save-local");
        var save=(Button)form.getDialogPane().lookupButton(saveType);save.setId("admin-exam-save");save.getStyleClass().add("primary");
        var fields=new VBox(10,new HBox(12,field("Mã đề",code),field("Tên đề thi",title)),field("Mô tả",description),progress,
                Ui.wrapping("Mỗi câu cần nội dung, các lựa chọn và đáp án đúng; Part 6–7 cần thêm đoạn văn. Bạn có thể hoàn thiện các câu theo thứ tự tùy ý.","muted"),questions,error);
        HBox.setHgrow(title.getParent(),Priority.ALWAYS);title.setMaxWidth(Double.MAX_VALUE);
        fields.getStyleClass().add("exam-authoring");fields.setPadding(new Insets(16));fields.setPrefSize(1050,680);VBox.setVgrow(questions,Priority.ALWAYS);form.getDialogPane().setContent(fields);
        local.addEventFilter(javafx.event.ActionEvent.ACTION,e->{
            e.consume();if(busy)return;var picker=new FileChooser();picker.setTitle("Lưu bản nháp và âm thanh");
            picker.setInitialFileName("TOEIC-draft.zip");picker.getExtensionFilters().add(new FileChooser.ExtensionFilter("Bản nháp TOEIC","*.zip"));
            var target=picker.showSaveDialog(form.getDialogPane().getScene().getWindow());if(target==null)return;
            var tree=questions.document();tree.addProperty("examId",code.getText().strip());tree.addProperty("title",title.getText());tree.addProperty("description",description.getText());
            fields.setDisable(true);local.setDisable(true);save.setDisable(true);
            submit(()->{var zip=media.bundle(tree);try{java.nio.file.Files.copy(zip,target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}finally{java.nio.file.Files.deleteIfExists(zip);}return true;},ok->{
                dirty[0]=false;fields.setDisable(false);local.setDisable(false);save.setDisable(false);error.setText("Đã lưu nháp trên máy, kèm âm thanh đã chọn. Chưa lưu lên server.");
            },failure->{fields.setDisable(false);local.setDisable(false);save.setDisable(false);error.setText(failure);});
        });
        save.addEventFilter(javafx.event.ActionEvent.ACTION,e->{
            e.consume();if(busy)return;
            String payload;
            try{payload=JSON.toJson(importDocument(questions.document(),code.getText(),title.getText(),description.getText()));
                if(!code.getText().strip().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}"))throw new IllegalArgumentException("Mã đề chỉ gồm chữ, số, gạch ngang hoặc gạch dưới (tối đa 128 ký tự).");
            }catch(RuntimeException bad){error.setText(bad.getMessage());return;}
            fields.setDisable(true);save.setDisable(true);local.setDisable(true);form.getDialogPane().lookupButton(cancelType).setDisable(true);error.setText("Đang lưu đề và âm thanh…");
            submit(()->{if(media.hasAudio()){var zip=media.bundle(JSON.fromJson(payload,JsonObject.class));try{api.uploadManual(exam==null?null:exam.examId(),zip);}finally{java.nio.file.Files.deleteIfExists(zip);}}
                else api.saveManual(exam==null?null:exam.examId(),payload);return true;},result->{saved[0]=true;form.close();load(null);},failure->{
                fields.setDisable(false);save.setDisable(false);local.setDisable(false);form.getDialogPane().lookupButton(cancelType).setDisable(false);error.setText(failure);
            });
        });
        form.setOnCloseRequest(e->{
            if(busy&&!closed){e.consume();return;}
            if(!closed&&!saved[0]&&dirty[0]){
                var discard=new ButtonType("Bỏ thay đổi",ButtonBar.ButtonData.YES);var stay=new ButtonType("Tiếp tục soạn",ButtonBar.ButtonData.CANCEL_CLOSE);
                var confirm=new Alert(Alert.AlertType.CONFIRMATION,"Thay đổi chưa được lưu. Bạn có thể tiếp tục soạn và lưu nháp vào máy trước khi đóng.",discard,stay);
                style(confirm);confirm.setTitle("Đề chưa được lưu");confirm.initOwner(form.getDialogPane().getScene().getWindow());confirm.setHeaderText("Đề chưa được lưu");
                if(confirm.showAndWait().orElse(stay)!=discard)e.consume();
            }
        });
        form.setOnHidden(e->{if(dialog==form)dialog=null;try{media.close();}catch(java.io.IOException ignored){}});form.show();
    }
    private void editQuestions(ExamSummary exam,String content) {
        if(closed||dialog!=null)return;
        var source=AdminExamImport.parse(content);var meta=metadata(source);
        var form=new Dialog<Void>();dialog=form;form.initOwner(getScene().getWindow());style(form);form.setResizable(true);
        form.setTitle("Sửa câu hỏi và đáp án");form.setHeaderText(exam.title());
        var code=new TextField(exam.examId());code.setId("admin-exam-code");code.setEditable(exam.inUse());
        var title=new TextField(meta.get("title").getAsString());title.setId("admin-exam-title");
        var description=new TextArea(meta.has("description")&&!meta.get("description").isJsonNull()?meta.get("description").getAsString():"");
        description.setId("admin-exam-description");description.setPrefRowCount(2);
        var questions=new ExamQuestionEditor(source);
        var revision=new CheckBox("Lưu thành đề mới để giữ nguyên bài làm và kết quả của đề cũ");
        revision.setId("admin-exam-revision");revision.setSelected(exam.inUse());revision.setVisible(exam.inUse());revision.setManaged(exam.inUse());
        if(exam.inUse())code.setText(exam.examId().substring(0,Math.min(100,exam.examId().length()))+"-edit-"+java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMddHHmmss")));
        var message=Ui.wrapping(exam.inUse()?"Đề đã dùng trong ca thi. Chỉnh sửa câu hỏi sẽ tạo một đề mới, kèm media hiện có trên server. Bỏ chọn bên dưới nếu chỉ muốn đổi tên/mô tả của đề cũ.":"Chọn câu bên trái, sửa nội dung và đáp án đúng rồi bấm Lưu thay đổi.","muted");
        var error=Ui.wrapping("");error.setId("admin-exam-form-status");
        var saveType=new ButtonType(exam.inUse()?"Lưu thành đề mới":"Lưu thay đổi",ButtonBar.ButtonData.OK_DONE);
        var cancelType=new ButtonType("Hủy",ButtonBar.ButtonData.CANCEL_CLOSE);form.getDialogPane().getButtonTypes().setAll(saveType,cancelType);
        var save=(Button)form.getDialogPane().lookupButton(saveType);save.setId("admin-exam-save");save.getStyleClass().add("primary");
        revision.selectedProperty().addListener((o,a,b)->{questions.setDisable(!b);code.setDisable(!b);save.setText(b?"Lưu thành đề mới":"Lưu tên và mô tả");});
        var header=new VBox(10,new HBox(12,field("Mã đề",code),field("Tên đề thi",title)),field("Mô tả",description),message,revision);
        HBox.setHgrow(title.getParent(),Priority.ALWAYS);title.setMaxWidth(Double.MAX_VALUE);
        var fields=new VBox(12,header,questions,error);fields.setPadding(new Insets(8));VBox.setVgrow(questions,Priority.ALWAYS);
        fields.setPrefSize(980,660);form.getDialogPane().setContent(fields);
        save.addEventFilter(javafx.event.ActionEvent.ACTION,e->{
            e.consume();if(busy)return;
            boolean copy=exam.inUse()&&revision.isSelected(),metadataOnly=exam.inUse()&&!copy;
            String payload;
            String name=title.getText(),desc=description.getText();
            try {
                if(!metadataOnly)questions.validate();
                var tree=importDocument(questions.document(),copy?code.getText():exam.examId(),name,desc);
                if(copy&&metadata(tree).get("examId").getAsString().equals(exam.examId()))throw new IllegalArgumentException("Nhập mã khác đề cũ để lưu bản chỉnh sửa.");
                payload=JSON.toJson(tree);AdminExamImport.parse(payload);
            }catch(RuntimeException bad){error.setText(bad.getMessage());return;}
            fields.setDisable(true);save.setDisable(true);form.getDialogPane().lookupButton(cancelType).setDisable(true);error.setText("Đang lưu đề thi…");
            submit(()->{if(metadataOnly)api.metadata(exam.examId(),name,desc);
                else if(copy)api.revise(exam.examId(),payload);else api.save(exam.examId(),payload);return true;
            },ok->{form.close();load(null);},failure->{fields.setDisable(false);save.setDisable(false);form.getDialogPane().lookupButton(cancelType).setDisable(false);error.setText(failure);});
        });
        form.setOnCloseRequest(e->{if(busy&&!closed)e.consume();});form.setOnHidden(e->{if(dialog==form)dialog=null;});form.show();
    }
    private void editor(ExamSummary exam,String content,AdminExamImport prepared) {
        if(closed || dialog!=null){if(prepared!=null)prepared.close();return;}
        imported=prepared;boolean metadataOnly=exam!=null && exam.inUse();
        // Import keeps the parsed document as its source of truth. The preview is never read back as input.
        JsonObject[] document={AdminExamImport.parse(content)};
        var form=new Dialog<Void>();dialog=form;form.initOwner(getScene().getWindow());style(form);form.setResizable(true);
        form.setTitle(exam==null?"Import đề thi từ JSON":"Sửa đề thi");
        form.setHeaderText(exam==null?"Kiểm tra đề trước khi import":exam.title());
        TextField code=new TextField(),title=new TextField();TextArea description=new TextArea();description.setPrefRowCount(2);
        code.setId("admin-exam-code");title.setId("admin-exam-title");description.setId("admin-exam-description");
        code.setEditable(exam==null);code.setPrefColumnCount(22);title.setPrefColumnCount(38);
        TextArea body=new TextArea(content);body.setId("admin-exam-content");body.setPrefRowCount(14);body.setWrapText(false);
        body.setEditable(exam!=null&&!metadataOnly);body.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        Label summary=Ui.wrapping("","muted"),error=Ui.wrapping("");error.setId("admin-exam-form-status");
        var saveType=new ButtonType(exam==null?"Import đề thi":"Lưu thay đổi",ButtonBar.ButtonData.OK_DONE);
        var cancelType=new ButtonType("Hủy",ButtonBar.ButtonData.CANCEL_CLOSE);
        form.getDialogPane().getButtonTypes().setAll(saveType,cancelType);
        Button save=(Button)form.getDialogPane().lookupButton(saveType);save.setId("admin-exam-save");save.getStyleClass().add("primary");
        boolean[] syncing={false};
        Runnable read=()->{
            if(syncing[0])return;syncing[0]=true;
            try {
                var tree=exam==null?document[0]:AdminExamImport.parse(body.getText());var meta=metadata(tree);
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
            try{var tree=exam==null?importDocument(document[0],code.getText(),title.getText(),description.getText()):JSON.fromJson(body.getText(),JsonObject.class);
                if(exam!=null){var meta=metadata(tree);meta.addProperty("title",title.getText());
                    if(description.getText().isBlank())meta.add("description",JsonNull.INSTANCE);else meta.addProperty("description",description.getText());}
                String rendered=JSON.toJson(tree);AdminExamImport.parse(rendered);
                syncing[0]=true;body.setText(rendered);save.setDisable(false);error.setText("");
            }catch(RuntimeException bad){save.setDisable(true);error.setText(bad.getMessage()==null?"Nhập mã đề, tên đề và JSON hợp lệ trước khi lưu.":bad.getMessage());}
            finally{syncing[0]=false;}
        };
        if(exam!=null)body.textProperty().addListener((o,a,b)->read.run());
        code.textProperty().addListener((o,a,b)->write.run());title.textProperty().addListener((o,a,b)->write.run());description.textProperty().addListener((o,a,b)->write.run());
        read.run();
        Button replace=new Button("Chọn JSON thay thế");replace.setId("admin-exam-import");replace.setDisable(metadataOnly);
        replace.setOnAction(e->{Path file=chooseFile(form.getDialogPane().getScene().getWindow());if(file==null)return;
            form.getDialogPane().setDisable(true);
            submit(()->AdminExamImport.load(file),replacement->{
                if(exam!=null && !exam.examId().equals(metadata(AdminExamImport.parse(replacement.json)).get("examId").getAsString())) {
                    replacement.close();form.getDialogPane().setDisable(false);error.setText("Mã đề thay thế phải là "+exam.examId());return;
                }
                if(imported!=null)imported.close();imported=replacement;document[0]=AdminExamImport.parse(replacement.json);body.setText(replacement.json);read.run();form.getDialogPane().setDisable(false);
            },message->{form.getDialogPane().setDisable(false);error.setText(message);});
        });
        var details=new TitledPane(exam==null?"Xem trước nội dung đề (chỉ đọc)":"Nội dung đề (JSON)",body);details.setExpanded(exam!=null&&!metadataOnly);
        VBox fields=new VBox(12,new HBox(12,field("Mã đề",code),field("Tên đề thi",title)),field("Mô tả",description),summary);
        if(metadataOnly)fields.getChildren().add(Ui.wrapping("Đề đã dùng trong ca thi: bạn có thể sửa tên và mô tả. Nội dung câu hỏi, đáp án được giữ nguyên để bảo toàn kết quả.","muted"));
        else fields.getChildren().addAll(replace,details);
        fields.getChildren().add(error);fields.setPadding(new Insets(8));
        ScrollPane scroll=new ScrollPane(fields);scroll.setFitToWidth(true);scroll.setPrefViewportWidth(820);scroll.setMaxHeight(600);
        form.getDialogPane().setContent(scroll);
        save.addEventFilter(javafx.event.ActionEvent.ACTION,e->{
            e.consume();if(busy)return;
            String payload,name=title.getText().strip(),desc=description.getText();
            try{if(name.isEmpty())throw new IllegalArgumentException("Nhập tên đề thi.");
                payload=exam==null?JSON.toJson(importDocument(document[0],code.getText(),name,desc)):body.getText();
                if(!metadataOnly)AdminExamImport.parse(payload);}
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
    private void controls(){find.setDisable(busy);search.setDisable(busy);first.setDisable(busy);next.setDisable(busy||cursor==null);add.setDisable(busy||!available);manualAdd.setDisable(busy);openDraft.setDisable(busy);table.setDisable(busy);}
    private static JsonObject metadata(JsonObject tree){return tree.has("exam")?tree.getAsJsonObject("exam"):tree;}
    private static JsonObject importDocument(JsonObject source,String code,String title,String description) {
        if(code==null||code.isBlank())throw new IllegalArgumentException("Nhập mã đề thi.");
        if(title==null||title.isBlank())throw new IllegalArgumentException("Nhập tên đề thi.");
        if(source.has("exam")&&!code.strip().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}"))
            throw new IllegalArgumentException("Mã đề chỉ gồm chữ, số, dấu gạch ngang hoặc gạch dưới, tối đa 128 ký tự.");
        var copy=source.deepCopy();var meta=metadata(copy);meta.addProperty("examId",code.strip());meta.addProperty("title",title.strip());
        if(description==null||description.isBlank())meta.add("description",JsonNull.INSTANCE);else meta.addProperty("description",description);
        return copy;
    }
    private static VBox field(String label,javafx.scene.Node node){return new VBox(5,Ui.label(label,"field-label"),node);}
    private static void style(Dialog<?> dialog){dialog.getDialogPane().getStyleClass().add("proctor-redesign");dialog.getDialogPane().getStylesheets().add(AdminExamView.class.getResource(ProctorRedesignView.STYLESHEET).toExternalForm());}
    @Override public void close(){closed=true;if(dialog!=null){dialog.setOnCloseRequest(null);dialog.close();dialog=null;}worker.shutdownNow();api.close();}
}
