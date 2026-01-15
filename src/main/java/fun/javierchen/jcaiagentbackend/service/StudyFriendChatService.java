package fun.javierchen.jcaiagentbackend.service;

import fun.javierchen.jcaiagentbackend.chat.model.entity.ChatSession;
import fun.javierchen.jcaiagentbackend.controller.dto.ChatMessageListResponse;
import fun.javierchen.jcaiagentbackend.controller.dto.ChatSessionListResponse;
import fun.javierchen.jcaiagentbackend.controller.dto.ChatSessionVO;

import java.time.LocalDateTime;

public interface StudyFriendChatService {

    ChatSessionVO createSession(Long tenantId, Long userId, String title);

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
