package com.clickchecker.eventrollup.repository;

import com.clickchecker.eventrollup.entity.EventHourlyRollup;
import com.clickchecker.eventrollup.entity.EventHourlyRollupId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHourlyRollupRepository extends JpaRepository<EventHourlyRollup, EventHourlyRollupId> {
}
