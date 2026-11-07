package com.university.ums.security;

import com.university.ums.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Writes JSON 401/403 responses for requests rejected by the security filter chain
 * (before they ever reach a controller).
 */
@Component
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 401: no token, or an invalid / expired / revoked token. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String message = "Authentication required: send 'Authorization: Bearer <token>' (get one from POST /api/auth/login)";
        String wwwAuthenticate = "Bearer";
        if (authException instanceof OAuth2AuthenticationException oauth) {
            String description = oauth.getError().getDescription();
            message = "Invalid or expired token" + (description != null ? ": " + description : "");
            wwwAuthenticate = "Bearer error=\"invalid_token\"";
        }
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate);
        write(response, request, HttpStatus.UNAUTHORIZED, message);
    }

    /** 403: authenticated, but the role is not allowed to use this endpoint. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, request, HttpStatus.FORBIDDEN, "You do not have permission to access this resource");
    }

    private void write(HttpServletResponse response, HttpServletRequest request, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
