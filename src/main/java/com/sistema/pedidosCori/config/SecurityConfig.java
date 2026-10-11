package com.sistema.pedidosCori.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import jakarta.servlet.http.HttpSession;

@Configuration
public class SecurityConfig {

    public static boolean esAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /**
     * Acceso al panel del dueño: usuario con rol ADMIN, o un mesero
     * que desbloqueó el panel con el PIN (flujo original del sistema).
     */
    private static final AuthorizationManager<RequestAuthorizationContext> ADMIN_O_PIN = (authSupplier, ctx) -> {
        Authentication auth = authSupplier.get();
        if (esAdmin(auth)) return new AuthorizationDecision(true);
        HttpSession session = ctx.getRequest().getSession(false);
        return new AuthorizationDecision(
            session != null && Boolean.TRUE.equals(session.getAttribute(AdminInterceptor.SESSION_KEY)));
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/img/**", "/favicon.ico", "/error").permitAll()

                // Vistas
                .requestMatchers("/mesero").hasAnyRole("ADMIN", "MESERO")
                .requestMatchers("/cocina").hasAnyRole("ADMIN", "COCINA")
                .requestMatchers("/menu", "/menu/**").hasAnyRole("ADMIN", "MESERO")

                // API del panel del dueño
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "MESERO")
                .requestMatchers(HttpMethod.GET, "/api/platos/todos").access(ADMIN_O_PIN)
                .requestMatchers(HttpMethod.POST, "/api/platos/**", "/api/menu-diario").access(ADMIN_O_PIN)
                .requestMatchers(HttpMethod.PUT, "/api/platos/**", "/api/presentaciones/**").access(ADMIN_O_PIN)
                .requestMatchers(HttpMethod.DELETE, "/api/platos/**", "/api/presentaciones/**").access(ADMIN_O_PIN)

                // API de cocina
                .requestMatchers(HttpMethod.PUT, "/api/pedidos/*/estado").hasAnyRole("ADMIN", "COCINA")

                // API de mesero
                .requestMatchers(HttpMethod.POST, "/api/pedidos", "/api/pedidos/**").hasAnyRole("ADMIN", "MESERO")
                .requestMatchers(HttpMethod.PUT, "/api/pedidos/*/pagar").hasAnyRole("ADMIN", "MESERO")
                .requestMatchers("/api/reportes/**").hasAnyRole("ADMIN", "MESERO")

                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            // Las vistas usan fetch() y SockJS sin token CSRF
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/ws/**"))
            // SockJS puede usar iframes del mismo origen
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
                .accessDeniedHandler((req, res, e) -> {
                    if (req.getRequestURI().startsWith("/api/")) res.sendError(HttpStatus.FORBIDDEN.value());
                    else res.sendRedirect("/?denegado");
                })
            );
        return http.build();
    }
}
