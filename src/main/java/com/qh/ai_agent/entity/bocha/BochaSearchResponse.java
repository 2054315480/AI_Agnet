package com.qh.ai_agent.entity.bocha;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 博查AI搜索响应实体类
 */
@Data
public class BochaSearchResponse {

    @JsonProperty("code")
    private Integer code;

    @JsonProperty("log_id")
    private String logId;

    @JsonProperty("msg")
    private String msg;

    @JsonProperty("data")
    private SearchData data;

    @Data
    public static class SearchData {
        @JsonProperty("_type")
        private String type;

        @JsonProperty("queryContext")
        private QueryContext queryContext;

        @JsonProperty("webPages")
        private WebPages webPages;

        @JsonProperty("images")
        private Images images;

        @JsonProperty("videos")
        private Videos videos;
    }

    @Data
    public static class QueryContext {
        @JsonProperty("originalQuery")
        private String originalQuery;
    }

    @Data
    public static class WebPages {
        @JsonProperty("webSearchUrl")
        private String webSearchUrl;

        @JsonProperty("totalEstimatedMatches")
        private Long totalEstimatedMatches;

        @JsonProperty("value")
        private List<WebPage> value;

        @JsonProperty("someResultsRemoved")
        private Boolean someResultsRemoved;
    }

    @Data
    public static class WebPage {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("url")
        private String url;

        @JsonProperty("displayUrl")
        private String displayUrl;

        @JsonProperty("snippet")
        private String snippet;

        @JsonProperty("summary")
        private String summary;

        @JsonProperty("siteName")
        private String siteName;

        @JsonProperty("siteIcon")
        private String siteIcon;

        @JsonProperty("dateLastCrawled")
        private String dateLastCrawled;

        @JsonProperty("datePublished")
        private String datePublished;

        @JsonProperty("cachedPageUrl")
        private String cachedPageUrl;

        @JsonProperty("language")
        private String language;

        @JsonProperty("isFamilyFriendly")
        private Boolean isFamilyFriendly;

        @JsonProperty("isNavigational")
        private Boolean isNavigational;
    }

    @Data
    public static class Images {
        @JsonProperty("id")
        private String id;

        @JsonProperty("readLink")
        private String readLink;

        @JsonProperty("webSearchUrl")
        private String webSearchUrl;

        @JsonProperty("value")
        private List<Image> value;

        @JsonProperty("isFamilyFriendly")
        private Boolean isFamilyFriendly;
    }

    @Data
    public static class Image {
        @JsonProperty("webSearchUrl")
        private String webSearchUrl;

        @JsonProperty("name")
        private String name;

        @JsonProperty("thumbnailUrl")
        private String thumbnailUrl;

        @JsonProperty("datePublished")
        private String datePublished;

        @JsonProperty("contentUrl")
        private String contentUrl;

        @JsonProperty("hostPageUrl")
        private String hostPageUrl;

        @JsonProperty("contentSize")
        private String contentSize;

        @JsonProperty("encodingFormat")
        private String encodingFormat;

        @JsonProperty("hostPageDisplayUrl")
        private String hostPageDisplayUrl;

        @JsonProperty("width")
        private Integer width;

        @JsonProperty("height")
        private Integer height;
    }

    @Data
    public static class Videos {
        @JsonProperty("id")
        private String id;

        @JsonProperty("readLink")
        private String readLink;

        @JsonProperty("webSearchUrl")
        private String webSearchUrl;

        @JsonProperty("isFamilyFriendly")
        private Boolean isFamilyFriendly;

        @JsonProperty("scenario")
        private String scenario;

        @JsonProperty("value")
        private List<Video> value;
    }

    @Data
    public static class Video {
        @JsonProperty("webSearchUrl")
        private String webSearchUrl;

        @JsonProperty("name")
        private String name;

        @JsonProperty("description")
        private String description;

        @JsonProperty("thumbnailUrl")
        private String thumbnailUrl;

        @JsonProperty("publisher")
        private List<Publisher> publisher;

        @JsonProperty("creator")
        private Creator creator;

        @JsonProperty("contentUrl")
        private String contentUrl;

        @JsonProperty("hostPageUrl")
        private String hostPageUrl;

        @JsonProperty("encodingFormat")
        private String encodingFormat;

        @JsonProperty("hostPageDisplayUrl")
        private String hostPageDisplayUrl;

        @JsonProperty("width")
        private Integer width;

        @JsonProperty("height")
        private Integer height;

        @JsonProperty("duration")
        private String duration;

        @JsonProperty("motionThumbnailUrl")
        private String motionThumbnailUrl;

        @JsonProperty("embedHtml")
        private String embedHtml;

        @JsonProperty("allowHttpsEmbed")
        private Boolean allowHttpsEmbed;

        @JsonProperty("viewCount")
        private Long viewCount;

        @JsonProperty("thumbnail")
        private Thumbnail thumbnail;

        @JsonProperty("allowMobileEmbed")
        private Boolean allowMobileEmbed;

        @JsonProperty("isSuperfresh")
        private Boolean isSuperfresh;

        @JsonProperty("datePublished")
        private String datePublished;
    }

    @Data
    public static class Publisher {
        @JsonProperty("name")
        private String name;
    }

    @Data
    public static class Creator {
        @JsonProperty("name")
        private String name;
    }

    @Data
    public static class Thumbnail {
        @JsonProperty("height")
        private Integer height;

        @JsonProperty("width")
        private Integer width;
    }
}
