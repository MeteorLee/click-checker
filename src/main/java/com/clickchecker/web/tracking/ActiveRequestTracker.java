package com.clickchecker.web.tracking;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class ActiveRequestTracker {

    private final AtomicInteger activeRequests = new AtomicInteger(0);

    public int increment() {
        return activeRequests.incrementAndGet();
    }

    public int decrement() {
        return activeRequests.decrementAndGet();
    }

    public int getActiveRequests() {
        return activeRequests.get();
    }
}
