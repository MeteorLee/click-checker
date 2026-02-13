package com.clickchecker.eventuser.repository;

import com.clickchecker.eventuser.entity.EventUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventUserRepository extends JpaRepository<EventUser, Long> {

    boolean existsByOrganizationIdAndExternalUserId(Long organizationId, String externalUserId);
}
