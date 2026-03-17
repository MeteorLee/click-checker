package com.clickchecker.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clickchecker.account.entity.Account;
import com.clickchecker.account.entity.AccountStatus;
import com.clickchecker.account.repository.AccountRepository;
import com.clickchecker.auth.service.JwtTokenProvider;
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

        mockMvc.perform(
                        get("/api/v1/admin/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.issueAccessToken(account.getId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(account.getId()))
                .andExpect(jsonPath("$.loginId").value("alice"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
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
        accountRepository.deleteAll();
    }
}
