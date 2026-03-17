package com.clickchecker.analytics.aggregate.controller;

import com.clickchecker.analytics.support.AnalyticsControllerIntegrationTestSupport;
import com.clickchecker.event.entity.Event;
import com.clickchecker.eventuser.entity.EventUser;
import com.clickchecker.organization.entity.Organization;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("ci")
@SpringBootTest
@AutoConfigureMockMvc
class AggregateAnalyticsControllerIntegrationTest extends AnalyticsControllerIntegrationTestSupport {

    @Test
    void aggregatePaths_returnsTopNPaths_withoutEventTypeFilter() throws Exception {
        Organization organization = saveOrganization();
        String apiKey = issueApiKey(organization);

        LocalDateTime base = LocalDateTime.of(2026, 2, 13, 12, 0);

        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).occurredAt(toInstant(base.plusMinutes(1))).build());
        eventRepository.save(Event.builder().eventType("view").path("/home").organization(organization).occurredAt(toInstant(base.plusMinutes(2))).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organization).occurredAt(toInstant(base.plusMinutes(3))).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organization).occurredAt(toInstant(base.plusMinutes(4))).build());
        eventRepository.save(Event.builder().eventType("view").path("/post/2").organization(organization).occurredAt(toInstant(base.plusMinutes(5))).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/paths")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
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
        Organization organization = saveOrganization();
        String apiKey = issueApiKey(organization);

        LocalDateTime base = LocalDateTime.of(2026, 2, 13, 12, 0);

        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).occurredAt(toInstant(base.plusMinutes(1))).build());
        eventRepository.save(Event.builder().eventType("view").path("/home").organization(organization).occurredAt(toInstant(base.plusMinutes(2))).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organization).occurredAt(toInstant(base.plusMinutes(3))).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organization).occurredAt(toInstant(base.plusMinutes(4))).build());
        eventRepository.save(Event.builder().eventType("view").path("/post/2").organization(organization).occurredAt(toInstant(base.plusMinutes(5))).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/paths")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
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
        Organization organizationA = saveOrganization("acme");
        Organization organizationB = saveOrganization("globex");
        String apiKey = issueApiKey(organizationA);

        LocalDateTime base = LocalDateTime.of(2026, 2, 13, 12, 0);

        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organizationA).occurredAt(toInstant(base.plusMinutes(1))).build());
        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organizationA).occurredAt(toInstant(base.plusMinutes(2))).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organizationA).occurredAt(toInstant(base.plusMinutes(3))).build());

        eventRepository.save(Event.builder().eventType("click").path("/hacked").organization(organizationB).occurredAt(toInstant(base.plusMinutes(4))).build());
        eventRepository.save(Event.builder().eventType("click").path("/hacked").organization(organizationB).occurredAt(toInstant(base.plusMinutes(5))).build());
        eventRepository.save(Event.builder().eventType("click").path("/hacked").organization(organizationB).occurredAt(toInstant(base.plusMinutes(6))).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/paths")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
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
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);
        EventUser eventUserA = saveEventUser(organization, "u-1001");
        EventUser eventUserB = saveEventUser(organization, "u-1002");

        LocalDateTime base = LocalDateTime.of(2026, 2, 13, 12, 0);

        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).eventUser(eventUserA).occurredAt(toInstant(base.plusMinutes(1))).build());
        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).eventUser(eventUserA).occurredAt(toInstant(base.plusMinutes(2))).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organization).eventUser(eventUserA).occurredAt(toInstant(base.plusMinutes(3))).build());
        eventRepository.save(Event.builder().eventType("click").path("/hacked").organization(organization).eventUser(eventUserB).occurredAt(toInstant(base.plusMinutes(4))).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/paths")
                                .param("externalUserId", eventUserA.getExternalUserId())
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
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
        Organization organization = saveOrganization();
        String apiKey = issueApiKey(organization);

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/paths")
                                .param("from", "2026-02-14T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "5")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void aggregatePaths_returnsBadRequest_whenTopIsOutOfRange() throws Exception {
        Organization organization = saveOrganization();
        String apiKey = issueApiKey(organization);

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/paths")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "0")
                )
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/paths")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "101")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void aggregatePaths_ignoresBlankExternalUserIdFilter() throws Exception {
        Organization organization = saveOrganization();
        String apiKey = issueApiKey(organization);

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/paths")
                                .param("externalUserId", " ")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "5")
                )
                .andExpect(status().isOk());
    }

    @Test
    void aggregatePaths_returnsBadRequest_whenDateTimeFormatIsInvalid() throws Exception {
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/paths")
                                .param("from", "invalid-date")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "5")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void aggregatePaths_returnsUnauthorized_whenApiKeyIsMissing() throws Exception {
        saveOrganization("acme");

        mockMvc.perform(
                        get("/api/v1/events/analytics/aggregates/paths")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "5")
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aggregatePaths_returnsUnauthorized_whenApiKeyIsInvalid() throws Exception {
        saveOrganization("acme");

        mockMvc.perform(
                        authorizedGet("ck_test_v1_invalid_deadbeef", "/api/v1/events/analytics/aggregates/paths")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "5")
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aggregateRoutes_groupsRawPathsByResolvedRouteKey() throws Exception {
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        saveRouteTemplate(organization, "/posts/{id}", "/posts/{id}", 100);
        saveRouteTemplate(organization, "/landing", "/landing", 10);

        LocalDateTime base = LocalDateTime.of(2026, 2, 13, 12, 0);
        eventRepository.save(Event.builder().eventType("click").path("/posts/1").organization(organization).occurredAt(toInstant(base.plusMinutes(1))).build());
        eventRepository.save(Event.builder().eventType("click").path("/posts/2").organization(organization).occurredAt(toInstant(base.plusMinutes(2))).build());
        eventRepository.save(Event.builder().eventType("click").path("/landing").organization(organization).occurredAt(toInstant(base.plusMinutes(3))).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/routes")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("eventType", "click")
                                .param("top", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.eventType").value("click"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].routeKey").value("/posts/{id}"))
                .andExpect(jsonPath("$.items[0].count").value(2))
                .andExpect(jsonPath("$.items[1].routeKey").value("/landing"))
                .andExpect(jsonPath("$.items[1].count").value(1));
    }

    @Test
    void aggregateRouteUniqueUsers_groupsDistinctUsersByResolvedRouteKey() throws Exception {
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);
        EventUser eventUserA = saveEventUser(organization, "u-1001");
        EventUser eventUserB = saveEventUser(organization, "u-1002");
        EventUser eventUserC = saveEventUser(organization, "u-1003");

        saveRouteTemplate(organization, "/posts/{id}", "/posts/{id}", 100);
        saveRouteTemplate(organization, "/landing", "/landing", 10);

        eventRepository.save(Event.builder().eventType("click").path("/posts/1").organization(organization).eventUser(eventUserA).occurredAt(Instant.parse("2026-02-13T00:10:00Z")).build());
        eventRepository.save(Event.builder().eventType("click").path("/posts/2").organization(organization).eventUser(eventUserB).occurredAt(Instant.parse("2026-02-13T00:20:00Z")).build());
        eventRepository.save(Event.builder().eventType("click").path("/posts/3").organization(organization).eventUser(eventUserA).occurredAt(Instant.parse("2026-02-13T00:30:00Z")).build());
        eventRepository.save(Event.builder().eventType("click").path("/landing").organization(organization).eventUser(eventUserC).occurredAt(Instant.parse("2026-02-13T00:40:00Z")).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/routes/unique-users")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("eventType", "click")
                                .param("top", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.eventType").value("click"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].routeKey").value("/posts/{id}"))
                .andExpect(jsonPath("$.items[0].uniqueUsers").value(2))
                .andExpect(jsonPath("$.items[1].routeKey").value("/landing"))
                .andExpect(jsonPath("$.items[1].uniqueUsers").value(1));
    }

    @Test
    void aggregateUnmatchedPaths_returnsOnlyRawPathsResolvedAsUnmatchedRoute() throws Exception {
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        saveRouteTemplate(organization, "/posts/{id}", "/posts/{id}", 100);

        eventRepository.save(Event.builder().eventType("click").path("/posts/1").organization(organization).occurredAt(Instant.parse("2026-02-13T00:10:00Z")).build());
        eventRepository.save(Event.builder().eventType("click").path("/unknown/b").organization(organization).occurredAt(Instant.parse("2026-02-13T00:20:00Z")).build());
        eventRepository.save(Event.builder().eventType("click").path("/unknown/a").organization(organization).occurredAt(Instant.parse("2026-02-13T00:30:00Z")).build());
        eventRepository.save(Event.builder().eventType("click").path("/unknown/b").organization(organization).occurredAt(Instant.parse("2026-02-13T00:40:00Z")).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/routes/unmatched-paths")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("eventType", "click")
                                .param("top", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.eventType").value("click"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].path").value("/unknown/b"))
                .andExpect(jsonPath("$.items[0].count").value(2))
                .andExpect(jsonPath("$.items[1].path").value("/unknown/a"))
                .andExpect(jsonPath("$.items[1].count").value(1));
    }

    @Test
    void aggregateRawEventTypes_returnsTopRawEventTypes() throws Exception {
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        EventUser eventUserA = saveEventUser(organization, "u-1001");
        EventUser eventUserB = saveEventUser(organization, "u-1002");

        eventRepository.save(Event.builder().eventType("button_click").path("/landing").organization(organization).eventUser(eventUserA).occurredAt(Instant.parse("2026-02-13T00:10:00Z")).build());
        eventRepository.save(Event.builder().eventType("button_click").path("/landing").organization(organization).eventUser(eventUserA).occurredAt(Instant.parse("2026-02-13T00:20:00Z")).build());
        eventRepository.save(Event.builder().eventType("page_view").path("/landing").organization(organization).eventUser(eventUserB).occurredAt(Instant.parse("2026-02-13T00:30:00Z")).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/raw-event-types")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.top").value(10))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].rawEventType").value("button_click"))
                .andExpect(jsonPath("$.items[0].count").value(2))
                .andExpect(jsonPath("$.items[1].rawEventType").value("page_view"))
                .andExpect(jsonPath("$.items[1].count").value(1));
    }

    @Test
    void aggregateCanonicalEventTypes_groupsRawEventTypesByMappingAndFallback() throws Exception {
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        saveEventTypeMapping(organization, "button_click", "click");
        saveEventTypeMapping(organization, "post_click", "click");

        EventUser eventUserA = saveEventUser(organization, "u-1001");
        EventUser eventUserB = saveEventUser(organization, "u-1002");

        eventRepository.save(Event.builder().eventType("button_click").path("/landing").organization(organization).eventUser(eventUserA).occurredAt(Instant.parse("2026-02-13T00:10:00Z")).build());
        eventRepository.save(Event.builder().eventType("post_click").path("/landing").organization(organization).eventUser(eventUserA).occurredAt(Instant.parse("2026-02-13T00:20:00Z")).build());
        eventRepository.save(Event.builder().eventType("mystery_event").path("/landing").organization(organization).eventUser(eventUserB).occurredAt(Instant.parse("2026-02-13T00:30:00Z")).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/event-types")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.top").value(10))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].canonicalEventType").value("click"))
                .andExpect(jsonPath("$.items[0].count").value(2))
                .andExpect(jsonPath("$.items[1].canonicalEventType").value("UNMAPPED_EVENT_TYPE"))
                .andExpect(jsonPath("$.items[1].count").value(1));
    }

    @Test
    void aggregateCanonicalEventTypeUniqueUsers_groupsDistinctUsersByResolvedCanonicalEventType() throws Exception {
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        saveEventTypeMapping(organization, "button_click", "click");
        saveEventTypeMapping(organization, "post_click", "click");
        saveEventTypeMapping(organization, "page_view", "view");

        EventUser eventUserA = saveEventUser(organization, "u-1001");
        EventUser eventUserB = saveEventUser(organization, "u-1002");
        EventUser eventUserC = saveEventUser(organization, "u-1003");

        eventRepository.save(Event.builder().eventType("button_click").path("/landing").organization(organization).eventUser(eventUserA).occurredAt(Instant.parse("2026-02-13T00:10:00Z")).build());
        eventRepository.save(Event.builder().eventType("post_click").path("/landing").organization(organization).eventUser(eventUserB).occurredAt(Instant.parse("2026-02-13T00:20:00Z")).build());
        eventRepository.save(Event.builder().eventType("button_click").path("/landing").organization(organization).eventUser(eventUserA).occurredAt(Instant.parse("2026-02-13T00:30:00Z")).build());
        eventRepository.save(Event.builder().eventType("page_view").path("/landing").organization(organization).eventUser(eventUserC).occurredAt(Instant.parse("2026-02-13T00:40:00Z")).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/event-types/unique-users")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.top").value(10))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].canonicalEventType").value("click"))
                .andExpect(jsonPath("$.items[0].uniqueUsers").value(2))
                .andExpect(jsonPath("$.items[1].canonicalEventType").value("view"))
                .andExpect(jsonPath("$.items[1].uniqueUsers").value(1));
    }

    @Test
    void aggregateRouteEventTypes_groupsByResolvedRouteKeyAndCanonicalEventType() throws Exception {
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        saveRouteTemplate(organization, "/posts/{id}", "/posts/{id}", 100);
        saveRouteTemplate(organization, "/landing", "/landing", 10);
        saveEventTypeMapping(organization, "button_click", "click");
        saveEventTypeMapping(organization, "post_click", "click");
        saveEventTypeMapping(organization, "page_view", "view");

        EventUser eventUserA = saveEventUser(organization, "u-1001");
        EventUser eventUserB = saveEventUser(organization, "u-1002");

        eventRepository.save(Event.builder().eventType("button_click").path("/posts/1").organization(organization).eventUser(eventUserA).occurredAt(Instant.parse("2026-02-13T00:10:00Z")).build());
        eventRepository.save(Event.builder().eventType("post_click").path("/posts/2").organization(organization).eventUser(eventUserA).occurredAt(Instant.parse("2026-02-13T00:20:00Z")).build());
        eventRepository.save(Event.builder().eventType("page_view").path("/landing").organization(organization).eventUser(eventUserB).occurredAt(Instant.parse("2026-02-13T00:30:00Z")).build());
        eventRepository.save(Event.builder().eventType("mystery_event").path("/landing").organization(organization).eventUser(eventUserB).occurredAt(Instant.parse("2026-02-13T00:40:00Z")).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/route-event-types")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.top").value(10))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].routeKey").value("/posts/{id}"))
                .andExpect(jsonPath("$.items[0].canonicalEventType").value("click"))
                .andExpect(jsonPath("$.items[0].count").value(2))
                .andExpect(jsonPath("$.items[1].routeKey").value("/landing"))
                .andExpect(jsonPath("$.items[1].canonicalEventType").value("UNMAPPED_EVENT_TYPE"))
                .andExpect(jsonPath("$.items[1].count").value(1))
                .andExpect(jsonPath("$.items[2].routeKey").value("/landing"))
                .andExpect(jsonPath("$.items[2].canonicalEventType").value("view"))
                .andExpect(jsonPath("$.items[2].count").value(1));
    }
}
