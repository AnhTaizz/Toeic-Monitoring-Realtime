package vn.edu.toeic.client;

final class ClientSettings {
    private static final String DEFAULT_SERVER_URL = "http://127.0.0.1:8080";

    private ClientSettings() {
    }

    static String serverUrl() {
        String property = System.getProperty("toeic.server.url");
        if (property != null && !property.isBlank()) {
            return property;
        }
        String environment = System.getenv("TOEIC_SERVER_URL");
        return environment == null || environment.isBlank() ? DEFAULT_SERVER_URL : environment;
    }
}
