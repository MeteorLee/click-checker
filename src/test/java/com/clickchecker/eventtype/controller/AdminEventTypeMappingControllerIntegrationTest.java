package com.clickchecker.eventtype.controller;

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
import com.clickchecker.eventtype.entity.EventTypeMapping;
import com.clickchecker.eventtype.repository.EventTypeMappingRepository;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organizationmember.entity.OrganizationMember;
import com.clickchecker.organizationmember.entity.OrganizationRole;
import com.clickchecker.organizationmember.repository.OrganizationMemberRepository;
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
class AdminEventTypeMappingControllerIntegrationTest extends AnalyticsControllerIntegrationTestSupport {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private EventTypeMappingRepository eventTypeMappingRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void getAll_returnsMappings_whenRequesterIsViewer() throws Exception {
        Organization organization = saveOrganization("acme");
        Account viewer = saveAccount("viewer01");
        saveMembership(viewer, organization, OrganizationRole.VIEWER);
        eventTypeMappingRepository.save(EventTypeMapping.builder()
                .organization(organization)
                .rawEventType("button_click")
                .canonicalEventType("CLICK")
                .active(true)
                .build());

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/event-type-mappings", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(viewer))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].canonicalEventType").value("CLICK"));
    }

    @Test
    void create_update_active_delete_work_whenRequesterIsAdmin() throws Exception {
        Organization organization = saveOrganization("acme");
        Account admin = saveAccount("admin01");
        saveMembership(admin, organization, OrganizationRole.ADMIN);

        mockMvc.perform(
                        post("/api/v1/admin/organizations/{organizationId}/event-type-mappings", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "rawEventType": "page_view",
                                          "canonicalEventType": "VIEW"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rawEventType").value("page_view"))
                .andExpect(jsonPath("$.canonicalEventType").value("VIEW"));

        EventTypeMapping saved = eventTypeMappingRepository.findByOrganizationIdOrderByRawEventTypeAscIdAsc(organization.getId()).getFirst();

        mockMvc.perform(
                        put("/api/v1/admin/organizations/{organizationId}/event-type-mappings/{eventTypeMappingId}",
                                organization.getId(), saved.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "rawEventType": "page_view",
                                          "canonicalEventType": "PAGE_VIEW"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canonicalEventType").value("PAGE_VIEW"));

        mockMvc.perform(
                        put("/api/v1/admin/organizations/{organizationId}/event-type-mappings/{eventTypeMappingId}/active",
                                organization.getId(), saved.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"active\":false}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(
                        delete("/api/v1/admin/organizations/{organizationId}/event-type-mappings/{eventTypeMappingId}",
                                organization.getId(), saved.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
                )
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returnsForbidden_whenRequesterIsViewer() throws Exception {
        Organization organization = saveOrganization("acme");
        Account viewer = saveAccount("viewer02");
        saveMembership(viewer, organization, OrganizationRole.VIEWER);
        EventTypeMapping saved = eventTypeMappingRepository.save(EventTypeMapping.builder()
                .organization(organization)
                .rawEventType("button_click")
                .canonicalEventType("CLICK")
                .active(true)
                .build());

        mockMvc.perform(
                        delete("/api/v1/admin/organizations/{organizationId}/event-type-mappings/{eventTypeMappingId}",
                                organization.getId(), saved.getId())
                                .header(HttpHeaders.AUTHORIZATION, bearerToken(viewer))
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
