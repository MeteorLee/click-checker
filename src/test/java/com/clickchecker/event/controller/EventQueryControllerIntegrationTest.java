package com.clickchecker.event.controller;

import com.clickchecker.event.entity.Event;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.eventuser.entity.EventUser;
import com.clickchecker.eventuser.repository.EventUserRepository;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
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

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private EventUserRepository eventUserRepository;

    @Test
    void aggregatePaths_returnsTopNPaths_withoutEventTypeFilter() throws Exception {
        cleanup();

        Organization organization = saveOrganization();

        LocalDateTime base = LocalDateTime.of(2026, 2, 13, 12, 0);

        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).occurredAt(base.plusMinutes(1)).build());
        eventRepository.save(Event.builder().eventType("view").path("/home").organization(organization).occurredAt(base.plusMinutes(2)).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organization).occurredAt(base.plusMinutes(3)).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organization).occurredAt(base.plusMinutes(4)).build());
        eventRepository.save(Event.builder().eventType("view").path("/post/2").organization(organization).occurredAt(base.plusMinutes(5)).build());

        mockMvc.perform(
                        get("/api/events/aggregates/paths")
                                .param("organizationId", organization.getId().toString())
                                .param("from", "2026-02-13T00:00:00")
                                .param("to", "2026-02-14T00:00:00")
                                .param("top", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
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
        cleanup();

        Organization organization = saveOrganization();

        LocalDateTime base = LocalDateTime.of(2026, 2, 13, 12, 0);

        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).occurredAt(base.plusMinutes(1)).build());
        eventRepository.save(Event.builder().eventType("view").path("/home").organization(organization).occurredAt(base.plusMinutes(2)).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organization).occurredAt(base.plusMinutes(3)).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organization).occurredAt(base.plusMinutes(4)).build());
        eventRepository.save(Event.builder().eventType("view").path("/post/2").organization(organization).occurredAt(base.plusMinutes(5)).build());

        mockMvc.perform(
                        get("/api/events/aggregates/paths")
                                .param("organizationId", organization.getId().toString())
                                .param("from", "2026-02-13T00:00:00")
                                .param("to", "2026-02-14T00:00:00")
                                .param("eventType", "click")
                                .param("top", "5")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.eventType").value("click"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].path").value("/post/1"))
                .andExpect(jsonPath("$.items[0].count").value(2))
                .andExpect(jsonPath("$.items[1].path").value("/home"))
                .andExpect(jsonPath("$.items[1].count").value(1));
    }

    @Test
    void aggregatePaths_excludesOtherOrganizationData() throws Exception {
        cleanup();

        Organization organizationA = saveOrganization("acme");
        Organization organizationB = saveOrganization("globex");

        LocalDateTime base = LocalDateTime.of(2026, 2, 13, 12, 0);

        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organizationA).occurredAt(base.plusMinutes(1)).build());
        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organizationA).occurredAt(base.plusMinutes(2)).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organizationA).occurredAt(base.plusMinutes(3)).build());

        eventRepository.save(Event.builder().eventType("click").path("/hacked").organization(organizationB).occurredAt(base.plusMinutes(4)).build());
        eventRepository.save(Event.builder().eventType("click").path("/hacked").organization(organizationB).occurredAt(base.plusMinutes(5)).build());
        eventRepository.save(Event.builder().eventType("click").path("/hacked").organization(organizationB).occurredAt(base.plusMinutes(6)).build());

        mockMvc.perform(
                        get("/api/events/aggregates/paths")
                                .param("organizationId", organizationA.getId().toString())
                                .param("from", "2026-02-13T00:00:00")
                                .param("to", "2026-02-14T00:00:00")
                                .param("eventType", "click")
                                .param("top", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organizationA.getId()))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].path").value("/home"))
                .andExpect(jsonPath("$.items[0].count").value(2))
                .andExpect(jsonPath("$.items[1].path").value("/post/1"))
                .andExpect(jsonPath("$.items[1].count").value(1));
    }

    @Test
    void aggregatePaths_filtersByExternalUserId_whenExternalUserIdIsProvided() throws Exception {
        cleanup();

        Organization organization = saveOrganization("acme");
        EventUser eventUserA = saveEventUser(organization, "u-1001");
        EventUser eventUserB = saveEventUser(organization, "u-1002");

        LocalDateTime base = LocalDateTime.of(2026, 2, 13, 12, 0);

        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).eventUser(eventUserA).occurredAt(base.plusMinutes(1)).build());
        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).eventUser(eventUserA).occurredAt(base.plusMinutes(2)).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organization).eventUser(eventUserA).occurredAt(base.plusMinutes(3)).build());
        eventRepository.save(Event.builder().eventType("click").path("/hacked").organization(organization).eventUser(eventUserB).occurredAt(base.plusMinutes(4)).build());

        mockMvc.perform(
                        get("/api/events/aggregates/paths")
                                .param("organizationId", organization.getId().toString())
                                .param("externalUserId", eventUserA.getExternalUserId())
                                .param("from", "2026-02-13T00:00:00")
                                .param("to", "2026-02-14T00:00:00")
                                .param("eventType", "click")
                                .param("top", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.externalUserId").value(eventUserA.getExternalUserId()))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].path").value("/home"))
                .andExpect(jsonPath("$.items[0].count").value(2))
                .andExpect(jsonPath("$.items[1].path").value("/post/1"))
                .andExpect(jsonPath("$.items[1].count").value(1));
    }

    @Test
    void aggregatePaths_returnsBadRequest_whenFromIsNotBeforeTo() throws Exception {
        cleanup();
        Organization organization = saveOrganization();

        mockMvc.perform(
                        get("/api/events/aggregates/paths")
                                .param("organizationId", organization.getId().toString())
                                .param("from", "2026-02-14T00:00:00")
                                .param("to", "2026-02-14T00:00:00")
                                .param("top", "5")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void aggregatePaths_returnsBadRequest_whenTopIsOutOfRange() throws Exception {
        cleanup();
        Organization organization = saveOrganization();

        mockMvc.perform(
                        get("/api/events/aggregates/paths")
                                .param("organizationId", organization.getId().toString())
                                .param("from", "2026-02-13T00:00:00")
                                .param("to", "2026-02-14T00:00:00")
                                .param("top", "0")
                )
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        get("/api/events/aggregates/paths")
                                .param("organizationId", organization.getId().toString())
                                .param("from", "2026-02-13T00:00:00")
                                .param("to", "2026-02-14T00:00:00")
                                .param("top", "101")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void aggregatePaths_ignoresBlankExternalUserIdFilter() throws Exception {
        cleanup();
        Organization organization = saveOrganization();

        mockMvc.perform(
                        get("/api/events/aggregates/paths")
                                .param("organizationId", organization.getId().toString())
                                .param("externalUserId", " ")
                                .param("from", "2026-02-13T00:00:00")
                                .param("to", "2026-02-14T00:00:00")
                                .param("top", "5")
                )
                .andExpect(status().isOk());
    }

    private void cleanup() {
        eventRepository.deleteAll();
        eventUserRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    private Organization saveOrganization() {
        return saveOrganization("acme");
    }

    private Organization saveOrganization(String name) {
        return organizationRepository.save(
                Organization.builder()
                        .name(name)
                        .build()
        );
    }

    private EventUser saveEventUser(Organization organization, String externalUserId) {
        return eventUserRepository.save(
                EventUser.builder()
                        .organization(organization)
                        .externalUserId(externalUserId)
                        .build()
        );
    }
}
