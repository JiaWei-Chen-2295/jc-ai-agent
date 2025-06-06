package fun.javierchen.jcaiagentbackend.controller;


import fun.javierchen.jcaiagentbackend.app.StudyFriend;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping(value = "/ai_friend")
public class StudyFriendController {

    @Resource
    private StudyFriend studyFriend;

    @GetMapping("/do_chat/async")
    public String doChatWithRAG(String chatMessage, String chatId) {
        return studyFriend.doChatWithRAG(chatMessage, chatId);
    }

    @GetMapping("/do_chat/sse/emitter")
    public SseEmitter doChatWithRAGStream(String chatMessage, String chatId) {
        SseEmitter sseEmitter = new SseEmitter(3 * 60 * 1000L);
        studyFriend.doChatWithRAGStream(chatMessage, chatId).subscribe(
                chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (Exception e) {
                        sseEmitter.completeWithError(e);
                    }
                },
                t -> {
                    log.error("出错{}", t.getMessage());
                    sseEmitter.complete();
                },
                sseEmitter::complete
        );
        return sseEmitter;
    }
}
