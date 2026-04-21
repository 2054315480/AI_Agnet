# ========== Stage 1: Build Vue frontend ==========
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm install --registry=https://registry.npmmirror.com
COPY . .
RUN npm run build

# ========== Stage 2: Serve with nginx ==========
FROM nginx:alpine
# 删除默认配置
RUN rm /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
