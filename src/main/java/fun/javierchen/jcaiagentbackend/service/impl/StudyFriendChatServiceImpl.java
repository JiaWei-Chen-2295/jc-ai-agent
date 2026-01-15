package fun.javierchen.jcaiagentbackend.service.impl;

import fun.javierchen.jcaiagentbackend.chat.model.entity.ChatMessage;
import fun.javierchen.jcaiagentbackend.chat.model.entity.ChatSession;
import fun.javierchen.jcaiagentbackend.chat.model.enums.ChatRole;
import fun.javierchen.jcaiagentbackend.common.ErrorCode;
import fun.javierchen.jcaiagentbackend.constant.ChatConstant;
import fun.javierchen.jcaiagentbackend.controller.dto.ChatMessageListResponse;
import fun.javierchen.jcaiagentbackend.controller.dto.ChatMessageVO;
import fun.javierchen.jcaiagentbackend.controller.dto.ChatSessionListResponse;
import fun.javierchen.jcaiagentbackend.controller.dto.ChatSessionVO;
import fun.javierchen.jcaiagentbackend.exception.BusinessException;
import fun.javierchen.jcaiagentbackend.exception.ThrowUtils;
import fun.javierchen.jcaiagentbackend.repository.ChatMessageRepository;
import fun.javierchen.jcaiagentbackend.repository.ChatSessionRepository;
import fun.javierchen.jcaiagentbackend.service.StudyFriendChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudyFriendChatServiceImpl implements StudyFriendChatService {

    private static final String DEFAULT_TITLE = "New Chat";
    private static final int DUPLICATE_WINDOW_SECONDS = 5;

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Override
    public ChatSessionVO createSession(Long tenantId, Long userId, String title) {
        ThrowUtils.throwIf(tenantId == null || userId == null, ErrorCode.PARAMS_ERROR, "tenantId and userId are required");
        String finalTitle = StringUtils.hasText(title) ? title.trim() : DEFAULT_TITLE;
        if (finalTitle.length() > 255) {
            finalTitle = finalTitle.substring(0, 255);
        }
        LocalDateTime now = LocalDateTime.now();
        ChatSession session = new ChatSession();
        session.setChatId(UUID.randomUUID().toString());
        session.setTenantId(tenantId);
        session.setUserId(userId);
        session.setAppCode(ChatConstant.APP_CODE_STUDY_FRIEND);
        session.setTitle(finalTitle);
        session.setLastMessageAt(now);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setIsDeleted(0);
        chatSessionRepository.insert(session);
        return toSessionVO(session);
    }

    @Override
    public ChatSession requireSessionForUser(String chatId, Long tenantId, Long userId) {
        if (!StringUtils.hasText(chatId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "chatId is required");
        }
        return chatSessionRepository.findActiveByChatIdAndUser(chatId, tenantId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "Chat session not found"));
    }

    @Override
    public ChatSession requireSessionForAdmin(String chatId) {
        if (!StringUtils.hasText(chatId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "chatId is required");
        }
        return chatSessionRepository.findActiveByChatId(chatId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "Chat session not found"));
    }



    @Override
    public void appendUserMessage(String chatId, Long tenantId, Long userId, String content, String clientMessageId) {
        appendMessage(chatId, tenantId, userId, ChatRole.USER.getCode(), content, clientMessageId);
    }

    @Override
    public void appendAssistantMessage(String chatId, Long tenantId, Long userId, String content) {
        appendMessage(chatId, tenantId, userId, ChatRole.ASSISTANT.getCode(), content, null);
    }

    @Override
    public ChatSessionListResponse listSessionsForUser(Long tenantId, Long userId,
                                                       LocalDateTime beforeLastMessageAt, String beforeChatId, int limit) {
        int pageSize = normalizeLimit(limit);
        List<ChatSession> sessions = chatSessionRepository.listByUser(
                tenantId, userId, ChatConstant.APP_CODE_STUDY_FRIEND,
                beforeLastMessageAt, beforeChatId, pageSize + 1);
        return buildSessionResponse(sessions, pageSize);
    }

    @Override
    public ChatSessionListResponse listSessionsForAdmin(Long tenantId, Long userId,
                                                        LocalDateTime beforeLastMessageAt, String beforeChatId, int limit) {
        int pageSize = normalizeLimit(limit);
        List<ChatSession> sessions = chatSessionRepository.listByAdmin(
                tenantId, userId, ChatConstant.APP_CODE_STUDY_FRIEND,
                beforeLastMessageAt, beforeChatId, pageSize + 1);
        return buildSessionResponse(sessions, pageSize);
    }

    @Override
    public ChatMessageListResponse listMessagesForUser(String chatId, Long tenantId, Long userId,
                                                       Long beforeId, int limit) {
        int pageSize = normalizeLimit(limit);
        List<ChatMessage> messages = chatMessageRepository.listByChatIdForUser(chatId, tenantId, userId,
                beforeId, pageSize + 1);
        return buildMessageResponse(messages, pageSize);
    }

    @Override
    public ChatMessageListResponse listMessagesForAdmin(String chatId, Long beforeId, int limit) {
        int pageSize = normalizeLimit(limit);
        List<ChatMessage> messages = chatMessageRepository.listByChatIdForAdmin(chatId, beforeId, pageSize + 1);
        return buildMessageResponse(messages, pageSize);
    }

    private void appendMessage(String chatId, Long tenantId, Long userId, String role,
                               String content, String clientMessageId) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        if (StringUtils.hasText(clientMessageId)
                && chatMessageRepository.existsByClientMessageId(chatId, userId, role, clientMessageId)) {
            return;
        }
        ChatMessage lastMessage = chatMessageRepository.findLatestByChatIdAndRole(chatId, userId, role);
        if (lastMessage != null && content.equals(lastMessage.getContent())) {
            LocalDateTime lastCreatedAt = lastMessage.getCreatedAt();
            if (lastCreatedAt != null && lastCreatedAt.isAfter(LocalDateTime.now().minusSeconds(DUPLICATE_WINDOW_SECONDS))) {
                return;
            }
        }
        ChatMessage message = new ChatMessage();
        message.setChatId(chatId);
        message.setTenantId(tenantId);
        message.setUserId(userId);
        message.setRole(role);
        message.setClientMessageId(StringUtils.hasText(clientMessageId) ? clientMessageId : null);
        message.setContent(content);
        LocalDateTime now = LocalDateTime.now();
        message.setCreatedAt(now);
        chatMessageRepository.insert(message);
        chatSessionRepository.updateLastMessageAt(chatId, now);
    }

    private ChatSessionListResponse buildSessionResponse(List<ChatSession> sessions, int limit) {
        boolean hasMore = sessions.size() > limit;
        if (hasMore) {
            sessions = sessions.subList(0, limit);
        }
        ChatSessionListResponse response = new ChatSessionListResponse();
        response.setHasMore(hasMore);
        if (!sessions.isEmpty()) {
            ChatSession last = sessions.get(sessions.size() - 1);
            response.setNextChatId(hasMore ? last.getChatId() : null);
            response.setNextLastMessageAt(hasMore ? last.getLastMessageAt() : null);
        }
        List<ChatSessionVO> records = sessions.stream()
                .map(this::toSessionVO)
                .collect(Collectors.toList());
        response.setRecords(records);
        return response;
    }

    private ChatMessageListResponse buildMessageResponse(List<ChatMessage> messages, int limit) {
        boolean hasMore = messages.size() > limit;
        if (hasMore) {
            messages = messages.subList(0, limit);
        }
        Long nextBeforeId = hasMore && !messages.isEmpty() ? messages.get(messages.size() - 1).getId() : null;
        Collections.reverse(messages);
        List<ChatMessageVO> records = messages.stream()
                .map(this::toMessageVO)
                .collect(Collectors.toList());
        ChatMessageListResponse response = new ChatMessageListResponse();
        response.setRecords(records);
        response.setHasMore(hasMore);
        response.setNextBeforeId(nextBeforeId);
        return response;
    }

    private ChatSessionVO toSessionVO(ChatSession session) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.setChatId(session.getChatId());
        vo.setTitle(session.getTitle());
        vo.setLastMessageAt(session.getLastMessageAt());
        vo.setCreatedAt(session.getCreatedAt());
        return vo;
    }

    private ChatMessageVO toMessageVO(ChatMessage message) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(message.getId());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setCreatedAt(message.getCreatedAt());
        return vo;
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return ChatConstant.DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, ChatConstant.MAX_PAGE_SIZE);
    }
}
