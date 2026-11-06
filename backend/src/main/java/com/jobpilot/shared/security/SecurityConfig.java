package com.jobpilot.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Seguridad de la API (sección 2.5): sin estado, JWT Bearer validado por el resource server de Spring Security,
 * todo {@code /api/v1/**} protegido salvo registro y login, y CORS solo para el origen del frontend.
 *
 * <p>CSRF desactivado a propósito: no hay sesión ni cookies, el token viaja en la cabecera
 * {@code Authorization}, que un sitio ajeno no puede añadir a una petición del navegador del usuario.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    /** BCrypt de coste 12 (sección 2.5). */
    static final int BCRYPT_STRENGTH = 12;

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

    /** Rutas sin token. En ellas también se ignora un {@code Bearer} caducado o no válido (p. ej. al volver a hacer login). */
    static final RequestMatcher PUBLIC = new OrRequestMatcher(
            PATHS.matcher(HttpMethod.POST, "/api/v1/auth/register"),
            PATHS.matcher(HttpMethod.POST, "/api/v1/auth/login"),
            PATHS.matcher(HttpMethod.GET, "/actuator/health"),
            PATHS.matcher(HttpMethod.GET, "/actuator/health/**"),
            // Documentación de la API; en prod springdoc está desactivado y estas rutas dan 404.
            PATHS.matcher("/v3/api-docs"),
            PATHS.matcher("/v3/api-docs/**"),
            PATHS.matcher("/swagger-ui.html"),
            PATHS.matcher("/swagger-ui/**"),
            PATHS.matcher("/error"));

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ProblemDetailAuthenticationEntryPoint entryPoint)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> { })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new AuthenticatedUserConverter()))
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint));
        return http.build();
    }

    /** Lee el {@code Bearer} de la cabecera, salvo en las rutas públicas. */
    private static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
        return (HttpServletRequest request) -> PUBLIC.matches(request) ? null : headerResolver.resolve(request);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.cors().allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Location: id del recurso creado (201). Retry-After: cuándo reintentar tras un 429/503.
        cors.setExposedHeaders(List.of("Location", "Retry-After"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
