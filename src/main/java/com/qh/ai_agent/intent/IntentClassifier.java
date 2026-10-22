package com.qh.ai_agent.intent;

import com.qh.ai_agent.dialog.model.DialogState;
import com.qh.ai_agent.intent.model.CustomerIntent;
import com.qh.ai_agent.intent.model.IntentResult;
import com.qh.ai_agent.intent.model.SlotDefinition;
import com.qh.ai_agent.intent.model.SlotExtractionResult;
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

    private final ChatModel openAiChatModel;
    private final IntentConfig intentConfig;
    private final PromptTemplateService promptTemplateService;

    /** 正则预筛：订单号（带前缀匹配） */
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("(?i)(?:订单号?|order\\s*(?:id|no)?)[是：:为]??\\s*([A-Za-z0-9]{6,20})");
    /** 正则预筛：纯订单号（6-20位字母数字，无中文/空格） */
    private static final Pattern PURE_ORDER_ID_PATTERN = Pattern.compile("^[A-Za-z0-9]{6,20}$");
    /** 正则预筛：手机号 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");

    /**
     * 显式的人工请求关键词：命中即转人工。
     * 注意：这里不能再放单独的"客服"，否则"你们客服电话是多少"会被误判成转人工。
     */
    private static final List<String> HUMAN_REQUEST_KEYWORDS = List.of(
            "转人工", "人工客服", "人工服务", "找人工", "我要人工", "联系人工", "接人工", "要人工",
            "不是机器人", "不要机器人", "不要AI", "别用机器人",
            "活人", "真正的人", "真人客服", "人工接待",
            "帮我转", "转接", "转客服", "换人", "换个客服",
            "跟人说话", "让人来", "不想跟机器", "跟真人"
    );

    /** 弱关键词：只有不在"客服电话/智能客服/人工智能/人工费"这类语境下才算转人工 */
    private static final List<String> HUMAN_REQUEST_WEAK_KEYWORDS = List.of("人工", "真人", "客服");

    /** 这些语境里出现"人工/客服"并不代表用户要转人工 */
    private static final List<String> NON_HANDOFF_CONTEXT_KEYWORDS = List.of(
            "客服电话", "客服热线", "客服中心", "客服地址", "客服邮箱", "客服工作时间",
            "智能客服", "人工智能", "人工费", "人工成本", "人工智障"
    );

    /**
     * 对用户输入进行意图识别和槽位提取
     */
    public IntentResult classify(String userInput) {
        return classify(userInput, null, null);
    }

    public IntentResult classify(String userInput, Map<String, String> existingSlots) {
        return classify(userInput, existingSlots, null);
    }

    /**
     * 带对话状态的意图识别（用于多轮对话上下文增强）
     */
    public IntentResult classify(String userInput, Map<String, String> existingSlots, DialogState dialogState) {
        log.info("[IntentClassifier] 输入: {}", userInput);

        String trimmed = userInput.trim();

        // ====== 关键词预筛（高置信度，不走 LLM）======
        IntentResult keywordResult = tryKeywordClassification(trimmed, existingSlots);
        if (keywordResult != null) {
            log.info("[IntentClassifier] 关键词预筛命中: intent={}, confidence={}",
                    keywordResult.getIntent(), keywordResult.getConfidence());
            return keywordResult;
        }

        // ====== LLM 语义分类 ======
        // 1. 正则预筛，提取格式固定字段
        Map<String, String> regexSlots = new HashMap<>();
        extractByRegex(userInput, regexSlots);

        // 2. 合并已有槽位
        if (existingSlots != null) {
            regexSlots.putAll(existingSlots);
        }

        // 3. 构建 Prompt，调用 LLM 做意图分类（带上下文时使用增强版）
        String classificationPrompt;
        if (dialogState != null && dialogState.getCurrentIntent() != null
                && dialogState.getLastBotMessage() != null) {
            classificationPrompt = buildClassificationPromptWithContext(userInput, regexSlots, dialogState);
        } else {
            classificationPrompt = buildClassificationPrompt(userInput, regexSlots);
        }
        String llmResponse = callLlm(classificationPrompt);

        // 4. 解析 LLM 结构化输出
        IntentResult result = parseLlmResponse(llmResponse, regexSlots);
        log.info("[IntentClassifier] 结果: intent={}, confidence={}, slots={}",
                result.getIntent(), result.getConfidence(), result.getSlots());
        return result;
    }

    /**
     * 关键词预筛：覆盖所有主要意图，高置信度直接返回，不走 LLM
     * 按优先级从高到低排列（转人工 > 退款 > 投诉 > ... > 闲聊）
     */
    /** 明显与客服业务无关的输入特征（算术、天气、创作、影视、公司信息等） */
    private static final java.util.regex.Pattern OUT_OF_DOMAIN_PATTERN = java.util.regex.Pattern.compile(
            "^\\s*\\d+\\s*[+\\-*/×÷]\\s*\\d+.*"          // 1+1、3*5
            + "|等于(几|多少)|平方|立方|开方"
            + "|天气|气温|下雨|雾霾"
            + "|写(一)?[篇首个]?(诗|作文|文章|小说)|作诗"
            + "|(推荐|想听|来一?[首个])[^，。]{0,8}(电影|电视剧|动漫|综艺|歌)"
            + "|电影|电视剧|动漫|综艺|歌曲|音乐"
            + "|(公司|你们)?的?(老板|创始人|CEO|董事长)是谁"
            + "|你是谁|你叫什么|你会什么"
            + "|股票|彩票|算命|星座"
            + "|笑话|几号|几点|星期几|翻译");

    /** 域外判定：命中特征且不含任何客服业务词，才算 OUT_OF_SCOPE */
    boolean looksOutOfDomain(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        if (!OUT_OF_DOMAIN_PATTERN.matcher(input).find()) {
            return false;
        }
        // 含业务词时不做拦截（例如"退货政策是什么"不会被误伤）
        return !containsAny(input, "订单", "退款", "退货", "换货", "保修", "物流", "配送",
                "发票", "套餐", "宽带", "信用卡", "账户", "理赔", "运费", "商品", "产品", "客服");
    }

    /** 包内可见：便于单测直接验证关键词预筛，不依赖大模型 */
    IntentResult tryKeywordClassification(String input, Map<String, String> existingSlots) {
        Map<String, String> slots = existingSlots != null ? new HashMap<>(existingSlots) : new HashMap<>();

        // 0. 域外问题优先拦截：算术/天气/写诗/影视/公司信息等与客服业务无关的输入，
        //    之前会被大模型判成 POLICY_QUERY 然后去检索知识库，导致"1+1等于几"引用店铺支付文档。
        if (looksOutOfDomain(input)) {
            return IntentResult.builder().intent(CustomerIntent.OUT_OF_SCOPE)
                    .confidence(0.9).slots(slots).needsClarification(false).build();
        }

        // 1. 转人工：先看显式表达，再看弱关键词（弱关键词要排除"客服电话/人工智能"等语境）
        if (containsAny(input, HUMAN_REQUEST_KEYWORDS.toArray(new String[0]))) {
            return IntentResult.builder().intent(CustomerIntent.REQUEST_HUMAN)
                    .confidence(0.95).slots(slots).needsClarification(false).build();
        }
        if (!containsAny(input, NON_HANDOFF_CONTEXT_KEYWORDS.toArray(new String[0]))
                && containsAny(input, HUMAN_REQUEST_WEAK_KEYWORDS.toArray(new String[0]))) {
            return IntentResult.builder().intent(CustomerIntent.REQUEST_HUMAN)
                    .confidence(0.90).slots(slots).needsClarification(false).build();
        }

        // 2. 退款/退钱
        if (containsAny(input, "退款", "退钱", "申请退款", "退费", "退款怎么", "退款原因")) {
            return IntentResult.builder().intent(CustomerIntent.ORDER_REFUND)
                    .confidence(0.92).slots(slots).build();
        }

        // 3. 取消/删除/移除（取消订单、删除购物车商品等）
        if (containsAny(input, "取消订单", "不要了", "取消", "不想要", "取消发货",
                "删掉", "移除", "删除", "去掉", "清除", "从购物车删")) {
            return IntentResult.builder().intent(CustomerIntent.ORDER_CANCEL)
                    .confidence(0.90).slots(slots).build();
        }

        // 4. 投诉/上报问题
        if (containsAny(input, "投诉", "举报", "信号差", "乱扣费", "服务态度", "垃圾短信", "诈骗", "不满意",
                "上报", "反馈问题", "有问题")) {
            return IntentResult.builder().intent(CustomerIntent.COMPLAINT)
                    .confidence(0.90).slots(slots).build();
        }

        // 5. 售后/维修/损坏
        if (containsAny(input, "售后", "维修", "保修", "换货", "补卡", "SIM卡坏", "手机坏了", "号码丢了", "过户",
                "坏了", "压坏", "损坏", "破损", "质量问题", "摔坏")) {
            return IntentResult.builder().intent(CustomerIntent.AFTER_SALE)
                    .confidence(0.88).slots(slots).build();
        }

        // 6. 订单查询/物流/购物车
        if (containsAny(input, "订单", "物流", "快递", "发货", "配送", "到哪了", "包裹",
                "购物车", "查物流", "到哪儿了", "在哪", "在哪里", "找包裹", "查包裹")) {
            return IntentResult.builder().intent(CustomerIntent.ORDER_QUERY)
                    .confidence(0.90).slots(slots).build();
        }

        // 7. 产品/商品/业务咨询（匹配面最广，放后面）
        if (containsAny(input, "套餐", "流量", "话费", "宽带", "漫游", "充值", "账单",
                "月租", "资费", "办理", "开通", "关闭", "退订", "订购",
                "花卡", "全家享", "5G", "4G", "VoLTE", "eSIM",
                "咪咕", "云盘", "PLUS", "会员", "彩铃", "积分",
                "移动花卡", "飞享", "动感地带", "全球通",
                "停机保号", "销户", "销号", "补卡",
                "查询余额", "查流量", "查话费", "话费余额",
                "国际漫游", "港台", "出国", "境外",
                "加入购物车", "添加到购物车", "加购物车", "商品详情",
                // 银行/金融领域
                "信用卡", "银行卡", "储蓄卡", "借记卡", "余额", "转账",
                "取现", "取款", "额度", "提额", "还款", "利息",
                "开户", "贷款", "理财", "基金", "定期",
                "活期", "网银", "手机银行", "网上银行", "柜台",
                // 电商领域
                "店铺", "商家", "卖家", "上架", "下架", "库存",
                "评价", "好评", "差评", "退货地址",
                "退货", "换货", "退款退货", "仅退款")) {
            return IntentResult.builder().intent(CustomerIntent.PRODUCT_INFO)
                    .confidence(0.88).slots(slots).build();
        }

        // 8. 产品推荐
        if (containsAny(input, "推荐", "选哪个", "哪个好", "哪个划算", "有什么优惠", "活动", "促销")) {
            return IntentResult.builder().intent(CustomerIntent.PRODUCT_RECOMMEND)
                    .confidence(0.85).slots(slots).build();
        }

        // 9. 政策/指引查询
        if (containsAny(input, "政策", "规则", "流程", "规定", "条款", "合约", "协议",
                "指引", "怎么办", "怎么操作", "该如何", "如何办理",
                "怎么查", "哪里查", "去哪里", "在哪看", "怎么看", "在哪里", "查询方法",
                "在哪里查", "去哪查", "从哪查", "如何查询", "怎么查询",
                "收费", "手续费", "年费", "利率", "汇率")) {
            return IntentResult.builder().intent(CustomerIntent.POLICY_QUERY)
                    .confidence(0.85).slots(slots).build();
        }

        // 10. 闲聊（打招呼/感谢/再见）
        if (containsAny(input, "你好", "谢谢", "感谢", "再见", "辛苦了", "你是谁", "hello", "hi")) {
            return IntentResult.builder().intent(CustomerIntent.CHITCHAT)
                    .confidence(0.85).slots(slots).build();
        }

        // 没有关键词匹配，返回 null 让 LLM 来判断
        return null;
    }

    /** 检查输入是否包含列表中任一关键词 */
    private boolean containsAny(String input, String... keywords) {
        for (String kw : keywords) {
            if (input.contains(kw)) return true;
        }
        return false;
    }

    /** 正则预筛提取 */
    private void extractByRegex(String input, Map<String, String> slots) {
        Matcher orderMatcher = ORDER_ID_PATTERN.matcher(input);
        if (orderMatcher.find()) {
            slots.put("order_id", orderMatcher.group(1));
        } else if (PURE_ORDER_ID_PATTERN.matcher(input.trim()).matches()) {
            // 没有前缀但整个输入是 6-20 位字母数字，视为订单号
            slots.put("order_id", input.trim());
        }
        Matcher phoneMatcher = PHONE_PATTERN.matcher(input);
        if (phoneMatcher.find()) {
            slots.put("phone", phoneMatcher.group());
        }
    }

    /** 构建意图分类 Prompt */
    private String buildClassificationPrompt(String userInput, Map<String, String> preExtracted) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个精准的意图识别系统，服务于综合客服场景（电信/银行/电商通用）。请分析用户输入，识别意图并提取关键信息。\n\n");

        sb.append("## 可选意图列表及判定规则\n\n");

        sb.append("1. ORDER_QUERY（订单查询）— 用户要查订单/物流/配送\n");
        sb.append("   示例：「我的订单到哪了」「查一下物流」「订单状态」「快递到了没」「包裹到哪了」\n");
        sb.append("   需要提取：order_id(订单号)\n\n");

        sb.append("2. ORDER_CANCEL（取消订单）— 用户要取消/不要了\n");
        sb.append("   示例：「我要取消订单」「不想要了」「退货」「这单不要了」\n");
        sb.append("   需要提取：order_id(订单号)\n\n");

        sb.append("3. ORDER_REFUND（退款申请）— 用户要退款/退钱\n");
        sb.append("   示例：「我要退款」「申请退款」「退钱」「退款怎么还没到账」「能退款吗」\n");
        sb.append("   需要提取：order_id(订单号), refund_reason(退款原因)\n\n");

        sb.append("4. PRODUCT_INFO（产品咨询）— 用户问套餐/业务/产品详情\n");
        sb.append("   示例：「这个套餐包含什么」「流量包多少钱」「宽带怎么办理」「花卡宝藏版免流有哪些」「国际漫游怎么开」\n");
        sb.append("   需要提取：product_name(产品/套餐/业务名称)\n\n");

        sb.append("5. PRODUCT_RECOMMEND（产品推荐）— 用户要推荐/比较/选哪个\n");
        sb.append("   示例：「推荐个套餐」「哪个套餐划算」「想换个便宜的套餐」「有什么优惠活动」\n\n");

        sb.append("6. COMPLAINT（投诉建议）— 用户投诉/不满/举报\n");
        sb.append("   示例：「我要投诉」「信号太差」「服务态度不好」「乱扣费」「垃圾短信」「诈骗电话」\n");
        sb.append("   需要提取：issue_type(问题类型：物流问题/质量问题/服务态度/虚假宣传/乱扣费/信号差/其他)\n\n");

        sb.append("7. AFTER_SALE（售后服务）— 维修/换货/保修/补卡\n");
        sb.append("   示例：「手机坏了怎么办」「补卡」「SIM卡坏了」「保修期多久」「号码丢了」\n");
        sb.append("   需要提取：issue_type(问题类型)\n\n");

        sb.append("8. POLICY_QUERY（政策查询）— 用户问规则/政策/流程\n");
        sb.append("   示例：「退换货政策」「话费充值规则」「流量结转」「销户流程」「停机保号怎么弄」\n\n");

        sb.append("9. REQUEST_HUMAN（转人工）— 用户要求人工服务\n");
        sb.append("   示例：「转人工」「我要找人工」「人工客服」「不想跟机器人说话」「让真人来」\n\n");

        sb.append("10. CHITCHAT（闲聊）— 纯寒暄/感谢/打招呼/无关话题\n");
        sb.append("    示例：「你好」「谢谢」「辛苦了」「你是谁」「今天天气怎么样」\n\n");

        sb.append("11. CLARIFICATION（用户澄清）— 用户回答系统追问\n");
        sb.append("    示例：用户直接回答了上一轮的追问（如订单号、原因等短文本）\n\n");

        sb.append("12. OUT_OF_SCOPE（超出范围）— 完全与电信/银行/电商客服无关\n");
        sb.append("    示例：「帮我写代码」「讲个笑话」「翻译一下」「推荐电影」\n");
        sb.append("    注意：银行金融类问题（信用卡、转账、账户、贷款等）属于 PRODUCT_INFO 或 POLICY_QUERY，不是超出范围！\n");
        sb.append("    注意：店铺商家类问题（开店、商品上架、库存等）属于 PRODUCT_INFO 或 POLICY_QUERY，不是超出范围！\n\n");

        if (!preExtracted.isEmpty()) {
            sb.append("## 已通过预筛提取到的信息\n");
            preExtracted.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
            sb.append("\n");
        }

        sb.append("## 用户输入\n");
        sb.append(userInput).append("\n\n");

        sb.append("## 输出格式\n");
        sb.append("请严格按以下 JSON 格式回复，不要包含任何其他内容：\n");
        sb.append("{\"intent\":\"意图名称\",\"confidence\":0.95,\"slots\":{\"key\":\"value\"},");
        sb.append("\"needsClarification\":false,\"clarificationQuestion\":\"\"}\n\n");

        sb.append("## 判定规则（必须严格遵守）\n");
        sb.append("1. confidence 范围 0.0-1.0。意图非常明确时 0.9+，比较明确 0.7-0.9，模糊 0.5-0.7，不确定 <0.5\n");
        sb.append("2. 只有 confidence < 0.5 时才设 needsClarification=true\n");
        sb.append("3. 如果缺少必填槽位但意图明确，confidence 仍应较高（≥0.7），不要因缺少槽位而降低意图置信度\n");
        sb.append("4. 用户要求转人工/找真人/不要机器人，一律归类为 REQUEST_HUMAN\n");
        sb.append("5. 不在上述意图范围内的，归类为 OUT_OF_SCOPE\n");
        sb.append("6. 优先匹配最具体的意图。例如「退款」是 ORDER_REFUND 而非 ORDER_CANCEL\n");
        sb.append("7. 关于移动业务的问题（套餐、流量、话费、宽带、漫游等）优先归类为 PRODUCT_INFO 或 POLICY_QUERY\n");

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
            ChatResponse response = openAiChatModel.call(prompt);
            String text = response.getResult().getOutput().getText();
            log.info("[IntentClassifier] LLM 原始响应: {}", text);
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

    /**
     * 带对话上下文的意图分类 Prompt
     * 让 LLM 了解之前的对话状态，避免短回答被误分类
     */
    private String buildClassificationPromptWithContext(String userInput, Map<String, String> preExtracted,
                                                         DialogState dialogState) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个精准的意图识别系统，服务于综合客服场景（电信/银行/电商通用）。请结合对话上下文分析用户输入。\n\n");

        sb.append("## 对话上下文\n");
        sb.append("- 当前意图：").append(dialogState.getCurrentIntent().name())
          .append("（").append(dialogState.getCurrentIntent().getDisplayName()).append("）\n");
        sb.append("- 对话阶段：").append(dialogState.getDialogPhase()).append("\n");
        if (dialogState.getLastBotMessage() != null) {
            sb.append("- 系统上次的提问：").append(dialogState.getLastBotMessage()).append("\n");
        }
        if (!dialogState.getFilledSlots().isEmpty()) {
            sb.append("- 已收集信息：");
            dialogState.getFilledSlots().forEach((k, v) -> sb.append(k).append("=").append(v).append("; "));
            sb.append("\n");
        }

        sb.append("\n## 可选意图（仅列出关键意图，完整列表参考无上下文版本）\n");
        sb.append("ORDER_QUERY, ORDER_CANCEL, ORDER_REFUND, PRODUCT_INFO, PRODUCT_RECOMMEND,\n");
        sb.append("COMPLAINT, AFTER_SALE, POLICY_QUERY, REQUEST_HUMAN, CHITCHAT, CLARIFICATION, OUT_OF_SCOPE\n\n");

        if (!preExtracted.isEmpty()) {
            sb.append("## 已通过预筛提取到的信息\n");
            preExtracted.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
            sb.append("\n");
        }

        sb.append("## 用户输入\n");
        sb.append(userInput).append("\n\n");

        sb.append("## 输出格式\n");
        sb.append("请严格按以下 JSON 格式回复，不要包含任何其他内容：\n");
        sb.append("{\"intent\":\"意图名称\",\"confidence\":0.95,\"slots\":{\"key\":\"value\"},");
        sb.append("\"needsClarification\":false,\"clarificationQuestion\":\"\"}\n\n");

        sb.append("## 关键规则（必须严格遵守）\n");
        sb.append("1. 如果用户输入是对系统上次提问的直接回答（如订单号、原因等短文本），保持当前意图不变，从输入中提取槽位\n");
        sb.append("2. 短文本在对话上下文中回答提问时，不要将其归类为 CHITCHAT 或 OUT_OF_SCOPE\n");
        sb.append("3. 只有当用户明确提出与当前意图完全无关的新话题时，才切换意图\n");
        sb.append("4. 用户要求转人工/找真人/不要机器人，一律归类为 REQUEST_HUMAN\n");
        sb.append("5. confidence 范围 0.0-1.0。意图非常明确时 0.9+，比较明确 0.7-0.9\n");
        sb.append("6. 缺少必填槽位但不影响意图判定时，confidence 仍应 ≥ 0.7\n");

        return sb.toString();
    }

    /**
     * 轻量级槽位提取（不做完整意图分类）
     * 用于 SLOT_FILLING / CLARIFYING 阶段，直接从用户回答中提取槽位
     */
    public SlotExtractionResult extractSlots(String userInput, String lastBotMessage,
                                              CustomerIntent currentIntent, Map<String, String> filledSlots,
                                              List<String> pendingSlots) {
        log.info("[extractSlots] 意图={}, 待填充={}, 输入={}", currentIntent, pendingSlots, userInput);

        List<SlotDefinition> slotDefs = intentConfig.getSlotDefinitions(currentIntent.name());

        // 正则预筛
        Map<String, String> regexSlots = new HashMap<>();
        extractByRegex(userInput, regexSlots);

        StringBuilder sb = new StringBuilder();
        sb.append("你是一个槽位提取系统。请从用户的回答中提取缺失的信息。\n\n");

        sb.append("## 当前意图\n");
        sb.append(currentIntent.name()).append("（").append(currentIntent.getDisplayName()).append("）\n");
        sb.append("需要收集的槽位：");
        for (SlotDefinition sd : slotDefs) {
            sb.append(sd.getSlotName()).append("(").append(sd.getDescription());
            if (sd.isRequired()) sb.append(", 必填");
            sb.append("); ");
        }
        sb.append("\n");

        if (!filledSlots.isEmpty()) {
            sb.append("\n## 已收集信息\n");
            filledSlots.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        }

        sb.append("\n## 待收集槽位\n");
        for (String slot : pendingSlots) {
            sb.append("- ").append(slot).append("\n");
        }

        if (!regexSlots.isEmpty()) {
            sb.append("\n预筛提取：");
            regexSlots.forEach((k, v) -> sb.append(k).append("=").append(v).append("; "));
            sb.append("\n");
        }

        sb.append("\n## 系统上次提问\n");
        sb.append(lastBotMessage).append("\n");

        sb.append("\n## 用户回答\n");
        sb.append(userInput).append("\n\n");

        sb.append("请严格按以下 JSON 格式回复：\n");
        sb.append("{\"extracted\":{\"slot_name\":\"value\"},\"is_topic_change\":false}\n\n");
        sb.append("规则：\n");
        sb.append("1. 从用户回答中提取待收集槽位的值，放入 extracted\n");
        sb.append("2. 如果用户完全偏离话题（如提出新的不相关请求），设置 is_topic_change=true\n");
        sb.append("3. 如果用户回答了部分信息，只提取能确定的部分\n");
        sb.append("4. 不要猜测，只提取用户明确提供的信息\n");

        String llmResponse = callLlm(sb.toString());
        return parseSlotExtractionResult(llmResponse, regexSlots);
    }

    /** 解析槽位提取结果 */
    private SlotExtractionResult parseSlotExtractionResult(String llmResponse, Map<String, String> regexSlots) {
        try {
            String json = extractJson(llmResponse);
            String extractedStr = extractJsonValue(json, "extracted");
            String topicChangeStr = extractJsonValue(json, "is_topic_change");

            Map<String, String> extracted = new HashMap<>(regexSlots);
            parseSlotsFromJson(extractedStr, extracted);

            boolean topicChange = "true".equalsIgnoreCase(topicChangeStr);

            return SlotExtractionResult.builder()
                    .extracted(extracted)
                    .topicChange(topicChange)
                    .rawResponse(llmResponse)
                    .build();
        } catch (Exception e) {
            log.error("[extractSlots] 解析失败，原始响应: {}", llmResponse, e);
            // 解析失败时返回预筛结果，不标记话题切换
            return SlotExtractionResult.builder()
                    .extracted(regexSlots)
                    .topicChange(false)
                    .rawResponse(llmResponse)
                    .build();
        }
    }
}
