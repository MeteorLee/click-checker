package com.clickchecker.organization.controller;

import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.eventuser.repository.EventUserRepository;
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
class OrganizationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private EventUserRepository eventUserRepository;

    @Autowired
    private EventRepository eventRepository;

    @Test
    void create_returnsId_whenRequestIsValid() throws Exception {
        cleanup();

        mockMvc.perform(
                        post("/api/organizations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "acme"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber());

        assertThat(organizationRepository.count()).isEqualTo(1);
        assertThat(organizationRepository.findAll().getFirst().getName()).isEqualTo("acme");
    }

    @Test
    void create_returnsBadRequest_whenNameIsBlank() throws Exception {
        cleanup();

        mockMvc.perform(
                        post("/api/organizations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": ""
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());
    }

    private void cleanup() {
        eventRepository.deleteAll();
        eventUserRepository.deleteAll();
        organizationRepository.deleteAll();
    }
}
