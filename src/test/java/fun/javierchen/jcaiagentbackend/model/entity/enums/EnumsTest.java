package fun.javierchen.jcaiagentbackend.model.entity.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 枚举类测试
 * 测试所有枚举的方法和逻辑
 *
 * @author JavierChen
 */
@DisplayName("枚举类测试")
class EnumsTest {

    @Nested
    @DisplayName("QuestionType 测试")
    class QuestionTypeTest {

        @Test
        @DisplayName("fromCode 应返回正确的枚举")
        void fromCode_shouldReturnCorrectEnum() {
            assertThat(QuestionType.fromCode("single_choice")).isEqualTo(QuestionType.SINGLE_CHOICE);
            assertThat(QuestionType.fromCode("SINGLE_CHOICE")).isEqualTo(QuestionType.SINGLE_CHOICE);
            assertThat(QuestionType.fromCode("invalid")).isNull();
        }

        @Test
        @DisplayName("requiresOptions 应正确判断")
        void requiresOptions_shouldReturnCorrectly() {
            assertThat(QuestionType.SINGLE_CHOICE.requiresOptions()).isTrue();
            assertThat(QuestionType.MULTIPLE_SELECT.requiresOptions()).isTrue();
            assertThat(QuestionType.MATCHING.requiresOptions()).isTrue();
            assertThat(QuestionType.ORDERING.requiresOptions()).isTrue();

            assertThat(QuestionType.TRUE_FALSE.requiresOptions()).isFalse();
            assertThat(QuestionType.FILL_IN_BLANK.requiresOptions()).isFalse();
            assertThat(QuestionType.EXPLANATION.requiresOptions()).isFalse();
        }

        @Test
        @DisplayName("isObjective 应区分客观题和主观题")
        void isObjective_shouldDistinguish() {
            assertThat(QuestionType.SINGLE_CHOICE.isObjective()).isTrue();
            assertThat(QuestionType.TRUE_FALSE.isObjective()).isTrue();
            assertThat(QuestionType.FILL_IN_BLANK.isObjective()).isTrue();

            assertThat(QuestionType.EXPLANATION.isObjective()).isFalse();
            assertThat(QuestionType.SHORT_ANSWER.isObjective()).isFalse();
            assertThat(QuestionType.CODE_COMPLETION.isObjective()).isFalse();
        }

        @Test
        @DisplayName("isSubjective 应正确判断主观题")
        void isSubjective_shouldIdentifySubjective() {
            assertThat(QuestionType.SHORT_ANSWER.isSubjective()).isTrue();
            assertThat(QuestionType.EXPLANATION.isSubjective()).isTrue();
            assertThat(QuestionType.CODE_COMPLETION.isSubjective()).isTrue();

            assertThat(QuestionType.SINGLE_CHOICE.isSubjective()).isFalse();
        }

        @Test
        @DisplayName("supportsPartialScore 应正确判断")
        void supportsPartialScore_shouldReturnCorrectly() {
            assertThat(QuestionType.MULTIPLE_SELECT.supportsPartialScore()).isTrue();
            assertThat(QuestionType.FILL_IN_BLANK.supportsPartialScore()).isTrue();
            assertThat(QuestionType.MATCHING.supportsPartialScore()).isTrue();

            assertThat(QuestionType.SINGLE_CHOICE.supportsPartialScore()).isFalse();
            assertThat(QuestionType.TRUE_FALSE.supportsPartialScore()).isFalse();
        }
    }

    @Nested
    @DisplayName("Difficulty 测试")
    class DifficultyTest {

        @Test
        @DisplayName("fromCode 应返回正确的枚举")
        void fromCode_shouldReturnCorrectEnum() {
            assertThat(Difficulty.fromCode("easy")).isEqualTo(Difficulty.EASY);
            assertThat(Difficulty.fromCode("MEDIUM")).isEqualTo(Difficulty.MEDIUM);
            assertThat(Difficulty.fromCode("invalid")).isNull();
        }

        @Test
        @DisplayName("fromLevel 应返回正确的难度")
        void fromLevel_shouldReturnCorrectDifficulty() {
            assertThat(Difficulty.fromLevel(1)).isEqualTo(Difficulty.EASY);
            assertThat(Difficulty.fromLevel(2)).isEqualTo(Difficulty.MEDIUM);
            assertThat(Difficulty.fromLevel(3)).isEqualTo(Difficulty.HARD);
            assertThat(Difficulty.fromLevel(99)).isEqualTo(Difficulty.MEDIUM); // 默认
        }

        @Test
        @DisplayName("harder 应返回更高难度")
        void harder_shouldReturnHigherDifficulty() {
            assertThat(Difficulty.EASY.harder()).isEqualTo(Difficulty.MEDIUM);
            assertThat(Difficulty.MEDIUM.harder()).isEqualTo(Difficulty.HARD);
            assertThat(Difficulty.HARD.harder()).isEqualTo(Difficulty.HARD); // 最高已无法提升
        }

        @Test
        @DisplayName("easier 应返回更低难度")
        void easier_shouldReturnLowerDifficulty() {
            assertThat(Difficulty.HARD.easier()).isEqualTo(Difficulty.MEDIUM);
            assertThat(Difficulty.MEDIUM.easier()).isEqualTo(Difficulty.EASY);
            assertThat(Difficulty.EASY.easier()).isEqualTo(Difficulty.EASY); // 最低已无法降低
        }
    }

    @Nested
    @DisplayName("QuizStatus 测试")
    class QuizStatusTest {

        @Test
        @DisplayName("fromCode 应返回正确的枚举")
        void fromCode_shouldReturnCorrectEnum() {
            assertThat(QuizStatus.fromCode("in_progress")).isEqualTo(QuizStatus.IN_PROGRESS);
            assertThat(QuizStatus.fromCode("completed")).isEqualTo(QuizStatus.COMPLETED);
            assertThat(QuizStatus.fromCode("invalid")).isNull();
        }

        @Test
        @DisplayName("isTerminal 应识别终态")
        void isTerminal_shouldIdentifyTerminalStates() {
            assertThat(QuizStatus.COMPLETED.isTerminal()).isTrue();
            assertThat(QuizStatus.TIMEOUT.isTerminal()).isTrue();
            assertThat(QuizStatus.ABANDONED.isTerminal()).isTrue();

            assertThat(QuizStatus.IN_PROGRESS.isTerminal()).isFalse();
            assertThat(QuizStatus.PAUSED.isTerminal()).isFalse();
        }

        @Test
        @DisplayName("canPause 应正确判断")
        void canPause_shouldReturnCorrectly() {
            assertThat(QuizStatus.IN_PROGRESS.canPause()).isTrue();
            assertThat(QuizStatus.PAUSED.canPause()).isFalse();
            assertThat(QuizStatus.COMPLETED.canPause()).isFalse();
        }

        @Test
        @DisplayName("canResume 应正确判断")
        void canResume_shouldReturnCorrectly() {
            assertThat(QuizStatus.PAUSED.canResume()).isTrue();
            assertThat(QuizStatus.IN_PROGRESS.canResume()).isFalse();
            assertThat(QuizStatus.COMPLETED.canResume()).isFalse();
        }
    }

    @Nested
    @DisplayName("ConceptMastery 测试")
    class ConceptMasteryTest {

        @Test
        @DisplayName("fromCode 应返回正确的枚举")
        void fromCode_shouldReturnCorrectEnum() {
            assertThat(ConceptMastery.fromCode("mastered")).isEqualTo(ConceptMastery.MASTERED);
            assertThat(ConceptMastery.fromCode("partial")).isEqualTo(ConceptMastery.PARTIAL);
            assertThat(ConceptMastery.fromCode("invalid")).isNull();
        }

        @Test
        @DisplayName("needsRemediation 应正确判断")
        void needsRemediation_shouldReturnCorrectly() {
            assertThat(ConceptMastery.PARTIAL.needsRemediation()).isTrue();
            assertThat(ConceptMastery.UNMASTERED.needsRemediation()).isTrue();
            assertThat(ConceptMastery.MASTERED.needsRemediation()).isFalse();
        }

        @Test
        @DisplayName("isFullyMastered 应正确判断")
        void isFullyMastered_shouldReturnCorrectly() {
            assertThat(ConceptMastery.MASTERED.isFullyMastered()).isTrue();
            assertThat(ConceptMastery.PARTIAL.isFullyMastered()).isFalse();
            assertThat(ConceptMastery.UNMASTERED.isFullyMastered()).isFalse();
        }
    }

    @Nested
    @DisplayName("Severity 测试")
    class SeverityTest {

        @Test
        @DisplayName("fromCode 应返回正确的枚举")
        void fromCode_shouldReturnCorrectEnum() {
            assertThat(Severity.fromCode("high")).isEqualTo(Severity.HIGH);
            assertThat(Severity.fromCode("MEDIUM")).isEqualTo(Severity.MEDIUM);
            assertThat(Severity.fromCode("invalid")).isNull();
        }

        @Test
        @DisplayName("getPriority 应返回正确的优先级")
        void getPriority_shouldReturnCorrectPriority() {
            assertThat(Severity.HIGH.getPriority()).isEqualTo(3);
            assertThat(Severity.MEDIUM.getPriority()).isEqualTo(2);
            assertThat(Severity.LOW.getPriority()).isEqualTo(1);
        }
    }
}
