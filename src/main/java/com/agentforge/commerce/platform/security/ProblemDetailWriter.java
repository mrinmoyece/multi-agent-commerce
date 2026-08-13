package com.agentforge.commerce.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * Serialises a {@link ProblemDetail} to the servlet response for security
 * failures raised before the MVC exception handler runs. Adds the correlation id
 * so a rejected call can still be traced end to end.
 */
final class ProblemDetailWriter {

    static final String CORRELATION_ID = "correlationId";

    private ProblemDetailWriter() {
    }

    static void write(ObjectMapper objectMapper, HttpServletResponse response,
                      HttpStatus status, String title, String detail,
                      HttpServletRequest request) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("about:blank"));
        problem.setInstance(URI.create(request.getRequestURI()));
        String correlationId = MDC.get(CORRELATION_ID);
        if (correlationId != null) {
            problem.setProperty(CORRELATION_ID, correlationId);
        }

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
