package com.clickchecker.account.service;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.clickchecker.account.entity.Account;
import com.clickchecker.account.repository.AccountRepository;
import com.clickchecker.organizationmember.repository.OrganizationMemberQueryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Service
public class AccountQueryService {

    private final AccountRepository accountRepository;
    private final OrganizationMemberQueryRepository organizationMemberQueryRepository;

    @Transactional(readOnly = true)
    public Account getById(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Unauthorized."));
    }

    @Transactional(readOnly = true)
    public List<AccountMembershipView> getMemberships(Long accountId) {
        return organizationMemberQueryRepository.findMembershipsByAccountId(accountId)
                .stream()
                .map(membership -> new AccountMembershipView(
                        membership.getId(),
                        membership.getOrganization().getId(),
                        membership.getOrganization().getName(),
                        membership.getRole().name()
                ))
                .toList();
    }

    public record AccountMembershipView(
            Long membershipId,
            Long organizationId,
            String organizationName,
            String role
    ) {
    }
}
