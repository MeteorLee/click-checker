package com.clickchecker.web.tracking;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class TrafficStateManager {

    private final AtomicReference<TrafficState> trafficState =
            new AtomicReference<>(TrafficState.SERVING);

    public TrafficState getCurrentState() {
        return trafficState.get();
    }

    public boolean enterDraining() {
        return trafficState.compareAndSet(
                TrafficState.SERVING,
                TrafficState.DRAINING
        );
    }

    public boolean enterServing() {
        return trafficState.compareAndSet(
                TrafficState.DRAINING,
                TrafficState.SERVING
        );
    }

    public boolean isDraining() {
        return trafficState.get() == TrafficState.DRAINING;
    }
}
