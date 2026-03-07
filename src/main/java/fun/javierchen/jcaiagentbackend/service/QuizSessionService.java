package fun.javierchen.jcaiagentbackend.service;

import fun.javierchen.jcaiagentbackend.controller.dto.quiz.*;

import java.util.UUID;

/**
 * 测验会话服务接口
 *
 * @author JavierChen
 */
public interface QuizSessionService {

    /**
     * 创建测验会话
     */
    QuizSessionVO createSession(Long tenantId, Long userId, CreateQuizSessionRequest request);

    /**
     * 获取会话详情
     */
    QuizSessionDetailVO getSessionDetail(UUID sessionId, Long userId);

    /**
     * 更新会话状态 (暂停/恢复/放弃)
     */
    QuizSessionVO updateSession(UUID sessionId, Long userId, UpdateQuizSessionRequest request);

    /**
     * 删除会话 (软删除)
     */
    boolean deleteSession(UUID sessionId, Long userId);

    /**
     * 获取用户的测验历史
     */
    QuizSessionListVO listUserSessions(Long userId, QuizSessionQueryRequest request);

    /**
     * 提交答案
     */
    SubmitAnswerResponse submitAnswer(UUID sessionId, Long tenantId, Long userId, SubmitAnswerRequest request);

    /**
     * 获取会话实时状态
     */
    QuizSessionStatusVO getSessionStatus(UUID sessionId, Long userId);

    /**
     * 获取下一题
     */
    QuestionVO getNextQuestion(UUID sessionId, Long userId);
}
