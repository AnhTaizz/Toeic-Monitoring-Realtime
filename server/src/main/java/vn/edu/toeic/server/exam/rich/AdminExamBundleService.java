package vn.edu.toeic.server.exam.rich;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.exam.rich.*;
import vn.edu.toeic.server.auth.*;
import vn.edu.toeic.server.exam.ExamApiException;

/** Bounded upload of normalized JSON and its media. File replacements are restored if the DB transaction fails. */
@Service
public class AdminExamBundleService {
    private static final long MAX_TOTAL=322L*1024*1024;
    private final Path audioRoot,imageRoot;
    private final AuthorizationService authorization;
    private final AdminExamService exams;
    private final RichExamJson json;
    private final RichExamValidator validator;
    private final TransactionTemplate transaction;
    private record Published(Path target,Path backup) {}
    public AdminExamBundleService(@Value("${toeic.audio.root:audio-resources}") String audio,
            @Value("${toeic.image.root:image-resources}") String images,AuthorizationService authorization,
            AdminExamService exams,RichExamJson json,RichExamValidator validator,PlatformTransactionManager manager) {
        audioRoot=Path.of(audio).toAbsolutePath().normalize();imageRoot=Path.of(images).toAbsolutePath().normalize();
        this.authorization=authorization;this.exams=exams;this.json=json;this.validator=validator;
        transaction=new TransactionTemplate(manager);
    }
    public void upload(AuthenticatedUser admin,String replaceId,InputStream input) throws IOException {
        authorization.requireRole(admin,Role.ADMIN);
        Path temp=Files.createTempDirectory("toeic-exam-upload-");
        List<Published> published=new ArrayList<>();List<Path> directories=new ArrayList<>();
        try {
            Set<String> entries=extract(input,temp);
            RichExamImportRequest request;
            try(var body=Files.newInputStream(temp.resolve("rich-import.json"))){request=json.read(body);}
            validator.validate(request);
            Set<String> expected=new HashSet<>(Set.of("rich-import.json"));
            for(var media:request.media())expected.add(entry(request.exam().examId(),media));
            if(!expected.equals(entries))throw ExamApiException.invalidInput("Gói import thiếu media hoặc chứa tệp không thuộc đề.");
            try {
                transaction.executeWithoutResult(status->{
                    exams.prepareWrite(admin,replaceId,request.exam().examId());
                    try {
                        for(var media:request.media()) {
                            Path root=media.mediaType()==MediaType.AUDIO?audioRoot:imageRoot;
                            Files.createDirectories(root);root=root.toRealPath();
                            Path directory=root.resolve(request.exam().examId());
                            if(!Files.exists(directory,LinkOption.NOFOLLOW_LINKS)){Files.createDirectory(directory);directories.add(directory);}
                            safe(directory,true);
                            Path target=directory.resolve(media.localFile()),backup=null;
                            if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)) {
                                safe(target,false);backup=temp.resolve("backup-"+published.size());Files.copy(target,backup);
                            }
                            published.add(new Published(target,backup));
                            Files.copy(temp.resolve(entry(request.exam().examId(),media)),target,StandardCopyOption.REPLACE_EXISTING);
                        }
                    }catch(IOException failure){throw new UncheckedIOException(failure);}
                    exams.save(admin,replaceId,request);
                });
            } catch(RuntimeException failure) {
                // Restore only paths this request changed; never remove pre-existing exam directories.
                for(var file:published.reversed()) {
                    if(file.backup()==null)Files.deleteIfExists(file.target());
                    else Files.copy(file.backup(),file.target(),StandardCopyOption.REPLACE_EXISTING);
                }
                for(Path directory:directories.reversed())Files.deleteIfExists(directory);
                throw failure;
            }
        } finally {cleanup(temp);}
    }
    private static String entry(String exam,MediaImport media){return (media.mediaType()==MediaType.AUDIO?"audio/":"images/")+exam+"/"+media.localFile();}
    private static Set<String> extract(InputStream input,Path temp) throws IOException {
        Set<String> names=new HashSet<>();long total=0;
        try(var zip=new ZipInputStream(input)) {
            for(ZipEntry entry;(entry=zip.getNextEntry())!=null;) {
                String name=entry.getName();
                if(entry.isDirectory() || names.size()>=201 || !names.add(name)
                        || !(name.equals("rich-import.json") || name.matches("(?:audio|images)/[A-Za-z0-9][A-Za-z0-9_-]{0,127}/[A-Za-z0-9][A-Za-z0-9._-]{0,254}")))
                    throw ExamApiException.invalidInput("Đường dẫn hoặc số lượng tệp trong gói import không hợp lệ.");
                Path target=temp.resolve(name).normalize();if(!target.startsWith(temp))throw ExamApiException.invalidInput("Đường dẫn import không hợp lệ.");
                Files.createDirectories(target.getParent());
                long limit=name.equals("rich-import.json")?RichExamJson.MAX_BYTES:name.startsWith("images/")?8L*1024*1024:64L*1024*1024;
                try(var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)) {
                    byte[] bytes=new byte[65536];long size=0;int count;
                    while((count=zip.read(bytes))!=-1) {
                        size+=count;total+=count;if(size>limit || total>MAX_TOTAL)throw ExamApiException.invalidInput("Gói JSON/media vượt giới hạn dung lượng.");
                        out.write(bytes,0,count);
                    }
                }
            }
        }catch(ZipException bad){throw ExamApiException.invalidInput("Gói import bị hỏng hoặc không phải ZIP.");}
        if(!names.contains("rich-import.json"))throw ExamApiException.invalidInput("Gói import thiếu JSON đề thi.");return names;
    }
    private static void safe(Path path,boolean directory) throws IOException {
        var attrs=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(attrs.isSymbolicLink() || attrs.isOther() || attrs.isDirectory()!=directory || !directory&&!attrs.isRegularFile()
                || !path.toRealPath().equals(path.toAbsolutePath().normalize()))throw ExamApiException.invalidInput("Thư mục media không hợp lệ.");
    }
    private static void cleanup(Path temp) throws IOException {
        try(var paths=Files.walk(temp)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
    }
}
