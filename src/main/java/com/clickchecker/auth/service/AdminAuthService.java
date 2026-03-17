package com.clickchecker.auth.service;

import com.clickchecker.account.entity.Account;
import com.clickchecker.account.repository.AccountRepository;
import com.clickchecker.auth.entity.RefreshToken;
import com.clickchecker.auth.repository.RefreshTokenRepository;
import com.clickchecker.auth.service.result.AdminTokenResult;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Service
public class AdminAuthService {

    private static final String DUPLICATED_LOGIN_ID_MESSAGE = "Duplicated loginId.";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials.";
    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token.";

    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenIssuer refreshTokenIssuer;

    @Transactional
    public AdminTokenResult signup(String loginId, String password) {
        String normalizedLoginId = normalizeLoginId(loginId);

        if (accountRepository.findByLoginId(normalizedLoginId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATED_LOGIN_ID_MESSAGE);
        }

        Account account = accountRepository.save(Account.builder()
                .loginId(normalizedLoginId)
                .passwordHash(passwordEncoder.encode(password))
                .status(null)
                .build());

        return issueTokens(account);
    }

    @Transactional
    public AdminTokenResult login(String loginId, String password) {
        String normalizedLoginId = normalizeLoginId(loginId);

        Account account = accountRepository.findByLoginId(normalizedLoginId)
                .filter(found -> !found.isDisabled())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE));

        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
        }

        return issueTokens(account);
    }

    @Transactional
    public AdminTokenResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw invalidRefreshToken();
        }

        Instant now = Instant.now();
        String tokenHash = refreshTokenIssuer.hash(refreshToken);
        RefreshToken savedRefreshToken = refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHash)
                .orElseThrow(this::invalidRefreshToken);

        if (savedRefreshToken.isExpired(now) || savedRefreshToken.getAccount().isDisabled()) {
            throw invalidRefreshToken();
        }

        savedRefreshToken.markUsed(now);
        savedRefreshToken.revoke(now);

        return issueTokens(savedRefreshToken.getAccount());
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw invalidRefreshToken();
        }

        Instant now = Instant.now();
        String tokenHash = refreshTokenIssuer.hash(refreshToken);
        RefreshToken savedRefreshToken = refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHash)
                .orElseThrow(this::invalidRefreshToken);

        if (savedRefreshToken.isExpired(now) || savedRefreshToken.getAccount().isDisabled()) {
            throw invalidRefreshToken();
        }

        savedRefreshToken.markUsed(now);
        savedRefreshToken.revoke(now);
    }

    private AdminTokenResult issueTokens(Account account) {
        RefreshTokenIssuer.IssuedRefreshToken issuedRefreshToken = refreshTokenIssuer.issue();
        refreshTokenRepository.save(RefreshToken.builder()
                .account(account)
                .tokenHash(issuedRefreshToken.tokenHash())
                .expiresAt(issuedRefreshToken.expiresAt())
                .build());

        return new AdminTokenResult(
                account.getId(),
                jwtTokenProvider.issueAccessToken(account.getId()),
                jwtTokenProvider.getAccessTokenExpirationSeconds(),
                issuedRefreshToken.plainToken(),
                refreshTokenIssuer.getRefreshTokenExpirationSeconds()
        );
    }

    private ResponseStatusException invalidRefreshToken() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_REFRESH_TOKEN_MESSAGE);
    }

    private String normalizeLoginId(String loginId) {
        return loginId == null
                ? null
                : loginId.trim().toLowerCase(Locale.ROOT);
    }
}
