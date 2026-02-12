package com.clickchecker.event.controller;

import com.clickchecker.event.entity.Event;
import com.clickchecker.event.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("ci")
@SpringBootTest
@AutoConfigureMockMvc
class EventQueryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Test
    void aggregatePaths_returnsTopNPaths_withoutEventTypeFilter() throws Exception {
        eventRepository.deleteAll();

        LocalDateTime base = LocalDateTime.of(2026, 2, 13, 12, 0);

        eventRepository.save(Event.builder().eventType("click").path("/home").occurredAt(base.plusMinutes(1)).build());
        eventRepository.save(Event.builder().eventType("view").path("/home").occurredAt(base.plusMinutes(2)).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").occurredAt(base.plusMinutes(3)).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").occurredAt(base.plusMinutes(4)).build());
        eventRepository.save(Event.builder().eventType("view").path("/post/2").occurredAt(base.plusMinutes(5)).build());

        mockMvc.perform(
                        get("/api/events/aggregates/paths")
                                .param("from", "2026-02-13T00:00:00")
                                .param("to", "2026-02-14T00:00:00")
                                .param("top", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.top").value(2))
                .andExpect(jsonPath("$.eventType").isEmpty())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].path").value("/home"))
                .andExpect(jsonPath("$.items[0].count").value(2))
                .andExpect(jsonPath("$.items[1].path").value("/post/1"))
                .andExpect(jsonPath("$.items[1].count").value(2));
    }

    @Test
    void aggregatePaths_filtersByEventType_whenEventTypeIsProvided() throws Exception {
        eventRepository.deleteAll();

        LocalDateTime base = LocalDateTime.of(2026, 2, 13, 12, 0);

        eventRepository.save(Event.builder().eventType("click").path("/home").occurredAt(base.plusMinutes(1)).build());
        eventRepository.save(Event.builder().eventType("view").path("/home").occurredAt(base.plusMinutes(2)).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").occurredAt(base.plusMinutes(3)).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").occurredAt(base.plusMinutes(4)).build());
        eventRepository.save(Event.builder().eventType("view").path("/post/2").occurredAt(base.plusMinutes(5)).build());

        mockMvc.perform(
                        get("/api/events/aggregates/paths")
                                .param("from", "2026-02-13T00:00:00")
                                .param("to", "2026-02-14T00:00:00")
                                .param("eventType", "click")
                                .param("top", "5")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("click"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].path").value("/post/1"))
                .andExpect(jsonPath("$.items[0].count").value(2))
                .andExpect(jsonPath("$.items[1].path").value("/home"))
                .andExpect(jsonPath("$.items[1].count").value(1));
    }

    @Test
    void aggregatePaths_returnsBadRequest_whenFromIsNotBeforeTo() throws Exception {
        mockMvc.perform(
                        get("/api/events/aggregates/paths")
                                .param("from", "2026-02-14T00:00:00")
                                .param("to", "2026-02-14T00:00:00")
                                .param("top", "5")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void aggregatePaths_returnsBadRequest_whenTopIsOutOfRange() throws Exception {
        mockMvc.perform(
                        get("/api/events/aggregates/paths")
                                .param("from", "2026-02-13T00:00:00")
                                .param("to", "2026-02-14T00:00:00")
                                .param("top", "0")
                )
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        get("/api/events/aggregates/paths")
                                .param("from", "2026-02-13T00:00:00")
                                .param("to", "2026-02-14T00:00:00")
                                .param("top", "101")
                )
                .andExpect(status().isBadRequest());
    }
}
