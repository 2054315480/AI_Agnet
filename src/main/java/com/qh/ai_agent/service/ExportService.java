package com.qh.ai_agent.service;

import com.qh.ai_agent.entity.Conversation;
import com.qh.ai_agent.entity.ConversationMessage;
import com.qh.ai_agent.mapper.ConversationMapper;
import com.qh.ai_agent.mapper.ConversationMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;

/**
 * 对话日志导出服务
 * 使用手写 CSV 输出（无额外依赖）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;

    /** 导出全部对话日志为 CSV */
    public void exportAllCsv(Writer writer) throws IOException {
        List<Conversation> conversations = conversationMapper.selectList(null);
        writeCsvHeader(writer);

        for (Conversation conv : conversations) {
            List<ConversationMessage> messages = messageMapper.findByConversationId(conv.getId());
            writeConversationRows(writer, conv, messages);
        }
        writer.flush();
        log.info("[Export] 全量导出完成，共 {} 个会话", conversations.size());
    }

    /** 导出单个会话对话日志为 CSV */
    public void exportSessionCsv(String conversationId, Writer writer) throws IOException {
        Conversation conv = conversationMapper.selectById(conversationId);
        List<ConversationMessage> messages = messageMapper.findByConversationId(conversationId);

        writeCsvHeader(writer);
        if (conv != null) {
            writeConversationRows(writer, conv, messages);
        }
        writer.flush();
        log.info("[Export] 会话 {} 导出完成，共 {} 条消息", conversationId, messages.size());
    }

    private void writeCsvHeader(Writer writer) throws IOException {
        writer.write("会话ID,会话标题,消息ID,角色,内容,意图,槽位,置信度,转人工,澄清,来源,创建时间\n");
    }

    private void writeConversationRows(Writer writer, Conversation conv,
                                        List<ConversationMessage> messages) throws IOException {
        for (ConversationMessage msg : messages) {
            writer.write(csvEscape(conv.getId()));
            writer.write(",");
            writer.write(csvEscape(conv.getTitle()));
            writer.write(",");
            writer.write(String.valueOf(msg.getId()));
            writer.write(",");
            writer.write(csvEscape(msg.getRole()));
            writer.write(",");
            writer.write(csvEscape(msg.getContent()));
            writer.write(",");
            writer.write(csvEscape(msg.getIntent()));
            writer.write(",");
            writer.write(csvEscape(msg.getSlots()));
            writer.write(",");
            writer.write(msg.getConfidence() != null ? String.format("%.2f", msg.getConfidence()) : "");
            writer.write(",");
            writer.write(Boolean.TRUE.equals(msg.getIsHandoff()) ? "是" : "否");
            writer.write(",");
            writer.write(Boolean.TRUE.equals(msg.getIsClarification()) ? "是" : "否");
            writer.write(",");
            writer.write(csvEscape(msg.getSources()));
            writer.write(",");
            writer.write(msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : "");
            writer.write("\n");
        }
    }

    /** CSV 字段转义：处理逗号、引号、换行 */
    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"").replace("\n", " ").replace("\r", "") + "\"";
        }
        return value;
    }
}
