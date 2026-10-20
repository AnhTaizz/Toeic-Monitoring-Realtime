package vn.edu.toeic.server.exam.rich;

import static org.assertj.core.api.Assertions.*;
import com.google.gson.JsonObject;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.toeic.server.exam.ExamApiException;

class RichMediaValidatorTest {
    @TempDir Path temp;
    @Test void actualMockPngAndWavAreVerifiedTwiceWithoutWritingRoots() throws Exception {
        var request=RichExamFixtures.request();Path audio=temp.resolve("audio"),image=temp.resolve("images");
        RichExamFixtures.files(audio,image,request);
        var validator=new RichMediaValidator(audio.toString(),image.toString());
        var stamps=validator.verify(request);assertThat(stamps).hasSize(57);
        validator.recheck(request,stamps);
        assertThat(Files.readAllBytes(image.resolve(request.exam().examId()).resolve("mock-grid.png")))
                .isEqualTo(Files.readAllBytes(RichExamFixtures.ROOT.resolve("assets/images/mock-ex02-200/mock-grid.png")));
    }

    @Test void windowsJunctionOrUnixDirectorySymlinkIsRejectedEvenWhenTargetIsInsideRoot() throws Exception {
        var request=RichExamFixtures.request();Path audio=temp.resolve("audio"),image=temp.resolve("images");
        RichExamFixtures.files(audio,image,request);
        Path exam=image.resolve(request.exam().examId()),target=image.resolve("operator-copy");
        Files.move(exam,target);
        boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
        if(windows) {
            var process=new ProcessBuilder("cmd.exe","/d","/c","mklink","/J",exam.toString(),target.toString()).redirectErrorStream(true).start();
            try {
                assertThat(process.waitFor(10,TimeUnit.SECONDS)).isTrue();
                process.getInputStream().readAllBytes();
                assertThat(process.exitValue()).as("Tạo junction thật, không SKIP thay cho PASS").isZero();
            } finally {process.destroy();}
        } else Files.createSymbolicLink(exam,target);
        try {
            assertThatThrownBy(()->new RichMediaValidator(audio.toString(),image.toString()).verify(request))
                    .isInstanceOfSatisfying(ExamApiException.class,e->assertThat(e.status().value()).isEqualTo(409));
        } finally {Files.delete(exam);}
    }

    @Test void validSignatureAndHashStillRequireSuccessfulImageDecodeAndExactDimensions() throws Exception {
        Path audio=temp.resolve("audio"),image=temp.resolve("images");
        var tree=RichExamFixtures.tree();var request=RichExamFixtures.request();RichExamFixtures.files(audio,image,request);
        var m=RichExamFixtures.media(tree,0);
        byte[] corrupt={ (byte)137,80,78,71,13,10,26,10,0,0,0,0 };
        Files.write(image.resolve(request.exam().examId()).resolve("mock-grid.png"),corrupt);
        m.addProperty("sizeBytes",corrupt.length);m.addProperty("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(corrupt)));
        var altered=RichExamFixtures.parse(tree.toString());
        assertThatThrownBy(()->new RichMediaValidator(audio.toString(),image.toString()).verify(altered))
                .isInstanceOfSatisfying(ExamApiException.class,e->assertThat(e.status().value()).isEqualTo(409));
    }
}
