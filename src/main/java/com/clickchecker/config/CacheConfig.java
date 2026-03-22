package com.clickchecker.config;

import com.clickchecker.eventtype.service.EventTypeMappingCacheService;
import com.clickchecker.route.service.RouteTemplateCacheService;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                RouteTemplateCacheService.ACTIVE_ROUTE_TEMPLATES_BY_ORG_CACHE,
                EventTypeMappingCacheService.ACTIVE_EVENT_TYPE_MAPPINGS_BY_ORG_CACHE
        );
        cacheManager.setAllowNullValues(false);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterAccess(Duration.ofMinutes(10)));
        return cacheManager;
    }
}
