# Chunk Retrieval Evaluation

这个目录用于离线评测 chunk 级检索效果，不依赖 Web 接口。

## 1. 导出 chunk 清单

运行评测 Runner 时会自动导出：

- `eval/chunk_catalog.json`

里面包含每个文档分片的：

- `source`
- `chunkIndex`
- `chunkKey`
- `chunkId`
- `title`
- `preview`

你可以先打开这个文件，给问题标注正确的 `expectedChunkIndexes`。

## 2. 标注样本

样本文件：

- `eval/chunk_eval_samples.jsonl`

每行格式：

```json
{"id":"q001","query":"payment-service CPU 飙高应该怎么排查？","expectedSource":"cpu_high_usage.md","expectedChunkIndexes":[3,5]}
```

说明：

- `expectedSource`: 正确文档文件名
- `expectedChunkIndexes`: 可接受的 chunkIndex 列表，可以有多个

## 3. 运行评测

```bash
mvn exec:java -Dexec.mainClass="org.example.eval.ChunkRetrievalEvalRunner" -Dexec.args="--samples=eval/chunk_eval_samples.jsonl --docs=aiops-docs --reindex=true"
```

## 4. 输出

运行后会生成：

- `eval/chunk_catalog.json`
- `eval/chunk_eval_results.json`

控制台会输出：

- `Hit@1`
- `Hit@3`
- `MRR@3`
