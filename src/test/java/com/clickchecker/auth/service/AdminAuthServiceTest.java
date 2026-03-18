package com.clickchecker.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clickchecker.account.entity.Account;
import com.clickchecker.account.entity.AccountStatus;
import com.clickchecker.account.repository.AccountRepository;
import com.clickchecker.auth.entity.RefreshToken;
import com.clickchecker.auth.repository.RefreshTokenRepository;
import com.clickchecker.auth.service.result.AdminTokenResult;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class AdminAuthServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtTokenProvider jwtTokenProvider =
            new JwtTokenProvider("local-dev-jwt-secret-local-dev-jwt-secret-123456", 900);
    private final RefreshTokenIssuer refreshTokenIssuer = new RefreshTokenIssuer(1209600);
    private final AdminAuthService adminAuthService =
            new AdminAuthService(
                    accountRepository,
                    refreshTokenRepository,
                    passwordEncoder,
                    jwtTokenProvider,
                    refreshTokenIssuer
            );

    @Test
    void loginShouldIssueAccessTokenWhenCredentialsAreValid() {
        Account account = Account.builder()
                .loginId("alice")
                .passwordHash(passwordEncoder.encode("secret123!"))
                .status(AccountStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(account, "id", 1L);

        when(accountRepository.findByLoginId("alice"))
                .thenReturn(Optional.of(account));

        AdminTokenResult result = adminAuthService.login("alice", "secret123!");

        assertThat(result.accountId()).isEqualTo(1L);
        assertThat(result.accessTokenExpiresIn()).isEqualTo(900);
        assertThat(result.refreshToken()).startsWith("rt_");
        assertThat(result.refreshTokenExpiresIn()).isEqualTo(1209600);
        assertThat(jwtTokenProvider.isValidAccessToken(result.accessToken())).isTrue();
        assertThat(jwtTokenProvider.extractAccountId(result.accessToken())).isEqualTo(1L);

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        RefreshToken savedRefreshToken = refreshTokenCaptor.getValue();
        assertThat(savedRefreshToken.getAccount()).isEqualTo(account);
        assertThat(savedRefreshToken.getTokenHash()).isEqualTo(refreshTokenIssuer.hash(result.refreshToken()));
        assertThat(savedRefreshToken.getExpiresAt()).isNotNull();
    }

    @Test
    void loginShouldRejectUnknownLoginId() {
        when(accountRepository.findByLoginId("alice"))
                .thenReturn(Optional.empty());

        assertUnauthorized(() -> adminAuthService.login("alice", "secret123!"));
    }

    @Test
    void loginShouldRejectMismatchedPassword() {
        Account account = Account.builder()
                .loginId("alice")
                .passwordHash(passwordEncoder.encode("secret123!"))
                .status(AccountStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(account, "id", 1L);

        when(accountRepository.findByLoginId("alice"))
                .thenReturn(Optional.of(account));

        assertUnauthorized(() -> adminAuthService.login("alice", "wrong-password"));
    }

    @Test
    void loginShouldRejectDisabledAccount() {
        Account account = Account.builder()
                .loginId("alice")
                .passwordHash(passwordEncoder.encode("secret123!"))
                .status(AccountStatus.DISABLED)
                .build();
        ReflectionTestUtils.setField(account, "id", 1L);

        when(accountRepository.findByLoginId("alice"))
                .thenReturn(Optional.of(account));

        assertUnauthorized(() -> adminAuthService.login("alice", "secret123!"));
    }

    @Test
    void refreshShouldRotateRefreshTokenWhenSavedTokenIsValid() {
        Account account = Account.builder()
                .loginId("alice")
                .passwordHash(passwordEncoder.encode("secret123!"))
                .status(AccountStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(account, "id", 1L);

        String oldRefreshToken = "rt_existing_refresh_token";
        RefreshToken existingRefreshToken = RefreshToken.builder()
                .account(account)
                .tokenHash(refreshTokenIssuer.hash(oldRefreshToken))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(refreshTokenIssuer.hash(oldRefreshToken)))
                .thenReturn(Optional.of(existingRefreshToken));

        AdminTokenResult result = adminAuthService.refresh(oldRefreshToken);

        assertThat(result.accountId()).isEqualTo(1L);
        assertThat(result.refreshToken()).startsWith("rt_");
        assertThat(result.refreshToken()).isNotEqualTo(oldRefreshToken);
        assertThat(existingRefreshToken.isRevoked()).isTrue();
        assertThat(existingRefreshToken.getLastUsedAt()).isNotNull();

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        RefreshToken rotatedRefreshToken = refreshTokenCaptor.getValue();
        assertThat(rotatedRefreshToken.getTokenHash()).isEqualTo(refreshTokenIssuer.hash(result.refreshToken()));
    }

    @Test
    void refreshShouldRejectUnknownRefreshToken() {
        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(refreshTokenIssuer.hash("rt_missing")))
                .thenReturn(Optional.empty());

        assertInvalidRefreshToken(() -> adminAuthService.refresh("rt_missing"));
    }

    @Test
    void refreshShouldRejectExpiredRefreshToken() {
        Account account = Account.builder()
                .loginId("alice")
                .passwordHash(passwordEncoder.encode("secret123!"))
                .status(AccountStatus.ACTIVE)
                .build();
        String oldRefreshToken = "rt_expired_refresh_token";
        RefreshToken expiredRefreshToken = RefreshToken.builder()
                .account(account)
                .tokenHash(refreshTokenIssuer.hash(oldRefreshToken))
                .expiresAt(Instant.now().minusSeconds(1))
                .build();

        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(refreshTokenIssuer.hash(oldRefreshToken)))
                .thenReturn(Optional.of(expiredRefreshToken));

        assertInvalidRefreshToken(() -> adminAuthService.refresh(oldRefreshToken));
    }

    @Test
    void logoutShouldRevokeRefreshTokenWhenSavedTokenIsValid() {
        Account account = Account.builder()
                .loginId("alice")
                .passwordHash(passwordEncoder.encode("secret123!"))
                .status(AccountStatus.ACTIVE)
                .build();
        String refreshToken = "rt_logout_refresh_token";
        RefreshToken savedRefreshToken = RefreshToken.builder()
                .account(account)
                .tokenHash(refreshTokenIssuer.hash(refreshToken))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(refreshTokenIssuer.hash(refreshToken)))
                .thenReturn(Optional.of(savedRefreshToken));

        adminAuthService.logout(refreshToken);

        assertThat(savedRefreshToken.isRevoked()).isTrue();
        assertThat(savedRefreshToken.getLastUsedAt()).isNotNull();
    }

    @Test
    void logoutShouldRejectUnknownRefreshToken() {
        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(refreshTokenIssuer.hash("rt_missing")))
                .thenReturn(Optional.empty());

        assertInvalidRefreshToken(() -> adminAuthService.logout("rt_missing"));
    }

    private void assertUnauthorized(ThrowingRunnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(401);
                    assertThat(ex.getReason()).isEqualTo("Invalid credentials.");
                });
    }

    private void assertInvalidRefreshToken(ThrowingRunnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(401);
                    assertThat(ex.getReason()).isEqualTo("Invalid refresh token.");
                });
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
