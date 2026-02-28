package com.clickchecker.event.controller;

import com.clickchecker.event.entity.Event;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
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

    @Test
    void aggregateTimeBuckets_groupsByHour_inPostgreSQL() throws Exception {
        cleanup();

        Organization organization = saveOrganization("acme");

        LocalDateTime base = LocalDateTime.of(2026, 2, 13, 10, 0);
        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).occurredAt(base.plusMinutes(1)).build());
        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).occurredAt(base.plusMinutes(25)).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organization).occurredAt(base.plusHours(1).plusMinutes(5)).build());

        mockMvc.perform(
                        get("/api/events/aggregates/time-buckets")
                                .param("organizationId", organization.getId().toString())
                                .param("from", "2026-02-13T00:00:00")
                                .param("to", "2026-02-14T00:00:00")
                                .param("bucket", "HOUR")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.bucket").value("HOUR"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].bucketStart").value("2026-02-13T10:00:00"))
                .andExpect(jsonPath("$.items[0].count").value(2))
                .andExpect(jsonPath("$.items[1].bucketStart").value("2026-02-13T11:00:00"))
                .andExpect(jsonPath("$.items[1].count").value(1));
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
}
