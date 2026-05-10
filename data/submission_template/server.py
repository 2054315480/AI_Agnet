"""FastAPI HTTP 服务(框架代码,选手通常不需要修改)。

启动:
    export API_TOKEN=your-secret-here
    uvicorn server:app --host 0.0.0.0 --port 8000

接口对齐《赛题说明书》§5.2 与 submission_template/README.md §2。
"""
from __future__ import annotations

import os
from typing import Any

from fastapi import Depends, FastAPI, HTTPException, Header
from pydantic import BaseModel

from system import CustomerServiceSystem

API_TOKEN = os.environ.get("API_TOKEN", "")
VERSION = "1.0.0"

app = FastAPI(title="智能客服系统", version=VERSION)
system = CustomerServiceSystem()


# ───────── 鉴权 ─────────
def verify_token(authorization: str | None = Header(default=None)) -> None:
    if not API_TOKEN:
        return  # 开发模式无 token
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="missing or malformed Authorization header")
    if authorization.removeprefix("Bearer ").strip() != API_TOKEN:
        raise HTTPException(status_code=401, detail="invalid token")


# ───────── 请求模型 ─────────
class PredictBatchRequest(BaseModel):
    samples: list[dict[str, Any]]


# ───────── 端点 ─────────
@app.get("/health")
async def health() -> dict:
    return {"status": "ok", "version": VERSION}


@app.post("/predict")
async def predict(sample: dict[str, Any], _=Depends(verify_token)) -> dict:
    if "id" not in sample or "type" not in sample:
        raise HTTPException(status_code=400, detail="sample missing 'id' or 'type'")
    try:
        return await system.predict_one(sample)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"predict failed: {e}")


@app.post("/predict_batch")
async def predict_batch(req: PredictBatchRequest, _=Depends(verify_token)) -> dict:
    if not req.samples:
        return {"predictions": []}
    if len(req.samples) > 32:
        raise HTTPException(status_code=400, detail="batch size > 32")

    import asyncio
    sem = asyncio.Semaphore(int(os.environ.get("PREDICT_CONCURRENCY", "8")))

    async def _do(s):
        async with sem:
            try:
                return await system.predict_one(s)
            except Exception as e:
                return {
                    "id": s.get("id", ""),
                    "answer": "",
                    "retrieved_kb_ids": [],
                    "is_refusal": True,
                    "confidence": 0.0,
                    "_error": str(e),
                }

    results = await asyncio.gather(*[_do(s) for s in req.samples])
    return {"predictions": results}
