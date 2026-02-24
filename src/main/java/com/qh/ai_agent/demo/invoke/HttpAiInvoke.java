package com.qh.ai_agent.demo.invoke;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;


/**
 * HTTP AI SDK  调用
 */
public class HttpAiInvoke {

    public static void main(String[] args) {
        // API密钥
        String apiKey = TestApiKey.API_KEY; // 从环境变量获取
        // 或者直接指定: String apiKey = "your-api-key-here";

        // 构建请求URL
        String url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

        // 构建请求体
        JSONObject requestBody = new JSONObject();
        requestBody.set("model", "qwen-max");

        // 构建 messages 数组
        JSONArray messages = new JSONArray();

        JSONObject systemMessage = new JSONObject();
        systemMessage.set("role", "system");
        systemMessage.set("content", "You are a helpful assistant.");
        messages.add(systemMessage);

        JSONObject userMessage = new JSONObject();
        userMessage.set("role", "user");
        userMessage.set("content", "你是谁？");
        messages.add(userMessage);

        // 构建 input 对象
        JSONObject input = new JSONObject();
        input.set("messages", messages);
        requestBody.set("input", input);

        // 构建 parameters 对象
        JSONObject parameters = new JSONObject();
        parameters.set("result_format", "message");
        requestBody.set("parameters", parameters);

        // 发送 POST 请求
        HttpResponse response = HttpRequest.post(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(JSONUtil.toJsonStr(requestBody))
                .execute();

        // 获取响应结果
        String result = response.body();
        System.out.println("响应状态码: " + response.getStatus());
        System.out.println("响应内容: " + result);

        // 解析 JSON 响应(可选)
        if (response.isOk()) {
            JSONObject jsonResponse = JSONUtil.parseObj(result);
            System.out.println("解析后的响应: " + jsonResponse);
        }
    }
}

