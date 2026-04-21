package com.qh.ai_agent.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.qh.ai_agent.config.OssConfig;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

@Service
@Slf4j
public class OssService {

    private final OssConfig ossConfig;
    private OSS ossClient;

    public OssService(OssConfig ossConfig) {
        this.ossConfig = ossConfig;
    }

    public boolean isEnabled() {
        return ossConfig.getEndpoint() != null && !ossConfig.getEndpoint().isEmpty()
                && ossConfig.getAccessKeyId() != null && !ossConfig.getAccessKeyId().isEmpty();
    }

    private OSS getClient() {
        if (ossClient == null) {
            ossClient = new OSSClientBuilder().build(
                    ossConfig.getEndpoint(),
                    ossConfig.getAccessKeyId(),
                    ossConfig.getAccessKeySecret()
            );
        }
        return ossClient;
    }

    public String uploadFile(String objectKey, File file) {
        try (InputStream is = new FileInputStream(file)) {
            getClient().putObject(ossConfig.getBucket(), objectKey, is);
            String url = buildUrl(objectKey);
            log.info("OSS 上传成功: {} -> {}", objectKey, url);
            return url;
        } catch (Exception e) {
            log.error("OSS 上传失败: {}", e.getMessage());
            throw new RuntimeException("OSS 上传失败: " + e.getMessage(), e);
        }
    }

    public String uploadBytes(String objectKey, byte[] data, String contentType) {
        try {
            com.aliyun.oss.model.ObjectMetadata meta = new com.aliyun.oss.model.ObjectMetadata();
            meta.setContentType(contentType);
            meta.setContentLength(data.length);
            getClient().putObject(ossConfig.getBucket(), objectKey, new java.io.ByteArrayInputStream(data), meta);
            String url = buildUrl(objectKey);
            log.info("OSS 上传成功: {} -> {}", objectKey, url);
            return url;
        } catch (Exception e) {
            log.error("OSS 上传失败: {}", e.getMessage());
            throw new RuntimeException("OSS 上传失败: " + e.getMessage(), e);
        }
    }

    private String buildUrl(String objectKey) {
        if (ossConfig.getUrlPrefix() != null && !ossConfig.getUrlPrefix().isEmpty()) {
            return ossConfig.getUrlPrefix() + "/" + objectKey;
        }
        return "https://" + ossConfig.getBucket() + "." + ossConfig.getEndpoint() + "/" + objectKey;
    }

    @PreDestroy
    public void cleanup() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }
}
