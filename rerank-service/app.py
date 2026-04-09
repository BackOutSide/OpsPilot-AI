import os
import time
from contextlib import asynccontextmanager
from typing import List

import torch
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from transformers import AutoModelForSequenceClassification, AutoTokenizer


MODEL_NAME = os.getenv("RERANK_MODEL_NAME", "BAAI/bge-reranker-base")
DEVICE = os.getenv("RERANK_DEVICE", "cuda" if torch.cuda.is_available() else "cpu")
MAX_LENGTH = int(os.getenv("RERANK_MAX_LENGTH", "512"))
BATCH_SIZE = int(os.getenv("RERANK_BATCH_SIZE", "8"))


class RerankRequest(BaseModel):
    query: str = Field(..., min_length=1, description="User question to rerank against")
    documents: List[str] = Field(..., min_length=1, description="Candidate chunks")
    top_n: int = Field(3, ge=1, description="How many top results to return")


class RerankResult(BaseModel):
    index: int
    score: float


class RerankResponse(BaseModel):
    model: str
    elapsed_ms: int
    results: List[RerankResult]


class HealthResponse(BaseModel):
    status: str
    model: str
    device: str


class RerankEngine:
    def __init__(self) -> None:
        self.tokenizer = None
        self.model = None

    def load(self) -> None:
        self.tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
        self.model = AutoModelForSequenceClassification.from_pretrained(MODEL_NAME)
        self.model.to(DEVICE)
        self.model.eval()

    def score(self, query: str, documents: List[str]) -> List[float]:
        if self.model is None or self.tokenizer is None:
            raise RuntimeError("Rerank model is not loaded")

        scores: List[float] = []
        pairs = [[query, doc] for doc in documents]

        with torch.no_grad():
            for start in range(0, len(pairs), BATCH_SIZE):
                batch_pairs = pairs[start:start + BATCH_SIZE]
                inputs = self.tokenizer(
                    batch_pairs,
                    padding=True,
                    truncation=True,
                    max_length=MAX_LENGTH,
                    return_tensors="pt",
                )
                inputs = {key: value.to(DEVICE) for key, value in inputs.items()}
                logits = self.model(**inputs).logits.view(-1).float().cpu().tolist()
                scores.extend(logits)

        return scores


engine = RerankEngine()


@asynccontextmanager
async def lifespan(_: FastAPI):
    engine.load()
    yield


app = FastAPI(
    title="bge-rerank-base service",
    version="1.0.0",
    lifespan=lifespan,
)


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    if engine.model is None:
        raise HTTPException(status_code=503, detail="Model not loaded")
    return HealthResponse(status="ok", model=MODEL_NAME, device=DEVICE)


@app.post("/rerank", response_model=RerankResponse)
def rerank(request: RerankRequest) -> RerankResponse:
    if not request.documents:
        raise HTTPException(status_code=400, detail="documents must not be empty")

    started_at = time.perf_counter()
    scores = engine.score(request.query, request.documents)

    ranked = sorted(
        (
            RerankResult(index=index, score=score)
            for index, score in enumerate(scores)
        ),
        key=lambda item: item.score,
        reverse=True,
    )
    top_n = min(request.top_n, len(ranked))
    elapsed_ms = int((time.perf_counter() - started_at) * 1000)

    return RerankResponse(
        model=MODEL_NAME,
        elapsed_ms=elapsed_ms,
        results=ranked[:top_n],
    )
