# -*- coding: utf-8 -*-
import paramiko, sys
cli = paramiko.SSHClient(); cli.set_missing_host_key_policy(paramiko.AutoAddPolicy())
cli.connect("114.132.153.143", 22, "root", "2054315480.qh", timeout=20, auth_timeout=30, banner_timeout=30)
def run(c, t=900, quiet=False):
    _, o, e = cli.exec_command(c, timeout=t)
    out = o.read().decode("utf-8","replace"); err = e.read().decode("utf-8","replace")
    if not quiet:
        tail = "\n".join((out + err).strip().splitlines()[-6:])
        print("  " + tail.replace("\n", "\n  "))
    return out, err

print("=== 1) 加 PGDG 源 ===")
run("yum install -y https://download.postgresql.org/pub/repos/yum/reporpms/EL-7-x86_64/pgdg-redhat-repo-latest.noarch.rpm 2>&1 | tail -4")

print("=== 2) 可用的 PG 大版本 ===")
out, _ = run("yum list available 2>/dev/null | grep -oE 'postgresql1[0-9]-server\\.x86_64' | sort -u", quiet=True)
print("  " + out.strip().replace("\n", ", "))
ver = "16" if "postgresql16-server" in out else ("15" if "postgresql15-server" in out else "14")
print("  选择 PG 版本: " + ver)

print("=== 3) 安装软件包（可能需要几分钟）===")
pkgs = "postgresql%s-server postgresql%s-devel postgresql%s-contrib nginx redis git gcc make" % (ver, ver, ver)
run("yum install -y %s 2>&1 | tail -5" % pkgs)

print("=== 4) 加 1G swap（2G 内存保底）===")
run("if [ ! -f /swapfile ]; then dd if=/dev/zero of=/swapfile bs=1M count=1024 status=none && chmod 600 /swapfile && mkswap /swapfile >/dev/null && swapon /swapfile && grep -q swapfile /etc/fstab || echo '/swapfile swap swap defaults 0 0' >> /etc/fstab; fi; free -m | tail -2")

print("=== 5) 初始化并启动 PG / redis / nginx ===")
run("test -f /var/lib/pgsql/%s/data/PG_VERSION || /usr/pgsql-%s/bin/postgresql-%s-setup initdb" % (ver, ver, ver))
run("systemctl enable postgresql-%s redis nginx >/dev/null 2>&1; systemctl restart postgresql-%s redis nginx; sleep 3; systemctl is-active postgresql-%s redis nginx | tr '\\n' ' '" % (ver, ver, ver))

print("=== 6) 版本确认 ===")
out, _ = run("su - postgres -c '/usr/pgsql-%s/bin/postgres --version'; nginx -v 2>&1; redis-server --version | cut -c1-40" % ver, quiet=True)
print("  " + out.strip().replace("\n", "\n  "))
cli.close()
