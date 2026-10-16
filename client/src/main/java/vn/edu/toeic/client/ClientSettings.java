package vn.edu.toeic.client;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;
import java.util.Set;

final class ClientSettings {
    private static final String DEFAULT_SERVER_URL="http://127.0.0.1:8080";
    record Configuration(String serverUrl,String notice) {}
    private static volatile Configuration userConfig=new Configuration(DEFAULT_SERVER_URL,"");
    private ClientSettings() {}

    /** Called by Launcher before JavaFX starts; no filesystem work on the FX thread. */
    static void initialize(){
        String local=System.getenv("LOCALAPPDATA");
        Path file=local==null||local.isBlank()?null:Path.of(local,"TOEIC Monitor","server-url.txt");
        userConfig=readConfiguration(System.getProperty("toeic.server.url"),System.getenv("TOEIC_SERVER_URL"),file);
    }
    static String serverUrl(){
        String property=System.getProperty("toeic.server.url");
        if(property!=null&&!property.isBlank())return property;
        String environment=System.getenv("TOEIC_SERVER_URL");
        return environment!=null&&!environment.isBlank()?environment:userConfig.serverUrl();
    }
    static String notice(){return userConfig.notice();}
    static Configuration readConfiguration(String property,String environment,Path file){
        if(property!=null&&!property.isBlank())return new Configuration(property,"");
        if(environment!=null&&!environment.isBlank())return new Configuration(environment,"");
        if(file==null||!Files.exists(file))return new Configuration(DEFAULT_SERVER_URL,"");
        try(var input=Files.newInputStream(file)){
            byte[] bytes=input.readNBytes(4097);
            if(bytes.length>4096)throw new IllegalArgumentException();
            String value=new String(bytes,StandardCharsets.UTF_8).trim();URI uri=URI.create(value);
            if(!Set.of("http","https").contains(uri.getScheme().toLowerCase(Locale.ROOT))||uri.getHost()==null
                    ||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||uri.getPort()==0
                    ||uri.getPort()>65535||!(uri.getPath().isEmpty()||uri.getPath().equals("/")))throw new IllegalArgumentException();
            return new Configuration(value,"");
        }catch(Exception invalid){return new Configuration(DEFAULT_SERVER_URL,"Không đọc được cấu hình địa chỉ server. Nhập địa chỉ hợp lệ để đăng nhập; không lưu credential trong file cấu hình.");}
    }
}
