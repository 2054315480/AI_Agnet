package com.qh.imagesearchmcpserver;

import com.qh.imagesearchmcpserver.Tools.ImageSearchTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.net.InetAddress;

@Slf4j
@SpringBootApplication
public class ImageSearchMcpServerApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(ImageSearchMcpServerApplication.class);
		var context = app.run(args);
		Environment env = context.getEnvironment();
		String port = env.getProperty("server.port", "8081");
		String profile = env.getProperty("spring.profiles.active", "default");

	}

	@Bean
	public ToolCallbackProvider imageSearchTools(ImageSearchTool imageSearchTool) {
		log.info("注册图片搜索工具: ImageSearchTool");
		return MethodToolCallbackProvider.builder()
				.toolObjects(imageSearchTool)
				.build();
	}
}
