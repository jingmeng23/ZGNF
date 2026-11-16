# 商品搜索 ES 设计

## Mapping

正式模板见 `src/main/resources/es/product-index-mapping.json`。`title` 使用 `text + ik_max_word` 建索引、`ik_smart` 查询，适合中文标题召回；`title.keyword` 是 keyword 子字段，用于精确匹配和排序时避免分词。`brand`、`status`、`category_path` 使用 keyword，因为它们用于 facet/filter；`brand_id`、`category_id` 使用 long，便于 term 过滤。`attributes` 使用 flattened 保存任意属性键值；`attribute_pairs` 使用 nested 的 name/value 结构，避免属性名与属性值交叉匹配。`price` 用 scaled_float、分值 100，避免 double 精度；`sales` 用 long；时间字段用 date；`suggest` 用 completion。

线上要求安装 IK Analysis 插件。索引 `dynamic=strict`，新增字段必须先改模板，避免任意属性把 mapping 撑爆。索引别名为 `mall-products`，实际索引带版本后缀。

## DSL

`SearchDslBuilder.search` 生成关键词、状态/类目/品牌/价格/库存过滤、排序、高亮和聚合。关键词为 `multi_match`：`title^4`、品牌、类目路径和属性；排序支持相关度、销量、价格、新品，并以 `id` 作为稳定次序。ES `from + size` 限制为 10000，超限直接降级，深分页应改用 `search_after`。

典型请求：

```json
{
  "from": 0, "size": 20, "track_total_hits": true,
  "query": {"bool": {
    "must": [{"multi_match": {"query": "燃气灶", "fields": ["title^4", "brand^2", "category_path", "attributes.*"]}}],
    "filter": [
      {"terms": {"status": ["on_sale", "sold_out", "presale", "scheduled"]}},
      {"term": {"brand_id": 12}},
      {"range": {"price": {"gte": 100, "lte": 300}}}
    ]
  }},
  "highlight": {"fields": {"title": {"pre_tags": ["<em>"], "post_tags": ["</em>"]}}},
  "aggs": {
    "brands": {"terms": {"field": "brand", "size": 100}},
    "attributes": {"nested": {"path": "attribute_pairs"}, "aggs": {"values": {"terms": {"field": "attribute_pairs.value", "size": 100}}}}
  },
  "sort": [{"_score": "desc"}, {"id": "desc"}]
}
```

当前接口返回 `facets.brand` 与 `facets.attributes`，每项包含 `key/count`；nested 属性聚合读取 `aggregations.attributes.values.buckets`，高亮返回 `highlights[productId]`。Facet 只用于展示筛选项，最终商品数、价格、库存和状态仍由商品中心实时校验。

## 同步

推荐生产使用 Canal 监听商品中心 MySQL binlog：商品创建/标题、品牌、类目、属性、状态、价格相关字段变更发送到搜索消费者；消费者按 productId 幂等 upsert，删除使用 tombstone 或 `_delete`。状态变更事件先写商品中心，再同步 `status`；搜索接口同时过滤实时商品中心状态，所以即使 ES 消费延迟，下架商品也不会出现在最终响应。事件需包含版本号，消费者只接受不小于当前版本的事件，避免乱序覆盖。

若暂时没有 Canal，使用 `updated_at` 游标定时增量扫描作为兜底，每 5 秒扫描最近 2 分钟并按版本幂等 upsert；每晚全量校验并通过版本索引+别名原子切换修复漏事件。当前仓库已实现管理员触发的版本重建 `POST /api/v1/admin/mall/search/reindex`，以及可选的定时重建配置。

下架必须同步删除或更新为不可搜索状态。为保证“立即移除”，商品中心状态过滤是硬保障；Canal 更新 ES 是最终一致性优化，而不是唯一安全边界。删除事件应重试、进入死信队列并告警；消费失败不能阻塞商品中心事务。

## 无结果、联想和热搜

无结果保持 `results.total=0`，返回销量排序的最多 6 条 `recommendations`，不伪造搜索结果。ES 未配置、超时或候选超过 10000 时回退数据库基础关键词搜索并设置 `degraded=true`。联想使用 completion 或 `match_phrase_prefix`，只返回可公开状态；ES 故障时回退运营热搜词表。热搜词来自 `search_hotword`，按运营启用状态、搜索次数和 ID 排序，最多 10 条。

## Java 客户端骨架

当前项目使用 JDK `HttpClient`，`MallSearchService` 的 `call` 是传输层；`SearchDslBuilder` 是 DSL 层，后续可替换为官方 Java API Client 而不改变商品中心过滤契约：

```java
SearchResponse<ProductDocument> response = es.search(s -> s
    .index("mall-products")
    .from((page - 1) * size).size(size)
    .query(q -> q.bool(b -> b
        .must(m -> m.multiMatch(mm -> mm.query(keyword).fields("title^4", "brand^2", "category_path")))
        .filter(f -> f.terms(t -> t.field("status").terms(v -> v.value(publicStates))))))
    .highlight(h -> h.fields("title", hf -> hf.preTags("<em>").postTags("</em>")))
    .aggregations("brands", a -> a.terms(t -> t.field("brand").size(100)))
    .sort(sortFor(query)), ProductDocument.class);
if ((long) (page - 1) * size + size > 10_000) {
    // use search_after with the last hit's [sort] values instead of from/size
}
```

## 压测指标

关注 ES P95/P99、query cache 命中率、segment merge、heap/GC、search thread pool rejected、聚合耗时、from 深分页比例、Canal lag、事件重试/死信、下架到不可搜延迟、ES 与商品中心结果差异，以及降级时商品中心 QPS。
