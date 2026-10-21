# 服务器部署说明

> 目标机：CentOS 7 / 2C2G / 40G（114.132.153.143）。整套服务均为**原生安装**，未使用 Docker（该机器 Docker 源不可达）。

## 一、架构

```
浏览器
  │  :80
  ▼
nginx  ──静态资源──> /opt/ai-agent/web        （前端 dist）
  │
  └──/api/ 反向代理──> 127.0.0.1:8123          （Spring Boot，仅监听回环）
                        ├── PostgreSQL 15（127.0.0.1:5432, 库 Love_Agent, pgvector 0.7.2）
                        └── Redis 3.2（127.0.0.1:6379，会话状态）
```

## 二、目录约定

| 路径 | 作用 |
| --- | --- |
| `/opt/jdk21` | Temurin JDK 21 |
| `/opt/ai-agent/app.jar` | 后端可执行 jar |
| `/opt/ai-agent/config/application-server.yml` | 生产配置（含数据库密码与模型 Key，**不进 git**） |
| `/opt/ai-agent/web` | 前端静态文件 |
| `/opt/ai-agent/data` | 知识库源文件（`data/data/knowledge_base`） |
| `/opt/ai-agent/logs` | 应用日志（app.log / stdout.log / stderr.log） |
| `/opt/ai-agent/kb-lucene-index`、`kb-lucene-faq-index` | BM25 / FAQ Lucene 索引（启动时重建） |

## 三、关键步骤

### 1. 依赖

```bash
# PostgreSQL 15（阿里云 PGDG 镜像，EL7 最高可用版本）
curl -o /etc/yum.repos.d/pgdg15-aliyun.repo <<'EOF'
[pgdg15-aliyun]
name=PostgreSQL 15 for RHEL/CentOS 7 (aliyun)
baseurl=https://mirrors.aliyun.com/postgresql/repos/yum/15/redhat/rhel-7-x86_64
enabled=1
gpgcheck=0
EOF
yum install -y postgresql15-server postgresql15-contrib postgresql15 pgvector_15 nginx redis
/usr/pgsql-15/bin/postgresql-15-setup initdb
systemctl enable --now postgresql-15 redis nginx
```

> 注意：PGDG 官方 repo 的 `pgdg-redhat-all.repo` 会启用 PG11/12/13 等**已下架**版本，EL7 上返回 410 导致整个 yum 事务失败，务必只启用 pgdg15。
> `postgresql15-devel` 依赖 SCL 的 clang，装不上；pgvector 直接用官方 `pgvector_15` RPM，无需编译。

### 2. 数据库

```bash
su - postgres -c "createdb Love_Agent"
su - postgres -c "psql -d Love_Agent -c 'CREATE EXTENSION IF NOT EXISTS vector;'"
```

迁移已有数据时用 `pg_dump --no-owner --no-privileges`（含 `flyway_schema_history`，避免应用重复执行迁移），
高版本 pg_dump 导出到低版本前要剔除 `\restrict` / `SET transaction_timeout` 等新版本语句。

### 3. JDK

```bash
mkdir -p /opt/jdk21 && cd /tmp
curl -sL -o jdk21.tar.gz https://mirrors.tuna.tsinghua.edu.cn/Adoptium/21/jdk/x64/linux/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz
tar -xzf jdk21.tar.gz -C /opt/jdk21 --strip-components=1
```

### 4. systemd

`/etc/systemd/system/ai-agent.service` 关键行：

```
WorkingDirectory=/opt/ai-agent
Environment="LANG=en_US.utf8"
Environment="LC_ALL=en_US.utf8"
ExecStart=/opt/jdk21/bin/java -Xms256m -Xmx640m -XX:+UseSerialGC   -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8   -jar /opt/ai-agent/app.jar   --spring.profiles.active=server   --spring.config.additional-location=file:/opt/ai-agent/config/
```

> **必须设置 `LANG` 和 `sun.jnu.encoding`**：CentOS 7 上 `LANG=C.UTF-8` 不是有效 locale，
> JVM 的 `sun.jnu.encoding` 会退化成 `ANSI_X3.4-1968`，导致 `File.listFiles()` 读不到**中文目录名**，
> 知识库会静默加载 0 条（日志只显示"加载完成: 总计 0 条"，没有任何报错）。

### 5. nginx

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8123/api/;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_buffering off;          # SSE 流式必须关闭缓冲
    proxy_read_timeout 300s;
}
location / { try_files $uri $uri/ /index.html; }
```

## 四、运维命令

```bash
systemctl status ai-agent            # 状态
systemctl restart ai-agent           # 重启
tail -f /opt/ai-agent/logs/app.log   # 日志
curl -s localhost:8123/api/analytics/overview -H "Authorization: Bearer <token>"
```

## 五、已知限制

1. 目前只有 HTTP（80），没有域名与证书；如需 HTTPS 可加 certbot 或云厂商证书。
2. 单机单实例，会话状态虽已放 Redis，但 BM25 索引是每实例启动自建。
3. 2G 内存，JVM 堆限制 640M，`cs.reindex.embed-batch-size` 必须 <= 10（DashScope 单批上限）。
4. 数据库与配置未做自动备份，建议加 `pg_dump` 定时任务。

## 六、增量更新（日常最常用）

改完代码后**不要整台重装**，按改动范围选择下面之一即可。

### 6.1 一键脚本（推荐）

```powershell
pip install paramiko            # 只需一次
$env:DEPLOY_HOST="114.132.153.143"
$env:DEPLOY_USER="root"
$env:DEPLOY_PASSWORD="******"

python scripts/deploy_update.py               # 后端 + 前端都更新（会重新构建）
python scripts/deploy_update.py --backend     # 只改了 Java / application.yml
python scripts/deploy_update.py --frontend    # 只改了 .vue / .css
python scripts/deploy_update.py --skip-build  # 用现成 jar/dist 直接传
```

脚本做的事：构建 -> 分片上传（40MB/片，带 md5 校验）-> 备份旧 `app.jar` / `web` -> 切换 -> 重启 ->
轮询日志等 `Started AiAgentApplication` -> 打健康检查；**失败会自动回滚到上一版**。

### 6.2 改动类型对照表

| 改了什么 | 需要做什么 | 要不要重启 |
| --- | --- | --- |
| Java 代码 / pom | 重新打包 jar，上传，重启 | 是 |
| `application.yml` | 只改服务器上的 `/opt/ai-agent/config/application-server.yml`，重启 | 是 |
| 前端 `.vue/.css/.js` | 重新 `npm run build`，上传 dist | 否（nginx 直接读静态文件） |
| `data/` 知识库源文件 | 上传 `data/`，再调 `POST /api/admin/faq/reindex` | 否 |
| 新增配置项 | 同步到服务器 `application-server.yml` | 是 |

### 6.3 手动命令（等价于脚本）

```bash
# 后端
scp target/AI_Agent-0.0.1-SNAPSHOT.jar root@host:/opt/ai-agent/app.jar.new
ssh root@host "cd /opt/ai-agent && cp -f app.jar app.jar.bak && mv app.jar.new app.jar && systemctl restart ai-agent"

# 前端
tar -czf dist.tar.gz -C AI_Agent_Fronted/dist .
scp dist.tar.gz root@host:/opt/ai-agent/
ssh root@host "cd /opt/ai-agent && rm -rf web_new && mkdir web_new && tar -xzf dist.tar.gz -C web_new && rm -f dist.tar.gz && rm -rf web.bak && mv web web.bak && mv web_new web"

# 回滚
ssh root@host "cd /opt/ai-agent && mv -f app.jar.bak app.jar && systemctl restart ai-agent"
```

### 6.4 注意

- 每次替换前都会留 `app.jar.bak` 和 `web.bak`，确认没问题后可手动删除（`rm -f app.jar.prev app.jar.bak; rm -rf web.bak`）。
- 改了数据库结构（新增表）时：本项目没有 Flyway 脚本目录，观测/图谱表由 `SchemaInitializer` 在启动时 `CREATE TABLE IF NOT EXISTS` 兜底；其它表变更需要手动执行 SQL。
- 上传 120MB 的 jar 约 1 分钟（服务器带宽约 2MB/s），分片是为了避免单次超时。
