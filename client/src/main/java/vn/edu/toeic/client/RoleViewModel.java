package vn.edu.toeic.client;

import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.auth.LoginResponse;

record RoleViewModel(String heading, String description) {
    static RoleViewModel from(LoginResponse response) {
        Role role = Role.valueOf(response.user().role());
        return switch (role) {
            case CANDIDATE -> new RoleViewModel(
                    "Giao diện thí sinh",
                    "Đăng nhập thành công. Collector chưa được bật ở task T1-B1.");
            case PROCTOR -> new RoleViewModel(
                    "Giao diện giám thị",
                    "Đăng nhập thành công. Collector không chạy trên role giám thị.");
        };
    }
}
