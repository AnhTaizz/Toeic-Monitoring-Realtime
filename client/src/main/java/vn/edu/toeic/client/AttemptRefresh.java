package vn.edu.toeic.client;

import java.util.List;
import java.util.Set;
import vn.edu.toeic.protocol.Role;

/**
 * Quyết định hiển thị của ô chọn lượt thi sau một lần hỏi server danh sách lượt ACTIVE.
 * Thuần Java để test không cần JavaFX; màn thí sinh chỉ áp kết quả lên control.
 *
 * <p>Danh sách này vẫn là scope monitoring ACTIVE-only do server trả; lớp này không cấp quyền gì.
 */
final class AttemptRefresh {
    enum Next {
        /** Không phải thí sinh hoặc server không cấp lượt ACTIVE nào. */
        NO_ATTEMPT,
        /** Đã có danh sách; chờ người dùng chọn rồi bấm vào thi. */
        CHOOSE,
        /** Người dùng bấm bật giám sát nhưng lượt đang chọn không còn trong scope. */
        SELECTION_REVOKED,
        /** Lượt đang chọn còn quyền: bật giám sát cho nó. */
        START_MONITORING,
        /** Chưa hỏi được server: giữ nguyên danh sách, cho bấm làm mới lại. */
        RETRY
    }

    record Outcome(List<String> items, String selected, boolean canEnter, Next next) {
    }

    private AttemptRefresh() {
    }

    /**
     * @param previousSelection lượt đang chọn trước khi làm mới; null khi ô chọn đang rỗng
     * @param startMonitoring   true khi người dùng bấm bật giám sát, false khi chỉ làm mới danh sách
     */
    static Outcome loaded(Role role, Set<String> freshScope, String previousSelection, boolean startMonitoring) {
        List<String> items = freshScope.stream().sorted().toList();
        // Set bất biến ném NullPointerException với contains(null), nên phải kiểm null trước.
        boolean kept = previousSelection != null && freshScope.contains(previousSelection);
        String selected = kept ? previousSelection : items.isEmpty() ? null : items.getFirst();
        if (role != Role.CANDIDATE || items.isEmpty()) return new Outcome(items, selected, false, Next.NO_ATTEMPT);
        if (!startMonitoring) return new Outcome(items, selected, true, Next.CHOOSE);
        return new Outcome(items, selected, true, kept ? Next.START_MONITORING : Next.SELECTION_REVOKED);
    }

    /** Lỗi mạng hoặc server: không đổi danh sách và lựa chọn; chưa cho vào thi tới khi làm mới được. */
    static Outcome failed(List<String> currentItems, String currentSelection) {
        return new Outcome(List.copyOf(currentItems), currentSelection, false, Next.RETRY);
    }
}
