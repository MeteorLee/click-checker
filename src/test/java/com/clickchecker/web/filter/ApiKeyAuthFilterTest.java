package com.clickchecker.web.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.clickchecker.organization.entity.ApiKeyStatus;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.organization.service.ApiKeyIssuer;
import com.clickchecker.security.principal.ApiKeyPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyAuthFilterTest {

    private static final String VALID_API_KEY = "ck_live_v1_abcd1234_secretsecretsecret";
    private static final String INVALID_FORMAT_API_KEY = "plain-secret-key";
    private static final String HASHED_API_KEY = "hashed-api-key";
    private static final String REQUEST_ID = "request-1234";
    private static final long ORGANIZATION_ID = 101L;

    private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    private final ApiKeyIssuer apiKeyIssuer = mock(ApiKeyIssuer.class);
    private final ApiKeyAuthFilter filter = new ApiKeyAuthFilter(organizationRepository, apiKeyIssuer);

    @AfterEach
    void tearDown() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldNotLogPlainApiKeyWhenFormatIsInvalid() throws Exception {
        when(apiKeyIssuer.extractKid(INVALID_FORMAT_API_KEY))
                .thenThrow(new IllegalArgumentException("Invalid API key format."));

        ListAppender<ILoggingEvent> appender = attachAppender(Level.WARN);
        MockHttpServletResponse response = doFilterWithApiKey(INVALID_FORMAT_API_KEY);

        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_UNAUTHORIZED);
        assertThat(joinedMessages(appender))
                .contains("reason=invalid_key_format")
                .doesNotContain(INVALID_FORMAT_API_KEY);
    }

    @Test
    void shouldNotLogPlainApiKeyWhenKidDoesNotExist() throws Exception {
        when(apiKeyIssuer.extractKid(VALID_API_KEY)).thenReturn("abcd1234");
        when(organizationRepository.findByApiKeyKidAndApiKeyStatus("abcd1234", ApiKeyStatus.ACTIVE))
                .thenReturn(Optional.empty());

        ListAppender<ILoggingEvent> appender = attachAppender(Level.WARN);
        MockHttpServletResponse response = doFilterWithApiKey(VALID_API_KEY);

        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_UNAUTHORIZED);
        assertThat(joinedMessages(appender))
                .contains("reason=kid_not_found_or_inactive")
                .doesNotContain(VALID_API_KEY);
    }

    @Test
    void shouldNotLogPlainApiKeyWhenHashDoesNotMatch() throws Exception {
        Organization organization = Organization.builder()
                .name("acme")
                .apiKeyKid("abcd1234")
                .apiKeyHash("stored-hash")
                .build();

        when(apiKeyIssuer.extractKid(VALID_API_KEY)).thenReturn("abcd1234");
        when(organizationRepository.findByApiKeyKidAndApiKeyStatus("abcd1234", ApiKeyStatus.ACTIVE))
                .thenReturn(Optional.of(organization));
        when(apiKeyIssuer.hash(VALID_API_KEY)).thenReturn(HASHED_API_KEY);
        when(apiKeyIssuer.constantTimeEquals(HASHED_API_KEY, "stored-hash")).thenReturn(false);

        ListAppender<ILoggingEvent> appender = attachAppender(Level.WARN);
        MockHttpServletResponse response = doFilterWithApiKey(VALID_API_KEY);

        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_UNAUTHORIZED);
        assertThat(joinedMessages(appender))
                .contains("reason=hash_mismatch")
                .doesNotContain(VALID_API_KEY)
                .doesNotContain(HASHED_API_KEY);
    }

    @Test
    void shouldLogOnlyMaskedKidOnSuccess() throws Exception {
        Organization organization = Organization.builder()
                .name("acme")
                .apiKeyKid("abcd1234")
                .apiKeyHash("stored-hash")
                .build();
        ReflectionTestUtils.setField(organization, "id", ORGANIZATION_ID);

        when(apiKeyIssuer.extractKid(VALID_API_KEY)).thenReturn("abcd1234");
        when(organizationRepository.findByApiKeyKidAndApiKeyStatus("abcd1234", ApiKeyStatus.ACTIVE))
                .thenReturn(Optional.of(organization));
        when(apiKeyIssuer.hash(VALID_API_KEY)).thenReturn(HASHED_API_KEY);
        when(apiKeyIssuer.constantTimeEquals(HASHED_API_KEY, "stored-hash")).thenReturn(true);

        ListAppender<ILoggingEvent> appender = attachAppender(Level.DEBUG);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/events");
        request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, VALID_API_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put(RequestIdFilter.MDC_KEY, REQUEST_ID);

        filter.doFilter(request, response, new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new ApiKeyPrincipal(ORGANIZATION_ID, "abcd1234"));
        assertThat(joinedMessages(appender))
                .contains("api key auth success")
                .contains("orgId=" + ORGANIZATION_ID)
                .contains("kidMasked=ab***34")
                .doesNotContain(VALID_API_KEY)
                .doesNotContain(HASHED_API_KEY)
                .doesNotContain("kidMasked=abcd1234");
    }

    @Test
    void shouldNotPersistApiKeyLastUsedAtWhenRecentlyUpdated() throws Exception {
        Organization organization = Organization.builder()
                .name("acme")
                .apiKeyKid("abcd1234")
                .apiKeyHash("stored-hash")
                .apiKeyLastUsedAt(Instant.now())
                .build();
        ReflectionTestUtils.setField(organization, "id", ORGANIZATION_ID);

        when(apiKeyIssuer.extractKid(VALID_API_KEY)).thenReturn("abcd1234");
        when(organizationRepository.findByApiKeyKidAndApiKeyStatus("abcd1234", ApiKeyStatus.ACTIVE))
                .thenReturn(Optional.of(organization));
        when(apiKeyIssuer.hash(VALID_API_KEY)).thenReturn(HASHED_API_KEY);
        when(apiKeyIssuer.constantTimeEquals(HASHED_API_KEY, "stored-hash")).thenReturn(true);

        MockHttpServletResponse response = doFilterWithApiKey(VALID_API_KEY);

        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
        verify(organizationRepository, never()).save(organization);
    }

    @Test
    void shouldPersistApiKeyLastUsedAtWhenMissing() throws Exception {
        Organization organization = Organization.builder()
                .name("acme")
                .apiKeyKid("abcd1234")
                .apiKeyHash("stored-hash")
                .build();
        ReflectionTestUtils.setField(organization, "id", ORGANIZATION_ID);

        when(apiKeyIssuer.extractKid(VALID_API_KEY)).thenReturn("abcd1234");
        when(organizationRepository.findByApiKeyKidAndApiKeyStatus("abcd1234", ApiKeyStatus.ACTIVE))
                .thenReturn(Optional.of(organization));
        when(apiKeyIssuer.hash(VALID_API_KEY)).thenReturn(HASHED_API_KEY);
        when(apiKeyIssuer.constantTimeEquals(HASHED_API_KEY, "stored-hash")).thenReturn(true);

        MockHttpServletResponse response = doFilterWithApiKey(VALID_API_KEY);

        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
        verify(organizationRepository, times(1)).save(organization);
        assertThat(organization.getApiKeyLastUsedAt()).isNotNull();
    }

    @Test
    void shouldNotHashWhenApiKeyHeaderIsMissing() throws Exception {
        ListAppender<ILoggingEvent> appender = attachAppender(Level.WARN);
        MockHttpServletResponse response = doFilterWithApiKey(null);

        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_UNAUTHORIZED);
        assertThat(joinedMessages(appender))
                .contains("reason=missing_api_key")
                .doesNotContain(VALID_API_KEY);
        verify(apiKeyIssuer, never()).extractKid(VALID_API_KEY);
        verify(apiKeyIssuer, never()).hash(VALID_API_KEY);
    }

    private MockHttpServletResponse doFilterWithApiKey(String apiKey) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/events");
        if (apiKey != null) {
            request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, apiKey);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put(RequestIdFilter.MDC_KEY, REQUEST_ID);

        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private ListAppender<ILoggingEvent> attachAppender(Level level) {
        Logger logger = (Logger) LoggerFactory.getLogger(ApiKeyAuthFilter.class);
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
