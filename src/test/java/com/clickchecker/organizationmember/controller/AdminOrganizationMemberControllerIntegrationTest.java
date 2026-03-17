package com.clickchecker.organizationmember.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clickchecker.account.entity.Account;
import com.clickchecker.account.entity.AccountStatus;
import com.clickchecker.account.repository.AccountRepository;
import com.clickchecker.auth.service.JwtTokenProvider;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.organizationmember.entity.OrganizationMember;
import com.clickchecker.organizationmember.entity.OrganizationRole;
import com.clickchecker.organizationmember.repository.OrganizationMemberRepository;
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
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

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
        organizationMemberRepository.deleteAll();
        accountRepository.deleteAll();
        organizationRepository.deleteAll();
    }
}
