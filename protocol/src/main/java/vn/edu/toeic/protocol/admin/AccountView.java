package vn.edu.toeic.protocol.admin;

public record AccountView(long userId, String username, String displayName, String role, boolean enabled) { }
