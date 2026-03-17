package com.clickchecker.route.controller;

import com.clickchecker.event.repository.EventRepository;
import com.clickchecker.eventtype.repository.EventTypeMappingRepository;
import com.clickchecker.eventuser.repository.EventUserRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("ci")
@SpringBootTest
@AutoConfigureMockMvc
class RouteTemplateControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RouteTemplateRepository routeTemplateRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventUserRepository eventUserRepository;

    @Autowired
    private EventTypeMappingRepository eventTypeMappingRepository;

    @Test
    void create_savesRouteTemplateUnderAuthenticatedOrganization() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);

        mockMvc.perform(
                        authorizedPost(apiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "template": "/posts/{id}",
                                          "routeKey": "/posts/{id}",
                                          "priority": 100
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.template").value("/posts/{id}"))
                .andExpect(jsonPath("$.routeKey").value("/posts/{id}"))
                .andExpect(jsonPath("$.priority").value(100))
                .andExpect(jsonPath("$.active").value(true));

        assertThat(routeTemplateRepository.count()).isEqualTo(1);
        assertThat(routeTemplateRepository.findAll().getFirst().getOrganization().getId())
                .isEqualTo(organization.getId());
    }

    @Test
    void create_returnsUnauthorized_whenApiKeyIsMissing() throws Exception {
        cleanup();
        saveOrganization("acme");

        mockMvc.perform(
                        post("/api/events/route-templates")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "template": "/posts/{id}",
                                          "routeKey": "/posts/{id}",
                                          "priority": 100
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAll_returnsOnlyAuthenticatedOrganizationTemplates_inPriorityOrder() throws Exception {
        cleanup();
        Organization organizationA = saveOrganization("acme");
        Organization organizationB = saveOrganization("globex");
        String apiKey = issueApiKey(organizationA);

        saveRouteTemplate(organizationA, "/posts/{id}", "/posts/{id}", 100);
        saveRouteTemplate(organizationA, "/landing", "/landing", 10);
        saveRouteTemplate(organizationB, "/hidden", "/hidden", 999);

        mockMvc.perform(authorizedGet(apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].template").value("/posts/{id}"))
                .andExpect(jsonPath("$.items[0].priority").value(100))
                .andExpect(jsonPath("$.items[1].template").value("/landing"))
                .andExpect(jsonPath("$.items[1].priority").value(10));
    }

    @Test
    void update_modifiesTemplateUnderAuthenticatedOrganization() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);
        RouteTemplate routeTemplate = saveRouteTemplate(organization, "/posts/{id}", "/posts/{id}", 100);

        mockMvc.perform(
                        authorizedPut(apiKey, routeTemplate.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "template": "/articles/{id}",
                                          "routeKey": "/articles/{id}",
                                          "priority": 50
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(routeTemplate.getId()))
                .andExpect(jsonPath("$.template").value("/articles/{id}"))
                .andExpect(jsonPath("$.routeKey").value("/articles/{id}"))
                .andExpect(jsonPath("$.priority").value(50))
                .andExpect(jsonPath("$.active").value(true));

        RouteTemplate updated = routeTemplateRepository.findById(routeTemplate.getId()).orElseThrow();
        assertThat(updated.getTemplate()).isEqualTo("/articles/{id}");
        assertThat(updated.getRouteKey()).isEqualTo("/articles/{id}");
        assertThat(updated.getPriority()).isEqualTo(50);
    }

    @Test
    void updateActive_togglesActiveFlagUnderAuthenticatedOrganization() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);
        RouteTemplate routeTemplate = saveRouteTemplate(organization, "/posts/{id}", "/posts/{id}", 100);

        mockMvc.perform(
                        authorizedPutActive(apiKey, routeTemplate.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "active": false
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(routeTemplate.getId()))
                .andExpect(jsonPath("$.active").value(false));

        RouteTemplate updated = routeTemplateRepository.findById(routeTemplate.getId()).orElseThrow();
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    void delete_removesTemplateUnderAuthenticatedOrganization() throws Exception {
        cleanup();
        Organization organization = saveOrganization("acme");
        String apiKey = issueApiKey(organization);
        RouteTemplate routeTemplate = saveRouteTemplate(organization, "/posts/{id}", "/posts/{id}", 100);

        mockMvc.perform(authorizedDelete(apiKey, routeTemplate.getId()))
                .andExpect(status().isNoContent());

        assertThat(routeTemplateRepository.findById(routeTemplate.getId())).isEmpty();
    }

    private void cleanup() {
        eventRepository.deleteAll();
        eventTypeMappingRepository.deleteAll();
        routeTemplateRepository.deleteAll();
        eventUserRepository.deleteAll();
        organizationRepository.deleteAll();
    }

    private Organization saveOrganization(String name) {
        return organizationRepository.save(
                Organization.builder()
                        .name(name)
                        .build()
        );
    }

    private String issueApiKey(Organization organization) {
        return apiKeyService.issueForOrganization(organization.getId()).apiKey();
    }

    private RouteTemplate saveRouteTemplate(
            Organization organization,
            String template,
            String routeKey,
            int priority
    ) {
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

    private MockHttpServletRequestBuilder authorizedGet(String apiKey) {
        return get("/api/events/route-templates")
                .header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey);
    }

    private MockHttpServletRequestBuilder authorizedPut(String apiKey, Long routeTemplateId) {
        return put("/api/events/route-templates/{routeTemplateId}", routeTemplateId)
                .header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey);
    }

    private MockHttpServletRequestBuilder authorizedPutActive(String apiKey, Long routeTemplateId) {
        return put("/api/events/route-templates/{routeTemplateId}/active", routeTemplateId)
                .header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey);
    }

    private MockHttpServletRequestBuilder authorizedDelete(String apiKey, Long routeTemplateId) {
        return delete("/api/events/route-templates/{routeTemplateId}", routeTemplateId)
                .header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey);
    }

    private MockHttpServletRequestBuilder authorizedPost(String apiKey) {
        return post("/api/events/route-templates")
                .header(ApiKeyAuthFilter.API_KEY_HEADER, apiKey);
    }
}
