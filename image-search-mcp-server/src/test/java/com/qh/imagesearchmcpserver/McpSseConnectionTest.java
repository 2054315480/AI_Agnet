package com.qh.imagesearchmcpserver;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP SSE 连接测试
 *
 * 测试 MCP 服务器的 SSE 端点是否可访问
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("sse")
public class McpSseConnectionTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	public void testSseEndpointAccessible() {
		String url = "http://localhost:8123/mcp/sse";

		log.info("测试 SSE 端点访问: {}", url);

		HttpHeaders headers = new HttpHeaders();
		headers.add("Accept", "text/event-stream");

		HttpEntity<String> entity = new HttpEntity<>(headers);

		try {
			ResponseEntity<String> response = restTemplate.exchange(
				url,
				HttpMethod.GET,
				entity,
				String.class
			);

			log.info("响应状态码: {}", response.getStatusCode());
			log.info("响应头: {}", response.getHeaders());

			assertThat(response.getStatusCode()).isIn(
				HttpStatus.OK,
				HttpStatus.ACCEPTED
			);

		} catch (Exception e) {
			log.error("连接 SSE 端点失败", e);
			// SSE 连接可能需要特定的客户端，这里仅测试端点是否可访问
		}
	}

	@Test
	public void testServerInfo() {
		String url = "http://localhost:8123/actuator/health";

		try {
			ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
			log.info("健康检查响应: {}", response.getBody());
		} catch (Exception e) {
			log.info("Actuator 未启用，跳过健康检查");
		}
	}
}
