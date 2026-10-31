package com.autocare.ai.config;

import com.autocare.ai.model.enums.UserRole;
import com.autocare.ai.exception.ErrorResponseFactory;
import com.autocare.ai.security.RateLimitFilter;
import com.autocare.ai.security.RequestLoggingFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AutoCareProperties.class)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            RequestLoggingFilter requestLoggingFilter,
            RateLimitFilter rateLimitFilter) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/admin.html",
                                "/styles.css",
                                "/app.js",
                                "/js/**",
                                "/favicon.ico",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers("/kb/**")
                        .hasRole(UserRole.ADMIN.name())
                        .requestMatchers("/ai/autocare/monitoring/**")
                        .hasRole(UserRole.ADMIN.name())
                        .requestMatchers("/ai/autocare/**")
                        .hasAnyRole(
                                UserRole.OWNER.name(),
                                UserRole.ADVISOR.name(),
                                UserRole.ADMIN.name())
                        .anyRequest()
                        .authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(authEntryPoint(objectMapper)))
                .exceptionHandling(errors -> errors.accessDeniedHandler(accessDeniedHandler(objectMapper)))
                .addFilterBefore(requestLoggingFilter, BasicAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, BasicAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${autocare.security.cors.allowed-origins:http://localhost:5500,http://127.0.0.1:5500}")
                    List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RateLimitFilter rateLimitFilter(AutoCareProperties properties, ObjectMapper objectMapper) {
        return new RateLimitFilter(properties, objectMapper);
    }

    @Bean
    public RequestLoggingFilter requestLoggingFilter() {
        return new RequestLoggingFilter();
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        return disabledServletRegistration(filter);
    }

    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilterRegistration(RequestLoggingFilter filter) {
        return disabledServletRegistration(filter);
    }

    /**
     * Manual Swagger UI / Postman test accounts, one per {@link UserRole}, so each protected endpoint
     * can be exercised with Basic Auth. Passwords are local-dev defaults, overridable via
     * {@code autocare.security.test-users.<role>-password}; change them before any shared deployment.
     */
    @Bean
    public UserDetailsService userDetailsService(
            PasswordEncoder passwordEncoder,
            @Value("${autocare.security.test-users.owner-password:owner-pass}") String ownerPassword,
            @Value("${autocare.security.test-users.advisor-password:advisor-pass}") String advisorPassword,
            @Value("${autocare.security.test-users.admin-password:admin-pass}") String adminPassword) {
        return new InMemoryUserDetailsManager(
                User.withUsername("owner")
                        .password(passwordEncoder.encode(ownerPassword))
                        .roles(UserRole.OWNER.name())
                        .build(),
                User.withUsername("advisor")
                        .password(passwordEncoder.encode(advisorPassword))
                        .roles(UserRole.ADVISOR.name())
                        .build(),
                User.withUsername("admin")
                        .password(passwordEncoder.encode(adminPassword))
                        .roles(UserRole.ADMIN.name())
                        .build());
    }

    private AuthenticationEntryPoint authEntryPoint(ObjectMapper objectMapper) {
        return (request, response, exception) ->
                writeError(objectMapper, request, response, HttpStatus.UNAUTHORIZED, "Authentication is required.");
    }

    private AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, exception) ->
                writeError(objectMapper, request, response, HttpStatus.FORBIDDEN, "Access is forbidden.");
    }

    private void writeError(
            ObjectMapper objectMapper,
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            HttpStatus status,
            String message) throws IOException {
        var error = ErrorResponseFactory.create(status, message, request);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Correlation-ID", error.correlationId());
        String path = request.getRequestURI().replaceAll("[\\r\\n\\t]", "_");
        log.info(
                "correlationId={} user={} role={} endpoint={} mode={} advisors=none latencyMs=0 status={}",
                error.correlationId(),
                status == HttpStatus.UNAUTHORIZED ? "anonymous" : request.getRemoteUser(),
                status == HttpStatus.UNAUTHORIZED ? "anonymous" : "denied",
                path,
                path.endsWith("/stream") ? "async" : path.endsWith("/sync") ? "sync" : "admin",
                status.value());
        objectMapper.writeValue(response.getOutputStream(), error);
    }

    private <T extends Filter> FilterRegistrationBean<T> disabledServletRegistration(T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
