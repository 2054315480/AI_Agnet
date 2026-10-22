package com.qh.ai_agent.kg;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 实体与关系抽取（规则优先）。
 *
 * 为什么不直接上 LLM 抽取：
 * 1. 规则零成本、毫秒级、完全可复现，适合作为基线
 * 2. 客服域实体类型有限（产品/部件/政策/问题/动作/渠道/时长），词典 + 正则覆盖率高
 * 3. LLM 抽取留作增强开关，用评测集判断是否值得
 *
 * 实体类型：PRODUCT / PART / POLICY / ISSUE / ACTION / CHANNEL / DURATION
 */
@Slf4j
@Component
public class KgExtractor {

    /** 动作词典 */
    private static final Map<String, String[]> ACTIONS = new LinkedHashMap<>();
    static {
        ACTIONS.put("退货", new String[]{"退款", "退钱", "退回"});
        ACTIONS.put("换货", new String[]{"换新", "更换"});
        ACTIONS.put("维修", new String[]{"报修", "修理", "修"});
        ACTIONS.put("退款", new String[]{"退钱", "返还"});
        ACTIONS.put("取消", new String[]{"撤销"});
        ACTIONS.put("投诉", new String[]{"举报", "反馈"});
    }

    /** 问题/症状词典 */
    private static final String[] ISSUE_WORDS = {
            "没声音", "无声音", "故障", "坏了", "损坏", "无法使用", "不能用", "连不上",
            "充不进电", "断连", "卡顿", "发热", "漏液", "破损", "少件", "错发", "未收到"
    };

    /** 渠道词典 */
    private static final String[] CHANNELS = {
            "客服电话", "客服热线", "官网", "APP", "小程序", "邮箱", "在线客服", "门店"
    };

    /** 政策词典 */
    private static final String[] POLICIES = {
            "退货政策", "换货政策", "保修政策", "退款政策", "配送政策", "发票政策", "价格保护"
    };

    /** 产品/部件词典（可从 KB 标题动态扩充） */
    private static final String[] PRODUCTS = {
            "耳机", "蓝牙耳机", "充电宝", "充电器", "数据线", "手机", "平板", "笔记本",
            "路由器", "音箱", "手表", "手环"
    };
    private static final String[] PARTS = {
            "左耳", "右耳", "耳塞", "充电盒", "充电仓", "线控", "接口", "屏幕", "电池"
    };

    /** 时长：数字 + 时间单位 */
    private static final Pattern DURATION = Pattern.compile("(\\d{1,3})\\s*(天|日|小时|个工作日|工作日|个月|年)");

    /** 一次抽取结果 */
    public record Extracted(List<Entity> entities, List<Relation> relations) {
    }

    public record Entity(String name, String type, String normalized) {
    }

    public record Relation(String source, String target, String relation) {
    }

    /**
     * 从一条知识条目中抽取实体与关系。
     */
    public Extracted extract(String kbId, String question, String answer) {
        String text = ((question == null ? "" : question) + " " + (answer == null ? "" : answer));
        Map<String, Entity> entityMap = new LinkedHashMap<>();

        // 1) 词典匹配
        matchDict(text, ACTIONS.keySet(), "ACTION", entityMap);
        for (Map.Entry<String, String[]> e : ACTIONS.entrySet()) {
            matchDict(text, Arrays.asList(e.getValue()), "ACTION", entityMap);
        }
        matchDict(text, Arrays.asList(ISSUE_WORDS), "ISSUE", entityMap);
        matchDict(text, Arrays.asList(CHANNELS), "CHANNEL", entityMap);
        matchDict(text, Arrays.asList(POLICIES), "POLICY", entityMap);
        matchDict(text, Arrays.asList(PRODUCTS), "PRODUCT", entityMap);
        matchDict(text, Arrays.asList(PARTS), "PART", entityMap);

        // 2) 正则抽时长
        Matcher m = DURATION.matcher(text);
        while (m.find()) {
            String name = m.group(1) + m.group(2);
            entityMap.putIfAbsent(name, new Entity(name, "DURATION", normalize(name)));
        }

        // 3) 规则关系：同一句里 问题 -> 动作、产品 -> 政策、动作 -> 时长
        List<Relation> relations = new ArrayList<>();
        List<Entity> issues = filter(entityMap, "ISSUE");
        List<Entity> actions = filter(entityMap, "ACTION");
        List<Entity> policies = filter(entityMap, "POLICY");
        List<Entity> products = filter(entityMap, "PRODUCT");
        List<Entity> durations = filter(entityMap, "DURATION");

        for (Entity issue : issues) {
            for (Entity action : actions) {
                relations.add(new Relation(issue.name(), action.name(), "resolved_by"));
            }
        }
        for (Entity product : products) {
            for (Entity policy : policies) {
                relations.add(new Relation(product.name(), policy.name(), "has_policy"));
            }
        }
        for (Entity action : actions) {
            for (Entity duration : durations) {
                relations.add(new Relation(action.name(), duration.name(), "requires"));
            }
        }

        // 通用共现关系：同一 KB 条目里出现的不同类型实体至少建立 co_occurs（保底，避免关系数为 0）
        List<Entity> all = new ArrayList<>(entityMap.values());
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                Entity a = all.get(i);
                Entity b = all.get(j);
                if (a.type().equals(b.type())) {
                    continue;
                }
                relations.add(new Relation(a.name(), b.name(), "co_occurs"));
            }
        }

        return new Extracted(new ArrayList<>(entityMap.values()), relations);
    }

    private void matchDict(String text, Collection<String> words, String type, Map<String, Entity> out) {
        for (String w : words) {
            // 单字词（修/查/换…）在本域几乎必然过宽：doc_freq 极高、对排序零区分度，
            // 之前线上就抽出过 doc_freq=78 的实体"修"，把整张图变成了噪声源，这里直接跳过。
            if (w == null || w.trim().length() < 2) {
                continue;
            }
            if (text.contains(w)) {
                out.putIfAbsent(w, new Entity(w, type, normalize(w)));
            }
        }
    }

    private List<Entity> filter(Map<String, Entity> map, String type) {
        List<Entity> list = new ArrayList<>();
        for (Entity e : map.values()) {
            if (type.equals(e.type())) {
                list.add(e);
            }
        }
        return list;
    }

    /** 归一化：去空格、转小写、去标点 */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("[\\s\\p{Punct}，。！？、；：（）【】《》]", "").toLowerCase();
    }
}
