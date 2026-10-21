#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
增量部署脚本：把本地改动推到服务器（114.132.153.143）。

前置：
    pip install paramiko
    # 服务器地址/账号/密码（不要写死在仓库里）
    set DEPLOY_HOST=114.132.153.143
    set DEPLOY_USER=root
    set DEPLOY_PASSWORD=xxxxxx

用法：
    python scripts/deploy_update.py                # 后端 + 前端全量更新（会重新构建）
    python scripts/deploy_update.py --backend      # 只更新后端（改了 Java / 配置）
    python scripts/deploy_update.py --frontend     # 只更新前端（改了 .vue / .css）
    python scripts/deploy_update.py --skip-build   # 用 target 里现成的 jar / dist 直接上传

跑完会自动：备份旧 jar 与 web 目录 -> 切换 -> 重启 -> 等健康检查 -> 失败自动回滚。
"""
import argparse
import hashlib
import os
import subprocess
import sys
import tarfile
import time

HOST = os.environ.get("DEPLOY_HOST", "114.132.153.143")
USER = os.environ.get("DEPLOY_USER", "root")
PWD = os.environ.get("DEPLOY_PASSWORD", "")

REMOTE_DIR = "/opt/ai-agent"
PART_SIZE = 40 * 1024 * 1024
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAR = os.path.join(ROOT, "target", "AI_Agent-0.0.1-SNAPSHOT.jar")
DIST = os.path.join(ROOT, "AI_Agent_Fronted", "dist")


def run_local(cmd, cwd):
    print("  $ " + cmd)
    r = subprocess.run(cmd, cwd=cwd, shell=True)
    if r.returncode != 0:
        sys.exit("本地命令失败: " + cmd)


def build_backend():
    print("[1/4] 构建后端 jar ...")
    run_local("mvn -o -DskipTests -q package", ROOT)


def build_frontend():
    print("[1/4] 构建前端 dist ...")
    run_local("npm run build", os.path.join(ROOT, "AI_Agent_Fronted"))


def connect():
    try:
        import paramiko
    except ImportError:
        sys.exit("缺少 paramiko：pip install paramiko")
    if not PWD:
        sys.exit("请先设置环境变量 DEPLOY_PASSWORD")
    cli = paramiko.SSHClient()
    cli.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    cli.connect(HOST, 22, USER, PWD, timeout=20, auth_timeout=30, banner_timeout=30)
    return cli


def run(cli, cmd, timeout=600, show=True):
    _, o, e = cli.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace") + e.read().decode("utf-8", "replace")
    if show and out.strip():
        print("  " + out.strip()[-1500:])
    return out


def upload_backend(cli):
    print("[2/4] 上传后端 jar ...")
    size = os.path.getsize(JAR)
    md5 = hashlib.md5(open(JAR, "rb").read()).hexdigest()
    n = (size + PART_SIZE - 1) // PART_SIZE
    sftp = cli.open_sftp()
    with open(JAR, "rb") as f:
        for i in range(n):
            data = f.read(PART_SIZE)
            local = os.path.join(ROOT, ".upload_part")
            open(local, "wb").write(data)
            sftp.put(local, "%s/new.part%d" % (REMOTE_DIR, i))
    sftp.close()
    os.remove(os.path.join(ROOT, ".upload_part"))
    cat = " ".join("%s/new.part%d" % (REMOTE_DIR, i) for i in range(n))
    run(cli, "cd %s && cat %s > app.jar.new && rm -f new.part* && md5sum app.jar.new" % (REMOTE_DIR, cat))
    remote = run(cli, "cd %s && md5sum app.jar.new | cut -d' ' -f1" % REMOTE_DIR, show=False).strip().splitlines()[-1].strip()
    if remote.split()[0] != md5:
        sys.exit("jar 上传校验失败: local=%s remote=%s" % (md5, remote))
    print("  md5 校验通过: " + md5)


def upload_frontend(cli):
    print("[2/4] 上传前端 dist ...")
    tgz = os.path.join(ROOT, ".dist.tar.gz")
    with tarfile.open(tgz, "w:gz") as t:
        t.add(DIST, arcname=".")
    sftp = cli.open_sftp()
    sftp.put(tgz, "%s/dist.tar.gz" % REMOTE_DIR)
    sftp.close()
    os.remove(tgz)
    run(cli, "cd %s && rm -rf web_new && mkdir -p web_new && tar -xzf dist.tar.gz -C web_new && rm -f dist.tar.gz" % REMOTE_DIR)


def switch_and_restart(cli, backend, frontend):
    print("[3/4] 备份并切换版本 ...")
    if backend:
        run(cli, "cd %s && cp -f app.jar app.jar.bak && mv -f app.jar.new app.jar" % REMOTE_DIR)
    if frontend:
        run(cli, "cd %s && rm -rf web.bak && mv web web.bak && mv web_new web" % REMOTE_DIR)
    print("[4/4] 重启并健康检查 ...")
    run(cli, ": > %s/logs/app.log; systemctl restart ai-agent" % REMOTE_DIR)
    for _ in range(30):
        time.sleep(3)
        out = run(cli, "grep -c 'Started AiAgentApplication' %s/logs/app.log || true" % REMOTE_DIR, show=False)
        if out.strip().endswith("1"):
            print("  启动成功")
            run(cli, "systemctl is-active ai-agent; curl -s -o /dev/null -w 'HTTP %{http_code}\n' http://127.0.0.1:8123/api/system/info")
            return True
        if "APPLICATION FAILED" in run(cli, "tail -30 %s/logs/app.log" % REMOTE_DIR, show=False):
            break
    print("  !! 启动失败，开始回滚")
    if backend:
        run(cli, "cd %s && mv -f app.jar.bak app.jar && systemctl restart ai-agent" % REMOTE_DIR)
    if frontend:
        run(cli, "cd %s && rm -rf web && mv web.bak web" % REMOTE_DIR)
    return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--backend", action="store_true", help="只更新后端")
    ap.add_argument("--frontend", action="store_true", help="只更新前端")
    ap.add_argument("--skip-build", action="store_true", help="跳过构建，直接上传现有产物")
    args = ap.parse_args()

    backend = not args.frontend
    frontend = not args.backend

    if not args.skip_build:
        if backend:
            build_backend()
        if frontend:
            build_frontend()

    cli = connect()
    if backend:
        upload_backend(cli)
    if frontend:
        upload_frontend(cli)
    ok = switch_and_restart(cli, backend, frontend)
    cli.close()
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
