package com.clickchecker.event.controller;

import com.clickchecker.event.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/events")
public class EventQueryController {

    private final EventService eventService;

    @GetMapping("/count")
    public CountResponse count(@RequestParam String eventType) {
        Long count = eventService.countByEventType(eventType);
        return new CountResponse(eventType, count);
    }

    public record CountResponse(String eventType, Long count) {}
}
