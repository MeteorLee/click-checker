package com.clickchecker.eventtype.repository;

import com.clickchecker.eventtype.entity.EventTypeMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventTypeMappingRepository extends JpaRepository<EventTypeMapping, Long> {

    List<EventTypeMapping> findByOrganizationIdAndActiveTrueOrderByRawEventTypeAsc(Long organizationId);

    List<EventTypeMapping> findByOrganizationIdOrderByRawEventTypeAscIdAsc(Long organizationId);

    Optional<EventTypeMapping> findByIdAndOrganizationId(Long id, Long organizationId);
}
