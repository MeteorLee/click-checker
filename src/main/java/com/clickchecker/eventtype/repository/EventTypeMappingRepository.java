package com.clickchecker.eventtype.repository;

import com.clickchecker.eventtype.entity.EventTypeMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventTypeMappingRepository extends JpaRepository<EventTypeMapping, Long> {

    List<EventTypeMapping> findByOrganizationIdAndActiveTrueOrderByRawEventTypeAsc(Long organizationId);
}
