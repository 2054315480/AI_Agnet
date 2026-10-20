# -*- coding: utf-8 -*-
import sys, os, tarfile, io
sys.path.insert(0, r"E:\工房资料\项目\面试项目\面试项目\简历项目\_deploy")
from helper import connect
cli = connect()
base = r"E:\工房资料\项目\面试项目\面试项目\简历项目\AI_Agent"
jar = os.path.join(base, r"target\AI_Agent-0.0.1-SNAPSHOT.jar")
log = io.open(r"E:\工房资料\项目\面试项目\面试项目\简历项目\_deploy\upload.log", "w", encoding="utf-8")
def say(m):
    print(m); log.write(m + "\n"); log.flush()
try:
    cli.exec_command("mkdir -p /opt/ai-agent/web /opt/ai-agent/config")
    sftp = cli.open_sftp()
    say("开始上传 jar ...")
    sftp.put(jar, "/opt/ai-agent/app.jar", callback=lambda done, total: (say("  jar %.1f%%" % (done*100.0/total)) if int(done*100/total) % 20 == 0 else None))
    say("jar 上传完成: %d bytes" % sftp.stat("/opt/ai-agent/app.jar").st_size)
    sftp.close()
    # 前端 dist 打包成 tar 后上传解压
    dist = os.path.join(base, r"AI_Agent_Fronted\dist")
    tarpath = r"E:\工房资料\项目\面试项目\面试项目\简历项目\_deploy\dist.tar.gz"
    with tarfile.open(tarpath, "w:gz") as t:
        t.add(dist, arcname=".")
    say("dist 打包完成: %d bytes" % os.path.getsize(tarpath))
    sftp = cli.open_sftp()
    sftp.put(tarpath, "/opt/ai-agent/dist.tar.gz")
    sftp.close()
    say("dist 上传完成")
    say("UPLOAD_DONE")
except Exception as e:
    say("ERROR: %r" % (e,))
finally:
    log.close(); cli.close()
