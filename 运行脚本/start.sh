#!/bin/bash
set -e

echo "========================================"
echo "  AI 智能客服系统 - 开发环境启动脚本"
echo "  （推荐使用 Docker Compose: ./docker-start.sh）"
echo "========================================"
echo ""

# 检测 Docker
if command -v docker >/dev/null 2>&1; then
    echo "[提示] 检测到 Docker，推荐使用 ./docker-start.sh 一键部署"
    echo "       继续使用本地开发模式启动..."
    echo ""
fi

# ========== 前置检查 ==========
echo "[1/4] 检查环境..."
command -v java >/dev/null 2>&1 || { echo "[ERROR] 未找到 Java，请安装 JDK 21+"; exit 1; }
command -v node >/dev/null 2>&1 || { echo "[ERROR] 未找到 Node.js，请安装 Node.js 18+"; exit 1; }
command -v mvn >/dev/null 2>&1 || { echo "[ERROR] 未找到 Maven，请安装 Maven 3.8+"; exit 1; }
echo "      Java: $(java -version 2>&1 | head -1)"
echo "      Node: $(node -v)"
echo "      Maven: $(mvn -v 2>&1 | head -1)"

# ========== 后端启动 ==========
echo ""
echo "[2/4] 编译并启动后端服务 (端口 8123)..."
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"
mvn spring-boot:run -Dmaven.test.skip=true &
BACKEND_PID=$!
echo "      后端 PID: $BACKEND_PID"

# 等待后端就绪
echo "      等待后端启动中..."
for i in $(seq 1 30); do
    if curl -s http://localhost:8123/api/analytics/overview > /dev/null 2>&1; then
        echo "      后端启动成功！"
        break
    fi
    sleep 2
done

# ========== 前端启动 ==========
echo ""
echo "[3/4] 安装前端依赖并启动前端 (端口 5173)..."
cd "$PROJECT_DIR/AI_Agent_Fronted"
npm install
npm run dev &
FRONTEND_PID=$!
echo "      前端 PID: $FRONTEND_PID"

# ========== 完成 ==========
echo ""
echo "[4/4] 启动完成！"
echo ""
echo "========================================"
echo "  后端 API:  http://localhost:8123/api"
echo "  接口文档:  http://localhost:8123/api/doc.html"
echo "  前端界面:  http://localhost:5173"
echo "========================================"
echo ""
echo "按 Ctrl+C 停止所有服务"

# 捕获退出信号
trap "echo '正在停止服务...'; kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit 0" SIGINT SIGTERM
wait
