package com.clickchecker.analytics.trend.controller;

import com.clickchecker.organization.entity.Organization;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("ci")
@SpringBootTest
@AutoConfigureMockMvc
class TrendAnalyticsControllerIntegrationTest extends com.clickchecker.analytics.support.AnalyticsControllerIntegrationTestSupport {

    @Test
    void aggregateTimeBuckets_returnsBadRequest_whenBucketIsInvalid() throws Exception {
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/time-buckets")
                                .param("from", "2026-02-13T00:00:00Z")
                                .param("to", "2026-02-14T00:00:00Z")
                                .param("bucket", "WEEK")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void aggregateTimeBuckets_returnsBadRequest_whenBucketRangeExceedsLimit() throws Exception {
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/time-buckets")
                                .param("from", "2026-02-01T00:00:00Z")
                                .param("to", "2026-02-17T00:00:00Z")
                                .param("bucket", "HOUR")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void aggregateRouteEventTypeTimeBuckets_returnsBadRequest_whenBucketRangeExceedsLimit() throws Exception {
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        mockMvc.perform(
                        authorizedGet(apiKey, "/api/v1/events/analytics/aggregates/route-event-type-time-buckets")
                                .param("from", "2026-02-01T00:00:00Z")
                                .param("to", "2026-02-17T00:00:00Z")
                                .param("bucket", "HOUR")
                )
                .andExpect(status().isBadRequest());
    }
}
