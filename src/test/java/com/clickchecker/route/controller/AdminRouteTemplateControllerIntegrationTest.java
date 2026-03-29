package com.clickchecker.route.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.clickchecker.route.entity.RouteTemplate;
import com.clickchecker.route.repository.RouteTemplateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("ci")
@SpringBootTest
@AutoConfigureMockMvc
class AdminRouteTemplateControllerIntegrationTest extends AnalyticsControllerIntegrationTestSupport {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private RouteTemplateRepository routeTemplateRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void getAll_returnsTemplates_whenRequesterIsViewer() throws Exception {
        Organization organization = saveOrganization("acme");
        Account viewer = saveAccount("viewer01");
        saveMembership(viewer, organization, OrganizationRole.VIEWER);
        routeTemplateRepository.save(RouteTemplate.builder()
                .organization(organization)
                .template("/posts/{id}")
                .routeKey("/posts/{id}")
                .priority(100)
                .active(true)
                .build());

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/route-templates", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(viewer))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].routeKey").value("/posts/{id}"));
    }

    @Test
    void create_update_active_delete_work_whenRequesterIsAdmin() throws Exception {
        Organization organization = saveOrganization("acme");
        Account admin = saveAccount("admin01");
        saveMembership(admin, organization, OrganizationRole.ADMIN);

        String createBody = """
                {
                  "template": "/pricing",
                  "routeKey": "/pricing",
                  "priority": 50
                }
                """;

        mockMvc.perform(
                        post("/api/v1/admin/organizations/{organizationId}/route-templates", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.template").value("/pricing"))
                .andExpect(jsonPath("$.active").value(true));

        RouteTemplate saved = routeTemplateRepository.findByOrganizationIdOrderByTemplateAscIdAsc(organization.getId()).getFirst();

        String updateBody = """
                {
                  "template": "/pricing-v2",
                  "routeKey": "/pricing",
                  "priority": 60
                }
                """;

        mockMvc.perform(
                        put("/api/v1/admin/organizations/{organizationId}/route-templates/{routeTemplateId}",
                                organization.getId(), saved.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.template").value("/pricing-v2"))
                .andExpect(jsonPath("$.priority").value(60));

        mockMvc.perform(
                        put("/api/v1/admin/organizations/{organizationId}/route-templates/{routeTemplateId}/active",
                                organization.getId(), saved.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"active\":false}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(
                        delete("/api/v1/admin/organizations/{organizationId}/route-templates/{routeTemplateId}",
                                organization.getId(), saved.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                )
                .andExpect(status().isNoContent());
    }

    @Test
    void create_returnsForbidden_whenRequesterIsViewer() throws Exception {
        Organization organization = saveOrganization("acme");
        Account viewer = saveAccount("viewer02");
        saveMembership(viewer, organization, OrganizationRole.VIEWER);

        mockMvc.perform(
                        post("/api/v1/admin/organizations/{organizationId}/route-templates", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(viewer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "template": "/docs",
                                          "routeKey": "/docs",
                                          "priority": 10
                                        }
                                        """)
                )
                .andExpect(status().isForbidden());
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
