# bge-rerank-base service

这个目录提供一个最小可运行的 `FastAPI` 微服务，用于给 Java 主应用提供 `query-doc` 精排能力。

## 启动

### 本地 Python

```bash
cd rerank-service
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8001
```

### Docker Compose

在项目根目录执行：

```bash
docker compose up -d --build reranker
```

## 健康检查

```bash
curl http://localhost:8001/health
```

## 精排请求

```bash
curl -X POST http://localhost:8001/rerank \
  -H "Content-Type: application/json" \
  -d '{
    "query": "payment-service CPU 飙高应该怎么排查？",
    "documents": [
      "payment-service CPU 使用率过高处理手册\n当 payment-service CPU 飙高时，先查看 Prometheus 指标...",
      "K8s Pod CPU 诊断流程\n排查 CPU 高负载时，可以先看 Pod 资源限制..."
    ],
    "top_n": 2
  }'
```

## 响应格式

```json
{
  "model": "BAAI/bge-reranker-base",
  "elapsed_ms": 42,
  "results": [
    {"index": 0, "score": 6.128},
    {"index": 1, "score": 3.447}
  ]
}
```
