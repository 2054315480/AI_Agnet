"""通过 HTTP API 跑预测(给 B 榜本地端到端联调用)。

Usage:
    python samples/predict_via_api.py \\
        --eval data/eval_public.jsonl \\
        --url http://localhost:8000 \\
        --token your-secret-token \\
        --out predictions.jsonl

也可对自己部署到云上的 URL 测试:
    python samples/predict_via_api.py --url https://your-domain.com ...

支持单样本和批量两种调用模式(默认批量,效率更高)。
"""
from __future__ import annotations

import argparse
import asyncio
import json
import os
import sys

import httpx
from tqdm import tqdm


async def predict_batch(client: httpx.AsyncClient, url: str, token: str, samples: list[dict]) -> list[dict]:
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    resp = await client.post(
        f"{url.rstrip('/')}/predict_batch",
        json={"samples": samples},
        headers=headers,
        timeout=120.0,
    )
    resp.raise_for_status()
    return resp.json()["predictions"]


async def predict_single(client: httpx.AsyncClient, url: str, token: str, sample: dict) -> dict:
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    resp = await client.post(
        f"{url.rstrip('/')}/predict",
        json=sample,
        headers=headers,
        timeout=120.0,
    )
    resp.raise_for_status()
    return resp.json()


async def main(eval_path: str, url: str, token: str, out_path: str, batch_size: int):
    # 健康检查
    async with httpx.AsyncClient() as client:
        try:
            r = await client.get(f"{url.rstrip('/')}/health", timeout=10.0)
            print(f"[health] {r.json()}")
        except Exception as e:
            print(f"[health] failed: {e}", file=sys.stderr)
            sys.exit(1)

    samples = []
    with open(eval_path) as f:
        for line in f:
            if line.strip():
                samples.append(json.loads(line))
    print(f"[load] {len(samples)} samples")

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    written = 0
    async with httpx.AsyncClient() as client:
        with open(out_path, "w") as f, tqdm(total=len(samples), desc="predict") as pbar:
            if batch_size > 1:
                for i in range(0, len(samples), batch_size):
                    batch = samples[i:i + batch_size]
                    try:
                        preds = await predict_batch(client, url, token, batch)
                    except Exception as e:
                        print(f"[error] batch {i}: {e}", file=sys.stderr)
                        preds = [{"id": s["id"], "answer": "", "retrieved_kb_ids": [], "is_refusal": True, "_error": str(e)} for s in batch]
                    for p in preds:
                        f.write(json.dumps(p, ensure_ascii=False) + "\n")
                        written += 1
                        pbar.update(1)
            else:
                for s in samples:
                    try:
                        pred = await predict_single(client, url, token, s)
                    except Exception as e:
                        pred = {"id": s["id"], "answer": "", "retrieved_kb_ids": [], "is_refusal": True, "_error": str(e)}
                    f.write(json.dumps(pred, ensure_ascii=False) + "\n")
                    written += 1
                    pbar.update(1)

    print(f"[done] wrote {written} predictions → {out_path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--eval", required=True, help="eval JSONL path")
    parser.add_argument("--url", required=True, help="your service base URL (e.g. http://localhost:8000)")
    parser.add_argument("--token", default="", help="API token (Bearer)")
    parser.add_argument("--out", required=True, help="output predictions JSONL")
    parser.add_argument("--batch-size", type=int, default=16, help="set 1 to disable batching")
    args = parser.parse_args()
    asyncio.run(main(args.eval, args.url, args.token, args.out, args.batch_size))
