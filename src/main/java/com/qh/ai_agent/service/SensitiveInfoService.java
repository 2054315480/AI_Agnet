package com.qh.ai_agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感信息检测与脱敏服务
 * 支持检测和脱敏：手机号、身份证号、邮箱地址、银行卡号
 */
@Slf4j
@Service
public class SensitiveInfoService {

    // 中国手机号：1[3-9]开头的11位数字
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(1[3-9]\\d)\\d{4}(\\d{4})");

    // 中国身份证号：18位数字（最后一位可能是X）
    private static final Pattern ID_CARD_PATTERN =
            Pattern.compile("([1-9]\\d{2})\\d{11}(\\d{4}[\\dXx])");

    // 邮箱地址
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("(\\w{2})[\\w.+-]*(@[\\w.-]+\\.[a-zA-Z]{2,})");

    // 银行卡号：16-19位连续数字
    private static final Pattern BANK_CARD_PATTERN =
            Pattern.compile("(\\d{4})\\d{8,11}(\\d{4})");

    private boolean maskPhone = true;
    private boolean maskIdCard = true;
    private boolean maskEmail = true;
    private boolean maskBankCard = true;

    public void setMaskPhone(boolean maskPhone) {
        this.maskPhone = maskPhone;
    }

    public void setMaskIdCard(boolean maskIdCard) {
        this.maskIdCard = maskIdCard;
    }

    public void setMaskEmail(boolean maskEmail) {
        this.maskEmail = maskEmail;
    }

    public void setMaskBankCard(boolean maskBankCard) {
        this.maskBankCard = maskBankCard;
    }

    /**
     * 检测文本中是否包含敏感信息
     */
    public boolean containsSensitiveInfo(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (maskPhone && PHONE_PATTERN.matcher(text).find()) {
            return true;
        }
        if (maskIdCard && ID_CARD_PATTERN.matcher(text).find()) {
            return true;
        }
        if (maskEmail && EMAIL_PATTERN.matcher(text).find()) {
            return true;
        }
        if (maskBankCard && BANK_CARD_PATTERN.matcher(text).find()) {
            return true;
        }
        return false;
    }

    /**
     * 对文本中的敏感信息进行脱敏
     */
    public String maskSensitiveInfo(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        if (maskPhone) {
            result = PHONE_PATTERN.matcher(result).replaceAll("$1****$2");
        }
        if (maskIdCard) {
            result = ID_CARD_PATTERN.matcher(result).replaceAll("$1***********$2");
        }
        if (maskEmail) {
            result = EMAIL_PATTERN.matcher(result).replaceAll("$1***$2");
        }
        if (maskBankCard) {
            result = BANK_CARD_PATTERN.matcher(result).replaceAll("$1********$2");
        }
        return result;
    }

    /**
     * 查找文本中所有匹配的敏感信息类型
     */
    public List<String> findSensitiveTypes(String text) {
        List<String> types = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return types;
        }
        if (maskPhone && PHONE_PATTERN.matcher(text).find()) {
            types.add("手机号");
        }
        if (maskIdCard && ID_CARD_PATTERN.matcher(text).find()) {
            types.add("身份证号");
        }
        if (maskEmail && EMAIL_PATTERN.matcher(text).find()) {
            types.add("邮箱地址");
        }
        if (maskBankCard && BANK_CARD_PATTERN.matcher(text).find()) {
            types.add("银行卡号");
        }
        return types;
    }
}
