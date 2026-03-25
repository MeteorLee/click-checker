package com.clickchecker.config;

import com.clickchecker.account.repository.AccountRepository;
import com.clickchecker.auth.service.JwtTokenProvider;
import com.clickchecker.organization.repository.OrganizationRepository;
import com.clickchecker.organization.service.ApiKeyIssuer;
import com.clickchecker.web.filter.ApiKeyAuthFilter;
import com.clickchecker.web.filter.JwtAuthFilter;
import jakarta.servlet.Filter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public ApiKeyAuthFilter apiKeyAuthFilter(
            OrganizationRepository organizationRepository,
            ApiKeyIssuer apiKeyIssuer
    ) {
        return new ApiKeyAuthFilter(organizationRepository, apiKeyIssuer);
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(
            JwtTokenProvider jwtTokenProvider,
            AccountRepository accountRepository
    ) {
        return new JwtAuthFilter(jwtTokenProvider, accountRepository);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain adminAuthSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/v1/admin/auth/**")
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .anonymous(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain adminSecurityFilterChain(
            HttpSecurity http,
            JwtAuthFilter jwtAuthFilter
    ) throws Exception {
        http
                .securityMatcher("/api/v1/admin/**")
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, AnonymousAuthenticationFilter.class)
                .anonymous(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain eventSecurityFilterChain(
            HttpSecurity http,
            ApiKeyAuthFilter apiKeyAuthFilter
    ) throws Exception {
        http
                .securityMatcher("/api/events/**", "/api/v1/events/**")
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(apiKeyAuthFilter, AnonymousAuthenticationFilter.class)
                .anonymous(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    @Order(4)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .anyRequest().permitAll()
                )
                .anonymous(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<Filter> disableApiKeyAuthFilterRegistration(ApiKeyAuthFilter apiKeyAuthFilter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(apiKeyAuthFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<Filter> disableJwtAuthFilterRegistration(JwtAuthFilter jwtAuthFilter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(jwtAuthFilter);
        registration.setEnabled(false);
        return registration;
    }
}
