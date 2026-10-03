package vn.edu.toeic.server.auth;

import com.google.gson.Gson;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.Collections;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.toeic.protocol.ErrorCode;
import vn.edu.toeic.protocol.Protocol;
import vn.edu.toeic.protocol.error.ApiErrorResponse;

@Component
public final class BearerAuthenticationFilter extends OncePerRequestFilter {
    private final SessionAuthenticationService authentication;
    private final Gson gson = new Gson();
    public BearerAuthenticationFilter(SessionAuthenticationService authentication) { this.authentication = authentication; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith("/api/") || ("POST".equals(request.getMethod()) && "/api/v1/auth/login".equals(path));
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws ServletException, IOException {
        final AuthenticatedUser user;
        try {
            if (request.getParameter("token") != null || request.getParameter("access_token") != null
                    || Collections.list(request.getHeaders("Authorization")).size() != 1) {
                throw AccessDeniedException.unauthorized();
            }
            user = authentication.authenticate(request.getHeader("Authorization")).user();
        } catch (AccessDeniedException exception) {
            reject(response, ErrorCode.UNAUTHORIZED, exception.getMessage(), false, 401);
            return;
        } catch (RuntimeException exception) {
            reject(response, ErrorCode.RETRYABLE_SERVER_ERROR, "Server tạm thời không xử lý được yêu cầu", true, 503);
            return;
        }
        request.setAttribute(AuthenticatedUser.ATTRIBUTE, user);
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public Principal getUserPrincipal() { return user; }
            @Override public boolean isUserInRole(String role) { return user.role().name().equals(role); }
        }, response);
    }
    private void reject(HttpServletResponse response, ErrorCode code, String message, boolean retryable, int status) throws IOException {
        response.setStatus(status);
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        gson.toJson(new ApiErrorResponse(Protocol.VERSION, "ERROR", null, UUID.randomUUID().toString(),
                new ApiErrorResponse.ErrorDetail(code.name(), message, retryable)), response.getWriter());
    }
}
