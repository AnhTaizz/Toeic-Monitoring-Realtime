package vn.edu.toeic.server.exam.rich;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.admin.ManualExamDraft;
import vn.edu.toeic.server.auth.*;
import vn.edu.toeic.server.exam.ExamApiException;

/** Saves audio, images and the authored document in one operation. */
@Service
public class ManualExamBundleService {
    private final ManualExamMedia media;
    private final ManualExamService manual;
    private final AdminExamService exams;
    private final AuthorizationService authorization;
    private final RichExamJson json;
    private final TransactionTemplate transaction;
    public ManualExamBundleService(ManualExamMedia media,ManualExamService manual,AdminExamService exams,
            AuthorizationService authorization,RichExamJson json,PlatformTransactionManager manager) {
        this.media=media;this.manual=manual;this.exams=exams;this.authorization=authorization;this.json=json;transaction=new TransactionTemplate(manager);
    }
    public ManualExamDraft upload(AuthenticatedUser admin,String replaceId,InputStream input) throws IOException {
        authorization.requireRole(admin,Role.ADMIN);Path temp=Files.createTempDirectory("toeic-manual-upload-");
        var published=new ArrayList<Path>();var directories=new ArrayList<Path>();
        try {
            Set<String> names=extract(input,temp);ManualExamDraft draft;
            try(var body=Files.newInputStream(temp.resolve("manual-draft.json"))){draft=json.read(body,ManualExamDraft.class);}
            ManualExamService.validate(draft);var referenced=new HashSet<String>();
            for(var q:draft.questions()) {
                if(q.audioFile()!=null&&!q.audioFile().isBlank())referenced.add("audio/"+q.audioFile());
                for(String file:q.imageFiles())referenced.add("images/"+file);
            }
            if(!referenced.containsAll(names))throw ExamApiException.invalidInput("Gói đề chứa media không được dùng trong đề.");
            try {
                return transaction.execute(status->{
                    exams.prepareWrite(admin,replaceId,draft.examId());
                    try {
                        for(String name:names) {
                            Path root=name.startsWith("images/")?media.imageRoot:media.audioRoot;
                            Files.createDirectories(root);Path directory=root.toRealPath().resolve(draft.examId());
                            if(!Files.exists(directory,LinkOption.NOFOLLOW_LINKS)){Files.createDirectory(directory);directories.add(directory);}
                            if(Files.isSymbolicLink(directory)||!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))throw ExamApiException.invalidInput("Thư mục media không hợp lệ.");
                            Path source=temp.resolve(name),target=directory.resolve(name.substring(name.indexOf('/')+1));
                            if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)) {
                                if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)||Files.mismatch(source,target)!=-1)throw ExamApiException.invalidInput("Media trên server không khớp nội dung tải lên.");
                            }else {Files.copy(source,target);published.add(target);}
                        }
                        return manual.save(admin,replaceId,draft);
                    }catch(IOException | RuntimeException bad) {
                        try{rollback(published,directories);}catch(IOException cleanup){bad.addSuppressed(cleanup);}
                        if(bad instanceof IOException io)throw new UncheckedIOException(io);throw (RuntimeException)bad;
                    }
                });
            }catch(RuntimeException bad) {
                try{rollback(published,directories);}catch(IOException cleanup){bad.addSuppressed(cleanup);}throw bad;
            }
        }finally{try(var paths=Files.walk(temp)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
    private static void rollback(List<Path> published,List<Path> directories) throws IOException {
        for(Path file:published.reversed())Files.deleteIfExists(file);published.clear();
        for(Path directory:directories.reversed())Files.deleteIfExists(directory);directories.clear();
    }
    private static Set<String> extract(InputStream stream,Path temp) throws IOException {
        Set<String> entries=new HashSet<>(),files=new LinkedHashSet<>();long[] totals={0,0};int[] counts={0,0};
        try(var input=new ZipInputStream(stream)) {
            for(ZipEntry entry;(entry=input.getNextEntry())!=null;) {
                String name=entry.getName();boolean manifest=name.equals("manual-draft.json"),image=name.startsWith("images/");
                if(entry.isDirectory()||!entries.add(name)||entries.size()>201||!(manifest
                        ||name.matches("audio/[0-9]{3}-[a-f0-9]{64}\\.(mp3|wav|m4a)")||name.matches("images/[0-9]{3}-[a-f0-9]{64}\\.(png|jpe?g)")))
                    throw ExamApiException.invalidInput("Đường dẫn media trong gói đề không hợp lệ.");
                int limit=manifest?RichExamJson.MAX_BYTES:(image?8:64)*1024*1024;
                byte[] bytes=input.readNBytes(limit+1);
                if(bytes.length==0||bytes.length>limit)throw ExamApiException.invalidInput("Tệp trong gói đề vượt giới hạn dung lượng.");
                if(!manifest) {
                    int index=image?1:0;totals[index]+=bytes.length;counts[index]++;
                    if(counts[index]>100||totals[index]>(image?64L:256L)*1024*1024)throw ExamApiException.invalidInput("Media của đề vượt giới hạn dung lượng hoặc số tệp.");
                    String file=name.substring(name.indexOf('/')+1);
                    if(!vn.edu.toeic.server.audio.AudioCatalog.sha256(bytes).equals(file.substring(4,68)))throw ExamApiException.invalidInput("Nội dung media không khớp checksum.");
                    ManualExamMedia.metadata(file,bytes,image);files.add(name);
                }
                Path target=temp.resolve(name);Files.createDirectories(target.getParent());Files.write(target,bytes,StandardOpenOption.CREATE_NEW);
            }
        }catch(ZipException bad){throw ExamApiException.invalidInput("Gói đề tải lên bị hỏng.");}
        if(!entries.contains("manual-draft.json"))throw ExamApiException.invalidInput("Gói đề thiếu nội dung.");return files;
    }
}
