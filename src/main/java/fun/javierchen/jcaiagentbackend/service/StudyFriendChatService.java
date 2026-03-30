package fun.javierchen.jcaiagentbackend.service;

import fun.javierchen.jcaiagentbackend.chat.model.entity.ChatSession;
import fun.javierchen.jcaiagentbackend.controller.dto.ChatMessageListResponse;
import fun.javierchen.jcaiagentbackend.controller.dto.ChatSessionListResponse;
import fun.javierchen.jcaiagentbackend.controller.dto.ChatSessionVO;

import java.time.LocalDateTime;

public interface StudyFriendChatService {

    /**
     * 创建会话（指定模型 ID）
     *
     * @param modelId 用户选择的模型 ID，为空时使用默认模型
     */
    ChatSessionVO createSession(Long tenantId, Long userId, String title, String modelId);

    /**
     * 创建会话（使用默认模型）
     */
    default ChatSessionVO createSession(Long tenantId, Long userId, String title) {
        return createSession(tenantId, userId, title, null);
    }

    ChatSession requireSessionForUser(String chatId, Long tenantId, Long userId);

    ChatSession requireSessionForAdmin(String chatId);

    void appendUserMessage(String chatId, Long tenantId, Long userId, String content, String clientMessageId);

    void appendAssistantMessage(String chatId, Long tenantId, Long userId, String content);

    ChatSessionListResponse listSessionsForUser(Long tenantId, Long userId,
                                                LocalDateTime beforeLastMessageAt, String beforeChatId, int limit);

    ChatSessionListResponse listSessionsForAdmin(Long tenantId, Long userId,
                                                 LocalDateTime beforeLastMessageAt, String beforeChatId, int limit);

    ChatMessageListResponse listMessagesForUser(String chatId, Long tenantId, Long userId,
                                                Long beforeId, int limit);

    ChatMessageListResponse listMessagesForAdmin(String chatId, Long beforeId, int limit);
}
