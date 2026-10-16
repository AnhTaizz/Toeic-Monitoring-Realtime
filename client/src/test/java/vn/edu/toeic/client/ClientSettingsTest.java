package vn.edu.toeic.client;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClientSettingsTest {
    @TempDir Path folder;
    @Test void explicitPropertyAndEnvironmentOverrideDurableUserConfig()throws Exception{
        Path file=folder.resolve("server-url.txt");Files.writeString(file,"http://file.invalid:8081");
        assertThat(ClientSettings.readConfiguration("http://property.invalid","http://env.invalid",file).serverUrl()).isEqualTo("http://property.invalid");
        assertThat(ClientSettings.readConfiguration(null,"http://env.invalid",file).serverUrl()).isEqualTo("http://env.invalid");
    }
    @Test void unicodeUserPathIsReadWithoutWritingInstallOrChangingConfig()throws Exception{
        Path file=folder.resolve("Cấu hình server có dấu.txt");String original="https://example.invalid:8443/\n";Files.writeString(file,original);
        var cfg=ClientSettings.readConfiguration(null,null,file);assertThat(cfg.serverUrl()).isEqualTo("https://example.invalid:8443/");assertThat(cfg.notice()).isEmpty();assertThat(Files.readString(file)).isEqualTo(original);
    }
    @Test void invalidOrOversizedUserFileHasSafeNoticeAndRemainsEditable()throws Exception{
        Path file=folder.resolve("server-url.txt");Files.writeString(file,"http://user:TEST-secret@example.invalid/?token=TEST");
        var invalid=ClientSettings.readConfiguration(null,null,file);assertThat(invalid.notice()).isNotEmpty().doesNotContain("TEST-secret","token=");assertThat(invalid.serverUrl()).isEqualTo("http://127.0.0.1:8080");
        Files.writeString(file,"x".repeat(4097));assertThat(ClientSettings.readConfiguration(null,null,file).notice()).isNotEmpty();
    }
    @Test void missingFileRetainsDefaultWithoutCreatingUserState(){
        Path file=folder.resolve("missing.txt");var cfg=ClientSettings.readConfiguration(null,null,file);
        assertThat(cfg.serverUrl()).isEqualTo("http://127.0.0.1:8080");assertThat(cfg.notice()).isEmpty();assertThat(file).doesNotExist();
    }
}
