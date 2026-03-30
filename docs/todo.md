# 出现的问题
[X] 1. 系统的 fallback decision 不会告知大模型 仅仅给前端
[X] 2. 前端出了返回用户的回答并不能左右模型的输出
[X] 3. 向量检索失败会返回空集合 此时不会生成有效的题目 用户体验非常低

当前 agent 的问题在于：
> 例子: 问题在处理复杂问题时，忽略边缘部分有时可以简化计算而不显著影响结果。 
以上的问题出自的资料是关于计算机网络的 不是关于这个问题的 
> 事实上 问题来自于召回有效的信息过少 从而让 Agent 自己生成了概念题目

```bash
2026-03-09T19:45:43.293+08:00  INFO 40840 --- [jc-ai-agent-backend] [nio-8525-exec-7] f.j.j.a.quiz.tools.QuizGeneratorTool     : 生成题目: topic=边缘部分, count=3, difficulty=MEDIUM
2026-03-09T19:45:43.293+08:00  WARN 40840 --- [jc-ai-agent-backend] [nio-8525-exec-7] f.j.j.a.quiz.tools.QuizGeneratorTool     : 向量检索失败: 租户未选择
2026-03-09T19:45:43.294+08:00  INFO 40840 --- c'l[jc-ai-agent-backend] [nio-8525-exec-7] f.j.j.a.quiz.tools.QuizGeneratorTool     : 向量检索无结果，尝试基于文档元信息生成题目
```

[]新增混合检索 即为 RAG + 关键词检索

elasticsearch 采取 chunk + overlap 策略 

```
chunk1: 0-300
chunk2: 250-550
chunk3: 500-800
---------------
overlap = 50
```



[]实现模型的路由