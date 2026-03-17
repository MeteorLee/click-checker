package com.clickchecker.auth.controller;

import com.clickchecker.auth.controller.request.AdminLoginRequest;
import com.clickchecker.auth.controller.request.AdminLogoutRequest;
import com.clickchecker.auth.controller.request.AdminRefreshRequest;
import com.clickchecker.auth.controller.response.AdminLoginResponse;
import com.clickchecker.auth.controller.response.AdminRefreshResponse;
import com.clickchecker.auth.service.AdminAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public ResponseEntity<AdminLoginResponse> login(@RequestBody @Valid AdminLoginRequest request) {
        AdminAuthService.TokenResult result = adminAuthService.login(request.loginId(), request.password());
        return ResponseEntity.ok(new AdminLoginResponse(
                result.accountId(),
                result.accessToken(),
                result.accessTokenExpiresIn(),
                result.refreshToken(),
                result.refreshTokenExpiresIn()
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AdminRefreshResponse> refresh(@RequestBody @Valid AdminRefreshRequest request) {
        AdminAuthService.TokenResult result = adminAuthService.refresh(request.refreshToken());
        return ResponseEntity.ok(new AdminRefreshResponse(
                result.accountId(),
                result.accessToken(),
                result.accessTokenExpiresIn(),
                result.refreshToken(),
                result.refreshTokenExpiresIn()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody @Valid AdminLogoutRequest request) {
        adminAuthService.logout(request.refreshToken());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
