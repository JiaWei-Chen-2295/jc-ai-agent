# 智能测验 Agent 阶段总结

> 规划日期：2026-03-16

智能测验 Agent — Quiz ReAct Agent
技术栈: Spring Boot 3 / Spring AI / PostgreSQL + PGVector / Redis / 通义千问

- 设计 Redis Write-Behind 写缓冲架构，将答题过程中的认知指标写入
  合并为 session 结束时批量 flush，减少 DB 写入频率，Redis 不可用
  时自动降级直写 DB
- 实现三层结束守卫（最低题数 + 概念覆盖率 + 认知达标），修复知识
  点覆盖不全即提前结束的缺陷，概念总数支持 Redis → agentState →
  chunk 估算三级降级
- 基于 LLM 提取文档概念清单并缓存至 Redis，驱动决策引擎定向覆盖
  未测概念；通过编辑距离 + Jaccard 混合算法归一化概念名称，消除同
  义概念重复统计
- 向量检索 topK 提升至 15 并结合 Redis Set 去重已用 chunk，保证多
  轮出题知识片段不重复

