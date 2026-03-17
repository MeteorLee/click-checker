package com.clickchecker.auth.controller;

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
class AdminAccountControllerIntegrationTest {

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
    void me_returnsAccountInfo_whenAccessTokenIsValid() throws Exception {
        cleanup();
        Account account = accountRepository.save(Account.builder()
                .loginId("alice")
                .passwordHash(passwordEncoder.encode("secret123!"))
                .status(AccountStatus.ACTIVE)
                .build());
        Organization acmeOne = organizationRepository.save(Organization.builder()
                .name("Acme")
                .build());
        Organization acmeTwo = organizationRepository.save(Organization.builder()
                .name("Acme")
                .build());
        Organization beta = organizationRepository.save(Organization.builder()
                .name("Beta")
                .build());
        Organization zebra = organizationRepository.save(Organization.builder()
                .name("Zebra")
                .build());
        organizationMemberRepository.save(OrganizationMember.builder()
                .account(account)
                .organization(zebra)
                .role(OrganizationRole.VIEWER)
                .build());
        OrganizationMember firstAcmeMembership = organizationMemberRepository.save(OrganizationMember.builder()
                .account(account)
                .organization(acmeOne)
                .role(OrganizationRole.OWNER)
                .build());
        OrganizationMember secondAcmeMembership = organizationMemberRepository.save(OrganizationMember.builder()
                .account(account)
                .organization(acmeTwo)
                .role(OrganizationRole.ADMIN)
                .build());
        organizationMemberRepository.save(OrganizationMember.builder()
                .account(account)
                .organization(beta)
                .role(OrganizationRole.ADMIN)
                .build());

        mockMvc.perform(
                        get("/api/v1/admin/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(account.getId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.getId()))
                .andExpect(jsonPath("$.loginId").value("alice"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.memberships[0].organizationName").value("Acme"))
                .andExpect(jsonPath("$.memberships[0].membershipId").value(firstAcmeMembership.getId()))
                .andExpect(jsonPath("$.memberships[1].organizationName").value("Acme"))
                .andExpect(jsonPath("$.memberships[1].membershipId").value(secondAcmeMembership.getId()))
                .andExpect(jsonPath("$.memberships[2].organizationName").value("Beta"))
                .andExpect(jsonPath("$.memberships[2].role").value("ADMIN"))
                .andExpect(jsonPath("$.memberships[3].organizationName").value("Zebra"))
                .andExpect(jsonPath("$.memberships[3].role").value("VIEWER"));
    }

    @Test
    void me_returnsUnauthorized_whenAuthorizationHeaderIsMissing() throws Exception {
        cleanup();

        mockMvc.perform(get("/api/v1/admin/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_returnsUnauthorized_whenAccessTokenIsInvalid() throws Exception {
        cleanup();

        mockMvc.perform(
                        get("/api/v1/admin/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_returnsForbidden_whenAccountIsDisabled() throws Exception {
        cleanup();
        Account account = accountRepository.save(Account.builder()
                .loginId("alice")
                .passwordHash(passwordEncoder.encode("secret123!"))
                .status(AccountStatus.DISABLED)
                .build());

        mockMvc.perform(
                        get("/api/v1/admin/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(account.getId()))
                )
                .andExpect(status().isForbidden());
    }

    private void cleanup() {
        organizationMemberRepository.deleteAll();
        accountRepository.deleteAll();
        organizationRepository.deleteAll();
    }
}
