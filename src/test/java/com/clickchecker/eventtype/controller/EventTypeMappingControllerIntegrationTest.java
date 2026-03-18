package com.clickchecker.eventtype.controller;

import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.eventtype.entity.EventTypeMapping;
import com.clickchecker.eventtype.repository.EventTypeMappingRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("ci")
@SpringBootTest
@AutoConfigureMockMvc
class EventTypeMappingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventTypeMappingRepository eventTypeMappingRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventUserRepository eventUserRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Test
    void create_savesEventTypeMappingUnderAuthenticatedOrganization() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        mockMvc.perform(
                        authorizedPost(apiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "rawEventType": "button_click",
                                          "canonicalEventType": "click"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.rawEventType").value("button_click"))
                .andExpect(jsonPath("$.canonicalEventType").value("click"))
                .andExpect(jsonPath("$.active").value(true));

        assertThat(eventTypeMappingRepository.count()).isEqualTo(1);
        assertThat(eventTypeMappingRepository.findAll().getFirst().getOrganization().getId())
                .isEqualTo(organization.getId());
    }

    @Test
    void create_returnsUnauthorized_whenApiKeyIsMissing() throws Exception {
        cleanup();
        saveOrganization("acme");

        mockMvc.perform(
                        post("/api/events/event-type-mappings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "rawEventType": "button_click",
                                          "canonicalEventType": "click"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAll_returnsOnlyAuthenticatedOrganizationMappings_inRawEventTypeOrder() throws Exception {
        cleanup();
        Organization organizationA = saveOrganization("acme");
        Organization organizationB = saveOrganization("globex");
        String apiKey = issueApiKey(organizationA);

        saveEventTypeMapping(organizationA, "button_click", "click");
        saveEventTypeMapping(organizationA, "page_view", "view");
        saveEventTypeMapping(organizationB, "hidden_event", "hidden");

        mockMvc.perform(authorizedGet(apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].rawEventType").value("button_click"))
                .andExpect(jsonPath("$.items[0].canonicalEventType").value("click"))
                .andExpect(jsonPath("$.items[1].rawEventType").value("page_view"))
                .andExpect(jsonPath("$.items[1].canonicalEventType").value("view"));
    }

    @Test
    void update_modifiesMappingUnderAuthenticatedOrganization() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);
        EventTypeMapping eventTypeMapping = saveEventTypeMapping(organization, "button_click", "click");

        mockMvc.perform(
                        authorizedPut(apiKey, eventTypeMapping.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "rawEventType": "cta_click",
                                          "canonicalEventType": "interaction"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventTypeMapping.getId()))
                .andExpect(jsonPath("$.rawEventType").value("cta_click"))
                .andExpect(jsonPath("$.canonicalEventType").value("interaction"))
                .andExpect(jsonPath("$.active").value(true));

        EventTypeMapping updated = eventTypeMappingRepository.findById(eventTypeMapping.getId()).orElseThrow();
        assertThat(updated.getRawEventType()).isEqualTo("cta_click");
        assertThat(updated.getCanonicalEventType()).isEqualTo("interaction");
    }

    @Test
    void updateActive_togglesActiveFlagUnderAuthenticatedOrganization() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);
        EventTypeMapping eventTypeMapping = saveEventTypeMapping(organization, "button_click", "click");

        mockMvc.perform(
                        authorizedPutActive(apiKey, eventTypeMapping.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "active": false
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventTypeMapping.getId()))
                .andExpect(jsonPath("$.active").value(false));

        EventTypeMapping updated = eventTypeMappingRepository.findById(eventTypeMapping.getId()).orElseThrow();
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    void delete_removesMappingUnderAuthenticatedOrganization() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);
        EventTypeMapping eventTypeMapping = saveEventTypeMapping(organization, "button_click", "click");

        mockMvc.perform(authorizedDelete(apiKey, eventTypeMapping.getId()))
                .andExpect(status().isNoContent());

        assertThat(eventTypeMappingRepository.findById(eventTypeMapping.getId())).isEmpty();
    }

    private void cleanup() {
        eventRepository.deleteAll();
        eventTypeMappingRepository.deleteAll();
        eventUserRepository.deleteAll();
        organizationMemberRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    private Organization saveOrganization(String name) {
        return organizationRepository.save(
                Organization.builder()
                        .name(name)
                        .build()
        );
    }

    private String issueApiKey(Organization organization) {
        return apiKeyService.issueForOrganization(organization.getId()).apiKey();
    }

    private EventTypeMapping saveEventTypeMapping(
            Organization organization,
            String rawEventType,
            String canonicalEventType
    ) {
        return eventTypeMappingRepository.save(
                EventTypeMapping.builder()
                        .organization(organization)
                        .rawEventType(rawEventType)
                        .canonicalEventType(canonicalEventType)
                        .active(true)
                        .build()
        );
    }

    private MockHttpServletRequestBuilder authorizedGet(String apiKey) {
        return get("/api/events/event-type-mappings")
                .header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey);
    }

    private MockHttpServletRequestBuilder authorizedPut(String apiKey, Long eventTypeMappingId) {
        return put("/api/events/event-type-mappings/{eventTypeMappingId}", eventTypeMappingId)
                .header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey);
    }

    private MockHttpServletRequestBuilder authorizedPutActive(String apiKey, Long eventTypeMappingId) {
        return put("/api/events/event-type-mappings/{eventTypeMappingId}/active", eventTypeMappingId)
                .header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey);
    }

    private MockHttpServletRequestBuilder authorizedDelete(String apiKey, Long eventTypeMappingId) {
        return delete("/api/events/event-type-mappings/{eventTypeMappingId}", eventTypeMappingId)
                .header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey);
    }

    private MockHttpServletRequestBuilder authorizedPost(String apiKey) {
        return post("/api/events/event-type-mappings")
                .header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey);
    }
}
