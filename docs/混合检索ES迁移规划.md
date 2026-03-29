# 混合检索方案：PGVector + ES BM25 + RRF 融合

> 在现有 PGVector 向量检索基础上，新增 Elasticsearch BM25 关键词检索，通过 RRF 算法统一融合排序，实现混合召回。**不迁移现有 PGVector 数据。**

## 一、技术方案概述

### 1.1 架构对比

```
┌─────────────────────────────────────────────────────────────┐
│                        当前架构                               │
│  ┌──────────┐    ┌─────────────┐    ┌──────────────────┐   │
│  │  用户请求 │───▶│ VectorStore │───▶│ PgVector (向量)   │   │
│  └──────────┘    └─────────────┘    └──────────────────┘   │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                        目标架构                               │
│                                                              │
│  ┌──────────┐    ┌─────────────────┐                        │
│  │  用户请求 │───▶│ HybridRetriever │                        │
│  └──────────┘    └────────┬────────┘                        │
│                           │                                  │
│              ┌────────────┼────────────┐                    │
│              ▼                         ▼                    │
│     ┌──────────────┐         ┌──────────────┐              │
│     │ PgVector     │         │ Elasticsearch │              │
│     │ (向量检索)    │         │ (BM25 关键词)  │              │
│     └──────┬───────┘         └──────┬───────┘              │
│            │                        │                       │
│            └───────────┬────────────┘                       │
│                        ▼                                    │
│               ┌────────────────┐                            │
│               │  RRF 融合排序   │                            │
│               └────────────────┘                            │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 核心设计决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 向量检索 | **保留 PGVector** | 现有实现稳定，< 10K chunks 性能足够，零迁移成本 |
| 关键词检索 | **ES 9.x BM25** | IK 中文分词成熟，BM25 效果好，ES 已配置 |
| 融合算法 | **RRF** | 纯算法实现，零 API 成本，零额外延迟 |
| 数据同步 | **PG → ES 单向同步** | 只同步 text + metadata（不含 embedding），数据量极小 |

### 1.3 关键优势（对比原全量迁移方案）

| 对比维度 | 原方案（全量迁移 ES） | 新方案（PGVector + ES BM25） |
|----------|----------------------|------------------------------|
| 数据迁移 | 需要全量迁移向量数据 | **无需迁移向量数据** |
| 同步数据量 | text + metadata + embedding (1536维) | **仅 text + metadata（体积减少 ~90%）** |
| 故障影响 | ES 宕机 → 检索完全不可用 | **ES 宕机 → 自动降级为纯向量检索** |
| 代码改动 | 需要替换所有 VectorStore 调用 | **原有 VectorStore 接口不变** |
| 回滚难度 | 高（需恢复 PGVector 调用链） | **极低（关闭 ES 检索即可）** |

---

## 二、实施步骤

### Phase 1: ES 环境准备

#### 1.1 安装 IK 分词插件

```bash
# 进入 ES 容器
docker exec -it elasticsearch bash

# 安装 IK 分词器（版本需与 ES 版本一致）
bin/elasticsearch-plugin install https://get.infini.cloud/elasticsearch/analysis-ik/9.0.0

# 重启 ES
docker restart elasticsearch
```

> **注意**: IK 插件版本需与 ES 版本严格一致。若 9.x 版 IK 尚未发布，可使用 `smartcn` 作为备选。

#### 1.2 创建索引映射

ES 索引**只存储文本和元数据**，不存储向量：

```json
PUT /study_friends_bm25
{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0,
    "analysis": {
      "analyzer": {
        "ik_smart_analyzer": {
          "type": "custom",
          "tokenizer": "ik_smart"
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "id": { "type": "keyword" },
      "tenant_id": { "type": "long" },
      "document_id": { "type": "keyword" },
      "content": {
        "type": "text",
        "analyzer": "ik_smart_analyzer",
        "search_analyzer": "ik_smart_analyzer"
      },
      "metadata": {
        "type": "object",
        "enabled": true
      },
      "created_at": { "type": "date" },
      "updated_at": { "type": "date" }
    }
  }
}
```

> 对比原方案：去掉了 `embedding` 字段（`dense_vector` 1536 维），每条文档体积大幅缩小。

---

### Phase 2: 代码实现

#### 2.1 新增依赖 (pom.xml)

```xml
<!-- Elasticsearch Java Client -->
<dependency>
    <groupId>co.elastic.clients</groupId>
    <artifactId>elasticsearch-java</artifactId>
</dependency>
```

> Spring Boot 已管理 ES 客户端版本，可不指定 version。`application.yml` 中已有 ES 配置。

#### 2.2 核心类设计

```
src/main/java/fun/javierchen/jcaiagentbackend/
├── rag/
│   ├── elasticsearch/
│   │   ├── config/
│   │   │   └── ElasticsearchClientConfig.java   # ES RestClient Bean
│   │   ├── service/
│   │   │   └── EsKeywordSearchService.java      # ES BM25 关键词检索
│   │   └── sync/
│   │       └── PgToEsSyncService.java           # PG → ES 文本同步
│   ├── retrieval/
│   │   ├── HybridRetriever.java                 # 混合检索器（核心）
│   │   └── RRFMerger.java                       # RRF 融合算法
│   └── config/
│       └── VectorStoreService.java              # [已有] 无需修改
```

#### 2.3 RRF 融合算法

```java
/**
 * Reciprocal Rank Fusion 排序融合
 * 公式: RRF(d) = Σ 1 / (k + rank_i(d))
 * 其中 k 为常数（默认 60），rank_i 为文档 d 在第 i 个结果列表中的排名
 */
public class RRFMerger {

    private static final int DEFAULT_K = 60;

    /**
     * 融合多路检索结果
     * @param resultLists 多路检索结果（每路已按相关性排序）
     * @param topK 最终返回数量
     * @return 融合后的结果
     */
    public static List<Document> merge(List<List<Document>> resultLists, int topK) {
        Map<String, Double> scoreMap = new HashMap<>();
        Map<String, Document> docMap = new HashMap<>();

        for (List<Document> results : resultLists) {
            for (int rank = 0; rank < results.size(); rank++) {
                Document doc = results.get(rank);
                String id = doc.getId();
                double rrfScore = 1.0 / (DEFAULT_K + rank + 1);
                scoreMap.merge(id, rrfScore, Double::sum);
                docMap.putIfAbsent(id, doc);
            }
        }

        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> docMap.get(e.getKey()))
                .toList();
    }
}
```

#### 2.4 HybridRetriever 核心检索器

```java
/**
 * 混合检索器
 * 并行执行 PGVector 向量检索 + ES BM25 关键词检索，RRF 融合
 */
@Service
@RequiredArgsConstructor
public class HybridRetriever {

    private final VectorStore studyFriendPGvectorStore;
    private final EsKeywordSearchService esKeywordSearchService;

    /**
     * 混合检索
     * @param query    查询文本
     * @param tenantId 租户ID
     * @param topK     返回数量
     * @return 融合后的文档列表
     */
    public List<Document> search(String query, Long tenantId, int topK) {
        // 1. 并行执行两路检索
        CompletableFuture<List<Document>> vectorFuture = CompletableFuture.supplyAsync(
                () -> vectorSearch(query, tenantId, topK));
        CompletableFuture<List<Document>> keywordFuture = CompletableFuture.supplyAsync(
                () -> esKeywordSearchService.search(query, tenantId, topK));

        List<Document> vectorResults = vectorFuture.join();
        List<Document> keywordResults = keywordFuture.join();

        // 2. RRF 融合
        return RRFMerger.merge(List.of(vectorResults, keywordResults), topK);
    }

    /**
     * 纯向量检索（降级时使用）
     */
    public List<Document> vectorSearch(String query, Long tenantId, int topK) {
        TenantContextHolder.setTenantId(tenantId);
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(query).topK(topK).build();
            return studyFriendPGvectorStore.similaritySearch(request);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
```

#### 2.5 ES 关键词检索服务

```java
@Service
@RequiredArgsConstructor
public class EsKeywordSearchService {

    private final ElasticsearchClient esClient;

    private static final String INDEX_NAME = "study_friends_bm25";

    public List<Document> search(String query, Long tenantId, int topK) {
        try {
            SearchResponse<Map> response = esClient.search(s -> s
                    .index(INDEX_NAME)
                    .size(topK)
                    .query(q -> q.bool(b -> b
                            .must(m -> m.match(mt -> mt
                                    .field("content")
                                    .query(query)
                                    .analyzer("ik_smart_analyzer")))
                            .filter(f -> f.term(t -> t
                                    .field("tenant_id")
                                    .value(tenantId))))),
                    Map.class);

            return response.hits().hits().stream()
                    .map(this::toDocument)
                    .toList();
        } catch (Exception e) {
            log.warn("ES 关键词检索失败，跳过: {}", e.getMessage());
            return List.of();  // ES 故障时返回空，降级为纯向量检索
        }
    }
}
```

---

### Phase 3: 数据同步（PG → ES）

#### 3.1 推荐方案：写入时双写

在现有 `VectorStoreService` 的写入和删除方法中追加 ES 操作，零延迟、零复杂度：

**写入双写** — `addDocuments()` 末尾追加：

```java
// VectorStoreService.addDocuments() 末尾追加:
try {
    esKeywordSearchService.index(documents, documentId, tenantId);
} catch (Exception e) {
    log.warn("ES 写入失败（不影响主流程）: {}", e.getMessage());
}
```

**删除双写** — `deleteByDocumentId()` 末尾追加：

```java
// VectorStoreService.deleteByDocumentId() 末尾追加:
try {
    esKeywordSearchService.deleteByDocumentId(documentId, tenantId);
} catch (Exception e) {
    log.warn("ES 删除失败（不影响主流程）: {}", e.getMessage());
}
```

> 当前删除链路：`DocumentController` → `DocumentUploadService.deleteDocument()` / `deleteDocumentByAdmin()` → `VectorStoreService.deleteByDocumentId()`。
> 普通删除和管理员删除**都经过** `VectorStoreService.deleteByDocumentId()`，因此只需在这一处追加 ES 删除即可覆盖所有场景。

**重新索引** — `DocumentAsyncIndexer.reindexDocument()` 已调用 `deleteByDocumentId()` + `handleDocumentUpload()`，双写逻辑自动生效，无需额外改动。

**双写 vs 定时同步对比**：

| 方案 | 实时性 | 复杂度 | 一致性 |
|------|--------|--------|--------|
| 双写 | 实时 | 低（几行代码） | 可能因 ES 写入失败丢失，但有 PG 兜底 |
| 定时同步 | 5 分钟延迟 | 中（需 sync 状态表 + 调度） | 最终一致 |

> 建议先用双写，后续如需补偿可加一个全量对账任务。

#### 3.2 全量同步脚本（首次部署执行一次）

```java
/**
 * 一次性全量同步（只同步 text + metadata，不含 embedding）
 */
public void fullSync() {
    String sql = "SELECT id, tenant_id, content, metadata FROM study_friends";
    // 分页读取 PG → 批量写入 ES (Bulk API)
    // 注意：不读取 embedding 列，大幅减少查询数据量
}
```

---

### Phase 4: 适配现有代码

#### 4.1 改动范围（最小化）

| 文件 | 改动 | 说明 |
|------|------|------|
| `VectorStoreService.java` | 追加双写 | `addDocuments()` 追加 ES 写入，`deleteByDocumentId()` 追加 ES 删除 |
| `TenantVectorStore.java` | **不改** | PGVector 实现完全保留 |
| `StudyFriendVectorStoreConfig.java` | **不改** | Bean 配置不动 |
| `DocumentUploadService.java` | **不改** | 删除链路已经过 VectorStoreService，双写自动生效 |
| `DocumentAsyncIndexer.java` | **不改** | reindex 调用 delete + upload，双写自动生效 |
| `StudyFriend.java` | 小改 | `buildRagAdvisor()` 可选择使用 `HybridRetriever` |
| `KnowledgeRetrieverTool.java` | 小改 | 注入 `HybridRetriever`，调用 `search()` |
| `QuizGeneratorTool.java` | 小改 | 同上 |
| `RagBenchmarkService.java` | 小改 | 增加混合检索的 benchmark 模式 |

#### 4.2 降级设计

```java
// HybridRetriever 内置降级逻辑
public List<Document> search(String query, Long tenantId, int topK) {
    List<Document> vectorResults = vectorSearch(query, tenantId, topK);

    List<Document> keywordResults;
    try {
        keywordResults = esKeywordSearchService.search(query, tenantId, topK);
    } catch (Exception e) {
        log.warn("ES 不可用，降级为纯向量检索");
        return vectorResults;  // ES 故障 → 降级为当前行为
    }

    return RRFMerger.merge(List.of(vectorResults, keywordResults), topK);
}
```

> 核心保证：**ES 挂了 ≠ 系统挂了**。降级后等同于当前的纯向量检索行为。

---

### Phase 5: 测试与验证

#### 5.1 测试用例

| 测试项 | 验证点 |
|--------|--------|
| 混合检索 vs 纯向量检索 | 对比 MRR、Recall@K，验证混合检索提升效果 |
| ES 降级 | 关闭 ES 后，系统自动退回纯向量检索，功能不受影响 |
| 多租户隔离 | ES 和 PGVector 都正确过滤 tenant_id |
| 中文关键词 | IK 分词对专业术语、长句的分词效果 |
| 双写一致性 | 新增文档后，ES 和 PG 数据一致 |
| 性能基准 | 混合检索延迟 < 200ms（两路并行 + RRF 融合） |

#### 5.2 Benchmark 对比

复用现有 `RagBenchmarkService`，新增混合检索模式，对比：

```
Pure Vector (当前):  query → PGVector → results
Hybrid (目标):       query → PGVector + ES BM25 → RRF → results
```

指标：Precision@K, Recall@K, MRR, NDCG@K, 延迟

---

## 三、里程碑

| 阶段 | 任务 | 主要内容 |
|------|------|----------|
| Phase 1 | ES 环境准备 | 安装 IK 插件 + 创建 `study_friends_bm25` 索引 |
| Phase 2 | 核心代码 | `RRFMerger` + `EsKeywordSearchService` + `HybridRetriever` |
| Phase 3 | 数据同步 | 双写逻辑 + 全量同步脚本（一次性） |
| Phase 4 | 适配集成 | `StudyFriend` / Tool 类切换到 `HybridRetriever` |
| Phase 5 | 测试验证 | Benchmark 对比 + 降级测试 |

---

## 四、风险与应对

| 风险 | 影响 | 应对措施 |
|------|------|----------|
| ES 服务不可用 | 关键词检索失效 | HybridRetriever 自动降级为纯向量检索（当前行为） |
| IK 9.x 未发布 | 无法安装分词插件 | 使用 ES 内置 `smartcn` 分词器作为备选 |
| 双写 ES 失败 | 少量数据未同步 | 主流程不受影响 + 全量对账任务补偿 |
| RRF 效果不佳 | 融合结果不如纯向量 | 调整 k 常数 / 增加权重参数 / 后续可升级为 reranker 模型 |

---

## 五、简历亮点提炼

完成后可在简历中这样描述：

> **RAG 混合检索优化**
> - 在 PGVector 向量检索基础上引入 Elasticsearch BM25 关键词检索，实现双路混合召回
> - 采用 RRF (Reciprocal Rank Fusion) 算法融合排序，召回准确率提升 XX%
> - 设计降级机制，ES 故障时自动退回纯向量检索，保障系统可用性
> - 集成 IK 中文分词器，优化中文关键词匹配效果

---

## 六、已确认事项

- [x] PGVector **保留**，向量检索逻辑不动
- [x] ES 仅用于 **BM25 关键词检索**（不存储向量）
- [x] 融合算法：**RRF**（代码实现，零 API 成本）
- [x] ES 版本：**9.x**（已安装）
- [x] 数据规模：**< 10K chunks**（PGVector 性能充足）
- [x] 同步策略：**双写**（写入向量时同步写入 ES）

---

*文档版本: v2.0*
*创建时间: 2026-03-15*
*更新时间: 2026-03-16*
