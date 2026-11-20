package com.example.banque.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CORS en premier : le preflight OPTIONS (envoye sans identifiants) est accepte
                .cors(withDefaults())
                // les regles sont lues dans l'ordre : la plus precise d'abord
                .authorizeHttpRequests(authz -> authz
                        // seul un conseiller valide un virement (ROLE_CONSEILLER)
                        .requestMatchers(HttpMethod.POST, "/api/virements/*/valider").hasRole("CONSEILLER")
                        // toutes les autres requetes /api/** doivent etre authentifiees
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                // pas de CSRF : API stateless, aucun cookie envoye automatiquement par le navigateur
                .csrf(csrf -> csrf.disable())
                // identification par Basic Auth (en-tete Authorization: Basic ...)
                .httpBasic(withDefaults())
                // pas de session : les identifiants sont renvoyes a chaque requete
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    /** Ce que le CORS autorise : le front Angular, avec l'en-tete Authorization. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.origines}") String[] origines) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origines));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * Compare le mot de passe recu (en clair, dans l'en-tete Basic)
     * au hash BCrypt stocke en base.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
