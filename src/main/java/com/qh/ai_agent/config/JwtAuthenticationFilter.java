package com.qh.ai_agent.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器：解析 token, 校验用户, 并把角色 claim 转换为 Spring Security authorities。
 * 只有 authorities 正确填充, hasRole("ADMIN") 等鉴权规则才会生效。
 */
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
                        // 优先使用 token 中的角色；历史 token 没有 role 时回退到数据库角色
                        String tokenRole = jwtUtil.getRoleFromToken(token);
                        List<SimpleGrantedAuthority> authorities = resolveAuthorities(tokenRole, user.getRole());
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(user, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        log.debug("JWT 认证成功: userId={}, username={}, authorities={}, uri={}",
                                userId, user.getUsername(), authorities, uri);
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

    /**
     * 角色归一化：数据库存 admin/user, 这里统一转换为 ROLE_ADMIN/ROLE_USER。
     * 同时兼容已经带 ROLE_ 前缀的 claim, 并支持逗号分隔的多角色。
     */
    private List<SimpleGrantedAuthority> resolveAuthorities(String tokenRole, String databaseRole) {
        String roleSource = StringUtils.hasText(tokenRole) ? tokenRole : databaseRole;
        if (!StringUtils.hasText(roleSource)) {
            roleSource = "user";
        }
        return Arrays.stream(roleSource.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(role -> role.toUpperCase(Locale.ROOT))
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        // 兼容 SSE 请求通过 query param 传 token
        String queryToken = request.getParameter("token");
        if (StringUtils.hasText(queryToken)) {
            return queryToken;
        }
        return null;
    }
}