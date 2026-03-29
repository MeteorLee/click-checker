package com.clickchecker.route.repository;

import com.clickchecker.route.entity.RouteTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteTemplateRepository extends JpaRepository<RouteTemplate, Long> {

    List<RouteTemplate> findByOrganizationIdAndActiveTrueOrderByPriorityDescIdAsc(Long organizationId);

    List<RouteTemplate> findByOrganizationIdOrderByTemplateAscIdAsc(Long organizationId);

    Optional<RouteTemplate> findByIdAndOrganizationId(Long id, Long organizationId);
}
