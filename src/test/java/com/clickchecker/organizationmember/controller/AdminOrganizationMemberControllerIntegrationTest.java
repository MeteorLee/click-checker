package com.clickchecker.organizationmember.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clickchecker.account.entity.Account;
import com.clickchecker.account.entity.AccountStatus;
import com.clickchecker.account.repository.AccountRepository;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.eventtype.repository.EventTypeMappingRepository;
import com.clickchecker.eventuser.repository.EventUserRepository;
import com.clickchecker.auth.service.JwtTokenProvider;
import com.clickchecker.auth.repository.RefreshTokenRepository;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.organizationmember.entity.OrganizationMember;
import com.clickchecker.organizationmember.entity.OrganizationRole;
import com.clickchecker.organizationmember.repository.OrganizationMemberRepository;
import com.clickchecker.route.repository.RouteTemplateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("ci")
@SpringBootTest
@AutoConfigureMockMvc
class AdminOrganizationMemberControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventTypeMappingRepository eventTypeMappingRepository;

    @Autowired
    private EventUserRepository eventUserRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private RouteTemplateRepository routeTemplateRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void getMembers_returnsMembers_whenRequesterIsOwner() throws Exception {
        cleanup();
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Acme")
                .build());
        Account owner = saveAccount("owner", AccountStatus.ACTIVE);
        Account alice = saveAccount("alice", AccountStatus.ACTIVE);
        Account charlie = saveAccount("charlie", AccountStatus.DISABLED);

        organizationMemberRepository.save(OrganizationMember.builder()
                .account(owner)
                .organization(organization)
                .role(OrganizationRole.OWNER)
                .build());
        OrganizationMember aliceMembership = organizationMemberRepository.save(OrganizationMember.builder()
                .account(alice)
                .organization(organization)
                .role(OrganizationRole.ADMIN)
                .build());
        OrganizationMember charlieMembership = organizationMemberRepository.save(OrganizationMember.builder()
                .account(charlie)
                .organization(organization)
                .role(OrganizationRole.VIEWER)
                .build());

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/members", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(owner.getId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].loginId").value("alice"))
                .andExpect(jsonPath("$.members[0].memberId").value(aliceMembership.getId()))
                .andExpect(jsonPath("$.members[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.members[1].loginId").value("charlie"))
                .andExpect(jsonPath("$.members[1].memberId").value(charlieMembership.getId()))
                .andExpect(jsonPath("$.members[1].accountStatus").value("DISABLED"))
                .andExpect(jsonPath("$.members[2].loginId").value("owner"))
                .andExpect(jsonPath("$.members[2].role").value("OWNER"));
    }

    @Test
    void addMember_returnsCreated_whenRequesterIsOwner() throws Exception {
        cleanup();
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Acme")
                .build());
        Account owner = saveAccount("owner", AccountStatus.ACTIVE);
        Account member = saveAccount("member", AccountStatus.ACTIVE);

        organizationMemberRepository.save(OrganizationMember.builder()
                .account(owner)
                .organization(organization)
                .role(OrganizationRole.OWNER)
                .build());

        mockMvc.perform(
                        post("/api/v1/admin/organizations/{organizationId}/members", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(owner.getId()))
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "accountId": %d,
                                          "role": "VIEWER"
                                        }
                                        """.formatted(member.getId()))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(member.getId()))
                .andExpect(jsonPath("$.loginId").value("member"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.role").value("VIEWER"));

        OrganizationMember membership = organizationMemberRepository
                .findByAccountIdAndOrganizationId(member.getId(), organization.getId())
                .orElseThrow();
        assert membership.getRole() == OrganizationRole.VIEWER;
    }

    @Test
    void addMember_returnsForbidden_whenRequesterIsAdmin() throws Exception {
        cleanup();
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Acme")
                .build());
        Account admin = saveAccount("admin", AccountStatus.ACTIVE);
        Account member = saveAccount("member", AccountStatus.ACTIVE);

        organizationMemberRepository.save(OrganizationMember.builder()
                .account(admin)
                .organization(organization)
                .role(OrganizationRole.ADMIN)
                .build());

        mockMvc.perform(
                        post("/api/v1/admin/organizations/{organizationId}/members", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(admin.getId()))
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "accountId": %d,
                                          "role": "VIEWER"
                                        }
                                        """.formatted(member.getId()))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void addMember_returnsConflict_whenMembershipAlreadyExists() throws Exception {
        cleanup();
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Acme")
                .build());
        Account owner = saveAccount("owner", AccountStatus.ACTIVE);
        Account member = saveAccount("member", AccountStatus.ACTIVE);

        organizationMemberRepository.save(OrganizationMember.builder()
                .account(owner)
                .organization(organization)
                .role(OrganizationRole.OWNER)
                .build());
        organizationMemberRepository.save(OrganizationMember.builder()
                .account(member)
                .organization(organization)
                .role(OrganizationRole.VIEWER)
                .build());

        mockMvc.perform(
                        post("/api/v1/admin/organizations/{organizationId}/members", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(owner.getId()))
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "accountId": %d,
                                          "role": "ADMIN"
                                        }
                                        """.formatted(member.getId()))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Organization member already exists."));
    }

    @Test
    void addMember_returnsNotFound_whenTargetAccountDoesNotExist() throws Exception {
        cleanup();
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Acme")
                .build());
        Account owner = saveAccount("owner", AccountStatus.ACTIVE);

        organizationMemberRepository.save(OrganizationMember.builder()
                .account(owner)
                .organization(organization)
                .role(OrganizationRole.OWNER)
                .build());

        mockMvc.perform(
                        post("/api/v1/admin/organizations/{organizationId}/members", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(owner.getId()))
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "accountId": 9999,
                                          "role": "VIEWER"
                                        }
                                        """)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found."));
    }

    @Test
    void updateRole_returnsOk_whenRequesterIsOwner() throws Exception {
        cleanup();
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Acme")
                .build());
        Account owner = saveAccount("owner", AccountStatus.ACTIVE);
        Account member = saveAccount("member", AccountStatus.ACTIVE);

        organizationMemberRepository.save(OrganizationMember.builder()
                .account(owner)
                .organization(organization)
                .role(OrganizationRole.OWNER)
                .build());
        OrganizationMember membership = organizationMemberRepository.save(OrganizationMember.builder()
                .account(member)
                .organization(organization)
                .role(OrganizationRole.VIEWER)
                .build());

        mockMvc.perform(
                        put("/api/v1/admin/organizations/{organizationId}/members/{memberId}/role", organization.getId(), membership.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(owner.getId()))
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "role": "ADMIN"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(membership.getId()))
                .andExpect(jsonPath("$.accountId").value(member.getId()))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        OrganizationMember updatedMembership = organizationMemberRepository.findById(membership.getId())
                .orElseThrow();
        assert updatedMembership.getRole() == OrganizationRole.ADMIN;
    }

    @Test
    void updateRole_returnsForbidden_whenRequesterIsAdmin() throws Exception {
        cleanup();
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Acme")
                .build());
        Account owner = saveAccount("owner", AccountStatus.ACTIVE);
        Account admin = saveAccount("admin", AccountStatus.ACTIVE);
        Account member = saveAccount("member", AccountStatus.ACTIVE);

        organizationMemberRepository.save(OrganizationMember.builder()
                .account(owner)
                .organization(organization)
                .role(OrganizationRole.OWNER)
                .build());
        organizationMemberRepository.save(OrganizationMember.builder()
                .account(admin)
                .organization(organization)
                .role(OrganizationRole.ADMIN)
                .build());
        OrganizationMember membership = organizationMemberRepository.save(OrganizationMember.builder()
                .account(member)
                .organization(organization)
                .role(OrganizationRole.VIEWER)
                .build());

        mockMvc.perform(
                        put("/api/v1/admin/organizations/{organizationId}/members/{memberId}/role", organization.getId(), membership.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(admin.getId()))
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "role": "ADMIN"
                                        }
                                        """)
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void updateRole_returnsConflict_whenDemotingLastOwner() throws Exception {
        cleanup();
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Acme")
                .build());
        Account owner = saveAccount("owner", AccountStatus.ACTIVE);

        OrganizationMember ownerMembership = organizationMemberRepository.save(OrganizationMember.builder()
                .account(owner)
                .organization(organization)
                .role(OrganizationRole.OWNER)
                .build());

        mockMvc.perform(
                        put("/api/v1/admin/organizations/{organizationId}/members/{memberId}/role", organization.getId(), ownerMembership.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(owner.getId()))
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "role": "ADMIN"
                                        }
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Last owner cannot be demoted."));
    }

    @Test
    void updateRole_returnsNotFound_whenMemberDoesNotExistInOrganization() throws Exception {
        cleanup();
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Acme")
                .build());
        Organization anotherOrganization = organizationRepository.save(Organization.builder()
                .name("Beta")
                .build());
        Account owner = saveAccount("owner", AccountStatus.ACTIVE);
        Account member = saveAccount("member", AccountStatus.ACTIVE);

        organizationMemberRepository.save(OrganizationMember.builder()
                .account(owner)
                .organization(organization)
                .role(OrganizationRole.OWNER)
                .build());
        OrganizationMember anotherOrgMembership = organizationMemberRepository.save(OrganizationMember.builder()
                .account(member)
                .organization(anotherOrganization)
                .role(OrganizationRole.VIEWER)
                .build());

        mockMvc.perform(
                        put("/api/v1/admin/organizations/{organizationId}/members/{memberId}/role", organization.getId(), anotherOrgMembership.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(owner.getId()))
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "role": "ADMIN"
                                        }
                                        """)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Organization member not found."));
    }

    @Test
    void removeMember_returnsNoContent_whenRequesterIsOwner() throws Exception {
        cleanup();
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Acme")
                .build());
        Account owner = saveAccount("owner", AccountStatus.ACTIVE);
        Account member = saveAccount("member", AccountStatus.ACTIVE);

        organizationMemberRepository.save(OrganizationMember.builder()
                .account(owner)
                .organization(organization)
                .role(OrganizationRole.OWNER)
                .build());
        OrganizationMember membership = organizationMemberRepository.save(OrganizationMember.builder()
                .account(member)
                .organization(organization)
                .role(OrganizationRole.VIEWER)
                .build());

        mockMvc.perform(
                        delete("/api/v1/admin/organizations/{organizationId}/members/{memberId}", organization.getId(), membership.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(owner.getId()))
                )
                .andExpect(status().isNoContent());

        assert organizationMemberRepository.findById(membership.getId()).isEmpty();
    }

    @Test
    void removeMember_returnsForbidden_whenRequesterIsAdmin() throws Exception {
        cleanup();
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Acme")
                .build());
        Account owner = saveAccount("owner", AccountStatus.ACTIVE);
        Account admin = saveAccount("admin", AccountStatus.ACTIVE);
        Account member = saveAccount("member", AccountStatus.ACTIVE);

        organizationMemberRepository.save(OrganizationMember.builder()
                .account(owner)
                .organization(organization)
                .role(OrganizationRole.OWNER)
                .build());
        organizationMemberRepository.save(OrganizationMember.builder()
                .account(admin)
                .organization(organization)
                .role(OrganizationRole.ADMIN)
                .build());
        OrganizationMember membership = organizationMemberRepository.save(OrganizationMember.builder()
                .account(member)
                .organization(organization)
                .role(OrganizationRole.VIEWER)
                .build());

        mockMvc.perform(
                        delete("/api/v1/admin/organizations/{organizationId}/members/{memberId}", organization.getId(), membership.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(admin.getId()))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void removeMember_returnsConflict_whenRemovingLastOwner() throws Exception {
        cleanup();
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Acme")
                .build());
        Account owner = saveAccount("owner", AccountStatus.ACTIVE);

        OrganizationMember ownerMembership = organizationMemberRepository.save(OrganizationMember.builder()
                .account(owner)
                .organization(organization)
                .role(OrganizationRole.OWNER)
                .build());

        mockMvc.perform(
                        delete("/api/v1/admin/organizations/{organizationId}/members/{memberId}", organization.getId(), ownerMembership.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(owner.getId()))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Last owner cannot be removed."));
    }

    @Test
    void removeMember_returnsNotFound_whenMemberDoesNotExistInOrganization() throws Exception {
        cleanup();
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Acme")
                .build());
        Organization anotherOrganization = organizationRepository.save(Organization.builder()
                .name("Beta")
                .build());
        Account owner = saveAccount("owner", AccountStatus.ACTIVE);
        Account member = saveAccount("member", AccountStatus.ACTIVE);

        organizationMemberRepository.save(OrganizationMember.builder()
                .account(owner)
                .organization(organization)
                .role(OrganizationRole.OWNER)
                .build());
        OrganizationMember anotherOrgMembership = organizationMemberRepository.save(OrganizationMember.builder()
                .account(member)
                .organization(anotherOrganization)
                .role(OrganizationRole.VIEWER)
                .build());

        mockMvc.perform(
                        delete("/api/v1/admin/organizations/{organizationId}/members/{memberId}", organization.getId(), anotherOrgMembership.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(owner.getId()))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Organization member not found."));
    }

    @Test
    void getMembers_returnsMembers_whenRequesterIsAdmin() throws Exception {
        cleanup();
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Acme")
                .build());
        Account admin = saveAccount("admin", AccountStatus.ACTIVE);
        Account alice = saveAccount("alice", AccountStatus.ACTIVE);

        organizationMemberRepository.save(OrganizationMember.builder()
                .account(admin)
                .organization(organization)
                .role(OrganizationRole.ADMIN)
                .build());
        OrganizationMember aliceMembership = organizationMemberRepository.save(OrganizationMember.builder()
                .account(alice)
                .organization(organization)
                .role(OrganizationRole.VIEWER)
                .build());

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/members", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(admin.getId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].loginId").value("admin"))
                .andExpect(jsonPath("$.members[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.members[1].loginId").value("alice"))
                .andExpect(jsonPath("$.members[1].memberId").value(aliceMembership.getId()))
                .andExpect(jsonPath("$.members[1].role").value("VIEWER"));
    }

    @Test
    void getMembers_returnsForbidden_whenRequesterHasNoMembership() throws Exception {
        cleanup();
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Acme")
                .build());
        Account outsider = saveAccount("outsider", AccountStatus.ACTIVE);

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/members", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(outsider.getId()))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void getMembers_returnsForbidden_whenRequesterIsViewer() throws Exception {
        cleanup();
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Acme")
                .build());
        Account viewer = saveAccount("viewer", AccountStatus.ACTIVE);

        organizationMemberRepository.save(OrganizationMember.builder()
                .account(viewer)
                .organization(organization)
                .role(OrganizationRole.VIEWER)
                .build());

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/members", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(viewer.getId()))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void getMembers_returnsNotFound_whenOrganizationDoesNotExist() throws Exception {
        cleanup();
        Account owner = saveAccount("owner", AccountStatus.ACTIVE);

        mockMvc.perform(
                        get("/api/v1/admin/organizations/{organizationId}/members", 9999L)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(owner.getId()))
                )
                .andExpect(status().isNotFound());
    }

    private Account saveAccount(String loginId, AccountStatus status) {
        return accountRepository.save(Account.builder()
                .loginId(loginId)
                .passwordHash(passwordEncoder.encode("secret123!"))
                .status(status)
                .build());
    }

    private void cleanup() {
        eventRepository.deleteAll();
        eventTypeMappingRepository.deleteAll();
        routeTemplateRepository.deleteAll();
        eventUserRepository.deleteAll();
        organizationMemberRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        accountRepository.deleteAll();
        organizationRepository.deleteAll();
    }
}
