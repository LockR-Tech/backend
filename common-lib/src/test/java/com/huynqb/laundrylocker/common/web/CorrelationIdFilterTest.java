package com.huynqb.laundrylocker.common.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void preservesSafeIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationIds.HEADER_NAME, "trace-2026-abc");

        filter.doFilter(request, response, chainAssertingMdc("trace-2026-abc"));

        assertEquals("trace-2026-abc", request.getAttribute(CorrelationIds.REQUEST_ATTRIBUTE));
        assertEquals("trace-2026-abc", response.getHeader(CorrelationIds.HEADER_NAME));
        assertNull(MDC.get(CorrelationIds.MDC_KEY));
    }

    @Test
    void generatesCorrelationIdWhenIncomingHeaderIsUnsafe() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationIds.HEADER_NAME, "../../bad value");

        filter.doFilter(request, response, chainAssertingGeneratedMdc());

        String generated = response.getHeader(CorrelationIds.HEADER_NAME);
        assertNotNull(generated);
        assertEquals(generated, request.getAttribute(CorrelationIds.REQUEST_ATTRIBUTE));
        assertTrue(generated.length() >= 8);
        assertNull(MDC.get(CorrelationIds.MDC_KEY));
    }

    private MockFilterChain chainAssertingMdc(String expectedCorrelationId) {
        return new MockFilterChain(
                new HttpServlet() {
                    @Override
                    protected void service(HttpServletRequest request, HttpServletResponse response)
                            throws ServletException, IOException {
                        assertEquals(expectedCorrelationId, MDC.get(CorrelationIds.MDC_KEY));
                        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    }
                });
    }

    private MockFilterChain chainAssertingGeneratedMdc() {
        return new MockFilterChain(
                new HttpServlet() {
                    @Override
                    protected void service(HttpServletRequest request, HttpServletResponse response)
                            throws ServletException, IOException {
                        String correlationId = MDC.get(CorrelationIds.MDC_KEY);
                        assertNotNull(correlationId);
                        assertEquals(correlationId, request.getAttribute(CorrelationIds.REQUEST_ATTRIBUTE));
                        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    }
                });
    }
}
