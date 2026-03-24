package com.clickchecker.eventrollup.service;

import com.clickchecker.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventRollupSchedulerTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    private final EventRollupService eventRollupService = mock(EventRollupService.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement lockStatement = mock(PreparedStatement.class);
    private final PreparedStatement unlockStatement = mock(PreparedStatement.class);
    private final ResultSet lockResultSet = mock(ResultSet.class);

    private EventRollupScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        scheduler = new EventRollupScheduler(dataSource, organizationRepository, eventRollupService);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)")).thenReturn(lockStatement);
        when(connection.prepareStatement("SELECT pg_advisory_unlock(?)")).thenReturn(unlockStatement);
        when(lockStatement.executeQuery()).thenReturn(lockResultSet);
        when(lockResultSet.next()).thenReturn(true);
        when(unlockStatement.execute()).thenReturn(true);
    }

    @Test
    void refreshHourlyRollups_skipsWhenAdvisoryLockNotAcquired() throws Exception {
        when(lockResultSet.getBoolean(1)).thenReturn(false);

        scheduler.refreshHourlyRollups();

        verify(organizationRepository, never()).findAllIds();
        verify(eventRollupService, never()).refreshOrganizationHourlyRollups(org.mockito.ArgumentMatchers.anyLong());
        verify(unlockStatement, never()).execute();
    }

    @Test
    void refreshHourlyRollups_refreshesAllOrganizationsWhenAdvisoryLockAcquired() throws Exception {
        when(lockResultSet.getBoolean(1)).thenReturn(true);
        when(organizationRepository.findAllIds()).thenReturn(List.of(1L, 2L));
        when(eventRollupService.refreshOrganizationHourlyRollups(1L))
                .thenReturn(new EventRollupRefreshResult(1L, 3, null));
        when(eventRollupService.refreshOrganizationHourlyRollups(2L))
                .thenReturn(new EventRollupRefreshResult(2L, 0, null));

        scheduler.refreshHourlyRollups();

        verify(organizationRepository).findAllIds();
        verify(eventRollupService).refreshOrganizationHourlyRollups(1L);
        verify(eventRollupService).refreshOrganizationHourlyRollups(2L);
        verify(unlockStatement).execute();
    }
}
