package com.clickchecker.web.controller;

import com.clickchecker.web.dto.DrainStatusResponseDto;
import com.clickchecker.web.tracking.ActiveRequestTracker;
import com.clickchecker.web.tracking.TrafficStateManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/drain")
public class InternalDrainController {

    private final TrafficStateManager trafficStateManager;
    private final ActiveRequestTracker activeRequestTracker;

    public InternalDrainController(
            TrafficStateManager trafficStateManager,
            ActiveRequestTracker activeRequestTracker
    ) {
        this.trafficStateManager = trafficStateManager;
        this.activeRequestTracker = activeRequestTracker;
    }

    @PostMapping("/start")
    public ResponseEntity<DrainStatusResponseDto> startDraining() {
        boolean changed = trafficStateManager.enterDraining();

        return ResponseEntity.ok(new DrainStatusResponseDto(
                changed,
                trafficStateManager.getCurrentState(),
                activeRequestTracker.getActiveRequests()
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<DrainStatusResponseDto> drainStatus() {
        return ResponseEntity.ok(new DrainStatusResponseDto(
                null,
                trafficStateManager.getCurrentState(),
                activeRequestTracker.getActiveRequests()
        ));
    }
}
