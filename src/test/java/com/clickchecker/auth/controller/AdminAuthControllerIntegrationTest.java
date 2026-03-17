package com.clickchecker.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clickchecker.account.entity.Account;
import com.clickchecker.account.entity.AccountStatus;
import com.clickchecker.account.repository.AccountRepository;
import com.clickchecker.auth.entity.RefreshToken;
import com.clickchecker.auth.repository.RefreshTokenRepository;
import com.clickchecker.auth.service.JwtTokenProvider;
import com.clickchecker.auth.service.RefreshTokenIssuer;
import com.clickchecker.organizationmember.repository.OrganizationMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("ci")
@SpringBootTest
@AutoConfigureMockMvc
class AdminAuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RefreshTokenIssuer refreshTokenIssuer;

    @Test
    void signup_returnsTokensAndStoresAccount_whenRequestIsValid() throws Exception {
        cleanup();

        String responseBody = mockMvc.perform(
                        post("/api/v1/admin/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "loginId": "alice",
                                          "password": "secret123!"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").isNumber())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String accessToken = extractField(responseBody, "accessToken");
        String refreshToken = extractField(responseBody, "refreshToken");
        Account savedAccount = accountRepository.findByLoginId("alice").orElseThrow();

        assertThat(jwtTokenProvider.isValidAccessToken(accessToken)).isTrue();
        assertThat(jwtTokenProvider.extractAccountId(accessToken)).isEqualTo(savedAccount.getId());
        assertThat(savedAccount.getPasswordHash()).isNotEqualTo("secret123!");
        assertThat(refreshTokenRepository.findByTokenHash(refreshTokenIssuer.hash(refreshToken))).isPresent();
    }

    @Test
    void signup_normalizesLoginIdToLowercase_beforeSaving() throws Exception {
        cleanup();

        mockMvc.perform(
                        post("/api/v1/admin/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "loginId": "Alice_01",
                                          "password": "secret123!"
                                        }
                                        """)
                )
                .andExpect(status().isCreated());

        assertThat(accountRepository.findByLoginId("alice_01")).isPresent();
    }

    @Test
    void signup_returnsConflict_whenLoginIdAlreadyExists() throws Exception {
        cleanup();
        accountRepository.save(Account.builder()
                .loginId("alice")
                .passwordHash(passwordEncoder.encode("secret123!"))
                .status(AccountStatus.ACTIVE)
                .build());

        mockMvc.perform(
                        post("/api/v1/admin/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "loginId": "alice",
                                          "password": "secret123!"
                                        }
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Duplicated loginId."));
    }

    @Test
    void signup_returnsBadRequest_whenLoginIdFormatIsInvalid() throws Exception {
        cleanup();

        mockMvc.perform(
                        post("/api/v1/admin/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "loginId": "ab",
                                          "password": "secret123!"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void signup_returnsBadRequest_whenPasswordFormatIsInvalid() throws Exception {
        cleanup();

        mockMvc.perform(
                        post("/api/v1/admin/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "loginId": "alice",
                                          "password": "abcdefgh"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void login_returnsTokensAndStoresRefreshToken_whenCredentialsAreValid() throws Exception {
        cleanup();
        Account account = accountRepository.save(Account.builder()
                .loginId("alice")
                .passwordHash(passwordEncoder.encode("secret123!"))
                .status(AccountStatus.ACTIVE)
                .build());

        String responseBody = mockMvc.perform(
                        post("/api/v1/admin/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "loginId": "alice",
                                          "password": "secret123!"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.getId()))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.accessTokenExpiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.refreshTokenExpiresIn").value(1209600))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String accessToken = extractField(responseBody, "accessToken");
        String refreshToken = extractField(responseBody, "refreshToken");
        assertThat(jwtTokenProvider.isValidAccessToken(accessToken)).isTrue();
        assertThat(jwtTokenProvider.extractAccountId(accessToken)).isEqualTo(account.getId());
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash(refreshTokenIssuer.hash(refreshToken))).isPresent();
    }

    @Test
    void refresh_returnsNewTokensAndRevokesPreviousRefreshToken_whenRefreshTokenIsValid() throws Exception {
        cleanup();
        accountRepository.save(Account.builder()
                .loginId("alice")
                .passwordHash(passwordEncoder.encode("secret123!"))
                .status(AccountStatus.ACTIVE)
                .build());

        String loginResponse = mockMvc.perform(
                        post("/api/v1/admin/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "loginId": "alice",
                                          "password": "secret123!"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String oldRefreshToken = extractField(loginResponse, "refreshToken");
        String refreshResponse = mockMvc.perform(
                        post("/api/v1/admin/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "refreshToken": "%s"
                                        }
                                        """.formatted(oldRefreshToken))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.refreshTokenExpiresIn").value(1209600))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String newAccessToken = extractField(refreshResponse, "accessToken");
        String newRefreshToken = extractField(refreshResponse, "refreshToken");
        assertThat(jwtTokenProvider.isValidAccessToken(newAccessToken)).isTrue();
        assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);
        assertThat(refreshTokenRepository.count()).isEqualTo(2);
        RefreshToken oldSavedRefreshToken = refreshTokenRepository
                .findByTokenHash(refreshTokenIssuer.hash(oldRefreshToken))
                .orElseThrow();
        assertThat(oldSavedRefreshToken.isRevoked()).isTrue();
        assertThat(oldSavedRefreshToken.getLastUsedAt()).isNotNull();
        assertThat(refreshTokenRepository.findByTokenHash(refreshTokenIssuer.hash(newRefreshToken))).isPresent();
    }

    @Test
    void refresh_returnsUnauthorized_whenRefreshTokenIsInvalid() throws Exception {
        cleanup();

        mockMvc.perform(
                        post("/api/v1/admin/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "refreshToken": "rt_invalid_token"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid refresh token."));
    }

    @Test
    void refresh_returnsBadRequest_whenRefreshTokenIsBlank() throws Exception {
        cleanup();

        mockMvc.perform(
                        post("/api/v1/admin/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "refreshToken": ""
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void logout_returnsNoContent_andRevokesRefreshToken_whenRefreshTokenIsValid() throws Exception {
        cleanup();
        accountRepository.save(Account.builder()
                .loginId("alice")
                .passwordHash(passwordEncoder.encode("secret123!"))
                .status(AccountStatus.ACTIVE)
                .build());

        String loginResponse = mockMvc.perform(
                        post("/api/v1/admin/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "loginId": "alice",
                                          "password": "secret123!"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String refreshToken = extractField(loginResponse, "refreshToken");

        mockMvc.perform(
                        post("/api/v1/admin/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "refreshToken": "%s"
                                        }
                                        """.formatted(refreshToken))
                )
                .andExpect(status().isNoContent());

        RefreshToken savedRefreshToken = refreshTokenRepository.findByTokenHash(refreshTokenIssuer.hash(refreshToken))
                .orElseThrow();
        assertThat(savedRefreshToken.isRevoked()).isTrue();
        assertThat(savedRefreshToken.getLastUsedAt()).isNotNull();

        mockMvc.perform(
                        post("/api/v1/admin/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "refreshToken": "%s"
                                        }
                                        """.formatted(refreshToken))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid refresh token."));
    }

    @Test
    void logout_returnsUnauthorized_whenRefreshTokenIsInvalid() throws Exception {
        cleanup();

        mockMvc.perform(
                        post("/api/v1/admin/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "refreshToken": "rt_invalid_token"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid refresh token."));
    }

    @Test
    void logout_returnsBadRequest_whenRefreshTokenIsBlank() throws Exception {
        cleanup();

        mockMvc.perform(
                        post("/api/v1/admin/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "refreshToken": ""
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void login_returnsUnauthorized_whenCredentialsAreInvalid() throws Exception {
        cleanup();
        accountRepository.save(Account.builder()
                .loginId("alice")
                .passwordHash(passwordEncoder.encode("secret123!"))
                .status(AccountStatus.ACTIVE)
                .build());

        mockMvc.perform(
                        post("/api/v1/admin/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "loginId": "alice",
                                          "password": "wrong-password"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials."));
    }

    @Test
    void login_returnsBadRequest_whenRequiredFieldsAreBlank() throws Exception {
        cleanup();

        mockMvc.perform(
                        post("/api/v1/admin/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "loginId": "",
                                          "password": ""
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    private void cleanup() {
        organizationMemberRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        accountRepository.deleteAll();
    }

    private String extractField(String responseBody, String fieldName) {
        String prefix = "\"" + fieldName + "\":\"";
        int start = responseBody.indexOf(prefix);
        if (start < 0) {
            throw new IllegalStateException("Missing field: " + fieldName);
        }
        int valueStart = start + prefix.length();
        int valueEnd = responseBody.indexOf('"', valueStart);
        return responseBody.substring(valueStart, valueEnd);
    }
}
