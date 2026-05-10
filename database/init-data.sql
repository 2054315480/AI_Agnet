-- ============================================================
-- AI智能客服系统 - 初始种子数据
-- 包含默认管理员用户和测试用户
-- ============================================================

-- 管理员用户 (admin / admin123)
INSERT INTO users (username, password_hash, nickname, role) VALUES ('admin', '$2a$10$5xhoq2.8dpbOO720zJXPB.4xu2xMaR7eobB7EFtaq/p8Co8nttKry', '管理员', 'admin') ON CONFLICT (username) DO NOTHING;

-- 测试用户 (密码都是 123456)
INSERT INTO users (username, password_hash, nickname, role) VALUES ('testuser', '$2a$10$nyawkCON7Z5IazA2zWhbqeuAJIR1U.UH.3CEAUXfbEwd6VMTg6Wj.', '测试用户', 'user') ON CONFLICT (username) DO NOTHING;
INSERT INTO users (username, password_hash, nickname, role) VALUES ('qiuhe', '$2a$10$mQrYqSD/A2dKLizF1..Ob.hgDVBZSf2yqVgW3vle7kUWzpaXHJbBW', 'qiuhe', 'user') ON CONFLICT (username) DO NOTHING;
