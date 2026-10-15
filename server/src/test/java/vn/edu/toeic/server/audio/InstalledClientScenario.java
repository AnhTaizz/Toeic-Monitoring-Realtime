package vn.edu.toeic.server.audio;

import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.jar.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.socket.*;
import vn.edu.toeic.server.realtime.RealtimeSessionRegistry;
import static org.assertj.core.api.Assertions.*;

/** TEST-only controller for the production installed EXE; real isolated Spring/PG fixture owned by parent. */
final class InstalledClientScenario {
    interface Action {void run()throws Exception;}
    static void run(String base,String password,String session,String attempt,ConfigurableApplicationContext context,
                    Action start,BooleanSupplier interrupted)throws Exception {
        Path install=Path.of(System.getenv("TOEIC_INSTALLED_CLIENT")).toAbsolutePath();
        Path output=Files.createDirectories(Path.of("target","t3-b5-installed",UUID.randomUUID().toString()));
        Path agent=output.resolve("TEST-ui-agent.jar").toAbsolutePath();
        var manifest=new Manifest();manifest.getMainAttributes().putValue("Manifest-Version","1.0");
        manifest.getMainAttributes().putValue("Premain-Class","vn.edu.toeic.client.InstalledClientAgent");
        Path classes=Path.of("..","client","target","test-classes","vn","edu","toeic","client");
        try(var jar=new JarOutputStream(Files.newOutputStream(agent),manifest);var files=Files.list(classes)){
            for(Path file:files.filter(p->p.getFileName().toString().matches("InstalledClientAgent.*\\.class")).toList()){
                jar.putNextEntry(new JarEntry("vn/edu/toeic/client/"+file.getFileName()));Files.copy(file,jar);jar.closeEntry();
            }
        }
        var builder=new ProcessBuilder(install.resolve("ToeicMonitor.exe").toString()).directory(install.toFile());
        var env=builder.environment();env.remove("JDK_JAVA_OPTIONS");env.remove("_JAVA_OPTIONS");env.remove("JAVA_HOME");
        env.put("PATH",System.getenv("SystemRoot")+"\\System32;"+System.getenv("SystemRoot"));
        env.put("JAVA_TOOL_OPTIONS","-javaagent:\""+agent+"\" -Duser.timezone=UTC");
        env.put("TOEIC_SERVER_URL",base);env.put("TOEIC_PACKAGE_TEST_INSTALL",install.toString());
        env.put("TOEIC_PACKAGE_TEST_OUTPUT",output.toAbsolutePath().toString());
        env.put("TOEIC_PACKAGE_TEST_PASSWORD",password);env.put("TOEIC_PACKAGE_TEST_SESSION",session);env.put("TOEIC_PACKAGE_TEST_ATTEMPT",attempt);
        // Exercise the default LOCALAPPDATA layout in an owned TEST profile; keep real user cache untouched.
        Path local=Files.createDirectories(output.resolve("Hồ sơ người dùng có dấu")).toAbsolutePath();
        env.put("LOCALAPPDATA",local.toString());
        builder.redirectOutput(output.resolve("client.stdout").toFile()).redirectError(output.resolve("client.stderr").toFile());
        Process process=builder.start();
        try {
            await(process,output,()->Files.exists(output.resolve("ready.signal")),"installed READY");start.run();
            await(process,output,()->Files.exists(output.resolve("playing.signal")),"native playback");
            // SIMULATED network fault: close actual owned server sockets, without changing firewall or OS network.
            var registry=context.getBean(RealtimeSessionRegistry.class);
            var field=RealtimeSessionRegistry.class.getDeclaredField("sessions");field.setAccessible(true);
            @SuppressWarnings("unchecked") var sockets=(Map<String,WebSocketSession>)field.get(registry);
            for(var socket:List.copyOf(sockets.values()))registry.close(socket,CloseStatus.SERVER_ERROR);
            await(process,output,interrupted,"INTERRUPTED persisted after reconnect");
            Files.writeString(output.resolve("close.signal"),"TEST");
            if(!process.waitFor(35,TimeUnit.SECONDS))throw new AssertionError("Installed client did not exit normally");
            assertThat(process.exitValue()).isZero();assertThat(output.resolve("failed.txt")).doesNotExist();
            assertThat(output.resolve("passed.txt")).exists();assertThat(output.resolve("dashboard.signal")).exists();assertThat(output.resolve("full.signal")).exists();
            Path cache=local.resolve("TOEIC Monitor/audio-cache");
            try(var files=Files.walk(cache.resolve("playback-started"))){assertThat(files.filter(Files::isRegularFile).count()).isPositive();}
            try(var files=Files.walk(cache)){assertThat(files.anyMatch(p->p.getFileName().toString().endsWith(".part"))).isFalse();}
            System.out.println("B5 INSTALLED PASS real production EXE/runtime, write denied, login/B4/FULL/audio/READY/WS interruption/reconnect/normal close; hearing+socket fault SIMULATED. Output: "+output);
        } finally {
            if(process.isAlive()){process.destroy();if(!process.waitFor(5,TimeUnit.SECONDS))process.destroyForcibly().waitFor(5,TimeUnit.SECONDS);}
            // Owned profile/cache is only TEST output under server/target, not shipped or user data.
        }
    }
    private static void await(Process process,Path output,BooleanSupplier condition,String description)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(100);
        while(!condition.getAsBoolean()){
            if(!process.isAlive()||Files.exists(output.resolve("failed.txt"))||System.nanoTime()>end)
                throw new AssertionError("B5 "+description+" failed; inspect "+output);
            Thread.sleep(50);
        }
    }
    static byte[] wave(){int bytes=8000*2*30;var b=ByteBuffer.allocate(44+bytes).order(ByteOrder.LITTLE_ENDIAN);b.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(36+bytes).put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16).put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(bytes);return b.array();}
}
