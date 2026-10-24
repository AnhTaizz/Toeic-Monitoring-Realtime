package vn.edu.toeic.protocol.admin;

import java.util.List;

public record AccountPage(java.util.List<AccountView> items, Long nextAfterUserId) {
    public AccountPage { items = List.copyOf(items); }
}
