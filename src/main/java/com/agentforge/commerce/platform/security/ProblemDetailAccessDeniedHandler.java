package com.agentforge.commerce.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Emits an RFC 9457 {@code application/problem+json} body for authenticated but
 * insufficiently authorised requests (missing scope/role).
 */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final transient ObjectMapper objectMapper;

    public ProblemDetailAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ProblemDetailWriter.write(objectMapper, response, HttpStatus.FORBIDDEN,
                "Access denied",
                "The presented token lacks the scope or role required for this operation.",
                request);
    }
}
