package fun.javierchen.jcaiagentbackend.agent.quiz.cache;

import fun.javierchen.jcaiagentbackend.model.entity.enums.TopicType;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.UserKnowledgeState;
import fun.javierchen.jcaiagentbackend.repository.UserKnowledgeStateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeStateFlushService 测试")
class KnowledgeStateFlushServiceTest {

    @Mock
    private QuizRedisService quizRedisService;

    @Mock
    private UserKnowledgeStateRepository knowledgeStateRepository;

    @InjectMocks
    private KnowledgeStateFlushService flushService;

    @Test
    @DisplayName("全部 flush 成功时应清理整个 session 缓存")
    void flush_shouldCleanupWholeSession_whenAllStatesPersisted() {
        Map<String, Map<String, String>> allStates = new LinkedHashMap<>();
        allStates.put("Java集合", stateMap(78, 35, 80, 3, 2));
        allStates.put("线程安全", stateMap(72, 38, 76, 2, 2));

        when(quizRedisService.getAllKnowledgeStates("s1")).thenReturn(allStates);
        when(knowledgeStateRepository.findActiveByUserAndTopic(1L, 2L, TopicType.CONCEPT, "Java集合"))
                .thenReturn(Optional.empty());
        when(knowledgeStateRepository.findActiveByUserAndTopic(1L, 2L, TopicType.CONCEPT, "线程安全"))
                .thenReturn(Optional.empty());
        when(knowledgeStateRepository.save(any(UserKnowledgeState.class))).thenAnswer(invocation -> invocation.getArgument(0));

        flushService.flush("s1", 1L, 2L);

        verify(knowledgeStateRepository, times(2)).save(any(UserKnowledgeState.class));
        verify(quizRedisService).cleanupSession("s1");
        verify(quizRedisService, never()).deleteKnowledgeStates(any(), any());
    }

    @Test
    @DisplayName("部分 flush 失败时只清理已成功落库的概念缓存")
    void flush_shouldDeleteOnlySucceededConcepts_whenPartialFailureHappens() {
        Map<String, Map<String, String>> allStates = new LinkedHashMap<>();
        allStates.put("Java集合", stateMap(78, 35, 80, 3, 2));
        allStates.put("线程安全", stateMap(72, 38, 76, 2, 2));

        when(quizRedisService.getAllKnowledgeStates("s2")).thenReturn(allStates);
        when(knowledgeStateRepository.findActiveByUserAndTopic(1L, 2L, TopicType.CONCEPT, "Java集合"))
                .thenReturn(Optional.empty());
        when(knowledgeStateRepository.findActiveByUserAndTopic(1L, 2L, TopicType.CONCEPT, "线程安全"))
                .thenReturn(Optional.empty());
        when(knowledgeStateRepository.save(argThat(arg ->
                arg != null && "Java集合".equals(arg.getTopicName()))))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(knowledgeStateRepository.save(argThat(arg ->
                arg != null && "线程安全".equals(arg.getTopicName()))))
                .thenThrow(new RuntimeException("db write failed"));

        flushService.flush("s2", 1L, 2L);

        ArgumentCaptor<List<String>> conceptsCaptor = ArgumentCaptor.forClass(List.class);
        verify(quizRedisService).deleteKnowledgeStates(org.mockito.ArgumentMatchers.eq("s2"), conceptsCaptor.capture());
        verify(quizRedisService, never()).cleanupSession("s2");
        assertThat(conceptsCaptor.getValue()).containsExactly("Java集合");
    }

    @Test
    @DisplayName("全部 flush 失败时应保留全部缓存等待重试")
    void flush_shouldKeepAllBufferedStates_whenNothingWasPersisted() {
        Map<String, Map<String, String>> allStates = Map.of("Java集合", stateMap(78, 35, 80, 3, 2));

        when(quizRedisService.getAllKnowledgeStates("s3")).thenReturn(allStates);
        when(knowledgeStateRepository.findActiveByUserAndTopic(1L, 2L, TopicType.CONCEPT, "Java集合"))
                .thenReturn(Optional.empty());
        when(knowledgeStateRepository.save(any(UserKnowledgeState.class)))
                .thenThrow(new RuntimeException("db write failed"));

        flushService.flush("s3", 1L, 2L);

        verify(quizRedisService, never()).cleanupSession("s3");
        verify(quizRedisService, never()).deleteKnowledgeStates(any(), any());
    }

    private Map<String, String> stateMap(int depth, int load, int stability, int total, int correct) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("depth", String.valueOf(depth));
        map.put("load", String.valueOf(load));
        map.put("stability", String.valueOf(stability));
        map.put("total", String.valueOf(total));
        map.put("correct", String.valueOf(correct));
        return map;
    }
}
