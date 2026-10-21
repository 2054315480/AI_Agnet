package com.qh.ai_agent.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Spring Security 配置：JWT 无状态认证 + 基于角色的接口鉴权。
 * 鉴权矩阵：
 * /auth/**                        公开（登录、注册；/auth/me 由控制器内部校验登录态）
 * /customer-service/chat          公开（面向终端用户的客服对话）
 * /customer-service/session/**    必须登录
 * /customer-service/batch-test    必须登录
 * /admin/**                       必须拥有 ADMIN 角色
 * /analytics/**                   必须拥有 ADMIN 角色
 * 其余接口                        默认必须登录
 * 未登录返回 401，已登录但角色不足返回 403。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // JWT 场景无需 CSRF；跨域沿用现有 CORS 配置
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 无状态：不创建 HttpSession，权限完全来自每次请求携带的 JWT
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // 明确区分 401（未认证）与 403（已认证但无权限）
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或 token 无效"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJson(response, HttpServletResponse.SC_FORBIDDEN, "无权限访问")))
                .authorizeHttpRequests(auth -> auth
                        // CORS 预检请求不携带 token，必须放行
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 登录、注册公开；/auth/me 由控制器内部校验登录态
                        .requestMatchers("/auth/**").permitAll()
                        // 面向终端用户的公开客服对话
                        .requestMatchers("/customer-service/chat").permitAll()
                    // 自证条：只读系统信息，公开可访问（不含敏感数据）
                    .requestMatchers("/system/info").permitAll()
                        // 客服会话状态与批量测试只要求登录
                        .requestMatchers("/customer-service/session/**").authenticated()
                        .requestMatchers("/customer-service/batch-test").authenticated()
                        // 管理端与对话分析必须是 ADMIN
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/analytics/**").hasRole("ADMIN")
                        // 接口文档保持原有公开配置
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/doc.html", "/webjars/**").permitAll()
                        // 错误转发端点放行，避免异常响应被二次鉴权覆盖
                        .requestMatchers("/error").permitAll()
                        // 其余接口默认必须登录，杜绝默认全放行
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 统一输出 JSON 错误体，保持前端可解析的响应格式。
     */
    private static void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}