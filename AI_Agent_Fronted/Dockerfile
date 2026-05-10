# ============ 前端多阶段构建 ============

# 阶段 1: Node 构建
FROM node:20-alpine AS builder
WORKDIR /build

COPY package.json package-lock.json* ./
RUN npm ci --silent 2>/dev/null || npm install --silent

COPY . .
RUN npm run build

# 阶段 2: Nginx 运行时
FROM nginx:alpine

COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
