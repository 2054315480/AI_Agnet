package com.qh.ai_agent.demo.invoke;


/**
 *  测试API KEY
 *  通过环境变量 DASHSCOPE_API_KEY 或启动参数 -Ddashscope.api.key=xxx 传入密钥
 */
public interface TestApiKey {
    String API_KEY = System.getProperty("dashscope.api.key",
            System.getenv().getOrDefault("DASHSCOPE_API_KEY", ""));
}
