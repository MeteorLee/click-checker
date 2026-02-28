package com.clickchecker.event.controller;

import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.eventuser.entity.EventUser;
import com.clickchecker.eventuser.repository.EventUserRepository;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("ci")
@SpringBootTest
@AutoConfigureMockMvc
class EventCommandControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventUserRepository eventUserRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Test
    void create_succeeds_whenExternalUserIdBelongsToOrganization() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        EventUser eventUser = saveEventUser(organization, "u-1001");

        mockMvc.perform(
                        post("/api/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "organizationId": %d,
                                          "externalUserId": "%s",
                                          "eventType": "click",
                                          "path": "/home",
                                          "occurredAt": "2026-02-13T15:03:00",
                                          "payload": "buttonId=signup"
                                        }
                                        """.formatted(organization.getId(), eventUser.getExternalUserId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber());

        assertThat(eventRepository.count()).isEqualTo(1);
    }

    @Test
    void create_createsEventUserInRequestedOrganization_whenExternalUserIdExistsInAnotherOrganization() throws Exception {
        cleanup();
        Organization organizationA = saveOrganization("acme");
        Organization organizationB = saveOrganization("globex");
        EventUser eventUserInB = saveEventUser(organizationB, "u-2001");

        mockMvc.perform(
                        post("/api/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "organizationId": %d,
                                          "externalUserId": "%s",
                                          "eventType": "click",
                                          "path": "/home",
                                          "occurredAt": "2026-02-13T15:03:00",
                                          "payload": "buttonId=signup"
                                        }
                                        """.formatted(organizationA.getId(), eventUserInB.getExternalUserId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber());

        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(eventUserRepository.count()).isEqualTo(2);
    }

    @Test
    void create_createsEventUser_whenExternalUserIdDoesNotExist() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");

        mockMvc.perform(
                        post("/api/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "organizationId": %d,
                                          "externalUserId": "u-9999",
                                          "eventType": "click",
                                          "path": "/home",
                                          "occurredAt": "2026-02-13T15:03:00",
                                          "payload": "buttonId=signup"
                                        }
                                        """.formatted(organization.getId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber());

        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(eventUserRepository.count()).isEqualTo(1);
        assertThat(eventUserRepository.findAll().getFirst().getExternalUserId()).isEqualTo("u-9999");
    }

    private void cleanup() {
        eventRepository.deleteAll();
        eventUserRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    private Organization saveOrganization(String name) {
        return organizationRepository.save(
                Organization.builder()
                        .name(name)
                        .build()
        );
    }

    private EventUser saveEventUser(Organization organization, String externalUserId) {
        return eventUserRepository.save(
                EventUser.builder()
                        .organization(organization)
                        .externalUserId(externalUserId)
                        .build()
        );
    }
}
