package com.qh.ai_agent.intent;

import com.qh.ai_agent.intent.model.CustomerIntent;
import com.qh.ai_agent.intent.model.IntentResult;
import com.qh.ai_agent.intent.model.SlotDefinition;
import com.qh.ai_agent.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图识别服务：LLM 结构化输出 + 正则预筛双通道
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentClassifier {

    private final ChatModel chatModel;
    private final IntentConfig intentConfig;
    private final PromptTemplateService promptTemplateService;

    /** 正则预筛：订单号（支持多种格式：订单号123/订单号：123/订单号是123/纯数字6-20位） */
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("(?i)(?:订单号?|order\\s*(?:id|no)?)[是：:为]??\\s*([A-Za-z0-9]{6,20})");
    /** 正则预筛：手机号 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");

    /**
     * 对用户输入进行意图识别和槽位提取
     */
    public IntentResult classify(String userInput) {
        return classify(userInput, null);
    }

    public IntentResult classify(String userInput, Map<String, String> existingSlots) {
        log.info("[IntentClassifier] 输入: {}", userInput);

        // 1. 正则预筛，提取格式固定字段
        Map<String, String> regexSlots = new HashMap<>();
        extractByRegex(userInput, regexSlots);

        // 2. 合并已有槽位
        if (existingSlots != null) {
            regexSlots.putAll(existingSlots);
        }

        // 3. 构建 Prompt，调用 LLM 做意图分类
        String classificationPrompt = buildClassificationPrompt(userInput, regexSlots);
        String llmResponse = callLlm(classificationPrompt);

        // 4. 解析 LLM 结构化输出
        IntentResult result = parseLlmResponse(llmResponse, regexSlots);
        log.info("[IntentClassifier] 结果: intent={}, confidence={}, slots={}",
                result.getIntent(), result.getConfidence(), result.getSlots());
        return result;
    }

    /** 正则预筛提取 */
    private void extractByRegex(String input, Map<String, String> slots) {
        Matcher orderMatcher = ORDER_ID_PATTERN.matcher(input);
        if (orderMatcher.find()) {
            slots.put("order_id", orderMatcher.group(1));
        }
        Matcher phoneMatcher = PHONE_PATTERN.matcher(input);
        if (phoneMatcher.find()) {
            slots.put("phone", phoneMatcher.group());
        }
    }

    /** 构建意图分类 Prompt */
    private String buildClassificationPrompt(String userInput, Map<String, String> preExtracted) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个意图识别系统。请分析用户的输入，识别意图并提取关键信息。\n\n");

        sb.append("可选意图列表：\n");
        for (CustomerIntent intent : CustomerIntent.values()) {
            List<SlotDefinition> slotDefs = intentConfig.getSlotDefinitions(intent.name());
            sb.append("- ").append(intent.name()).append("（").append(intent.getDisplayName())
              .append("）：").append(intent.getDescription());
            if (!slotDefs.isEmpty()) {
                sb.append("。需要提取：");
                for (SlotDefinition sd : slotDefs) {
                    sb.append(sd.getSlotName()).append("(").append(sd.getDescription()).append("); ");
                }
            }
            sb.append("\n");
        }

        if (!preExtracted.isEmpty()) {
            sb.append("\n已通过预筛提取到的信息：\n");
            preExtracted.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        }

        sb.append("\n用户输入：").append(userInput).append("\n\n");

        sb.append("请严格按以下 JSON 格式回复，不要包含其他内容：\n");
        sb.append("{\"intent\":\"意图名称\",\"confidence\":0.95,\"slots\":{\"key\":\"value\"},");
        sb.append("\"needsClarification\":false,\"clarificationQuestion\":\"\"}\n");
        sb.append("\n规则：\n");
        sb.append("1. confidence 范围 0.0-1.0，低于 0.5 时 needsClarification=true\n");
        sb.append("2. 如果缺少必填槽位，needsClarification=true 并在 clarificationQuestion 中写出追问\n");
        sb.append("3. 不在上述意图范围内的，归类为 OUT_OF_SCOPE\n");
        sb.append("4. 用户要求转人工客服的，归类为 REQUEST_HUMAN\n");

        return sb.toString();
    }

    /** 调用 LLM */
    private String callLlm(String promptText) {
        try {
            List<org.springframework.ai.chat.messages.Message> messages = List.of(
                    new SystemMessage("你是一个精准的意图识别系统，只输出 JSON 格式的结果。"),
                    new UserMessage(promptText)
            );
            Prompt prompt = new Prompt(messages);
            ChatResponse response = chatModel.call(prompt);
            String text = response.getResult().getOutput().getText();
            log.debug("[IntentClassifier] LLM 原始响应: {}", text);
            return text;
        } catch (Exception e) {
            log.error("[IntentClassifier] LLM 调用失败", e);
            return "";
        }
    }

    /** 解析 LLM JSON 响应 */
    private IntentResult parseLlmResponse(String llmResponse, Map<String, String> preExtracted) {
        try {
            // 提取 JSON 部分（LLM 可能在前后加文字）
            String json = extractJson(llmResponse);

            // 简易 JSON 解析（避免引入额外依赖）
            String intentStr = extractJsonValue(json, "intent");
            String confidenceStr = extractJsonValue(json, "confidence");
            String slotsStr = extractJsonValue(json, "slots");
            String needsClarificationStr = extractJsonValue(json, "needsClarification");
            String clarificationQuestion = extractJsonValue(json, "clarificationQuestion");

            CustomerIntent intent;
            try {
                intent = CustomerIntent.valueOf(intentStr);
            } catch (IllegalArgumentException e) {
                intent = CustomerIntent.OUT_OF_SCOPE;
            }

            double confidence = 0.0;
            try {
                confidence = Double.parseDouble(confidenceStr);
            } catch (NumberFormatException ignored) {}

            // 合并槽位：预筛结果 + LLM 提取结果
            Map<String, String> slots = new HashMap<>(preExtracted);
            parseSlotsFromJson(slotsStr, slots);

            boolean needsClarification = "true".equalsIgnoreCase(needsClarificationStr);

            return IntentResult.builder()
                    .intent(intent)
                    .confidence(confidence)
                    .slots(slots)
                    .rawResponse(llmResponse)
                    .needsClarification(needsClarification)
                    .clarificationQuestion(clarificationQuestion)
                    .build();
        } catch (Exception e) {
            log.error("[IntentClassifier] 解析失败，原始响应: {}", llmResponse, e);
            return IntentResult.builder()
                    .intent(CustomerIntent.OUT_OF_SCOPE)
                    .confidence(0.0)
                    .slots(preExtracted)
                    .rawResponse(llmResponse)
                    .needsClarification(false)
                    .build();
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*";
        java.util.regex.Matcher m = Pattern.compile(pattern + "\"([^\"]*)\"").matcher(json);
        if (m.find()) return m.group(1);
        // 尝试非字符串值
        m = Pattern.compile(pattern + "([^,}\\s]+)").matcher(json);
        if (m.find()) return m.group(1).trim();
        return "";
    }

    private void parseSlotsFromJson(String slotsStr, Map<String, String> slots) {
        if (slotsStr == null || slotsStr.isBlank()) return;
        // 简易解析 "key1":"value1","key2":"value2"
        java.util.regex.Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"").matcher(slotsStr);
        while (m.find()) {
            slots.put(m.group(1), m.group(2));
        }
    }
}
