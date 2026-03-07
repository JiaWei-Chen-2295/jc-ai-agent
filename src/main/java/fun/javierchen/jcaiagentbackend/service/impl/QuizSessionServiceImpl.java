package fun.javierchen.jcaiagentbackend.service.impl;

import fun.javierchen.jcaiagentbackend.agent.quiz.QuizReActAgent;
import fun.javierchen.jcaiagentbackend.agent.quiz.analyzer.CognitiveAnalyzer;
import fun.javierchen.jcaiagentbackend.agent.quiz.core.AgentContext;
import fun.javierchen.jcaiagentbackend.agent.quiz.core.AgentResponse;
import fun.javierchen.jcaiagentbackend.agent.quiz.decision.DecisionEngine;
import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.controller.dto.quiz.*;
import fun.javierchen.jcaiagentbackend.exception.BusinessException;
import fun.javierchen.jcaiagentbackend.model.entity.enums.ConceptMastery;
import fun.javierchen.jcaiagentbackend.model.entity.enums.Difficulty;
import fun.javierchen.jcaiagentbackend.model.entity.enums.QuestionType;
import fun.javierchen.jcaiagentbackend.model.entity.enums.QuizStatus;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.*;
import fun.javierchen.jcaiagentbackend.repository.*;
import fun.javierchen.jcaiagentbackend.service.QuizSessionService;
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
    private final QuizReActAgent quizAgent;
    private final CognitiveAnalyzer cognitiveAnalyzer;
    private final DecisionEngine decisionEngine;

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
                session.setStatus(QuizStatus.PAUSED);
            }
            case RESUME -> {
                if (session.getStatus() != QuizStatus.PAUSED) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "只有暂停的会话可以恢复");
                }
                session.setStatus(QuizStatus.IN_PROGRESS);
            }
            case ABANDON -> {
                if (session.isFinished()) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "会话已结束");
                }
                session.setStatus(QuizStatus.ABANDONED);
                session.setCompletedAt(LocalDateTime.now());
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

        // 更新用户知识状态
        if (question.getRelatedConcept() != null) {
            cognitiveAnalyzer.updateKnowledgeState(tenantId, userId, question.getRelatedConcept(), response);
        }

        // 重新获取更新后的知识状态，用于判断是否结束
        List<UserKnowledgeState> updatedKnowledgeStates = knowledgeStateRepository.findActiveByUserId(userId);

        // 持续评估结束条件
        boolean shouldFinishByMetrics = decisionEngine.checkShouldFinish(updatedKnowledgeStates);
        
        QuestionVO nextQuestion = null;
        boolean hasNext = false;
        boolean quizCompleted = false;

        if (shouldFinishByMetrics) {
            // 认知指标达标，结束测验
            log.info("认知指标达标，结束测验: sessionId={}", sessionId);
            session.setStatus(QuizStatus.COMPLETED);
            session.setCompletedAt(LocalDateTime.now());
            quizCompleted = true;
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
