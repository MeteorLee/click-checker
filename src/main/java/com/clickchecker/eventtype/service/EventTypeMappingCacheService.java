package com.clickchecker.eventtype.service;

import com.clickchecker.eventtype.repository.EventTypeMappingRepository;
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
public class EventTypeMappingCacheService {

    public static final String ACTIVE_EVENT_TYPE_MAPPINGS_BY_ORG_CACHE = "activeEventTypeMappingsByOrg";

    private final EventTypeMappingRepository eventTypeMappingRepository;
    private final CacheManager cacheManager;

    @Cacheable(cacheNames = ACTIVE_EVENT_TYPE_MAPPINGS_BY_ORG_CACHE, key = "#organizationId")
    @Transactional(readOnly = true)
    public List<CachedEventTypeMapping> getActiveMappings(Long organizationId) {
        return eventTypeMappingRepository.findByOrganizationIdAndActiveTrueOrderByRawEventTypeAsc(organizationId).stream()
                .map(mapping -> new CachedEventTypeMapping(mapping.getRawEventType(), mapping.getCanonicalEventType()))
                .toList();
    }

    public void evictActiveMappingsAfterCommit(Long organizationId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictActiveMappings(organizationId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictActiveMappings(organizationId);
            }
        });
    }

    private void evictActiveMappings(Long organizationId) {
        Cache cache = cacheManager.getCache(ACTIVE_EVENT_TYPE_MAPPINGS_BY_ORG_CACHE);
        if (cache != null) {
            cache.evict(organizationId);
        }
    }

    public record CachedEventTypeMapping(
            String rawEventType,
            String canonicalEventType
    ) {
    }
}
