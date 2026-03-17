package com.clickchecker.config;

import com.clickchecker.web.resolver.CurrentOrganizationIdResolver;
import com.clickchecker.web.resolver.CurrentAccountIdResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CurrentAccountIdResolver currentAccountIdResolver;
    private final CurrentOrganizationIdResolver currentOrganizationIdResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentAccountIdResolver);
        resolvers.add(currentOrganizationIdResolver);
    }
}
