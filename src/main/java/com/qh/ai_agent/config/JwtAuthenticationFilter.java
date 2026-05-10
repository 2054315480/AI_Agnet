package com.qh.ai_agent.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final com.qh.ai_agent.service.UserService userService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, com.qh.ai_agent.service.UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        String uri = request.getRequestURI();
        log.debug("JWT 过滤器: uri={}, hasToken={}", uri, token != null);

        if (token != null) {
            if (jwtUtil.validateToken(token)) {
                try {
                    Long userId = jwtUtil.getUserIdFromToken(token);
                    var user = userService.findById(userId);
                    if (user != null) {
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(user, null, List.of());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        log.debug("JWT 认证成功: userId={}, username={}, uri={}", userId, user.getUsername(), uri);
                    } else {
                        log.warn("JWT 认证失败: 用户不存在, userId={}, uri={}", userId, uri);
                    }
                } catch (Exception e) {
                    log.warn("JWT 认证异常: {}, uri={}", e.getMessage(), uri);
                }
            } else {
                log.debug("JWT token 验证失败（可能已过期）, uri={}", uri);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        // 兼容 SSE 请求通过 query param 传递 token
        String queryToken = request.getParameter("token");
        if (StringUtils.hasText(queryToken)) {
            return queryToken;
        }
        return null;
    }
}
