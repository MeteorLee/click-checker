package com.clickchecker.analytics.support;

import com.clickchecker.event.entity.Event;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.eventtype.entity.EventTypeMapping;
import com.clickchecker.eventtype.repository.EventTypeMappingRepository;
import com.clickchecker.eventuser.entity.EventUser;
import com.clickchecker.eventuser.repository.EventUserRepository;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.organization.service.ApiKeyService;
import com.clickchecker.route.entity.RouteTemplate;
import com.clickchecker.route.repository.RouteTemplateRepository;
import com.clickchecker.web.filter.ApiKeyAuthFilter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public abstract class AnalyticsControllerIntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected EventRepository eventRepository;

    @Autowired
    protected OrganizationRepository organizationRepository;

    @Autowired
    protected EventUserRepository eventUserRepository;

    @Autowired
    protected ApiKeyService apiKeyService;

    @Autowired
    protected RouteTemplateRepository routeTemplateRepository;

    @Autowired
    protected EventTypeMappingRepository eventTypeMappingRepository;

    @BeforeEach
    void cleanupBeforeEach() {
        cleanup();
    }

    @AfterEach
    void cleanupAfterEach() {
        cleanup();
    }

    protected void cleanup() {
        eventRepository.deleteAll();
        eventUserRepository.deleteAll();
        eventTypeMappingRepository.deleteAll();
        routeTemplateRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    protected Organization saveOrganization() {
        return saveOrganization("acme");
    }

    protected Organization saveOrganization(String name) {
        return organizationRepository.save(
                Organization.builder()
                        .name(name)
                        .build()
        );
    }

    protected EventUser saveEventUser(Organization organization, String externalUserId) {
        return eventUserRepository.save(
                EventUser.builder()
                        .organization(organization)
                        .externalUserId(externalUserId)
                        .build()
        );
    }

    protected void saveRouteTemplate(Organization organization, String template, String routeKey, int priority) {
        routeTemplateRepository.save(
                RouteTemplate.builder()
                        .organization(organization)
                        .template(template)
                        .routeKey(routeKey)
                        .priority(priority)
                        .active(true)
                        .build()
        );
    }

    protected void saveEventTypeMapping(
            Organization organization,
            String rawEventType,
            String canonicalEventType
    ) {
        eventTypeMappingRepository.save(
                EventTypeMapping.builder()
                        .organization(organization)
                        .rawEventType(rawEventType)
                        .canonicalEventType(canonicalEventType)
                        .active(true)
                        .build()
        );
    }

    protected void saveEvent(
            Organization organization,
            EventUser eventUser,
            String eventType,
            String path,
            String occurredAt
    ) {
        eventRepository.save(Event.builder()
                .organization(organization)
                .eventUser(eventUser)
                .eventType(eventType)
                .path(path)
                .occurredAt(Instant.parse(occurredAt))
                .build());
    }

    protected String issueApiKey(Organization organization) {
        return apiKeyService.issueForOrganization(organization.getId()).apiKey();
    }

    protected MockHttpServletRequestBuilder authorizedGet(String apiKey, String path) {
        return get(path).header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey);
    }

    protected Instant toInstant(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }
}
