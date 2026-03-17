package com.clickchecker.analytics.retention.controller;

import com.clickchecker.event.entity.Event;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.eventtype.repository.EventTypeMappingRepository;
import com.clickchecker.eventuser.entity.EventUser;
import com.clickchecker.eventuser.repository.EventUserRepository;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.organization.service.ApiKeyService;
import com.clickchecker.route.repository.RouteTemplateRepository;
import com.clickchecker.web.filter.ApiKeyAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("ci")
@SpringBootTest
@AutoConfigureMockMvc
class RetentionMatrixControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventTypeMappingRepository eventTypeMappingRepository;

    @Autowired
    private EventUserRepository eventUserRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private RouteTemplateRepository routeTemplateRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @Test
    void matrix_returnsCustomDayRetentionValues() throws Exception {
        cleanup();

        Organization organization = saveOrganization();
        String apiKey = issueApiKey(organization);

        EventUser user1 = saveEventUser(organization, "user-1");
        EventUser user2 = saveEventUser(organization, "user-2");

        saveEvent(organization, user1, "view", "/landing", "2026-03-01T01:00:00Z");
        saveEvent(organization, user1, "view", "/landing", "2026-03-02T03:00:00Z");
        saveEvent(organization, user1, "view", "/landing", "2026-03-04T03:00:00Z");

        saveEvent(organization, user2, "view", "/landing", "2026-03-01T12:00:00Z");
        saveEvent(organization, user2, "view", "/landing", "2026-03-08T12:00:00Z");

        mockMvc.perform(
                        authorizedGet(apiKey)
                                .param("from", "2026-03-01T00:00:00Z")
                                .param("to", "2026-03-08T00:00:00Z")
                                .param("timezone", "Asia/Seoul")
                                .param("days", "1")
                                .param("days", "3")
                                .param("days", "7")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.days[0]").value(1))
                .andExpect(jsonPath("$.days[1]").value(3))
                .andExpect(jsonPath("$.days[2]").value(7))
                .andExpect(jsonPath("$.items[0].cohortDate").value("2026-03-01"))
                .andExpect(jsonPath("$.items[0].cohortUsers").value(2))
                .andExpect(jsonPath("$.items[0].values[0].day").value(1))
                .andExpect(jsonPath("$.items[0].values[0].users").value(1))
                .andExpect(jsonPath("$.items[0].values[0].retentionRate").value(0.5))
                .andExpect(jsonPath("$.items[0].values[1].day").value(3))
                .andExpect(jsonPath("$.items[0].values[1].users").value(1))
                .andExpect(jsonPath("$.items[0].values[2].day").value(7))
                .andExpect(jsonPath("$.items[0].values[2].users").value(1));
    }

    @Test
    void matrix_filtersOutSmallCohorts_usingMinCohortUsers() throws Exception {
        cleanup();

        Organization organization = saveOrganization();
        String apiKey = issueApiKey(organization);

        EventUser user1 = saveEventUser(organization, "user-1");
        EventUser user2 = saveEventUser(organization, "user-2");
        EventUser user3 = saveEventUser(organization, "user-3");

        saveEvent(organization, user1, "view", "/landing", "2026-03-01T01:00:00Z");
        saveEvent(organization, user1, "view", "/landing", "2026-03-02T03:00:00Z");

        saveEvent(organization, user2, "view", "/landing", "2026-03-01T12:00:00Z");

        saveEvent(organization, user3, "view", "/landing", "2026-03-02T01:00:00Z");
        saveEvent(organization, user3, "view", "/landing", "2026-03-03T01:00:00Z");

        mockMvc.perform(
                        authorizedGet(apiKey)
                                .param("from", "2026-03-01T00:00:00Z")
                                .param("to", "2026-03-08T00:00:00Z")
                                .param("timezone", "Asia/Seoul")
                                .param("days", "1")
                                .param("minCohortUsers", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].cohortDate").value("2026-03-01"))
                .andExpect(jsonPath("$.items[0].cohortUsers").value(2));
    }

    private void cleanup() {
        eventRepository.deleteAll();
        eventTypeMappingRepository.deleteAll();
        routeTemplateRepository.deleteAll();
        eventUserRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    private Organization saveOrganization() {
        return organizationRepository.save(Organization.builder()
                .name("click-checker")
                .build());
    }

    private EventUser saveEventUser(Organization organization, String externalUserId) {
        return eventUserRepository.save(EventUser.builder()
                .organization(organization)
                .externalUserId(externalUserId)
                .build());
    }

    private void saveEvent(
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

    private String issueApiKey(Organization organization) {
        return apiKeyService.issueForOrganization(organization.getId()).apiKey();
    }

    private MockHttpServletRequestBuilder authorizedGet(String apiKey) {
        return get("/api/v1/events/analytics/retention/matrix")
                .header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey);
    }
}
