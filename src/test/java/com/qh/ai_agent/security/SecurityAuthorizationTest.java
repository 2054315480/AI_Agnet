package com.qh.ai_agent.security;

import com.qh.ai_agent.config.JwtAuthenticationFilter;
import com.qh.ai_agent.config.JwtUtil;
import com.qh.ai_agent.config.SecurityConfig;
import com.qh.ai_agent.entity.User;
import com.qh.ai_agent.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实 SecurityFilterChain 的鉴权矩阵测试。
 * 通过 MockMvc 走完整的 Spring Security 过滤器链, 只 mock UserService, 不连接数据库。
 */
@SpringJUnitWebConfig(classes = SecurityAuthorizationTest.TestConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=security-test-secret-key-at-least-32-characters-long",
        "jwt.expiration=3600000"
})
class SecurityAuthorizationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(userService.findById(1L)).thenReturn(user(1L, "admin", "admin"));
        when(userService.findById(2L)).thenReturn(user(2L, "testuser", "user"));
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    private User user(long id, String username, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }

    private String adminToken() {
        return jwtUtil.generateToken(1L, "admin", "admin");
    }

    private String userToken() {
        return jwtUtil.generateToken(2L, "testuser", "user");
    }

    @Test
    void adminApiWithoutTokenShouldReturn401() throws Exception {
        mockMvc.perform(get("/admin/ping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminApiWithInvalidTokenShouldReturn401() throws Exception {
        mockMvc.perform(get("/admin/ping")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminApiWithUserTokenShouldReturn403() throws Exception {
        mockMvc.perform(get("/admin/ping")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminApiWithAdminTokenShouldReturn2xx() throws Exception {
        mockMvc.perform(get("/admin/ping")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().is2xxSuccessful())
                .andExpect(content().string("admin-ok"));
    }

    @Test
    void analyticsApiWithoutTokenShouldReturn401() throws Exception {
        mockMvc.perform(get("/analytics/ping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void analyticsApiWithUserTokenShouldReturn403() throws Exception {
        mockMvc.perform(get("/analytics/ping")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void analyticsApiWithAdminTokenShouldReturn2xx() throws Exception {
        mockMvc.perform(get("/analytics/ping")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().is2xxSuccessful())
                .andExpect(content().string("analytics-ok"));
    }

    @Test
    void customerServiceChatShouldBePublic() throws Exception {
        mockMvc.perform(get("/customer-service/chat").param("message", "hello"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(content().string("chat-ok"));
    }

    @Test
    void customerServiceSessionShouldRequireLogin() throws Exception {
        mockMvc.perform(get("/customer-service/session/s1/state"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/customer-service/session/s1/state")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void customerServiceBatchTestShouldRequireLogin() throws Exception {
        mockMvc.perform(post("/customer-service/batch-test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unlistedApiShouldDefaultToAuthenticated() throws Exception {
        mockMvc.perform(get("/ai/ping"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/ai/ping")
                        .header("Authorization", "Bearer " + userToken()))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void authApiShouldStayPublic() throws Exception {
        mockMvc.perform(get("/auth/ping"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(content().string("auth-ok"));
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, JwtUtil.class})
    static class TestConfig {

        @Bean
        UserService userService() {
            return mock(UserService.class);
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtil jwtUtil, UserService userService) {
            return new JwtAuthenticationFilter(jwtUtil, userService);
        }

        @Bean
        SecurityTestEndpoints securityTestEndpoints() {
            return new SecurityTestEndpoints();
        }
    }

    @RestController
    static class SecurityTestEndpoints {

        @GetMapping("/admin/ping")
        String adminPing() {
            return "admin-ok";
        }

        @GetMapping("/analytics/ping")
        String analyticsPing() {
            return "analytics-ok";
        }

        @GetMapping("/customer-service/chat")
        String chat(@RequestParam(name = "message", required = false) String message) {
            return "chat-ok";
        }

        @GetMapping("/customer-service/session/{sessionId}/state")
        String sessionState(@PathVariable("sessionId") String sessionId) {
            return "session-ok";
        }

        @PostMapping("/customer-service/batch-test")
        String batchTest() {
            return "batch-ok";
        }

        @GetMapping("/ai/ping")
        String aiPing() {
            return "ai-ok";
        }

        @GetMapping("/auth/ping")
        String authPing() {
            return "auth-ok";
        }
    }
}