package com.clickchecker.analytics.user.controller;

import com.clickchecker.event.entity.Event;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.eventtype.repository.EventTypeMappingRepository;
import com.clickchecker.eventuser.entity.EventUser;
import com.clickchecker.eventuser.repository.EventUserRepository;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.organization.service.ApiKeyService;
import com.clickchecker.organizationmember.repository.OrganizationMemberRepository;
import com.clickchecker.web.filter.ApiKeyAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("ci")
@SpringBootTest
@AutoConfigureMockMvc
class UserAnalyticsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private EventUserRepository eventUserRepository;

    @Autowired
    private EventTypeMappingRepository eventTypeMappingRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Test
    void overview_returnsIdentifiedNewReturningAndAverage() throws Exception {
        cleanup();

        Organization organization = saveOrganization();
        String apiKey = issueApiKey(organization);

        EventUser newUser = saveEventUser(organization, "new-user");
        EventUser returningUser = saveEventUser(organization, "returning-user");

        eventRepository.save(Event.builder()
                .organization(organization)
                .eventUser(returningUser)
                .eventType("view")
                .path("/landing")
                .occurredAt(Instant.parse("2026-03-01T10:00:00Z"))
                .build());

        eventRepository.save(Event.builder()
                .organization(organization)
                .eventUser(newUser)
                .eventType("click")
                .path("/signup")
                .occurredAt(Instant.parse("2026-03-12T10:00:00Z"))
                .build());
        eventRepository.save(Event.builder()
                .organization(organization)
                .eventUser(newUser)
                .eventType("click")
                .path("/signup")
                .occurredAt(Instant.parse("2026-03-13T10:00:00Z"))
                .build());
        eventRepository.save(Event.builder()
                .organization(organization)
                .eventUser(returningUser)
                .eventType("click")
                .path("/home")
                .occurredAt(Instant.parse("2026-03-14T10:00:00Z"))
                .build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/users/overview")
                                .param("from", "2026-03-10T00:00:00Z")
                                .param("to", "2026-03-17T00:00:00Z")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.totalEvents").value(3))
                .andExpect(jsonPath("$.identifiedEvents").value(3))
                .andExpect(jsonPath("$.anonymousEvents").value(0))
                .andExpect(jsonPath("$.identifiedUsers").value(2))
                .andExpect(jsonPath("$.newUsers").value(1))
                .andExpect(jsonPath("$.returningUsers").value(1))
                .andExpect(jsonPath("$.newUserEvents").value(2))
                .andExpect(jsonPath("$.returningUserEvents").value(1))
                .andExpect(jsonPath("$.avgEventsPerIdentifiedUser").value(1.5));
    }

    private void cleanup() {
        eventRepository.deleteAll();
        eventTypeMappingRepository.deleteAll();
        eventUserRepository.deleteAll();
        organizationMemberRepository.deleteAll();
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

    private String issueApiKey(Organization organization) {
        return apiKeyService.issueForOrganization(organization.getId()).apiKey();
    }

    private MockHttpServletRequestBuilder authorizedGet(String apiKey, String path) {
        return get(path)
                .header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey);
    }
}
