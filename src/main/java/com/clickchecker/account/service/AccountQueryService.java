package com.clickchecker.account.service;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.clickchecker.account.entity.Account;
import com.clickchecker.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Service
public class AccountQueryService {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public Account getById(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Unauthorized."));
    }
}
