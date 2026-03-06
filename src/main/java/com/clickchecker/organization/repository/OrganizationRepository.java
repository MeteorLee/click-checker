package com.clickchecker.organization.repository;

import com.clickchecker.organization.entity.ApiKeyStatus;
import com.clickchecker.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByApiKeyHashAndApiKeyStatus(String apiKeyHash, ApiKeyStatus status);

    Optional<Organization> findByApiKeyKidAndApiKeyStatus(String apiKeyKid, ApiKeyStatus status);
}
