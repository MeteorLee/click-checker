package com.clickchecker.eventrollup.service;

import com.clickchecker.organization.repository.OrganizationRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@ConditionalOnProperty(prefix = "app.rollup", name = "enabled", havingValue = "true")
public class EventRollupScheduler {

    private static final Logger log = LoggerFactory.getLogger(EventRollupScheduler.class);

    private static final long ROLLUP_SCHEDULER_LOCK_KEY = 4815162342L;
    private static final String TRY_ADVISORY_LOCK_SQL = "SELECT pg_try_advisory_lock(?)";
    private static final String ADVISORY_UNLOCK_SQL = "SELECT pg_advisory_unlock(?)";

    private final DataSource dataSource;
    private final OrganizationRepository organizationRepository;
    private final EventRollupService eventRollupService;

    @Scheduled(fixedDelayString = "${app.rollup.fixed-delay}")
    public void refreshHourlyRollups() {
        try (Connection connection = dataSource.getConnection()) {
            if (!tryAcquireLock(connection)) {
                log.debug("event rollup scheduler skipped because advisory lock is already held");
                return;
            }

            try {
                refreshAllOrganizations();
            } finally {
                releaseLock(connection);
            }
        } catch (SQLException exception) {
            log.error("event rollup scheduler failed to manage advisory lock", exception);
        }
    }

    private void refreshAllOrganizations() {
        List<Long> organizationIds = organizationRepository.findAllIds();
        int refreshed = 0;
        int idle = 0;
        int failed = 0;

        for (Long organizationId : organizationIds) {
            try {
                EventRollupRefreshResult result = eventRollupService.refreshOrganizationHourlyRollups(organizationId);
                if (result.batchCount() > 0) {
                    refreshed++;
                } else {
                    idle++;
                }
            } catch (RuntimeException exception) {
                failed++;
                log.warn("event rollup refresh failed for organizationId={}", organizationId, exception);
            }
        }

        log.info(
                "event rollup scheduler finished: organizations={}, refreshed={}, idle={}, failed={}",
                organizationIds.size(),
                refreshed,
                idle,
                failed
        );
    }

    private boolean tryAcquireLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(TRY_ADVISORY_LOCK_SQL)) {
            statement.setLong(1, ROLLUP_SCHEDULER_LOCK_KEY);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("pg_try_advisory_lock returned no rows");
                }
                return resultSet.getBoolean(1);
            }
        }
    }

    private void releaseLock(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement(ADVISORY_UNLOCK_SQL)) {
            statement.setLong(1, ROLLUP_SCHEDULER_LOCK_KEY);
            statement.execute();
        } catch (SQLException exception) {
            log.warn("failed to release event rollup advisory lock", exception);
        }
    }
}
