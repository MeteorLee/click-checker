package com.clickchecker.organization.repository;

import com.clickchecker.organization.entity.ApiKeyStatus;
import com.clickchecker.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByApiKeyHashAndApiKeyStatus(String apiKeyHash, ApiKeyStatus status);

    Optional<Organization> findByApiKeyKidAndApiKeyStatus(String apiKeyKid, ApiKeyStatus status);

    @Query("select o.id from Organization o order by o.id asc")
    List<Long> findAllIds();
}
