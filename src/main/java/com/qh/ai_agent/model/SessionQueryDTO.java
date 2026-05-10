package com.qh.ai_agent.model;

import lombok.Data;

/**
 * 会话列表查询参数
 */
@Data
public class SessionQueryDTO {

    /** 页码（从 1 开始） */
    private Integer page = 1;

    /** 每页条数 */
    private Integer size = 15;

    /** 排序字段：created_at, total_turns, avg_confidence, clarification_count */
    private String sortBy = "created_at";

    /** 排序方向：asc / desc */
    private String sortOrder = "desc";

    /** 按意图类型筛选（可选） */
    private String intent;

    /** 按是否转人工筛选（可选） */
    private Boolean handoff;

    /** 按是否有澄清筛选（可选） */
    private Boolean clarification;
}
