package com.clickchecker.eventuser.controller;

import com.clickchecker.event.repository.EventRepository;
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
class EventUserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventUserRepository eventUserRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private EventRepository eventRepository;

    @Test
    void create_returnsId_whenRequestIsValid() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");

        mockMvc.perform(
                        post("/api/event-users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "organizationId": %d,
                                          "externalUserId": "u-1001"
                                        }
                                        """.formatted(organization.getId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber());

        assertThat(eventUserRepository.count()).isEqualTo(1);
        assertThat(eventUserRepository.findAll().getFirst().getExternalUserId()).isEqualTo("u-1001");
    }

    @Test
    void create_returnsConflict_whenExternalUserIdIsDuplicatedInSameOrganization() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");

        mockMvc.perform(
                        post("/api/event-users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "organizationId": %d,
                                          "externalUserId": "u-1001"
                                        }
                                        """.formatted(organization.getId()))
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/event-users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "organizationId": %d,
                                          "externalUserId": "u-1001"
                                        }
                                        """.formatted(organization.getId()))
                )
                .andExpect(status().isConflict());
    }

    @Test
    void create_allowsSameExternalUserId_whenOrganizationIsDifferent() throws Exception {
        cleanup();
        Organization organizationA = saveOrganization("acme");
        Organization organizationB = saveOrganization("globex");

        mockMvc.perform(
                        post("/api/event-users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "organizationId": %d,
                                          "externalUserId": "u-1001"
                                        }
                                        """.formatted(organizationA.getId()))
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/event-users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "organizationId": %d,
                                          "externalUserId": "u-1001"
                                        }
                                        """.formatted(organizationB.getId()))
                )
                .andExpect(status().isOk());

        assertThat(eventUserRepository.count()).isEqualTo(2);
    }

    @Test
    void create_returnsBadRequest_whenOrganizationIsInvalid() throws Exception {
        cleanup();

        mockMvc.perform(
                        post("/api/event-users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "organizationId": 9999,
                                          "externalUserId": "u-1001"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returnsBadRequest_whenExternalUserIdIsBlank() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");

        mockMvc.perform(
                        post("/api/event-users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "organizationId": %d,
                                          "externalUserId": ""
                                        }
                                        """.formatted(organization.getId()))
                )
                .andExpect(status().isBadRequest());
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
}
