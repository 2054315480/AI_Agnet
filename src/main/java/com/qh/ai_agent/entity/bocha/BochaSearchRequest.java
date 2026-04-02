package com.qh.ai_agent.entity.bocha;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 博查AI搜索请求实体类
 */
@Data
public class BochaSearchRequest {

    /**
     * 搜索关键字或语句
     */
    @JsonProperty("query")
    private String query;

    /**
     * 是否在搜索结果中包含摘要
     */
    @JsonProperty("summary")
    private Boolean summary;

    /**
     * 搜索指定时间范围内的网页（可选值 "noLimit"、"oneDay"、"oneWeek"、"oneMonth"、"oneYear"）
     */
    @JsonProperty("freshness")
    private String freshness;

    /**
     * 返回的搜索结果数量（1-50），默认为10
     */
    @JsonProperty("count")
    private Integer count;
}
