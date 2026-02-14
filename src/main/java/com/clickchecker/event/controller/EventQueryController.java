package com.clickchecker.event.controller;

import com.clickchecker.event.repository.dto.PathCountDto;
import com.clickchecker.event.service.EventQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/events")
public class EventQueryController {

    private final EventQueryService eventQueryService;

    // 개발용
    @GetMapping("/aggregates/count")
    public CountResponse count(@RequestParam String eventType) {
        Long count = eventQueryService.countByEventType(eventType);
        return new CountResponse(eventType, count);
    }

    @GetMapping("/aggregates/paths")
    public PathAggregateResponse aggregatePaths(
            @RequestParam Long organizationId,
            @RequestParam(required = false) String externalUserId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "10") int top
    ) {
        if (organizationId < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`organizationId` must be positive.");
        }
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`from` must be before `to`.");
        }
        if (top < 1 || top > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "`top` must be between 1 and 100.");
        }

        List<PathCountDto> pathCounts = eventQueryService.countByPathBetween(from, to, organizationId, externalUserId, eventType, top);
        return new PathAggregateResponse(organizationId, externalUserId, from, to, eventType, top, pathCounts);
    }

    public record CountResponse(String eventType, Long count) {}
    public record PathAggregateResponse(
            Long organizationId,
            String externalUserId,
            LocalDateTime from,
            LocalDateTime to,
            String eventType,
            int top,
            List<PathCountDto> items
    ) {}
}
