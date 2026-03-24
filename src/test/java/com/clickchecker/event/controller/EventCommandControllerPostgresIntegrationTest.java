package com.clickchecker.event.controller;

import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.eventuser.entity.EventUser;
import com.clickchecker.eventuser.repository.EventUserRepository;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.organization.service.ApiKeyService;
import com.clickchecker.organizationmember.repository.OrganizationMemberRepository;
import com.clickchecker.web.filter.ApiKeyAuthFilter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@Tag("postgres")
@ActiveProfiles("ci")
@SpringBootTest
@AutoConfigureMockMvc
class EventCommandControllerPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("click_checker_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Autowired
    private MockMvc mockMvc;

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
    void create_succeeds_whenExternalUserIdBelongsToOrganization_inPostgreSQL() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        EventUser eventUser = saveEventUser(organization, "u-1001");
        String apiKey = issueApiKey(organization);

        mockMvc.perform(
                        authorizedEventPost(apiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "externalUserId": "%s",
                                          "eventType": "click",
                                          "path": "/home",
                                          "occurredAt": "2026-02-13T15:03:00Z",
                                          "payload": "buttonId=signup"
                                        }
                                        """.formatted(eventUser.getExternalUserId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber());

        assertThat(eventRepository.count()).isEqualTo(1);
    }

    @Test
    void create_createsEventUserInRequestedOrganization_whenExternalUserIdExistsInAnotherOrganization_inPostgreSQL() throws Exception {
        cleanup();
        Organization organizationA = saveOrganization("acme");
        Organization organizationB = saveOrganization("globex");
        EventUser eventUserInB = saveEventUser(organizationB, "u-2001");
        String apiKey = issueApiKey(organizationA);

        mockMvc.perform(
                        authorizedEventPost(apiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "externalUserId": "%s",
                                          "eventType": "click",
                                          "path": "/home",
                                          "occurredAt": "2026-02-13T15:03:00Z",
                                          "payload": "buttonId=signup"
                                        }
                                        """.formatted(eventUserInB.getExternalUserId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber());

        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(eventUserRepository.count()).isEqualTo(2);
    }

    @Test
    void create_createsEventUser_whenExternalUserIdDoesNotExist_inPostgreSQL() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        mockMvc.perform(
                        authorizedEventPost(apiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "externalUserId": "u-9999",
                                          "eventType": "click",
                                          "path": "/home",
                                          "occurredAt": "2026-02-13T15:03:00Z",
                                          "payload": "buttonId=signup"
                                        }
                                        """)
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

    private EventUser saveEventUser(Organization organization, String externalUserId) {
        return eventUserRepository.save(
                EventUser.builder()
                        .organization(organization)
                        .externalUserId(externalUserId)
                        .build()
        );
    }

    private String issueApiKey(Organization organization) {
        return apiKeyService.issueForOrganization(organization.getId()).apiKey();
    }

    private MockHttpServletRequestBuilder authorizedEventPost(String apiKey) {
        return post("/api/events").header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey);
    }
}
