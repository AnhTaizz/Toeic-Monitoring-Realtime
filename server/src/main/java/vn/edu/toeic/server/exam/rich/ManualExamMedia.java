package vn.edu.toeic.server.exam.rich;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edu.toeic.protocol.admin.ManualExamDraft;
import vn.edu.toeic.protocol.exam.rich.*;
import vn.edu.toeic.server.exam.ExamApiException;
import vn.edu.toeic.server.audio.AudioCatalog;

/** Exam-scoped allowlist and metadata for selected audio/images, including PENDING content. */
@Component
public class ManualExamMedia {
    final Path audioRoot,imageRoot;
    public ManualExamMedia(@Value("${toeic.audio.root:audio-resources}") String audio,@Value("${toeic.image.root:image-resources}") String images) {
        audioRoot=Path.of(audio).toAbsolutePath().normalize();imageRoot=Path.of(images).toAbsolutePath().normalize();
    }
    Map<String,MediaImport> inspect(ManualExamDraft draft) {
        var media=new LinkedHashMap<String,MediaImport>();long audioBytes=0,imageBytes=0;int audios=0,images=0;
        for(var q:draft.questions()) {
            var names=new ArrayList<>(q.imageFiles());if(q.audioFile()!=null&&!q.audioFile().isBlank())names.add(q.audioFile());
            for(String name:names) {
                if(media.containsKey(name))continue;boolean image=q.imageFiles().contains(name);
                byte[] bytes=read(draft.examId(),name,image);var m=metadata(name,bytes,image);media.put(name,m);
                if(image){images++;imageBytes+=bytes.length;}else{audios++;audioBytes+=bytes.length;}
            }
        }
        if(audios>100||images>100||audioBytes>256L*1024*1024||imageBytes>64L*1024*1024)throw ExamApiException.invalidInput("Media của đề vượt giới hạn dung lượng hoặc số tệp.");
        return media;
    }
    byte[] read(String exam,String name,boolean image) {
        try {
            Path root=(image?imageRoot:audioRoot).toRealPath(),directory=root.resolve(exam),file=directory.resolve(name);
            if(!RichExamValidator.safeFile(name)||Files.isSymbolicLink(directory)||!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS)
                    ||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||!file.toRealPath().startsWith(directory.toRealPath()))throw new IOException();
            try(var input=Files.newInputStream(file)){
                int limit=(image?8:64)*1024*1024;byte[] bytes=input.readNBytes(limit+1);
                if(bytes.length==0||bytes.length>limit)throw new IOException();return bytes;
            }
        }catch(IOException bad){throw ExamApiException.invalidInput("Không đọc được media của đề: "+name+". Hãy chọn lại tệp.");}
    }
    static MediaImport metadata(String name,byte[] bytes,boolean image) {
        String hash=AudioCatalog.sha256(bytes),id=(image?"image-":"audio-")+name.substring(0,name.lastIndexOf('.'));
        if(!image) {
            String mime=name.endsWith(".wav")?"audio/wav":name.endsWith(".mp3")?"audio/mpeg":"audio/mp4";
            if(!audioHeader(name,bytes))throw ExamApiException.invalidInput("Tệp âm thanh không đúng định dạng MP3/WAV/M4A.");
            return new MediaImport(id,MediaType.AUDIO,"manual",null,StorageStatus.LOCAL_VERIFIED,name,mime,bytes.length,hash,null,null,null,null);
        }
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException();var reader=readers.next();
            try {
                reader.setInput(input);int width=reader.getWidth(0),height=reader.getHeight(0);String format=reader.getFormatName();
                if(width<1||height<1||width>8192||height>8192||(long)width*height>16000000)throw new IOException();
                String mime=format.equalsIgnoreCase("png")?"image/png":format.equalsIgnoreCase("jpeg")?"image/jpeg":null;
                if(mime==null||mime.equals("image/png")!=name.endsWith(".png")||reader.read(0)==null)throw new IOException();
                return new MediaImport(id,MediaType.IMAGE,"manual",null,StorageStatus.LOCAL_VERIFIED,name,mime,bytes.length,hash,width,height,null,null);
            }finally{reader.dispose();}
        }catch(IOException | RuntimeException bad){throw ExamApiException.invalidInput("Ảnh phải là PNG/JPEG hợp lệ, tối đa 8192 px và 16 triệu pixel.");}
    }
    private static boolean audioHeader(String name,byte[] b) {
        if(b.length<12)return false;
        if(name.endsWith(".wav"))return ascii(b,0,4).equals("RIFF")&&ascii(b,8,12).equals("WAVE");
        if(name.endsWith(".m4a"))return ascii(b,4,8).equals("ftyp");
        return ascii(b,0,3).equals("ID3")||((b[0]&255)==255&&(b[1]&224)==224);
    }
    private static String ascii(byte[] b,int start,int end){return new String(b,start,end-start,java.nio.charset.StandardCharsets.US_ASCII);}
}
