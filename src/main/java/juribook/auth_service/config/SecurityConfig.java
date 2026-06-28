package juribook.auth_service.config;

import juribook.auth_service.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuration Spring Security de l'auth-service.
 *
 * Déclare deux beans essentiels :
 *   - PasswordEncoder (BCrypt) → injecté dans AuthService pour hasher les mots de passe
 *   - SecurityFilterChain     → règles d'accès + filtre JWT
 *
 * Routes publiques :
 *   POST /api/auth/register, /register/lawyer, /login, /refresh
 *   GET  /actuator/health, /swagger-ui/**, /v3/api-docs/**
 *
 * Routes protégées :
 *   POST /api/auth/logout → JWT requis
 *   GET  /api/users/**   → JWT requis + rôle
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final String[] PUBLIC_URLS = {
        "/api/auth/register",
        "/api/auth/register/lawyer",
        "/api/auth/login",
        "/api/auth/refresh",
        "/actuator/health",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/**"
    };

    /**
     * Bean PasswordEncoder — BCrypt avec force 10 (défaut).
     * Injecté dans AuthService via @RequiredArgsConstructor.
     * ⚠️ Doit être dans un @Configuration pour être visible par Spring.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(PUBLIC_URLS).permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/users/me").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/users/lawyer-profile").hasRole("LAWYER")
                    .requestMatchers(HttpMethod.GET, "/api/users/client-dashboard").hasRole("CLIENT")
                    .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter,
                    UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}