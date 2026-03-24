package com.clickchecker.analytics.trend.controller;

import com.clickchecker.event.entity.Event;
import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.eventtype.entity.EventTypeMapping;
import com.clickchecker.eventtype.repository.EventTypeMappingRepository;
import com.clickchecker.eventuser.entity.EventUser;
import com.clickchecker.eventuser.repository.EventUserRepository;
import com.clickchecker.organization.entity.Organization;
import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.organization.service.ApiKeyService;
import com.clickchecker.organizationmember.repository.OrganizationMemberRepository;
import com.clickchecker.route.entity.RouteTemplate;
import com.clickchecker.route.repository.RouteTemplateRepository;
import com.clickchecker.web.filter.ApiKeyAuthFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@Tag("postgres")
@ActiveProfiles("ci")
@SpringBootTest
@AutoConfigureMockMvc
class TrendAnalyticsControllerPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("click_checker_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

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

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @BeforeEach
    void cleanupBeforeEach() {
        cleanup();
    }

    @AfterEach
    void cleanupAfterEach() {
        cleanup();
    }

    @Test
    void aggregateRouteTimeBuckets_groupsByResolvedRouteKeyWithinEachBucket() throws Exception {
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        saveRouteTemplate(organization, "/posts/{id}", "/posts/{id}", 100);
        saveRouteTemplate(organization, "/landing", "/landing", 10);

        eventRepository.save(Event.builder().eventType("click").path("/posts/1").organization(organization).occurredAt(Instant.parse("2026-02-13T10:10:00Z")).build());
        eventRepository.save(Event.builder().eventType("click").path("/posts/2").organization(organization).occurredAt(Instant.parse("2026-02-13T10:20:00Z")).build());
        eventRepository.save(Event.builder().eventType("click").path("/landing").organization(organization).occurredAt(Instant.parse("2026-02-13T11:05:00Z")).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/route-time-buckets")
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
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/event-type-time-buckets")
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
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        saveRouteTemplate(organization, "/posts/{id}", "/posts/{id}", 100);
        saveRouteTemplate(organization, "/landing", "/landing", 10);
        saveEventTypeMapping(organization, "button_click", "click");
        saveEventTypeMapping(organization, "page_view", "view");

        eventRepository.save(Event.builder().eventType("button_click").path("/posts/1").organization(organization).occurredAt(Instant.parse("2026-02-13T10:10:00Z")).build());
        eventRepository.save(Event.builder().eventType("page_view").path("/landing").organization(organization).occurredAt(Instant.parse("2026-02-13T11:15:00Z")).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/route-event-type-time-buckets")
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
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        LocalDateTime base = LocalDateTime.of(2026, 2, 13, 10, 0);
        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).occurredAt(toInstant(base.plusMinutes(1))).build());
        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).occurredAt(toInstant(base.plusMinutes(25))).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organization).occurredAt(toInstant(base.plusHours(1).plusMinutes(5))).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/time-buckets")
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
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).occurredAt(toInstant(LocalDateTime.of(2026, 2, 13, 10, 5))).build());
        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).occurredAt(toInstant(LocalDateTime.of(2026, 2, 13, 13, 10))).build());
        eventRepository.save(Event.builder().eventType("click").path("/post/1").organization(organization).occurredAt(toInstant(LocalDateTime.of(2026, 2, 14, 9, 15))).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/time-buckets")
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
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        eventRepository.save(Event.builder()
                .eventType("click")
                .path("/home")
                .organization(organization)
                .occurredAt(Instant.parse("2026-02-13T16:00:00Z"))
                .build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/time-buckets")
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
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);
        EventUser eventUserA = saveEventUser(organization, "u-1001");
        EventUser eventUserB = saveEventUser(organization, "u-1002");

        LocalDateTime base = LocalDateTime.of(2026, 2, 13, 10, 0);
        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).eventUser(eventUserA).occurredAt(toInstant(base.plusMinutes(1))).build());
        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).eventUser(eventUserA).occurredAt(toInstant(base.plusMinutes(5))).build());
        eventRepository.save(Event.builder().eventType("click").path("/home").organization(organization).eventUser(eventUserB).occurredAt(toInstant(base.plusMinutes(10))).build());

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/time-buckets")
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

    private void cleanup() {
        eventRepository.deleteAll();
        eventUserRepository.deleteAll();
        eventTypeMappingRepository.deleteAll();
        routeTemplateRepository.deleteAll();
        organizationMemberRepository.deleteAll();
        organizationRepository.deleteAll();
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

    private void saveRouteTemplate(Organization organization, String template, String routeKey, int priority) {
        routeTemplateRepository.save(
                RouteTemplate.builder()
                        .organization(organization)
                        .template(template)
                        .routeKey(routeKey)
                        .priority(priority)
                        .active(true)
                        .build()
        );
    }

    private void saveEventTypeMapping(
            Organization organization,
            String rawEventType,
            String canonicalEventType
    ) {
        eventTypeMappingRepository.save(
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
