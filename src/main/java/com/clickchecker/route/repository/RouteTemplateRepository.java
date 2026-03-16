package com.clickchecker.route.repository;

import com.clickchecker.route.entity.RouteTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteTemplateRepository extends JpaRepository<RouteTemplate, Long> {

    List<RouteTemplate> findByOrganizationIdAndActiveTrueOrderByPriorityDescIdAsc(Long organizationId);
}
