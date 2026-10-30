package vn.edu.toeic.server.exam.rich;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.admin.ManualExamDraft;
import vn.edu.toeic.server.auth.*;
import vn.edu.toeic.server.exam.ExamApiException;

/** Publish selected local audio and its draft together; restore files on any database failure. */
@Service
public class ManualExamBundleService {
    private final Path root;
    private final ManualExamService manual;
    private final AdminExamService exams;
    private final AuthorizationService authorization;
    private final RichExamJson json;
    private final TransactionTemplate transaction;
    public ManualExamBundleService(@Value("${toeic.audio.root:audio-resources}") String root,
            ManualExamService manual,AdminExamService exams,AuthorizationService authorization,
            RichExamJson json,PlatformTransactionManager manager) {
        this.root=Path.of(root).toAbsolutePath().normalize();this.manual=manual;this.exams=exams;
        this.authorization=authorization;this.json=json;transaction=new TransactionTemplate(manager);
    }
    public ManualExamDraft upload(AuthenticatedUser admin,String replaceId,InputStream input) throws IOException {
        authorization.requireRole(admin,Role.ADMIN);
        Path temp=Files.createTempDirectory("toeic-manual-upload-");
        var published=new ArrayList<Path>();Path[] createdDirectory={null};
        try {
            Set<String> names=extract(input,temp);
            ManualExamDraft draft;
            try(var body=Files.newInputStream(temp.resolve("manual-draft.json"))){draft=json.read(body,ManualExamDraft.class);}
            ManualExamService.validate(draft);
            var referenced=new HashSet<String>();
            for(var q:draft.questions())if(q.audioFile()!=null&&!q.audioFile().isBlank())referenced.add(q.audioFile());
            for(String name:names)if(!referenced.contains(name))throw ExamApiException.invalidInput("Gói nháp chứa âm thanh không được dùng trong đề.");
            try {
                return transaction.execute(status->{
                    exams.prepareWrite(admin,replaceId,draft.examId());
                    try {
                        Files.createDirectories(root);Path realRoot=root.toRealPath();Path directory=realRoot.resolve(draft.examId());
                        if(!Files.exists(directory,LinkOption.NOFOLLOW_LINKS)){Files.createDirectory(directory);createdDirectory[0]=directory;}
                        if(Files.isSymbolicLink(directory)||!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))throw ExamApiException.invalidInput("Thư mục âm thanh không hợp lệ.");
                        for(String name:names) {
                            Path source=temp.resolve(name),target=directory.resolve(name);
                            if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)) {
                                if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)||Files.mismatch(source,target)!=-1)
                                    throw ExamApiException.invalidInput("Âm thanh trên server không khớp nội dung tải lên.");
                            }else {Files.copy(source,target);published.add(target);}
                        }
                        for(String name:referenced)if(!Files.isRegularFile(directory.resolve(name),LinkOption.NOFOLLOW_LINKS))
                            throw ExamApiException.invalidInput("Thiếu âm thanh: "+name+". Hãy chọn lại tệp từ máy.");
                        return manual.save(admin,replaceId,draft);
                    }catch(IOException bad){throw new UncheckedIOException(bad);}
                });
            }catch(RuntimeException bad) {
                for(Path path:published)Files.deleteIfExists(path);
                if(createdDirectory[0]!=null)Files.deleteIfExists(createdDirectory[0]);
                throw bad;
            }
        }finally{try(var paths=Files.walk(temp)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
    private static Set<String> extract(InputStream stream,Path temp) throws IOException {
        Set<String> entries=new HashSet<>(),audio=new LinkedHashSet<>();long total=0;
        try(var input=new ZipInputStream(stream)) {
            for(ZipEntry entry;(entry=input.getNextEntry())!=null;) {
                String name=entry.getName();boolean manifest=name.equals("manual-draft.json");
                if(entry.isDirectory()||!entries.add(name)||entries.size()>101
                        ||!(manifest||name.matches("audio/[0-9]{3}-[a-f0-9]{64}\\.(mp3|wav|m4a)")))
                    throw ExamApiException.invalidInput("Gói nháp có đường dẫn hoặc số tệp không hợp lệ.");
                int limit=manifest?RichExamJson.MAX_BYTES:64*1024*1024;
                byte[] bytes=input.readNBytes(limit+1);total+=bytes.length;
                if(bytes.length==0||bytes.length>limit||total>258L*1024*1024)throw ExamApiException.invalidInput("Gói nháp vượt giới hạn dung lượng.");
                String file=manifest?name:name.substring(6);
                if(!manifest) {
                    if(!vn.edu.toeic.server.audio.AudioCatalog.sha256(bytes).equals(file.substring(4,68)))throw ExamApiException.invalidInput("Nội dung âm thanh không khớp checksum.");
                    if(!audioHeader(file,bytes))throw ExamApiException.invalidInput("Nội dung tệp không phải âm thanh MP3, WAV hoặc M4A hợp lệ.");
                    audio.add(file);
                }
                Files.write(temp.resolve(file),bytes,StandardOpenOption.CREATE_NEW);
            }
        }catch(ZipException bad){throw ExamApiException.invalidInput("Gói nháp ZIP bị hỏng.");}
        if(!entries.contains("manual-draft.json"))throw ExamApiException.invalidInput("Gói nháp thiếu nội dung đề.");return audio;
    }
    private static boolean audioHeader(String name,byte[] b) {
        if(b.length<12)return false;
        if(name.endsWith(".wav"))return ascii(b,0,4).equals("RIFF")&&ascii(b,8,12).equals("WAVE");
        if(name.endsWith(".m4a"))return ascii(b,4,8).equals("ftyp");
        return ascii(b,0,3).equals("ID3")||((b[0]&255)==255&&(b[1]&224)==224);
    }
    private static String ascii(byte[] b,int start,int end){return new String(b,start,end-start,java.nio.charset.StandardCharsets.US_ASCII);}
}
