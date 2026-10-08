package com.lexhive.lending.config;

import com.lexhive.lending.security.ProblemDetailSecurityHandler;
import com.lexhive.lending.security.Role;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless HTTP Basic. URL rules guard the coarse areas (API, actuator, docs); fine-grained role and ownership
 * rules live next to each endpoint as {@code @PreAuthorize}.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityUsersProperties.class)
public class SecurityConfig {

    private static final String[] STAFF = {Role.LIBRARIAN.name(), Role.ADMIN.name()};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemDetailSecurityHandler problemHandler) throws Exception {
        return http
                // No cookies or sessions: every request carries credentials, so CSRF does not apply.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.authenticationEntryPoint(problemHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/**").hasRole(Role.ADMIN.name())
                        // Catalog and member writes are staff-only (also declared per endpoint with @StaffOnly).
                        // Checking here too rejects members before the request body is parsed and validated.
                        .requestMatchers(HttpMethod.POST, "/api/v1/books", "/api/v1/members").hasAnyRole(STAFF)
                        .requestMatchers(HttpMethod.PUT, "/api/v1/books/*", "/api/v1/members/*").hasAnyRole(STAFF)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/books/*", "/api/v1/members/*").hasAnyRole(STAFF)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    UserDetailsService userDetailsService(SecurityUsersProperties properties) {
        var users = properties.users().stream()
                .map(user -> User.withUsername(user.username())
                        .password(user.password())
                        .roles(user.roles().stream().map(Role::name).toArray(String[]::new))
                        .build())
                .toList();
        return new InMemoryUserDetailsManager(users.toArray(UserDetails[]::new));
    }

    /** Delegating encoder: understands {@code {bcrypt}...} hashes and allows upgrading algorithms later. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
