package com.clickchecker.web.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.clickchecker.account.entity.Account;
import com.clickchecker.account.entity.AccountStatus;
import com.clickchecker.account.repository.AccountRepository;
import com.clickchecker.auth.service.JwtTokenProvider;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class JwtAuthFilterTest {

    private static final String REQUEST_ID = "request-1234";

    private final JwtTokenProvider jwtTokenProvider =
            new JwtTokenProvider("local-dev-jwt-secret-local-dev-jwt-secret-123456", 900);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwtTokenProvider, accountRepository);

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldReturnUnauthorizedWhenAuthorizationHeaderIsMissing() throws Exception {
        ListAppender<ILoggingEvent> appender = attachAppender(Level.WARN);
        MockHttpServletResponse response = doFilter(null);

        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_UNAUTHORIZED);
        assertThat(joinedMessages(appender)).contains("reason=missing_or_invalid_authorization_header");
    }

    @Test
    void shouldReturnUnauthorizedWhenTokenIsInvalid() throws Exception {
        ListAppender<ILoggingEvent> appender = attachAppender(Level.WARN);
        MockHttpServletResponse response = doFilter("Bearer invalid-token");

        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_UNAUTHORIZED);
        assertThat(joinedMessages(appender)).contains("reason=invalid_access_token");
    }

    @Test
    void shouldReturnForbiddenWhenAccountIsDisabled() throws Exception {
        Account account = Account.builder()
                .loginId("alice")
                .passwordHash("hashed")
                .status(AccountStatus.DISABLED)
                .build();
        ReflectionTestUtils.setField(account, "id", 1L);
        String token = jwtTokenProvider.issueAccessToken(1L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        ListAppender<ILoggingEvent> appender = attachAppender(Level.WARN);
        MockHttpServletResponse response = doFilter("Bearer " + token);

        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_FORBIDDEN);
        assertThat(joinedMessages(appender)).contains("reason=account_disabled");
    }

    @Test
    void shouldSetAccountIdWhenTokenIsValid() throws Exception {
        Account account = Account.builder()
                .loginId("alice")
                .passwordHash("hashed")
                .status(AccountStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(account, "id", 1L);
        String token = jwtTokenProvider.issueAccessToken(1L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        ListAppender<ILoggingEvent> appender = attachAppender(Level.DEBUG);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put(RequestIdFilter.MDC_KEY, REQUEST_ID);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
        assertThat(request.getAttribute(JwtAuthFilter.AUTH_ACCOUNT_ID)).isEqualTo(1L);
        assertThat(joinedMessages(appender))
                .contains("jwt auth success")
                .contains("accountId=1");
    }

    private MockHttpServletResponse doFilter(String authorizationHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/me");
        if (authorizationHeader != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorizationHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put(RequestIdFilter.MDC_KEY, REQUEST_ID);

        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private ListAppender<ILoggingEvent> attachAppender(Level level) {
        Logger logger = (Logger) LoggerFactory.getLogger(JwtAuthFilter.class);
        logger.detachAndStopAllAppenders();
        logger.setLevel(level);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private String joinedMessages(ListAppender<ILoggingEvent> appender) {
        List<String> messages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        return String.join("\n", messages);
    }
}
