package com.clickchecker.analytics.trend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clickchecker.account.entity.Account;
import com.clickchecker.account.entity.AccountStatus;
import com.clickchecker.account.repository.AccountRepository;
import com.clickchecker.analytics.support.AnalyticsControllerIntegrationTestSupport;
import com.clickchecker.auth.service.JwtTokenProvider;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organizationmember.entity.OrganizationMember;
import com.clickchecker.organizationmember.entity.OrganizationRole;
import com.clickchecker.organizationmember.repository.OrganizationMemberRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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
class AdminTrendAnalyticsControllerPostgresIntegrationTest extends AnalyticsControllerIntegrationTestSupport {

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
    private AccountRepository accountRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void trends_returnsEventAndUniqueUserSeries_whenRequesterIsViewer() throws Exception {
        Organization organization = saveOrganization("acme");
        Account viewer = saveAccount("viewer01");
        saveMembership(viewer, organization, OrganizationRole.VIEWER);

        var userA = saveEventUser(organization, "u-1001");
        var userB = saveEventUser(organization, "u-1002");

        saveEvent(organization, userA, "click", "/landing", "2026-02-12T15:10:00Z");
        saveEvent(organization, userA, "click", "/landing", "2026-02-12T15:20:00Z");
        saveEvent(organization, userB, "click", "/landing", "2026-02-12T16:05:00Z");

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/analytics/trends", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(viewer))
                                .param("from", "2026-02-13")
                                .param("to", "2026-02-14")
                                .param("bucket", "HOUR")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.bucket").value("HOUR"))
                .andExpect(jsonPath("$.eventCounts.length()").value(24))
                .andExpect(jsonPath("$.eventCounts[0].count").value(2))
                .andExpect(jsonPath("$.eventCounts[1].count").value(1))
                .andExpect(jsonPath("$.uniqueUserCounts.length()").value(24))
                .andExpect(jsonPath("$.uniqueUserCounts[0].count").value(1))
                .andExpect(jsonPath("$.uniqueUserCounts[1].count").value(1));
    }

    private Account saveAccount(String loginId) {
        return accountRepository.save(Account.builder()
                .loginId(loginId)
                .passwordHash("hashed-password")
                .status(AccountStatus.ACTIVE)
                .build());
    }

    private OrganizationMember saveMembership(Account account, Organization organization, OrganizationRole role) {
        return organizationMemberRepository.save(OrganizationMember.builder()
                .account(account)
                .organization(organization)
                .role(role)
                .build());
    }

    private String bearerToken(Account account) {
        return "Bearer " + jwtTokenProvider.issueAccessToken(account.getId());
    }
}
