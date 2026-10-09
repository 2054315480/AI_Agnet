package com.qh.ai_agent.dialog.store;

import com.qh.ai_agent.dialog.model.DialogState;

import java.util.function.Supplier;

/**
 * 会话状态存储抽象。
 *
 * 把"会话状态放哪里"从 DialogStateManager 里解耦出来：
 * - InMemoryDialogStateStore：单实例内存，带 TTL 与容量上限（默认）
 * - RedisDialogStateStore：多实例共享 + TTL（cs.session.store=redis 时启用）
 */
public interface DialogStateStore {

    /** 读取会话状态，不存在或已过期返回 null */
    DialogState get(String sessionId);

    /** 写入/覆盖会话状态，并按实现刷新 TTL */
    void put(String sessionId, DialogState state);

    /** 删除会话状态，返回被删除的对象（没有则 null） */
    DialogState remove(String sessionId);

    /** 当前会话数；实现无法统计时返回 -1 */
    int size();

    /** 存储实现描述，便于日志与排障 */
    String describe();

    /** 取不到就创建并写入。默认实现非原子，实现类可覆盖为原子版本。 */
    default DialogState getOrCreate(String sessionId, Supplier<DialogState> factory) {
        DialogState exist = get(sessionId);
        if (exist != null) {
            return exist;
        }
        DialogState created = factory.get();
        put(sessionId, created);
        return created;
    }
}
