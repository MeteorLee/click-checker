package com.clickchecker.event.controller;

import com.clickchecker.event.entity.Event;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.eventuser.entity.EventUser;
import com.clickchecker.eventuser.repository.EventUserRepository;
import com.clickchecker.eventtype.entity.EventTypeMapping;
import com.clickchecker.eventtype.repository.EventTypeMappingRepository;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.organization.service.ApiKeyService;
import com.clickchecker.route.entity.RouteTemplate;
import com.clickchecker.route.repository.RouteTemplateRepository;
import com.clickchecker.web.filter.ApiKeyAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private RouteTemplateRepository routeTemplateRepository;

    @Autowired
    private EventTypeMappingRepository eventTypeMappingRepository;

    @Test
    void aggregatePaths_returnsTopNPaths_withoutEventTypeFilter() throws Exception {
        cleanup();

        Organization organization = saveOrganization();
        String apiKey = issueApiKey(organization);

        LocalDateTime base = LocalDateTime.of(2026, 2, 13, 12, 0);

        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).occurredAt(toInstant(base.plusMinutes(1))).build());
        eventRepository.save(Event.builder().eventType("view").path("/home").organization(organization).occurredAt(toInstant(base.plusMinutes(2))).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organization).occurredAt(toInstant(base.plusMinutes(3))).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organization).occurredAt(toInstant(base.plusMinutes(4))).build());
        eventRepository.save(Event.builder().eventType("view").path("/post/2").organization(organization).occurredAt(toInstant(base.plusMinutes(5))).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/events/aggregates/paths")
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
        cleanup();

        Organization organization = saveOrganization();
        String apiKey = issueApiKey(organization);

        LocalDateTime base = LocalDateTime.of(2026, 2, 13, 12, 0);

        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).occurredAt(toInstant(base.plusMinutes(1))).build());
        eventRepository.save(Event.builder().eventType("view").path("/home").organization(organization).occurredAt(toInstant(base.plusMinutes(2))).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organization).occurredAt(toInstant(base.plusMinutes(3))).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organization).occurredAt(toInstant(base.plusMinutes(4))).build());
        eventRepository.save(Event.builder().eventType("view").path("/post/2").organization(organization).occurredAt(toInstant(base.plusMinutes(5))).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/events/aggregates/paths")
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
        cleanup();

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
                        authorizedGet(apiKey, "/api/events/aggregates/paths")
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
        cleanup();

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
                        authorizedGet(apiKey, "/api/events/aggregates/paths")
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
        cleanup();
        Organization organization = saveOrganization();
        String apiKey = issueApiKey(organization);

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/events/aggregates/paths")
                                .param("from", "2026-02-14T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "5")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void aggregatePaths_returnsBadRequest_whenTopIsOutOfRange() throws Exception {
        cleanup();
        Organization organization = saveOrganization();
        String apiKey = issueApiKey(organization);

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/events/aggregates/paths")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "0")
                )
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/events/aggregates/paths")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "101")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void aggregatePaths_ignoresBlankExternalUserIdFilter() throws Exception {
        cleanup();
        Organization organization = saveOrganization();
        String apiKey = issueApiKey(organization);

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/events/aggregates/paths")
                                .param("externalUserId", " ")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "5")
                )
                .andExpect(status().isOk());
    }

    @Test
    void aggregatePaths_returnsBadRequest_whenDateTimeFormatIsInvalid() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/events/aggregates/paths")
                                .param("from", "invalid-date")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "5")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void aggregatePaths_returnsUnauthorized_whenApiKeyIsMissing() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");

        mockMvc.perform(
                        get("/api/events/aggregates/paths")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "5")
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aggregatePaths_returnsUnauthorized_whenApiKeyIsInvalid() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");

        mockMvc.perform(
                        authorizedGet("ck_test_v1_invalid_deadbeef", "/api/events/aggregates/paths")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("top", "5")
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aggregateRoutes_groupsRawPathsByResolvedRouteKey() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        saveRouteTemplate(organization, "/posts/{id}", "/posts/{id}", 100);
        saveRouteTemplate(organization, "/landing", "/landing", 10);

        LocalDateTime base = LocalDateTime.of(2026, 2, 13, 12, 0);
        eventRepository.save(Event.builder().eventType("click").path("/posts/1").organization(organization).occurredAt(toInstant(base.plusMinutes(1))).build());
        eventRepository.save(Event.builder().eventType("click").path("/posts/2").organization(organization).occurredAt(toInstant(base.plusMinutes(2))).build());
        eventRepository.save(Event.builder().eventType("click").path("/landing").organization(organization).occurredAt(toInstant(base.plusMinutes(3))).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/events/aggregates/routes")
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
    void aggregateOverview_returnsSummaryWithComparisonAndTopBreakdowns() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);
        EventUser eventUserA = saveEventUser(organization, "u-1001");
        EventUser eventUserB = saveEventUser(organization, "u-1002");

        saveRouteTemplate(organization, "/posts/{id}", "/posts/{id}", 100);
        saveRouteTemplate(organization, "/landing", "/landing", 10);
        saveEventTypeMapping(organization, "button_click", "click");
        saveEventTypeMapping(organization, "page_view", "view");

        eventRepository.save(Event.builder().eventType("button_click").path("/posts/1").organization(organization).eventUser(eventUserA).occurredAt(Instant.parse("2026-02-13T00:10:00Z")).build());
        eventRepository.save(Event.builder().eventType("button_click").path("/posts/2").organization(organization).eventUser(eventUserA).occurredAt(Instant.parse("2026-02-13T00:20:00Z")).build());
        eventRepository.save(Event.builder().eventType("page_view").path("/landing").organization(organization).eventUser(eventUserB).occurredAt(Instant.parse("2026-02-13T00:30:00Z")).build());

        eventRepository.save(Event.builder().eventType("button_click").path("/posts/3").organization(organization).eventUser(eventUserA).occurredAt(Instant.parse("2026-02-12T00:10:00Z")).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/events/aggregates/overview")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.totalEvents").value(3))
                .andExpect(jsonPath("$.uniqueUsers").value(2))
                .andExpect(jsonPath("$.identifiedEventRate").isNumber())
                .andExpect(jsonPath("$.comparison.current").value(3))
                .andExpect(jsonPath("$.comparison.previous").value(1))
                .andExpect(jsonPath("$.comparison.delta").value(2))
                .andExpect(jsonPath("$.comparison.deltaRate").value(2.0))
                .andExpect(jsonPath("$.comparison.hasPreviousBaseline").value(true))
                .andExpect(jsonPath("$.topRoutes.length()").value(2))
                .andExpect(jsonPath("$.topRoutes[0].routeKey").value("/posts/{id}"))
                .andExpect(jsonPath("$.topRoutes[0].count").value(2))
                .andExpect(jsonPath("$.topRoutes[1].routeKey").value("/landing"))
                .andExpect(jsonPath("$.topRoutes[1].count").value(1))
                .andExpect(jsonPath("$.topEventTypes.length()").value(2))
                .andExpect(jsonPath("$.topEventTypes[0].eventType").value("click"))
                .andExpect(jsonPath("$.topEventTypes[0].count").value(2))
                .andExpect(jsonPath("$.topEventTypes[1].eventType").value("view"))
                .andExpect(jsonPath("$.topEventTypes[1].count").value(1));
    }

    @Test
    void aggregateRawEventTypes_returnsTopRawEventTypes() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        EventUser eventUserA = saveEventUser(organization, "u-1001");
        EventUser eventUserB = saveEventUser(organization, "u-1002");

        eventRepository.save(Event.builder().eventType("button_click").path("/landing").organization(organization).eventUser(eventUserA).occurredAt(Instant.parse("2026-02-13T00:10:00Z")).build());
        eventRepository.save(Event.builder().eventType("button_click").path("/landing").organization(organization).eventUser(eventUserA).occurredAt(Instant.parse("2026-02-13T00:20:00Z")).build());
        eventRepository.save(Event.builder().eventType("page_view").path("/landing").organization(organization).eventUser(eventUserB).occurredAt(Instant.parse("2026-02-13T00:30:00Z")).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/events/aggregates/raw-event-types")
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
        cleanup();
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
                        authorizedGet(apiKey, "/api/events/aggregates/event-types")
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
    void aggregateRouteEventTypes_groupsByResolvedRouteKeyAndCanonicalEventType() throws Exception {
        cleanup();
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
                        authorizedGet(apiKey, "/api/events/aggregates/route-event-types")
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

    @Test
    void aggregateRouteTimeBuckets_groupsByResolvedRouteKeyWithinEachBucket() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        saveRouteTemplate(organization, "/posts/{id}", "/posts/{id}", 100);
        saveRouteTemplate(organization, "/landing", "/landing", 10);

        eventRepository.save(Event.builder().eventType("click").path("/posts/1").organization(organization).occurredAt(Instant.parse("2026-02-13T10:10:00Z")).build());
        eventRepository.save(Event.builder().eventType("click").path("/posts/2").organization(organization).occurredAt(Instant.parse("2026-02-13T10:20:00Z")).build());
        eventRepository.save(Event.builder().eventType("click").path("/landing").organization(organization).occurredAt(Instant.parse("2026-02-13T11:05:00Z")).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/events/aggregates/route-time-buckets")
                                .param("from", "2026-02-13T10:00:00Z")
                                .param("to", "2026-02-13T12:00:00Z")
                                .param("eventType", "click")
                                .param("bucket", "HOUR")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.timezone").value("UTC"))
                .andExpect(jsonPath("$.bucket").value("HOUR"))
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[0].routeKey").value("/landing"))
                .andExpect(jsonPath("$.items[0].bucketStart").value("2026-02-13T10:00:00Z"))
                .andExpect(jsonPath("$.items[0].count").value(0))
                .andExpect(jsonPath("$.items[1].routeKey").value("/posts/{id}"))
                .andExpect(jsonPath("$.items[1].bucketStart").value("2026-02-13T10:00:00Z"))
                .andExpect(jsonPath("$.items[1].count").value(2))
                .andExpect(jsonPath("$.items[2].routeKey").value("/landing"))
                .andExpect(jsonPath("$.items[2].bucketStart").value("2026-02-13T11:00:00Z"))
                .andExpect(jsonPath("$.items[2].count").value(1))
                .andExpect(jsonPath("$.items[3].routeKey").value("/posts/{id}"))
                .andExpect(jsonPath("$.items[3].bucketStart").value("2026-02-13T11:00:00Z"))
                .andExpect(jsonPath("$.items[3].count").value(0));
    }

    @Test
    void aggregateEventTypeTimeBuckets_groupsByResolvedCanonicalEventTypeWithinEachBucket() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        saveEventTypeMapping(organization, "button_click", "click");
        saveEventTypeMapping(organization, "post_click", "click");
        saveEventTypeMapping(organization, "page_view", "view");

        eventRepository.save(Event.builder().eventType("button_click").path("/posts/1").organization(organization).occurredAt(Instant.parse("2026-02-13T10:10:00Z")).build());
        eventRepository.save(Event.builder().eventType("post_click").path("/posts/2").organization(organization).occurredAt(Instant.parse("2026-02-13T10:20:00Z")).build());
        eventRepository.save(Event.builder().eventType("page_view").path("/landing").organization(organization).occurredAt(Instant.parse("2026-02-13T11:05:00Z")).build());
        eventRepository.save(Event.builder().eventType("mystery_event").path("/landing").organization(organization).occurredAt(Instant.parse("2026-02-13T11:15:00Z")).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/events/aggregates/event-type-time-buckets")
                                .param("from", "2026-02-13T10:00:00Z")
                                .param("to", "2026-02-13T12:00:00Z")
                                .param("bucket", "HOUR")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.timezone").value("UTC"))
                .andExpect(jsonPath("$.bucket").value("HOUR"))
                .andExpect(jsonPath("$.items.length()").value(6))
                .andExpect(jsonPath("$.items[0].canonicalEventType").value("UNMAPPED_EVENT_TYPE"))
                .andExpect(jsonPath("$.items[0].bucketStart").value("2026-02-13T10:00:00Z"))
                .andExpect(jsonPath("$.items[0].count").value(0))
                .andExpect(jsonPath("$.items[1].canonicalEventType").value("click"))
                .andExpect(jsonPath("$.items[1].bucketStart").value("2026-02-13T10:00:00Z"))
                .andExpect(jsonPath("$.items[1].count").value(2))
                .andExpect(jsonPath("$.items[2].canonicalEventType").value("view"))
                .andExpect(jsonPath("$.items[2].bucketStart").value("2026-02-13T10:00:00Z"))
                .andExpect(jsonPath("$.items[2].count").value(0))
                .andExpect(jsonPath("$.items[3].canonicalEventType").value("UNMAPPED_EVENT_TYPE"))
                .andExpect(jsonPath("$.items[3].bucketStart").value("2026-02-13T11:00:00Z"))
                .andExpect(jsonPath("$.items[3].count").value(1))
                .andExpect(jsonPath("$.items[4].canonicalEventType").value("click"))
                .andExpect(jsonPath("$.items[4].bucketStart").value("2026-02-13T11:00:00Z"))
                .andExpect(jsonPath("$.items[4].count").value(0))
                .andExpect(jsonPath("$.items[5].canonicalEventType").value("view"))
                .andExpect(jsonPath("$.items[5].bucketStart").value("2026-02-13T11:00:00Z"))
                .andExpect(jsonPath("$.items[5].count").value(1));
    }

    @Test
    void aggregateRouteEventTypeTimeBuckets_groupsByResolvedAxesWithinEachBucket() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        saveRouteTemplate(organization, "/posts/{id}", "/posts/{id}", 100);
        saveRouteTemplate(organization, "/landing", "/landing", 10);
        saveEventTypeMapping(organization, "button_click", "click");
        saveEventTypeMapping(organization, "page_view", "view");

        eventRepository.save(Event.builder().eventType("button_click").path("/posts/1").organization(organization).occurredAt(Instant.parse("2026-02-13T10:10:00Z")).build());
        eventRepository.save(Event.builder().eventType("page_view").path("/landing").organization(organization).occurredAt(Instant.parse("2026-02-13T11:15:00Z")).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/events/aggregates/route-event-type-time-buckets")
                                .param("from", "2026-02-13T10:00:00Z")
                                .param("to", "2026-02-13T12:00:00Z")
                                .param("bucket", "HOUR")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.timezone").value("UTC"))
                .andExpect(jsonPath("$.bucket").value("HOUR"))
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[0].routeKey").value("/landing"))
                .andExpect(jsonPath("$.items[0].canonicalEventType").value("view"))
                .andExpect(jsonPath("$.items[0].bucketStart").value("2026-02-13T10:00:00Z"))
                .andExpect(jsonPath("$.items[0].count").value(0))
                .andExpect(jsonPath("$.items[1].routeKey").value("/posts/{id}"))
                .andExpect(jsonPath("$.items[1].canonicalEventType").value("click"))
                .andExpect(jsonPath("$.items[1].bucketStart").value("2026-02-13T10:00:00Z"))
                .andExpect(jsonPath("$.items[1].count").value(1))
                .andExpect(jsonPath("$.items[2].routeKey").value("/landing"))
                .andExpect(jsonPath("$.items[2].canonicalEventType").value("view"))
                .andExpect(jsonPath("$.items[2].bucketStart").value("2026-02-13T11:00:00Z"))
                .andExpect(jsonPath("$.items[2].count").value(1))
                .andExpect(jsonPath("$.items[3].routeKey").value("/posts/{id}"))
                .andExpect(jsonPath("$.items[3].canonicalEventType").value("click"))
                .andExpect(jsonPath("$.items[3].bucketStart").value("2026-02-13T11:00:00Z"))
                .andExpect(jsonPath("$.items[3].count").value(0));
    }

    @Test
    void aggregateTimeBuckets_groupsByHour() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        LocalDateTime base = LocalDateTime.of(2026, 2, 13, 10, 0);
        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).occurredAt(toInstant(base.plusMinutes(1))).build());
        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).occurredAt(toInstant(base.plusMinutes(25))).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organization).occurredAt(toInstant(base.plusHours(1).plusMinutes(5))).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/events/aggregates/time-buckets")
                                .param("from", "2026-02-13T10:00:00Z")
                                .param("to", "2026-02-13T12:00:00Z")
                                .param("bucket", "HOUR")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.timezone").value("UTC"))
                .andExpect(jsonPath("$.bucket").value("HOUR"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].bucketStart").value("2026-02-13T10:00:00Z"))
                .andExpect(jsonPath("$.items[0].count").value(2))
                .andExpect(jsonPath("$.items[1].bucketStart").value("2026-02-13T11:00:00Z"))
                .andExpect(jsonPath("$.items[1].count").value(1));
    }

    @Test
    void aggregateTimeBuckets_groupsByDay() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).occurredAt(toInstant(LocalDateTime.of(2026, 2, 13, 10, 5))).build());
        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).occurredAt(toInstant(LocalDateTime.of(2026, 2, 13, 13, 10))).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organization).occurredAt(toInstant(LocalDateTime.of(2026, 2, 14, 9, 15))).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/events/aggregates/time-buckets")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-15T00:00:00Z")
                                .param("bucket", "DAY")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("UTC"))
                .andExpect(jsonPath("$.bucket").value("DAY"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].bucketStart").value("2026-02-13T00:00:00Z"))
                .andExpect(jsonPath("$.items[0].count").value(2))
                .andExpect(jsonPath("$.items[1].bucketStart").value("2026-02-14T00:00:00Z"))
                .andExpect(jsonPath("$.items[1].count").value(1));
    }

    @Test
    void aggregateTimeBuckets_usesTimezoneAndFillsMissingBuckets() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        eventRepository.save(Event.builder()
                .eventType("click")
                .path("/home")
                .organization(organization)
                .occurredAt(Instant.parse("2026-02-13T16:00:00Z"))
                .build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/events/aggregates/time-buckets")
                                .param("from", "2026-02-13T15:00:00Z")
                                .param("to", "2026-02-15T15:00:00Z")
                                .param("timezone", "Asia/Seoul")
                                .param("bucket", "DAY")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.bucket").value("DAY"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].bucketStart").value("2026-02-13T15:00:00Z"))
                .andExpect(jsonPath("$.items[0].count").value(1))
                .andExpect(jsonPath("$.items[1].bucketStart").value("2026-02-14T15:00:00Z"))
                .andExpect(jsonPath("$.items[1].count").value(0));
    }

    @Test
    void aggregateTimeBuckets_filtersByExternalUserId() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);
        EventUser eventUserA = saveEventUser(organization, "u-1001");
        EventUser eventUserB = saveEventUser(organization, "u-1002");

        LocalDateTime base = LocalDateTime.of(2026, 2, 13, 10, 0);
        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).eventUser(eventUserA).occurredAt(toInstant(base.plusMinutes(1))).build());
        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).eventUser(eventUserA).occurredAt(toInstant(base.plusMinutes(5))).build());
        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).eventUser(eventUserB).occurredAt(toInstant(base.plusMinutes(10))).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/events/aggregates/time-buckets")
                                .param("externalUserId", "u-1001")
                                .param("from", "2026-02-13T10:00:00Z")
                                .param("to", "2026-02-13T11:00:00Z")
                                .param("bucket", "HOUR")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalUserId").value("u-1001"))
                .andExpect(jsonPath("$.timezone").value("UTC"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].bucketStart").value("2026-02-13T10:00:00Z"))
                .andExpect(jsonPath("$.items[0].count").value(2));
    }

    @Test
    void aggregateTimeBuckets_returnsBadRequest_whenBucketIsInvalid() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/events/aggregates/time-buckets")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("bucket", "WEEK")
                )
                .andExpect(status().isBadRequest());
    }

    private void cleanup() {
        eventRepository.deleteAll();
        eventUserRepository.deleteAll();
        eventTypeMappingRepository.deleteAll();
        routeTemplateRepository.deleteAll();
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

    private RouteTemplate saveRouteTemplate(Organization organization, String template, String routeKey, int priority) {
        return routeTemplateRepository.save(
                RouteTemplate.builder()
                        .organization(organization)
                        .template(template)
                        .routeKey(routeKey)
                        .priority(priority)
                        .active(true)
                        .build()
        );
    }

    private EventTypeMapping saveEventTypeMapping(
            Organization organization,
            String rawEventType,
            String canonicalEventType
    ) {
        return eventTypeMappingRepository.save(
                EventTypeMapping.builder()
                        .organization(organization)
                        .rawEventType(rawEventType)
                        .canonicalEventType(canonicalEventType)
                        .active(true)
                        .build()
        );
    }

    private String issueApiKey(Organization organization) {
        return apiKeyService.issueForOrganization(organization.getId()).apiKey();
    }

    private MockHttpServletRequestBuilder authorizedGet(String apiKey, String path) {
        return get(path).header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey);
    }

    private Instant toInstant(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }
}
