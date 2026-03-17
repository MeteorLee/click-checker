package com.clickchecker.analytics.funnel.controller;

import com.clickchecker.event.entity.Event;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.eventtype.entity.EventTypeMapping;
import com.clickchecker.eventtype.repository.EventTypeMappingRepository;
import com.clickchecker.eventuser.entity.EventUser;
import com.clickchecker.eventuser.repository.EventUserRepository;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.organization.service.ApiKeyService;
import com.clickchecker.web.filter.ApiKeyAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("ci")
@SpringBootTest
@AutoConfigureMockMvc
class FunnelAnalyticsControllerIntegrationTest {

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
    private ApiKeyService apiKeyService;

    @Test
    void report_returnsStepUsersAndConversionRates() throws Exception {
        cleanup();

        Organization organization = saveOrganization();
        String apiKey = issueApiKey(organization);

        saveEventTypeMapping(organization, "signup_button_click", "SIGN_UP");
        saveEventTypeMapping(organization, "purchase_complete", "PURCHASE");

        EventUser user1 = saveEventUser(organization, "user-1");
        EventUser user2 = saveEventUser(organization, "user-2");
        EventUser user3 = saveEventUser(organization, "user-3");

        saveEvent(organization, user1, "signup_button_click", "/signup", "2026-03-07T10:00:00Z");
        saveEvent(organization, user1, "purchase_complete", "/purchase", "2026-03-10T09:00:00Z");

        saveEvent(organization, user2, "signup_button_click", "/signup", "2026-03-07T12:00:00Z");
        saveEvent(organization, user2, "purchase_complete", "/purchase", "2026-03-07T13:00:00Z");

        saveEvent(organization, user3, "signup_button_click", "/signup", "2026-03-07T14:00:00Z");
        saveEvent(organization, user3, "purchase_complete", "/purchase", "2026-03-16T00:00:00Z");

        mockMvc.perform(
                        authorizedPost(apiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "from": "2026-03-01T00:00:00Z",
                                          "to": "2026-03-08T00:00:00Z",
                                          "steps": ["SIGN_UP", "PURCHASE"]
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.conversionWindow").value("7d"))
                .andExpect(jsonPath("$.items[0].canonicalEventType").value("SIGN_UP"))
                .andExpect(jsonPath("$.items[0].users").value(3))
                .andExpect(jsonPath("$.items[0].conversionRateFromFirstStep").value(1.0))
                .andExpect(jsonPath("$.items[0].previousStepUsers").doesNotExist())
                .andExpect(jsonPath("$.items[0].conversionRateFromPreviousStep").doesNotExist())
                .andExpect(jsonPath("$.items[0].dropOffUsersFromPreviousStep").doesNotExist())
                .andExpect(jsonPath("$.items[1].canonicalEventType").value("PURCHASE"))
                .andExpect(jsonPath("$.items[1].users").value(2))
                .andExpect(jsonPath("$.items[1].conversionRateFromFirstStep").value(2.0 / 3.0))
                .andExpect(jsonPath("$.items[1].previousStepUsers").value(3))
                .andExpect(jsonPath("$.items[1].conversionRateFromPreviousStep").value(2.0 / 3.0))
                .andExpect(jsonPath("$.items[1].dropOffUsersFromPreviousStep").value(1));
    }

    private void cleanup() {
        eventRepository.deleteAll();
        eventTypeMappingRepository.deleteAll();
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

    private void saveEventTypeMapping(Organization organization, String rawEventType, String canonicalEventType) {
        eventTypeMappingRepository.save(EventTypeMapping.builder()
                .organization(organization)
                .rawEventType(rawEventType)
                .canonicalEventType(canonicalEventType)
                .active(true)
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

    private MockHttpServletRequestBuilder authorizedPost(String apiKey) {
        return post("/api/v1/events/analytics/funnels/report")
                .header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey);
    }
}
