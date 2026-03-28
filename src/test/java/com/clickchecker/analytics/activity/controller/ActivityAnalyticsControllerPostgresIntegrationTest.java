package com.clickchecker.analytics.activity.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clickchecker.analytics.support.AnalyticsControllerIntegrationTestSupport;
import com.clickchecker.eventuser.entity.EventUser;
import com.clickchecker.organization.entity.Organization;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Tag("postgres")
@ActiveProfiles("ci")
@SpringBootTest
@AutoConfigureMockMvc
class ActivityAnalyticsControllerPostgresIntegrationTest extends AnalyticsControllerIntegrationTestSupport {

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

    @Test
    void activity_returnsDistributionBreakdowns_forApiKeyClients() throws Exception {
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);
        EventUser firstUser = saveEventUser(organization, "user-1");
        EventUser secondUser = saveEventUser(organization, "user-2");

        saveEvent(organization, firstUser, "view", "/landing", "2026-03-11T00:10:00Z");
        saveEvent(organization, secondUser, "click", "/signup", "2026-03-11T02:20:00Z");
        saveEvent(organization, firstUser, "click", "/pricing", "2026-03-12T05:00:00Z");

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/activity")
                                .param("from", "2026-03-11T00:00:00Z")
                                .param("to", "2026-03-13T00:00:00Z")
                                .param("timezone", "Asia/Seoul")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.totalEvents").value(3))
                .andExpect(jsonPath("$.activeDays").value(2))
                .andExpect(jsonPath("$.weekdaySummary.eventCount").value(3))
                .andExpect(jsonPath("$.weekdaySummary.uniqueUserCount").value(2))
                .andExpect(jsonPath("$.weekendSummary.eventCount").value(0))
                .andExpect(jsonPath("$.dayOfWeekDistribution.length()").value(7))
                .andExpect(jsonPath("$.dailyActivity.length()").value(2))
                .andExpect(jsonPath("$.hourlyDistribution.length()").value(24))
                .andExpect(jsonPath("$.weekdayHourlyDistribution.length()").value(24))
                .andExpect(jsonPath("$.weekendHourlyDistribution.length()").value(24))
                .andExpect(jsonPath("$.hourlyDistribution[9].eventCount").value(1))
                .andExpect(jsonPath("$.hourlyDistribution[11].eventCount").value(1))
                .andExpect(jsonPath("$.hourlyDistribution[14].eventCount").value(1))
                .andExpect(jsonPath("$.peakDayEventCount").value(2));
    }
}
