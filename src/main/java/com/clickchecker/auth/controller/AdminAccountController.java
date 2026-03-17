package com.clickchecker.auth.controller;

import com.clickchecker.account.entity.Account;
import com.clickchecker.account.service.AccountQueryService;
import com.clickchecker.web.resolver.CurrentAccountId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin")
public class AdminAccountController {

    private final AccountQueryService accountQueryService;

    @GetMapping("/me")
    public AdminMeResponse me(@CurrentAccountId Long accountId) {
        Account account = accountQueryService.getById(accountId);
        return new AdminMeResponse(account.getId(), account.getLoginId(), account.getStatus().name());
    }

    public record AdminMeResponse(
            Long accountId,
            String loginId,
            String status
    ) {
    }
}
