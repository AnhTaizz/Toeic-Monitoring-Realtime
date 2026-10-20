package vn.edu.toeic.dataset;
import java.nio.file.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.toeic.dataset.Json.*;
class DatasetArchiveTest {
 @TempDir Path temp;
 Path fixture()throws Exception {
  Path repo=temp.resolve("repo");Files.createDirectories(repo.resolve(".git"));Path data=repo.resolve("datasets/mock");Files.createDirectories(data);Path media=repo.resolve("audio/mock-exam/001-mock.wav");Files.createDirectories(media.getParent());Files.writeString(media,"MOCK bytes for archive integrity, not a playback fixture");
  write(data.resolve("normalized.json"),obj("exam",obj("examId","mock-exam"),"source",obj("path","missing-original.html","sha256","a".repeat(64)),"questions",arr(obj("questionId","MOCK")),"media",arr(obj("mediaId","mock-audio","mediaType","AUDIO","localFile","001-mock.wav","sizeBytes",Files.size(media),"sha256",hash(media)))));
  write(data.resolve("draft.v2.json"),obj("schemaVersion",2,"exam",obj("examId","mock-exam"),"MOCK",true));return repo;
 }
 void create(Path repo,Path archive)throws Exception {new DatasetArchive().create(repo,repo.resolve("datasets"),repo.resolve("audio"),repo.resolve("images"),archive,"MOCK-COMMIT");}
 @Test void archiveRestoreDoesNotNeedOriginalHtmlOrWebsite()throws Exception {Path repo=fixture(),archive=temp.resolve("archive"),restored=temp.resolve("restored");create(repo,archive);new DatasetArchive().restore(archive,restored);assertEquals(hash(repo.resolve("datasets/mock/normalized.json")),hash(restored.resolve("datasets/mock/normalized.json")));assertEquals(hash(repo.resolve("audio/mock-exam/001-mock.wav")),hash(restored.resolve("audio-resources/mock-exam/001-mock.wav")));assertFalse(read(restored.resolve("archive-manifest.json")).getAsJsonArray("datasets").get(0).getAsJsonObject().get("importReady").getAsBoolean());}
 @Test void refusesMissingOrChangedMedia()throws Exception {Path repo=fixture();Files.writeString(repo.resolve("audio/mock-exam/001-mock.wav"),"MOCK changed");assertThrows(IOException.class,()->create(repo,temp.resolve("bad")));}
 @Test void corruptArchiveRejectedBeforeDestinationIsCreated()throws Exception {Path repo=fixture(),archive=temp.resolve("archive"),dest=temp.resolve("dest");create(repo,archive);Files.writeString(archive.resolve("audio-resources/mock-exam/001-mock.wav"),"MOCK corruption");assertThrows(IOException.class,()->new DatasetArchive().restore(archive,dest));assertFalse(Files.exists(dest));}
 @Test void noOverwriteAndNoWorktreeBackup()throws Exception {Path repo=fixture();assertThrows(IOException.class,()->create(repo,repo.resolve("inside")));Path archive=temp.resolve("archive");create(repo,archive);assertThrows(IOException.class,()->create(repo,archive));assertThrows(IOException.class,()->new DatasetArchive().restore(archive,repo));}
 @Test void rejectsArchiveTraversalPaths() {for(String s:new String[]{"../escape","/abs","C:/secret","a\\b","a/../b"})assertThrows(IOException.class,()->DatasetArchive.safeResolve(temp,s));}
}
