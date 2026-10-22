#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
验收自检脚本：对着线上实例逐条检查《待解决_问题清单》里的问题是否已修复。

用法：
    set ACCEPT_BASE=http://114.132.153.143          # 或你的域名
    set ACCEPT_ADMIN=admin:123456                    # 管理员账号（用于需要鉴权的检查）
    python scripts/acceptance_check.py
    python scripts/acceptance_check.py --repo .      # 额外做本地代码级检查（XLSX 解析等）

退出码：0 = 全部 PASS/INFO；1 = 存在 FAIL。
"""
import argparse, base64, io, json, os, re, subprocess, sys, time, urllib.parse

try:
    sys.stdout.reconfigure(encoding="utf-8")   # Windows 控制台默认 gbk，会把中文输出成乱码
except Exception:
    pass

BASE = os.environ.get("ACCEPT_BASE", "http://114.132.153.143").rstrip("/")
ADMIN = os.environ.get("ACCEPT_ADMIN", "admin:123456")
TOKEN = None
RESULTS = []


def http(method, path, body=None, timeout=120, auth=True, raw=False):
    url = BASE + path
    cmd = ["curl", "-s", "-X", method, "--max-time", str(timeout)]
    if auth and TOKEN:
        cmd += ["-H", "Authorization: Bearer " + TOKEN]
    if body is not None:
        cmd += ["-H", "Content-Type: application/json", "--data-binary", "@-"]
    cmd.append(url)
    p = subprocess.run(cmd, input=(json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None),
                       capture_output=True)
    out = p.stdout.decode("utf-8", "replace")
    if raw:
        return out
    try:
        return json.loads(out)
    except Exception:
        return {"_raw": out[:400]}


def check(item, ok, expected, actual, level="FAIL"):
    tag = "PASS" if ok else level
    RESULTS.append((tag, item, expected, actual))
    print("[%-4s] %-34s 预期: %-40s 实测: %s" % (tag, item, expected, actual))


def login():
    global TOKEN
    u, p = ADMIN.split(":", 1)
    r = http("POST", "/api/auth/login", {"username": u, "password": p}, auth=False)
    TOKEN = r.get("token")
    return bool(TOKEN)


def chat(question, sid=None):
    sid = sid or ("acc_" + base64.urlsafe_b64encode(os.urandom(6)).decode().strip("="))
    out = http("GET", "/api/customer-service/chat?message=%s&sessionId=%s" % (
        urllib.parse.quote(question), sid), timeout=180, raw=True)
    answer, sources, exit_type = "", None, None
    for line in out.splitlines():
        if not line.startswith("data:"):
            continue
        try:
            ev = json.loads(line[5:].strip())
        except Exception:
            continue
        t = ev.get("type")
        if t == "answer":
            answer = ev.get("content", "")
        elif t == "sources":
            try:
                sources = json.loads(ev.get("content", "[]"))
            except Exception:
                sources = ev.get("content")
        elif t in ("clarification", "handoff"):
            exit_type = t
    return answer, sources, exit_type


# ---------------- 检查项 ----------------

def c_p0_demo_report():
    d = http("POST", "/api/admin/demo/run", {})
    head_ok = bool(d.get("runAt")) and bool(d.get("uptimeHuman")) and isinstance(d.get("summary"), dict)
    check("P0-1 自检报告头字段齐全", head_ok,
          "runAt/uptimeHuman/summary 均非空", "runAt=%s uptime=%s summary=%s" % (d.get("runAt"), d.get("uptimeHuman"), d.get("summary")))
    gc = str(d.get("gitCommit", ""))
    check("P0-1b gitCommit 不是报错文本", not gc.lower().startswith("fatal"),
          "unknown 或构建号", gc[:60])
    total, passed = d.get("total"), d.get("passed")
    check("P0 一键自检全部通过", total and passed == total,
          "passed == total", "passed=%s / total=%s" % (passed, total))
    for r in d.get("results", []):
        if not r.get("pass"):
            check("P0 失败项: " + r.get("name", "?"), False, r.get("expected"), r.get("actual"))
    return d


def c_demo_covers(d):
    names = [r.get("name", "") for r in (d.get("results") or [])]
    need = ["向量写入幂等", "评测体系", "链路追踪", "缓存击穿", "混合检索"]
    missing = [n for n in need if not any(n in x for x in names)]
    check("P0 自检覆盖关键能力", not missing, "包含 %s" % need, "缺失 %s" % missing if missing else "齐全(%d 项)" % len(names))


def c_system_info():
    d = http("GET", "/api/system/info")
    ok = (d.get("uptimeSeconds") or 0) > 0 and isinstance(d.get("tables"), dict)
    check("P0-1c system/info 有运行时长与表行数", ok, "uptime>0 且 tables 非空",
          "uptime=%ss tables=%s" % (d.get("uptimeSeconds"), list((d.get("tables") or {}).keys())[:4]))
    cs = (d.get("tables") or {}).get("csVectorStore")
    check("P4-1 语料规模（向量块数）", (cs or 0) >= 1500,
          ">=1500 块（导入测试语料后）", "当前 %s 块" % cs)


def c_retrieval_scale():
    bad = []
    for q in ["1+1等于几", "你们公司的老板是谁", "帮我写一首诗"]:
        d = http("POST", "/api/admin/retrieval/explain", {"query": q})
        fused = d.get("fused") or []
        top = max([c.get("score") or 0 for c in fused], default=0)
        if top >= 0.5:
            bad.append("%s top=%.4f" % (q, top))
    check("P12-1 无关查询融合分不得 >=0.5", not bad, "全部 <0.5", "、".join(bad) if bad else "全部达标")
    d = http("POST", "/api/admin/retrieval/explain", {"query": "退货政策是什么"})
    fused = d.get("fused") or []
    vec = d.get("vector") or []
    check("P12-1b 相关查询仍能召回", len(fused) > 0 and len(vec) > 0,
          "融合/向量候选均 >0", "融合=%d 向量=%d" % (len(fused), len(vec)))
    scores = [c.get("score") or 0 for c in fused]
    ok = all(0 <= s <= 1 for s in scores)
    check("P12-3 分数落在 [0,1]", ok, "全部 0~1", "异常项=%s" % [s for s in scores if s > 1 or s < 0])


def c_unrelated_no_citation():
    hits = []
    for q in ["1+1等于几", "你们公司的老板是谁", "今天天气怎么样"]:
        ans, src, exit_type = chat(q)
        if src and len(src) > 0:
            hits.append("%s -> %s" % (q, src[:2]))
    check("P5-1 无关问题不得引用知识库", not hits, "sources 为空或走澄清", "、".join(hits) if hits else "全部无引用")


def c_placeholder_leak():
    leak = []
    for q in ["更换宽带运营商的步骤是什么", "我要在手机上注销维萨信用卡", "我想申请房贷"]:
        ans, src, _ = chat(q)
        found = re.findall(r"\{\{[^}]+\}\}", ans)
        if found:
            leak.append("%s -> %s" % (q[:12], found[:2]))
    check("P10-1 回答不得泄露占位符", not leak, "无 {{...}}", "、".join(leak) if leak else "无泄露")


def c_answer_format():
    ans, _, _ = chat("退款多久能到账")
    bad = ans.strip().startswith("Q:") or ans.count("Q:") > 1
    check("P10-2 回答不是原文直出", not bad, "不以 Q: 开头且不重复问句", "开头=%s" % ans.strip()[:40])


def c_kg_quality():
    d = http("GET", "/api/admin/kg/stats")
    tops = [e.get("name", "") for e in (d.get("topEntities") or [])]
    single = [n for n in tops if len(n) < 2]
    check("P3-1 图谱无明显单字实体", not single, "Top 实体均为多字词", "单字=%s" % single if single else "Top=%s" % tops[:6])
    r = http("POST", "/api/admin/kg/link", {"query": "蓝牙耳机的保修期是多久"})
    cnt = r.get("relatedKbCount")
    check("P3-2 实体链接不再全库命中", (cnt or 0) < 50, "<50 个 KB", "relatedKbCount=%s" % cnt)


def c_resilience():
    d = http("GET", "/api/admin/experiments/resilience")
    check("P7-1 熔断/限流有真实触发记录", ((d.get("windowTotal") or 0) > 0 or (d.get("rateLimitRejected") or 0) > 0),
          "windowTotal>0（跑过真实流量/实验）", "windowTotal=%s rejected=%s ratelimit=%s" % (
              d.get("windowTotal"), d.get("rejectedByCircuit"), d.get("rateLimitRejected")), level="INFO")


def c_eval_history():
    h = http("GET", "/api/admin/eval/history?limit=5")
    n = len(h) if isinstance(h, list) else -1
    check("P0-3/P1-3 评测历史可落库可查", n > 0, ">=1 条历史", "%s 条" % n)


def c_repo_checks(repo):
    if not repo or not os.path.isdir(repo):
        check("P4-2 XLSX 解析器存在", False, "新增 XlsxKbParser", "未提供 --repo，跳过", level="INFO")
        return
    has_xlsx_parser = False
    whitelist_ok = False
    for root, _, files in os.walk(os.path.join(repo, "src", "main", "java")):
        for f in files:
            if "Xlsx" in f and f.endswith(".java"):
                has_xlsx_parser = True
    for root, _, files in os.walk(os.path.join(repo, "src", "main", "java")):
        for f in files:
            p = os.path.join(root, f)
            try:
                t = io.open(p, encoding="utf-8").read()
            except Exception:
                continue
            if "isSupportedFile" in t and ".xlsx" in t:
                whitelist_ok = True
    check("P4-2 XlsxKbParser 已实现", has_xlsx_parser, "存在 Xlsx*Parser.java", "已存在" if has_xlsx_parser else "未找到")
    check("P4-2b .xlsx 进入解析白名单", whitelist_ok, "isSupportedFile 含 .xlsx", "含" if whitelist_ok else "不含")
    dup = os.path.join(repo, "data", "data", "knowledge_base", "电信", "test_ascii.md")
    check("P8-1 重复文件已清理", not os.path.exists(dup),
          "删除 test_ascii.md", "仍存在" if os.path.exists(dup) else "已删除")
    corpus = os.path.join(repo, "data", "data", "knowledge_base")
    if os.path.isdir(corpus):
        exts = {}
        for root, _, files in os.walk(corpus):
            for f in files:
                exts[os.path.splitext(f)[1].lower()] = exts.get(os.path.splitext(f)[1].lower(), 0) + 1
        check("P4-4 测试语料已生成", exts.get(".xlsx", 0) >= 3 and exts.get(".docx", 0) >= 1,
              "含 xlsx/docx/csv/json/txt", "%s" % exts)
    else:
        check("P4-4 测试语料目录存在", False, "data/data/knowledge_base", "不存在")


def c_experiments():
    """P7-3 / P7-4 / P7-5 / P2-1 / P2-2 / P2-3：跑真实实验并断言。"""
    r = http("POST", "/api/admin/experiments/idempotency", {"concurrency": 20, "rounds": 1}, timeout=120)
    check("P7-3 幂等并发上传实验", bool(r.get("pass")) and r.get("finalRows") == 1,
          "20 并发同 kb_id -> 表内 1 行",
          "attempted=%s succeeded=%s rejected=%s rows=%s" % (
              r.get("attempted"), r.get("succeeded"), r.get("rejected"), r.get("finalRows")))

    r = http("POST", "/api/admin/experiments/retrieval-bench", {"samples": 10, "rounds": 2}, timeout=180)
    ser = r.get("serial") or {}
    par = r.get("parallel") or {}
    check("P7-4 检索并行 vs 串行对比",
          ser.get("p50Ms") is not None and par.get("p50Ms") is not None,
          "输出 P50/P95 对照",
          "serial P50=%s/P95=%s  parallel P50=%s/P95=%s  speedup=%s" % (
              ser.get("p50Ms"), ser.get("p95Ms"), par.get("p50Ms"), par.get("p95Ms"), r.get("speedupP50")))

    r = http("POST", "/api/admin/kg/ablation", {"sampleSize": 10}, timeout=300)
    check("P7-5 图谱消融实验", (r.get("sampleCount") or 0) > 0,
          "同候选池 kg 开/关指标差", "samples=%s entityLinked=%s" % (
              r.get("sampleCount"), r.get("entityLinkedSamples")))

    recent = http("GET", "/api/admin/trace/recent?limit=1")
    tid = recent[0].get("trace_id") if isinstance(recent, list) and recent else None
    if not tid:
        check("P2-1 全链路证据视图", False, "存在 trace 记录", "无 trace")
        return
    d = http("GET", "/api/admin/trace/" + tid)
    trace = d.get("trace") or {}
    hops = d.get("hops") or []
    check("P2-2 trace 落库 token 与费用", trace.get("total_tokens") is not None,
          "total_tokens 非 null", "tokens=%s" % trace.get("total_tokens"))
    check("P2-1 一条请求的全链路证据视图（9 跳）",
          len(hops) == 9 and bool(trace.get("db_ref")),
          "9 跳且第 8 跳含落库行 id",
          "hops=%s db_ref=%s" % (len(hops), str(trace.get("db_ref"))[:60]))
    md = http("GET", "/api/admin/trace/%s/export?format=md" % tid, raw=True)
    check("P2-3 证据快照导出（带 traceId 水印）",
          isinstance(md, str) and tid in md and "证据快照" in md,
          "md 含 traceId 水印", "len=%s" % (len(md) if isinstance(md, str) else 0))


def c_manual():
    for item, exp in [("P11-1 浏览器业务请求带 Authorization（用 DevTools/代理核对）", "每个 /api/* 请求 auth=YES"),
                      ("P11-1b 对话分析页会话数与接口一致", "非 0 且与接口一致")]:
        check(item, None, exp, "人工核对", level="MANUAL")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default="")
    args = ap.parse_args()
    print("验收目标: %s\n" % BASE)
    if not login():
        print("登录失败，无法继续（检查 ACCEPT_ADMIN）")
        sys.exit(2)
    print("登录成功\n" + "-" * 100)
    d = c_p0_demo_report()
    c_demo_covers(d)
    c_system_info()
    c_retrieval_scale()
    c_unrelated_no_citation()
    c_placeholder_leak()
    c_answer_format()
    c_kg_quality()
    c_resilience()
    c_eval_history()
    c_repo_checks(args.repo)
    c_experiments()
    c_manual()
    print("-" * 100)
    fails = [r for r in RESULTS if r[0] == "FAIL"]
    print("汇总: PASS=%d FAIL=%d INFO=%d MANUAL=%d" % (
        sum(1 for r in RESULTS if r[0] == "PASS"), len(fails),
        sum(1 for r in RESULTS if r[0] == "INFO"), sum(1 for r in RESULTS if r[0] == "MANUAL")))
    if fails:
        print("\n仍失败项:")
        for r in fails:
            print("  - %s | 预期 %s | 实测 %s" % (r[1], r[2], r[3]))
    sys.exit(1 if fails else 0)


if __name__ == "__main__":
    main()
