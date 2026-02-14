package com.clickchecker.eventuser.repository;

import com.clickchecker.eventuser.entity.EventUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EventUserRepository extends JpaRepository<EventUser, Long> {

    boolean existsByOrganizationIdAndExternalUserId(Long organizationId, String externalUserId);

    Optional<EventUser> findByOrganizationIdAndExternalUserId(Long organizationId, String externalUserId);
}
