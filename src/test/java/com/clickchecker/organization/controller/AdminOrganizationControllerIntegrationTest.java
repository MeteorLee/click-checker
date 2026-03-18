package com.clickchecker.organization.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clickchecker.account.entity.Account;
import com.clickchecker.account.entity.AccountStatus;
import com.clickchecker.account.repository.AccountRepository;
import com.clickchecker.auth.repository.RefreshTokenRepository;
import com.clickchecker.auth.service.JwtTokenProvider;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.eventtype.repository.EventTypeMappingRepository;
import com.clickchecker.eventuser.repository.EventUserRepository;
import com.clickchecker.organization.entity.ApiKeyStatus;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.organizationmember.entity.OrganizationMember;
import com.clickchecker.organizationmember.entity.OrganizationRole;
import com.clickchecker.organizationmember.repository.OrganizationMemberRepository;
import com.clickchecker.route.repository.RouteTemplateRepository;
import com.clickchecker.web.filter.ApiKeyAuthFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("ci")
@SpringBootTest
@AutoConfigureMockMvc
class AdminOrganizationControllerIntegrationTest {

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

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventTypeMappingRepository eventTypeMappingRepository;

    @Autowired
    private EventUserRepository eventUserRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RouteTemplateRepository routeTemplateRepository;

    @Test
    void create_returnsCreated_andAddsOwnerMembership() throws Exception {
        cleanup();
        Account account = saveAccount("alice");

        mockMvc.perform(
                        post("/api/v1/admin/organizations")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(account.getId()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Acme"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").isNumber())
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.ownerMembershipId").isNumber())
                .andExpect(jsonPath("$.apiKey").isString())
                .andExpect(jsonPath("$.apiKeyPrefix").isString());

        assertThat(organizationRepository.count()).isEqualTo(1);
        var savedOrganization = organizationRepository.findAll().getFirst();
        Long organizationId = savedOrganization.getId();
        OrganizationMember membership = organizationMemberRepository
                .findByAccountIdAndOrganizationId(account.getId(), organizationId)
                .orElseThrow();
        assertThat(membership.getRole()).isEqualTo(OrganizationRole.OWNER);
        assertThat(savedOrganization.getName()).isEqualTo("Acme");
        assertThat(savedOrganization.getApiKeyKid()).isNotBlank();
        assertThat(savedOrganization.getApiKeyHash()).hasSize(64);
        assertThat(savedOrganization.getApiKeyPrefix()).isNotBlank();
        assertThat(savedOrganization.getApiKeyStatus()).isEqualTo(ApiKeyStatus.ACTIVE);
        assertThat(savedOrganization.getApiKeyCreatedAt()).isNotNull();
    }

    @Test
    void create_returnsUnauthorized_whenAccessTokenIsMissing() throws Exception {
        cleanup();

        mockMvc.perform(
                        post("/api/v1/admin/organizations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Acme"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_returnsBadRequest_whenNameIsBlank() throws Exception {
        cleanup();
        Account account = saveAccount("alice");

        mockMvc.perform(
                        post("/api/v1/admin/organizations")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(account.getId()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": ""
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void getApiKeyMetadata_returnsMetadata_whenRequesterIsOwner() throws Exception {
        cleanup();
        Account owner = saveAccount("alice");
        Organization organization = saveOrganization("Acme");
        saveMembership(owner, organization, OrganizationRole.OWNER);

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/v1/admin/organizations/{organizationId}/api-key", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(owner.getId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kid").value("kid-1234567890"))
                .andExpect(jsonPath("$.apiKeyPrefix").value("kid-1234"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.apiKey").doesNotExist());
    }

    @Test
    void getApiKeyMetadata_returnsForbidden_whenRequesterIsViewer() throws Exception {
        cleanup();
        Account viewer = saveAccount("viewer");
        Organization organization = saveOrganization("Acme");
        saveMembership(viewer, organization, OrganizationRole.VIEWER);

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/v1/admin/organizations/{organizationId}/api-key", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(viewer.getId()))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void rotateApiKey_returnsNewKey_whenRequesterIsOwner() throws Exception {
        cleanup();
        Account owner = saveAccount("owner");
        Organization organization = saveOrganization("Acme");
        saveMembership(owner, organization, OrganizationRole.OWNER);

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/admin/organizations/{organizationId}/api-key/rotate", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(owner.getId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").isString())
                .andExpect(jsonPath("$.apiKeyPrefix").isString())
                .andExpect(jsonPath("$.rotatedAt").isString());

        Organization rotated = organizationRepository.findById(organization.getId()).orElseThrow();
        assertThat(rotated.getApiKeyKid()).isNotBlank();
        assertThat(rotated.getApiKeyHash()).hasSize(64);
        assertThat(rotated.getApiKeyPrefix()).isNotBlank();
        assertThat(rotated.getApiKeyPrefix()).isNotEqualTo("kid-1234");
        assertThat(rotated.getApiKeyRotatedAt()).isNotNull();
    }

    @Test
    void rotateApiKey_returnsForbidden_whenRequesterIsAdmin() throws Exception {
        cleanup();
        Account admin = saveAccount("admin");
        Organization organization = saveOrganization("Acme");
        saveMembership(admin, organization, OrganizationRole.ADMIN);

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/admin/organizations/{organizationId}/api-key/rotate", organization.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(admin.getId()))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void rotateApiKey_invalidatesOldKey_andAllowsNewKeyForEventIngest() throws Exception {
        cleanup();
        Account owner = saveAccount("owner");
        String accessToken = jwtTokenProvider.issueAccessToken(owner.getId());

        String createResponse = mockMvc.perform(
                        post("/api/v1/admin/organizations")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Acme"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = objectMapper.readTree(createResponse);
        Long organizationId = created.get("organizationId").asLong();
        String oldApiKey = created.get("apiKey").asText();

        String rotateResponse = mockMvc.perform(
                        post("/api/v1/admin/organizations/{organizationId}/api-key/rotate", organizationId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode rotated = objectMapper.readTree(rotateResponse);
        String newApiKey = rotated.get("apiKey").asText();

        mockMvc.perform(
                        post("/api/events")
                                .header(ApiKeyAuthFilter.API_KEY_HEADER, oldApiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "eventType": "click",
                                          "path": "/pricing",
                                          "occurredAt": "2026-03-18T10:00:00Z"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        post("/api/events")
                                .header(ApiKeyAuthFilter.API_KEY_HEADER, newApiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "eventType": "click",
                                          "path": "/pricing",
                                          "occurredAt": "2026-03-18T10:00:00Z"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber());

        assertThat(eventRepository.count()).isEqualTo(1);
    }

    private Account saveAccount(String loginId) {
        return accountRepository.save(Account.builder()
                .loginId(loginId)
                .passwordHash(passwordEncoder.encode("secret123!"))
                .status(AccountStatus.ACTIVE)
                .build());
    }

    private Organization saveOrganization(String name) {
        return organizationRepository.save(Organization.builder()
                .name(name)
                .apiKeyKid("kid-1234567890")
                .apiKeyHash("a".repeat(64))
                .apiKeyPrefix("kid-1234")
                .apiKeyStatus(ApiKeyStatus.ACTIVE)
                .apiKeyCreatedAt(Instant.parse("2026-03-18T10:00:00Z"))
                .apiKeyRotatedAt(Instant.parse("2026-03-18T10:00:00Z"))
                .build());
    }

    private OrganizationMember saveMembership(Account account, Organization organization, OrganizationRole role) {
        return organizationMemberRepository.save(OrganizationMember.builder()
                .account(account)
                .organization(organization)
                .role(role)
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
