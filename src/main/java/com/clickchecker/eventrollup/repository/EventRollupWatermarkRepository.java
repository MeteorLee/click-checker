package com.clickchecker.eventrollup.repository;

import com.clickchecker.eventrollup.entity.EventRollupWatermark;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRollupWatermarkRepository extends JpaRepository<EventRollupWatermark, Long> {
}
