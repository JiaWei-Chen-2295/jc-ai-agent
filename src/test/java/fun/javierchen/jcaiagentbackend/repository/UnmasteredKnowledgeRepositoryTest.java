package fun.javierchen.jcaiagentbackend.repository;

import fun.javierchen.jcaiagentbackend.model.entity.enums.*;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.UnmasteredKnowledge;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("UnmasteredKnowledge Repository 测试")
class UnmasteredKnowledgeRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UnmasteredKnowledgeRepository repository;

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 100L;
    private UnmasteredKnowledge savedGap;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        savedGap = createKnowledgeGap("多线程同步", GapType.CONCEPTUAL, Severity.MEDIUM, 2);
        entityManager.persistAndFlush(savedGap);
    }

    private UnmasteredKnowledge createKnowledgeGap(String conceptName, GapType type, Severity severity,
            int failureCount) {
        UnmasteredKnowledge gap = new UnmasteredKnowledge();
        gap.setTenantId(TENANT_ID);
        gap.setUserId(USER_ID);
        gap.setConceptName(conceptName);
        gap.setGapType(type);
        gap.setSeverity(severity);
        gap.setFailureCount(failureCount);
        gap.setStatus(KnowledgeGapStatus.ACTIVE);
        gap.setIsDelete(0);
        return gap;
    }

    @Test
    @DisplayName("保存知识缺口应生成UUID")
    void save_shouldGenerateUUID() {
        UnmasteredKnowledge newGap = createKnowledgeGap("内存管理", GapType.PROCEDURAL, Severity.LOW, 1);
        UnmasteredKnowledge saved = repository.save(newGap);
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    @DisplayName("查询活跃缺口")
    void findActiveGapsByUserId_shouldReturnActiveOnly() {
        List<UnmasteredKnowledge> activeGaps = repository.findActiveGapsByUserId(USER_ID);
        assertThat(activeGaps).hasSize(1);
        assertThat(activeGaps.get(0).getStatus()).isEqualTo(KnowledgeGapStatus.ACTIVE);
    }

    @Test
    @DisplayName("统计活跃缺口数量")
    void countActiveGapsByUserId_shouldReturnCount() {
        long count = repository.countActiveGapsByUserId(USER_ID);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("批量标记为已解决")
    void markAsResolved_shouldUpdateStatus() {
        List<UUID> ids = List.of(savedGap.getId());
        int updated = repository.markAsResolved(ids, OffsetDateTime.now());
        entityManager.clear();
        assertThat(updated).isEqualTo(1);
        UnmasteredKnowledge reloaded = repository.findById(savedGap.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(KnowledgeGapStatus.RESOLVED);
    }

    @Test
    @DisplayName("Entity便捷方法-markAsResolved")
    void entityMarkAsResolved_shouldSetStatusAndTime() {
        savedGap.markAsResolved();
        assertThat(savedGap.getStatus()).isEqualTo(KnowledgeGapStatus.RESOLVED);
        assertThat(savedGap.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("Entity便捷方法-incrementFailureCount")
    void entityIncrementFailureCount_shouldUpgradeSeverity() {
        savedGap.setSeverity(Severity.LOW);
        savedGap.setFailureCount(2);
        savedGap.incrementFailureCount();
        assertThat(savedGap.getSeverity()).isEqualTo(Severity.MEDIUM);
    }

    @Test
    @DisplayName("Entity便捷方法-isHighPriority")
    void isHighPriority_shouldReturnCorrectly() {
        savedGap.setSeverity(Severity.HIGH);
        assertThat(savedGap.isHighPriority()).isTrue();
        savedGap.setSeverity(Severity.LOW);
        savedGap.setFailureCount(5);
        assertThat(savedGap.isHighPriority()).isTrue();
    }
}
