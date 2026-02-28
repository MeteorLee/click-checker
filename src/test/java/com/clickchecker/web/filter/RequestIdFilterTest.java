package com.clickchecker.web.filter;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void shouldGenerateRequestIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String responseHeader = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(responseHeader).isNotBlank();
        assertThatCodeIsUuid(responseHeader);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void shouldPropagateIncomingRequestIdHeader() throws Exception {
        String incomingRequestId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, incomingRequestId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo(incomingRequestId);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    private void assertThatCodeIsUuid(String value) {
        UUID parsed = UUID.fromString(value);
        assertThat(parsed).isNotNull();
    }
}
