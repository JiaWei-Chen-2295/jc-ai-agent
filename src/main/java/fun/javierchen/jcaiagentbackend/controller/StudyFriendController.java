package fun.javierchen.jcaiagentbackend.controller;


import fun.javierchen.jcaiagentbackend.app.StudyFriend;
import fun.javierchen.jcaiagentbackend.agent.service.StudyFriendAgentEventStreamService;
import fun.javierchen.jcaiagentbackend.common.BaseResponse;
import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.common.ResultUtils;
import fun.javierchen.jcaiagentbackend.common.TenantContextHolder;
import fun.javierchen.jcaiagentbackend.controller.dto.ChatMessageListResponse;
import fun.javierchen.jcaiagentbackend.controller.dto.ChatSessionListResponse;
import fun.javierchen.jcaiagentbackend.controller.dto.ChatSessionVO;
import fun.javierchen.jcaiagentbackend.exception.ThrowUtils;
import fun.javierchen.jcaiagentbackend.model.entity.User;
import fun.javierchen.jcaiagentbackend.service.StudyFriendChatService;
import fun.javierchen.jcaiagentbackend.service.TenantService;
import fun.javierchen.jcaiagentbackend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping(value = "/ai_friend")
@Tag(name = "学习伙伴聊天", description = "基于向量检索的 AI 学习助手对话接口（支持轮询与 SSE）")
public class StudyFriendController {

    @Resource
    private StudyFriend studyFriend;

    @Resource
    private UserService userService;

    @Resource
    private StudyFriendChatService studyFriendChatService;

    @Resource
    private TenantService tenantService;

    @Resource
    private StudyFriendAgentEventStreamService studyFriendAgentEventStreamService;

    @PostMapping(value = "/session", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create chat session", description = "Create a StudyFriend chat session and return chatId.")
    public BaseResponse<ChatSessionVO> createSession(
            @RequestParam(value = "title", required = false) String title,
            jakarta.servlet.http.HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long tenantId = requireTenantId();
        if (!userService.isAdmin(loginUser)) {
            tenantService.requireMember(tenantId, loginUser.getId());
        }
        ChatSessionVO session = studyFriendChatService.createSession(tenantId, loginUser.getId(), title);
        return ResultUtils.success(session);
    }

    @GetMapping(value = "/session/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List chat sessions (cursor)", description = "Cursor pagination for current user's sessions.")
    public BaseResponse<ChatSessionListResponse> listSessions(
            @RequestParam(value = "beforeLastMessageAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beforeLastMessageAt,
            @RequestParam(value = "beforeChatId", required = false) String beforeChatId,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            jakarta.servlet.http.HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long tenantId = requireTenantId();
        if (!userService.isAdmin(loginUser)) {
            tenantService.requireMember(tenantId, loginUser.getId());
        }
        ChatSessionListResponse response = studyFriendChatService.listSessionsForUser(
                tenantId, loginUser.getId(), beforeLastMessageAt, beforeChatId, limit);
        return ResultUtils.success(response);
    }

    @GetMapping(value = "/session/{chatId}/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List chat messages (cursor)", description = "Cursor pagination for chat messages.")
    public BaseResponse<ChatMessageListResponse> listMessages(
            @PathVariable("chatId") String chatId,
            @RequestParam(value = "beforeId", required = false) Long beforeId,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            jakarta.servlet.http.HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long tenantId = requireTenantId();
        if (!userService.isAdmin(loginUser)) {
            tenantService.requireMember(tenantId, loginUser.getId());
        }
        studyFriendChatService.requireSessionForUser(chatId, tenantId, loginUser.getId());
        ChatMessageListResponse response = studyFriendChatService.listMessagesForUser(
                chatId, tenantId, loginUser.getId(), beforeId, limit);
        return ResultUtils.success(response);
    }

    @GetMapping(value = "/admin/session/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Admin list chat sessions (cursor)", description = "Admin can list sessions across tenants.")
    public BaseResponse<ChatSessionListResponse> listSessionsByAdmin(
            @RequestParam(value = "tenantId", required = false) Long tenantId,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "beforeLastMessageAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beforeLastMessageAt,
            @RequestParam(value = "beforeChatId", required = false) String beforeChatId,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            jakarta.servlet.http.HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "Admin required");
        ChatSessionListResponse response = studyFriendChatService.listSessionsForAdmin(
                tenantId, userId, beforeLastMessageAt, beforeChatId, limit);
        return ResultUtils.success(response);
    }

    @GetMapping(value = "/admin/session/{chatId}/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Admin list chat messages (cursor)", description = "Admin can read messages across tenants.")
    public BaseResponse<ChatMessageListResponse> listMessagesByAdmin(
            @PathVariable("chatId") String chatId,
            @RequestParam(value = "beforeId", required = false) Long beforeId,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            jakarta.servlet.http.HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "Admin required");
        studyFriendChatService.requireSessionForAdmin(chatId);
        ChatMessageListResponse response = studyFriendChatService.listMessagesForAdmin(chatId, beforeId, limit);
        return ResultUtils.success(response);
    }

    @GetMapping(value = "/do_chat/async", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(
            summary = "异步聊天（轮询获取完整回答）",
            description = "使用 chatId 维持上下文，会在服务端完成检索后返回一次性完整回答。",
            parameters = {
                    @Parameter(name = "chatMessage", description = "用户提问或对话内容", required = true, example = "什么是向量数据库？"),
                    @Parameter(name = "chatId", description = "会话唯一标识，复用以保持上下文", required = true, example = "session-001")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "AI 的完整回答", content = @Content(
                            mediaType = MediaType.TEXT_PLAIN_VALUE,
                            examples = @ExampleObject(value = "向量数据库用于存储和检索高维向量，常用于相似度搜索。")
                    ))
            }
    )
    public String doChatWithRAG(@RequestParam("chatMessage") String chatMessage,
                                @RequestParam("chatId") String chatId,
                                @RequestParam(value = "messageId", required = false) String messageId,
                                jakarta.servlet.http.HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long tenantId = requireTenantId();
        if (!userService.isAdmin(loginUser)) {
            tenantService.requireMember(tenantId, loginUser.getId());
        }
        studyFriendChatService.requireSessionForUser(chatId, tenantId, loginUser.getId());
        studyFriendChatService.appendUserMessage(chatId, tenantId, loginUser.getId(), chatMessage, messageId);
        String content = studyFriend.doChatWithRAG(chatMessage, chatId, tenantId);
        studyFriendChatService.appendAssistantMessage(chatId, tenantId, loginUser.getId(), content);
        return content;
    }

    @GetMapping(value = "/do_chat/sse/emitter", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "SSE 流式聊天",
            description = "以 text/event-stream 返回增量输出，前端可直接用 EventSource 订阅。每条 data 为模型的部分回答。",
            parameters = {
                    @Parameter(name = "chatMessage", description = "用户提问或对话内容", required = true, example = "请帮我总结这段话"),
                    @Parameter(name = "chatId", description = "会话唯一标识，复用以保持上下文", required = true, example = "session-001")
            }
    )
    @ApiResponse(responseCode = "200", description = "SSE 数据流（data 字段为字符串增量）", content = @Content(
            mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
            examples = @ExampleObject(value = "data: 正在为你整理要点\\n\\n")
    ))
    public SseEmitter doChatWithRAGStream(@RequestParam("chatMessage") String chatMessage,
                                          @RequestParam("chatId") String chatId,
                                          @RequestParam(value = "messageId", required = false) String messageId,
                                          jakarta.servlet.http.HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long tenantId = requireTenantId();
        if (!userService.isAdmin(loginUser)) {
            tenantService.requireMember(tenantId, loginUser.getId());
        }
        studyFriendChatService.requireSessionForUser(chatId, tenantId, loginUser.getId());
        studyFriendChatService.appendUserMessage(chatId, tenantId, loginUser.getId(), chatMessage, messageId);
        // SSE 流默认超时 3 分钟
        SseEmitter sseEmitter = new SseEmitter(3 * 60 * 1000L);
        // 缓存增量输出，流结束后一次性落库
        StringBuilder assistantBuffer = new StringBuilder();
        // 增量内容直接透传给前端，完成后补全持久化
        studyFriend.doChatWithRAGStream(chatMessage, chatId, tenantId).subscribe(
                chunk -> {
                    try {
                        assistantBuffer.append(chunk);
                        sseEmitter.send(chunk);
                    } catch (Exception e) {
                        sseEmitter.completeWithError(e);
                    }
                },
                t -> {
                    log.error("出错{}", t.getMessage());
                    sseEmitter.complete();
                },
                () -> {
                    studyFriendChatService.appendAssistantMessage(chatId, tenantId, loginUser.getId(),
                            assistantBuffer.toString());
                    sseEmitter.complete();
                }
        );
        return sseEmitter;
    }


    @GetMapping(value = "/do_chat/sse_with_tool/emitter", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "SSE 流式聊天（可触发工具调用）",
            description = "与上一个接口一致，但内部可调用工具以获取外部数据，返回的流中同样包含文本增量。",
            parameters = {
                    @Parameter(name = "chatMessage", description = "用户提问或对话内容", required = true, example = "帮我查询最新的课程表"),
                    @Parameter(name = "chatId", description = "会话唯一标识，复用以保持上下文", required = true, example = "session-001")
            }
    )
    @ApiResponse(responseCode = "200", description = "SSE 数据流（data 字段为字符串增量）", content = @Content(
            mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
            examples = @ExampleObject(value = "data: 我去调用工具查询...\\n\\n")
    ))
    public SseEmitter doChatWithRAGStreamTool(@RequestParam("chatMessage") String chatMessage,
                                              @RequestParam("chatId") String chatId,
                                              @RequestParam(value = "messageId", required = false) String messageId,
                                              jakarta.servlet.http.HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long tenantId = requireTenantId();
        if (!userService.isAdmin(loginUser)) {
            tenantService.requireMember(tenantId, loginUser.getId());
        }
        studyFriendChatService.requireSessionForUser(chatId, tenantId, loginUser.getId());
        studyFriendChatService.appendUserMessage(chatId, tenantId, loginUser.getId(), chatMessage, messageId);
        // SSE 流默认超时 3 分钟
        SseEmitter sseEmitter = new SseEmitter(3 * 60 * 1000L);
        // 缓存增量输出，流结束后一次性落库
        StringBuilder assistantBuffer = new StringBuilder();
        // 增量内容直接透传给前端，完成后补全持久化
        studyFriend.doChatWithRAGStreamTool(chatMessage, chatId, tenantId).subscribe(
                chunk -> {
                    try {
                        assistantBuffer.append(chunk);
                        sseEmitter.send(chunk);
                    } catch (Exception e) {
                        sseEmitter.completeWithError(e);
                    }
                },
                t -> {
                    log.error("出错{}", t.getMessage());
                    sseEmitter.complete();
                },
                () -> {
                    studyFriendChatService.appendAssistantMessage(chatId, tenantId, loginUser.getId(),
                            assistantBuffer.toString());
                    sseEmitter.complete();
                }
        );
        return sseEmitter;
    }

    // AgentEvent -> DisplayEvent 的 SSE 接口
    @GetMapping(value = "/do_chat/sse/agent/emitter", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "SSE 流式聊天（AgentEvent）",
            description = "以 text/event-stream 返回 DisplayEvent JSON，前端按 display 事件渲染。",
            parameters = {
                    @Parameter(name = "chatMessage", description = "用户提问或对话内容", required = true, example = "请帮我总结这段话"),
                    @Parameter(name = "chatId", description = "会话唯一标识，复用以保持上下文", required = true, example = "session-001")
            }
    )
    @ApiResponse(responseCode = "200", description = "SSE 数据流（data 字段为 DisplayEvent JSON）", content = @Content(
            mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
            examples = @ExampleObject(value = "data: {\"type\":\"display\",\"stage\":\"output\",\"format\":\"text\",\"content\":\"...\",\"delta\":true}\\n\\n")
    ))
    public SseEmitter doChatWithAgentEventStream(@RequestParam("chatMessage") String chatMessage,
                                                 @RequestParam("chatId") String chatId,
                                                 @RequestParam(value = "messageId", required = false) String messageId,
                                                 jakarta.servlet.http.HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long tenantId = requireTenantId();
        if (!userService.isAdmin(loginUser)) {
            tenantService.requireMember(tenantId, loginUser.getId());
        }
        studyFriendChatService.requireSessionForUser(chatId, tenantId, loginUser.getId());
        studyFriendChatService.appendUserMessage(chatId, tenantId, loginUser.getId(), chatMessage, messageId);
        return studyFriendAgentEventStreamService.stream(tenantId, loginUser.getId(), chatId, chatMessage, false);
    }

    @GetMapping(value = "/do_chat/sse_with_tool/agent/emitter", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "SSE 流式聊天（AgentEvent，可触发工具调用）",
            description = "与上一个接口一致，但内部可调用工具。",
            parameters = {
                    @Parameter(name = "chatMessage", description = "用户提问或对话内容", required = true, example = "帮我查询最新的课程表"),
                    @Parameter(name = "chatId", description = "会话唯一标识，复用以保持上下文", required = true, example = "session-001")
            }
    )
    @ApiResponse(responseCode = "200", description = "SSE 数据流（data 字段为 DisplayEvent JSON）", content = @Content(
            mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
            examples = @ExampleObject(value = "data: {\"type\":\"display\",\"stage\":\"output\",\"format\":\"text\",\"content\":\"...\",\"delta\":true}\\n\\n")
    ))
    public SseEmitter doChatWithAgentEventStreamTool(@RequestParam("chatMessage") String chatMessage,
                                                     @RequestParam("chatId") String chatId,
                                                     @RequestParam(value = "messageId", required = false) String messageId,
                                                     jakarta.servlet.http.HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long tenantId = requireTenantId();
        if (!userService.isAdmin(loginUser)) {
            tenantService.requireMember(tenantId, loginUser.getId());
        }
        studyFriendChatService.requireSessionForUser(chatId, tenantId, loginUser.getId());
        studyFriendChatService.appendUserMessage(chatId, tenantId, loginUser.getId(), chatMessage, messageId);
        return studyFriendAgentEventStreamService.stream(tenantId, loginUser.getId(), chatId, chatMessage, true);
    }

    private Long requireTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        ThrowUtils.throwIf(tenantId == null, ErrorCode.NO_AUTH_ERROR, "Tenant not selected");
        return tenantId;
    }
}
