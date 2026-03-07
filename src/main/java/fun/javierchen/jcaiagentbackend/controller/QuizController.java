package fun.javierchen.jcaiagentbackend.controller;

import fun.javierchen.jcaiagentbackend.common.BaseResponse;
import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.common.ResultUtils;
import fun.javierchen.jcaiagentbackend.common.TenantContextHolder;
import fun.javierchen.jcaiagentbackend.controller.dto.quiz.*;
import fun.javierchen.jcaiagentbackend.exception.ThrowUtils;
import fun.javierchen.jcaiagentbackend.model.entity.User;
import fun.javierchen.jcaiagentbackend.service.QuizSessionService;
import fun.javierchen.jcaiagentbackend.service.UserAnalysisService;
import fun.javierchen.jcaiagentbackend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 测验模块控制器
 *
 * @author JavierChen
 */
@RestController
@RequestMapping("/v1/quiz")
@RequiredArgsConstructor
@Tag(name = "智能测验", description = "智能测验会话管理与分析接口")
public class QuizController {

    private final QuizSessionService quizSessionService;
    private final UserAnalysisService userAnalysisService;
    private final UserService userService;

    // ==================== 会话管理 ====================

    @PostMapping("/session")
    @Operation(summary = "创建测验会话", description = "开始一次新的测验")
    public BaseResponse<QuizSessionVO> createSession(
            @Valid @RequestBody CreateQuizSessionRequest request,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        Long tenantId = TenantContextHolder.getTenantId();
        QuizSessionVO session = quizSessionService.createSession(tenantId, loginUser.getId(), request);
        return ResultUtils.success(session);
    }

    @GetMapping("/session/{id}")
    @Operation(summary = "查询会话详情", description = "获取测验会话的完整信息")
    public BaseResponse<QuizSessionDetailVO> getSessionDetail(
            @PathVariable("id") UUID sessionId,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        QuizSessionDetailVO detail = quizSessionService.getSessionDetail(sessionId, loginUser.getId());
        return ResultUtils.success(detail);
    }

    @PutMapping("/session/{id}")
    @Operation(summary = "更新会话状态", description = "暂停/恢复/放弃测验")
    public BaseResponse<QuizSessionVO> updateSession(
            @PathVariable("id") UUID sessionId,
            @Valid @RequestBody UpdateQuizSessionRequest request,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        QuizSessionVO session = quizSessionService.updateSession(sessionId, loginUser.getId(), request);
        return ResultUtils.success(session);
    }

    @DeleteMapping("/session/{id}")
    @Operation(summary = "删除会话", description = "删除测验会话 (软删除)")
    public BaseResponse<Boolean> deleteSession(
            @PathVariable("id") UUID sessionId,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        boolean result = quizSessionService.deleteSession(sessionId, loginUser.getId());
        return ResultUtils.success(result);
    }

    @GetMapping("/session/list")
    @Operation(summary = "查询测验历史", description = "获取用户的测验会话列表")
    public BaseResponse<QuizSessionListVO> listSessions(
            QuizSessionQueryRequest request,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        QuizSessionListVO list = quizSessionService.listUserSessions(loginUser.getId(), request);
        return ResultUtils.success(list);
    }

    // ==================== 答题交互 ====================

    @PostMapping("/session/{id}/answer")
    @Operation(summary = "提交答案", description = "提交题目答案并获取评估结果")
    public BaseResponse<SubmitAnswerResponse> submitAnswer(
            @PathVariable("id") UUID sessionId,
            @Valid @RequestBody SubmitAnswerRequest request,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        Long tenantId = TenantContextHolder.getTenantId();
        SubmitAnswerResponse response = quizSessionService.submitAnswer(
                sessionId, tenantId, loginUser.getId(), request);
        return ResultUtils.success(response);
    }

    @GetMapping("/session/{id}/status")
    @Operation(summary = "获取会话状态", description = "获取会话实时状态")
    public BaseResponse<QuizSessionStatusVO> getSessionStatus(
            @PathVariable("id") UUID sessionId,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        QuizSessionStatusVO status = quizSessionService.getSessionStatus(sessionId, loginUser.getId());
        return ResultUtils.success(status);
    }

    @GetMapping("/session/{id}/next")
    @Operation(summary = "获取下一题", description = "获取当前会话的下一道题目")
    public BaseResponse<QuestionVO> getNextQuestion(
            @PathVariable("id") UUID sessionId,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        QuestionVO question = quizSessionService.getNextQuestion(sessionId, loginUser.getId());
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR, "没有更多题目");
        return ResultUtils.success(question);
    }

    // ==================== 分析接口 ====================

    @GetMapping("/analysis/user/{userId}")
    @Operation(summary = "获取用户认知状态", description = "查看用户的三维认知模型状态")
    public BaseResponse<UserCognitiveStateVO> getUserCognitiveState(
            @PathVariable("userId") Long userId,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        // 自己可以查看自己的，管理员可以查看所有人的
        if (!loginUser.getId().equals(userId) && !userService.isAdmin(loginUser)) {
            ThrowUtils.throwIf(true, ErrorCode.NO_AUTH_ERROR);
        }
        Long tenantId = TenantContextHolder.getTenantId();
        UserCognitiveStateVO state = userAnalysisService.getUserCognitiveState(tenantId, userId);
        return ResultUtils.success(state);
    }

    @GetMapping("/analysis/session/{id}/report")
    @Operation(summary = "获取会话报告", description = "获取测验会话的详细分析报告")
    public BaseResponse<SessionReportVO> getSessionReport(
            @PathVariable("id") UUID sessionId,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        SessionReportVO report = userAnalysisService.getSessionReport(sessionId, loginUser.getId());
        return ResultUtils.success(report);
    }

    @GetMapping("/analysis/user/{userId}/gaps")
    @Operation(summary = "获取知识缺口", description = "获取用户的知识缺口列表")
    public BaseResponse<KnowledgeGapListVO> getUserKnowledgeGaps(
            @PathVariable("userId") Long userId,
            KnowledgeGapQueryRequest request,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        if (!loginUser.getId().equals(userId) && !userService.isAdmin(loginUser)) {
            ThrowUtils.throwIf(true, ErrorCode.NO_AUTH_ERROR);
        }
        KnowledgeGapListVO gaps = userAnalysisService.getUserKnowledgeGaps(userId, request);
        return ResultUtils.success(gaps);
    }

    @PostMapping("/analysis/gap/{id}/resolve")
    @Operation(summary = "标记缺口已解决", description = "将知识缺口标记为已解决")
    public BaseResponse<Boolean> markGapAsResolved(
            @PathVariable("id") UUID gapId,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        boolean result = userAnalysisService.markGapAsResolved(gapId, loginUser.getId());
        return ResultUtils.success(result);
    }
}
