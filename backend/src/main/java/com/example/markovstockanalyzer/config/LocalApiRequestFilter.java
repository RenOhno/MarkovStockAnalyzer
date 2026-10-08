package com.example.markovstockanalyzer.config;

import com.example.markovstockanalyzer.exception.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Local, unauthenticated MVP boundary. Forwarded headers are intentionally not trusted. */
@Component
public class LocalApiRequestFilter extends OncePerRequestFilter {
    static final int MAX_BODY_BYTES = 5 * 1024 * 1024;
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");
    private final JsonMapper json = JsonMapper.builder().build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store");
        URI target = authority(request.getScheme(), request.getHeader("Host") == null
                ? request.getServerName() + ":" + request.getServerPort() : request.getHeader("Host"));
        if (target == null || !LOCAL_HOSTS.contains(target.getHost().toLowerCase(Locale.ROOT))) {
            reject(response, 403, "INVALID_HOST", "Request host is not allowed");
            return;
        }
        if (!"POST".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        String origin = request.getHeader("Origin");
        if ((origin != null && !sameOrigin(target, origin))
                || "cross-site".equalsIgnoreCase(request.getHeader("Sec-Fetch-Site"))) {
            reject(response, 403, "INVALID_ORIGIN", "Request origin is not allowed");
            return;
        }
        try {
            MediaType contentType = request.getContentType() == null ? null
                    : MediaType.parseMediaType(request.getContentType());
            if (contentType == null || !"application".equals(contentType.getType())
                    || !"json".equals(contentType.getSubtype())) {
                reject(response, 415, "UNSUPPORTED_MEDIA_TYPE", "Request must use application/json");
                return;
            }
        } catch (IllegalArgumentException invalidContentType) {
            reject(response, 415, "UNSUPPORTED_MEDIA_TYPE", "Request must use application/json");
            return;
        }
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            reject(response, 413, "PAYLOAD_TOO_LARGE", "Request body exceeds 5 MiB");
            return;
        }
        // Bound unknown/chunked lengths as well; MVC must read this same verified byte sequence.
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            reject(response, 413, "PAYLOAD_TOO_LARGE", "Request body exceeds 5 MiB");
            return;
        }
        chain.doFilter(new BufferedRequest(request, body), response);
    }

    private static URI authority(String scheme, String host) {
        try {
            URI uri = URI.create(scheme + "://" + host);
            return uri.getHost() != null && uri.getRawUserInfo() == null
                    && uri.getRawPath().isEmpty() && uri.getRawQuery() == null && uri.getRawFragment() == null
                    ? uri : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static boolean sameOrigin(URI target, String value) {
        try {
            URI origin = URI.create(value);
            return origin.getHost() != null && origin.getRawUserInfo() == null
                    && origin.getRawPath().isEmpty() && origin.getRawQuery() == null && origin.getRawFragment() == null
                    && target.getScheme().equals(origin.getScheme())
                    && target.getHost().equalsIgnoreCase(origin.getHost()) && port(target) == port(origin);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static int port(URI uri) {
        return uri.getPort() < 0 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort();
    }

    private void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        String requestId = UUID.randomUUID().toString();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Request-Id", requestId);
        json.writeValue(response.getOutputStream(), new ApiErrorResponse(code, message, requestId, Map.of()));
    }

    private static final class BufferedRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private BufferedRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream stream = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return stream.read(); }
                @Override public boolean isFinished() { return stream.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Synchronous JSON requests only");
                }
            };
        }
    }
}
