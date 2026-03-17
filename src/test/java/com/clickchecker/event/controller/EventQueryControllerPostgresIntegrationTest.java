package com.clickchecker.event.controller;

import com.clickchecker.event.entity.Event;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.organization.service.ApiKeyService;
import com.clickchecker.web.filter.ApiKeyAuthFilter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@Tag("postgres")
@ActiveProfiles("ci")
@SpringBootTest
@AutoConfigureMockMvc
class EventQueryControllerPostgresIntegrationTest {

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
    private OrganizationRepository organizationRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @Test
    void aggregateTimeBuckets_groupsByHour_inPostgreSQL() throws Exception {
        cleanup();

        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        LocalDateTime base = LocalDateTime.of(2026, 2, 13, 10, 0);
        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).occurredAt(toInstant(base.plusMinutes(1))).build());
        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).occurredAt(toInstant(base.plusMinutes(25))).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organization).occurredAt(toInstant(base.plusHours(1).plusMinutes(5))).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/time-buckets")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("bucket", "HOUR")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.bucket").value("HOUR"))
                .andExpect(jsonPath("$.items.length()").value(24))
                .andExpect(jsonPath("$.items[0].bucketStart").value("2026-02-13T00:00:00Z"))
                .andExpect(jsonPath("$.items[0].count").value(0))
                .andExpect(jsonPath("$.items[10].bucketStart").value("2026-02-13T10:00:00Z"))
                .andExpect(jsonPath("$.items[10].count").value(2))
                .andExpect(jsonPath("$.items[11].bucketStart").value("2026-02-13T11:00:00Z"))
                .andExpect(jsonPath("$.items[11].count").value(1))
                .andExpect(jsonPath("$.items[23].bucketStart").value("2026-02-13T23:00:00Z"))
                .andExpect(jsonPath("$.items[23].count").value(0));
    }

    @Test
    void aggregatePaths_returnsBadRequest_whenFromIsNotBeforeTo_inPostgreSQL() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/paths")
                                .param("from", "2026-02-14T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "5")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void aggregatePaths_returnsBadRequest_whenTopIsOutOfRange_inPostgreSQL() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/paths")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "0")
                )
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/paths")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "101")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void aggregatePaths_returnsBadRequest_whenDateTimeFormatIsInvalid_inPostgreSQL() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/paths")
                                .param("from", "invalid-date")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "5")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void aggregatePaths_returnsUnauthorized_whenApiKeyIsMissing_inPostgreSQL() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");

        mockMvc.perform(
                        get("/api/v1/events/analytics/aggregates/paths")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "5")
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aggregatePaths_returnsUnauthorized_whenApiKeyIsInvalid_inPostgreSQL() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");

        mockMvc.perform(
                        authorizedGet("ck_test_v1_invalid_deadbeef", "/api/v1/events/analytics/aggregates/paths")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "5")
                )
                .andExpect(status().isUnauthorized());
    }

    private void cleanup() {
        eventRepository.deleteAll();
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

    private MockHttpServletRequestBuilder authorizedGet(String apiKey, String path) {
        return get(path).header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey);
    }

    private Instant toInstant(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }
}
