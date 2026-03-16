package fun.javierchen.jcaiagentbackend.service.impl;

import fun.javierchen.jcaiagentbackend.agent.quiz.QuizReActAgent;
import fun.javierchen.jcaiagentbackend.agent.quiz.analyzer.CognitiveAnalyzer;
import fun.javierchen.jcaiagentbackend.agent.quiz.cache.QuizRedisService;
import fun.javierchen.jcaiagentbackend.agent.quiz.core.AgentContext;
import fun.javierchen.jcaiagentbackend.agent.quiz.core.AgentResponse;
import fun.javierchen.jcaiagentbackend.agent.quiz.decision.DecisionEngine;
import fun.javierchen.jcaiagentbackend.agent.quiz.inventory.ConceptInventoryService;
import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.controller.dto.quiz.*;
import fun.javierchen.jcaiagentbackend.exception.BusinessException;
import fun.javierchen.jcaiagentbackend.model.entity.enums.ConceptMastery;
import fun.javierchen.jcaiagentbackend.model.entity.enums.Difficulty;
import fun.javierchen.jcaiagentbackend.model.entity.enums.GapType;
import fun.javierchen.jcaiagentbackend.model.entity.enums.KnowledgeGapStatus;
import fun.javierchen.jcaiagentbackend.model.entity.enums.QuestionType;
import fun.javierchen.jcaiagentbackend.model.entity.enums.QuizStatus;
import fun.javierchen.jcaiagentbackend.model.entity.enums.Severity;
import fun.javierchen.jcaiagentbackend.model.entity.enums.TopicType;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.*;
import fun.javierchen.jcaiagentbackend.repository.*;
import fun.javierchen.jcaiagentbackend.service.QuizSessionService;
import fun.javierchen.jcaiagentbackend.agent.quiz.cache.KnowledgeStateFlushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;


/**
 * 测验会话服务实现
 *
 * @author JavierChen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizSessionServiceImpl implements QuizSessionService {

    private final QuizSessionRepository sessionRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuestionResponseRepository responseRepository;
    private final UserKnowledgeStateRepository knowledgeStateRepository;
    private final UnmasteredKnowledgeRepository unmasteredKnowledgeRepository;
    private final QuizReActAgent quizAgent;
    private final CognitiveAnalyzer cognitiveAnalyzer;
    private final DecisionEngine decisionEngine;
    private final ConceptInventoryService conceptInventoryService;
    private final QuizRedisService quizRedisService;
    private final KnowledgeStateFlushService knowledgeStateFlushService;

    @Override
    @Transactional
    public QuizSessionVO createSession(Long tenantId, Long userId, CreateQuizSessionRequest request) {
        log.info("创建测验会话: userId={}, mode={}", userId, request.getQuizMode());

        // 检查是否有正在进行的会话
        if (sessionRepository.hasInProgressSession(userId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "您有一个正在进行的测验，请先完成或取消");
        }

        // 创建会话
        QuizSession session = new QuizSession();
        session.setTenantId(tenantId);
        session.setUserId(userId);
        session.setQuizMode(request.getQuizMode());
        session.setDocumentScope(request.getDocumentIds());
        session.setStatus(QuizStatus.IN_PROGRESS);
        session.setStartedAt(LocalDateTime.now());

        session = sessionRepository.save(session);

        // Phase 2a: 提取概念清单 → 写 Redis + agentState 双写
        try {
            Set<String> concepts = conceptInventoryService.extractAndCacheConcepts(
                    session.getId().toString(), tenantId, request.getDocumentIds());
            if (!concepts.isEmpty()) {
                Map<String, Object> agentState = session.getAgentState();
                if (agentState == null) {
                    agentState = new HashMap<>();
                }
                agentState.put("concepts", new ArrayList<>(concepts));
                session.setAgentState(agentState);
                session = sessionRepository.save(session);
            }
        } catch (Exception e) {
            log.warn("概念提取失败，不影响会话创建: {}", e.getMessage());
        }

        // 使用 Agent 生成第一批题目
        QuestionVO firstQuestion = generateAndSaveQuestions(session, tenantId, userId);

        return QuizSessionVO.builder()
                .sessionId(session.getId())
                .quizMode(session.getQuizMode())
                .status(session.getStatus())
                .currentQuestionNo(session.getCurrentQuestionNo())
                .totalQuestions(session.getTotalQuestions())
                .score(session.getScore())
                .startedAt(session.getStartedAt())
                .createTime(session.getCreateTime())
                .firstQuestion(firstQuestion)
                .build();
    }

    /**
     * 生成并保存题目
     */
    private QuestionVO generateAndSaveQuestions(QuizSession session, Long tenantId, Long userId) {
        // 获取用户知识状态
        List<UserKnowledgeState> knowledgeStates = knowledgeStateRepository.findActiveByUserId(userId);

        // 构建 Agent 上下文
        AgentContext context = AgentContext.builder()
                .tenantId(tenantId)
                .userId(userId)
                .session(session)
                .knowledgeStates(knowledgeStates)
                .documentScope(session.getDocumentScope())
                .extras(buildGenerationExtras(session))
                .build();

        // 执行 Agent
        AgentResponse response = quizAgent.execute(context);

        log.info("Agent 执行完成: success={}, type={}, questionsCount={}", 
                response.isSuccess(), 
                response.getType(),
                response.getQuestions() != null ? response.getQuestions().size() : 0);
        
        if (!response.isSuccess()) {
            log.warn("Agent 执行失败: {}", response.getErrorMessage());
            return createDefaultQuestion(session, tenantId);
        }
        
        if (response.getQuestions() == null) {
            log.warn("Agent 返回的 questions 为 null，使用默认题目");
            return createDefaultQuestion(session, tenantId);
        }
        
        if (response.getQuestions().isEmpty()) {
            log.warn("Agent 返回的 questions 为空列表，使用默认题目");
            return createDefaultQuestion(session, tenantId);
        }

        // 保存题目
        int questionNo = session.getCurrentQuestionNo();
        QuestionVO firstQuestion = null;

        for (AgentResponse.GeneratedQuestion generated : response.getQuestions()) {
            questionNo++;
            QuizQuestion question = new QuizQuestion();
            question.setTenantId(tenantId);
            question.setSession(session);
            question.setQuestionNo(questionNo);
            question.setQuestionText(generated.getText());
            
            // 解析题目类型，容错处理
            QuestionType questionType = parseQuestionType(generated.getType());
            if (questionType == null) {
                log.warn("无法识别的题目类型: {}, 默认使用单选题", generated.getType());
                questionType = QuestionType.SINGLE_CHOICE;
            }
            question.setQuestionType(questionType);
            
            question.setOptions(generated.getOptions());
            question.setCorrectAnswer(generated.getCorrectAnswer());
            question.setExplanation(generated.getExplanation());
            question.setRelatedConcept(generated.getRelatedConcept());
            
            // 解析难度，容错处理
            Difficulty difficulty = parseDifficulty(generated.getDifficulty());
            if (difficulty == null) {
                log.warn("无法识别的难度: {}, 默认使用中等难度", generated.getDifficulty());
                difficulty = Difficulty.MEDIUM;
            }
            question.setDifficulty(difficulty);

            question = questionRepository.save(question);

            if (firstQuestion == null) {
                firstQuestion = convertToQuestionVO(question);
            }
        }

        // 更新会话
        session.setTotalQuestions(questionNo);
        session.setCurrentQuestionNo(1);
        sessionRepository.save(session);

        return firstQuestion;
    }

    /**
     * 创建默认题目 (降级处理)
     */
    private QuestionVO createDefaultQuestion(QuizSession session, Long tenantId) {
        QuizQuestion question = new QuizQuestion();
        question.setTenantId(tenantId);
        question.setSession(session);
        question.setQuestionNo(1);
        question.setQuestionText("以下哪个是 Java 的基本数据类型？");
        question.setQuestionType(QuestionType.SINGLE_CHOICE);
        question.setOptions(List.of("String", "Integer", "int", "Object"));
        question.setCorrectAnswer("int");
        question.setExplanation("int 是 Java 的基本数据类型，其他都是引用类型");
        question.setRelatedConcept("Java基本数据类型");
        question.setDifficulty(Difficulty.EASY);

        question = questionRepository.save(question);

        session.setTotalQuestions(1);
        session.setCurrentQuestionNo(1);
        sessionRepository.save(session);

        return convertToQuestionVO(question);
    }
    
    /**
     * 解析题目类型，支持多种格式
     * 支持: SINGLE_CHOICE, single_choice, singleChoice 等
     */
    private QuestionType parseQuestionType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        
        // 先尝试直接匹配 code
        QuestionType result = QuestionType.fromCode(type);
        if (result != null) {
            return result;
        }
        
        // 尝试大写下划线转小写下划线
        String normalized = type.toLowerCase();
        result = QuestionType.fromCode(normalized);
        if (result != null) {
            return result;
        }
        
        // 手动映射常见的 LLM 返回格式
        return switch (normalized) {
            case "single_choice", "singlechoice" -> QuestionType.SINGLE_CHOICE;
            case "multiple_choice", "multiplechoice", "multiple_select", "multipleselect" -> QuestionType.MULTIPLE_SELECT;
            case "true_false", "truefalse", "judge" -> QuestionType.TRUE_FALSE;
            case "fill_in_blank", "fillinblank", "fill_in_the_blank", "fillinthblank" -> QuestionType.FILL_IN_BLANK;
            case "short_answer", "shortanswer" -> QuestionType.SHORT_ANSWER;
            case "explanation" -> QuestionType.EXPLANATION;
            case "matching" -> QuestionType.MATCHING;
            case "ordering" -> QuestionType.ORDERING;
            case "code_completion", "codecompletion" -> QuestionType.CODE_COMPLETION;
            default -> null;
        };
    }
    
    /**
     * 解析难度，支持多种格式
     */
    private Difficulty parseDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return null;
        }
        
        try {
            // 尝试直接解析枚举
            return Difficulty.valueOf(difficulty.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.debug("无法解析难度: {}", difficulty);
            return null;
        }
    }

    @Override
    public QuizSessionDetailVO getSessionDetail(UUID sessionId, Long userId) {
        // 分步查询，避免 MultipleBagFetchException
        // 1. 查询会话基本信息
        QuizSession session = sessionRepository.findActiveById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在"));

        // 权限校验
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问此会话");
        }
        
        // 2. 分别查询题目和回答（避免同时 fetch 两个集合）
        List<QuestionVO> questions = questionRepository.findActiveBySessionId(sessionId).stream()
                .map(this::convertToQuestionVO)
                .toList();

        List<ResponseVO> responses = responseRepository.findActiveBySessionId(sessionId).stream()
                .map(this::convertToResponseVO)
                .toList();

        // 计算认知摘要
        Double avgTime = responseRepository.findAvgResponseTimeBySessionId(sessionId);
        long correctCount = responseRepository.countCorrectBySessionId(sessionId);
        long totalCount = responseRepository.countActiveBySessionId(sessionId);
        double accuracy = totalCount > 0 ? (double) correctCount / totalCount * 100 : 0;

        QuizSessionDetailVO.CognitiveSummary summary = QuizSessionDetailVO.CognitiveSummary.builder()
                .understandingDepth(50) // 简化处理
                .cognitiveLoad(50)
                .stability(50)
                .accuracy(accuracy)
                .build();

        return QuizSessionDetailVO.builder()
                .session(convertToSessionVO(session))
                .questions(questions)
                .responses(responses)
                .cognitiveSummary(summary)
                .build();
    }

    @Override
    @Transactional
    public QuizSessionVO updateSession(UUID sessionId, Long userId, UpdateQuizSessionRequest request) {
        QuizSession session = sessionRepository.findActiveById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在"));

        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作此会话");
        }

        switch (request.getAction()) {
            case PAUSE -> {
                if (session.getStatus() != QuizStatus.IN_PROGRESS) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "只有进行中的会话可以暂停");
                }
                log.info("暂停会话: sessionId={}, progress={}/{}",
                        sessionId, session.getCurrentQuestionNo(), session.getTotalQuestions());
                session.setStatus(QuizStatus.PAUSED);
            }
            case RESUME -> {
                if (session.getStatus() != QuizStatus.PAUSED) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "只有暂停的会话可以恢复");
                }
                log.info("恢复会话: sessionId={}", sessionId);
                session.setStatus(QuizStatus.IN_PROGRESS);
            }
            case ABANDON -> {
                if (session.isFinished()) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "会话已结束");
                }
                log.info("放弃会话: sessionId={}, progress={}/{}",
                        sessionId, session.getCurrentQuestionNo(), session.getTotalQuestions());
                session.setStatus(QuizStatus.ABANDONED);
                session.setCompletedAt(LocalDateTime.now());
                // Phase 2b: 放弃也 flush（保留学习记录）
                flushKnowledgeStates(session.getId().toString(), session.getTenantId(), session.getUserId());
            }
        }

        session = sessionRepository.save(session);
        return convertToSessionVO(session);
    }

    @Override
    @Transactional
    public boolean deleteSession(UUID sessionId, Long userId) {
        QuizSession session = sessionRepository.findActiveById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在"));

        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权删除此会话");
        }

        session.setIsDelete(1);
        sessionRepository.save(session);
        return true;
    }

    @Override
    public QuizSessionListVO listUserSessions(Long userId, QuizSessionQueryRequest request) {
        PageRequest pageable = PageRequest.of(
                request.getPageNum() - 1,
                request.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));

        Page<QuizSession> page;
        if (request.getStatus() != null) {
            page = sessionRepository.findByUserIdAndIsDelete(userId, 0, pageable);
        } else {
            page = sessionRepository.findActiveByUserId(userId, pageable);
        }

        List<QuizSessionVO> list = page.getContent().stream()
                .map(this::convertToSessionVO)
                .toList();

        return QuizSessionListVO.builder()
                .total(page.getTotalElements())
                .list(list)
                .build();
    }

    @Override
    @Transactional
    public SubmitAnswerResponse submitAnswer(UUID sessionId, Long tenantId, Long userId, SubmitAnswerRequest request) {
        log.info("提交答案: sessionId={}, questionId={}", sessionId, request.getQuestionId());

        // 获取会话
        QuizSession session = sessionRepository.findActiveById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在"));

        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        if (!session.canContinue()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "会话已结束或已暂停");
        }

        // 获取题目
        QuizQuestion question = questionRepository.findActiveById(request.getQuestionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "题目不存在"));

        // 使用 Agent 评估答案
        List<UserKnowledgeState> knowledgeStates = knowledgeStateRepository.findActiveByUserId(userId);
        
        // 构建额外参数，将题目信息传递给 Agent
        Map<String, Object> extras = new HashMap<>();
        extras.put("currentQuestion", question);
        extras.put("questionText", question.getQuestionText());
        extras.put("questionType", question.getQuestionType() != null ? question.getQuestionType().getCode() : null);
        extras.put("correctAnswer", question.getCorrectAnswer());
        Double avgResponseTime = responseRepository.findAvgResponseTimeByUserId(userId);
        extras.put("avgResponseTimeMs", avgResponseTime != null ? avgResponseTime.intValue() : 30000);

        AgentContext context = AgentContext.builder()
                .tenantId(tenantId)
                .userId(userId)
                .session(session)
                .knowledgeStates(knowledgeStates)
                .userInput(request.getAnswer())
                .responseTimeMs(request.getResponseTimeMs())
                .extras(extras)
                .build();

        AgentResponse agentResponse = quizAgent.execute(context);

        // 解析评估结果
        boolean isCorrect = false;
        int score = 0;
        String feedback = "回答已记录";
        String conceptMastery = "PARTIAL";

        if (agentResponse.getEvaluation() != null) {
            AgentResponse.AnswerEvaluation eval = agentResponse.getEvaluation();
            isCorrect = eval.isCorrect();
            score = eval.getScore();
            feedback = eval.getFeedback();
            conceptMastery = eval.getConceptMastery();
        }

        // 保存答题记录
        QuestionResponse response = new QuestionResponse();
        response.setTenantId(tenantId);
        response.setUserId(userId);
        response.setSession(session);
        response.setQuestion(question);
        response.setUserAnswer(request.getAnswer());
        response.setIsCorrect(isCorrect);
        response.setScore(score);
        response.setResponseTimeMs(request.getResponseTimeMs() != null ? request.getResponseTimeMs() : 0);
        response.setFeedback(feedback);
        response.setConceptMastery(ConceptMastery.valueOf(conceptMastery));
        if (agentResponse.getEvaluation() != null) {
            response.setHesitationDetected(agentResponse.getEvaluation().isHesitationDetected());
            response.setConfusionDetected(agentResponse.getEvaluation().isConfusionDetected());
        }

        responseRepository.save(response);

        // 更新会话分数
        if (isCorrect) {
            session.setScore(session.getScore() + 1);
        }

        // 记录未掌握知识（答错时）
        if (!isCorrect && question.getRelatedConcept() != null) {
            recordUnmasteredKnowledge(tenantId, userId, sessionId, question, response);
        }

        // 更新用户知识状态 (Phase 2b: 写 Redis，session 结束时 flush)
        if (question.getRelatedConcept() != null) {
            log.debug("更新概念知识状态: concept={}, sessionId={}", question.getRelatedConcept(), sessionId);
            cognitiveAnalyzer.updateKnowledgeState(tenantId, userId,
                    question.getRelatedConcept(), response, sessionId.toString());
        } else {
            log.debug("题目未关联概念，跳过知识状态更新: questionNo={}", question.getQuestionNo());
        }

        // 重新获取更新后的知识状态，用于判断是否结束
        List<UserKnowledgeState> updatedKnowledgeStates = getEffectiveKnowledgeStates(
                sessionId.toString(), tenantId, userId);

        // 持续评估结束条件
        boolean shouldFinishByMetrics = decisionEngine.checkShouldFinish(updatedKnowledgeStates, session);
        
        QuestionVO nextQuestion = null;
        boolean hasNext = false;
        boolean quizCompleted = false;

        if (shouldFinishByMetrics) {
            // 认知指标达标，结束测验
            log.info("认知指标达标，结束测验: sessionId={}", sessionId);
            session.setStatus(QuizStatus.COMPLETED);
            session.setCompletedAt(LocalDateTime.now());
            quizCompleted = true;
            // Phase 2b: 触发 flush
            flushKnowledgeStates(sessionId.toString(), tenantId, userId);
        } else if (session.getCurrentQuestionNo() < session.getTotalQuestions()) {
            // 还有已生成的题目未答
            session.setCurrentQuestionNo(session.getCurrentQuestionNo() + 1);
            nextQuestion = getQuestionByNo(session.getId(), session.getCurrentQuestionNo());
            hasNext = true;
        } else {
            // 当前题目用尽，尝试动态生成新题目
            log.info("题目用尽，尝试动态生成新题目: sessionId={}", sessionId);
            QuestionVO newQuestion = generateMoreQuestions(session, tenantId, userId);
            if (newQuestion != null) {
                nextQuestion = newQuestion;
                hasNext = true;
            } else {
                // 无法生成更多题目，结束测验
                log.info("无法生成更多题目，结束测验: sessionId={}", sessionId);
                session.setStatus(QuizStatus.COMPLETED);
                session.setCompletedAt(LocalDateTime.now());
                quizCompleted = true;
                // Phase 2b: 触发 flush
                flushKnowledgeStates(sessionId.toString(), tenantId, userId);
            }
        }

        sessionRepository.save(session);

        return SubmitAnswerResponse.builder()
                .isCorrect(isCorrect)
                .score(score)
                .correctAnswer(question.getCorrectAnswer())
                .explanation(question.getExplanation())
                .feedback(feedback)
                .conceptMastery(conceptMastery)
                .hasNextQuestion(hasNext)
                .nextQuestion(nextQuestion)
                .quizCompleted(quizCompleted)
                .totalScore(session.getScore())
                .currentQuestionNo(session.getCurrentQuestionNo())
                .totalQuestions(session.getTotalQuestions())
                .build();
    }

    /**
     * 动态生成更多题目
     * 当当前题目用尽且认知指标未达标时调用
     */
    private QuestionVO generateMoreQuestions(QuizSession session, Long tenantId, Long userId) {
        log.info("动态生成更多题目: sessionId={}, currentTotal={}", session.getId(), session.getTotalQuestions());

        // 获取用户知识状态
        List<UserKnowledgeState> knowledgeStates = getEffectiveKnowledgeStates(
                session.getId().toString(), tenantId, userId);

        // 构建 Agent 上下文
        AgentContext context = AgentContext.builder()
                .tenantId(tenantId)
                .userId(userId)
                .session(session)
                .knowledgeStates(knowledgeStates)
                .documentScope(session.getDocumentScope())
                .extras(buildGenerationExtras(session))
                .build();

        // 执行 Agent
        AgentResponse response = quizAgent.execute(context);

        if (!response.isSuccess() || response.getQuestions() == null || response.getQuestions().isEmpty()) {
            log.warn("动态生成题目失败: {}", response.getErrorMessage());
            return null;
        }

        // 保存新生成的题目
        int questionNo = session.getTotalQuestions();
        QuestionVO firstNewQuestion = null;

        for (AgentResponse.GeneratedQuestion generated : response.getQuestions()) {
            questionNo++;
            QuizQuestion question = new QuizQuestion();
            question.setTenantId(tenantId);
            question.setSession(session);
            question.setQuestionNo(questionNo);
            question.setQuestionText(generated.getText());

            QuestionType questionType = parseQuestionType(generated.getType());
            if (questionType == null) {
                questionType = QuestionType.SINGLE_CHOICE;
            }
            question.setQuestionType(questionType);

            question.setOptions(generated.getOptions());
            question.setCorrectAnswer(generated.getCorrectAnswer());
            question.setExplanation(generated.getExplanation());
            question.setRelatedConcept(generated.getRelatedConcept());

            Difficulty difficulty = parseDifficulty(generated.getDifficulty());
            if (difficulty == null) {
                difficulty = Difficulty.MEDIUM;
            }
            question.setDifficulty(difficulty);

            question = questionRepository.save(question);

            if (firstNewQuestion == null) {
                firstNewQuestion = convertToQuestionVO(question);
            }
        }

        // 更新会话
        session.setTotalQuestions(questionNo);
        session.setCurrentQuestionNo(session.getCurrentQuestionNo() + 1);

        log.info("动态生成了 {} 道新题目，总题数更新为 {}", response.getQuestions().size(), questionNo);

        return firstNewQuestion;
    }

    private Map<String, Object> buildGenerationExtras(QuizSession session) {
        Map<String, Object> extras = new HashMap<>();
        List<QuestionResponse> responses = responseRepository.findActiveBySessionId(session.getId());
        if (responses.isEmpty()) {
            return extras;
        }

        List<Map<String, Object>> recentResponses = new ArrayList<>();
        int start = Math.max(0, responses.size() - 3);
        for (int i = start; i < responses.size(); i++) {
            QuestionResponse response = responses.get(i);
            Map<String, Object> item = new HashMap<>();
            item.put("questionText", response.getQuestion().getQuestionText());
            item.put("userAnswer", response.getUserAnswer());
            item.put("feedback", response.getFeedback());
            item.put("isCorrect", response.getIsCorrect());
            item.put("relatedConcept", response.getQuestion().getRelatedConcept());
            recentResponses.add(item);
        }

        QuestionResponse latest = responses.get(responses.size() - 1);
        extras.put("recentResponses", recentResponses);
        extras.put("latestUserAnswer", latest.getUserAnswer());
        extras.put("latestFeedback", latest.getFeedback());
        return extras;
    }

    /**
     * Phase 2b: flush Redis 中缓冲的认知指标到 DB
     */
    private void flushKnowledgeStates(String sessionId, Long tenantId, Long userId) {
        try {
            knowledgeStateFlushService.flush(sessionId, tenantId, userId);
        } catch (Exception e) {
            log.error("flush 认知指标失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 记录未掌握知识
     * 当用户答错题目时，创建或更新知识缺口记录
     */
    private void recordUnmasteredKnowledge(Long tenantId, Long userId, UUID sessionId,
            QuizQuestion question, QuestionResponse response) {
        String conceptName = question.getRelatedConcept();
        if (conceptName == null || conceptName.isBlank()) {
            return;
        }

        try {
            // 查找是否已存在该概念的活跃知识缺口
            var existingGap = unmasteredKnowledgeRepository.findActiveByUserIdAndConcept(userId, conceptName);

            if (existingGap.isPresent()) {
                // 已存在，增加失败计数并更新信息
                UnmasteredKnowledge gap = existingGap.get();
                gap.incrementFailureCount();
                // 更新来源会话
                gap.setSourceSessionId(sessionId);
                unmasteredKnowledgeRepository.save(gap);
                log.info("更新知识缺口: concept={}, failureCount={}", conceptName, gap.getFailureCount());
            } else {
                // 不存在，创建新的知识缺口记录
                UnmasteredKnowledge newGap = new UnmasteredKnowledge();
                newGap.setTenantId(tenantId);
                newGap.setUserId(userId);
                newGap.setConceptName(conceptName);
                newGap.setSourceSessionId(sessionId);
                newGap.setStatus(KnowledgeGapStatus.ACTIVE);
                newGap.setFailureCount(1);

                // 根据题目类型推断缺口类型
                GapType gapType = inferGapType(question.getQuestionType());
                newGap.setGapType(gapType);

                // 根据分数推断严重程度
                Severity severity = inferSeverity(response.getScore());
                newGap.setSeverity(severity);

                // 生成缺口描述
                String description = generateGapDescription(conceptName, question, response);
                newGap.setGapDescription(description);

                unmasteredKnowledgeRepository.save(newGap);
                log.info("创建知识缺口: concept={}, type={}, severity={}", conceptName, gapType, severity);
            }
        } catch (Exception e) {
            log.error("记录未掌握知识失败: concept={}, error={}", conceptName, e.getMessage());
        }
    }

    /**
     * 根据题目类型推断缺口类型
     */
    private GapType inferGapType(QuestionType questionType) {
        if (questionType == null) {
            return GapType.CONCEPTUAL;
        }
        return switch (questionType) {
            case SINGLE_CHOICE, TRUE_FALSE -> GapType.CONCEPTUAL;
            case MULTIPLE_SELECT, FILL_IN_BLANK -> GapType.PROCEDURAL;
            case SHORT_ANSWER, EXPLANATION -> GapType.CONCEPTUAL;
            case CODE_COMPLETION -> GapType.PROCEDURAL;
            default -> GapType.CONCEPTUAL;
        };
    }

    /**
     * 根据得分推断严重程度
     */
    private Severity inferSeverity(int score) {
        if (score <= 20) {
            return Severity.HIGH;
        } else if (score <= 50) {
            return Severity.MEDIUM;
        } else {
            return Severity.LOW;
        }
    }

    /**
     * 生成缺口描述
     */
    private String generateGapDescription(String conceptName, QuizQuestion question, QuestionResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append("在概念「").append(conceptName).append("」上答错");

        if (question.getQuestionType() != null) {
            sb.append("，题型：").append(question.getQuestionType().getDescription());
        }

        if (response.getScore() > 0) {
            sb.append("，部分理解（得分：").append(response.getScore()).append("）");
        } else {
            sb.append("，完全不理解");
        }

        return sb.toString();
    }

    @Override
    public QuizSessionStatusVO getSessionStatus(UUID sessionId, Long userId) {
        QuizSession session = sessionRepository.findActiveById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在"));

        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        long totalAnswered = responseRepository.countActiveBySessionId(sessionId);
        long correctCount = responseRepository.countCorrectBySessionId(sessionId);
        double accuracy = totalAnswered > 0 ? (double) correctCount / totalAnswered * 100 : 0;

        QuestionVO currentQuestion = null;
        if (session.canContinue() && session.getCurrentQuestionNo() > 0) {
            currentQuestion = getQuestionByNo(sessionId, session.getCurrentQuestionNo());
        }

        return QuizSessionStatusVO.builder()
                .sessionId(session.getId())
                .status(session.getStatus())
                .currentQuestionNo(session.getCurrentQuestionNo())
                .totalQuestions(session.getTotalQuestions())
                .score(session.getScore())
                .accuracy(accuracy)
                .canContinue(session.canContinue())
                .currentQuestion(currentQuestion)
                .build();
    }

    @Override
    public QuestionVO getNextQuestion(UUID sessionId, Long userId) {
        log.info("获取下一题: sessionId={}", sessionId);
        
        QuizSession session = sessionRepository.findActiveById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在"));

        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        if (!session.canContinue()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "会话已结束或已暂停");
        }
        
        log.info("当前题号: currentQuestionNo={}, totalQuestions={}", 
                session.getCurrentQuestionNo(), session.getTotalQuestions());

        // 检查是否所有题目都已答完
        long answeredCount = responseRepository.countActiveBySessionId(sessionId);
        if (answeredCount >= session.getTotalQuestions()) {
            log.info("所有题目已答完: answered={}, total={}", answeredCount, session.getTotalQuestions());
            return null;
        }

        QuestionVO question = getQuestionByNo(sessionId, session.getCurrentQuestionNo());
        
        // 如果当前题号对应的题目不存在，可能是索引问题，尝试查找第一道未答题目
        if (question == null) {
            log.warn("当前题号对应题目不存在，尝试查找未答题目: currentNo={}", session.getCurrentQuestionNo());
            // 查找会话中第一道未答题目
            question = findFirstUnansweredQuestion(sessionId);
        }
        
        return question;
    }

    /**
     * 查找会话中第一道未答题目
     */
    private QuestionVO findFirstUnansweredQuestion(UUID sessionId) {
        List<QuizQuestion> allQuestions = questionRepository.findActiveBySessionId(sessionId);
        for (QuizQuestion q : allQuestions) {
            boolean answered = responseRepository.existsByQuestionId(q.getId());
            if (!answered) {
                return convertToQuestionVO(q);
            }
        }
        return null;
    }

    private QuestionVO getQuestionByNo(UUID sessionId, int questionNo) {
        return questionRepository.findActiveBySessionIdAndQuestionNo(sessionId, questionNo)
                .map(this::convertToQuestionVO)
                .orElse(null);
    }

    @Override
    public KnowledgeCoverageVO getKnowledgeCoverage(UUID sessionId, Long userId) {
        QuizSession session = sessionRepository.findActiveById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在"));

        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问此会话");
        }

        String sid = sessionId.toString();

        // 1. 获取概念清单（Redis → agentState → chunk 估算）
        Set<String> allConcepts;
        String conceptSource;

        Set<String> redisConcepts = Set.of();
        try {
            redisConcepts = quizRedisService.getConcepts(sid);
        } catch (Exception e) {
            log.debug("Redis 读取概念清单失败: {}", e.getMessage());
        }

        if (!redisConcepts.isEmpty()) {
            allConcepts = redisConcepts;
            conceptSource = "REDIS";
        } else if (session.getAgentState() != null
                && session.getAgentState().get("concepts") instanceof List<?> col
                && !col.isEmpty()) {
            allConcepts = col.stream().map(Object::toString).collect(java.util.stream.Collectors.toSet());
            conceptSource = "AGENT_STATE";
        } else {
            // 无概念清单，用已有 state 拼凑
            allConcepts = Set.of();
            conceptSource = "CHUNK_ESTIMATE";
        }

        // 2. 获取已有 UserKnowledgeState
        List<UserKnowledgeState> states = knowledgeStateRepository.findActiveByUserId(userId);

        // 同时检查 Redis 中缓冲的指标（还未 flush 的）
        Map<String, Map<String, String>> bufferedStates = Map.of();
        try {
            bufferedStates = quizRedisService.getAllKnowledgeStates(sid);
        } catch (Exception e) {
            log.debug("Redis 读取缓冲指标失败: {}", e.getMessage());
        }

        // 3. 合并：DB states + Redis buffered states → 已测概念集合
        Map<String, KnowledgeCoverageVO.ConceptDetail> detailMap = new java.util.LinkedHashMap<>();

        // 从 DB states 填充
        for (UserKnowledgeState s : states) {
            detailMap.put(s.getTopicName(), KnowledgeCoverageVO.ConceptDetail.builder()
                    .name(s.getTopicName())
                    .status(s.isMastered() ? "MASTERED" : "TESTING")
                    .understandingDepth(s.getUnderstandingDepth())
                    .cognitiveLoad(s.getCognitiveLoadScore())
                    .stability(s.getStabilityScore())
                    .questionCount(s.getTotalQuestions())
                    .build());
        }

        // 从 Redis buffered states 填充（覆盖 DB 值，因为更新）
        for (Map.Entry<String, Map<String, String>> entry : bufferedStates.entrySet()) {
            String concept = entry.getKey();
            Map<String, String> m = entry.getValue();
            int depth = parseIntSafe(m.get("depth"), 50);
            int load = parseIntSafe(m.get("load"), 50);
            int stability = parseIntSafe(m.get("stability"), 50);
            int total = parseIntSafe(m.get("total"), 0);
            boolean mastered = depth >= 70 && load <= 40 && stability >= 70;
            detailMap.put(concept, KnowledgeCoverageVO.ConceptDetail.builder()
                    .name(concept)
                    .status(mastered ? "MASTERED" : "TESTING")
                    .understandingDepth(depth)
                    .cognitiveLoad(load)
                    .stability(stability)
                    .questionCount(total)
                    .build());
        }

        // 4. 补充未测概念
        for (String concept : allConcepts) {
            detailMap.putIfAbsent(concept, KnowledgeCoverageVO.ConceptDetail.builder()
                    .name(concept)
                    .status("UNTESTED")
                    .understandingDepth(null)
                    .cognitiveLoad(null)
                    .stability(null)
                    .questionCount(0)
                    .build());
        }

        // 5. 计算统计
        int totalConcepts = allConcepts.isEmpty() ? detailMap.size() : allConcepts.size();
        int testedConcepts = (int) detailMap.values().stream()
                .filter(d -> !"UNTESTED".equals(d.getStatus()))
                .count();
        int masteredConcepts = (int) detailMap.values().stream()
                .filter(d -> "MASTERED".equals(d.getStatus()))
                .count();

        double coveragePercent = totalConcepts > 0
                ? Math.round((double) testedConcepts / totalConcepts * 1000) / 10.0
                : 0;
        double masteryPercent = totalConcepts > 0
                ? Math.round((double) masteredConcepts / totalConcepts * 1000) / 10.0
                : 0;

        long answeredCount = responseRepository.countActiveBySessionId(sessionId);

        return KnowledgeCoverageVO.builder()
                .sessionId(sessionId)
                .totalConcepts(totalConcepts)
                .testedConcepts(testedConcepts)
                .masteredConcepts(masteredConcepts)
                .coveragePercent(coveragePercent)
                .masteryPercent(masteryPercent)
                .answeredQuestions((int) answeredCount)
                .conceptSource(conceptSource)
                .concepts(new ArrayList<>(detailMap.values()))
                .build();
    }

    private int parseIntSafe(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 合并 DB 中已落库状态与 Redis 中当前 session 的缓冲状态
     * Redis 中的值优先，保证会话内决策能看到最新答题结果。
     */
    private List<UserKnowledgeState> getEffectiveKnowledgeStates(String sessionId, Long tenantId, Long userId) {
        Map<String, UserKnowledgeState> mergedStates = new java.util.LinkedHashMap<>();

        for (UserKnowledgeState state : knowledgeStateRepository.findActiveByUserId(userId)) {
            mergedStates.put(state.getTopicName(), state);
        }

        try {
            Map<String, Map<String, String>> bufferedStates = quizRedisService.getAllKnowledgeStates(sessionId);
            for (Map.Entry<String, Map<String, String>> entry : bufferedStates.entrySet()) {
                String concept = entry.getKey();
                Map<String, String> metrics = entry.getValue();

                UserKnowledgeState baseState = mergedStates.get(concept);
                if (baseState == null) {
                    baseState = new UserKnowledgeState();
                    baseState.setTenantId(tenantId);
                    baseState.setUserId(userId);
                    baseState.setTopicType(TopicType.CONCEPT);
                    baseState.setTopicId(concept);
                    baseState.setTopicName(concept);
                }

                baseState.setUnderstandingDepth(parseIntSafe(metrics.get("depth"), baseState.getUnderstandingDepth()));
                baseState.setCognitiveLoadScore(parseIntSafe(metrics.get("load"), baseState.getCognitiveLoadScore()));
                baseState.setStabilityScore(parseIntSafe(metrics.get("stability"), baseState.getStabilityScore()));
                baseState.setTotalQuestions(parseIntSafe(metrics.get("total"), baseState.getTotalQuestions()));
                baseState.setCorrectAnswers(parseIntSafe(metrics.get("correct"), baseState.getCorrectAnswers()));
                mergedStates.put(concept, baseState);
            }
        } catch (Exception e) {
            log.debug("读取 Redis 缓冲知识状态失败，降级仅使用 DB: sessionId={}, error={}", sessionId, e.getMessage());
        }

        return new ArrayList<>(mergedStates.values());
    }

    private QuizSessionVO convertToSessionVO(QuizSession session) {
        return QuizSessionVO.builder()
                .sessionId(session.getId())
                .quizMode(session.getQuizMode())
                .status(session.getStatus())
                .currentQuestionNo(session.getCurrentQuestionNo())
                .totalQuestions(session.getTotalQuestions())
                .score(session.getScore())
                .startedAt(session.getStartedAt())
                .completedAt(session.getCompletedAt())
                .createTime(session.getCreateTime())
                .build();
    }

    private QuestionVO convertToQuestionVO(QuizQuestion question) {
        return QuestionVO.builder()
                .id(question.getId())
                .questionNo(question.getQuestionNo())
                .questionType(question.getQuestionType())
                .questionText(question.getQuestionText())
                .options(question.getOptions())
                .difficulty(question.getDifficulty())
                .relatedConcept(question.getRelatedConcept())
                .answered(false) // 简化处理
                .build();
    }

    private ResponseVO convertToResponseVO(QuestionResponse response) {
        return ResponseVO.builder()
                .id(response.getId())
                .questionId(response.getQuestion().getId())
                .userAnswer(response.getUserAnswer())
                .isCorrect(response.getIsCorrect())
                .score(response.getScore())
                .responseTimeMs(response.getResponseTimeMs())
                .conceptMastery(response.getConceptMastery())
                .feedback(response.getFeedback())
                .createTime(response.getCreateTime())
                .build();
    }
}
