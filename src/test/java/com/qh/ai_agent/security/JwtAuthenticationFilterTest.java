package com.qh.ai_agent.security;

import com.qh.ai_agent.config.JwtAuthenticationFilter;
import com.qh.ai_agent.config.JwtUtil;
import com.qh.ai_agent.entity.User;
import com.qh.ai_agent.service.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 JWT 过滤器会把 role claim 转成 Spring Security authorities。
 * 不启动 Spring 容器，也不依赖数据库。
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "unit-test-secret-key-at-least-32-characters-long";

    private JwtUtil jwtUtil;
    private UserService userService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3_600_000L);

        userService = mock(UserService.class);
        filter = new JwtAuthenticationFilter(jwtUtil, userService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User user(long id, String username, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }

    private Authentication doFilterWithToken(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/faq/list");
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void adminRoleClaimShouldPopulateRoleAdminAuthority() throws Exception {
        when(userService.findById(1L)).thenReturn(user(1L, "admin", "admin"));
        String token = jwtUtil.generateToken(1L, "admin", "admin");

        assertEquals("admin", jwtUtil.getRoleFromToken(token));
        Authentication authentication = doFilterWithToken(token);

        assertNotNull(authentication);
        assertTrue(authentication.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())),
                "admin 角色应转换为 ROLE_ADMIN");
        assertEquals("admin", ((User) authentication.getPrincipal()).getUsername());
    }

    @Test
    void userRoleClaimShouldOnlyPopulateRoleUserAuthority() throws Exception {
        when(userService.findById(2L)).thenReturn(user(2L, "testuser", "user"));
        String token = jwtUtil.generateToken(2L, "testuser", "user");

        Authentication authentication = doFilterWithToken(token);

        assertNotNull(authentication);
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_USER".equals(a.getAuthority())));
        assertTrue(authentication.getAuthorities().stream()
                .noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
    }

    @Test
    void legacyTokenWithoutRoleClaimShouldFallBackToDatabaseRole() throws Exception {
        when(userService.findById(3L)).thenReturn(user(3L, "legacy", "admin"));
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String legacyToken = Jwts.builder()
                .subject("3")
                .claim("username", "legacy")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(key)
                .compact();

        Authentication authentication = doFilterWithToken(legacyToken);

        assertNotNull(authentication);
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())),
                "历史 token 没有 role 时应使用数据库角色");
    }

    @Test
    void tokenWithRolePrefixShouldNotBePrefixedTwice() throws Exception {
        when(userService.findById(4L)).thenReturn(user(4L, "admin2", "admin"));
        String token = jwtUtil.generateToken(4L, "admin2", "ROLE_ADMIN");

        Authentication authentication = doFilterWithToken(token);

        assertNotNull(authentication);
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
        assertTrue(authentication.getAuthorities().stream()
                .noneMatch(a -> "ROLE_ROLE_ADMIN".equals(a.getAuthority())));
    }

    @Test
    void requestWithoutTokenShouldStayAnonymous() throws Exception {
        Authentication authentication = doFilterWithToken(null);
        assertNull(authentication);
    }
}