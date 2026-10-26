package vn.edu.toeic.server.exam.rich;

import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.toeic.protocol.Role;
import vn.edu.toeic.protocol.exam.rich.CandidateCatalogItem;
import vn.edu.toeic.protocol.exam.rich.CandidateCatalogPage;
import vn.edu.toeic.server.auth.AuthenticatedUser;
import vn.edu.toeic.server.auth.AuthorizationService;
import vn.edu.toeic.server.exam.ExamApiException;

@Service
public class CandidateDirectoryService {
    private final JdbcClient jdbc;
    private final AuthorizationService authorization;

    public CandidateDirectoryService(JdbcClient jdbc, AuthorizationService authorization) {
        this.jdbc = jdbc;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public CandidateCatalogPage candidates(AuthenticatedUser user, Map<String, String[]> query) {
        authorization.requireRole(user, Role.PROCTOR);
        for (var entry : query.entrySet()) {
            if (!Set.of("search", "limit", "afterUserId").contains(entry.getKey())
                    || entry.getValue() == null || entry.getValue().length != 1 || entry.getValue()[0] == null) {
                throw ExamApiException.invalidInput("Tham số tìm thí sinh không hợp lệ.");
            }
        }
        int limit = (int) positive(query, "limit", 20, 100);
        long after = positive(query, "afterUserId", 0, Long.MAX_VALUE);
        String search = query.containsKey("search") ? query.get("search")[0].trim() : "";
        if (search.length() > 100 || search.chars().anyMatch(Character::isISOControl)) {
            throw ExamApiException.invalidInput("Từ khóa tìm thí sinh tối đa 100 ký tự.");
        }
        // '!' is the LIKE escape character, so user-entered %, _ and ! are literal text.
        String pattern = "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var rows = jdbc.sql("""
                SELECT id, username, display_name FROM user_accounts
                WHERE role = 'CANDIDATE' AND enabled = TRUE AND id > :after
                  AND (username ILIKE :search ESCAPE '!' OR display_name ILIKE :search ESCAPE '!')
                ORDER BY id LIMIT :limit
                """).param("after", after).param("search", pattern).param("limit", limit + 1)
                .query((r, n) -> new CandidateCatalogItem(r.getLong("id"), r.getString("username"), r.getString("display_name")))
                .list();
        boolean more = rows.size() > limit;
        var page = more ? rows.subList(0, limit) : rows;
        return new CandidateCatalogPage(2, page, more ? page.getLast().userId() : null);
    }

    private static long positive(Map<String, String[]> query, String name, long fallback, long max) {
        if (!query.containsKey(name)) return fallback;
        try {
            String value = query.get(name)[0];
            if (!value.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
            long parsed = Long.parseLong(value);
            if (parsed > max) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException invalid) {
            throw ExamApiException.invalidInput(name + " không hợp lệ.");
        }
    }
}
