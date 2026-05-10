#!/bin/bash
set -e

cd /home/puaiuc/AI_Agent

# 加载环境变量
set -a
[ -f .env ] && source .env
set +a

POSTGRES_DB=${POSTGRES_DB:-Love_Agent}
POSTGRES_USER=${POSTGRES_USER:-postgres}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-postgres}
POSTGRES_PORT=${POSTGRES_PORT:-5432}
FRONTEND_PORT=${FRONTEND_PORT:-80}

echo "============================================"
echo "  AI 智能客服系统 - 一键部署 (原生)"
echo "============================================"

# ============================================
# 1. 安装系统依赖
# ============================================
echo "[1/9] 安装系统依赖..."

# 恢复 apt 源（如果之前被改坏了）
if ! grep -q "archive.ubuntu.com" /etc/apt/sources.list 2>/dev/null; then
    cat > /etc/apt/sources.list << 'APTSRC'
deb http://archive.ubuntu.com/ubuntu/ noble main restricted universe multiverse
deb http://archive.ubuntu.com/ubuntu/ noble-updates main restricted universe multiverse
deb http://archive.ubuntu.com/ubuntu/ noble-backports main restricted universe multiverse
deb http://security.ubuntu.com/ubuntu/ noble-security main restricted universe multiverse
APTSRC
    apt update
fi

# 安装基础依赖
apt install -y openjdk-21-jre-headless maven nodejs npm redis-server nginx 2>/dev/null

# 安装 PostgreSQL + pgvector
apt install -y postgresql postgresql-client postgresql-16-pgvector 2>/dev/null

echo "[OK] 依赖安装完成"

# ============================================
# 2. 配置 PostgreSQL + pgvector
# ============================================
echo "[2/9] 配置 PostgreSQL..."
systemctl start postgresql
systemctl enable postgresql 2>/dev/null || true

# 创建数据库用户
su - postgres -c "psql -tc \"SELECT 1 FROM pg_roles WHERE rolname='$POSTGRES_USER'\"" 2>/dev/null | grep -q 1 || \
    su - postgres -c "psql -c \"CREATE ROLE $POSTGRES_USER WITH LOGIN PASSWORD '$POSTGRES_PASSWORD' SUPERUSER;\""

# 创建数据库（如果不存在）
su - postgres -c "psql -tc \"SELECT 1 FROM pg_database WHERE datname='$POSTGRES_DB'\"" 2>/dev/null | grep -q 1 || \
    su - postgres -c "psql -c \"CREATE DATABASE \\\"$POSTGRES_DB\\\" OWNER $POSTGRES_USER;\""

# 启用 pgvector 扩展
su - postgres -c "psql -d \"$POSTGRES_DB\" -c 'CREATE EXTENSION IF NOT EXISTS vector;'" 2>&1 || {
    echo "[ERROR] pgvector 扩展启用失败！"
    echo "请检查: apt install postgresql-16-pgvector -y"
    exit 1
}
echo "[OK] pgvector 扩展已启用"

# 修改认证方式（允许密码登录）
PG_HBA=$(su - postgres -c "psql -t -c 'SHOW hba_file;'" 2>/dev/null | tr -d ' ')
if [ -n "$PG_HBA" ]; then
    sed -i 's/local\s\+all\s\+all\s\+peer/local   all             all                                     md5/' "$PG_HBA" 2>/dev/null || true
    systemctl reload postgresql 2>/dev/null || true
fi

# 设置 postgres 用户密码
su - postgres -c "psql -c \"ALTER USER postgres PASSWORD '$POSTGRES_PASSWORD';\"" 2>/dev/null || true

echo "[OK] PostgreSQL 配置完成"

# ============================================
# 3. 初始化数据库表和数据
# ============================================
echo "[3/8] 初始化数据库..."

# 运行建表脚本
if [ -f database/schema.sql ]; then
    su - postgres -c "psql -d $POSTGRES_DB -f /home/puaiuc/AI_Agent/database/schema.sql" 2>&1 || echo "[!] 部分表可能已存在，跳过"
    echo "[OK] 数据库表创建完成"
else
    echo "[!] 未找到 database/schema.sql，跳过建表"
fi

# 导入种子数据（用户、历史会话等）
if [ -f database/seed-data.sql ]; then
    su - postgres -c "psql -d $POSTGRES_DB -f /home/puaiuc/AI_Agent/database/seed-data.sql" 2>&1 || true
    echo "[OK] 种子数据导入完成"
fi

# 导入初始用户数据（管理员 + 测试用户）
if [ -f database/init-data.sql ]; then
    su - postgres -c "psql -d $POSTGRES_DB -f /home/puaiuc/AI_Agent/database/init-data.sql" 2>&1 || true
    echo "[OK] 初始用户导入完成"
else
    echo "[!] 未找到 database/init-data.sql，跳过用户初始化"
fi

echo "[OK] 数据库初始化完成"

# ============================================
# 3. 配置 Redis
# ============================================
echo "[4/9] 配置 Redis..."
systemctl start redis-server
systemctl enable redis-server 2>/dev/null || true
echo "[OK] Redis 已启动"

# ============================================
# 4. 构建后端
# ============================================
echo "[5/9] 构建后端 (Maven, 首次需下载依赖)..."
mvn clean package -Dmaven.test.skip=true -q
echo "[OK] 后端构建完成"

# ============================================
# 5. 启动后端
# ============================================
echo "[6/9] 启动后端..."
pkill -f "ai_agent.*\.jar" 2>/dev/null || true
sleep 1

mkdir -p /home/puaiuc/AI_Agent/logs
JAR_FILE=$(ls target/*.jar 2>/dev/null | head -1)

if [ -z "$JAR_FILE" ]; then
    echo "[ERROR] 找不到 jar 文件，Maven 构建可能失败了"
    exit 1
fi

nohup env \
    POSTGRES_HOST=localhost \
    POSTGRES_PORT=$POSTGRES_PORT \
    POSTGRES_DB=$POSTGRES_DB \
    POSTGRES_USER=$POSTGRES_USER \
    POSTGRES_PASSWORD=$POSTGRES_PASSWORD \
    AI_API_KEY=$AI_API_KEY \
    AI_BASE_URL=$AI_BASE_URL \
    REDIS_HOST=localhost \
    JWT_SECRET=$JWT_SECRET \
    java -jar "$JAR_FILE" \
    --spring.profiles.active=local,docker \
    --server.port=8123 \
    > /home/puaiuc/AI_Agent/logs/backend.log 2>&1 &

BACKEND_PID=$!
echo "[OK] 后端已启动 (PID: $BACKEND_PID)"

# ============================================
# 6. 构建前端
# ============================================
echo "[7/9] 构建前端..."
cd AI_Agent_Fronted
npm install --silent 2>/dev/null || npm install
npm run build
cd ..
echo "[OK] 前端构建完成"

# ============================================
# 7. 配置 Nginx
# ============================================
echo "[8/9] 配置 Nginx..."
cat > /etc/nginx/sites-available/ai-agent << 'NGINX'
server {
    listen 80;
    server_name _;

    location / {
        root /home/puaiuc/AI_Agent/AI_Agent_Fronted/dist;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8123/api/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;
        client_max_body_size 50m;
    }
}
NGINX

rm -f /etc/nginx/sites-enabled/default
ln -sf /etc/nginx/sites-available/ai-agent /etc/nginx/sites-enabled/ai-agent

nginx -t 2>&1 && systemctl reload nginx 2>/dev/null || systemctl restart nginx
systemctl enable nginx 2>/dev/null || true
echo "[OK] Nginx 配置完成"

# ============================================
# 8. 验证
# ============================================
sleep 3

# 检查后端是否还在运行
if kill -0 $BACKEND_PID 2>/dev/null; then
    echo "[OK] 后端运行正常"
else
    echo "[!] 后端可能启动失败，查看日志:"
    tail -20 /home/puaiuc/AI_Agent/logs/backend.log
fi

echo ""
echo "============================================"
echo "  部署完成！"
echo "============================================"
echo ""
echo "  前端界面:  http://10.1.235.42"
echo "  接口文档:  http://10.1.235.42:8123/api/doc.html"
echo ""
echo "  常用命令:"
echo "  查看后端日志:  tail -f /home/puaiuc/AI_Agent/logs/backend.log"
echo "  重启后端:      pkill -f 'ai_agent.*\.jar' && bash 运行脚本/docker-start-standalone.sh"
echo "  停止服务:      pkill -f 'ai_agent.*\.jar'"
echo ""
