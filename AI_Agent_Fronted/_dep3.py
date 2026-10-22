# -*- coding: utf-8 -*-
import sys, os, hashlib, time, tarfile
sys.path.insert(0, r"E:\工房资料\项目\面试项目\面试项目\简历项目\_deploy")
from helper import connect, run
base = r"E:\工房资料\项目\面试项目\面试项目\简历项目\AI_Agent"
outdir = r"E:\工房资料\项目\面试项目\面试项目\简历项目\_deploy"
jar = os.path.join(base, r"target\AI_Agent-0.0.1-SNAPSHOT.jar")
md5 = hashlib.md5(open(jar,'rb').read()).hexdigest()
size = os.path.getsize(jar); part = 40*1024*1024; n=(size+part-1)//part
with open(jar,'rb') as f:
    for i in range(n): open(os.path.join(outdir,"x.part%d"%i),'wb').write(f.read(part))
tgz = os.path.join(outdir, "dist.tar.gz")
with tarfile.open(tgz,'w:gz') as t: t.add(os.path.join(base, r"AI_Agent_Fronted\dist"), arcname=".")
cli = connect(); sftp = cli.open_sftp(); t0=time.time()
for i in range(n): sftp.put(os.path.join(outdir,"x.part%d"%i), "/opt/ai-agent/x.part%d"%i)
sftp.put(tgz, "/opt/ai-agent/dist.tar.gz"); sftp.close()
print("上传 %.0fs md5=%s" % (time.time()-t0, md5))
run(cli, "cd /opt/ai-agent && cat x.part0 x.part1 x.part2 x.part3 > app.jar.new && rm -f x.part* && md5sum app.jar.new && rm -rf web_new && mkdir web_new && tar -xzf dist.tar.gz -C web_new && rm -f dist.tar.gz")
run(cli, "su - postgres -c \"/usr/pgsql-15/bin/psql -d Love_Agent -c 'TRUNCATE cs_vector_store;'\"")
run(cli, "cd /opt/ai-agent && cp -f app.jar app.jar.bak2 && mv -f app.jar.new app.jar && rm -rf web.bak && mv web web.bak && mv web_new web && : > logs/app.log && systemctl restart ai-agent")
for i in range(45):
    time.sleep(10)
    out = run(cli, "grep -E 'Started AiAgentApplication|APPLICATION FAILED' /opt/ai-agent/logs/app.log | tail -1", show=False)[0]
    if "Started AiAgentApplication" in out:
        print("  启动成功（%ds）" % ((i+1)*10)); break
    if "APPLICATION FAILED" in out: print("  启动失败"); break
run(cli, "grep -E 'CS-VectorStore|RetrieverInitializer|长条目切分' /opt/ai-agent/logs/app.log | tail -5")
cli.close()
