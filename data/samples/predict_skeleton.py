"""最小预测脚本骨架 —— 演示输入输出格式,不含检索/生成实现。

选手参考此脚本理解:
  1. 如何读取 eval_public.jsonl 的三类样本
  2. 如何生成符合规范的 predictions.jsonl
  3. 多轮样本的 turn_index 处理方式

实际系统替换 predict_*() 三个函数即可。

用法:
    python samples/predict_skeleton.py --eval data/eval_public.jsonl --out predictions.jsonl
"""
import argparse
import json
import os


REFUSAL_TEMPLATE = "抱歉,该问题超出我的知识范围,建议您联系人工客服。"


def predict_single_turn(user_query: str) -> dict:
    """实现你的单轮问答逻辑。返回:
       {answer, retrieved_kb_ids, is_refusal, confidence}
    """
    # TODO: 替换为你的 RAG / 模型调用
    return {
        "answer": "(请实现 predict_single_turn)",
        "retrieved_kb_ids": [],
        "is_refusal": False,
        "confidence": 0.5,
    }


def predict_multi_turn(history: list[dict], current_turn_index: int, current_user: str) -> dict:
    """实现你的多轮对话逻辑。
       history 是该轮之前的所有对话(list of {role, content})。
       返回:{answer, retrieved_kb_ids, is_refusal, confidence}
    """
    # TODO: 替换为你的多轮 RAG 实现
    return {
        "answer": "(请实现 predict_multi_turn)",
        "retrieved_kb_ids": [],
        "is_refusal": False,
        "confidence": 0.5,
    }


def predict_trap(user_query: str, trap_subtype: str) -> dict:
    """实现你的陷阱判别 + 拒答逻辑。
       通常 trap 都应该 is_refusal=True。
    """
    # TODO: 实际中,陷阱判别可能需要意图分类 / 阈值检测,这里简单全部拒答
    return {
        "answer": REFUSAL_TEMPLATE,
        "retrieved_kb_ids": [],
        "is_refusal": True,
        "confidence": 0.9,
    }


def process_sample(sample: dict) -> dict:
    sid = sample["id"]
    stype = sample["type"]

    if stype == "single_turn":
        result = predict_single_turn(sample["user_query"])
        return {"id": sid, **result}

    if stype == "trap":
        result = predict_trap(sample["user_query"], sample["trap_subtype"])
        return {"id": sid, **result}

    if stype == "multi_turn":
        turn_outputs = []
        for target in sample["evaluation_targets"]:
            ti = target["turn_index"]
            history = sample["turns"][:ti]
            current_user = sample["turns"][ti]["content"]
            r = predict_multi_turn(history, ti, current_user)
            turn_outputs.append({"turn_index": ti, **r})
        return {"id": sid, "turn_outputs": turn_outputs}

    raise ValueError(f"unknown type: {stype}")


def main(eval_path: str, out_path: str):
    with open(eval_path) as f:
        samples = [json.loads(ln) for ln in f if ln.strip()]
    print(f"[load] {len(samples)} eval samples from {eval_path}")

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    with open(out_path, "w") as f:
        for s in samples:
            pred = process_sample(s)
            f.write(json.dumps(pred, ensure_ascii=False) + "\n")
    print(f"[done] wrote {len(samples)} predictions to {out_path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--eval", required=True, help="评测集路径(eval_public.jsonl)")
    parser.add_argument("--out", required=True, help="预测输出路径(predictions.jsonl)")
    args = parser.parse_args()
    main(args.eval, args.out)
