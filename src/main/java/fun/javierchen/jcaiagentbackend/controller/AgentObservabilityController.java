package fun.javierchen.jcaiagentbackend.controller;

import fun.javierchen.jcaiagentbackend.common.BaseResponse;
import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.common.ResultUtils;
import fun.javierchen.jcaiagentbackend.common.TenantContextHolder;
import fun.javierchen.jcaiagentbackend.controller.dto.agent.ExecutionLogVO;
import fun.javierchen.jcaiagentbackend.controller.dto.agent.ExecutionOverviewVO;
import fun.javierchen.jcaiagentbackend.controller.dto.agent.ExecutionTimelineVO;
import fun.javierchen.jcaiagentbackend.controller.dto.agent.ToolStatsVO;
import fun.javierchen.jcaiagentbackend.exception.ThrowUtils;
import fun.javierchen.jcaiagentbackend.model.entity.User;
import fun.javierchen.jcaiagentbackend.model.entity.enums.AgentPhase;
import fun.javierchen.jcaiagentbackend.model.entity.quiz.AgentExecutionLog;
import fun.javierchen.jcaiagentbackend.rag.observability.RagRetrievalTrace;
import fun.javierchen.jcaiagentbackend.rag.observability.RagTraceStore;
import fun.javierchen.jcaiagentbackend.repository.AgentExecutionLogRepository;
import fun.javierchen.jcaiagentbackend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 可观测性接口
 * 用于 debug 和后期管理员监控
 *
 * @author JavierChen
 */
@RestController
@RequestMapping("/v1/agent/observability")
@RequiredArgsConstructor
@Tag(name = "Agent 可观测性", description = "Agent 执行日志查询与监控接口")
public class AgentObservabilityController {

    private final AgentExecutionLogRepository logRepository;
    private final UserService userService;
    private final RagTraceStore ragTraceStore;

    /**
     * 获取会话执行时间线
     * 返回按 iteration 分组的 Thought→Action→Observation 完整链路
     */
    @GetMapping("/session/{sessionId}/timeline")
    @Operation(summary = "获取执行时间线", description = "返回会话的完整 ReAct 执行时间线，按迭代次数分组")
    public BaseResponse<ExecutionTimelineVO> getExecutionTimeline(
            @Parameter(description = "会话ID", required = true)
            @PathVariable UUID sessionId,
            HttpServletRequest httpRequest) {

        userService.getLoginUser(httpRequest);
        requireTenantId();

        List<AgentExecutionLog> logs = logRepository.findActiveBySessionId(sessionId);

        Map<Integer, List<AgentExecutionLog>> groupedByIteration = logs.stream()
                .collect(Collectors.groupingBy(AgentExecutionLog::getIteration, TreeMap::new, Collectors.toList()));

        List<ExecutionTimelineVO.IterationRecord> iterations = groupedByIteration.entrySet().stream()
                .map(entry -> buildIterationRecord(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        ExecutionTimelineVO vo = ExecutionTimelineVO.builder()
                .sessionId(sessionId)
                .iterations(iterations)
                .build();

        return ResultUtils.success(vo);
    }

    /**
     * 获取会话执行概览
     * 返回总迭代数、总耗时、各阶段数量、超时日志数
     */
    @GetMapping("/session/{sessionId}/overview")
    @Operation(summary = "获取执行概览", description = "返回会话的执行概览，包含迭代次数、耗时统计、阶段分布")
    public BaseResponse<ExecutionOverviewVO> getExecutionOverview(
            @Parameter(description = "会话ID", required = true)
            @PathVariable UUID sessionId,
            HttpServletRequest httpRequest) {

        userService.getLoginUser(httpRequest);
        requireTenantId();

        Integer totalIterations = logRepository.findMaxIterationBySessionId(sessionId);
        Long totalLogCount = logRepository.countActiveBySessionId(sessionId);
        Long totalExecutionTimeMs = logRepository.findTotalExecutionTimeBySessionId(sessionId);
        Double avgExecutionTimeMs = logRepository.findAvgExecutionTimeBySessionId(sessionId);

        List<Object[]> phaseCountResults = logRepository.countByPhaseForSession(sessionId);
        List<ExecutionOverviewVO.PhaseCount> phaseCounts = phaseCountResults.stream()
                .map(row -> ExecutionOverviewVO.PhaseCount.builder()
                        .phase((String) row[0])
                        .count(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());

        List<AgentExecutionLog> timeoutLogs = logRepository.findTimeoutLogsBySessionId(sessionId);
        List<ExecutionOverviewVO.TimeoutLogInfo> timeoutLogInfos = timeoutLogs.stream()
                .map(log -> ExecutionOverviewVO.TimeoutLogInfo.builder()
                        .id(log.getId())
                        .iteration(log.getIteration())
                        .phase(log.getPhase().name())
                        .toolName(log.getToolName())
                        .executionTimeMs(log.getExecutionTimeMs())
                        .build())
                .collect(Collectors.toList());

        ExecutionOverviewVO vo = ExecutionOverviewVO.builder()
                .sessionId(sessionId)
                .totalIterations(totalIterations)
                .totalLogCount(totalLogCount)
                .totalExecutionTimeMs(totalExecutionTimeMs)
                .avgExecutionTimeMs(avgExecutionTimeMs)
                .phaseCounts(phaseCounts)
                .timeoutLogCount((long) timeoutLogs.size())
                .timeoutLogs(timeoutLogInfos)
                .build();

        return ResultUtils.success(vo);
    }

    /**
     * 获取工具调用统计
     * 返回每个 Tool 的调用次数和平均耗时
     */
    @GetMapping("/session/{sessionId}/tools")
    @Operation(summary = "获取工具调用统计", description = "返回会话中各工具的调用次数和耗时统计")
    public BaseResponse<ToolStatsVO> getToolStats(
            @Parameter(description = "会话ID", required = true)
            @PathVariable UUID sessionId,
            HttpServletRequest httpRequest) {

        userService.getLoginUser(httpRequest);
        requireTenantId();

        List<Object[]> toolCountResults = logRepository.countByToolNameForSession(sessionId);
        List<ToolStatsVO.ToolStatItem> toolStats = new ArrayList<>();

        for (Object[] row : toolCountResults) {
            String toolName = (String) row[0];
            Long callCount = ((Number) row[1]).longValue();

            List<AgentExecutionLog> toolLogs = logRepository.findActiveBySessionIdAndToolName(sessionId, toolName);

            IntSummaryStatistics stats = toolLogs.stream()
                    .filter(log -> log.getExecutionTimeMs() != null)
                    .mapToInt(AgentExecutionLog::getExecutionTimeMs)
                    .summaryStatistics();

            ToolStatsVO.ToolStatItem item = ToolStatsVO.ToolStatItem.builder()
                    .toolName(toolName)
                    .callCount(callCount)
                    .avgExecutionTimeMs(stats.getCount() > 0 ? (double) stats.getAverage() : null)
                    .totalExecutionTimeMs(stats.getCount() > 0 ? stats.getSum() : null)
                    .maxExecutionTimeMs(stats.getCount() > 0 ? stats.getMax() : null)
                    .minExecutionTimeMs(stats.getCount() > 0 ? stats.getMin() : null)
                    .build();

            toolStats.add(item);
        }

        ToolStatsVO vo = ToolStatsVO.builder()
                .tools(toolStats)
                .build();

        return ResultUtils.success(vo);
    }

    /**
     * 分页查询租户执行日志
     */
    @GetMapping("/tenant/logs")
    @Operation(summary = "查询租户执行日志", description = "分页查询当前租户下所有执行日志")
    public BaseResponse<Page<ExecutionLogVO>> getTenantLogs(
            @Parameter(description = "页码，从0开始", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {

        userService.getLoginUser(httpRequest);
        Long tenantId = requireTenantId();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<AgentExecutionLog> logs = logRepository.findByTenantIdAndIsDelete(tenantId, 0, pageable);

        Page<ExecutionLogVO> voPage = logs.map(ExecutionLogVO::fromEntity);
        return ResultUtils.success(voPage);
    }

    private Long requireTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        ThrowUtils.throwIf(tenantId == null, ErrorCode.NO_AUTH_ERROR, "Tenant not selected");
        return tenantId;
    }

    private ExecutionTimelineVO.IterationRecord buildIterationRecord(Integer iteration, List<AgentExecutionLog> logs) {
        Map<AgentPhase, AgentExecutionLog> phaseMap = logs.stream()
                .collect(Collectors.toMap(AgentExecutionLog::getPhase, log -> log, (a, b) -> a));

        return ExecutionTimelineVO.IterationRecord.builder()
                .iteration(iteration)
                .thought(buildPhaseLog(phaseMap.get(AgentPhase.THOUGHT)))
                .action(buildPhaseLog(phaseMap.get(AgentPhase.ACTION)))
                .observation(buildPhaseLog(phaseMap.get(AgentPhase.OBSERVATION)))
                .build();
    }

    private ExecutionTimelineVO.PhaseLog buildPhaseLog(AgentExecutionLog log) {
        if (log == null) {
            return null;
        }
        return ExecutionTimelineVO.PhaseLog.builder()
                .id(log.getId())
                .phase(log.getPhase().name())
                .toolName(log.getToolName())
                .inputData(log.getInputData())
                .outputData(log.getOutputData())
                .executionTimeMs(log.getExecutionTimeMs())
                .timeout(log.isTimeout())
                .timestamp(log.getTimestamp())
                .build();
    }

    @GetMapping("/rag/latest")
    @Operation(summary = "查询最近 RAG 检索轨迹", description = "开发观测接口：查看 Vector/ES/RRF 的命中与耗时")
    public BaseResponse<List<RagRetrievalTrace>> getLatestRagTraces(
            @Parameter(description = "返回数量，默认20，最大200", example = "20")
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest httpRequest) {
        userService.getLoginUser(httpRequest);
        Long tenantId = requireTenantId();
        List<RagRetrievalTrace> traces = ragTraceStore.latest(Math.min(limit, 200), tenantId);
        return ResultUtils.success(traces);
    }

    @GetMapping("/rag/{traceId}")
    @Operation(summary = "查询单条 RAG 检索轨迹", description = "开发观测接口：按 traceId 查看完整检索细节")
    public BaseResponse<RagRetrievalTrace> getRagTraceById(
            @Parameter(description = "RAG 轨迹ID", required = true)
            @PathVariable String traceId,
            HttpServletRequest httpRequest) {
        userService.getLoginUser(httpRequest);
        Long tenantId = requireTenantId();
        RagRetrievalTrace trace = ragTraceStore.get(traceId);
        ThrowUtils.throwIf(trace == null, ErrorCode.NOT_FOUND_ERROR, "RAG 轨迹不存在");
        ThrowUtils.throwIf(trace.getTenantId() != null && !tenantId.equals(trace.getTenantId()),
                ErrorCode.NO_AUTH_ERROR, "无权查看该 RAG 轨迹");
        return ResultUtils.success(trace);
    }
}
