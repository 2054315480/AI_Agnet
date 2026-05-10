# 可选:容器化部署模板。
# 若你打算在自己服务器上直接 systemd/pm2 跑 uvicorn,无需 Docker。

FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

# 预下载模型(强烈推荐,运行时不再访问外网)
# RUN python -c "from sentence_transformers import SentenceTransformer; \
#     SentenceTransformer('BAAI/bge-large-zh-v1.5')"

COPY . /app

# 你的 KB 索引(若已预先构建好)
# COPY index/ /app/index/

EXPOSE 8000

# 容器启动 uvicorn(也可包一层 gunicorn 提升并发)
CMD ["uvicorn", "server:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "2"]
