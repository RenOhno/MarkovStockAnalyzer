package com.example.markovstockanalyzer.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalApiRequestFilterTests {
    private final LocalApiRequestFilter filter = new LocalApiRequestFilter();
    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("POST", "/api/analysis");
        request.addHeader("Host", "127.0.0.1:8080");
        request.setContentType("application/json;charset=UTF-8");
        request.setContent("{\"conditionId\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return request;
    }
    @ParameterizedTest @ValueSource(strings = {"http://127.0.0.1:8080", ""})
    void permitsSameOriginAndNonBrowserJsonClients(String origin) throws Exception {
        var request = request(); if (!origin.isEmpty()) request.addHeader("Origin", origin);
        var response = new MockHttpServletResponse();
        FilterChain chain = (forwarded, ignored) -> assertEquals("{\"conditionId\":1}",
                new String(forwarded.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
    }
    @ParameterizedTest @ValueSource(strings = {"http://attacker.example", "null", "http://127.0.0.1:8081",
            "https://127.0.0.1:8080", "http://user@127.0.0.1:8080", "http://127.0.0.1:8080/path"})
    void rejectsForeignOrMalformedOriginsBeforeService(String origin) throws Exception {
        var request = request(); request.addHeader("Origin", origin);
        assertRejected(request, 403, "INVALID_ORIGIN");
    }
    @Test void rejectsReboundHostEvenWithMatchingOriginAndForwardedHeaders() throws Exception {
        var request = request(); request.removeHeader("Host"); request.addHeader("Host", "attacker.example:8080");
        request.addHeader("Origin", "http://attacker.example:8080"); request.addHeader("X-Forwarded-Host", "localhost:8080");
        assertRejected(request, 403, "INVALID_HOST");
    }
    @Test void rejectsCrossSiteFetchWithoutOrigin() throws Exception {
        var request = request(); request.addHeader("Sec-Fetch-Site", "cross-site");
        assertRejected(request, 403, "INVALID_ORIGIN");
    }
    @ParameterizedTest @ValueSource(strings = {"text/plain", "application/x-www-form-urlencoded", "invalid", "*/*", "application/*"})
    void rejectsNonJsonPosts(String type) throws Exception {
        var request = request(); request.setContentType(type); assertRejected(request, 415, "UNSUPPORTED_MEDIA_TYPE");
    }
    @Test void rejectsDeclaredOversizeWithoutReadingBody() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/analysis") {
            @Override public long getContentLengthLong() { return LocalApiRequestFilter.MAX_BODY_BYTES + 1L; }
            @Override public jakarta.servlet.ServletInputStream getInputStream() { throw new AssertionError("Body must not be read"); }
        };
        request.addHeader("Host", "localhost:8080"); request.setContentType("application/json");
        assertRejected(request, 413, "PAYLOAD_TOO_LARGE");
    }
    @Test void rejectsOversizeUnknownLength() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/analysis") {
            @Override public long getContentLengthLong() { return -1; }
        };
        request.addHeader("Host", "localhost:8080"); request.setContentType("application/json");
        request.setContent(new byte[LocalApiRequestFilter.MAX_BODY_BYTES + 1]);
        assertRejected(request, 413, "PAYLOAD_TOO_LARGE");
    }
    @Test void permitsExactlyFiveMiB() throws Exception {
        var request = request(); request.setContent(new byte[LocalApiRequestFilter.MAX_BODY_BYTES]);
        var response = new MockHttpServletResponse(); var chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain); verify(chain).doFilter(any(), same(response));
    }
    private void assertRejected(MockHttpServletRequest request, int status, String code) throws Exception {
        var response = new MockHttpServletResponse(); var chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        assertEquals(status, response.getStatus()); assertTrue(response.getContentAsString().contains(code));
        assertTrue(response.getContentAsString().contains(response.getHeader("X-Request-Id")));
        assertFalse(response.getContentAsString().contains("attacker.example")); verifyNoInteractions(chain);
    }
}
