package fun.javierchen.jcaiagentbackend.service;

import fun.javierchen.jcaiagentbackend.controller.dto.quiz.*;

/**
 * 用户分析服务接口
 *
 * @author JavierChen
 */
public interface UserAnalysisService {

    /**
     * 获取用户认知状态
     */
    UserCognitiveStateVO getUserCognitiveState(Long tenantId, Long userId);

    /**
     * 获取会话分析报告
     */
    SessionReportVO getSessionReport(java.util.UUID sessionId, Long userId);

    /**
     * 获取用户知识缺口列表
     */
    KnowledgeGapListVO getUserKnowledgeGaps(Long userId, KnowledgeGapQueryRequest request);

    /**
     * 标记知识缺口为已解决
     */
    boolean markGapAsResolved(java.util.UUID gapId, Long userId);
}
