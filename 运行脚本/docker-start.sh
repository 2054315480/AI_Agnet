#!/bin/bash
set -e

echo "============================================"
echo "  AI 智能客服系统 - Docker Compose 一键部署"
echo "============================================"
echo ""

# 检查 Docker
command -v docker >/dev/null 2>&1 || { echo "[ERROR] 未找到 Docker，请先安装 Docker"; exit 1; }
echo "[OK] Docker 已安装"

# 检查 .env 文件
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
if [ ! -f "$PROJECT_DIR/.env" ]; then
    echo "[!] 未找到 .env 文件，从 .env.example 复制..."
    cp "$PROJECT_DIR/.env.example" "$PROJECT_DIR/.env"
    echo "[!] 请编辑 .env 文件，填入你的 DASHSCOPE_API_KEY 等信息"
    echo "    文件路径: $PROJECT_DIR/.env"
    echo ""
    read -p "按回车键继续..."
fi

# 构建并启动
echo ""
echo "[1/2] 构建镜像并启动服务..."
cd "$PROJECT_DIR"
docker compose up -d --build

# 等待服务就绪
echo ""
echo "[2/2] 等待服务就绪..."
sleep 10

# 检查状态
docker compose ps

echo ""
echo "============================================"
echo "  部署完成！"
echo "  前端界面:  http://localhost"
echo "  后端 API:  http://localhost:8123/api"
echo "  接口文档:  http://localhost:8123/api/doc.html"
echo "============================================"
echo ""
echo "常用命令:"
echo "  查看日志: docker compose logs -f"
echo "  停止服务: docker compose down"
echo "  重启服务: docker compose restart"
echo ""
