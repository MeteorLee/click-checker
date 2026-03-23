package com.clickchecker.eventrollup.service;

import com.clickchecker.eventrollup.entity.EventRollupWatermark;
import com.clickchecker.eventrollup.repository.EventRollupWatermarkRepository;
import jakarta.persistence.EntityManager;
import java.math.BigInteger;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("local")
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CLICKCHECKER_LOCAL_ROLLUP_TEST", matches = "true")
class EventRollupServiceLocalIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EventRollupService eventRollupService;

    @Autowired
    private EventRollupWatermarkRepository eventRollupWatermarkRepository;

    @Test
    void refreshOrganizationHourlyRollups_runsAgainstLocalDatabase() {
        Long organizationId = findOrganizationIdWithEvents();

        Optional<EventRollupWatermark> before = eventRollupWatermarkRepository.findById(organizationId);

        EventRollupRefreshResult result = eventRollupService.refreshOrganizationHourlyRollups(organizationId);

        Optional<EventRollupWatermark> after = eventRollupWatermarkRepository.findById(organizationId);

        assertThat(result.organizationId()).isEqualTo(organizationId);
        if (result.batchCount() > 0) {
            assertThat(after).isPresent();
            assertThat(after.orElseThrow().getProcessedCreatedAt()).isEqualTo(result.processedCreatedAt());
            assertThat(countHourlyRollups(organizationId)).isGreaterThan(0L);
        } else {
            assertThat(after.map(EventRollupWatermark::getProcessedCreatedAt))
                    .isEqualTo(before.map(EventRollupWatermark::getProcessedCreatedAt));
        }
    }

    private Long findOrganizationIdWithEvents() {
        Object raw = entityManager.createNativeQuery("""
                        SELECT e.organization_id
                        FROM events e
                        GROUP BY e.organization_id
                        ORDER BY COUNT(*) DESC
                        LIMIT 1
                        """)
                .getSingleResult();

        if (raw instanceof BigInteger bigInteger) {
            return bigInteger.longValue();
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("unsupported organization id type: " + raw);
    }

    private long countHourlyRollups(Long organizationId) {
        Object raw = entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM event_hourly_rollups
                        WHERE organization_id = :organizationId
                        """)
                .setParameter("organizationId", organizationId)
                .getSingleResult();

        if (raw instanceof BigInteger bigInteger) {
            return bigInteger.longValue();
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("unsupported count type: " + raw);
    }
}
