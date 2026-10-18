package com.sistema.pedidosCori.config;

import com.sistema.pedidosCori.service.LoginAttemptService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class LoginThrottleFilter extends OncePerRequestFilter {
    private final LoginAttemptService attempts;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(req.getMethod())
                && (req.getContextPath() + "/login").equals(req.getRequestURI())) {
            long minutes = attempts.remainingMinutes(req.getParameter("username"), req.getRemoteAddr());
            if (minutes > 0) {
                SecurityConfig.redirectLoginError(req, res, minutes);
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
