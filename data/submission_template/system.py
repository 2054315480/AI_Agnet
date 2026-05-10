"""智能客服系统主类(选手实现)

把 CustomerServiceSystem 替换成你的实现。
server.py 已经搭好 HTTP API 框架,无需修改。

⚠️ 你需要做的:
- __init__:加载向量索引、BM25、reranker、LLM 客户端
- predict_one:接受一条评测样本,返回 prediction(三类样本的 dispatch 已写好)
"""
from __future__ import annotations

REFUSAL_TEMPLATE = "抱歉，该问题超出我的知识范围，建议您联系人工客服。"


class CustomerServiceSystem:
    """选手实现的智能客服系统,被 server.py 实例化为单例。"""

    def __init__(self):
        # TODO 1: 加载 KB 文档解析器(MD/DOCX/PDF)
        # TODO 2: 构建/加载 FAISS + BM25 索引
        # TODO 3: 加载 reranker(如 bge-reranker-v2-m3)
        # TODO 4: 连接 LLM(主办方网关 / 自托管模型 / 第三方 API)
        pass

    async def retrieve(self, query: str, top_k: int = 5) -> list[dict]:
        """混合检索 → top_k 个 KB 段落,每个含 source_kb_id + text + score。"""
        # TODO: 实现混合检索 + 重排
        return []

    async def answer(self, query: str, history: list[dict] | None = None) -> dict:
        """单轮回答(多轮也走这里,history 是历史对话列表)。

        返回字段:
            answer: str
            retrieved_kb_ids: list[str]   ← 从 KB 文档中提取的 [条目编号:kb_qa_xxx]
            is_refusal: bool              ← True 表示拒答(陷阱 / 越权 / 注入)
            confidence: float             ← 0~1
        """
        history = history or []

        # TODO 1: 意图判别(可用 zero-shot LLM、规则、分类器)
        # ⚠️ 注意:误拒应答样本会让该样本计 0 分(说明书 §6.2 §7.2 雷 1)
        is_in_scope = True

        if not is_in_scope:
            return {
                "answer": REFUSAL_TEMPLATE,
                "retrieved_kb_ids": [],
                "is_refusal": True,
                "confidence": 0.9,
            }

        # TODO 2: 多轮重写(把历史折叠成独立 query)
        rewritten_query = query

        # TODO 3: 检索
        hits = await self.retrieve(rewritten_query, top_k=5)

        # TODO 4: 基于检索结果生成答案
        answer_text = "(请实现 CustomerServiceSystem.answer)"

        return {
            "answer": answer_text,
            "retrieved_kb_ids": [h["source_kb_id"] for h in hits],
            "is_refusal": False,
            "confidence": 0.5,
        }

    async def predict_one(self, sample: dict) -> dict:
        """处理一条评测样本(三类自动 dispatch)。

        框架代码,通常不需要修改。
        """
        sid = sample["id"]
        stype = sample["type"]

        if stype in ("single_turn", "trap"):
            result = await self.answer(sample["user_query"])
            return {"id": sid, **result}

        if stype == "multi_turn":
            turns = sample["turns"]
            targets = sample.get("evaluation_targets") or []
            turn_outputs = []
            for tgt in targets:
                ti = tgt["turn_index"]
                history = turns[:ti]
                current_user = turns[ti]["content"]
                r = await self.answer(current_user, history=history)
                turn_outputs.append({"turn_index": ti, **r})
            return {"id": sid, "turn_outputs": turn_outputs}

        raise ValueError(f"unknown sample type: {stype}")
