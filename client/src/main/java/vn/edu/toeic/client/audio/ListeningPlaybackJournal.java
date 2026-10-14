package vn.edu.toeic.client.audio;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import vn.edu.toeic.protocol.audio.ListeningStartCommand;

/** Durable local one-shot marker. A reserved command is never treated as replay permission after restart. */
public final class ListeningPlaybackJournal {
    private final Path directory;
    public ListeningPlaybackJournal(Path directory) { this.directory=directory.toAbsolutePath().normalize(); }
    public synchronized boolean reserve(ListeningStartCommand command) throws IOException {
        Files.createDirectories(directory);
        Path marker=marker(command);
        try {
            Files.writeString(marker,json(Map.of("attemptId",command.attemptId(),"startId",command.startId(),
                    "writerEpoch",command.writerEpoch(),"reservedAt",Instant.now().toString(),"playbackStarted",false)),
                    StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            return true;
        } catch(FileAlreadyExistsException duplicate){return false;}
    }
    public synchronized void started(ListeningStartCommand command,Instant at) throws IOException {
        Path marker=marker(command),temporary=Files.createTempFile(directory,"started-",".tmp");
        try {
            Files.writeString(temporary,json(Map.of("attemptId",command.attemptId(),"startId",command.startId(),
                    "writerEpoch",command.writerEpoch(),"playbackStarted",true,"playbackStartedAt",at.toString())),StandardCharsets.UTF_8);
            try { Files.move(temporary,marker,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException ignored){Files.move(temporary,marker,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temporary);}
    }
    Path marker(ListeningStartCommand command){return directory.resolve(hash(command.attemptId()+"\0"+command.writerEpoch()+"\0"+command.startId())+".json");}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    private static String json(Object value){return new GsonBuilder().disableHtmlEscaping().create().toJson(value)+"\n";}
}
