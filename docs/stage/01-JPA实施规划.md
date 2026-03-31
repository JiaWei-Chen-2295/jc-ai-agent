# JPA 实施规划

> 规划日期：2026-01-14

## 概述

本文档记录了将智能测验系统表结构适配 Spring Data JPA 的设计决策和实施计划。

---

## 设计决策总结

| 决策项 | 选择 | 说明 |
|:---|:---|:---|
| **外键处理** | JPA 关系映射 | 不使用数据库外键，由 `@ManyToOne` 管理 |
| **软删除** | 所有表支持 | 统一使用 `is_delete SMALLINT DEFAULT 0` |
| **JSONB 处理** | Hibernate Types | 强类型（List/enum）+ 弱类型（Map）混合 |
| **主键** | UUID | 使用 `@GeneratedValue(strategy = GenerationType.UUID)` |
| **审计字段** | create_time / update_time | 使用 `@CreatedDate` / `@LastModifiedDate` |
| **建表方式** | JPA 自动管理 | SQL 文件作为参考文档 |

---

## 已完成的修改

### 1. SQL 文件更新

**文件**: `sql/quiz_module_tables.sql`

修改内容：
- ✅ 所有表主键改为 UUID
- ✅ 移除数据库层面外键约束
- ✅ 所有表添加软删除字段 `is_delete`
- ✅ 时间字段统一为 `create_time` / `update_time`
- ✅ 增强 JSONB 字段注释（说明 Java 类型映射）
- ✅ 添加 `related_concept` 字段用于知识点关联

### 2. 设计文档更新

**文件**: `docs/后端设计文档.md`

修改内容：
- ✅ 增加 JPA 配置策略说明
- ✅ 增加 Maven 依赖说明
- ✅ 增加 JPA 设计规范
- ✅ 增加 Entity 示例代码
- ✅ 增加 Repository 示例代码
- ✅ 增加 JSONB 处理规范

### 3. Maven 依赖添加

**文件**: `pom.xml`

新增依赖：
```xml
<dependency>
    <groupId>io.hypersistence</groupId>
    <artifactId>hypersistence-utils-hibernate-63</artifactId>
    <version>3.7.0</version>
</dependency>
```

---

## 已完成任务 ✅

### Phase 1: 基础配置 ✅

```
[x] 1.1 启用 JPA 审计
    - 在 JcAiAgentBackendApplication.java 添加 @EnableJpaAuditing
    
[x] 1.2 配置 application.yml (已有现有配置支持)
```

### Phase 2: 创建 Entity 类 ✅

```
目标包: fun.javierchen.jcaiagentbackend.model.entity.quiz

[x] 2.1 QuizSession.java       - 测验会话实体
[x] 2.2 QuizQuestion.java      - 测验题目实体
[x] 2.3 QuestionResponse.java  - 题目回答实体
[x] 2.4 UserKnowledgeState.java - 用户知识状态(三维认知模型)
[x] 2.5 UnmasteredKnowledge.java - 未掌握知识(知识缺口)
[x] 2.6 AgentExecutionLog.java - Agent执行日志
```

### Phase 3: 创建枚举类 ✅

```
目标包: fun.javierchen.jcaiagentbackend.model.entity.enums

[x] 3.1 QuizMode.java        (EASY, MEDIUM, HARD, ADAPTIVE)
[x] 3.2 QuizStatus.java      (IN_PROGRESS, COMPLETED, PAUSED, TIMEOUT, ABANDONED)
[x] 3.3 QuestionType.java    (9种题型)
[x] 3.4 Difficulty.java      (EASY, MEDIUM, HARD)
[x] 3.5 ConceptMastery.java  (MASTERED, PARTIAL, UNMASTERED)
[x] 3.6 AgentPhase.java      (THOUGHT, ACTION, OBSERVATION)
[x] 3.7 TopicType.java       (DOCUMENT, CONCEPT)
[x] 3.8 GapType.java         (CONCEPTUAL, PROCEDURAL, BOUNDARY)
[x] 3.9 Severity.java        (HIGH, MEDIUM, LOW)
[x] 3.10 KnowledgeGapStatus.java (ACTIVE, RESOLVED)
```

### Phase 4: 创建 Repository ✅

```
目标包: fun.javierchen.jcaiagentbackend.repository

[x] 4.1 QuizSessionRepository.java       - 测验会话仓库
[x] 4.2 QuizQuestionRepository.java      - 测验题目仓库
[x] 4.3 QuestionResponseRepository.java  - 题目回答仓库
[x] 4.4 UserKnowledgeStateRepository.java - 用户知识状态仓库
[x] 4.5 UnmasteredKnowledgeRepository.java - 未掌握知识仓库
[x] 4.6 AgentExecutionLogRepository.java - Agent执行日志仓库
```

### Phase 5: 测试类 ✅

```
目标包: fun.javierchen.jcaiagentbackend.repository (test)

[x] QuizSessionRepositoryTest.java       - 会话仓库测试
[x] QuizQuestionRepositoryTest.java      - 题目仓库测试
[x] QuestionResponseRepositoryTest.java  - 回答仓库测试
[x] UserKnowledgeStateRepositoryTest.java - 知识状态仓库测试
[x] UnmasteredKnowledgeRepositoryTest.java - 知识缺口仓库测试
[x] AgentExecutionLogRepositoryTest.java - 执行日志仓库测试
[x] EnumsTest.java                       - 枚举类测试
```

---

## Entity 设计要点

### 公共基类（可选）

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Data
public abstract class BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @CreatedDate
    @Column(name = "create_time", nullable = false, updatable = false)
    private OffsetDateTime createTime;
    
    @LastModifiedDate
    @Column(name = "update_time", nullable = false)
    private OffsetDateTime updateTime;
    
    @Column(name = "is_delete", nullable = false)
    private Integer isDelete = 0;
}
```

### JSONB 字段注解

```java
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;

// 强类型 - 结构稳定
@Type(JsonBinaryType.class)
@Column(name = "document_scope", columnDefinition = "jsonb")
private List<Long> documentScope;

// 弱类型 - 结构多变
@Type(JsonBinaryType.class)
@Column(name = "agent_state", columnDefinition = "jsonb")
private Map<String, Object> agentState;
```

### 关联关系（无数据库外键）

```java
// 多对一
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "session_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
private QuizSession session;

// 一对多
@OneToMany(mappedBy = "session", fetch = FetchType.LAZY)
private List<QuizQuestion> questions = new ArrayList<>();
```

---

## 性能优化策略

### 1. 避免 N+1 查询

```java
// 方法一: EntityGraph
@EntityGraph(attributePaths = {"questions"})
Optional<QuizSession> findWithQuestionsById(UUID id);

// 方法二: JPQL JOIN FETCH
@Query("SELECT s FROM QuizSession s LEFT JOIN FETCH s.questions WHERE s.id = :id")
Optional<QuizSession> findByIdWithQuestions(@Param("id") UUID id);
```

### 2. 分页查询

```java
Page<QuizSession> findByUserIdAndIsDelete(Long userId, Integer isDelete, Pageable pageable);
```

### 3. 复杂统计使用原生 SQL

```java
@Query(value = """
    SELECT status, COUNT(*) as count 
    FROM quiz_session 
    WHERE user_id = :userId AND is_delete = 0 
    GROUP BY status
    """, nativeQuery = true)
List<Object[]> countByStatusForUser(@Param("userId") Long userId);
```

---

## 文件清单

| 文件 | 状态 | 说明 |
|:---|:---|:---|
| `sql/quiz_module_tables.sql` | ✅ 已更新 | JPA 参考文档 |
| `sql/create_table.sql` | - | 现有表，保持不变 |
| `sql/create_PGvector_store.sql` | - | 向量表，保持不变 |
| `sql/user_document_persistence.sql` | - | MySQL 语法，非本项目 |
| `docs/后端设计文档.md` | ✅ 已更新 | 增加 JPA 规范 |
| `docs/SRS.md` | ✅ 已收敛 | 作为唯一需求文档维护 |
| `pom.xml` | ✅ 已更新 | 添加 Hibernate Types 依赖 |

---

## 注意事项

1. **开发环境 vs 生产环境**
   - 开发: `ddl-auto: update` - JPA 自动更新表结构
   - 生产: `ddl-auto: validate` - 使用 Flyway/Liquibase 迁移

2. **现有表兼容性**
   - User、Tenant 等现有表使用 `create_time`/`update_time`
   - 新建 Entity 时请保持一致

3. **JSONB 性能**
   - 避免在 JSONB 字段上进行复杂过滤
   - 如需频繁查询，考虑创建 GIN 索引

4. **UUID vs Long**
   - 新建表使用 UUID
   - 现有表（user, tenant）继续使用 Long
   - 跨表关联字段类型需匹配

---

*最后更新: 2026-01-21*
