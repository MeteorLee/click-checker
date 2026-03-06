package com.clickchecker.config;

import com.clickchecker.organization.service.ApiKeyIssuer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiKeyConfig {

    @Bean
    public ApiKeyIssuer apiKeyIssuer(
            @Value("${app.api-key.env:live}") String env,
            @Value("${app.api-key.pepper:}") String pepper
    ) {
        return new ApiKeyIssuer(env, pepper);
    }
}
