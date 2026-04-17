package com.qh.ai_agent.config;

import com.qh.ai_agent.service.SensitiveInfoService;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 敏感信息脱敏配置
 */
@Configuration
@ConfigurationProperties(prefix = "ai.sensitive-info")
@Data
public class SensitiveInfoConfig {

    /**
     * 是否启用敏感信息脱敏
     */
    private boolean enabled = true;

    /**
     * 是否脱敏手机号
     */
    private boolean maskPhone = true;

    /**
     * 是否脱敏身份证号
     */
    private boolean maskIdCard = true;

    /**
     * 是否脱敏邮箱地址
     */
    private boolean maskEmail = true;

    /**
     * 是否脱敏银行卡号
     */
    private boolean maskBankCard = true;

    /**
     * 将配置应用到 SensitiveInfoService
     */
    public void applyTo(SensitiveInfoService service) {
        service.setMaskPhone(this.maskPhone);
        service.setMaskIdCard(this.maskIdCard);
        service.setMaskEmail(this.maskEmail);
        service.setMaskBankCard(this.maskBankCard);
    }
}
