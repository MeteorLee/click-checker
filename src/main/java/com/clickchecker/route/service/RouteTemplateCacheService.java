package com.clickchecker.route.service;

import com.clickchecker.route.repository.RouteTemplateRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class RouteTemplateCacheService {

    public static final String ACTIVE_ROUTE_TEMPLATES_BY_ORG_CACHE = "activeRouteTemplatesByOrg";

    private final RouteTemplateRepository routeTemplateRepository;
    private final CacheManager cacheManager;

    @Cacheable(cacheNames = ACTIVE_ROUTE_TEMPLATES_BY_ORG_CACHE, key = "#organizationId")
    @Transactional(readOnly = true)
    public List<CachedRouteTemplate> getActiveTemplates(Long organizationId) {
        return routeTemplateRepository.findByOrganizationIdAndActiveTrueOrderByPriorityDescIdAsc(organizationId).stream()
                .map(routeTemplate -> new CachedRouteTemplate(routeTemplate.getTemplate(), routeTemplate.getRouteKey()))
                .toList();
    }

    public void evictActiveTemplatesAfterCommit(Long organizationId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictActiveTemplates(organizationId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictActiveTemplates(organizationId);
            }
        });
    }

    private void evictActiveTemplates(Long organizationId) {
        Cache cache = cacheManager.getCache(ACTIVE_ROUTE_TEMPLATES_BY_ORG_CACHE);
        if (cache != null) {
            cache.evict(organizationId);
        }
    }

    public record CachedRouteTemplate(
            String template,
            String routeKey
    ) {
    }
}
