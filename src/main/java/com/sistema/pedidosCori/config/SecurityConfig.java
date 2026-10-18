package com.sistema.pedidosCori.config;

import com.sistema.pedidosCori.models.dao.IUsuarioDao;
import com.sistema.pedidosCori.models.entity.AccionAuditoria;
import com.sistema.pedidosCori.models.entity.RegistroAuditoria;
import com.sistema.pedidosCori.service.AuditoriaService;
import com.sistema.pedidosCori.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Slf4j
@Configuration
@EnableScheduling
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, LoginAttemptService attempts,
            SessionRegistry sessionRegistry, AuditoriaService auditoria, IUsuarioDao usuarioDao) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/error", "/favicon.ico", "/css/**", "/js/**", "/img/**", "/branding/logo").permitAll()
                .requestMatchers("/", "/logout", "/ws", "/ws/**").authenticated()
                .requestMatchers("/mesero", "/catalogo").hasAnyRole("ADMIN", "MESERO")
                .requestMatchers("/cocina").hasAnyRole("ADMIN", "COCINA")
                .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                .requestMatchers("/menu", "/menu/**", "/configuracion", "/api/admin/**", "/api/reportes/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/categorias", "/api/platos/*/imagen").hasAnyRole("ADMIN", "MESERO", "COCINA")
                .requestMatchers(HttpMethod.GET, "/api/platos/todos").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/platos/**", "/api/menu-diario").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/platos/**", "/api/presentaciones/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/platos/**", "/api/presentaciones/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/pedidos/*/estado").hasAnyRole("ADMIN", "COCINA", "MESERO")
                .requestMatchers(HttpMethod.POST, "/api/pedidos", "/api/pedidos/*/detalles").hasAnyRole("ADMIN", "MESERO")
                .requestMatchers(HttpMethod.PUT, "/api/pedidos/*/pagar").hasAnyRole("ADMIN", "MESERO")
                .requestMatchers(HttpMethod.GET, "/api/platos", "/api/platos/*/presentaciones").hasAnyRole("ADMIN", "MESERO")
                .requestMatchers(HttpMethod.GET, "/api/mesas/ocupadas").hasAnyRole("ADMIN", "MESERO")
                .requestMatchers(HttpMethod.GET, "/api/pedidos/cocina").hasAnyRole("ADMIN", "COCINA")
                .requestMatchers(HttpMethod.GET, "/api/pedidos/activos", "/api/pedidos/*").hasAnyRole("ADMIN", "MESERO", "COCINA")
                .anyRequest().denyAll()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler((req, res, authentication) -> {
                    attempts.reset(req.getParameter("username"), req.getRemoteAddr());
                    String username = authentication.getName();
                    try {
                        usuarioDao.registrarAcceso(username, LocalDateTime.now());
                    } catch (RuntimeException e) {
                        log.warn("No se pudo registrar el último acceso de {}: {}", username, e.getMessage());
                    }
                    auditoria.registrarComo(username, AccionAuditoria.LOGIN, "Usuario", null,
                        () -> "Acceso desde " + req.getRemoteAddr());
                    res.sendRedirect("/");
                })
                .failureHandler((req, res, exception) -> {
                    attempts.failed(req.getParameter("username"), req.getRemoteAddr());
                    String intentado = req.getParameter("username");
                    String usuario = intentado == null || intentado.isBlank() ? "(vacío)"
                        : AuditoriaService.recortar(intentado.trim(), RegistroAuditoria.MAX_USUARIO);
                    auditoria.registrarComo(usuario, AccionAuditoria.LOGIN_FALLIDO, "Usuario", null,
                        () -> (exception instanceof DisabledException ? "Cuenta desactivada" : "Credenciales incorrectas")
                            + " · Desde " + req.getRemoteAddr());
                    redirectLoginError(req, res, attempts.remainingMinutes(req.getParameter("username"), req.getRemoteAddr()));
                })
                .permitAll()
            )
            .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout").permitAll())
            .sessionManagement(session -> session.sessionConcurrency(concurrency -> concurrency
                .maximumSessions(-1)
                .sessionRegistry(sessionRegistry)
                .expiredSessionStrategy(event -> {
                    HttpServletRequest req = event.getRequest();
                    HttpServletResponse res = event.getResponse();
                    if (req.getRequestURI().startsWith(req.getContextPath() + "/api/"))
                        apiError(res, HttpStatus.UNAUTHORIZED, "Tu sesión fue cerrada por un administrador", "SESION_EXPIRADA");
                    else res.sendRedirect(req.getContextPath() + "/login?expirado");
                })))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/ws/**"))
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                    (req, res, e) -> apiError(res, HttpStatus.UNAUTHORIZED, "Debes iniciar sesión", "NO_AUTENTICADO"),
                    PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
                .accessDeniedHandler((req, res, e) -> {
                    if (req.getRequestURI().startsWith("/api/"))
                        apiError(res, HttpStatus.FORBIDDEN, "No tienes permiso para esta operación", "PROHIBIDO");
                    else res.sendRedirect("/?denegado");
                })
            );
        http.addFilterBefore(new LoginThrottleFilter(attempts), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    static void redirectLoginError(HttpServletRequest req, HttpServletResponse res, long minutes) throws IOException {
        res.sendRedirect(req.getContextPath() + (minutes > 0 ? "/login?blocked=" + minutes : "/login?error"));
    }

    private static void apiError(HttpServletResponse res, HttpStatus status, String mensaje, String codigo)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"mensaje\":\"" + mensaje + "\",\"codigo\":\"" + codigo + "\"}");
    }
}
